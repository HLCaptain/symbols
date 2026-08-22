# Samples

`composeApp` is the multiplatform launcher. It uses a Material 3 `Scaffold` and
top app bar and a Navigation 3 back stack. `:samples:api` owns the sealed
`SampleEntry` hierarchy, while Koin provides one `SampleItem` per feature.
`:samples:ui:components` contains
the shared pages, cards, status messages, list, and axis controls.

| Module | Targets | Focus |
| --- | --- | --- |
| `:samples:api` | Android, JVM, JS, Wasm, iOS arm64 | Sealed navigation entries and sample metadata |
| `:samples:ui:components` | Android, JVM, JS, Wasm, iOS arm64 | Shared Material 3 components |
| `:samples:material-static` | Android, JVM, JS, Wasm, iOS arm64 | Default-axis regular Material Symbols |
| `:samples:material-variable` | Android 26+, JVM, JS, Wasm, iOS arm64 | Bundled variable Material Symbols at embedded defaults |
| `:samples:custom-static` | Android, JVM, JS, Wasm, iOS arm64 | Minimal Powerline regular-font-to-`ImageVector` build |
| `:samples:custom-variable` | Android, JVM, JS, Wasm, iOS arm64 | Academmunicons fixed at `ital=0,wght=600` during generation |
| `:samples:image-vector-migration` | Android, JVM, JS, Wasm, iOS arm64 | Old Material Icons Extended vs generated Symbols, plus default-axis custom vectors and painters |
| `:samples:android-views` | Android, JVM, JS, Wasm, iOS arm64 | Compose-View interop on Android and an in-route fallback elsewhere |
| `:samples:theming` | Android, JVM, JS, Wasm, iOS arm64 | Independent `MaterialTheme` and `SymbolsTheme` inheritance |
| `:samples:runtime-axes` | Android 26+, JVM, JS, Wasm, iOS arm64 | Animated variable-font weight with live sliders and values |

`libs.plugins.symbolsSampleFeature` builds on the shared Compose Multiplatform
convention, adds Koin and sample dependencies, and generates each feature's
Gradle path as `SampleBuildConfig.MODULE_PATH`; sample source never hard-codes
its own module path. Following the
[Navigation 3 modular Koin recipe](https://github.com/android/nav3-recipes/tree/main/app/src/main/java/com/example/nav3recipes/modular/koin),
each feature contributes one qualified `SampleItem` containing its id, title,
description, generated module path, availability, and composable destination.
The Koin compiler discovers those annotated modules for the launcher's typed
`@KoinApplication`. The launcher gathers items with `getAll<SampleItem>()`.
`SampleItem` and the root `SampleList` share a sealed `SampleEntry` ancestor, so
the launcher uses a typed back stack and one polymorphic `NavEntry` renderer.

The sample launcher uses Android minSdk 23 because Navigation 3 requires it;
the Symbols libraries retain Android API 21 support. Navigation 3
publishes iOS arm64 device and simulator variants, so these runnable sample
modules omit the legacy Intel simulator target without changing the published
library targets.

The [Koin compiler plugin](https://insert-koin.io/docs/setup/compiler-plugin/)
runs with Kotlin 2.3.21 and Koin 4.2.2. It collects the annotated feature
modules at compile time, so the launcher needs no explicit common-feature
module list or generated registry. The Android Views sample has one common
Compose route: Android embeds its XML/View Binding/Data Binding hierarchy with
`AndroidView`, while other targets render an unavailable message in that route.
JS/Wasm incremental KLIB compilation is temporarily disabled in
`gradle.properties` for [KT-82395](https://youtrack.jetbrains.com/issue/KT-82395)
while the compiler plugin emits cross-module hint declarations.

All build-created sources and resources remain below the owning module's
`build/generated` directory. In particular, symbol outputs use
`build/generated/symbolFonts`, and BuildConfig uses
`build/generated/sources/buildConfig`. They are disposable `clean` outputs and
must not be edited or committed. See the [generator guide](../docs/GENERATOR.md#generated-output-locations)
for the complete layout. The Koin compiler transforms Kotlin IR and emits no
visible generated source or resource files.

Launcher previews use the AndroidX `androidx.compose.ui.tooling.preview.Preview`
annotation from `org.jetbrains.compose.ui:ui-tooling-preview`; Android debug
rendering uses `org.jetbrains.compose.ui:ui-tooling`.

Run the desktop launcher with `./gradlew :composeApp:run`, or build the Android
launcher with `./gradlew :composeApp:assembleDebug`.
