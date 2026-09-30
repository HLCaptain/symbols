import copy
from pathlib import Path
import tempfile
import unittest

import runtime


class RuntimePublicationTest(unittest.TestCase):
    def test_android_capability_preserves_default_and_other_platform_variants(self):
        metadata = {
            "component": {"group": runtime.GROUP, "module": "components-resources", "version": "1.12.1"},
            "variants": [
                {"name": name, "attributes": {"org.jetbrains.kotlin.platform.type": platform, "org.gradle.category": category},
                 "available-at": {"module": "components-resources-" + platform, "version": "1.12.1"}}
                for name, platform, category in (
                    ("androidApi", "androidJvm", "library"),
                    ("androidRuntime", "androidJvm", "library"),
                    ("androidSources", "androidJvm", "documentation"),
                    ("common", "common", "library"),
                    ("jvm", "jvm", "library"),
                    ("ios", "native", "library"),
                    ("web", "wasm", "library"),
                )
            ],
        }
        original = copy.deepcopy(metadata)
        runtime.add_android_capability(metadata, runtime.VERSION, "components-resources")
        self.assertEqual(metadata, original)
        runtime.add_android_capability(metadata, runtime.VERSION, "components-resources-android")
        for before, after in zip(original["variants"], metadata["variants"]):
            if before["attributes"]["org.jetbrains.kotlin.platform.type"] == "androidJvm":
                self.assertEqual(after["capabilities"], [
                    dict(group=runtime.GROUP, name="components-resources-android", version=runtime.VERSION),
                    dict(group=runtime.GROUP, name=runtime.CAPABILITY, version=runtime.VERSION),
                ])
                without_capabilities = {k: v for k, v in after.items() if k != "capabilities"}
                self.assertEqual(before, without_capabilities)
            else:
                self.assertEqual(before, after)
        once = copy.deepcopy(metadata)
        runtime.add_android_capability(metadata, runtime.VERSION, "components-resources-android")
        self.assertEqual(metadata, once)

        leaf = copy.deepcopy(original)
        runtime.add_android_capability(leaf, runtime.VERSION, "components-resources-android")
        self.assertEqual(leaf["variants"][0]["capabilities"][0]["name"], "components-resources-android")

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
