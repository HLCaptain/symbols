import importlib.util
from io import BytesIO, StringIO
from contextlib import chdir, redirect_stderr, redirect_stdout
import json
import os
import subprocess
from tempfile import TemporaryDirectory
from textwrap import dedent
from pathlib import Path
import unittest
from unittest.mock import call, patch
from urllib.error import HTTPError

spec = importlib.util.spec_from_file_location("release", Path(__file__).parents[1] / "release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTest(unittest.TestCase):
    def test_canonical_stable_and_prerelease_tags_are_preserved(self):
        for tag in ("0.0.0", "1.20.300", "1.0.0-alpha01", "1.0.0-beta01", "1.0.0-rc01", "1.0.0-dev02",
                    "2.0.0-SNAPSHOT-deadbeef", "0.0.0-SNAPSHOT-01234567"):
            with self.subTest(tag=tag):
                self.assertEqual(tag, release.version_from_tag(tag))
                for component in release.JOB_NAMES:
                    for url in release.publication_urls(component, release.version_from_tag(tag)):
                        self.assertIn(f"/{tag}", url)
        for tag in ("v1.2.3", "01.2.3", "1.02.3", "1.2.03", "1.2", "1.2.3-SNAPSHOT",
                    "1.2.3-snapshot", "1.2.3-Snapshot", "1.2.3-", "1.2.3-Alpha01", "1.2.3-01",
                    "1.2.3+build", "1.2.3\n", "1.2.3;echo injected",
                    "1.2.3-snapshot-deadbeef", "1.2.3-SNAPSHOT-DEADBEEF", "1.2.3-SNAPSHOT-deadbee",
                    "1.2.3-SNAPSHOT-deadbeef0", "1.2.3-SNAPSHOT-deadbeeg", "1.2.3-alpha01-SNAPSHOT-deadbeef",
                    "1.2.3-SNAPSHOT-deadbeef\n"):
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
        for tag in ("1.0.0", "1.0.0-alpha01", "1.0.0-beta01", "2.0.0-SNAPSHOT-deadbeef"):
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

    def test_snapshot_retry_accepts_only_expected_reusable_job_receipts(self):
        tag = "2.0.0-SNAPSHOT-deadbeef"
        name = f"Publish Central tooling and Plugin Portal ({tag})"
        for prefix in ("", "Publish snapshot / "):
            checks = [{"name": prefix + name, "conclusion": "success", "app": {"slug": "github-actions"}}]
            with self.subTest(prefix=prefix), patch.object(
                release, "exists", side_effect=AssertionError("successful receipt must avoid another registry submission")
            ):
                self.assertTrue(release.publication_done("portal", tag, checks))
                self.assertTrue(release.publication_done("tooling", tag, checks))
        for actual_name, conclusion, app in (
            ("Other workflow / " + name, "success", "github-actions"),
            ("Publish snapshot / " + name, "failure", "github-actions"),
            ("Publish snapshot / " + name, "success", "other-app"),
            ("Publish snapshot / " + name.replace("deadbeef", "feedface"), "success", "github-actions"),
        ):
            with self.subTest(name=actual_name, conclusion=conclusion, app=app):
                self.assertFalse(release.prior_success("portal", tag, [
                    {"name": actual_name, "conclusion": conclusion, "app": {"slug": app}},
                ]))

    def test_wait_checks_only_missing_coordinates_until_they_are_public(self):
        with patch.object(release, "publication_urls", return_value=["first", "second"]), \
                patch.object(release, "exists", side_effect=[True, False, True]) as exists, \
                patch.object(release.time, "monotonic", return_value=0), \
                patch.object(release.time, "sleep") as sleep:
            release.wait_for_publication("tooling", "1.0.1")
        self.assertEqual([call("first"), call("second"), call("second")], exists.call_args_list)
        sleep.assert_called_once_with(30)

    def test_wait_timeout_requires_inspection_instead_of_another_upload(self):
        with patch.object(release, "publication_urls", return_value=["pending"]), \
                patch.object(release, "exists", return_value=False), \
                patch.object(release.time, "monotonic", side_effect=[0, 1, 15]), \
                patch.object(release.time, "sleep") as sleep:
            with self.assertRaisesRegex(TimeoutError, "do not re-upload"):
                release.wait_for_publication("libraries", "1.0.1", timeout_seconds=15)
        sleep.assert_called_once_with(14)

    def test_wait_does_not_hide_registry_errors(self):
        error = HTTPError("url", 503, "unavailable", {}, None)
        with patch.object(release, "publication_urls", return_value=["url"]), \
                patch.object(release, "exists", side_effect=error), \
                self.assertRaises(HTTPError):
            release.wait_for_publication("tooling", "1.0.1")

    def test_coordinates_include_every_umbrella_pack_and_both_plugin_markers(self):
        self.assertEqual(20, len(set(release.publication_urls("libraries", "0.1.0"))))
        self.assertIn("/symbols-core/0.1.0/symbols-core-0.1.0.pom", release.publication_urls("libraries", "0.1.0")[0])
        self.assertEqual(3, len(release.publication_urls("tooling", "0.1.0")))
        self.assertTrue(release.publication_urls("portal", "0.1.0")[0].startswith("https://plugins.gradle.org/plugin/"))


class SnapshotReleaseTest(unittest.TestCase):
    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        working_directory = chdir(temporary.name)
        working_directory.__enter__()
        self.addCleanup(working_directory.__exit__, None, None, None)
        environment = patch.dict(os.environ, {"GITHUB_EVENT_NAME": "", "GITHUB_REF": "", "GITHUB_SHA": ""})
        environment.start()
        self.addCleanup(environment.stop)
        self.git("init", "--initial-branch=main")
        self.initial = self.commit("initial")

    def git(self, *args):
        return subprocess.check_output([
            "git", "-c", "user.name=Release test", "-c", "user.email=release-test@example.invalid",
            "-c", "commit.gpgsign=false", "-c", "tag.gpgsign=false", *args,
        ], text=True, stderr=subprocess.PIPE).strip()

    def commit(self, message, main=True):
        self.git("commit", "--allow-empty", "-m", message)
        commit = self.git("rev-parse", "HEAD")
        if main:
            self.git("update-ref", "refs/remotes/origin/main", commit)
        return commit

    def push_environment(self, commit, *, ref="refs/heads/main", event_name="push", **event_changes):
        event = {"ref": ref, "after": commit, "deleted": False, "forced": False, **event_changes}
        event_file = Path("event.json").resolve()
        event_file.write_text(json.dumps(event))
        return patch.dict(os.environ, {
            "GITHUB_EVENT_NAME": event_name, "GITHUB_REF": ref,
            "GITHUB_SHA": commit, "GITHUB_EVENT_PATH": str(event_file),
        })

    def test_snapshot_uses_highest_numeric_stable_ancestor_not_nearest_or_unmerged_tag(self):
        self.git("tag", "-a", "2.10.0", "-m", "stable")
        self.commit("later lower version")
        for tag in ("2.9.0", "3.0.0-rc01", "v4.0.0", "99.0.0-SNAPSHOT-" + self.initial[:8]):
            self.git("tag", tag)
        commit = self.commit("snapshot source")
        self.git("switch", "-c", "unmerged")
        self.commit("not on main", main=False)
        self.git("tag", "9.0.0")
        self.git("switch", "main")
        before = self.git("tag", "--list")
        self.assertEqual(f"2.10.0-SNAPSHOT-{commit[:8]}", release.snapshot_tag(commit))
        self.assertEqual(before, self.git("tag", "--list"), "preparation must not create tags")

    def test_snapshot_accepts_historical_main_commit_and_ignores_later_release(self):
        self.git("tag", "1.0.0")
        commit = self.commit("earlier push")
        self.commit("later push")
        self.git("tag", "2.0.0")
        self.assertEqual(f"1.0.0-SNAPSHOT-{commit[:8]}", release.snapshot_tag(commit))

    def test_snapshot_retry_reuses_existing_tag_when_a_new_stable_tag_is_added(self):
        self.git("tag", "1.0.0")
        commit = self.commit("snapshot source")
        tag = f"1.0.0-SNAPSHOT-{commit[:8]}"
        self.git("tag", "-a", tag, "-m", "snapshot")
        self.git("tag", "2.0.0")
        self.assertEqual(tag, release.snapshot_tag(commit))

    def test_snapshot_rejects_missing_stable_base(self):
        for tag in ("1.0.0-rc01", "v1.0.0", "01.0.0"):
            self.git("tag", tag)
        with self.assertRaises(ValueError):
            release.snapshot_tag(self.initial)

    def test_snapshot_rejects_ambiguous_existing_tags(self):
        self.git("tag", "1.0.0")
        commit = self.commit("snapshot source")
        for base in ("1.0.0", "2.0.0"):
            self.git("tag", f"{base}-SNAPSHOT-{commit[:8]}")
        with self.assertRaises(ValueError):
            release.snapshot_tag(commit)

    def test_snapshot_rejects_short_hash_collision_without_moving_ref(self):
        self.git("tag", "1.0.0")
        commit = self.commit("snapshot source")
        tag = f"1.0.0-SNAPSHOT-{commit[:8]}"
        self.git("tag", tag, self.initial)
        with self.assertRaises(ValueError):
            release.snapshot_tag(commit)
        self.assertEqual(self.initial, self.git("rev-parse", tag + "^{commit}"))

    def test_snapshot_requires_full_sha_and_main_ancestry(self):
        for commit in ("HEAD", self.initial[:8], "A" + self.initial[1:], "g" * 40, self.initial + "\n"):
            with self.subTest(commit=commit), self.assertRaises(ValueError):
                release.snapshot_tag(commit)
        self.git("tag", "1.0.0")
        other = self.commit("not on remote main", main=False)
        with self.assertRaises((ValueError, subprocess.CalledProcessError)):
            release.snapshot_tag(other)

    def test_snapshot_release_outputs_are_bound_to_the_push_and_do_not_create_tags(self):
        self.git("tag", "1.0.0")
        commit = self.commit("main push")
        tag = f"1.0.0-SNAPSHOT-{commit[:8]}"
        with self.push_environment(commit):
            self.assertEqual({"tag": tag, "version": tag, "commit": commit, "tag_exists": "false"},
                             release.snapshot_release())
            self.assertNotIn(tag, self.git("tag", "--list").splitlines())
            self.git("tag", tag)
            self.assertEqual({"tag": tag, "version": tag, "commit": commit, "tag_exists": "true"},
                             release.snapshot_release())

    def test_snapshot_release_allows_forced_main_push_but_rejects_wrong_context(self):
        self.git("tag", "1.0.0")
        commit = self.commit("main push")
        with self.push_environment(commit, forced=True):
            self.assertEqual(commit, release.snapshot_release()["commit"])
        for changes in ({"event_name": "pull_request"}, {"event_name": "workflow_dispatch"},
                        {"ref": "refs/heads/other"}, {"ref": "refs/tags/1.0.0"},
                        {"deleted": True}, {"after": self.initial}):
            with self.subTest(changes=changes), self.push_environment(commit, **changes), self.assertRaises(ValueError):
                release.snapshot_release()
        with self.push_environment(self.initial), self.assertRaises(ValueError):
            release.snapshot_release()  # HEAD is a different commit, despite valid ancestry.
        with self.push_environment(commit), self.assertRaises(ValueError):
            event_file = Path(os.environ["GITHUB_EVENT_PATH"])
            event = json.loads(event_file.read_text())
            event_file.write_text(json.dumps({**event, "ref": "refs/heads/other"}))
            release.snapshot_release()

    def test_snapshot_release_commit_binds_suffix_and_preserves_manual_retry_guard(self):
        self.git("tag", "1.0.0")
        commit = self.commit("snapshot source")
        tag = f"1.0.0-SNAPSHOT-{commit[:8]}"
        self.git("tag", "-a", tag, "-m", "snapshot")
        self.assertEqual(commit, release.release_commit(tag))
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_SHA": commit}):
            self.assertEqual(commit, release.release_commit(tag))
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_SHA": self.initial}):
            with self.assertRaisesRegex(ValueError, "Manual retries must run from the tag"):
                release.release_commit(tag)
        other_prefix = ("0" if commit[0] != "0" else "1") + commit[1:8]
        wrong = f"1.0.0-SNAPSHOT-{other_prefix}"
        self.git("tag", wrong)
        with self.assertRaises(ValueError):
            release.release_commit(wrong)

    def test_release_commit_allows_main_push_but_rejects_tag_moves_or_deletion(self):
        tag = "1.0.0-SNAPSHOT-" + self.initial[:8]
        self.git("tag", "-a", tag, "-m", "stable")
        with self.push_environment(self.initial, forced=True):
            self.assertEqual(self.initial, release.release_commit(tag))
        self.git("tag", "1.0.0")
        with self.push_environment(self.initial, forced=True), self.assertRaises(ValueError):
            release.release_commit("1.0.0")
        tag_object = self.git("rev-parse", "refs/tags/" + tag)
        with self.push_environment(self.initial, ref="refs/tags/" + tag, after=tag_object):
            self.assertEqual(self.initial, release.release_commit(tag))
        for changes in ({"forced": True}, {"deleted": True}, {"after": "f" * 40}):
            with self.subTest(changes=changes), self.push_environment(
                self.initial, ref="refs/tags/" + tag, **changes
            ), self.assertRaises(ValueError):
                release.release_commit(tag)

    def test_snapshot_cli_outputs_without_registry_queries_and_modes_are_exclusive(self):
        tag = "1.0.0-SNAPSHOT-" + self.initial[:8]
        expected = {"tag": tag, "version": tag, "commit": self.initial, "tag_exists": "false"}
        output = Path("github-output").resolve()
        stdout = StringIO()
        with patch.dict(os.environ, {"GITHUB_OUTPUT": str(output)}), \
                patch("sys.argv", ["release.py", "--snapshot"]), \
                patch.object(release, "snapshot_release", return_value=expected), \
                patch.object(release, "publication_done", side_effect=AssertionError("snapshot planning is read-only")), \
                redirect_stdout(stdout):
            release.main()
        self.assertEqual(expected, json.loads(stdout.getvalue()))
        values = dict(line.split("=", 1) for line in output.read_text().splitlines())
        self.assertEqual(tag, values["tag"])
        self.assertEqual(self.initial, values["commit"])
        self.assertEqual("false", values["tag_exists"])
        with patch("sys.argv", ["release.py", "--snapshot", "--tag", "1.0.0"]), \
                redirect_stderr(StringIO()), self.assertRaises(SystemExit) as error:
            release.main()
        self.assertEqual(2, error.exception.code)


if __name__ == "__main__":
    unittest.main()
