#!/usr/bin/env python3
"""Generate dependency-only consumers for a published native-raw prototype pack."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import tomllib

import publisher


def generate(args):
    descriptor = json.loads((args.publisher / "publisher.json").read_text())
    icons = descriptor["items"]
    count = len(icons) if args.count == "all" else int(args.count)
    if not 0 <= count <= len(icons):
        raise ValueError("Selection exceeds published corpus")
    selected = icons[:count]
    if args.access == "raw" and not selected:
        raise ValueError("Raw lookup requires a selected resource")
    versions = tomllib.loads((publisher.ROOT / "gradle/libs.versions.toml").read_text())["versions"]
    if args.agp:
        versions["agp"] = args.agp
    usage = publisher.load_usage()
    access = "dynamic" if args.access == "raw" else args.access
    manifest = usage.generate(publisher.ROOT, args.output.resolve(), args.repository.resolve(),
                              descriptor["version"], "compose", access, str(count), versions, publisher.ROOT)
    output = args.output.resolve()
    if descriptor["backend"] == "native":
        settings = output / "settings.gradle.kts"
        extra = "; ".join(f'includeVersion("org.jetbrains.compose.components", "{module}", "{descriptor["runtime_version"]}")'
                         for module in ("components-resources", "components-resources-android"))
        settings.write_text(settings.read_text().replace('includeGroup("io.github.hlcaptain")', 'includeGroup("io.github.hlcaptain"); ' + extra))
    source = output / "src/main/kotlin/study"
    (source / "Icons.kt").write_text(usage.renderer("compose", access, icons if access == "dynamic" else selected))
    (output / "src/main/assets/selection.tsv").write_text("".join(f'{i["name"]}\t{i["resource"]}\n' for i in selected))
    if args.access == "raw":
        # This is a consumer contract check, not a required application adapter.
        activity = usage.ACTIVITY.replace("import android.os.Bundle", '''import android.os.Bundle
import android.net.Uri
import android.util.Log
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import ''' + publisher.PACKAGE + '''.Res''')
        activity = activity.replace("class MainActivity", "@OptIn(ExperimentalResourceApi::class)\nclass MainActivity")
        activity = activity.replace('        val first = intent.getIntExtra', '''        val logicalPath = "drawable/${icons.first()[1]}.xml"
        val bytes = runBlocking { Res.readBytes(logicalPath) }
        val uri = Res.getUri(logicalPath)
        val fromUri = if (uri.startsWith("file:///android_asset/")) {
            assets.open(uri.removePrefix("file:///android_asset/")).use { it.readBytes() }
        } else {
            contentResolver.openInputStream(Uri.parse(uri))!!.use { it.readBytes() }
        }
        check(bytes.contentEquals(fromUri)) { "URI bytes differ" }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        Log.i("NativeRawProbe", "RAW_OK sha=$sha uri=$uri")
        val first = intent.getIntExtra''')
        (source / "MainActivity.kt").write_text(activity)
        with (output / "build.gradle.kts").open("a") as build:
            build.write(f'\ndependencies {{ implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:{versions["kotlinx-coroutines"]}") }}\n')
    # Raw lookup is checked without enumerating the drawable catalog; otherwise
    # it could hide a broken raw-reference graph by retaining the catalog anyway.
    if args.access == "raw":
        (source / "Icons.kt").write_text(usage.renderer("compose", "direct", selected))
    manifest.update(backend="compose", prototype_backend=descriptor["backend"], access=args.access, count=count,
                    all_unique_glyphs=len(icons), selected=selected,
                    all_resources=[i["resource"] for i in icons],
                    publisher_sha256=hashlib.sha256((args.publisher / "publisher.json").read_bytes()).hexdigest(),
                    runtime_version=descriptor["runtime_version"])
    if descriptor["backend"] == "native":
        retained = icons if args.access in ("dynamic", "raw") else selected
        manifest["native_raw"] = {
            "expected_resources": [i["raw_resource"] for i in retained],
            "all_resources": [i["raw_resource"] for i in icons],
            "forbidden_asset_prefix": "assets/" + publisher.PREFIX,
        }
    publisher.write(output, "fixture.json", json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"fixture": str(output), "access": args.access, "count": count,
                      "catalog": len(icons), "backend": descriptor["backend"]}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--publisher", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--access", choices=("direct", "dynamic", "raw"), required=True)
    parser.add_argument("--count", required=True, help="Number of icons, or all")
    parser.add_argument("--agp", help="Consumer AGP override for compatibility checks")
    args = parser.parse_args()
    if args.count != "all" and not args.count.isdecimal():
        parser.error("--count must be a nonnegative integer or all")
    generate(args)


if __name__ == "__main__":
    main()
