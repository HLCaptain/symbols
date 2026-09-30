#!/usr/bin/env python3
"""Compile independent consumers of the published Symbols plugin and runtime."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time
import tomllib
import zipfile


ROOT = Path(__file__).resolve().parents[2]
PROFILES = ("android-app", "android-java", "android-library", "kmp", "jvm")
# Verified official release pairs, including the JVM runtime wrapper's implementation.
COMPOSE_ANDROIDX_VERSIONS = {"1.11.1": "1.11.2", "1.12.1": "1.12.1"}


def compose_runtime_report(configuration):
    """Resolve real artifacts lazily; task actions receive only serializable inputs."""
    return '''
        abstract class ReportComposeRuntime : DefaultTask() {
            @get:Input abstract val modules: ListProperty<String>
            @get:OutputFile abstract val reportFile: RegularFileProperty
            @TaskAction fun report() {
                val target = reportFile.get().asFile
                target.parentFile.mkdirs()
                target.writeText(modules.get().joinToString("\\n", postfix = "\\n"))
            }
        }
        tasks.register<ReportComposeRuntime>("reportComposeRuntime") {
            modules.set(configurations.named("CONFIGURATION").flatMap {
                it.incoming.artifacts.resolvedArtifacts
            }.map { artifacts ->
                artifacts.mapNotNull { artifact ->
                    val id = artifact.id.componentIdentifier as?
                        org.gradle.api.artifacts.component.ModuleComponentIdentifier
                    id?.takeIf {
                        it.group.startsWith("org.jetbrains.compose.") ||
                            it.group.startsWith("androidx.compose.")
                    }?.let { "${it.group}:${it.module}:${it.version}" }
                }.distinct().sorted()
            })
            reportFile.set(layout.buildDirectory.file("reports/compose-runtime.txt"))
        }
    '''.replace("CONFIGURATION", configuration)


def verify_compose_runtime(path, compose_version, android, *, resources_version=None):
    modules = {}
    for line in path.read_text().splitlines():
        group, module, version = line.split(":")
        coordinate = f"{group}:{module}"
        if coordinate in modules and modules[coordinate] != version:
            raise ValueError(f"Conflicting resolved versions for {coordinate} in {path}")
        modules[coordinate] = version
    # The selected JVM wrapper also delegates to AndroidX runtime-desktop.
    # https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
    if compose_version not in COMPOSE_ANDROIDX_VERSIONS:
        raise ValueError(f"No verified AndroidX artifact mapping for Compose {compose_version}")
    android_version = COMPOSE_ANDROIDX_VERSIONS[compose_version]
    families = [("org.jetbrains.compose.components", "components-resources", resources_version or compose_version)]
    prefix = "androidx.compose" if android else "org.jetbrains.compose"
    families += [(f"{prefix}.{name}", name, android_version if android else compose_version)
                 for name in ("ui", "foundation", "runtime")]
    if not android:
        families.append(("androidx.compose.runtime", "runtime", android_version))
    for group, name, expected in families:
        candidates = {f"{group}:{name}{suffix}" for suffix in ("", "-android", "-desktop", "-jvm")}
        selected = {module: version for module, version in modules.items() if module in candidates}
        if not selected or any(version != expected for version in selected.values()):
            raise ValueError(
                f"Compose runtime mismatch in {path}: expected {group}:{name} {expected}, "
                f"resolved {selected or 'no matching artifact'}. Check transitive dependencies; "
                "a requested version alone does not establish compatibility."
            )
    return modules


def fixture(args, directory):
    android = args.profile != "jvm"
    kmp = args.profile == "kmp"
    compose_resources = args.compose_resources
    font_descriptors = compose_resources and not args.compose_drawables_only
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
    if compose_resources:
        plugins += [f'id("org.jetbrains.compose") version "{args.compose_version}"']
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
    if compose_resources:
        resource_scope = "api" if args.compose_drawables_only else "implementation"
        dependencies += f'''
            {resource_scope}("org.jetbrains.compose.components:components-resources:{args.compose_version}")
        '''
    if font_descriptors:
        dependencies += f'implementation("io.github.hlcaptain:symbols-variant-font-core:{args.version}")\n'
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
    if compose_resources:
        platform += '''
            compose.resources {
                packageOfResClass = "example.resources"
                publicResClass = true
                generateResClass = always
            }
        '''
        if font_descriptors:
            platform += '''
            val preparedFonts = tasks.register<Sync>("prepareFixtureFonts") {
                from(layout.projectDirectory.dir("fixture-fonts")) { into("font") }
                into(layout.buildDirectory.dir("preparedFixtureFonts"))
            }
            symbolFonts.composeFontResources.from(preparedFonts.map { it.destinationDir })
        '''
        platform += compose_runtime_report("jvmRuntimeClasspath")

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
        "composeDrawables()" if compose_resources else "androidDrawables()" if android else "",
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
        if not java_only and not compose_resources:
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
    if compose_resources:
        if font_descriptors:
            font = directory / "fixture-fonts/powerline_symbols.otf"
            font.parent.mkdir(exist_ok=True)
            shutil.copy2(ROOT / "samples/custom-static/src/commonMain/composeResources/font/powerline_symbols.otf", font)
        source = directory / "src/commonMain/kotlin/example/ResourceConsumer.kt"
        source.write_text('''
            package example
            import example.resources.*
            DESCRIPTOR
            @androidx.compose.runtime.Composable
            fun ResourcePreview() {
                androidx.compose.foundation.Image(
                    org.jetbrains.compose.resources.painterResource(Res.drawable.fixture_icons_outlined_check), null,
                )
            }
        '''.replace("DESCRIPTOR", "fun fontDescriptor() = Res.symbolFonts.powerline_symbols"
                    if font_descriptors else
                    "fun drawableDescriptor() = Res.drawable.fixture_icons_outlined_check"))


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
    if args.compose_resources:
        source = sources / "java/example/ConsumerActivity.java"
        source.write_text(source.read_text().replace(
            "image.setImageResource(example.consumer.R.drawable.fixture_icons_outlined_check);",
            ('setTitle(String.valueOf(example.ResourceConsumerKt.drawableDescriptor()));'
             if args.compose_drawables_only else
             'setTitle(example.ResourceConsumerKt.fontDescriptor().getFamilyName());'),
        ))
        build = directory / "build.gradle.kts"
        if not args.compose_drawables_only:
            build.write_text(build.read_text() + f'\ndependencies {{ implementation("io.github.hlcaptain:symbols-variant-font-core:{args.version}") }}\n')
        build.write_text(build.read_text() + compose_runtime_report("releaseRuntimeClasspath"))
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
    parser.add_argument("--compose-resources", action="store_true", help="For kmp, test Compose-only resources and font descriptors instead of native XML.")
    parser.add_argument("--compose-drawables-only", action="store_true",
                        help="Implies --compose-resources; omit the current-version font runtime to test older official Compose runtimes.")
    args = parser.parse_args()
    args.compose_resources = args.compose_resources or args.compose_drawables_only
    if args.compose_resources and args.profile != "kmp":
        parser.error("--compose-resources applies only to --profile kmp")
    if args.compose_resources and args.compose_version not in COMPOSE_ANDROIDX_VERSIONS:
        parser.error("Add a verified COMPOSE_ANDROIDX_VERSIONS entry for the requested Compose release")
    args.repository = args.repository.resolve()
    suffix = "-compose-drawables" if args.compose_drawables_only else "-compose" if args.compose_resources else ""
    directory = args.output.resolve() / (args.profile + suffix)
    fixture(args, directory)
    (directory / "result.json").unlink(missing_ok=True)
    projects = [(directory, ["assemble"])]
    if args.profile == "kmp":
        projects[0][1].append("publishAllPublicationsToFixtureRepository")
        projects.append((published_kmp_consumer(args, directory), ["assemble"]))
    results = []
    resolved_compose = {}
    for project, tasks in projects:
        if args.compose_resources:
            tasks.append("reportComposeRuntime")
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
        if args.compose_resources:
            resolved_compose["producer-jvm" if project == directory else "consumer-android"] = verify_compose_runtime(
                project / "build/reports/compose-runtime.txt", args.compose_version, project != directory,
            )
            archive_path = (
                next((project / "build/outputs/apk/release").glob("*.apk"))
                if project != directory else
                next((project / "published").glob("**/*.aar"))
            )
            with zipfile.ZipFile(archive_path) as archive:
                prefix = "assets/composeResources/example.resources/"
                if not args.compose_drawables_only:
                    font = (directory / "fixture-fonts/powerline_symbols.otf").read_bytes()
                    if archive.read(prefix + "font/powerline_symbols.otf") != font:
                        raise SystemExit(f"Generated font missing or changed in {archive_path}")
                if not archive.read(prefix + "drawable/fixture_icons_outlined_check.xml"):
                    raise SystemExit(f"Generated Compose drawable missing in {archive_path}")
        elif args.profile in ("android-app", "android-java") or project != directory:
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
        "compose_resources": args.compose_resources,
        "compose_drawables_only": args.compose_drawables_only,
        "resolved_compose": resolved_compose,
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
