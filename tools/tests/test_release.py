import importlib.util
from io import BytesIO
from contextlib import chdir
import os
import subprocess
from tempfile import TemporaryDirectory
from textwrap import dedent
from pathlib import Path
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

spec = importlib.util.spec_from_file_location("release", Path(__file__).parents[1] / "release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTest(unittest.TestCase):
    def test_canonical_stable_and_prerelease_tags_are_preserved(self):
        for tag in ("0.0.0", "1.20.300", "1.0.0-alpha01", "1.0.0-beta01", "1.0.0-rc01", "1.0.0-dev02"):
            with self.subTest(tag=tag):
                self.assertEqual(tag, release.version_from_tag(tag))
                for component in release.JOB_NAMES:
                    for url in release.publication_urls(component, release.version_from_tag(tag)):
                        self.assertIn(f"/{tag}", url)
        for tag in ("v1.2.3", "01.2.3", "1.02.3", "1.2.03", "1.2", "1.2.3-SNAPSHOT",
                    "1.2.3-snapshot", "1.2.3-Snapshot", "1.2.3-", "1.2.3-Alpha01", "1.2.3-01",
                    "1.2.3+build", "1.2.3\n", "1.2.3;echo injected"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.version_from_tag(tag)

    def test_github_release_type_follows_tag_without_replacing_existing_notes(self):
        workflow = Path(__file__).parents[2] / ".github/workflows/publish.yml"
        script = dedent(workflow.read_text().rsplit("        run: |\n", 1)[1])
        # Exercise the actual workflow shell with a local gh stub; no release API calls.
        stub = """
        gh() {
          if [[ "$2" == view ]]; then
            return "$RELEASE_MISSING"
          fi
          printf '%s\\n' "$@"
        }
        """
        for tag in ("1.0.0", "1.0.0-alpha01", "1.0.0-beta01"):
            for missing in ("0", "1"):
                with self.subTest(tag=tag, missing=missing):
                    args = subprocess.check_output(["bash", "-c", dedent(stub) + script], text=True,
                        env={**os.environ, "RELEASE_TAG": tag, "RELEASE_MISSING": missing}).splitlines()
                    self.assertEqual(["release", "create" if missing == "1" else "edit", tag], args[:3])
                    self.assertIn("--prerelease" if "-" in tag else "--prerelease=false", args)
                    self.assertEqual("-" in tag, "--latest=false" in args)
                    if missing == "0":
                        self.assertIn("--draft=false", args)
                        self.assertNotIn("--generate-notes", args)
                    else:
                        self.assertIn("--verify-tag", args)
                        self.assertIn("--generate-notes", args)

    def test_tag_must_resolve_to_a_commit_on_main(self):
        with TemporaryDirectory() as directory, chdir(directory), patch.dict(os.environ, {"GITHUB_EVENT_NAME": ""}):
            def git(*args):
                return subprocess.check_output(["git", "-c", "user.name=Release test",
                    "-c", "user.email=release-test@example.invalid", "-c", "commit.gpgsign=false",
                    "-c", "tag.gpgsign=false", *args], text=True).strip()
            git("init", "--initial-branch=main")
            git("commit", "--allow-empty", "-m", "main")
            commit = git("rev-parse", "HEAD")
            git("update-ref", "refs/remotes/origin/main", commit)
            git("tag", "1.0.0")
            self.assertEqual(commit, release.release_commit("1.0.0"))
            with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_SHA": "wrong"}):
                with self.assertRaisesRegex(ValueError, "Manual retries must run from the tag"):
                    release.release_commit("1.0.0")
            with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_SHA": commit}):
                self.assertEqual(commit, release.release_commit("1.0.0"))
            git("commit", "--allow-empty", "-m", "not on origin/main")
            git("tag", "1.0.1")
            with self.assertRaises(subprocess.CalledProcessError):
                release.release_commit("1.0.1")

    def test_registry_failure_cannot_be_treated_as_unpublished(self):
        for code in (401, 403, 429, 500):
            with patch.object(release, "urlopen", side_effect=HTTPError("url", code, "failure", {}, None)):
                with self.assertRaises(HTTPError):
                    release.exists("https://example.com/file.pom")
        with patch.object(release, "urlopen", side_effect=HTTPError("url", 404, "missing", {}, None)):
            self.assertFalse(release.exists("https://example.com/file.pom"))

    def test_portal_not_found_page_is_distinct_from_bad_request(self):
        url = "https://plugins.gradle.org/plugin/example/1.0.0"
        error = HTTPError(url, 400, "missing", {}, BytesIO(b"<title>Gradle - Plugin Not Found</title>"))
        with patch.object(release, "urlopen", side_effect=error):
            self.assertFalse(release.exists(url))
        error = HTTPError(url, 400, "bad request", {}, BytesIO(b"Bad request"))
        with patch.object(release, "urlopen", side_effect=error), self.assertRaises(HTTPError):
            release.exists(url)

    def test_partial_publication_fails_without_reupload(self):
        with patch.object(release, "exists", side_effect=[True, False, False]):
            with self.assertRaisesRegex(ValueError, "Part of the tooling release"):
                release.publication_done("tooling", "0.1.0", [])

    def test_retry_recognizes_success_including_pending_portal_approval(self):
        checks = [{"name": "Publish Central tooling and Plugin Portal (0.1.0)",
                   "conclusion": "success", "app": {"slug": "github-actions"}}]
        with patch.object(release, "exists", side_effect=AssertionError("must use successful job receipt")):
            self.assertTrue(release.publication_done("portal", "0.1.0", checks))
            self.assertTrue(release.publication_done("tooling", "0.1.0", checks))
        self.assertFalse(release.prior_success("portal", "0.2.0", checks))
        checks[0]["conclusion"] = "failure"
        self.assertFalse(release.prior_success("portal", "0.1.0", checks))

    def test_coordinates_include_every_umbrella_pack_and_both_plugin_markers(self):
        self.assertEqual(20, len(set(release.publication_urls("libraries", "0.1.0"))))
        self.assertIn("/symbols-core/0.1.0/symbols-core-0.1.0.pom", release.publication_urls("libraries", "0.1.0")[0])
        self.assertEqual(3, len(release.publication_urls("tooling", "0.1.0")))
        self.assertTrue(release.publication_urls("portal", "0.1.0")[0].startswith("https://plugins.gradle.org/plugin/"))


if __name__ == "__main__":
    unittest.main()
