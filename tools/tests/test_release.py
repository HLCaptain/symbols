import importlib.util
from io import BytesIO
from contextlib import chdir
import os
import subprocess
from tempfile import TemporaryDirectory
from pathlib import Path
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

spec = importlib.util.spec_from_file_location("release", Path(__file__).parents[1] / "release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTest(unittest.TestCase):
    def test_only_canonical_stable_tags_are_accepted(self):
        self.assertEqual("1.20.300", release.version_from_tag("v1.20.300"))
        for tag in ("1.2.3", "v01.2.3", "v1.2", "v1.2.3-SNAPSHOT", "v1.2.3\n", "v1.2.3;echo injected"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.version_from_tag(tag)

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
            git("tag", "v1.0.0")
            self.assertEqual(commit, release.release_commit("v1.0.0"))
            git("commit", "--allow-empty", "-m", "not on origin/main")
            git("tag", "v1.0.1")
            with self.assertRaises(subprocess.CalledProcessError):
                release.release_commit("v1.0.1")

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
                release.publication_done("tooling", "v0.1.0", [])

    def test_retry_recognizes_success_including_pending_portal_approval(self):
        checks = [{"name": "Publish Central tooling and Plugin Portal (v0.1.0)",
                   "conclusion": "success", "app": {"slug": "github-actions"}}]
        with patch.object(release, "exists", side_effect=AssertionError("must use successful job receipt")):
            self.assertTrue(release.publication_done("portal", "v0.1.0", checks))
            self.assertTrue(release.publication_done("tooling", "v0.1.0", checks))
        self.assertFalse(release.prior_success("portal", "v0.2.0", checks))
        checks[0]["conclusion"] = "failure"
        self.assertFalse(release.prior_success("portal", "v0.1.0", checks))

    def test_coordinates_include_every_umbrella_pack_and_both_plugin_markers(self):
        self.assertEqual(20, len(set(release.publication_urls("libraries", "0.1.0"))))
        self.assertIn("/symbols-core/0.1.0/symbols-core-0.1.0.pom", release.publication_urls("libraries", "0.1.0")[0])
        self.assertEqual(3, len(release.publication_urls("tooling", "0.1.0")))
        self.assertTrue(release.publication_urls("portal", "0.1.0")[0].startswith("https://plugins.gradle.org/plugin/"))


if __name__ == "__main__":
    unittest.main()
