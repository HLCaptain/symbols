#!/usr/bin/env python3
"""Model a Compose native-raw XML backend from its actual published source output.

Generated projects/artifacts stay outside this checkout. This is an upstream
code-generation experiment, not a production Symbols or Compose plugin option.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[3]
PACKAGE = "io.github.hlcaptain.symbols.material.outlined.compose.drawables.resources"
NAMESPACE = "io.github.hlcaptain.symbols.material.outlined.compose.drawables"
PREFIX = f"composeResources/{PACKAGE}/"
RAW_PREFIX = "symbols_probe_"
RUNTIME_VERSION = "1.12.2-native-raw01"
PROTOCOL = "compose-android-resource://"


def load_usage():
    spec = importlib.util.spec_from_file_location("usage_fixtures", Path(__file__).resolve().parents[1] / "fixtures.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def write(root, relative, text):
    if Path(relative).is_absolute() or ".." in Path(relative).parts:
        raise ValueError(f"Unsafe generated path: {relative}")
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def prepare(args):
    destination = args.output.resolve()
    if destination.exists():
        raise ValueError(f"Refusing to overwrite {destination}")
    versions = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())["versions"]
    usage = load_usage()
    icons = usage.catalog(ROOT / "fonts/material/MaterialSymbols.codepoints")
    if args.limit:
        icons = icons[:args.limit]
    names = {icon["resource"] for icon in icons}
    if not names:
        raise ValueError("Publisher corpus must contain at least one resource")
    for version in (args.version, RUNTIME_VERSION):
        if not re.fullmatch(r"[A-Za-z0-9_.+-]+", version):
            raise ValueError(f"Invalid Maven version: {version}")
    destination.mkdir(parents=True)
    for relative in ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties"):
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / relative, target)
    (destination / "gradlew").chmod(0o755)
    repository = args.repository.resolve()
    write(destination, "settings.gradle.kts", f'''pluginManagement {{ repositories {{ google(); mavenCentral(); gradlePluginPortal() }} }}
dependencyResolutionManagement {{ repositories {{
    maven {{ url = uri({json.dumps(repository.as_uri())}) }}
    google(); mavenCentral()
}} }}
rootProject.name = "symbols-material-compose-drawables-outlined"
''')
    runtime = RUNTIME_VERSION if args.backend == "native" else versions["composeMultiplatform"]
    write(destination, "build.gradle.kts", f'''plugins {{
    kotlin("multiplatform") version "{versions['kotlin']}"
    id("com.android.kotlin.multiplatform.library") version "{versions['agp']}"
    id("org.jetbrains.kotlin.plugin.compose") version "{versions['kotlin']}"
    `maven-publish`
}}
group = "io.github.hlcaptain"
version = "{args.version}"
kotlin {{
    jvmToolchain(17)
    android {{
        namespace = "{NAMESPACE}"
        compileSdk = 37
        minSdk = 23
        androidResources.enable = true
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        compilerOptions.moduleName.set("io.github.hlcaptain_material-compose-drawables-outlined")
    }}
    jvm {{ compilerOptions {{
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        moduleName.set("io.github.hlcaptain_material-compose-drawables-outlined")
    }} }}
    sourceSets.commonMain.dependencies {{
        api("org.jetbrains.compose.components:components-resources:{runtime}")
        implementation("org.jetbrains.compose.runtime:runtime:{versions['composeMultiplatform']}")
    }}
}}
publishing.repositories.maven {{ name = "probe"; url = uri({json.dumps(repository.as_uri())}) }}
''')
    write(destination, "gradle.properties", "android.useAndroidX=true\norg.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8\norg.gradle.workers.max=1\nkotlin.compiler.execution.strategy=in-process\n")

    with zipfile.ZipFile(args.sources) as source_zip, zipfile.ZipFile(args.aar) as aar:
        source_entries = {n: source_zip.read(n).decode() for n in source_zip.namelist() if n.endswith(".kt")}
        common = {n.split("commonMain/", 1)[1]: text for n, text in source_entries.items() if n.startswith("commonMain/")}
        collectors = next(text for n, text in source_entries.items() if n.endswith("ActualResourceCollectors.kt") and n.startswith("androidMain/"))
        active_collectors = []
        found = set()
        for relative, text in common.items():
            if Path(relative).name.startswith("Drawable"):
                first = text.index("@delegate:ResourceContentHash")
                header = text[:first]
                pattern = re.compile(r"@delegate:ResourceContentHash\([^\n]+\)\npublic val Res\.drawable\.([a-z0-9_]+):\s+DrawableResource\s+by\s+lazy \{.*?\n    \}\n", re.S)
                blocks = []
                for match in pattern.finditer(text):
                    name = match.group(1)
                    if name not in names:
                        continue
                    block = match.group(0)
                    original_path = f'"${{MD}}drawable/{name}.xml"'
                    if block.count(original_path) != 1:
                        raise ValueError(f"Unsupported accessor shape: {name}")
                    if args.backend == "native":
                        block = block.replace(original_path, f"nativePath_{name}()")
                    blocks.append(block)
                    found.add(name)
                if not blocks:
                    continue
                collector = re.search(r"internal fun (_collect\w+Resources)\(", text)
                if not collector:
                    raise ValueError(f"Missing collector in {relative}")
                collector_name = collector.group(1)
                active_collectors.append(collector_name)
                chosen = [m.group(1) for m in pattern.finditer(text) if m.group(1) in names]
                body = f"internal fun {collector_name}(map: MutableMap<String, DrawableResource>) {{\n"
                body += "".join(f'  map.put("{name}", Res.drawable.{name})\n' for name in chosen) + "}\n"
                write(destination, f"src/commonMain/kotlin/{relative}", header + "\n".join(blocks) + "\n" + body)
            elif Path(relative).name == "Res.kt" and args.backend == "native":
                old = json.dumps(PREFIX) + " + path"
                if text.count(old) != 2:
                    raise ValueError("Unsupported Res raw-access shape")
                write(destination, f"src/commonMain/kotlin/{relative}", text.replace(old, "platformResourcePath(path)"))
            else:
                write(destination, f"src/commonMain/kotlin/{relative}", text)
        if found != names:
            raise ValueError(f"Missing {len(names-found)} published accessors: {sorted(names-found)[:10]}")
        collectors = re.sub(r"^  (_collect\w+Resources)\(map\)\n", lambda m: m.group(0) if m.group(1) in active_collectors else "", collectors, flags=re.M)
        for target in ("androidMain", "jvmMain"):
            write(destination, f"src/{target}/kotlin/{PACKAGE.replace('.', '/')}/ActualResourceCollectors.kt", collectors)
        outputs = []
        for icon in icons:
            name = icon["resource"]
            data = aar.read(f"assets/{PREFIX}drawable/{name}.xml")
            raw_name = RAW_PREFIX + name
            for relative in [f"src/jvmMain/resources/{PREFIX}drawable/{name}.xml", f"src/androidMain/res/raw/{raw_name}.xml" if args.backend == "native" else f"src/androidMain/assets/{PREFIX}drawable/{name}.xml"]:
                path = destination / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
            outputs.append(dict(icon, raw_resource=raw_name, xml_sha256=digest(data), xml_bytes=len(data)))
        if args.backend == "native":
            base = f"package {PACKAGE}\n\n"
            declarations = base + "internal expect fun platformResourcePath(path: String): String\n"
            android = base + f"import {NAMESPACE}.R\n\n"
            jvm = base + f'internal actual fun platformResourcePath(path: String): String = "{PREFIX}" + path\n'
            for icon in icons:
                name = icon["resource"]
                declarations += f"internal expect fun nativePath_{name}(): String\n"
                android += f'internal actual fun nativePath_{name}(): String = "{PROTOCOL}${{R.raw.{RAW_PREFIX}{name}}}/drawable.xml"\n'
                jvm += f'internal actual fun nativePath_{name}(): String = "{PREFIX}drawable/{name}.xml"\n'
            # A raw lookup intentionally keeps the owning pack reachable. Split
            # dispatchers to stay below the JVM method-size limit for full packs.
            chunks = [icons[i:i+64] for i in range(0, len(icons), 64)]
            android += "\ninternal actual fun platformResourcePath(path: String): String {\n"
            for index in range(len(chunks)):
                android += f"    rawPath{index}(path)?.let {{ return it }}\n"
            android += f'    return "{PREFIX}" + path\n}}\n'
            for index, chunk in enumerate(chunks):
                android += f"private fun rawPath{index}(path: String): String? = when(path) {{\n"
                android += "".join(f'    "drawable/{i["resource"]}.xml" -> nativePath_{i["resource"]}()\n' for i in chunk)
                android += "    else -> null\n}\n"
            for target, text in (("commonMain", declarations), ("androidMain", android), ("jvmMain", jvm)):
                write(destination, f"src/{target}/kotlin/{PACKAGE.replace('.', '/')}/NativeResourcePaths.kt", text)
        for name in aar.namelist():
            if name.startswith("META-INF/") and (name.endswith("LICENSE") or name.endswith("THIRD_PARTY_NOTICES.md")):
                if ".." in Path(name).parts:
                    raise ValueError(f"Unsafe archive path: {name}")
                for target in ("androidMain", "jvmMain"):
                    path = destination / f"src/{target}/resources" / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(aar.read(name))
    manifest = dict(schema_version=1, backend=args.backend, version=args.version, runtime_version=runtime,
                    coordinates=f"io.github.hlcaptain:symbols-material-compose-drawables-outlined:{args.version}",
                    resource_package=PACKAGE, namespace=NAMESPACE, protocol=PROTOCOL,
                    source_aar_sha256=digest(args.aar.read_bytes()), source_jar_sha256=digest(args.sources.read_bytes()),
                    items=outputs)
    write(destination, "publisher.json", json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"project": str(destination), "backend": args.backend, "resources": len(outputs), "coordinates": manifest["coordinates"]}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aar", type=Path, required=True)
    parser.add_argument("--sources", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--backend", choices=("assets", "native"), required=True)
    parser.add_argument("--limit", type=int, help="Bounded corpus; omit for the complete published pack")
    args = parser.parse_args()
    if args.limit is not None and args.limit < 1:
        parser.error("--limit must be positive")
    prepare(args)


if __name__ == "__main__":
    main()
