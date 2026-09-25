#!/usr/bin/env python3
"""Compile independent consumers of the published Symbols plugin and runtime."""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import tomllib
import zipfile


ROOT = Path(__file__).resolve().parents[2]
PROFILES = ("android-app", "android-java", "android-library", "kmp", "jvm")


def fixture(args, directory):
    android = args.profile != "jvm"
    kmp = args.profile == "kmp"
    java_only = args.profile == "android-java"
    application = args.profile in ("android-app", "android-java")
    min_sdk = 21 if java_only else 23
    plugins = [f'id("io.github.hlcaptain.symbol-fonts") version "{args.version}"']
    if kmp:
        plugins += [f'kotlin("multiplatform") version "{args.kotlin_version}"']
        plugins += ['`maven-publish`']
    elif java_only:
        pass
    elif not android or args.profile.startswith("android") and args.agp_version.startswith("8."):
        kotlin_plugin = "jvm" if not android else "android"
        plugins += [f'kotlin("{kotlin_plugin}") version "{args.kotlin_version}"']
    else:
        # Upgrade AGP's built-in Kotlin compiler without applying kotlin-android.
        plugins += [f'kotlin("jvm") version "{args.kotlin_version}" apply false']
    if not java_only:
        plugins += [f'id("org.jetbrains.kotlin.plugin.compose") version "{args.kotlin_version}"']
    if android:
        android_plugin = {
            "android-app": "com.android.application",
            "android-java": "com.android.application",
            "android-library": "com.android.library",
            "kmp": "com.android.kotlin.multiplatform.library",
        }[args.profile]
        plugins += [f'id("{android_plugin}") version "{args.agp_version}"']

    dependencies = f'''
        implementation("io.github.hlcaptain:symbols-core:{args.version}")
        implementation("org.jetbrains.compose.ui:ui:{args.compose_version}")
        implementation("org.jetbrains.compose.foundation:foundation:{args.compose_version}")
    '''
    if java_only:
        dependencies = f'implementation("io.github.hlcaptain:symbols-material-drawables-outlined:{args.version}")'
    settings = f'''
        pluginManagement {{
            repositories {{
                maven {{ url = uri({json.dumps(args.repository.as_uri())}) }}
                google()
                mavenCentral()
                gradlePluginPortal()
            }}
        }}
        dependencyResolutionManagement {{
            repositories {{
                maven {{ url = uri({json.dumps(args.repository.as_uri())}) }}
                google()
                mavenCentral()
            }}
        }}
        rootProject.name = "symbols-{args.profile}-consumer"
    '''
    if args.profile == "kmp":
        platform = f'''
            kotlin {{
                android {{ namespace = "example.consumer"; compileSdk = {args.compile_sdk}; minSdk = 23 }}
                jvm()
                sourceSets.commonMain.dependencies {{ {dependencies} }}
            }}
        '''
    elif android:
        platform = f'''
            android {{ namespace = "example.consumer"; compileSdk = {args.compile_sdk}; defaultConfig {{ minSdk = {min_sdk} }} }}
            dependencies {{ {dependencies} }}
        '''
    else:
        platform = f'dependencies {{ {dependencies} }}'
    if java_only and not args.agp_version.startswith("8."):
        platform += "\nandroid { enableKotlin = false }\n"
    elif android and not kmp:
        platform += "\nandroid { buildFeatures { compose = true } }\n"
    if application:
        platform += '''
            android.buildTypes.named("release") {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(android.getDefaultProguardFile("proguard-android-optimize.txt"))
            }
        '''
    if kmp:
        platform += '''
            group = "example.compatibility"
            version = "1.0"
            publishing.repositories.maven {
                name = "fixture"
                url = uri(layout.projectDirectory.dir("published"))
            }
        '''

    build = '\n'.join([
        "plugins {", *plugins, "}", platform,
        '''
        symbolFonts {
            iconSet("FixtureIcons") {
                packageName.set("example.generated")
                style("Outlined") {
                    svgDirectory.set(layout.projectDirectory.dir("icons"))
        ''',
        "imageVectors()" if not java_only else "",
        "androidDrawables()" if android else "",
        "} } }",
    ])
    sources = "commonMain" if kmp else "main"
    files = {
        "settings.gradle.kts": settings,
        "build.gradle.kts": build,
        "gradle.properties": "org.gradle.jvmargs=-Xmx2g\norg.gradle.workers.max=2\nkotlin.compiler.execution.strategy=in-process\n",
        "icons/check.svg": '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M5 12L9 16L19 6Z"/></svg>',
        "icons/unused.svg": '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0L24 0L24 24Z"/></svg>',
    }
    if args.agp_version.startswith("8."):
        files["gradle.properties"] += "android.r8.optimizedResourceShrinking=true\n"
    if not java_only:
        files[f"src/{sources}/kotlin/example/Consumer.kt"] = '''
            package example
            import example.generated.FixtureIcons
            import example.generated.outlined.Check
            fun icon() = FixtureIcons.Outlined.Check
            @androidx.compose.runtime.Composable
            fun IconPreview() {
                androidx.compose.foundation.Image(icon(), contentDescription = null)
            }
        '''
    if android:
        android_sources = "androidMain" if kmp else "main"
        files[f"src/{android_sources}/AndroidManifest.xml"] = '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application/></manifest>'
        if not java_only:
            files[f"src/{android_sources}/kotlin/example/AndroidResource.kt"] = '''
            package example
            fun drawable() = example.consumer.R.drawable.fixture_icons_outlined_check
        '''
    if application:
        files["src/main/AndroidManifest.xml"] = '''
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application><activity android:name="example.ConsumerActivity" android:exported="false"/></application>
            </manifest>
        '''
        title = '"Native drawable"' if java_only else "example.ConsumerKt.icon().getName()"
        files["src/main/java/example/ConsumerActivity.java"] = f'''
            package example;
            public final class ConsumerActivity extends android.app.Activity {{
                @Override public void onCreate(android.os.Bundle state) {{
                    super.onCreate(state);
                    setTitle({title});
                    android.widget.ImageView image = new android.widget.ImageView(this);
                    image.setImageResource(example.consumer.R.drawable.fixture_icons_outlined_check);
                    {"image.setBackgroundResource(io.github.hlcaptain.symbols.material.outlined.drawables.R.drawable.material_symbols_outlined_home_ue9b2);" if java_only else ""}
                    setContentView(image);
                }}
            }}
        '''
    directory.mkdir(parents=True, exist_ok=True)
    for name, contents in files.items():
        target = directory / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(contents)


