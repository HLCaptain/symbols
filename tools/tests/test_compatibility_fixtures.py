import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("compatibility_check", ROOT / "tooling/compatibility/check.py")
compatibility = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(compatibility)


class CompatibilityFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.scratch = ROOT / "build/compatibility-fixture-tests"
        cls.scratch.mkdir(parents=True, exist_ok=True)

    def fixture(self, directory, profile, built_in_vectors=False, **overrides):
        args = SimpleNamespace(
            profile=profile,
            built_in_vectors=built_in_vectors,
            compose_resources=False,
            version="0.0.0-consumer-test",
            repository=directory / "maven",
            kotlin_version="2.4.20",
            compose_version="1.12.1",
            agp_version="9.4.1",
            compile_sdk=37,
        )
        args.__dict__.update(overrides)
        compatibility.fixture(args, directory)
        return (directory / "build.gradle.kts").read_text()

    def test_jvm_builtin_consumer_uses_published_vectors_and_public_api_tests(self):
        with TemporaryDirectory(dir=self.scratch) as temporary:
            directory = Path(temporary)
            build = self.fixture(directory, "jvm", built_in_vectors=True)
            self.assertIn("symbols-material-vectors-rounded:0.0.0-consumer-test", build)
            self.assertIn("symbols-material-vectors-themed:0.0.0-consumer-test", build)
            self.assertIn('testImplementation(kotlin("test-junit"))', build)
            self.assertIn('tasks.test { useJUnit() }', build)
            self.assertTrue((directory / "src/main/kotlin/example/BuiltInVectors.kt").is_file())
            test = (directory / "src/test/kotlin/MaterialSymbolsThemedVectorsTest.kt").read_text()
            self.assertIn("mirroredThemedPropertyTracksStyleChangesWithoutChangingNormalVectors", test)

    def test_minimum_android_consumer_keeps_vectors_reachable_and_sdk_floor(self):
        with TemporaryDirectory(dir=self.scratch) as temporary:
            directory = Path(temporary)
            build = self.fixture(directory, "android-app", built_in_vectors=True, agp_version="9.1.1")
            self.assertIn('id("com.android.application") version "9.1.1"', build)
            self.assertIn("compileSdk = 37; defaultConfig { minSdk = 23 }", build)
            activity = (directory / "src/main/java/example/ConsumerActivity.java").read_text()
            self.assertIn("BuiltInVectorsKt.builtInVectorSummary()", activity)

    def test_legacy_java_consumer_keeps_its_xml_only_contract(self):
        with TemporaryDirectory(dir=self.scratch) as temporary:
            directory = Path(temporary)
            build = self.fixture(directory, "android-java", agp_version="8.13.2", compile_sdk=36)
            self.assertIn("compileSdk = 36; defaultConfig { minSdk = 21 }", build)
            self.assertIn("symbols-material-drawables-outlined", build)
            self.assertNotIn("symbols-material-vectors", build)
            self.assertNotIn("org.jetbrains.kotlin", build)
            self.assertNotIn("org.jetbrains.compose", build)

    def test_ios_consumer_links_both_platforms_without_generator_or_os_override(self):
        with TemporaryDirectory(dir=self.scratch) as temporary:
            directory = Path(temporary)
            build = self.fixture(directory, "ios", built_in_vectors=True)
            self.assertIn("listOf(iosArm64(), iosSimulatorArm64())", build)
            self.assertIn('baseName = "SymbolsConsumer"', build)
            self.assertIn("symbols-material-vectors-themed", build)
            self.assertNotIn("io.github.hlcaptain.symbol-fonts", build)
            self.assertNotIn("override-konan-properties", build)
            self.assertFalse((directory / "src/commonMain/kotlin/example/Consumer.kt").exists())
            source = (directory / "src/commonMain/kotlin/example/BuiltInVectors.kt").read_text()
            self.assertIn("fun builtInVectorSummary(): String", source)
            self.assertIn("Symbols.Material.AutoMirrored.Themed.ArrowBack", source)
            self.assertIn("Symbols.Material.AutoMirrored.Rounded.RoundedArrowBack", source)


if __name__ == "__main__":
    unittest.main()
