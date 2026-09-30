#!/usr/bin/env python3
"""Exercise the real Symbols opt-in with local upstream contribution candidates.

These are proposal tests, not evidence of support in an official Compose release.
Build the reader and generator candidates first; no dependency is substituted.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
from types import SimpleNamespace
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[4]
spec = importlib.util.spec_from_file_location("compatibility_check", ROOT / "tooling/compatibility/check.py")
compatibility = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compatibility)


def snapshot(directory):
    return {str(path.relative_to(directory)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in directory.rglob("*.kt")}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--upstream-repository", type=Path, required=True)
    parser.add_argument("--symbols-version", required=True)
    parser.add_argument("--baseline", choices=sorted(compatibility.COMPOSE_ANDROIDX_VERSIONS), required=True)
    parser.add_argument("--plugin-version", required=True)
    parser.add_argument("--runtime-version", required=True)
    parser.add_argument("--upgrade-consumer-to", choices=sorted(compatibility.COMPOSE_ANDROIDX_VERSIONS),
                        help="Also consume the unchanged published pack with this Compose baseline")
    parser.add_argument("--upgrade-runtime-to", help="Resources runtime candidate for the upgraded consumer")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--gradle", type=Path, default=ROOT / "gradlew")
    args = parser.parse_args()
    if bool(args.upgrade_consumer_to) != bool(args.upgrade_runtime_to):
        parser.error("--upgrade-consumer-to and --upgrade-runtime-to must be supplied together")
    output = args.output.resolve()
    if output.exists():
        parser.error("Use a fresh output directory to preserve publication and validation evidence")
    versions = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())["versions"]
    fixture_args = SimpleNamespace(
        profile="kmp", compose_resources=True, compose_drawables_only=True,
        repository=args.repository.resolve(), version=args.symbols_version,
        kotlin_version=versions["kotlin"], agp_version=versions["agp"],
        compile_sdk=int(versions["android-compileSdk"]), compose_version=args.baseline,
    )
    producer = output / "producer"
    compatibility.fixture(fixture_args, producer)
    settings = producer / "settings.gradle.kts"
    settings.write_text(settings.read_text().replace("google()", "maven { url = uri(" +
        json.dumps(args.upstream_repository.resolve().as_uri()) + ") }\n google()"))
    build = producer / "build.gradle.kts"
    base_build = build.read_text().replace(
        f'id("org.jetbrains.compose") version "{args.baseline}"',
        f'id("org.jetbrains.compose") version "{args.plugin_version}"',
    ).replace(
        f'components-resources:{args.baseline}', f'components-resources:{args.runtime_version}',
    )
    authored = producer / "src/commonMain/composeResources/files/unrelated.txt"
    authored.parent.mkdir(parents=True)
    authored.write_text("Unrelated resources must survive native XML packaging.\n")
    authored_xml = producer / "src/commonMain/composeResources/drawable/authored.xml"
    authored_xml.parent.mkdir(parents=True)
    authored_xml.write_text('''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000" android:pathData="M1,1L3,1L3,3Z"/>
</vector>''')
    owned_prefix = "assets/composeResources/example.resources/drawable/fixture_icons_"
    results = []

    def run(project, stage, tasks, *, reuse=False, build_cache=False):
        command = [str(args.gradle.resolve()), "-p", str(project), *tasks,
                   "--no-daemon", "--max-workers=1", "--configuration-cache",
                   "--build-cache" if build_cache else "--no-build-cache", "--console=plain", "--stacktrace"]
        log = project / f"{stage}.log"
        with log.open("w") as stream:
            result = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT)
        if result.returncode:
            raise SystemExit(f"Failed {stage}; see {log}")
        if reuse and "Configuration cache entry reused." not in log.read_text():
            raise SystemExit(f"Configuration cache not reused; see {log}")
        results.append(dict(stage=stage, command=command, log=str(log.relative_to(output))))
        print(f"Passed {stage}", flush=True)
        return log

    def select(enabled):
        build.write_text(base_build + "\nsymbolFonts.experimentalComposeResourcePruning.set(" +
                         str(enabled).lower() + ")\n")

    def producer_archive():
        return next((producer / "build/outputs/aar").glob("*.aar"))

    def jvm_resources():
        jar = next((producer / "build/libs").glob("*-jvm-1.0.jar"))
        return {name: data for name, data in archive_entries(jar).items()
                if name.startswith("composeResources/")}

    def archive_entries(path):
        with zipfile.ZipFile(path) as archive:
            return {name: archive.read(name) for name in archive.namelist() if not name.endswith("/")}

    prefix = "assets/composeResources/example.resources/"
    select(False)
    run(producer, "disabled", ["assemble", "reportComposeRuntime"])
    generated_kotlin = producer / "build/generated/compose/resourceGenerator/kotlin"
    before_sources = snapshot(generated_kotlin)
    assert before_sources, "Expected real Compose-generated sources"
    before = archive_entries(producer_archive())
    before_jvm = jvm_resources()
    assert before_jvm, "Expected JVM resource packaging"
    xml = {name.removeprefix(prefix): data for name, data in before.items()
           if name.startswith(owned_prefix) and name.endswith(".xml")}
    assert len(xml) == 2, xml.keys()
    for enabled, stage in ((True, "enabled"), (False, "disabled-again")):
        select(enabled)
        log = run(producer, stage, ["assemble", "reportComposeRuntime"])
        assert ":generateFixtureIconsOutlinedSymbolFonts UP-TO-DATE" in log.read_text(), \
            "Changing packaging regenerated unchanged icon geometry"
        entries = archive_entries(producer_archive())
        assert jvm_resources() == before_jvm, "Android pruning changed JVM resources"
        assert entries[prefix + "files/unrelated.txt"] == authored.read_bytes()
        assert entries[prefix + "drawable/authored.xml"] == authored_xml.read_bytes()
        if enabled:
            assert not any(name.startswith(owned_prefix) for name in entries)
            raw = [data for name, data in entries.items() if name.startswith("res/raw/")]
            assert sorted(raw) == sorted(xml.values()), "Native XML bytes differ"
        else:
            assert {name: data for name, data in entries.items() if name.startswith(prefix)} == {
                name: data for name, data in before.items() if name.startswith(prefix)}
            assert not any(name.startswith("res/raw/") for name in entries)
            assert snapshot(generated_kotlin) == before_sources
    select(True)
    publish_tasks = ["assemble", "publishAllPublicationsToFixtureRepository", "reportComposeRuntime"]
    run(producer, "publish-enabled", publish_tasks)
    run(producer, "reuse-enabled", publish_tasks, reuse=True)
    runtime_reports = {"producer-jvm": compatibility.verify_compose_runtime(
        producer / "build/reports/compose-runtime.txt", args.baseline, False)}
    run(producer, "cache-seed", ["clean", "assemble"], build_cache=True)
    restored_log = run(producer, "cache-restore", ["clean", "assemble"], reuse=True, build_cache=True)
    # Stock accessor tasks deliberately disable build caching for IDE imports.
    # Require restoration of the new native tasks, without changing that policy.
    for task in ("prepareNativeAndroidXmlForCommonMain", "generateNativeXmlLocationsForAndroidMain",
                 "generateNativeXmlLocationsForJvmMain"):
        assert f":{task} FROM-CACHE" in restored_log.read_text(), f"Expected cache restoration of {task}"
    assert jvm_resources() == before_jvm
    restored = archive_entries(producer_archive())
    assert sorted(data for name, data in restored.items() if name.startswith("res/raw/")) == sorted(xml.values())
    assert not any(name.startswith(owned_prefix) for name in restored)

    consumer = compatibility.published_kmp_consumer(fixture_args, producer)
    consumer_build = consumer / "build.gradle.kts"
    consumer_build.write_text(consumer_build.read_text().replace("plugins {", f'''plugins {{
            kotlin("jvm") version "{versions['kotlin']}" apply false
            id("org.jetbrains.kotlin.plugin.compose") version "{versions['kotlin']}"
        ''') + f'''
android.buildFeatures.compose = true
android.defaultConfig.applicationId = "io.github.hlcaptain.symbols.nativeproposal"
android.defaultConfig.versionCode = 1
android.defaultConfig.versionName = "1.0"
android.buildTypes.named("release") {{
    signingConfig = android.signingConfigs.getByName("debug")
}}
dependencies {{ implementation("androidx.activity:activity:{versions['androidx-activity']}") }}
''')
    (consumer / "src/main/java/example/ConsumerActivity.java").unlink()
    (consumer / "src/main/AndroidManifest.xml").write_text('''
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Native XML proposal">
        <activity android:name="example.ConsumerActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
''')
    activity = consumer / "src/main/kotlin/example/ConsumerActivity.kt"
    activity.parent.mkdir(parents=True)
    activity.write_text('''package example
class ConsumerActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(state: android.os.Bundle?) {
        super.onCreate(state)
        setContentView(androidx.compose.ui.platform.ComposeView(this).apply {
            setContent { ResourcePreview() }
        })
    }
}
''')
    tasks = ["assembleRelease", "bundleRelease", "reportComposeRuntime"]
    run(consumer, "consumer", tasks)
    run(consumer, "consumer-reuse", tasks, reuse=True)
    runtime_reports["consumer-android"] = compatibility.verify_compose_runtime(
        consumer / "build/reports/compose-runtime.txt", args.baseline, True,
        resources_version=args.runtime_version)
    def checked_archives(project):
        archives = [next((project / "build/outputs/apk/release").glob("*.apk")),
                    next((project / "build/outputs/bundle/release").glob("*.aab"))]
        for archive in archives:
            entries = archive_entries(archive)
            values = list(entries.values())
            assert values.count(xml["drawable/fixture_icons_outlined_check.xml"]) == 1, archive
            assert xml["drawable/fixture_icons_outlined_unused.xml"] not in values, archive
            assert authored.read_bytes() in values, archive
            assert authored_xml.read_bytes() in values, archive
            assert not any(owned_prefix in name for name in entries), archive
        return archives

    archives = checked_archives(consumer)
    upgrade = None
    if args.upgrade_consumer_to:
        published_aars = list((producer / "published").rglob("*.aar"))
        assert len(published_aars) == 1, published_aars
        unchanged_aars = {
            path: hashlib.sha256(path.read_bytes()).hexdigest()
            for path in [producer_archive(), *published_aars]
        }
        upgraded = consumer.with_name("upgraded-consumer")
        upgraded.mkdir()
        shutil.copytree(consumer / "src", upgraded / "src")
        for name in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties"):
            shutil.copy2(consumer / name, upgraded / name)
        upgraded_build = upgraded / "build.gradle.kts"
        upgraded_build.write_text(upgraded_build.read_text().replace(
            f'org.jetbrains.compose.ui:ui:{args.baseline}',
            f'org.jetbrains.compose.ui:ui:{args.upgrade_consumer_to}',
        ) + f'''\ndependencies {{
    implementation("org.jetbrains.compose.foundation:foundation:{args.upgrade_consumer_to}")
    implementation("org.jetbrains.compose.components:components-resources:{args.upgrade_runtime_to}")
}}\n''')
        run(upgraded, "upgraded-consumer", tasks)
        run(upgraded, "upgraded-consumer-reuse", tasks, reuse=True)
        runtime_reports["upgraded-consumer-android"] = compatibility.verify_compose_runtime(
            upgraded / "build/reports/compose-runtime.txt", args.upgrade_consumer_to, True,
            resources_version=args.upgrade_runtime_to)
        archives += checked_archives(upgraded)
        for path, digest in unchanged_aars.items():
            assert hashlib.sha256(path.read_bytes()).hexdigest() == digest, \
                f"Consumer upgrade changed the original resource pack: {path}"
        upgrade = dict(baseline=args.upgrade_consumer_to, runtime_candidate=args.upgrade_runtime_to,
                       producer_rebuilt=False,
                       unchanged_aars=[dict(path=str(path.relative_to(output)), sha256=digest)
                                       for path, digest in unchanged_aars.items()])
    report = dict(status="Local upstream proposal verified; not an official Compose release",
                  baseline=args.baseline, generator_candidate=args.plugin_version,
                  runtime_candidate=args.runtime_version, symbols_version=args.symbols_version,
                  transitions="false -> true -> false -> true",
                  resolved_runtime=runtime_reports, stages=results,
                  archives=[dict(path=str(path.relative_to(output)), bytes=path.stat().st_size,
                                 sha256=hashlib.sha256(path.read_bytes()).hexdigest()) for path in archives])
    if upgrade is not None:
        report["consumer_upgrade"] = upgrade
    (output / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