def published_kmp_consumer(args, library):
    """Consume the fixture AAR through Maven, without a project dependency."""
    directory = library / "android-consumer"
    directory.mkdir(exist_ok=True)
    settings = (library / "settings.gradle.kts").read_text().replace(
        'rootProject.name = "symbols-kmp-consumer"',
        'rootProject.name = "symbols-kmp-android-consumer"',
    ).replace("google()", f'maven {{ url = uri({json.dumps((library / "published").as_uri())}) }}\n google()')
    (directory / "settings.gradle.kts").write_text(settings)
    (directory / "gradle.properties").write_text((library / "gradle.properties").read_text())
    (directory / "build.gradle.kts").write_text(f'''
        plugins {{
            id("com.android.application") version "{args.agp_version}"
        }}
        android {{
            namespace = "example.application"
            compileSdk = {args.compile_sdk}
            defaultConfig {{ minSdk = 23 }}
            buildTypes.named("release") {{
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            }}
        }}
        dependencies {{
            implementation("example.compatibility:symbols-kmp-consumer:1.0")
            implementation("org.jetbrains.compose.ui:ui:{args.compose_version}")
        }}
    ''')
    sources = directory / "src/main"
    (sources / "java/example").mkdir(parents=True, exist_ok=True)
    (sources / "AndroidManifest.xml").write_text('''
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application><activity android:name="example.ConsumerActivity" android:exported="false"/></application>
        </manifest>
    ''')
    (sources / "java/example/ConsumerActivity.java").write_text('''
        package example;
        public final class ConsumerActivity extends android.app.Activity {
            @Override public void onCreate(android.os.Bundle state) {
                super.onCreate(state);
                setTitle(example.ConsumerKt.icon().getName());
                android.widget.ImageView image = new android.widget.ImageView(this);
                image.setImageResource(example.consumer.R.drawable.fixture_icons_outlined_check);
                setContentView(image);
            }
        }
    ''')
    return directory


