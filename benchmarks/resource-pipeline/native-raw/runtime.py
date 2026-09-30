#!/usr/bin/env python3
"""Prepare local-only upstream reader candidates from pinned official Compose sources.

The published KMP bridge preserves upstream common metadata and non-Android
variants. Only the Android AAR is rebuilt; this is not an upstream release.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

UPSTREAM = "1.12.1"
VERSION = "1.12.2-native-raw02"
GROUP = "org.jetbrains.compose.components"
CAPABILITY = "components-resources-native-xml-v1"
BASE = "https://repo.maven.apache.org/maven2/org/jetbrains/compose/components"
HERE = Path(__file__).resolve().parent
PINS_BY_VERSION = json.loads((HERE / "runtime-inputs.json").read_text())
PINS = PINS_BY_VERSION[UPSTREAM]


def candidate_version(upstream: str) -> str:
    major, minor, patch = upstream.split(".")
    return f"{major}.{minor}.{int(patch) + 1}-native-raw02"


def fetch(directory: Path, filename: str, upstream: str = UPSTREAM) -> bytes:
    expected = PINS_BY_VERSION[upstream][filename]
    target = directory / filename
    if not target.exists():
        module = filename.split(f"-{upstream}")[0]
        with urllib.request.urlopen(f"{BASE}/{module}/{upstream}/{filename}", timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError(f"Checksum mismatch: {filename}")
        target.write_bytes(data)
    data = target.read_bytes()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError(f"Checksum mismatch: {target}")
    return data


def write(path: Path, data: str | bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, bytes):
        path.write_bytes(data)
    else:
        path.write_text(data)


def prepare(directory: Path, upstream: str = UPSTREAM) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    downloads = directory / "downloads"
    downloads.mkdir(exist_ok=True)
    inputs = {name: fetch(downloads, name, upstream) for name in PINS_BY_VERSION[upstream]}
    with zipfile.ZipFile(io.BytesIO(inputs[f"components-resources-android-{upstream}-sources.jar"])) as archive:
        for name in archive.namelist():
            if name.endswith(".kt"):
                if ".." in Path(name).parts or Path(name).is_absolute():
                    raise ValueError(f"Unsafe archive path: {name}")
                write(directory / "upstream" / name, archive.read(name))
    subprocess.run(["patch", "--batch", "--fuzz=0", "-p1", "-i", str(HERE / "resource-reader.patch")],
                   cwd=directory / "upstream", check=True)
    with zipfile.ZipFile(io.BytesIO(inputs[f"components-resources-android-{upstream}.aar"])) as archive:
        manifest = archive.read("AndroidManifest.xml").decode()
        manifest = manifest.replace('    package="org.jetbrains.compose.components.resources" ', " ")
        write(directory / "src/androidMain/AndroidManifest.xml", manifest)
    write(directory / "settings.gradle.kts", '''pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "compose-native-raw-runtime"
''')
    metadata = json.loads(inputs[f"components-resources-android-{upstream}.module"])
    runtime_variant = next(v for v in metadata["variants"]
                           if v["attributes"].get("org.gradle.category") == "library"
                           and v["attributes"].get("org.gradle.usage") == "java-runtime")
    dependencies = {d["module"]: d["version"]["requires"] for d in runtime_variant["dependencies"]}
    script = '''import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.4.20"
    id("com.android.kotlin.multiplatform.library") version "9.4.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}

kotlin {
    android {
        namespace = "org.jetbrains.compose.components.resources"
        compileSdk = 37
        minSdk = 23
        androidResources.enable = true
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            moduleName.set("library_release")
        }
    }
    sourceSets {
        // Same resource API opt-ins as upstream components/resources/library/build.gradle.kts.
        all {
            languageSettings.optIn("org.jetbrains.compose.resources.InternalResourceApi")
            languageSettings.optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
        }
        commonMain {
            kotlin.srcDir("upstream/commonMain")
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:1.12.1")
                implementation("org.jetbrains.compose.foundation:foundation:1.12.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        val blockingMain by creating {
            dependsOn(commonMain.get())
            kotlin.srcDir("upstream/blockingMain")
        }
        val jvmAndAndroidMain by creating {
            dependsOn(blockingMain)
            kotlin.srcDir("upstream/jvmAndAndroidMain")
        }
        androidMain {
            dependsOn(jvmAndAndroidMain)
            kotlin.srcDir("upstream/androidMain")
            dependencies { compileOnly("androidx.test:monitor:1.8.0") }
        }
    }
}
'''
    script = script.replace("runtime:1.12.1", "runtime:" + dependencies["runtime"])
    script = script.replace("foundation:1.12.1", "foundation:" + dependencies["foundation"])
    script = script.replace("kotlinx-coroutines-core:1.9.0", "kotlinx-coroutines-core:" + dependencies["kotlinx-coroutines-core"])
    write(directory / "build.gradle.kts", script)
    write(directory / "gradle.properties", '''org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.workers.max=2
org.gradle.parallel=false
kotlin.daemon.jvmargs=-Xmx1g
kotlin.mpp.applyDefaultHierarchyTemplate=false
android.useAndroidX=true
''')


def checksums(data: bytes) -> dict:
    return {algorithm: hashlib.new(algorithm, data).hexdigest()
            for algorithm in ("sha512", "sha256", "sha1", "md5")}


def candidate_pom(original: bytes, upstream: str = UPSTREAM, candidate: str | None = None) -> bytes:
    candidate = candidate or candidate_version(upstream)
    namespace = "http://maven.apache.org/POM/4.0.0"
    ET.register_namespace("", namespace)
    ET.register_namespace("xsi", "http://www.w3.org/2001/XMLSchema-instance")
    root = ET.fromstring(original)
    root.find(f"{{{namespace}}}version").text = candidate
    root.find(f"{{{namespace}}}description").text = (
        f"LOCAL UPSTREAM PROPOSAL: Compose {upstream} with an Android native-XML reader; not an upstream release."
    )
    # Keep the Gradle metadata marker, otherwise some consumers will ignore variants.
    root.insert(0, ET.Comment(" do_not_remove: published-with-gradle-metadata "))
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def require_fresh_repository(repository: Path, candidate: str = VERSION) -> None:
    for module in ("components-resources", "components-resources-android"):
        coordinate = repository / GROUP.replace(".", "/") / module / candidate
        if coordinate.exists():
            raise FileExistsError(f"Refusing to overwrite published runtime {coordinate}; use a fresh repository")


def add_android_capability(metadata: dict, candidate: str, module: str) -> None:
    """Advertise only on the Android leaf; root redirects must not duplicate the capability."""
    if module != "components-resources-android":
        return
    # KMP leaf metadata points component.module at its owner; its implicit
    # capability nevertheless uses the physical publication's coordinates.
    default_capability = module
    for variant in metadata["variants"]:
        if variant.get("attributes", {}).get("org.jetbrains.kotlin.platform.type") != "androidJvm":
            continue
        capabilities = variant.setdefault("capabilities", [])
        for name in (default_capability, CAPABILITY):
            if not any(c["group"] == GROUP and c["name"] == name for c in capabilities):
                capabilities.append(dict(group=GROUP, name=name, version=candidate))


def publish(directory: Path, repository: Path, upstream: str = UPSTREAM, candidate: str | None = None) -> dict:
    candidate = candidate or candidate_version(upstream)
    require_fresh_repository(repository, candidate)
    downloads = directory / "downloads"
    built = list((directory / "build/outputs/aar").glob("*.aar"))
    if len(built) != 1:
        raise ValueError(f"Expected one rebuilt AAR, got {built}")
    aar = built[0].read_bytes()
    source_buffer = io.BytesIO()
    with zipfile.ZipFile(source_buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted((directory / "upstream").rglob("*.kt")):
            info = zipfile.ZipInfo(path.relative_to(directory / "upstream").as_posix(), (1980, 1, 1, 0, 0, 0))
            archive.writestr(info, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
    artifacts = {}
    for module in ("components-resources", "components-resources-android"):
        target = repository / GROUP.replace(".", "/") / module / candidate
        target.mkdir(parents=True, exist_ok=True)
        metadata = json.loads(fetch(downloads, f"{module}-{upstream}.module", upstream))
        metadata["component"]["version"] = candidate
        if "url" in metadata["component"]:
            metadata["component"]["url"] = metadata["component"]["url"].replace(upstream, candidate)
        add_android_capability(metadata, candidate, module)
        for variant in metadata["variants"]:
            reference = variant.get("available-at")
            if reference and reference["module"] == "components-resources-android":
                reference["version"] = candidate
                reference["url"] = reference["url"].replace(upstream, candidate)
            for item in variant.get("files", []):
                if module.endswith("-android"):
                    data = aar if item["url"].endswith(".aar") else source_buffer.getvalue()
                else:
                    data = fetch(downloads, item["url"], upstream)
                name = item["url"].replace(upstream, candidate)
                item.update(name=name, url=name, size=len(data), **checksums(data))
                write(target / name, data)
                artifacts[str((target / name).relative_to(repository))] = checksums(data)["sha256"]
        write(target / f"{module}-{candidate}.module", json.dumps(metadata, indent=2) + "\n")
        write(target / f"{module}-{candidate}.pom", candidate_pom(fetch(downloads, f"{module}-{upstream}.pom", upstream), upstream, candidate))
    evidence = {
        "upstreamVersion": upstream,
        "candidateVersion": candidate,
        "upstreamInputsSha256": PINS_BY_VERSION[upstream],
        "patchSha256": hashlib.sha256((HERE / "resource-reader.patch").read_bytes()).hexdigest(),
        "toolchain": {"kotlin": "2.4.20", "agp": "9.4.1", "compileSdk": 37, "minSdk": 23},
        "artifactsSha256": artifacts,
        "androidCapability": f"{GROUP}:{CAPABILITY}",
        "scope": f"Local upstream Android reader proposal. Common metadata and non-Android variants remain upstream {upstream}.",
    }
    write(directory / "publication.json", json.dumps(evidence, indent=2) + "\n")
    return evidence


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--gradle", type=Path, required=True, help="Symbols' Gradle 9.7 wrapper")
    parser.add_argument("--prepare-only", action="store_true")
    parser.add_argument("--upstream-version", choices=sorted(PINS_BY_VERSION), default=UPSTREAM)
    parser.add_argument("--candidate-version", help="Local-only prerelease coordinate; defaults to the next patch's native-raw02")
    args = parser.parse_args()
    directory = args.output.resolve()
    candidate = args.candidate_version or candidate_version(args.upstream_version)
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+-native-raw[0-9A-Za-z.-]+", candidate):
        parser.error("--candidate-version must be explicitly marked as a native-raw prerelease")
    if not args.prepare_only:
        require_fresh_repository(args.repository.resolve(), candidate)
    prepare(directory, args.upstream_version)
    if args.prepare_only:
        print(f"Prepared {directory}")
        return
    command = [str(args.gradle.resolve()), "-p", str(directory), "assemble", "--console=plain", "--no-daemon",
               "--max-workers=1", "-Pkotlin.compiler.execution.strategy=in-process"]
    write(directory / "build-command.json", json.dumps(command, indent=2) + "\n")
    with (directory / "build.log").open("w") as log:
        completed = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, env=os.environ.copy())
    if completed.returncode:
        raise SystemExit(f"Runtime build failed ({completed.returncode}); see {directory / 'build.log'}")
    evidence = publish(directory, args.repository.resolve(), args.upstream_version, candidate)
    print(json.dumps({"candidateVersion": candidate, "report": str(directory / "publication.json"),
                      "artifacts": len(evidence["artifactsSha256"])}))


if __name__ == "__main__":
    main()
