import base64
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
        for name in ("changed", "removed"):
            (base / f"{name}.png").write_bytes(png(0))
        (base / "same.png").write_bytes(png(64))
        (head / "same.png").write_bytes(png(64))
        for name in ("changed", "added"):
            (head / f"{name}.png").write_bytes(png(255))
        (diffs / "changed_compare.png").write_bytes(png(128))
        result = report.build_report(base, head, diffs, root / "report", outcome, True, "a" * 40, "b" * 40)
        return result

    def unchanged_fixture(self, root, outcome="success"):
        for name in ("base", "head", "diffs"):
            (root / name).mkdir()
        for name in ("base", "head"):
            (root / name / "same.png").write_bytes(png(64))
        return report.build_report(root / "base", root / "head", root / "diffs", root / "report",
                                   outcome, True, "a" * 40, "b" * 40)

    def test_added_removed_changed_rows_store_only_the_displayed_images(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            result = self.fixture(root)
            self.assertEqual(["Added", "Changed", "Removed"], [r["status"] for r in result["changes"]])
            self.assertNotIn("previews", result)
            self.assertFalse(result["technical_failure"])
            self.assertEqual(3, len(list((root / "report/images").glob("*.png"))))
            self.assertEqual(["PR image", "Diff", "Base image"], [row["image_label"] for row in result["changes"]])
            for row, expected in zip(result["changes"], (png(255), png(128), png(0))):
                self.assertEqual({"name", "status", "image", "image_label"}, set(row))
                self.assertEqual(expected, (root / "report" / row["image"]).read_bytes())
            self.assertNotIn(png(64), [path.read_bytes() for path in (root / "report/images").glob("*.png")])

    def test_comparison_errors_cannot_be_approved_as_visual_differences(self):
        with TemporaryDirectory() as d:
            result = self.fixture(Path(d), "failure")
            self.assertTrue(result["technical_failure"])
            body = report.markdown(result)
            self.assertIn("cannot override", body)
            self.assertNotIn(f"apply the `{report.APPROVAL}`", body)

    def test_metadata_only_changes_have_no_image_payload(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            for name in ("base", "head", "diffs"):
                (root / name).mkdir()
            (root / "base/a.png").write_bytes(png(0))
            (root / "head/a.png").write_bytes(png(0) + b"metadata")
            result = report.build_report(root / "base", root / "head", root / "diffs", root / "report", "success", True, "a" * 40, "b" * 40)
            self.assertEqual([], result["changes"])
            self.assertNotIn("previews", result)
            self.assertEqual([], list((root / "report/images").glob("*.png")))
            self.assertNotIn("<img", report.markdown(result))

    def test_actual_images_are_ignored(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.unchanged_fixture(root)
            (root / "diffs/same_actual.png").write_bytes(png(255))
            (root / "diffs/unmatched_actual.png").write_bytes(png(128))
            result = report.build_report(root / "base", root / "head", root / "diffs", root / "extra-report",
                                         "success", True, "a" * 40, "b" * 40)
            self.assertEqual([], result["changes"])
            self.assertFalse(result["technical_failure"])
            self.assertEqual([], list((root / "extra-report/images").glob("*.png")))

    def test_failed_comparison_uses_current_image_when_no_diff_was_emitted(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            (root / "diffs/changed_compare.png").unlink()
            result = report.build_report(root / "base", root / "head", root / "diffs", root / "extra-report",
                                         "failure", True, "a" * 40, "b" * 40)
            changed = next(row for row in result["changes"] if row["name"] == "changed.png")
            self.assertEqual("Changed", changed["status"])
            self.assertEqual("PR image", changed["image_label"])
            self.assertEqual(png(255), (root / "extra-report" / changed["image"]).read_bytes())
            self.assertTrue(result["technical_failure"])
            self.assertIn("cannot override", report.markdown(result))

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

    def test_markdown_formats_profiles_and_limits_inline_images_per_group(self):
        prefix = "io.github.hlcaptain.symbols.sample.imagevectormigration.SvgIconExamplesKt"
        name = f"{prefix}.ExamplePreview.Expanded_W1000dp_H700dp.png"
        source = "samples/image-vector-migration/src/commonMain/kotlin/io/github/hlcaptain/symbols/sample/imagevectormigration/SvgIconExamples.kt"
        self.assertEqual(("SvgIconExamples.kt", "Expanded (1000x700 dp)", "ExamplePreview", source),
                         report.preview_details(name))
        result = {
            "head_sha": "a" * 40, "base_sha": "b" * 40, "outcome": "success", "technical_failure": False,
            "changes": [{"name": name, "status": status, "image": f"images/{status}-{index}.png", "image_label": label}
                        for status, label in (("Added", "PR image"), ("Changed", "Diff"), ("Removed", "Base image"))
                        for index in range(26)],
        }
        source_root = "https://github.com/owner/repo/blob/" + "a" * 40 + "/"
        body = report.markdown(result, "https://images.example/", source_root)
        self.assertIn("## Roborazzi Visual Comparison", body)
        self.assertEqual(3, body.count("| Baseline profile | Variant | Image |"))
        self.assertIn(f"[SvgIconExamples.kt]({source_root}{source})", body)
        self.assertIn("Preview: <code>ExamplePreview</code>", body)
        self.assertIn("Expanded &#40;1000x700 dp&#41;", body)
        self.assertEqual(75, body.count("<img "))
        self.assertEqual(78, body.count("[Open "))
        for status, title in (("Added", "Added"), ("Changed", "Modified"), ("Removed", "Removed")):
            self.assertIn(f"<summary>{title} baseline image profiles: 26</summary>", body)
            last_row = next(line for line in body.splitlines() if f"images/{status}-25.png" in line)
            self.assertNotIn("<img", last_row)
        self.assertNotIn("Current PR previews", body)

    def test_publication_is_orphaned_and_links_immutable_public_images(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            calls = []
            def api(endpoint, method="GET", data=None, **kwargs):
                calls.append((endpoint, method, data))
                if "/pulls/" in endpoint:
                    return {"state": "open", "head": {"sha": "a" * 40, "repo": {"full_name": "owner/repo"}},
                            "base": {"sha": "b" * 40}, "labels": []}
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
            blobs = [base64.b64decode(data["content"]) for endpoint, _, data in calls if endpoint.endswith("/git/blobs")]
            self.assertNotIn(png(64), blobs)
            self.assertEqual(3, sum(blob.startswith(report.PNG) for blob in blobs))

    def test_closed_stale_and_fork_prs_never_publish(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            for state, sha, base, repo in [
                ("closed", "a" * 40, "b" * 40, "owner/repo"),
                ("open", "d" * 40, "b" * 40, "owner/repo"),
                ("open", "a" * 40, "d" * 40, "owner/repo"),
                ("open", "a" * 40, "b" * 40, "fork/repo"),
            ]:
                pr = {"state": state, "head": {"sha": sha, "repo": {"full_name": repo}}, "base": {"sha": base}}
                with patch.object(report, "api", return_value=pr) as api:
                    report.publish(root / "report", "owner/repo", 7)
                    self.assertEqual(1, api.call_count)
                    self.assertEqual("repos/owner/repo/pulls/7", api.call_args.args[0])

    def test_base_change_during_publication_never_updates_branch_or_comment(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            calls = []
            pr_reads = 0
            def api(endpoint, method="GET", data=None, **kwargs):
                nonlocal pr_reads
                calls.append((endpoint, method, data))
                if "/pulls/" in endpoint:
                    pr_reads += 1
                    return {"state": "open", "head": {"sha": "a" * 40, "repo": {"full_name": "owner/repo"}},
                            "base": {"sha": ("b" if pr_reads == 1 else "d") * 40}, "labels": []}
                if "comments?" in endpoint:
                    return [[]]
                return {"sha": "c" * 40}
            with patch.object(report, "api", side_effect=api):
                report.publish(root / "report", "owner/repo", 7)
            self.assertEqual(2, pr_reads)
            self.assertFalse(any("/git/refs" in endpoint or ("/issues/" in endpoint and method != "GET")
                                 for endpoint, method, _ in calls))

    def test_clean_comparison_retires_existing_report_without_creating_a_comment(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.unchanged_fixture(root)
            for previous in (None, {"id": 1, "user": {"login": "github-actions[bot]"}, "body": report.MARKER + "old report"}):
                with self.subTest(previous=previous is not None):
                    calls = []
                    def api(endpoint, method="GET", data=None, **kwargs):
                        calls.append((endpoint, method, data))
                        if "/pulls/" in endpoint:
                            return {"state": "open", "head": {"sha": "a" * 40, "repo": {"full_name": "owner/repo"}},
                                    "base": {"sha": "b" * 40}, "labels": []}
                        if "comments?" in endpoint:
                            return [[previous] if previous else []]
                        return None
                    with patch.object(report, "api", side_effect=api), patch.dict(os.environ, {"GITHUB_RUN_ID": "123"}):
                        report.publish(root / "report", "owner/repo", 7)
                    writes = [(endpoint, method, data) for endpoint, method, data in calls if method != "GET"]
                    expected = [("repos/owner/repo/git/refs/heads/roborazzi-pr-7", "DELETE")]
                    if previous:
                        expected.append(("repos/owner/repo/issues/comments/1", "PATCH"))
                    self.assertEqual(expected, [(endpoint, method) for endpoint, method, _ in writes])
                    if previous:
                        body = writes[-1][2]["body"]
                        self.assertIn("No Roborazzi visual changes were detected.", body)
                        self.assertNotIn("<img", body)
                        self.assertNotIn("/archive/", body)

    def test_clean_report_rechecks_base_before_deleting_prior_report(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.unchanged_fixture(root)
            calls = []
            pr_reads = 0
            def api(endpoint, method="GET", data=None, **kwargs):
                nonlocal pr_reads
                calls.append((endpoint, method))
                if "/pulls/" in endpoint:
                    pr_reads += 1
                    return {"state": "open", "head": {"sha": "a" * 40, "repo": {"full_name": "owner/repo"}},
                            "base": {"sha": ("b" if pr_reads == 1 else "d") * 40}, "labels": []}
                if "comments?" in endpoint:
                    return [[{"id": 1, "user": {"login": "github-actions[bot]"}, "body": report.MARKER}]]
                return None
            with patch.object(report, "api", side_effect=api), patch.dict(os.environ, {"GITHUB_RUN_ID": "123"}):
                report.publish(root / "report", "owner/repo", 7)
            self.assertEqual(2, pr_reads)
            self.assertTrue(all(method == "GET" for _, method in calls))

    def test_failed_comparison_without_changes_posts_a_failure_comment(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            result = self.unchanged_fixture(root, "failure")
            self.assertTrue(result["technical_failure"])
            self.assertEqual([], result["changes"])
            calls = []
            def api(endpoint, method="GET", data=None, **kwargs):
                calls.append((endpoint, method, data))
                if "/pulls/" in endpoint:
                    return {"state": "open", "head": {"sha": "a" * 40, "repo": {"full_name": "owner/repo"}},
                            "base": {"sha": "b" * 40}, "labels": [{"name": report.APPROVAL}]}
                if "comments?" in endpoint:
                    return [[]]
                return None
            with patch.object(report, "api", side_effect=api), patch.dict(os.environ, {"GITHUB_RUN_ID": "123"}):
                report.publish(root / "report", "owner/repo", 7)
            body = next(data["body"] for endpoint, method, data in calls if endpoint.endswith("/comments") and method == "POST")
            self.assertIn("Status: **Failed**", body)
            self.assertIn("cannot override", body)
            self.assertNotIn("No Roborazzi visual changes", body)
            self.assertFalse(any(endpoint.endswith("/git/blobs") for endpoint, _, _ in calls))

    def test_approval_is_retained_only_for_the_same_head_and_base(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            for receipt, expect_delete in [(f'<!-- source:{"a" * 40}:{"b" * 40} -->', False), ("old report", True)]:
                calls = []
                def api(endpoint, method="GET", data=None, **kwargs):
                    calls.append((endpoint, method, data))
                    if "/pulls/" in endpoint:
                        return {"state": "open", "head": {"sha": "a" * 40, "repo": {"full_name": "owner/repo"}},
                                "base": {"sha": "b" * 40}, "labels": [{"name": report.APPROVAL}]}
                    if "comments?" in endpoint:
                        return [[{"id": 1, "user": {"login": "github-actions[bot]"}, "body": report.MARKER + receipt}]]
                    return {"sha": "c" * 40}
                with patch.object(report, "api", side_effect=api), patch.dict(os.environ, {"GITHUB_RUN_ID": "123"}):
                    report.publish(root / "report", "owner/repo", 7)
                self.assertEqual(expect_delete, any(method == "DELETE" for _, method, _ in calls))


if __name__ == "__main__":
    unittest.main()
