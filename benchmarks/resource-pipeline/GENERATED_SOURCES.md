# Mark generated Kotlin through the public source API

The built-in Material vector generator and the public Symbols plugin previously
registered generated files in `KotlinSourceSet.kotlin`. The change moves
those registrations to `generatedKotlin` for KMP/common and Kotlin/JVM sources.
The task-backed directory providers stay the same. Android-only projects keep
using AGP's `addGeneratedSourceDirectory`; KMP files are registered only once.

This uses Kotlin's public, experimental generated-source API with a private
implementation opt-in. It was introduced in Kotlin 2.3 and is present in the
selected 2.4.20 toolchain. No consumer DSL or icon accessor changes are involved.
[Kotlin API](https://kotlinlang.org/api/kotlin-gradle-plugin/kotlin-gradle-plugin-api/org.jetbrains.kotlin.gradle.plugin/-kotlin-source-set/generated-kotlin.html),
[migration guidance](https://kotlinlang.org/docs/compatibility-guide-23.html#change-the-approach-to-registering-all-generated-sources).

The publisher source archives establish the relevant behavior:

- AGP 9.4.1 `KmpComponentImpl.kt` maps ordinary `sourceSet.kotlin.srcDirs` into
  Android source providers. `AndroidLintInputs.kt` excludes generated entries
  from its normal source inputs. B's actual lint model includes the generated
  Material vector directory as ordinary Java/Kotlin source.
- KGP 2.4.20 `KotlinCompilationInfo.kt` includes both ordinary and generated
  Kotlin in compilation. `DefaultKotlinSourceSet.kt` combines them in `allKotlin`.
- `mppSourcesJar.kt` and `KotlinSoftwareComponent.kt` use that combined set for
  target and common source JARs. `KotlinSourceSetProcessor.kt` also includes both
  in the Java plugin's source collection for Kotlin/JVM source archives.

[AGP source archive](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.4.1/gradle-9.4.1-sources.jar),
[KGP source archive](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/2.4.20/kotlin-gradle-plugin-2.4.20-sources.jar).

The new functional regression compiles authored code that calls a generated
catalog on JVM and native Android KMP. It checks that every source JAR contains
both files exactly once, and that the Android lint model retains authored common
and Android source roots without classifying generated directories as authored.
The existing Android/KMP wiring test now examines `allKotlinSources` explicitly.

This does not disable lint, add suppression paths, or restore A's missing authored
common-source coverage. A/B benchmark revisions remain unchanged.

## Validation

Verified locally on September 28 with JDK 21:

- The focused functional regression passes for both Kotlin/JVM and native Android
  KMP. Generated declarations compile, source archives contain the generated and
  authored files once each, and authored common/Android roots remain in lint.
- The real rounded Material module compiles for Android and JVM. Its common,
  Android and JVM source JARs each contain all 62 generated Kotlin files exactly
  once, with byte-identical contents totalling 16,868,767 bytes per archive.
- Its lint model now lists `src/androidMain/kotlin:src/commonMain/kotlin`; the
  generated Material vector directory is absent from authored lint inputs.

The compiling TestKit regression allows normal dependency resolution because its
isolated cache starts empty. Forcing offline mode failed before compilation while
resolving Kotlin's standard library; the wiring-only tests retain offline mode.
Full exception logging preserves child-build failures in hosted logs.

Reproduce the focused regression with the tooling wrapper:

```bash
./tooling/gradlew -p tooling :symbol-gradle-plugin:test \
  --tests '*SymbolFontsPluginFunctionalTest.generatedKotlinCompilesAndShipsInSourcesWithoutHidingAuthoredLintInputs' \
  --max-workers=1 --no-daemon
```

The real-module validation uses `compileAndroidMain`, `compileKotlinJvm`,
`sourcesJar`, `androidSourcesJar`, `jvmSourcesJar` and
`generateAndroidMainLintModel` on `:modules:material-vectors-rounded`.

## Measured effect

Three controlled shell-profile repetitions compare upgrade-only `3aea238` with
the optimization at `108fa6d`. Median clean build time falls from 109.922 to
87.704 seconds (−20.2%), and peak process-tree RSS from 6,790.3 to 5,883.8 MiB
(−13.4%). Rounded-vector lint falls from 24.127 to 2.011 seconds; Kotlin
compilation remains similar at 34.113 versus 33.449 seconds. No-op and code-edit
differences are small.

All clean APK hashes and generated inventories match between these revisions.
The improvement comes from classifying generated code correctly for lint;
authored common/Android lint inputs remain present. Both configuration-cache
reuse and build-cache restoration passed separate probes. See the
[measurement method](README.md#measurement-method); cache probes are single
checks and are kept separate from the repeated timing results.
The [complete controlled report](CONTROLLED_BUILDS.md) includes sample ranges,
task attribution, memory and integrity checks.
