#!/usr/bin/env python3
"""Build a local-only Compose 1.12.1 Android reader experiment from pinned sources.

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
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

UPSTREAM = "1.12.1"
VERSION = "1.12.2-native-raw01"
GROUP = "org.jetbrains.compose.components"
BASE = "https://repo.maven.apache.org/maven2/org/jetbrains/compose/components"
HERE = Path(__file__).resolve().parent
PINS = {
    "components-resources-android-1.12.1-sources.jar": "847c6151711087a5ce752651084d9436ebbe4938374e9272dfa9d94c7f106783",
    "components-resources-android-1.12.1.aar": "04812fff18c192cd6415cbb155894de06093e9f4299595139cece32e3c932f20",
    "components-resources-android-1.12.1.pom": "526d52894c87ee49f9190ccf3f4a5e856a473a7a05a521e3c416b7a1761685f8",
    "components-resources-android-1.12.1.module": "2cd6b9436e31201678bad05c6f43b3a8d432fcd68a6aeb5154fa8eef218d90fa",
    "components-resources-1.12.1.module": "347aabc4ab9b743af73200c1055d07819dc3bc7003c2089a5d62ee0045beac57",
    "components-resources-1.12.1.jar": "fa8ea0bf4dc142596c3a65875d610b12fed6f8996d3f1863cdf39d3fbbd0cb08",
    "components-resources-1.12.1-sources.jar": "b46023a05206426795ac4029b3189f73db1b0c45038cdf927da31675b926cae0",
    "components-resources-1.12.1.pom": "a6760beb28b4289716b8b7949559125659c88698ec2c66c290b83d06458cc034",
}


def fetch(directory: Path, filename: str) -> bytes:
    target = directory / filename
    if not target.exists():
        module = filename.split(f"-{UPSTREAM}")[0]
        with urllib.request.urlopen(f"{BASE}/{module}/{UPSTREAM}/{filename}", timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != PINS[filename]:
            raise ValueError(f"Checksum mismatch: {filename}")
        target.write_bytes(data)
    data = target.read_bytes()
    if hashlib.sha256(data).hexdigest() != PINS[filename]:
        raise ValueError(f"Checksum mismatch: {target}")
    return data


def write(path: Path, data: str | bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, bytes):
        path.write_bytes(data)
    else:
        path.write_text(data)


def prepare(directory: Path) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    downloads = directory / "downloads"
    downloads.mkdir(exist_ok=True)
    inputs = {name: fetch(downloads, name) for name in PINS}
    with zipfile.ZipFile(io.BytesIO(inputs[f"components-resources-android-{UPSTREAM}-sources.jar"])) as archive:
        for name in archive.namelist():
            if name.endswith(".kt"):
                if ".." in Path(name).parts or Path(name).is_absolute():
                    raise ValueError(f"Unsafe archive path: {name}")
                write(directory / "upstream" / name, archive.read(name))
    subprocess.run(["patch", "--batch", "--fuzz=0", "-p1", "-i", str(HERE / "resource-reader.patch")],
                   cwd=directory / "upstream", check=True)
    with zipfile.ZipFile(io.BytesIO(inputs[f"components-resources-android-{UPSTREAM}.aar"])) as archive:
        manifest = archive.read("AndroidManifest.xml").decode()
        manifest = manifest.replace('    package="org.jetbrains.compose.components.resources" ', " ")
        write(directory / "src/androidMain/AndroidManifest.xml", manifest)
    write(directory / "settings.gradle.kts", '''pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "compose-native-raw-runtime"
''')
    write(directory / "build.gradle.kts", '''import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
''')
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


def candidate_pom(original: bytes) -> bytes:
    namespace = "http://maven.apache.org/POM/4.0.0"
    ET.register_namespace("", namespace)
    ET.register_namespace("xsi", "http://www.w3.org/2001/XMLSchema-instance")
    root = ET.fromstring(original)
    root.find(f"{{{namespace}}}version").text = VERSION
    root.find(f"{{{namespace}}}description").text = (
        "LOCAL EXPERIMENT: Compose 1.12.1 with an Android native-raw reader patch; not an upstream release."
    )
    # Keep the Gradle metadata marker, otherwise some consumers will ignore variants.
    root.insert(0, ET.Comment(" do_not_remove: published-with-gradle-metadata "))
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def require_fresh_repository(repository: Path) -> None:
    for module in ("components-resources", "components-resources-android"):
        coordinate = repository / GROUP.replace(".", "/") / module / VERSION
        if coordinate.exists():
            raise FileExistsError(f"Refusing to overwrite published runtime {coordinate}; use a fresh repository")


def publish(directory: Path, repository: Path) -> dict:
    require_fresh_repository(repository)
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
        target = repository / GROUP.replace(".", "/") / module / VERSION
        target.mkdir(parents=True, exist_ok=True)
        metadata = json.loads(fetch(downloads, f"{module}-{UPSTREAM}.module"))
        metadata["component"]["version"] = VERSION
        if "url" in metadata["component"]:
            metadata["component"]["url"] = metadata["component"]["url"].replace(UPSTREAM, VERSION)
        for variant in metadata["variants"]:
            reference = variant.get("available-at")
            if reference and reference["module"] == "components-resources-android":
                reference["version"] = VERSION
                reference["url"] = reference["url"].replace(UPSTREAM, VERSION)
            for item in variant.get("files", []):
                if module.endswith("-android"):
                    data = aar if item["url"].endswith(".aar") else source_buffer.getvalue()
                else:
                    data = fetch(downloads, item["url"])
                name = item["url"].replace(UPSTREAM, VERSION)
                item.update(name=name, url=name, size=len(data), **checksums(data))
                write(target / name, data)
                artifacts[str((target / name).relative_to(repository))] = checksums(data)["sha256"]
        write(target / f"{module}-{VERSION}.module", json.dumps(metadata, indent=2) + "\n")
        write(target / f"{module}-{VERSION}.pom", candidate_pom(fetch(downloads, f"{module}-{UPSTREAM}.pom")))
    evidence = {
        "upstreamVersion": UPSTREAM,
        "candidateVersion": VERSION,
        "upstreamInputsSha256": PINS,
        "patchSha256": hashlib.sha256((HERE / "resource-reader.patch").read_bytes()).hexdigest(),
        "toolchain": {"kotlin": "2.4.20", "agp": "9.4.1", "compileSdk": 37, "minSdk": 23},
        "artifactsSha256": artifacts,
        "scope": "Local Android reader prototype. Common metadata and non-Android runtime variants remain upstream 1.12.1.",
    }
    write(directory / "publication.json", json.dumps(evidence, indent=2) + "\n")
    return evidence


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--gradle", type=Path, required=True, help="Symbols' Gradle 9.7 wrapper")
    parser.add_argument("--prepare-only", action="store_true")
    args = parser.parse_args()
    directory = args.output.resolve()
    if not args.prepare_only:
        require_fresh_repository(args.repository.resolve())
    prepare(directory)
    if args.prepare_only:
        print(f"Prepared {directory}")
        return
    command = [str(args.gradle.resolve()), "-p", str(directory), "assemble", "--console=plain", "--no-daemon"]
    write(directory / "build-command.json", json.dumps(command, indent=2) + "\n")
    with (directory / "build.log").open("w") as log:
        completed = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, env=os.environ.copy())
    if completed.returncode:
        raise SystemExit(f"Runtime build failed ({completed.returncode}); see {directory / 'build.log'}")
    evidence = publish(directory, args.repository.resolve())
    print(json.dumps({"candidateVersion": VERSION, "report": str(directory / "publication.json"),
                      "artifacts": len(evidence["artifactsSha256"])}))


if __name__ == "__main__":
    main()
