#!/usr/bin/env python3
"""Compile a client against stock artifacts, then consume it unchanged with native resources."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import zipfile

import consumer
import publisher


def generate(args):
    descriptor = json.loads((args.publisher / "publisher.json").read_text())
    if descriptor["backend"] != "native":
        raise ValueError("The replacement dependency must use native resources")
    if args.output.exists():
        raise ValueError(f"Refusing to overwrite {args.output}")
    name = descriptor["items"][0]["resource"]
    getter = "get" + name[0].upper() + name[1:]
    # The first published Material accessor belongs to Drawable0_commonMainKt.
    # javac verifies this original JVM facade, rather than guessing a candidate API.
    source = f'''package study.compat;

import {publisher.PACKAGE}.Drawable0_commonMainKt;
import {publisher.PACKAGE}.Res;
import org.jetbrains.compose.resources.DrawableResource;

public final class PublishedAlias {{
    private PublishedAlias() {{}}

    public static DrawableResource icon() {{
        return Drawable0_commonMainKt.{getter}(Res.drawable.INSTANCE);
    }}
}}
'''
    with tempfile.TemporaryDirectory(prefix="symbols-stock-alias-") as directory:
        temporary = Path(directory)
        classpath = []
        for label, aar in (("pack", args.stock_pack_aar), ("runtime", args.stock_runtime_aar)):
            jar = temporary / f"stock-{label}.jar"
            with zipfile.ZipFile(aar) as archive:
                jar.write_bytes(archive.read("classes.jar"))
            classpath.append(jar)
        classpath.append(args.stdlib.resolve())
        java = temporary / "PublishedAlias.java"
        java.write_text(source)
        classes = temporary / "classes"
        classes.mkdir()
        command = [str(args.jdk / "bin/javac"), "--release", "11", "-cp",
                   ":".join(map(str, classpath)), "-d", str(classes), str(java)]
        subprocess.run(command, check=True)
        bytecode = (classes / "study/compat/PublishedAlias.class").read_bytes()

    consumer.generate(SimpleNamespace(publisher=args.publisher, repository=args.repository,
                                      output=args.output, access="direct", count="1", agp=None))
    output = args.output.resolve()
    # These source roots are deliberately not Java/Kotlin compilation source sets.
    # Keeping the helper JAR under src makes the existing runner fingerprint it.
    publisher.write(output, "src/precompiled-source/PublishedAlias.java", source)
    jar = output / "src/precompiled/published-alias-stock.jar"
    jar.parent.mkdir(parents=True)
    with zipfile.ZipFile(jar, "w") as archive:
        archive.writestr(zipfile.ZipInfo("study/compat/PublishedAlias.class"), bytecode)
    with (output / "build.gradle.kts").open("a") as build:
        build.write('\ndependencies { implementation(files("src/precompiled/published-alias-stock.jar")) }\n')
    publisher.write(output, "src/main/kotlin/study/Icons.kt", '''package study

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import study.compat.PublishedAlias

@Composable
internal fun iconPainter(index: Int, name: String, resource: String): Painter =
    painterResource(PublishedAlias.icon())
''')
    manifest_path = output / "fixture.json"
    manifest = json.loads(manifest_path.read_text())
    manifest["precompiled_alias"] = dict(
        source_sha256=publisher.digest(source.encode()),
        class_sha256=publisher.digest(bytecode),
        jar_sha256=publisher.digest(jar.read_bytes()),
        stock_pack_aar=str(args.stock_pack_aar.resolve()),
        stock_pack_aar_sha256=publisher.digest(args.stock_pack_aar.read_bytes()),
        stock_runtime_aar=str(args.stock_runtime_aar.resolve()),
        stock_runtime_aar_sha256=publisher.digest(args.stock_runtime_aar.read_bytes()),
        stdlib_sha256=publisher.digest(args.stdlib.read_bytes()),
        java_release=11,
        compile_contract="Stock pack + stock runtime + Kotlin stdlib only; never recompiled against candidate",
    )
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"alias_fixture": str(output), "precompiled_jar_sha256": manifest["precompiled_alias"]["jar_sha256"]}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("publisher", "repository", "output", "stock-pack-aar", "stock-runtime-aar", "stdlib", "jdk"):
        parser.add_argument("--" + name, type=Path, required=True)
    generate(parser.parse_args())


if __name__ == "__main__":
    main()
