"""Fast checks for measurement integrity; no Gradle or Android SDK required."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("resource_study", Path(__file__).with_name("run.py"))
study = importlib.util.module_from_spec(spec)
spec.loader.exec_module(study)


class IntegrityTest(unittest.TestCase):
    def test_edit_restores_exact_bytes_after_build_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "source"
            original = b'first\r\ncontentDescription = "Back",\r\nlast\r\n'
            path.write_bytes(original)
            with self.assertRaisesRegex(RuntimeError, "failed"):
                with study.edit(path, b'"Back"', b'"Benchmark back"'):
                    self.assertIn(b'"Benchmark back"', path.read_bytes())
                    raise RuntimeError("build failed")
            self.assertEqual(path.read_bytes(), original)
            with self.assertRaisesRegex(ValueError, "exactly one"):
                with study.edit(path, b"missing", b"replacement"):
                    self.fail("missing edit must fail before mutation")
            self.assertEqual(path.read_bytes(), original)

    def test_clean_removes_only_root_project_build_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for folder in ("build", "androidApp/build", "symbols/one/build", "tooling/build", "build-logic/convention/build", ".gradle"):
                (root / folder).mkdir(parents=True)
                (root / folder / "marker").write_text("keep")
            scripts = "build.gradle.kts\nandroidApp/build.gradle.kts\nsymbols/one/build.gradle.kts\ntooling/build.gradle.kts\nbuild-logic/convention/build.gradle.kts"
            with patch.object(study, "output", return_value=scripts):
                study.clean_outputs(root)
            for folder in ("build", "androidApp/build", "symbols/one/build"):
                self.assertFalse((root / folder).exists())
            for folder in ("tooling/build", "build-logic/convention/build", ".gradle"):
                self.assertTrue((root / folder / "marker").is_file())

    def test_summary_excludes_warmup_and_failures(self):
        rows = []
        for revision, values in (("A", (10, 30, 20)), ("B", (5, 15, 10))):
            for seconds, measured, code in [(999, False, 0), (999, True, 1), *[(value, True, 0) for value in values]]:
                rows.append(dict(revision=revision, profile="shell", variant="release", scenario="noop", measured=measured, exit_code=code, wall_seconds=seconds, peak_process_tree_rss_bytes_sampled=seconds*100, apk={"apk_bytes": 1000}))
        summary = study.summarize(rows)
        self.assertEqual(summary[0]["runs"], 3)
        self.assertEqual(summary[0]["wall_seconds"], dict(median=20, min=10, max=30, samples=[10, 30, 20]))
        comparison = study.comparisons(summary, ["A", "B"])[0]
        self.assertEqual(comparison["delta"]["wall_seconds"], dict(absolute=-10, percent=-50))

    def test_profile_task_duration_parsing(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "profile.html"
            path.write_text('<table><tr><td>:app:compileKotlin</td><td>1m2.50s</td><td>UP-TO-DATE</td></tr><tr><td>project total</td><td>2m</td></tr></table>')
            self.assertEqual(study.profile_tasks(path), [dict(path=":app:compileKotlin", seconds=62.5, result="UP-TO-DATE")])


if __name__ == "__main__":
    unittest.main()
