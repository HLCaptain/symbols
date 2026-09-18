import importlib.util
from io import StringIO
import os
from pathlib import Path
import signal
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).parents[1] / "ci_metrics.py"
spec = importlib.util.spec_from_file_location("ci_metrics", SCRIPT)
ci_metrics = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci_metrics)


class CiMetricsTest(unittest.TestCase):
    def test_failed_child_preserves_output_status_and_writes_sampled_summary(self):
        processes = "/usr/bin/java 1024\n/path with spaces/java 2048\nwasm-opt 4096\npython3 8192\n"
        with patch.object(ci_metrics.subprocess, "check_output", return_value=processes):
            self.assertEqual((7168, 4096), ci_metrics.sample_rss())
        with TemporaryDirectory() as directory:
            summary = Path(directory) / "summary.md"
            with patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": str(summary)}), \
                    patch.object(ci_metrics, "sample_rss", return_value=(7168, 4096)), \
                    patch("sys.stdout", new_callable=StringIO) as output:
                code = ci_metrics.run([sys.executable, "-c", "raise SystemExit(7)"], "Web build")
            self.assertEqual(7, code)
            self.assertIn("Web build: elapsed", summary.read_text())
            self.assertIn("peak combined Java + wasm-opt RSS 7.0 MiB", summary.read_text())
            self.assertIn("peak wasm-opt RSS 4.0 MiB", output.getvalue())

            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--label", "Child output", "--", sys.executable, "-c",
                 "import sys; print('child stdout'); print('child stderr', file=sys.stderr); sys.exit(9)"],
                capture_output=True, text=True, env={**os.environ, "GITHUB_STEP_SUMMARY": str(summary)},
            )
            self.assertEqual(9, result.returncode)
            self.assertIn("child stdout", result.stdout)
            self.assertIn("child stderr", result.stderr)

    def test_unavailable_metrics_do_not_fail_successful_or_signalled_children(self):
        with patch.object(ci_metrics, "sample_rss", side_effect=OSError("ps unavailable")), \
                patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": "/dev/null/not-a-directory"}), \
                patch("sys.stdout", new_callable=StringIO) as output:
            self.assertEqual(0, ci_metrics.run([sys.executable, "-c", "pass"], "No metrics"))
            self.assertEqual(128 + signal.SIGTERM, ci_metrics.run(
                [sys.executable, "-c", "import os, signal; os.kill(os.getpid(), signal.SIGTERM)"], "Signal",
            ))
        self.assertIn("RSS sampling unavailable", output.getvalue())


if __name__ == "__main__":
    unittest.main()
