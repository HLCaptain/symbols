# Mark generated Kotlin through the public source API

The built-in Material vector generator and the public Symbols plugin previously
registered generated files in `KotlinSourceSet.kotlin`. The candidate changes
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
common-source coverage. A/B benchmark revisions remain unchanged. Validate the
candidate after the baseline measurements: run the targeted functional test,
check the real Material module's Android/JVM and source archives, then compare
lint models and repeat the controlled timing series. Savings remain unproven
until those checks and measurements complete.
