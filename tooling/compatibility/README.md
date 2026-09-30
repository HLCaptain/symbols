# Published plugin compatibility

The tooling wrapper stays on Gradle 8.14.5 so the published plugin can run on
both Gradle 8 and 9. The root build uses the current application toolchain.
Always build the release tooling artifact with `tooling/gradlew -p tooling`.

Publish a disposable version to a private local Maven directory first:

```shell
./tooling/gradlew -p tooling publishToMavenLocal \
  -PVERSION_NAME=0.0.0-upgrade-test -Dmaven.repo.local=/tmp/symbols-upgrade-maven
./gradlew :modules:symbols-core:publishToMavenLocal \
  :modules:material-drawables-outlined:publishToMavenLocal \
  :modules:variant-font-core:publishToMavenLocal \
  -PVERSION_NAME=0.0.0-upgrade-test -Dmaven.repo.local=/tmp/symbols-upgrade-maven
```

`check.py` creates independent Kotlin DSL consumers under `build/reports`,
resolves the plugin marker and runtime from that Maven directory, compiles the
generated vector getter and Android resource reference, and requires a second
build to reuse the configuration cache. It uses no composite build, injected
TestKit classpath, generator classpath override, or snapshot of generated code.

Run each of `android-app`, `android-java`, `android-library`, `kmp`, and `jvm`
with the current catalog versions. `android-java` consumes native XML without
Kotlin or Compose dependencies, including the published Outlined drawable pack.
Both app profiles build an R8-minified release and verify that used drawables
survive while unused drawables are removed. The `kmp` case publishes its generated
library to another local Maven repository and compiles a separate Android app
against that AAR, exercising the complete transitive resource boundary:

```shell
python3 tooling/compatibility/check.py --profile kmp --gradle ./gradlew \
  --repository /tmp/symbols-upgrade-maven --version 0.0.0-upgrade-test
python3 tooling/compatibility/check.py --profile kmp --compose-resources --gradle ./gradlew \
  --repository /tmp/symbols-upgrade-maven --version 0.0.0-upgrade-test
```

The second KMP mode generates only Compose resources, with no native drawable
request or manual Android resource opt-in. It adds a font through a task-backed
resource root, compiles the generated `Res.symbolFonts` descriptor, and checks
that the exact font bytes and drawable XML survive both AAR publication and
downstream APK packaging. Both the library and consuming app must reuse the
configuration cache.

## Official Compose runtime versions

`--compose-drawables-only` implies `--compose-resources` and tests generated SVG
drawables without the font-descriptor dependency. This distinction matters:
the current published `symbols-variant-font-core` requires Compose 1.12.1, so
requesting an older version in the full font fixture can resolve back up to
1.12.1. The existing latest-version font fixture remains in the matrix.

Two SVG-only cases use the same published Symbols plugin with official Compose
plugin/runtime versions **1.11.1 and the current catalog version (1.12.1)**.
Each compiles the common resource calls for JVM and Android, publishes the KMP
library, and consumes its AAR from a separate Android application. Generated XML
must survive both publication and APK packaging, and both builds must reuse
their configuration caches. This checks ordinary asset packaging; it does not
enable or establish experimental drawable-pruning support.

A small Gradle task reports actual artifact coordinates from the producer's
`jvmRuntimeClasspath` and the consuming app's `releaseRuntimeClasspath`. Its lazy
artifact provider becomes a list of strings in task inputs; the action neither
captures a project/configuration nor resolves dependencies. Python checks the
resolved resources, UI, foundation and runtime versions and records the complete
Compose coordinates in `result.json`. Missing artifacts or silent upgrades fail
the check. There are no forced versions, substitutions or injected classpaths.
The JVM check verifies both the JetBrains runtime wrapper and its AndroidX
implementation; checking only the wrapper would miss an upgraded implementation.

