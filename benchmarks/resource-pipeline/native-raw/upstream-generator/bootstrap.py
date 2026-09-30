#!/usr/bin/env python3
"""Prepare/build a local Compose plugin contribution from verified upstream sources.

This recompiles the complete resources package, then overlays those classes into
the original plugin JAR. Other packages and module metadata remain upstream.
The source patch is upstream-shaped; this harness uses the same KotlinPoet
dependency and Shadow relocation as upstream. Nothing is published remotely.
"""
from __future__ import annotations

import argparse
import difflib
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
PACKAGE = "org/jetbrains/compose/resources/"
BASE = "https://repo.maven.apache.org/maven2/org/jetbrains/compose/compose-gradle-plugin"
PINS = {
    "1.11.1": {
        "sources.jar": "c5140e825fc0ee7cd037b942e5807e293d90b4d39a52e5edbba1b551dac8fb48",
        "jar": "a78b847b159d5cb702d1b62cfa8f26897e00ddca0bd88e0a57035aaef37486a9",
        "pom": "b72afb8363b8d17d22e2ce212d966a9cf852a0e1e40bfe84c189116e0cc7794c",
        "module": "ef9abafb2acbe73ffcef2566ceca08e7885465c90f40ef91449891c52dc51c77",
        "javadoc.jar": "97a2b5534449d473e7cc23e174301cc8ef69e80002d12c5dccccee2551b8563a",
    },
    "1.12.1": {
        "sources.jar": "2fdf6513d875644c2fcde80d5b5816c9fae6a8a6205ebc8a09407708698a67eb",
        "jar": "f02445941fc50c7fdde9fe6da709b793199c7dc97ee08cf15fee6e99ccb4dcca",
        "pom": "1f832d805f7c248bc147422d5ac5f9f01676a05a7e1f812429d8632ad7f2522c",
        "module": "11cc429f6b02a9a47abd41215fa352336eda6fc9b16ae08c7adbc2c6446adbf7",
        "javadoc.jar": "c6deada2fac53b8ea6523dbda77597b128006674616f140f04df23264c6d1aa3",
    },
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def artifact_name(version, suffix):
    return f"compose-gradle-plugin-{version}" + ("-" + suffix if suffix in ("sources.jar", "javadoc.jar") else "." + suffix)


def fetch(downloads, version, suffix):
    path = downloads / artifact_name(version, suffix)
    if not path.exists():
        with urllib.request.urlopen(f"{BASE}/{version}/{path.name}", timeout=60) as response:
            data = response.read()
        if sha(data) != PINS[version][suffix]:
            raise ValueError(f"Upstream checksum mismatch: {path.name}")
        path.write_bytes(data)
    data = path.read_bytes()
    if sha(data) != PINS[version][suffix]:
        raise ValueError(f"Upstream checksum mismatch: {path}")
    return data


def prepare(directory, version):
    directory.mkdir(parents=True, exist_ok=True)
    downloads = directory / "downloads"
    downloads.mkdir(exist_ok=True)
    inputs = {suffix: fetch(downloads, version, suffix) for suffix in PINS[version]}
    source = directory / "upstream"
    if source.exists():
        shutil.rmtree(source)
    with zipfile.ZipFile(io.BytesIO(inputs["sources.jar"])) as archive:
        for name in archive.namelist():
            if name.startswith(PACKAGE) and name.endswith(".kt"):
                if ".." in Path(name).parts:
                    raise ValueError(f"Unsafe source path: {name}")
                path = source / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(archive.read(name))
    for patch in (HERE / f"imports-{version}.patch", HERE / "compose-native-xml.patch"):
        subprocess.run(["patch", "--batch", "--fuzz=0", "-p1", "-i", str(patch)], cwd=source, check=True)
    # Canonical effective diff retains the historical patch identity even though
    # the contribution shares behavior hunks across both upstream baselines.
    differences = []
    with zipfile.ZipFile(io.BytesIO(inputs["sources.jar"])) as archive:
        for name in sorted(archive.namelist()):
            if name.startswith(PACKAGE) and name.endswith(".kt"):
                for line in difflib.unified_diff(archive.read(name).decode().splitlines(keepends=True),
                                               (source / name).read_text().splitlines(keepends=True),
                                               fromfile="a/" + name, tofile="b/" + name):
                    differences.append(line if line.endswith("\n") else line + "\n\\ No newline at end of file\n")
    (directory / "effective.patch").write_text("".join(differences))
    shutil.copy2(HERE / "NativeAndroidXmlResources.kt", source / PACKAGE / "NativeAndroidXmlResources.kt")
    compile_source = directory / "src/main/kotlin"
    if compile_source.exists():
        shutil.rmtree(compile_source)
    for path in source.rglob("*.kt"):
        target = compile_source / path.relative_to(source)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(path.read_text())
    test_source = directory / "src/test/kotlin" / PACKAGE / "NativeAndroidXmlResourcesTest.kt"
    test_source.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(HERE / "NativeAndroidXmlResourcesTest.kt", test_source)
    original_jar = downloads / artifact_name(version, "jar")
    (directory / "settings.gradle.kts").write_text('''pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "compose-native-xml-overlay"
''')
    (directory / "build.gradle.kts").write_text('''import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
plugins {
    kotlin("jvm") version "2.4.20"
    id("com.gradleup.shadow") version "9.1.0"
}
kotlin {
    jvmToolchain(17)
    compilerOptions {
        moduleName.set("compose")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xfriend-paths=" + file(@JAR@).absolutePath)
    }
}
dependencies {
    compileOnly(gradleApi())
    compileOnly(files(@JAR@))
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    compileOnly("com.android.tools.build:gradle:8.10.1")
    compileOnly("com.squareup:kotlinpoet:2.1.0")
    testImplementation(kotlin("test-junit"))
    testImplementation(gradleApi())
    testImplementation("com.squareup:kotlinpoet:2.1.0")
    testRuntimeOnly(files(@JAR@))
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    testRuntimeOnly("com.android.tools.build:gradle:8.10.1")
}
tasks.named<ShadowJar>("shadowJar") {
    configurations = emptyList()
    archiveClassifier.set("overlay")
    relocate("com.squareup.kotlinpoet", "org.jetbrains.compose.internal.com.squareup.kotlinpoet")
}
tasks.test {
    useJUnit()
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    systemProperty("originalComposePluginJar", file(@JAR@).absolutePath)
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}
'''.replace("@JAR@", json.dumps(str(original_jar))))
    (directory / "gradle.properties").write_text('''org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
org.gradle.workers.max=1
org.gradle.parallel=false
kotlin.compiler.execution.strategy=in-process
''')
    return inputs


def artifact_hashes(data):
    return {name: hashlib.new(name, data).hexdigest() for name in ("sha256", "sha512", "sha1", "md5")}


def require_fresh(repository, candidate):
    for module in ("compose-gradle-plugin", "org.jetbrains.compose.gradle.plugin"):
        directory = repository / "org/jetbrains/compose" / module / candidate
        if directory.exists():
            raise FileExistsError(f"Refusing to overwrite {directory}; select a fresh candidate version/repository")


def publish(directory, repository, baseline, candidate, inputs):
    require_fresh(repository, candidate)
    overlay_path = directory / "build/libs/compose-native-xml-overlay-overlay.jar"
    if not overlay_path.is_file():
        raise ValueError(f"Expected the compiled Shadow overlay: {overlay_path}")
    provenance = dict(upstream_version=baseline, candidate_version=candidate,
                      status="Local upstream contribution candidate, not an official Compose release",
                      source_sha256=PINS[baseline]["sources.jar"],
                      patch_sha256=sha((directory / "effective.patch").read_bytes()),
                      native_backend_source_sha256=sha((HERE / "NativeAndroidXmlResources.kt").read_bytes()),
                      compiler="Kotlin 2.4.20; moduleName=compose; JVM17; AGP8.10.1 compileOnly")
    result = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(inputs["jar"])) as original, zipfile.ZipFile(overlay_path) as overlay, zipfile.ZipFile(result, "w", zipfile.ZIP_DEFLATED) as output:
        for entry in original.infolist():
            if not (entry.filename.startswith(PACKAGE) and entry.filename.endswith(".class")):
                output.writestr(entry, original.read(entry))
        compiled = [entry for entry in overlay.infolist() if entry.filename.startswith(PACKAGE) and entry.filename.endswith(".class")]
        if not any(entry.filename.endswith("ResourcesExtension.class") for entry in compiled):
            raise ValueError("Overlay is missing the patched public resource extension")
        for entry in compiled:
            output.writestr(entry, overlay.read(entry))
        output.writestr("META-INF/compose-native-xml-prototype.json", json.dumps(provenance, sort_keys=True))
        # Keep original compose.kotlin_module: only member APIs are added publicly,
        # and replacing it with subset metadata would hide unrelated plugin packages.
    candidate_jar = result.getvalue()
    sources = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(inputs["sources.jar"])) as original, zipfile.ZipFile(sources, "w", zipfile.ZIP_DEFLATED) as output:
        for entry in original.infolist():
            replacement = directory / "upstream" / entry.filename
            output.writestr(entry, replacement.read_bytes() if replacement.is_file() else original.read(entry))
        output.writestr(PACKAGE + "NativeAndroidXmlResources.kt", (HERE / "NativeAndroidXmlResources.kt").read_bytes())
    target = repository / "org/jetbrains/compose/compose-gradle-plugin" / candidate
    target.mkdir(parents=True)
    metadata = json.loads(inputs["module"])
    metadata["component"]["version"] = candidate
    for variant in metadata["variants"]:
        if "org.gradle.jvm.version" in variant.get("attributes", {}):
            variant["attributes"]["org.gradle.jvm.version"] = 17
        for entry in variant.get("files", []):
            if entry["url"].endswith("-sources.jar"):
                data = sources.getvalue()
            elif entry["url"].endswith("-javadoc.jar"):
                data = inputs["javadoc.jar"]
            else:
                data = candidate_jar
            filename = entry["url"].replace(baseline, candidate)
            entry.update(name=filename, url=filename, size=len(data), **artifact_hashes(data))
            (target / filename).write_bytes(data)
    (target / f"compose-gradle-plugin-{candidate}.module").write_text(json.dumps(metadata, indent=2) + "\n")
    ns = "http://maven.apache.org/POM/4.0.0"
    ET.register_namespace("", ns)
    pom = ET.fromstring(inputs["pom"])
    pom.find(f"{{{ns}}}version").text = candidate
    pom.find(f"{{{ns}}}description").text = provenance["status"]
    pom.insert(0, ET.Comment(" do_not_remove: published-with-gradle-metadata "))
    (target / f"compose-gradle-plugin-{candidate}.pom").write_bytes(ET.tostring(pom, encoding="utf-8", xml_declaration=True))
    marker = repository / "org/jetbrains/compose/org.jetbrains.compose.gradle.plugin" / candidate
    marker.mkdir(parents=True)
    (marker / f"org.jetbrains.compose.gradle.plugin-{candidate}.pom").write_text(f'''<project xmlns="{ns}"><modelVersion>4.0.0</modelVersion>
<groupId>org.jetbrains.compose</groupId><artifactId>org.jetbrains.compose.gradle.plugin</artifactId><version>{candidate}</version><packaging>pom</packaging>
<dependencies><dependency><groupId>org.jetbrains.compose</groupId><artifactId>compose-gradle-plugin</artifactId><version>{candidate}</version></dependency></dependencies></project>
''')
    provenance.update(artifact_sha256=sha(candidate_jar), rebuilt_resource_classes=len(compiled),
                      artifact=str(target / f"compose-gradle-plugin-{candidate}.jar"))
    report = directory / "publication.json"
    if report.exists():
        previous = json.loads(report.read_text())
        (directory / f"publication-{previous['candidate_version']}.json").write_bytes(report.read_bytes())
    encoded = json.dumps(provenance, indent=2) + "\n"
    (directory / f"publication-{candidate}.json").write_text(encoded)
    report.write_text(encoded)
    return provenance


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", choices=sorted(PINS), default="1.12.1")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--build", action="store_true", help="Run the isolated compiler and publish locally")
    parser.add_argument("--repository", type=Path)
    parser.add_argument("--candidate-version")
    parser.add_argument("--gradle", type=Path, default=ROOT / "tooling/gradlew")
    args = parser.parse_args()
    if args.build and (args.repository is None or args.candidate_version is None):
        parser.error("--build requires --repository and a fresh --candidate-version")
    if args.candidate_version and (
        not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+-]*", args.candidate_version)
        or "-" not in args.candidate_version or args.candidate_version == args.baseline
    ):
        parser.error("Use an explicit local prerelease candidate version, not an upstream version")
    if args.build:
        require_fresh(args.repository.resolve(), args.candidate_version)
    directory = args.output.resolve()
    inputs = prepare(directory, args.baseline)
    if not args.build:
        print(json.dumps(dict(prepared=str(directory), upstream=args.baseline)))
        return
    command = [str(args.gradle.resolve()), "-p", str(directory), "test", "shadowJar", "--no-daemon", "--max-workers=1", "--console=plain"]
    log_path = directory / "build.log"
    if log_path.exists():
        attempts = [int(path.stem.rsplit("-", 1)[1]) for path in directory.glob("build-attempt-*.log")]
        log_path.rename(directory / f"build-attempt-{max(attempts, default=0)+1:02d}.log")
    with log_path.open("w") as log:
        completed = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, env=os.environ.copy())
    if completed.returncode:
        raise SystemExit(f"Overlay compilation failed; see {directory / 'build.log'}")
    print(json.dumps(publish(directory, args.repository.resolve(), args.baseline, args.candidate_version, inputs), indent=2))


if __name__ == "__main__":
    main()
