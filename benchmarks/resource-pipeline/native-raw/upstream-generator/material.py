#!/usr/bin/env python3
"""Prepare/inspect a full Material pack using the real Symbols/Compose generators."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil
import tomllib
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--plugin-repository", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--backend", choices=("assets", "native"), required=True)
    parser.add_argument("--symbols-version", default="0.0.0-pruning-api02")
    parser.add_argument("--plugin-version", default="1.12.2-native-xml05")
    parser.add_argument("--runtime-version", default="1.12.2-native-raw02")
    parser.add_argument("--collect", action="store_true")
    args = parser.parse_args()
    for version in (args.version, args.symbols_version, args.plugin_version, args.runtime_version):
        if not re.fullmatch(r"[A-Za-z0-9_.+-]+", version):
            parser.error("Invalid version")
    root, output = args.source_root.resolve(), args.output.resolve()
    package = "io.github.hlcaptain.symbols.material.outlined.compose.drawables.resources"
    namespace = package.removesuffix(".resources")
    prefix = f"composeResources/{package}/"
    versions = tomllib.loads((root / "gradle/libs.versions.toml").read_text())["versions"]
    runtime = args.runtime_version if args.backend == "native" else versions["composeMultiplatform"]
    spec = importlib.util.spec_from_file_location("usage_fixtures", root / "benchmarks/resource-pipeline/fixtures.py")
    fixtures = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(fixtures)
    icons = fixtures.catalog(root / "fonts/material/MaterialSymbols.codepoints")
    if not icons:
        raise ValueError("The Material catalog must not be empty")

    if not args.collect:
        if output.exists():
            parser.error("Use a fresh producer directory")
        output.mkdir(parents=True)
        for relative in ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties"):
            target = output / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(root / relative, target)
        (output / "gradlew").chmod(0o755)
        repositories = "\n".join(f'maven {{ url = uri({json.dumps(p.resolve().as_uri())}) }}'
                                  for p in (args.repository, args.plugin_repository))
        (output / "settings.gradle.kts").write_text(f'''pluginManagement {{ repositories {{
{repositories}
google(); mavenCentral(); gradlePluginPortal()
}} }}
dependencyResolutionManagement {{ repositories {{
{repositories}
google(); mavenCentral()
}} }}
rootProject.name = "symbols-material-compose-drawables-outlined"
''')
        (output / "build.gradle.kts").write_text(f'''plugins {{
    id("io.github.hlcaptain.symbol-fonts") version "{args.symbols_version}"
    kotlin("multiplatform") version "{versions['kotlin']}"
    id("com.android.kotlin.multiplatform.library") version "{versions['agp']}"
    id("org.jetbrains.kotlin.plugin.compose") version "{versions['kotlin']}"
    id("org.jetbrains.compose") version "{args.plugin_version}"
    `maven-publish`
}}
group = "io.github.hlcaptain"
version = "{args.version}"
kotlin {{
    jvmToolchain(17)
    android {{
        namespace = "{namespace}"
        compileSdk = {versions['android-compileSdk']}
        minSdk = 23
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }}
    jvm {{ compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }}
    sourceSets.commonMain.dependencies {{
        api("org.jetbrains.compose.components:components-resources:{runtime}")
        implementation("org.jetbrains.compose.runtime:runtime:{versions['composeMultiplatform']}")
    }}
}}
compose.resources {{ packageOfResClass = "{package}"; publicResClass = true; generateResClass = always }}
symbolFonts {{
    experimentalComposeResourcePruning.set({str(args.backend == 'native').lower()})
    iconSet("MaterialSymbolsOutlinedDrawables") {{
        style("Outlined") {{
            codepoints.set(layout.projectDirectory.file("inputs/MaterialSymbols.codepoints"))
            font.set(layout.projectDirectory.file("inputs/material_symbols_outlined_regular.ttf"))
            resourcePrefix.set("material_symbols")
            composeDrawables()
        }}
    }}
}}
tasks.withType<org.gradle.api.tasks.bundling.AbstractArchiveTask>().configureEach {{
    from(files("inputs/LICENSE", "inputs/THIRD_PARTY_NOTICES.md")) {{
        into("META-INF/material-compose-drawables-outlined")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }}
}}
publishing.repositories.maven {{ name = "probe"; url = uri({json.dumps(args.repository.resolve().as_uri())}) }}
''')
        (output / "gradle.properties").write_text("android.useAndroidX=true\norg.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8\norg.gradle.workers.max=1\nkotlin.compiler.execution.strategy=in-process\n")
        inputs = output / "inputs"
        inputs.mkdir()
        for source in (root / "fonts/material/MaterialSymbols.codepoints", root / "fonts/material/outlined-static/composeResources/font/material_symbols_outlined_regular.ttf", root / "LICENSE", root / "THIRD_PARTY_NOTICES.md"):
            shutil.copy2(source, inputs / source.name)
        (output / "inputs.json").write_text(json.dumps({p.name: sha(p.read_bytes()) for p in sorted(inputs.iterdir())}, indent=2) + "\n")
        print(json.dumps(dict(producer=str(output), icons=len(icons), backend=args.backend)))
        return

    locations = {}
    if args.backend == "native":
        manifest = output / "build/generated/compose/resourceGenerator/nativeAndroidXml/commonMain/locations.tsv"
        for line in manifest.read_text().splitlines():
            source_set, relative, logical, resource_name, content_hash = line.split("\t")
            assert source_set == "commonMain" and logical == prefix + relative
            resource = Path(relative).stem
            if resource in locations:
                raise ValueError(f"Duplicate native location: {resource}")
            locations[resource] = (resource_name, content_hash)
        if set(locations) != {icon["resource"] for icon in icons}:
            raise ValueError("Native locations must match the complete nonempty catalog")
    aar_path = next((output / "build/outputs/aar").glob("*.aar"))
    jvm_path = next((output / "build/libs").glob(f"*-jvm-{args.version}.jar"))
    outputs = []
    with zipfile.ZipFile(aar_path) as aar, zipfile.ZipFile(jvm_path) as jvm:
        for icon in icons:
            resource = icon["resource"]
            raw_name = locations[resource][0] if args.backend == "native" else None
            entry = f"res/raw/{raw_name}.xml" if raw_name else f"assets/{prefix}drawable/{resource}.xml"
            data = aar.read(entry)
            assert data == jvm.read(f"{prefix}drawable/{resource}.xml")
            if raw_name:
                assert sha(data) == locations[resource][1]
            outputs.append(dict(icon, raw_resource=raw_name, xml_sha256=sha(data), xml_bytes=len(data)))
        if args.backend == "native":
            assert not any(name.startswith('assets/' + prefix) for name in aar.namelist())
        for name in ("LICENSE", "THIRD_PARTY_NOTICES.md"):
            entry = "META-INF/material-compose-drawables-outlined/" + name
            assert aar.read(entry) == jvm.read(entry) == (output / "inputs" / name).read_bytes()
    report = dict(schema_version=1, backend=args.backend, version=args.version, runtime_version=runtime,
                  coordinates=f"io.github.hlcaptain:symbols-material-compose-drawables-outlined:{args.version}",
                  resource_package=package, namespace=namespace, items=outputs,
                  raw_resource_prefix="compose_native_xml_",
                  source="Actual published Symbols plugin and patched Compose generator; no source-output rewriting",
                  symbols_version=args.symbols_version, compose_plugin_version=args.plugin_version,
                  inputs=json.loads((output / 'inputs.json').read_text()),
                  aar_sha256=sha(aar_path.read_bytes()), jvm_sha256=sha(jvm_path.read_bytes()))
    (output / "publisher.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(dict(verified=True, resources=len(outputs), backend=args.backend, aar_bytes=aar_path.stat().st_size)))


if __name__ == "__main__":
    main()
