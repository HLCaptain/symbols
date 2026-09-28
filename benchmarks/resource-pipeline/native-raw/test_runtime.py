from pathlib import Path
import tempfile
import unittest

import runtime


class RuntimePublicationTest(unittest.TestCase):
    def test_existing_coordinate_is_rejected_before_any_publication(self):
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory) / "maven"
            existing = repository / runtime.GROUP.replace(".", "/") / "components-resources-android" / runtime.VERSION
            existing.mkdir(parents=True)
            evidence = existing / "unchanged.txt"
            evidence.write_text("frozen artifact")
            with self.assertRaisesRegex(FileExistsError, "fresh repository"):
                runtime.publish(Path(directory) / "no-build", repository)
            self.assertEqual(evidence.read_text(), "frozen artifact")
            self.assertFalse((repository / runtime.GROUP.replace(".", "/") / "components-resources").exists())


if __name__ == "__main__":
    unittest.main()
