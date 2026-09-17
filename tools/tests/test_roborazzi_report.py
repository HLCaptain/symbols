import importlib.util
import json
import os
from pathlib import Path
import struct
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
import zlib

spec = importlib.util.spec_from_file_location("report", Path(__file__).parents[1] / "roborazzi_report.py")
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


def png(red):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    return report.PNG + chunk(b"IHDR", struct.pack(">2I5B", 1, 1, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(bytes([0, red, 0, 0, 255]))) + chunk(b"IEND", b"")


class ReportTest(unittest.TestCase):
    def setUp(self):
        environment = patch.dict(os.environ, {"GITHUB_OUTPUT": "", "GITHUB_STEP_SUMMARY": ""})
        environment.start()
        self.addCleanup(environment.stop)

    def fixture(self, root, outcome="success"):
        base, head, diffs = [root / name for name in ("base", "head", "diffs")]
        for p in (base, head, diffs):
            p.mkdir()
        for name in ("same", "changed", "removed"):
            (base / f"{name}.png").write_bytes(png(0))
        (head / "same.png").write_bytes(png(0))
        for name in ("changed", "added"):
            (head / f"{name}.png").write_bytes(png(255))
        (diffs / "changed_compare.png").write_bytes(png(128))
        result = report.build_report(base, head, diffs, root / "report", outcome, True, "a" * 40, "b" * 40)
        return result

    def test_gallery_and_added_removed_changed_images(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            result = self.fixture(root)
            self.assertEqual(["Added", "Changed", "Removed"], [r["status"] for r in result["changes"]])
            self.assertEqual(3, len(result["previews"]))
            self.assertFalse(result["technical_failure"])
            self.assertEqual(3, len(list((root / "report/images").glob("*.png"))))

    def test_comparison_errors_cannot_be_approved_as_visual_differences(self):
        with TemporaryDirectory() as d:
            self.assertTrue(self.fixture(Path(d), "failure")["technical_failure"])

    def test_metadata_only_changes_and_unchanged_gallery(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            for name in ("base", "head", "diffs"):
                (root / name).mkdir()
            (root / "base/a.png").write_bytes(png(0))
            (root / "head/a.png").write_bytes(png(0) + b"metadata")
            result = report.build_report(root / "base", root / "head", root / "diffs", root / "report", "success", True, "a" * 40, "b" * 40)
            self.assertEqual([], result["changes"])
            self.assertEqual(1, len(result["previews"]))

    def test_unmatched_diff_cannot_silently_pass(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            (root / "diffs/unexpected_compare.png").write_bytes(png(128))
            result = report.build_report(root / "base", root / "head", root / "diffs", root / "extra-report", "success", True, "a" * 40, "b" * 40)
            self.assertTrue(result["technical_failure"])
            self.assertTrue(any(row["name"] == "unexpected.png" for row in result["changes"]))

    def test_rejects_symlinks_non_png_and_escapes_labels(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            (root / "secret.png").write_text("not a screenshot")
            with self.assertRaises(ValueError):
                report.images(root)
            (root / "secret.png").unlink()
            (root / "link.png").symlink_to(__file__)
            with self.assertRaises(ValueError):
                report.images(root)
        label = report.escape("x|<img>[click](url)`")
        self.assertNotIn("<img>", label)
        self.assertNotIn("[click]", label)
        self.assertNotIn("|", label)

    def test_publication_is_orphaned_and_links_immutable_public_images(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            calls = []
            def api(endpoint, method="GET", data=None, **kwargs):
                calls.append((endpoint, method, data))
                if "/pulls/" in endpoint:
                    return {"state": "open", "head": {"sha": "a" * 40, "repo": {"full_name": "owner/repo"}}, "labels": []}
                if "comments?" in endpoint:
                    return [[]]
                if "/git/ref/" in endpoint:
                    return None
                return {"sha": "c" * 40}
            with patch.object(report, "api", side_effect=api), patch.dict(os.environ, {"GITHUB_RUN_ID": "123"}):
                report.publish(root / "report", "owner/repo", 7)
            commit = next(data for endpoint, _, data in calls if endpoint.endswith("/git/commits"))
            self.assertEqual([], commit["parents"])
            ref = next(data for endpoint, _, data in calls if endpoint.endswith("/git/refs"))
            self.assertEqual("refs/heads/roborazzi-pr-7", ref["ref"])
            body = next(data["body"] for endpoint, method, data in calls if endpoint.endswith("/comments") and method == "POST")
            self.assertIn("https://raw.githubusercontent.com/owner/repo/" + "c" * 40 + "/images/", body)
            self.assertIn("/archive/refs/heads/roborazzi-pr-7.zip", body)

    def test_closed_stale_and_fork_prs_never_publish(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            for state, sha, repo in [("closed", "a" * 40, "owner/repo"), ("open", "d" * 40, "owner/repo"), ("open", "a" * 40, "fork/repo")]:
                pr = {"state": state, "head": {"sha": sha, "repo": {"full_name": repo}}}
                with patch.object(report, "api", return_value=pr) as api:
                    report.publish(root / "report", "owner/repo", 7)
                    self.assertEqual(1, api.call_count)
                    self.assertEqual("repos/owner/repo/pulls/7", api.call_args.args[0])

    def test_approval_is_retained_only_for_the_same_head_and_base(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            for receipt, expect_delete in [(f'<!-- source:{"a" * 40}:{"b" * 40} -->', False), ("old report", True)]:
                calls = []
                def api(endpoint, method="GET", data=None, **kwargs):
                    calls.append((endpoint, method, data))
                    if "/pulls/" in endpoint:
                        return {"state": "open", "head": {"sha": "a" * 40, "repo": {"full_name": "owner/repo"}}, "labels": [{"name": report.APPROVAL}]}
                    if "comments?" in endpoint:
                        return [[{"id": 1, "user": {"login": "github-actions[bot]"}, "body": report.MARKER + receipt}]]
                    return {"sha": "c" * 40}
                with patch.object(report, "api", side_effect=api), patch.dict(os.environ, {"GITHUB_RUN_ID": "123"}):
                    report.publish(root / "report", "owner/repo", 7)
                self.assertEqual(expect_delete, any(method == "DELETE" for _, method, _ in calls))


if __name__ == "__main__":
    unittest.main()
