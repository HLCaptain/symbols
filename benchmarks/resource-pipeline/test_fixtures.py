import importlib.util
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
import zipfile
from unittest.mock import patch


def load(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


fixtures = load("fixtures")
retention = load("retention")
usage = load("usage")
ROOT = Path(os.environ.get("SYMBOLS_SOURCE_ROOT", fixtures.ROOT))


class FixtureTest(unittest.TestCase):
    def test_preserves_only_original_aar_notices_without_copying_resources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            aar, fixture = root / "library.aar", root / "consumer"
            notices = {"META-INF/material-drawables-outlined/LICENSE": b"license\n",
                       "META-INF/material-drawables-outlined/THIRD_PARTY_NOTICES.md": b"notices\n"}
            with zipfile.ZipFile(aar, "w") as archive:
                for name, data in notices.items():
                    archive.writestr(name, data)
                archive.writestr("res/drawable/icon.xml", "<vector/>")
                archive.writestr("META-INF/unrelated.txt", "unrelated")
            result = fixtures.preserve_aar_notices(aar, fixture)
            self.assertEqual(result["aar_sha256"], hashlib.sha256(aar.read_bytes()).hexdigest())
            self.assertEqual(set(result["entries"]), set(notices))
            self.assertEqual({p.relative_to(fixture / "src/main/resources").as_posix()
                              for p in fixture.rglob("*") if p.is_file()}, set(notices))
            for name, data in notices.items():
                self.assertEqual((fixture / "src/main/resources" / name).read_bytes(), data)
                self.assertEqual(result["entries"][name], {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr("META-INF/../LICENSE", "unsafe")
            with self.assertRaisesRegex(ValueError, "Unsafe or duplicate"):
                fixtures.preserve_aar_notices(aar, root / "invalid")
            self.assertFalse((root / "invalid").exists())

    def test_real_catalog_selection_has_unique_glyphs_and_known_api_names(self):
        icons = fixtures.catalog(ROOT / "fonts/material/MaterialSymbols.codepoints")
        self.assertEqual(len(icons), 3802)
        self.assertEqual(len({icon["codepoint"] for icon in icons}), len(icons))
        self.assertEqual(icons[0], dict(name="10k", codepoint=0xE951, identifier="_10k", resource="material_symbols_outlined_10k_ue951"))
        home = next(icon for icon in icons if icon["name"] == "home")
        self.assertEqual(home["resource"], "material_symbols_outlined_home_ue9b2")
        for count in (0, 1, 25, 200, len(icons)):
            for backend in fixtures.BACKENDS:
                source = fixtures.renderer(backend, "direct", icons[:count])
                self.assertEqual(source.count(" -> rememberVectorPainter(") + source.count(" -> painterResource("), count)
                self.assertNotIn("allDrawableResources", source)
                self.assertNotIn("asOutlinedImageVector", source)

    def test_intermediate_cleanup_preserves_apks_mapping_and_sources(self):
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            for name in ("build/intermediates/a", "build/kotlin/a", "build/tmp/a", "build/generated/a", "build/outputs/apk/a.apk", "build/outputs/mapping/resources.txt", "src/main/a", "usage-build.log"):
                path = fixture / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("data")
            usage.discard_intermediates(fixture)
            for name in ("intermediates", "kotlin", "tmp", "generated"):
                self.assertFalse((fixture / "build" / name).exists())
            for name in ("build/outputs/apk/a.apk", "build/outputs/mapping/resources.txt", "src/main/a", "usage-build.log"):
                self.assertTrue((fixture / name).is_file())

    def test_resume_rejects_changed_sources_or_apks(self):
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            for name in ("fixture.json", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle/wrapper/gradle-wrapper.properties", "gradle/wrapper/gradle-wrapper.jar", "src/main/kotlin/App.kt"):
                path = fixture / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("input")
            apk = fixture / "test.apk"
            apk.write_bytes(b"original APK")
            row = dict(success=True, input_sha256=usage.inputs_hash(fixture), apks=[dict(apk=str(apk), sha256=usage.sha(apk))])
            self.assertTrue(usage.reusable(row, fixture))
            (fixture / "src/main/kotlin/App.kt").write_text("changed")
            self.assertFalse(usage.reusable(row, fixture))
            (fixture / "src/main/kotlin/App.kt").write_text("input")
            apk.write_bytes(b"changed APK")
            self.assertFalse(usage.reusable(row, fixture))

    def test_dynamic_uses_published_lookup_apis(self):
        self.assertIn("Symbols.Material.all.associateBy", fixtures.renderer("vectors", "dynamic", []))
        icon = dict(resource="material_symbols_outlined_home_ue9b2")
        native = fixtures.renderer("android", "dynamic", [icon])
        self.assertIn('catalog["material_symbols_outlined_home_ue9b2"] = SymbolsR.drawable.material_symbols_outlined_home_ue9b2', native)
        self.assertIn("painterResource(drawables.getValue(resource))", native)
        self.assertIn("Res.allDrawableResources.getValue", fixtures.renderer("compose", "dynamic", []))

    def test_retention_asserts_used_resources_and_reports_unused_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            used, unused = "material_symbols_outlined_used_ue001", "material_symbols_outlined_unused_ue002"
            manifest = dict(backend="compose", access="direct", count=1, selected=[dict(resource=used)], all_resources=[used, unused])
            (root / "fixture.json").write_text(json.dumps(manifest))
            apk = root / "fixture.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                for name in (used, unused):
                    archive.writestr(f"assets/composeResources/example/drawable/{name}.xml", "<vector/>")
            result = retention.analyze(root, apk)
            self.assertEqual(result["retained_selected"], 1)
            self.assertEqual(result["retained_unused"], 1)
            manifest["backend"] = "android"
            (root / "fixture.json").write_text(json.dumps(manifest))
            with patch.object(retention.subprocess, "check_output", return_value=f"resource 0x7f010000 drawable/{used}\n () (file) res/a.xml"):
                result = retention.analyze(root, apk, "aapt2")
            self.assertEqual(result["retained_unused"], 0)
            with patch.object(retention.subprocess, "check_output", return_value=""):
                with self.assertRaisesRegex(ValueError, "Used icon resources missing"):
                    retention.analyze(root, apk, "aapt2")


if __name__ == "__main__":
    unittest.main()
