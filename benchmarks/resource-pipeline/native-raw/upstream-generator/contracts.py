#!/usr/bin/env python3
"""Local generator contracts: actual accessors, physical qualifiers, dynamic raw reads.

Builds a real producer/ordinary consumer; device execution is a separate explicit step.
No generated Kotlin is written or modified by this helper.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
from types import SimpleNamespace
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[4]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', type=Path, required=True)
    parser.add_argument('--upstream-repository', type=Path, required=True)
    parser.add_argument('--symbols-version', required=True)
    parser.add_argument('--plugin-version', default='1.12.2-native-xml05')
    parser.add_argument('--runtime-version', default='1.12.2-native-raw02')
    parser.add_argument('--gradle', type=Path, default=ROOT / 'gradlew')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root, output = ROOT, args.output.resolve()
    if output.exists():
        parser.error('Use a fresh output directory')
    native = root / 'benchmarks/resource-pipeline/native-raw'
    sys.path.insert(0, str(native))
    spec = importlib.util.spec_from_file_location('native_contract_template', native / 'contracts.py')
    contracts = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(contracts)
    spec = importlib.util.spec_from_file_location('compatibility', root / 'tooling/compatibility/check.py')
    compatibility = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(compatibility)
    versions = tomllib.loads((root / 'gradle/libs.versions.toml').read_text())['versions']
    fixture_args = SimpleNamespace(profile='kmp', compose_resources=True, compose_drawables_only=True,
                                  repository=args.repository.resolve(), version=args.symbols_version,
                                  kotlin_version=versions['kotlin'], agp_version=versions['agp'],
                                  compile_sdk=int(versions['android-compileSdk']), compose_version='1.12.1')
    producer = output / 'producer'
    compatibility.fixture(fixture_args, producer)
    settings = producer / 'settings.gradle.kts'
    settings.write_text(settings.read_text().replace('google()', 'maven { url = uri(' +
                        json.dumps(args.upstream_repository.resolve().as_uri()) + ') }\n google()'))
    build = producer / 'build.gradle.kts'
    build.write_text(build.read_text().replace('id("org.jetbrains.compose") version "1.12.1"',
                     f'id("org.jetbrains.compose") version "{args.plugin_version}"').replace(
                     'components-resources:1.12.1', f'components-resources:{args.runtime_version}') + '''
symbolFonts.experimentalComposeResourcePruning.set(true)
compose.resources.experimentalAndroidNativeXmlResources(
    "commonMain", provider { layout.projectDirectory.dir("src/commonMain/composeResources") }
)
''')
    variants = []
    for theme, color in [('LIGHT', '#FF000000'), ('DARK', '#FF0000FF')]:
        relative = f'drawable-{theme.lower()}/contract.xml'
        path = producer / 'src/commonMain/composeResources' / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="{color}" android:pathData="M5,12L9,16L19,6Z"/>
</vector>''')
        variants.append(dict(theme=theme, path=relative, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    stages = []

    def run(project, stage, tasks, reuse=False):
        command = [str(args.gradle.resolve()), '-p', str(project), *tasks, '--no-daemon', '--max-workers=1',
                   '--configuration-cache', '--no-build-cache', '--console=plain', '--stacktrace']
        log = project / f'{stage}.log'
        with log.open('w') as stream:
            status = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT)
        if status.returncode:
            raise SystemExit(f'Failed {stage}; see {log}')
        if reuse:
            assert 'Configuration cache entry reused.' in log.read_text(), log
        stages.append(dict(stage=stage, command=command, log=str(log.relative_to(output))))
        print('Passed ' + stage, flush=True)

    producer_tasks = ['assemble', 'publishAllPublicationsToFixtureRepository', 'reportComposeRuntime']
    run(producer, 'producer', producer_tasks)
    run(producer, 'producer-reuse', producer_tasks, True)
    runtime = {'producer-jvm': compatibility.verify_compose_runtime(
        producer / 'build/reports/compose-runtime.txt', '1.12.1', False)}
    aar = next((producer / 'published').rglob('*.aar'))
    with zipfile.ZipFile(aar) as archive:
        native_xml = {name: archive.read(name) for name in archive.namelist()
                      if name.startswith('res/raw/') and name.endswith('.xml')}
        assert len(native_xml) == 4, native_xml.keys()
        assert len(set(native_xml.values())) == 4, 'Byte-count retention checks require distinct fixture XMLs'
        assert not any(name.startswith('assets/composeResources/') and name.endswith('.xml')
                       for name in archive.namelist())
    for variant in variants:
        assert list(hashlib.sha256(data).hexdigest() for data in native_xml.values()).count(variant['sha256']) == 1

    consumer = compatibility.published_kmp_consumer(fixture_args, producer)
    consumer_build = consumer / 'build.gradle.kts'
    consumer_build.write_text(consumer_build.read_text().replace('plugins {', f'''plugins {{
        kotlin("jvm") version "{versions['kotlin']}" apply false
        id("org.jetbrains.kotlin.plugin.compose") version "{versions['kotlin']}"
    ''') + f'''
android.buildFeatures.compose = true
android.defaultConfig.applicationId = "io.github.hlcaptain.symbols.generatorcontracts"
android.buildTypes.named("release") {{ signingConfig = android.signingConfigs.getByName("debug") }}
dependencies {{
    implementation("androidx.activity:activity:{versions['androidx-activity']}")
    implementation("org.jetbrains.compose.foundation:foundation:1.12.1")
}}
''')
    (consumer / 'src/main/java/example/ConsumerActivity.java').unlink()
    (consumer / 'src/main/AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application android:name="study.ContractApplication" android:label="Generated resource contracts">
    <activity android:name="study.MainActivity" android:exported="true">
      <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
    </activity>
  </application>
</manifest>''')
    activity = contracts.ACTIVITY.replace('@PACKAGE@', 'example.resources').replace('@ICON@', 'contract')
    activity = activity.replace('@PROTOCOL@', 'compose-android-resource://')
    activity = activity.replace('import androidx.activity.compose.setContent', 'import androidx.compose.ui.platform.ComposeView')
    activity = activity.replace('        setContent {\n', '        setContentView(ComposeView(this).apply { setContent {\n')
    activity = activity.replace('        }\n    }\n\n    @Composable', '        } })\n    }\n\n    @Composable')
    source = consumer / 'src/main/kotlin/study/MainActivity.kt'
    source.parent.mkdir(parents=True)
    source.write_text(activity)
    assets = consumer / 'src/main/assets'
    assets.mkdir()
    (assets / 'contract-variants.tsv').write_text(''.join(f'{v["theme"]}\t{v["path"]}\t{v["sha256"]}\n' for v in variants))
    (assets / 'contract-fallback.bin').write_text('untouched asset fallback')
    tasks = ['assembleRelease', 'reportComposeRuntime']
    run(consumer, 'consumer', tasks)
    run(consumer, 'consumer-reuse', tasks, True)
    runtime['consumer-android'] = compatibility.verify_compose_runtime(
        consumer / 'build/reports/compose-runtime.txt', '1.12.1', True, resources_version=args.runtime_version)
    apk = next((consumer / 'build/outputs/apk/release').glob('*.apk'))
    with zipfile.ZipFile(apk) as archive:
        values = [archive.read(name) for name in archive.namelist()]
        assert all(values.count(data) == 1 for data in native_xml.values()), 'Dynamic raw access must retain the whole enrolled XML set'
        assert not any(name.startswith('assets/composeResources/') and name.endswith('.xml')
                       for name in archive.namelist())
    report = dict(status='Archive contracts passed; device contracts pending', generator=args.plugin_version,
                  runtime=args.runtime_version, symbols_version=args.symbols_version,
                  stages=stages, resolved_runtime=runtime, variants=variants,
                  enrolled_xml=4, retained_xml=4, apk=str(apk), apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                  launch_component='io.github.hlcaptain.symbols.generatorcontracts/study.MainActivity',
                  required_log_markers=['PRE_CONTEXT_DESCRIPTOR_OK', 'CONTRACT_OK theme=LIGHT', 'CONTRACT_OK theme=DARK', 'CONTRACT_ALL_OK'])
    (output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
