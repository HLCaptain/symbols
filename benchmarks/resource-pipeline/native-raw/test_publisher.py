"""Generator contract checks; real compilation/publication is a separate gate."""
import argparse
from pathlib import Path
import tempfile
import unittest
import zipfile

import publisher
import run


class PublisherTest(unittest.TestCase):
    def test_runtime_republication_invalidates_resume_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            manifest = dict(coordinates="example:pack:1", prototype_backend="native", runtime_version="2")
            coordinates = [("example", name, "1") for name in ("pack", "pack-android", "pack-jvm")]
            coordinates += [("org.jetbrains.compose.components", name, "2") for name in ("components-resources", "components-resources-android")]
            for group, module, version in coordinates:
                artifact = repository / group.replace(".", "/") / module / version / "artifact"
                artifact.parent.mkdir(parents=True)
                artifact.write_bytes(b"original")
            before = run.publication_hash(repository, manifest)
            artifact.write_bytes(b"changed runtime")
            self.assertNotEqual(before, run.publication_hash(repository, manifest))

    def test_preserves_getters_and_separates_platform_locations(self):
        icons = publisher.load_usage().catalog(publisher.ROOT / "fonts/material/MaterialSymbols.codepoints")[:2]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sources, aar = root / "sources.jar", root / "pack.aar"
            res = f'package {publisher.PACKAGE}\nobject Res {{ object drawable; fun getUri(path: String) = "{publisher.PREFIX}" + path; fun readBytes(path: String) = "{publisher.PREFIX}" + path }}'
            body = f'package {publisher.PACKAGE}\nprivate const val MD = "{publisher.PREFIX}"\n'
            for icon in icons:
                name = icon["resource"]
                body += f'''@delegate:ResourceContentHash(1)
public val Res.drawable.{name}: DrawableResource by lazy {{
      DrawableResource("drawable:{name}", setOf(
        ResourceItem(setOf(), "${{MD}}drawable/{name}.xml", -1, -1),
      ))
    }}
'''
            body += 'internal fun _collectCommonMainDrawable0Resources(map: MutableMap<String, DrawableResource>) {}'
            body = body.replace(f"{icons[1]['resource']}: DrawableResource by lazy", f"{icons[1]['resource']}: DrawableResource by\n    lazy")
            package = publisher.PACKAGE.replace(".", "/")
            with zipfile.ZipFile(sources, "w") as archive:
                archive.writestr(f"commonMain/{package}/Res.kt", res)
                archive.writestr(f"commonMain/{package}/Drawable0.commonMain.kt", body)
                archive.writestr(f"androidMain/{package}/ActualResourceCollectors.kt", "  _collectCommonMainDrawable0Resources(map)\n")
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr("META-INF/LICENSE", "original license")
                for icon in icons:
                    archive.writestr(f'assets/{publisher.PREFIX}drawable/{icon["resource"]}.xml', "<vector/>")
            destination = root / "native"
            publisher.prepare(argparse.Namespace(output=destination, repository=root / "maven", aar=aar,
                                                sources=sources, limit=2, backend="native", version="0.0.0-test"))
            common = destination / "src/commonMain/kotlin" / package
            getters = (common / "Drawable0.commonMain.kt").read_text()
            android = (destination / "src/androidMain/kotlin" / package / "NativeResourcePaths.kt").read_text()
            jvm = (destination / "src/jvmMain/kotlin" / package / "NativeResourcePaths.kt").read_text()
            for icon in icons:
                name = icon["resource"]
                self.assertIn(f"public val Res.drawable.{name}: DrawableResource by", getters)
                self.assertIn(f"nativePath_{name}()", getters)
                self.assertIn(f"R.raw.symbols_probe_{name}", android)
                self.assertIn(f"{publisher.PREFIX}drawable/{name}.xml", jvm)
                self.assertEqual((destination / f"src/androidMain/res/raw/symbols_probe_{name}.xml").read_bytes(), b"<vector/>")
            self.assertEqual((common / "Res.kt").read_text().count("platformResourcePath(path)"), 2)
            self.assertFalse((destination / "src/androidMain/assets").exists())
            for target in ("androidMain", "jvmMain"):
                self.assertEqual((destination / f"src/{target}/resources/META-INF/LICENSE").read_text(), "original license")
            with self.assertRaisesRegex(ValueError, "Unsafe generated path"):
                publisher.write(destination, "../escape.kt", "invalid")


if __name__ == "__main__":
    unittest.main()
