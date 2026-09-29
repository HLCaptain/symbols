#!/usr/bin/env python3
"""Generate independent published-artifact Android consumers for an icon usage sweep."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BACKENDS = {"vectors": "symbols-material-vectors-outlined", "android": "symbols-material-drawables-outlined", "compose": "symbols-material-compose-drawables-outlined"}
COUNTS = ("0", "1", "25", "200", "all")
RESOURCE_PACKAGE = "io.github.hlcaptain.symbols.material.outlined.compose.drawables.resources"


def kotlin_string(value):
    return json.dumps(value).replace("$", "\\$")


def preserve_aar_notices(aar, fixture):
    """Match controls that carry the original AAR notices into the final APK."""
    entries = {}
    with zipfile.ZipFile(aar) as archive:
        for info in archive.infolist():
            name = info.filename
            if info.is_dir() or not name.startswith("META-INF/") or Path(name).name not in {"LICENSE", "THIRD_PARTY_NOTICES.md"}:
                continue
            if ".." in Path(name).parts or name in entries:
                raise ValueError(f"Unsafe or duplicate AAR notice: {name}")
            entries[name] = archive.read(info)
    if not entries:
        raise ValueError(f"No original AAR license/notices found: {aar}")
    for name, data in entries.items():
        target = fixture / "src/main/resources" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    return {"aar_sha256": hashlib.sha256(aar.read_bytes()).hexdigest(),
            "entries": {name: {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
                        for name, data in entries.items()}}


def catalog(path):
    # These are the same canonical-name and numeric-name rules as the library generators.
    aliases = {}
    for line in path.read_text().splitlines():
        if not re.fullmatch(r"[a-z0-9]+(?:_[a-z0-9]+)*\s+[0-9a-fA-F]{1,6}", line):
            raise ValueError(f"Invalid codepoint row: {line!r}")
        name, value = line.split()
        aliases.setdefault(int(value, 16), []).append(name)
    result = []
    for codepoint, names in aliases.items():
        name = min(names)
        identifier = "".join(part[0].upper() + part[1:] for part in name.split("_"))
        if identifier[0].isdigit(): identifier = "_" + identifier
        result.append(dict(name=name, codepoint=codepoint, identifier=identifier, resource=f"material_symbols_outlined_{name}_u{codepoint:x}"))
    return sorted(result, key=lambda item: item["name"])


def renderer(backend, access, icons):
    imports = ["import androidx.compose.runtime.Composable", "import androidx.compose.ui.graphics.painter.Painter"]
    if backend == "vectors":
        imports += ["import androidx.compose.ui.graphics.vector.rememberVectorPainter", "import io.github.hlcaptain.symbols.Symbols", "import io.github.hlcaptain.symbols.material.*", "import io.github.hlcaptain.symbols.material.outlined.vectors.*"]
        expression = lambda icon: f'rememberVectorPainter(Symbols.Material.Outlined.{icon["identifier"]})'
    elif backend == "android":
        imports += ["import androidx.compose.ui.res.painterResource", "import io.github.hlcaptain.symbols.material.outlined.drawables.R as SymbolsR"]
        expression = lambda icon: f'painterResource(SymbolsR.drawable.{icon["resource"]})'
    else:
        imports += ["import org.jetbrains.compose.resources.painterResource", f"import {RESOURCE_PACKAGE}.*"]
        expression = lambda icon: f'painterResource(Res.drawable.{icon["resource"]})'
    code = ["package study", "", *imports, ""]
    if access == "dynamic":
        if backend == "vectors":
            code += ["private val catalog = Symbols.Material.all.associateBy { it.name }", "@Composable", "internal fun iconPainter(index: Int, name: String, resource: String): Painter =", "    rememberVectorPainter(catalog.getValue(name).asOutlinedImageVector())"]
        elif backend == "android":
            chunks = [icons[index:index+64] for index in range(0, len(icons), 64)]
            code += ["private val drawables: Map<String, Int> by lazy {", "    buildMap {"]
            code += [f"        addDrawables{chunk}(this)" for chunk in range(len(chunks))]
            code += ["    }", "}", "@Composable", "internal fun iconPainter(index: Int, name: String, resource: String): Painter =", "    painterResource(drawables.getValue(resource))"]
            for chunk, entries in enumerate(chunks):
                code += ["", f"private fun addDrawables{chunk}(catalog: MutableMap<String, Int>) {{"]
                code += [f'    catalog["{icon["resource"]}"] = SymbolsR.drawable.{icon["resource"]}' for icon in entries]
                code += ["}"]
        else:
            code += ["@Composable", "internal fun iconPainter(index: Int, name: String, resource: String): Painter =", "    painterResource(Res.allDrawableResources.getValue(resource))"]
    elif not icons:
        code += ["@Composable", 'internal fun iconPainter(index: Int, name: String, resource: String): Painter = error("No icons selected")']
    else:
        chunks = [icons[index:index+64] for index in range(0, len(icons), 64)]
        code += ["@Composable", "internal fun iconPainter(index: Int, name: String, resource: String): Painter = when (index / 64) {"]
        code += [f"    {chunk} -> iconChunk{chunk}(index)" for chunk in range(len(chunks))]
        code += ['    else -> error("Invalid icon index: $index")', "}"]
        for chunk, entries in enumerate(chunks):
            code += ["", "@Composable", f"private fun iconChunk{chunk}(index: Int): Painter = when (index) {{"]
            code += [f"    {chunk*64+offset} -> {expression(icon)}" for offset, icon in enumerate(entries)]
            code += ['    else -> error("Invalid icon index: $index")', "}"]
    return "\n".join(code) + "\n"


ACTIVITY = '''package study

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val icons = assets.open("selection.tsv").bufferedReader().use { input ->
            input.readLines().filter { it.isNotBlank() }.map { it.split('\\t') }
        }
        val first = intent.getIntExtra("first", 0).coerceIn(0, (icons.size - 1).coerceAtLeast(0))
        setContent {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                state = rememberLazyGridState(initialFirstVisibleItemIndex = first),
                modifier = Modifier.fillMaxSize().background(Color.White),
            ) {
                itemsIndexed(icons) { index, icon ->
                    Image(
                        painter = iconPainter(index, icon[0], icon[1]),
                        contentDescription = icon[0],
                        colorFilter = ColorFilter.tint(Color.Black),
                        modifier = Modifier.padding(8.dp).size(48.dp),
                    )
                }
            }
        }
    }
}
'''


def generate(root, destination, repository, version, backend, access, count, versions, wrapper):
    if destination.exists():
        raise ValueError(f"Refusing to overwrite {destination}")
    icons = catalog(root / "fonts/material/MaterialSymbols.codepoints")
    selected = icons if count == "all" else icons[:int(count)]
    if count != "all" and len(selected) != int(count):
        raise ValueError("Selection exceeds available unique glyphs")
    destination.mkdir(parents=True)
    for relative in ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties"):
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(wrapper / relative, target)
    (destination / "gradlew").chmod(0o755)
    (destination / "settings.gradle.kts").write_text(f'''pluginManagement {{ repositories {{ google(); mavenCentral(); gradlePluginPortal() }} }}
rootProject.name = "symbols-usage-{backend}-{access}-{count}"
dependencyResolutionManagement {{
    repositories {{
        exclusiveContent {{
            forRepository {{ maven {{ url = uri({kotlin_string(repository.as_uri())}) }} }}
            filter {{ includeGroup("io.github.hlcaptain") }}
        }}
        google()
        mavenCentral()
    }}
}}
''')
    agp, kotlin, compose = (versions[key] for key in ("agp", "kotlin", "composeMultiplatform"))
    builtin = int(agp.split(".")[0]) >= 9
    kotlin_plugin = "" if builtin else f'    id("org.jetbrains.kotlin.android") version "{kotlin}"\n'
    classpath = f'''buildscript {{
    repositories {{ google(); mavenCentral() }}
    dependencies {{ classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:{kotlin}") }}
}}
''' if builtin else ""
    build = f'''{classpath}plugins {{
    id("com.android.application") version "{agp}"
{kotlin_plugin}    id("org.jetbrains.kotlin.plugin.compose") version "{kotlin}"
}}
android {{
    namespace = "study"
    compileSdk = {versions["android-compileSdk"]}
    defaultConfig {{
        applicationId = "io.github.hlcaptain.symbols.usage.{backend}.{access}"
        minSdk = 23
        targetSdk = {versions["android-targetSdk"]}
        versionCode = 1
        versionName = "1"
    }}
    buildFeatures {{ compose = true }}
    androidResources {{ noCompress += "ttf" }}
    buildTypes {{
        getByName("release") {{
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }}
        create("shrunk") {{
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }}
    }}
    compileOptions {{
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }}
}}
kotlin {{ compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }}
dependencies {{
    implementation("io.github.hlcaptain:{BACKENDS[backend]}:{version}")
    implementation("androidx.activity:activity-compose:{versions["androidx-activity"]}")
    implementation("org.jetbrains.compose.foundation:foundation:{compose}")
    implementation("org.jetbrains.compose.ui:ui:{compose}")
}}
'''
    (destination / "build.gradle.kts").write_text(build)
    (destination / "gradle.properties").write_text("android.useAndroidX=true\nandroid.nonTransitiveRClass=true\norg.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8\n" + ("" if builtin else "android.r8.optimizedResourceShrinking=true\n"))
    source = destination / "src/main/kotlin/study"
    source.mkdir(parents=True)
    (source / "MainActivity.kt").write_text(ACTIVITY)
    (source / "Icons.kt").write_text(renderer(backend, access, icons if access == "dynamic" else selected))
    assets = destination / "src/main/assets"
    assets.mkdir(parents=True)
    (assets / "selection.tsv").write_text("".join(f'{icon["name"]}\t{icon["resource"]}\n' for icon in selected))
    (destination / "src/main/AndroidManifest.xml").write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Symbols usage study" android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name="study.MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
''')
    manifest = {"schema_version": 1, "backend": backend, "access": access, "count": len(selected), "all_unique_glyphs": len(icons), "coordinates": f"io.github.hlcaptain:{BACKENDS[backend]}:{version}", "repository": str(repository), "versions": versions, "codepoints_sha256": hashlib.sha256((root / "fonts/material/MaterialSymbols.codepoints").read_bytes()).hexdigest(), "selected": selected, "all_resources": [icon["resource"] for icon in icons]}
    (destination / "fixture.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=ROOT)
    parser.add_argument("--wrapper-from", type=Path, help="Wrapper files to use; defaults to source root")
    parser.add_argument("--catalog", type=Path, help="Toolchain catalog; defaults to source root's catalog")
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--version", required=True, help="Version already published into the local repository")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--backend", action="append", choices=BACKENDS)
    parser.add_argument("--access", action="append", choices=("direct", "dynamic"))
    parser.add_argument("--count", action="append", choices=COUNTS)
    parser.add_argument("--preserve-aar-notices", type=Path,
                        help="Benchmark normalization: copy this AAR's original META-INF legal notices into app Java resources")
    args = parser.parse_args()
    root = args.source_root.resolve()
    repository = args.repository.resolve()
    if not repository.is_dir(): parser.error("--repository must be an existing local Maven directory")
    versions = tomllib.loads((args.catalog or root / "gradle/libs.versions.toml").read_text())["versions"]
    for value in (args.version, versions["agp"], versions["kotlin"], versions["composeMultiplatform"], versions["androidx-activity"]):
        if not re.fullmatch(r"[A-Za-z0-9_.+-]+", value): parser.error(f"Invalid version: {value!r}")
    for backend in dict.fromkeys(args.backend or BACKENDS):
        for access in dict.fromkeys(args.access or ("direct", "dynamic")):
            for count in dict.fromkeys(args.count or COUNTS):
                path = args.output.resolve() / f"{backend}-{access}-{count}"
                manifest = generate(root, path, repository, args.version, backend, access, count, versions, (args.wrapper_from or root).resolve())
                if args.preserve_aar_notices:
                    manifest["preserved_aar_notices"] = preserve_aar_notices(args.preserve_aar_notices.resolve(), path)
                    (path / "fixture.json").write_text(json.dumps(manifest, indent=2) + "\n")
                print(f"Generated {path}: {manifest['count']} unique glyphs; {manifest['coordinates']}")


if __name__ == "__main__":
    main()
