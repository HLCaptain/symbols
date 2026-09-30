# AGP 9 and Kotlin 2.4 migration

The repository builds with Gradle 9.7.0, AGP 9.4.1, Kotlin/Compose compiler
2.4.20, Compose Multiplatform 1.12.1, Koin compiler 1.2.1 and JDK 21.
Kotlin's published compatibility matrix ends at AGP 9.3.1, so passing this
repository's consumer matrix is required for the newer AGP combination.

## Consumer requirements

| Consumer | Supported configuration |
| --- | --- |
| Compose Android | AGP 9.1+, compile SDK 37, Android API 23+, Kotlin 2.4 |
| Android XML only | AGP 8.13.2+, Gradle 8.14.5+, compile SDK 36, API 21+; no Kotlin/Compose plugin required |
| JVM / shared Kotlin | Kotlin 2.4; JVM 11 library bytecode; Gradle plugin requires Java 17 |
| Apple source build | Xcode 26.4.1 on macOS; device and simulator targets retained |

The AndroidX artifacts used by Compose 1.12.1 declare their AGP/SDK floors in
`META-INF/com/android/build/gradle/aar-metadata.properties` and their manifests.
The previous Compose 1.11 dependencies already required API 23: the old blanket
API 21 claim described the native renderer's capability, not the resolved
Compose dependency graph. Native XML remains usable on API 21.

The Kotlin 2.4 requirement applies to JVM and KMP consumers as well as Android
Compose. Java/XML-only consumers need no Kotlin plugin. AGP and Android SDK
requirements apply only to Android targets.

Public icon accessors and the `symbolFonts` DSL are unchanged. Android applications
use AGP's built-in Kotlin; KMP libraries use
`com.android.kotlin.multiplatform.library` with `kotlin.android {}`. Generated
native resources and Kotlin sources are attached through task-backed public
variant APIs, including Android-only applications.

## Source-build changes

`androidApp` owns the Android launcher and application build types; `composeApp`
keeps the shared UI and desktop/web/iOS entrypoints. The Android Views sample's
binding implementation lives in `samples:android-views-platform`, an ordinary
Android library. Native Android KMP host tests run Roborazzi directly.

Build the public tooling with `./tooling/gradlew -p tooling …`, including releases.
Its wrapper remains Gradle 8.14.5 and its compile-only AGP API remains 8.13.2.
This prevents accidental use of Gradle 9-only APIs in the single published plugin.
The root composite build may build that same source under Gradle 9 during local
development. Gradle/KGP/AGP APIs are not imposed as runtime dependencies.

Native Android KMP lint is enabled explicitly. Archive verification includes
`bundleAndroidMainAar` and `androidSourcesJar`, and excludes the local lint AAR.
Publication-only CI continues to omit sample applications and benchmark APKs.

## Dependency decisions

Versions were checked against publisher artifact repositories on 2026-09-25.
The root build runs on JDK 21; compiler toolchains remain JDK 17 and published
library JVM bytecode remains Java 11.

| Dependency | Previous | Selected | Decision |
| --- | --- | --- | --- |
| Root Gradle | 8.14.5 | 9.7.0 | Within Kotlin 2.4.20's supported Gradle range; standalone tooling stays on 8.14.5 |
| AGP | 8.13.2 | 9.4.1 | Native Android KMP; public tooling's compile-only API stays on 8.13.2 |
| Kotlin / Compose compiler | 2.3.21 | 2.4.20 | Upgrade together |
| Compose Multiplatform | 1.11.1 | 1.12.1 | Accept the Android API 23 / compile SDK 37 / AGP 9.1 floor |
| Material3 | 1.11.0-alpha07 | 1.12.0-alpha03 | Newest compatible Compose 1.12 family |
| Android Compose test UI | 1.11.4 | 1.12.1 | Match the Android runtime family |
| Skiko | 0.9.22.2 | 0.150.1 | Match Compose's native runtime |
| Koin compiler | 1.1.0 | 1.2.1 | Explicit Kotlin 2.4.20 compiler API support |
| BuildConfig | 6.0.10 | 6.1.1 | Stable patch update |
| Navigation UI / runtime | 1.1.1 / 1.1.6 | 1.1.2 / 1.1.7 | Retain the sample's Android API 23 floor |
| Roborazzi | 1.72.0 | 1.75.0 | Native Android KMP host-test integration |
| Compose Preview Scanner | 0.9.1 | 0.9.3 | Stable patch update |
| Robolectric | 4.16.1 | 4.17 | Updated Android host-test runtime |
| Android Benchmark | 1.4.1 | 1.5.0 | Stable update |
| UI Automator | 2.3.0 | 2.4.0 | Stable update |
| FontTools / PicoSVG | 4.60.2 / 0.22.3 | 4.66.0 / 0.23.0 | Generator toolchain update |
| Tooling NMCP | 1.6.1 | 1.6.2 | Align with the root publication build |
| JavaScript webpack / CLI / dev server | 5.101.3 / 6.0.1 / 5.2.3 | 5.108.1 / 7.2.1 / 6.0.0 | Kotlin's tooling versions; regenerate the JS Yarn lock |