CMP 1.11.1 uses AndroidX Compose 1.11.2; CMP 1.12.1 uses AndroidX 1.12.1, following
the [official version mapping](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html#jetpack-compose-artifacts-used).
The [1.11.1 JVM wrapper metadata](https://repo.maven.apache.org/maven2/org/jetbrains/compose/runtime/runtime-desktop/1.11.1/runtime-desktop-1.11.1.pom)
also declares AndroidX runtime-desktop 1.11.2. `COMPOSE_ANDROIDX_VERSIONS` records
the tested pairs explicitly; verify and add a pair before extending the matrix.
The existing Kotlin 2.4.20/Compose compiler, Gradle 9.7, AGP 9.4.1, SDK 37 and
API-23 fixture settings remain fixed. Both resource-runtime AARs declare API 23;
AndroidX UI 1.12.1 additionally requires compile SDK 37 and AGP 9.1. The legacy
AGP-8 Java/XML case remains separate.

```shell
for compose_version in 1.11.1 1.12.1; do
  python3 tooling/compatibility/check.py --profile kmp --compose-drawables-only \
    --gradle ./gradlew --repository /tmp/symbols-upgrade-maven \
    --version 0.0.0-upgrade-test --compose-version "$compose_version" \
    --output "build/reports/tooling-compatibility-compose-$compose_version"
done
python3 -m unittest discover -s tooling/compatibility -p 'test_*.py'
```

The small Python checks cover both fixture modes and reject reports in which
requested 1.11.1 resources or AndroidX implementation artifacts resolve to 1.12.1.
They do not substitute for the independent Gradle builds. CI runs the two runtime
cases serially in the existing job, reusing its published candidate and environment
setup without uploading artifacts or adding another runner.

Local verification on September 30 passed all four resource cases against the
same published `0.0.0-pruning-api02` plugin artifact, SHA-256
`062846ebee4dab4d0cf98099294f4d92743439ee30cb4d1048e82cc5ed5e3a73`:

| Official CMP | AGP | Fixture | Verified |
| --- | --- | --- | --- |
| 1.11.1 | 9.4.1 | SVG-only | Producer Android/JVM compilation, AAR publication, separate Android consumer, both cache reuses, exact resolved versions |
| 1.12.1 | 9.4.1 | SVG-only | Same checks |
| 1.12.1 | 9.4.1 | Font descriptors and SVG | Same checks plus exact prepared font bytes in the AAR and APK |
| 1.12.1 | 9.1.0 | Font descriptors and SVG | Same checks at the supported Android Compose AGP minimum, including the new DSL hook |

All twelve resulting source JARs omit `.symbols-generated-files`, while the
ownership state remains on disk. The focused source-publication/lint regression
also passed after the marker exclusion. [Compact local evidence](RESULTS.json)
records artifact identity and resolved modules without raw logs or build-speed
claims. A real negative check also passed: requesting 1.11.1 with the current
font runtime compiled and reused the producer's configuration cache, then the
verifier rejected the actual `components-resources-desktop:1.12.1` resolution.
It exited with a version-mismatch error before building the consumer and wrote
no success report. This confirms that a successful build alone cannot silently
misrepresent the older runtime as tested.

## Android plugin versions

The hosted `published-consumers` job also checks the advertised AGP 9.1 minimum
with the same published candidate, root Gradle 9.7 wrapper, compile SDK 37, and
current Kotlin/Compose catalog versions:

| Toolchain | Consumer cases |
| --- | --- |
| Current catalog / root wrapper | Android app, Java/XML app, Android library, KMP, KMP Compose resources, JVM |
| CMP 1.11.1 / current Kotlin, AGP and root wrapper | KMP SVG-only resource publisher and separate consuming Android app |
| Current CMP / current Kotlin, AGP and root wrapper | KMP SVG-only resource publisher and separate consuming Android app |
| AGP 9.1.0 / Gradle 9.7.0 | Android Compose app |
| AGP 9.1.0 / Gradle 9.7.0 | KMP Compose-resource publisher and separate consuming Android app |
| AGP 8.13.2 / Gradle 8.14.5 | Java/XML app |

The original nine cases passed on September 28 at `9e4f43e`, including configuration-cache
reuse. [Hosted verification](https://github.com/HLCaptain/symbols/actions/runs/36418737452/job/108916523301).
The two runtime-version cases above were added subsequently and have the local
evidence recorded above; hosted exact-commit verification is still pending.

Reproduce the two minimum-version cases without overwriting the latest-toolchain
or legacy results:

```shell
python3 tooling/compatibility/check.py --profile android-app --gradle ./gradlew \
  --repository /tmp/symbols-upgrade-maven --version 0.0.0-upgrade-test \
  --agp-version 9.1.0 --compile-sdk 37 \
  --output build/reports/tooling-compatibility-agp91
python3 tooling/compatibility/check.py --profile kmp --compose-resources --gradle ./gradlew \
  --repository /tmp/symbols-upgrade-maven --version 0.0.0-upgrade-test \
  --agp-version 9.1.0 --compile-sdk 37 \
  --output build/reports/tooling-compatibility-agp91
```

The harness gives these cases distinct `android-app` and `kmp-compose` directories.
Both require configuration-cache reuse; the app checks release shrinking, while
the KMP case checks generated font/drawable bytes through publication and APK
packaging. AGP 9.1.0's [published API](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle-api/9.1.0/gradle-api-9.1.0-sources.jar)
includes the native KMP components extension, resource opt-in and generated-source
directory APIs used by the plugin. The hosted cases exercise those APIs through
actual compilation, publication and downstream packaging.

Test the same plugin artifact with the legacy supported Java/XML toolchain
(Gradle 8.14.5, AGP 8.13.2, API 21). Current Compose artifacts require AGP 9.1+
and API 23, so legacy KMP/Compose consumers are not part of this contract:

```shell
python3 tooling/compatibility/check.py --profile android-java \
  --gradle ./tooling/gradlew --repository /tmp/symbols-upgrade-maven \
  --version 0.0.0-upgrade-test --agp-version 8.13.2 \
  --compile-sdk 36 --output build/reports/tooling-compatibility-legacy
```

Modern Kotlin/Compose consumers use current catalog versions, compile SDK 37,
and API 23. The Java/XML fixture has no Kotlin or Compose runtime dependency.
Use separate `--output` directories when comparing toolchains. Set `JAVA_HOME`
to JDK 21 and `ANDROID_HOME` to the SDK installation before running. The script
keeps logs, timings, and the exact command locally; do not upload its generated
build artifacts to Actions storage. These tiny fixtures test compatibility,
not full-catalog performance or shrinker effectiveness.
