import argparse
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

import contracts
import publisher


class ContractsTest(unittest.TestCase):
    def test_distinct_physical_variants_and_reader_contract(self):
        icon = publisher.load_usage().catalog(publisher.ROOT / "fonts/material/MaterialSymbols.codepoints")[0]
        name, package = icon["resource"], publisher.PACKAGE.replace(".", "/")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sources, aar = root / "sources.jar", root / "pack.aar"
            with zipfile.ZipFile(sources, "w") as archive:
                archive.writestr(f"commonMain/{package}/Res.kt", f'package {publisher.PACKAGE}\nobject Res {{ object drawable; fun getUri(path: String) = "{publisher.PREFIX}" + path; fun readBytes(path: String) = "{publisher.PREFIX}" + path }}')
                archive.writestr(f"commonMain/{package}/Drawable0.commonMain.kt", f'''package {publisher.PACKAGE}
@delegate:ResourceContentHash(1)
public val Res.drawable.{name}: DrawableResource by lazy {{
      DrawableResource("drawable:{name}", setOf(
        ResourceItem(setOf(), "${{MD}}drawable/{name}.xml", -1, -1),
      ))
    }}
internal fun _collectCommonMainDrawable0Resources(map: MutableMap<String, DrawableResource>) {{}}
''')
                archive.writestr(f"androidMain/{package}/ActualResourceCollectors.kt", "  _collectCommonMainDrawable0Resources(map)\n")
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr(f"assets/{publisher.PREFIX}drawable/{name}.xml", '<vector xmlns:android="http://schemas.android.com/apk/res/android"><path android:fillColor="#FF000000"/></vector>')
            base = root / "base"
            publisher.prepare(argparse.Namespace(output=base, repository=root / "maven", aar=aar,
                                                sources=sources, limit=1, backend="native", version="0.0.0-base"))
            output = root / "contracts"
            report = contracts.prepare(argparse.Namespace(publisher=base, repository=root / "maven", output=output, version="0.0.0-contract"))
            variants = report["variants"]
            self.assertNotEqual(variants[0]["xml_sha256"], variants[1]["xml_sha256"])
            self.assertEqual(len({v["raw_resource"] for v in variants}), 2)
            for variant in variants:
                raw = output / f'publisher/src/androidMain/res/raw/{variant["raw_resource"]}.xml'
                ET.fromstring(raw.read_bytes())
            getter = (output / f"publisher/src/commonMain/kotlin/{package}/Drawable0.commonMain.kt").read_text()
            self.assertIn("ThemeQualifier.LIGHT", getter)
            self.assertIn("ThemeQualifier.DARK", getter)
            self.assertEqual(getter.count(f"public val Res.drawable.{name}:"), 1)
            self.assertNotIn("ResourceContentHash(1)", getter)
            android = (output / f"publisher/src/androidMain/kotlin/{package}/NativeResourcePaths.kt").read_text()
            for variant in variants:
                self.assertIn(f'R.raw.{variant["raw_resource"]}', android)
            fixture = json.loads((output / "consumer/fixture.json").read_text())
            self.assertEqual(set(fixture["native_raw"]["expected_resources"]), {v["raw_resource"] for v in variants})
            activity = (output / "consumer/src/main/kotlin/study/MainActivity.kt").read_text()
            self.assertNotIn(") includeVersion(", (output / "consumer/settings.gradle.kts").read_text())
            self.assertNotIn("@ICON@", activity)
            self.assertNotIn("setResourceReaderAndroidContext", activity)
            self.assertIn("reader.readPart", activity)
            self.assertIn("LocalConfiguration provides configuration", activity)
            self.assertIn("PRE_CONTEXT_DESCRIPTOR_OK", activity)
            with self.assertRaisesRegex(ValueError, "Refusing to overwrite"):
                contracts.prepare(argparse.Namespace(publisher=base, repository=root / "maven", output=output, version="0.0.0-contract"))


if __name__ == "__main__":
    unittest.main()