def main():
    versions = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())["versions"]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", choices=PROFILES, required=True)
    parser.add_argument("--gradle", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--kotlin-version", default=versions["kotlin"])
    parser.add_argument("--agp-version", default=versions["agp"])
    parser.add_argument("--compose-version", default=versions["composeMultiplatform"])
    parser.add_argument("--compile-sdk", type=int, default=int(versions["android-compileSdk"]))
    parser.add_argument("--output", type=Path, default=ROOT / "build/reports/tooling-compatibility")
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    args.repository = args.repository.resolve()
    directory = args.output.resolve() / args.profile
    fixture(args, directory)
    (directory / "result.json").unlink(missing_ok=True)
    projects = [(directory, ["assemble"])]
    if args.profile == "kmp":
        projects[0][1].append("publishAllPublicationsToFixtureRepository")
        projects.append((published_kmp_consumer(args, directory), ["assemble"]))
    results = []
    for project, tasks in projects:
        command = [str(args.gradle.resolve()), "-p", str(project), *tasks,
                   "--no-daemon", "--configuration-cache", "--no-build-cache", "--console=plain", "--stacktrace"]
        if args.offline:
            command.append("--offline")
        for stage in ("first", "reuse"):
            started = time.monotonic()
            log = project / f"{stage}.log"
            with log.open("w") as output:
                result = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT)
            results.append({"stage": stage, "seconds": round(time.monotonic() - started, 3),
                            "exit_code": result.returncode, "log": str(log), "command": command})
            print(json.dumps(results[-1]), flush=True)
            if result.returncode:
                raise SystemExit(result.returncode)
            if stage == "reuse" and "Configuration cache entry reused." not in log.read_text():
                raise SystemExit(f"Configuration cache was not reused: {log}")
        if args.profile in ("android-app", "android-java") or project != directory:
            report = (project / "build/outputs/mapping/release/resources.txt").read_text()
            # R8 9 also lists removed resources; R8 8 only lists reachable ones.
            report = "\n".join(line for line in report.splitlines() if " is not reachable." not in line)
            if "drawable:fixture_icons_outlined_check:" not in report:
                raise SystemExit("The shrunk app lost its referenced drawable")
            if "drawable:fixture_icons_outlined_unused:" in report:
                raise SystemExit("The shrunk app retained the unreferenced drawable")
            if args.profile == "android-java":
                if "drawable:material_symbols_outlined_home_ue9b2:" not in report:
                    raise SystemExit("The shrunk app lost its published-pack drawable")
                if "drawable:material_symbols_outlined_check_ue5ca:" in report:
                    raise SystemExit("The shrunk app retained an unused published-pack drawable")
            apk = next((project / "build/outputs/apk/release").glob("*.apk"))
            with zipfile.ZipFile(apk) as archive:
                resources = archive.read("resources.arsc")
            names = {"fixture_icons_outlined_check": True, "fixture_icons_outlined_unused": False}
            if args.profile == "android-java":
                names.update(material_symbols_outlined_home_ue9b2=True, material_symbols_outlined_check_ue5ca=False)
            for name, retained in names.items():
                if (name.encode() in resources) != retained:
                    raise SystemExit(f"Unexpected drawable retention in {apk}: {name}")
    summary = {
        "profile": args.profile, "version": args.version,
        "plugin_sha256": hashlib.sha256((args.repository /
            f"io/github/hlcaptain/symbol-gradle-plugin/{args.version}/"
            f"symbol-gradle-plugin-{args.version}.jar").read_bytes()).hexdigest(),
        "kotlin": args.kotlin_version, "agp": args.agp_version,
        "compose": args.compose_version, "runs": results,
    }
    (directory / "result.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps({"profile": args.profile, "plugin_sha256": summary["plugin_sha256"], "verified": True}))


if __name__ == "__main__":
    main()
