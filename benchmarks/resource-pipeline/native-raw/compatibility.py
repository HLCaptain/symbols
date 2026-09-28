#!/usr/bin/env python3
"""Generate a published KMP bridge and independent Android/JVM consumers."""
import argparse
import json
from pathlib import Path
import re
import shutil
from types import SimpleNamespace
import tomllib

import consumer
import publisher


def generate(args):
    if args.output.exists():
        raise ValueError(f"Refusing to overwrite {args.output}")
    if not re.fullmatch(r"[A-Za-z0-9_.+-]+", args.version):
        raise ValueError("Invalid intermediate publication version")
    descriptor = json.loads((args.publisher / "publisher.json").read_text())
    if descriptor["backend"] != "native":
        raise ValueError("Expected a native-resource publisher")
    icon = descriptor["items"][0]
    versions = tomllib.loads((publisher.ROOT / "gradle/libs.versions.toml").read_text())["versions"]
    bridge = "io.github.hlcaptain:symbols-native-raw-bridge:" + args.version
    android = args.output.resolve() / "android"
    consumer.generate(SimpleNamespace(publisher=args.publisher, repository=args.repository,
                                      output=android, access="direct", count="1", agp=None))
    build = android / "build.gradle.kts"
    old = f'implementation("{descriptor["coordinates"]}")'
    if build.read_text().count(old) != 1:
        raise ValueError("Expected exactly one direct resource dependency")
    build.write_text(build.read_text().replace(old, f'implementation("{bridge}")'))
    publisher.write(android, "src/main/kotlin/study/Icons.kt", '''package study

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import study.bridge.PublishedBridge

@Composable
internal fun iconPainter(index: Int, name: String, resource: String): Painter =
    painterResource(PublishedBridge.icon())
''')
    manifest = json.loads((android / "fixture.json").read_text())
    manifest.update(consumer_dependencies=[bridge], intermediate_coordinates=bridge,
                    compatibility_contract="Android app depends only on a published intermediate KMP library")
    publisher.write(android, "fixture.json", json.dumps(manifest, indent=2) + "\n")

    library, jvm = args.output.resolve() / "library", args.output.resolve() / "jvm"
    for directory, name in ((library, "symbols-native-raw-bridge"), (jvm, "native-raw-jvm-consumer")):
        for relative in ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties", "gradle.properties"):
            target = directory / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(android / relative, target)
        settings = (android / "settings.gradle.kts").read_text()
        settings = re.sub(r'rootProject.name = "[^"]+"', f'rootProject.name = "{name}"', settings)
        publisher.write(directory, "settings.gradle.kts", settings)

    publisher.write(library, "build.gradle.kts", f'''plugins {{
    kotlin("multiplatform") version "{versions['kotlin']}"
    id("com.android.kotlin.multiplatform.library") version "{versions['agp']}"
    `maven-publish`
}}
group = "io.github.hlcaptain"
version = "{args.version}"
kotlin {{
    jvmToolchain(17)
    android {{
        namespace = "study.bridge"
        compileSdk = {versions['android-compileSdk']}
        minSdk = 23
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }}
    jvm {{ compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }}
    sourceSets.commonMain.dependencies {{ api("{descriptor['coordinates']}") }}
}}
publishing.repositories.maven {{ name = "probe"; url = uri({json.dumps(args.repository.resolve().as_uri())}) }}
''')
    publisher.write(library, "src/commonMain/kotlin/study/bridge/PublishedBridge.kt", f'''package study.bridge

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import {publisher.PACKAGE}.*

@OptIn(ExperimentalResourceApi::class)
public object PublishedBridge {{
    public fun icon(): DrawableResource = Res.drawable.{icon['resource']}
    public suspend fun readBytes(path: String): ByteArray = Res.readBytes(path)
    public fun uri(path: String): String = Res.getUri(path)
}}
''')
    publisher.write(jvm, "build.gradle.kts", f'''plugins {{
    kotlin("jvm") version "{versions['kotlin']}"
    application
}}
kotlin {{ jvmToolchain(17) }}
dependencies {{
    implementation("{bridge}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:{versions['kotlinx-coroutines']}")
}}
application {{ mainClass.set("consumer.MainKt") }}
''')
    publisher.write(jvm, "src/main/kotlin/consumer/Main.kt", f'''package consumer

import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.DrawableResource
import study.bridge.PublishedBridge

fun main() = runBlocking {{
    val icon: DrawableResource = PublishedBridge.icon()
    val path = "drawable/{icon['resource']}.xml"
    val bytes = PublishedBridge.readBytes(path)
    val uri = PublishedBridge.uri(path)
    val uriBytes = URI(uri).toURL().openStream().use {{ it.readBytes() }}
    check(bytes.contentEquals(uriBytes)) {{ "JVM URI bytes differ" }}
    val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {{ "%02x".format(it) }}
    check(sha == "{icon['xml_sha256']}") {{ "JVM resource bytes differ from original published XML" }}
    println("JVM_TRANSITIVE_OK type=${{icon.javaClass.name}} sha=$sha uri=$uri")
}}
''')
    commands = {
        "publish_bridge": [str(library / "gradlew"), "-p", str(library), "publishAllPublicationsToProbeRepository"],
        "android": [str(android / "gradlew"), "-p", str(android), "assembleShrunk"],
        "jvm": [str(jvm / "gradlew"), "-p", str(jvm), "run"],
    }
    report = dict(schema_version=1, coordinates=bridge, resource_coordinates=descriptor["coordinates"],
                  expected_xml_sha256=icon["xml_sha256"], commands=commands)
    publisher.write(args.output, "compatibility.json", json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("publisher", "repository", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--version", required=True, help="Fresh version for the intermediate KMP publication")
    generate(parser.parse_args())


if __name__ == "__main__":
    main()