Koin 4.2.2, coroutines 1.11.0, Activity 1.13.0, AppCompat 1.8.0, AndroidX Test
1.7.0, ProfileInstaller 1.4.1, JUnit 4.13.2, Material Icons Extended 1.7.3,
Plugin Publish 2.2.1, Foojay 1.0.0 and skia-pathops 0.9.2 remain
unchanged; no compatible stable upgrade was selected.

- Material3 stays at 1.12.0-alpha03: 1.13 alpha would pull stable Compose UI and
  runtime onto the 1.13 prerelease family.
- Navigation UI is 1.1.2 with runtime 1.1.7. Runtime 1.2 requires API 24; retain
  the sample's API 23 floor.
- Skiko is aligned with Compose at 0.150.1, including Intel desktop artifacts.
- FontTools 4.66.0 and PicoSVG 0.23.0 update the generator environment; generated
  vectors remain build outputs. Existing font provenance and checksums are retained.
- The obsolete JS/Wasm incremental-compilation disable flags are removed after
  KT-82395 was fixed. The measured Binaryen inlining bound remains pending a
  separate comparison; it is not a published-library compiler option. The obsolete
  webpack watch override for KT-80582 is removed. The minimizer uses webpack's
  current bundled `minimizer-webpack-plugin`, with parallelism still disabled.
- AGP 9's test engine splits argument values on commas and forwards values
  without shell quoting. Benchmark CLI lists therefore use `+`, with commas
  retained for older/direct-ADB invocations. The [benchmark recipe](../benchmarks/animated-font/README.md#run)
  verifies actual test cases, and its analyzer can reject incomplete matrices
  with `--expected-cases`.

Primary references: [Kotlin compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html),
[AGP migration](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html),
[Android KMP integration](https://developer.android.com/kotlin/multiplatform/kmp-integration),
[Koin compiler 1.2.1](https://github.com/InsertKoinIO/koin-compiler-plugin/releases/tag/1.2.1).

Hosted Apple checks use the official `macos-26-intel` image with Xcode 26.4.1
(`Xcode_26.4.app` is also provided as a symlink). Android API 37's SDK Manager
package is `platforms;android-37.0`; API 36 is also needed for the core/XML
publications and legacy consumer checks. See the [runner image inventory](https://github.com/actions/runner-images/blob/main/images/macos/macos-26-Readme.md)
and [Android SDK repository metadata](https://dl.google.com/android/repository/repository2-3.xml).

## Verification and measurements

Run the independent published-consumer checks in
[tooling/compatibility](../tooling/compatibility/README.md). They resolve the
candidate plugin and libraries from an isolated Maven repository, without
composite-build substitution, and exercise configuration-cache reuse.

The resource study compares the main-branch baseline, this migration alone,
and subsequent resource changes. Build-time and size claims must be tied to
those separate revisions; an upgrade is not proof of a resource optimization.
Raw APKs, traces and profiles stay out of Git and Actions artifact storage.

Local migration checks (2026-09-25):

| Check | Result |
| --- | --- |
| Python generator/report tests | 64 passed; pinned fonts and catalog verified |
| Convention plugins | Tests and plugin validation passed |
| Generator/plugin | 32 generator tests and all 35 plugin cases passed; the updated overlay fixture was rerun separately |
| Independent consumers | All seven cases passed: JVM Compose, Android Compose app/library, modern/legacy Java/XML, native KMP AAR to app, and Compose-only KMP font/XML assets to app |
| Configuration cache | Reused by every independent consumer |
| Resource shrinking | Used generated/published native drawables retained, unused drawables removed, on AGP 8 and 9 |
| Publication archives | 105 JVM/Android, 92 web and 126 Apple archives verified with original legal/font/drawable invariants |
| Signing | 37 artifacts across seven publications verified with a disposable key |
| JVM library checks and sample | Library tests/lint and full Android app build passed; all five expected font payloads are packaged |
| Web | Full and incremental Koin compilation passed; JS/Wasm production linking and bundling passed with bounded memory |

The consumer runs used one Gradle-8-built plugin artifact
(`0.0.0-upgrade-test`, SHA-256
`9d810b1c9c2b709247a8752f6a4807ac7cdcdf3a576ca7d81108a8108e85111f`).
Their first-build timings include mixed cache state and are not performance
benchmarks. Hosted Apple framework linking and visual review are separate gates.
