import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile


spec = importlib.util.spec_from_file_location("verify", Path(__file__).with_name("verify.py"))
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)


class VerifyTest(unittest.TestCase):
    def test_publication_parity_uses_stock_bytes_and_embedded_notices(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = [root / name for name in ("stock.aar", "native.aar", "native.jar", "control.aar", "control.jar")]
            prefixes = ["assets/" + verify.DRAWABLE_ASSETS, "res/raw/" + verify.PREFIX,
                        verify.DRAWABLE_ASSETS, "assets/" + verify.DRAWABLE_ASSETS, verify.DRAWABLE_ASSETS]
            notice = "META-INF/outlined/LICENSE"

            def write_archive(index, changed=False, include_notice=True):
                with zipfile.ZipFile(paths[index], "w") as archive:
                    archive.writestr(prefixes[index], "")  # Directory entries are not resource files.
                    for name in ("first", "second"):
                        archive.writestr(prefixes[index] + name + ".xml", "changed" if changed else name)
                    if include_notice:
                        if index in (1, 3):
                            nested = io.BytesIO()
                            with zipfile.ZipFile(nested, "w") as classes:
                                classes.writestr(notice, "original license")
                            archive.writestr("classes.jar", nested.getvalue())
                        else:
                            archive.writestr(notice, "original license")

            for index in range(5):
                write_archive(index)
            result = verify.publication_bytes(*paths)
            self.assertEqual(result["stock"]["android_asset_files"], 2)
            self.assertEqual(result["stock"]["xml_files"], 2)
            for record in result["publications"].values():
                self.assertEqual(record["xml_inventory_sha256"], result["stock"]["xml_inventory_sha256"])
                self.assertEqual(record["different_xml_files"], 0)
            write_archive(4, changed=True)
            with self.assertRaisesRegex(ValueError, "XML bytes differ"):
                verify.publication_bytes(*paths)
            write_archive(4, include_notice=False)
            with self.assertRaisesRegex(ValueError, "notice missing or changed"):
                verify.publication_bytes(*paths)

    def test_exact_retention_and_raw_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            used, unused = "symbols_probe_used", "symbols_probe_unused"
            manifest = {"native_raw": {
                "expected_resources": [used],
                "all_resources": [used, unused],
                "forbidden_asset_prefix": "assets/composeResources/study/",
            }}
            (root / "fixture.json").write_text(json.dumps(manifest))
            aar, apk = root / "published.aar", root / "app.apk"
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr(f"res/raw/{used}.xml", "<vector used='true'/>")
                archive.writestr(f"res/raw/{unused}.xml", "<vector/>")

            def write_apk(data="<vector used='true'/>", duplicate=False):
                with zipfile.ZipFile(apk, "w") as archive:
                    archive.writestr("res/a.xml", data)
                    archive.writestr("assets/unrelated/file.txt", "retain unrelated assets")
                    if duplicate:
                        archive.writestr("assets/composeResources/study/drawable/icon.xml", data)

            dump = f"resource 0x7f010000 raw/{used}\n  () (file) res/a.xml type=STRING\n"
            write_apk()
            with patch.object(verify.subprocess, "check_output", return_value=dump):
                result = verify.analyze(root, apk, aar, "aapt2")
                self.assertEqual(result["retained_resource_count"], 1)
                self.assertEqual(result["retained_raw_bytes"], len("<vector used='true'/>"))
                self.assertTrue(result["verified"])
                write_apk("<vector corrupted='true'/>")
                with self.assertRaisesRegex(ValueError, "Raw bytes differ"):
                    verify.analyze(root, apk, aar, "aapt2")
                write_apk(duplicate=True)
                with self.assertRaisesRegex(ValueError, "still packages owned Compose assets"):
                    verify.analyze(root, apk, aar, "aapt2")

            write_apk()
            for invalid in ("", dump + f"resource 0x7f010001 raw/{unused}\n  () (file) res/b.xml type=STRING\n"):
                with patch.object(verify.subprocess, "check_output", return_value=invalid):
                    with self.assertRaisesRegex(ValueError, "Raw retention mismatch"):
                        verify.analyze(root, apk, aar, "aapt2")

            manifest["native_raw"]["expected_resources"] = []
            (root / "fixture.json").write_text(json.dumps(manifest))
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/unrelated/file.txt", "retain unrelated assets")
            with patch.object(verify.subprocess, "check_output", return_value=""):
                result = verify.analyze(root, apk, aar, "aapt2")
                self.assertEqual(result["retained_resource_count"], 0)
                self.assertEqual(result["retained_raw_bytes"], 0)

    def test_resource_table_preserves_qualifier_files_and_resets_between_types(self):
        dump = """resource 0x7f010000 raw/symbols_probe_day_night
  () (file) res/a.xml type=STRING
  (night) (file) res/b.xml type=STRING
resource 0x7f020000 drawable/not_owned
  () (file) res/c.xml type=XML
resource 0x7f010001 raw/unrelated
  () (file) res/d.xml type=STRING
"""
        self.assertEqual(verify.resource_files(dump), {"symbols_probe_day_night": ["res/a.xml", "res/b.xml"]})


if __name__ == "__main__":
    unittest.main()
