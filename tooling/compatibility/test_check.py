import importlib.util
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest


spec = importlib.util.spec_from_file_location("compatibility_check", Path(__file__).with_name("check.py"))
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class CompatibilityTest(unittest.TestCase):
    def arguments(self, directory, drawables_only):
        return SimpleNamespace(profile="kmp", version="0.0.0-test", repository=directory / "maven",
                               kotlin_version="2.4.20", agp_version="9.4.1", compile_sdk=37,
                               compose_version="1.11.1" if drawables_only else "1.12.1",
                               compose_resources=True, compose_drawables_only=drawables_only)

    def test_drawable_fixture_does_not_upgrade_runtime_through_font_library(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            args = self.arguments(directory, True)
            check.fixture(args, directory)
            consumer = check.published_kmp_consumer(args, directory)
            producer_build = (directory / "build.gradle.kts").read_text()
            self.assertIn('id("org.jetbrains.compose") version "1.11.1"', producer_build)
            self.assertIn('api("org.jetbrains.compose.components:components-resources:1.11.1")', producer_build)
            for project in (directory, consumer):
                self.assertNotIn("symbols-variant-font-core", (project / "build.gradle.kts").read_text())
            self.assertFalse((directory / "fixture-fonts").exists())
            common = (directory / "src/commonMain/kotlin/example/ResourceConsumer.kt").read_text()
            self.assertIn("fun drawableDescriptor() = Res.drawable.fixture_icons_outlined_check", common)
            self.assertIn("org.jetbrains.compose.resources.painterResource", common)
            self.assertIn("ResourceConsumerKt.drawableDescriptor()", (consumer / "src/main/java/example/ConsumerActivity.java").read_text())

    def test_existing_font_fixture_retains_descriptor_and_font_payload(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            args = self.arguments(directory, False)
            check.fixture(args, directory)
            consumer = check.published_kmp_consumer(args, directory)
            for project in (directory, consumer):
                self.assertIn("symbols-variant-font-core:0.0.0-test", (project / "build.gradle.kts").read_text())
            source_font = check.ROOT / "samples/custom-static/src/commonMain/composeResources/font/powerline_symbols.otf"
            self.assertEqual((directory / "fixture-fonts/powerline_symbols.otf").read_bytes(), source_font.read_bytes())
            self.assertIn("Res.symbolFonts.powerline_symbols", (directory / "src/commonMain/kotlin/example/ResourceConsumer.kt").read_text())

    def test_resolved_runtime_rejects_silent_upgrades_and_missing_artifacts(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / "runtime.txt"
            for android in (False, True):
                prefix, suffix, version = ("androidx.compose", "-android", "1.11.2") if android else ("org.jetbrains.compose", "-desktop", "1.11.1")
                modules = [f"org.jetbrains.compose.components:components-resources{suffix}:1.11.1"]
                modules += [f"{prefix}.{name}:{name}{suffix}:{version}" for name in ("ui", "foundation", "runtime")]
                if not android:
                    modules.append("androidx.compose.runtime:runtime-desktop:1.11.2")
                report.write_text("\n".join(modules) + "\n")
                self.assertEqual(len(check.verify_compose_runtime(report, "1.11.1", android)), len(modules))
                for index in (0, 1, len(modules) - 1):
                    upgraded = modules.copy()
                    upgraded[index] = upgraded[index].rsplit(":", 1)[0] + ":1.12.1"
                    report.write_text("\n".join(upgraded) + "\n")
                    with self.assertRaisesRegex(ValueError, "Compose runtime mismatch"):
                        check.verify_compose_runtime(report, "1.11.1", android)
                report.write_text("\n".join(modules[:-1]) + "\n")
                with self.assertRaisesRegex(ValueError, "no matching artifact"):
                    check.verify_compose_runtime(report, "1.11.1", android)
                report.write_text("\n".join(modules) + "\n")
                with self.assertRaisesRegex(ValueError, "No verified AndroidX artifact mapping"):
                    check.verify_compose_runtime(report, "1.99.0", android)


if __name__ == "__main__":
    unittest.main()
