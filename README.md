# Symbols

Typed Material Symbols for Compose Multiplatform, with variable and regular
font rendering plus build-time `ImageVector` and drawable generation.

[![CI](https://github.com/HLCaptain/symbols/actions/workflows/ci.yml/badge.svg)](https://github.com/HLCaptain/symbols/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

> Symbols is preparing its first `0.1.0` release. Snapshot APIs may still change.

Choose the delivery mode that matches the application: one variable font for
live axes, one default-axis regular font for Android API 21, or selected
font outlines converted to shrinker-friendly source/resources during the build.
All 4,102 upstream names have style-typed completion such as
`Symbols.Rounded.Home`; catalog lookup, aliases, raw code points, and built-in
`ImageVector` packs remain available.

## Highlights

- Outlined, Rounded, and Sharp variable-font artifacts with one font resource
  in each.
- API-21-compatible regular-font artifacts at
  `FILL=0, GRAD=0, opsz=24, wght=400`.
- Four live axes: fill, weight, grade, and optical size.
- 4,102 style-typed names covering 3,802 unique code points, including aliases.
- `MaterialSymbolsTheme` composition locals for inherited axes and vector style.
- A runtime variable-font capability gate for Android API 21–25 fallbacks.
- Allocation-light inline catalog handles and lazy lookup tables.
- Optional fixed and theme-selected vector packs at the default axes.
- Prebuilt Android `R.drawable` packs for legacy View and XML applications.
- A cacheable Gradle generator for regular/variable font to `ImageVector`,
  Android drawable, and Compose drawable output.
- Android, iOS, JVM/Desktop, JavaScript, and Wasm targets.
- Deterministic generators and strict font/provenance verification.
- Apache-2.0 project code and Apache-2.0 Google Material Symbols assets.

## Add a dependency

Snapshots are published to GitHub Packages from `main`. GitHub's
[Maven registry documentation](https://docs.github.com/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)
requires an authenticated package read, so configure a token with
`read:packages` in `GITHUB_TOKEN`. Runtime artifacts use the dependency
repository below; the generator's plugin marker uses the same registry in
`pluginManagement`, as shown in [the generator guide](docs/GENERATOR.md):

```kotlin
repositories {
    mavenCentral()
    google()
    maven {
        url = uri("https://maven.pkg.github.com/hlcaptain/symbols")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}
```

Add only the styles or access modes the application uses:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Live axes render on Android API 26+; the artifact has minSdk 21
            // so an app can select a regular/vector fallback at runtime.
            implementation(
                "io.github.hlcaptain:symbols-material-outlined:0.1.0-SNAPSHOT",
            )

            // Default-axis regular font. Android runtime support starts at API 21.
            implementation(
                "io.github.hlcaptain:symbols-material-outlined-static:0.1.0-SNAPSHOT",
            )

            // Theme-selected ImageVectors for all three styles; no fonts.
            implementation(
                "io.github.hlcaptain:symbols-material-vectors-themed:0.1.0-SNAPSHOT",
            )
        }
        androidMain.dependencies {
            // Native VectorDrawables for XML and the Android View system.
            implementation(
                "io.github.hlcaptain:symbols-material-drawables-outlined:0.1.0-SNAPSHOT",
            )
        }
    }
}
```

Replace `outlined` with `rounded` or `sharp` for another style. Variable-font
rendering requires Android API 26 or newer because Android's
[variable-font APIs](https://developer.android.com/develop/ui/compose/text/fonts#variable-fonts)
start there. The artifacts themselves use minSdk 21 so one application can carry
a variable style and select a regular-font/vector fallback through
`MaterialSymbolsRuntime.variableFontsSupported`.

## Render a variable-font icon

The style-typed API selects the matching bundled font and provides familiar
completion:

```kotlin
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.rounded.MaterialSymbolIcon

MaterialSymbolsTheme(
    axes = MaterialSymbolAxes(
        fill = 1f,
        weight = 500,
        grade = 0f,
        opticalSize = 24f,
    ),
) {
    MaterialSymbolIcon(
        symbol = Symbols.Rounded.Home,
        contentDescription = "Home",
        tint = MaterialTheme.colorScheme.primary,
    )
}
```

Use `contentDescription = null` for a decorative icon. The renderer removes the
private-use character from semantics and exposes only a supplied, localized
description.

An explicit `axes` argument wins over `MaterialSymbolsTheme`. Outside a provider,
font renderers use `MaterialSymbolAxes.Default`.

For a grid or list, the low-level API can build the `FontFamily` once and share
it:

```kotlin
import io.github.hlcaptain.symbols.material.outlined.MaterialSymbolsOutlined

val axes = MaterialSymbolAxes(weight = 500)
val family = rememberMaterialSymbolFontFamily(MaterialSymbolsOutlined, axes)

LazyRow {
    items(symbols) { symbol ->
        MaterialSymbolIcon(
            symbol = symbol,
            fontFamily = family,
            contentDescription = symbol.name,
            axes = axes,
        )
    }
}
```

The public `MaterialSymbolsOutlined.resource` property also exposes the underlying
Compose
[`FontResource`](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html#fonts)
for custom text layouts. It is intentionally a multiplatform resource rather
than a second Android-only `R.font` copy.

## Render a regular font on Android API 21

The `-static` artifacts contain deterministic default-axis instances of the same
Material Symbols snapshot:

```kotlin
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.rounded.staticfont.MaterialSymbolIcon

MaterialSymbolIcon(
    symbol = Symbols.Rounded.Home,
    contentDescription = "Home",
)
```

A regular font is fixed at `MaterialSymbolAxes.Default`. Passing different axes
fails clearly instead of silently ignoring them. Choose a runtime fallback
without guessing the platform version:

```kotlin
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.rounded.MaterialSymbolIcon as VariableRoundedIcon
import io.github.hlcaptain.symbols.material.rounded.staticfont.MaterialSymbolIcon as RegularRoundedIcon

if (MaterialSymbolsRuntime.variableFontsSupported) {
    VariableRoundedIcon(Symbols.Rounded.Home, contentDescription = "Home")
} else {
    RegularRoundedIcon(Symbols.Rounded.Home, contentDescription = "Home")
}
```

Here `VariableRoundedIcon` and `RegularRoundedIcon` are Kotlin import aliases for
the two style-specific `MaterialSymbolIcon` overloads. The capability is `false`
on Android API 21–25, `true` on Android API 26+, and `true` on the supported
non-Android targets.

## Lookup, aliases, and raw code points

```kotlin
val search = MaterialSymbols.fromName("search")
val allStarNames = MaterialSymbols.aliases(MaterialSymbols.Star)
val everyName = MaterialSymbols.all

check(search?.codePoint == 0xE8B6)
check(MaterialSymbols.size == 4_102)
```

Aliases remain distinct named entries even when they share a code point. Unknown
names return `null`; unknown code points return an empty alias list. Code points
are `Int`, and text encoding supports the full Unicode scalar range rather than
assuming every future glyph stays in the BMP.

A custom font glyph can bypass the generated catalog:

```kotlin
MaterialSymbolIcon(
    codePoint = 0xF0001,
    font = MySymbols,
    contentDescription = "Custom action",
)
```

`MySymbols` implements `MaterialSymbolVariableFont` or
`MaterialSymbolRegularFont` and points at the custom Compose font resource.
The legacy `MaterialSymbolFont` contract remains source compatible and is
treated as variable. Invalid Unicode scalar values fail immediately.

## Use an ImageVector

Vector packs are fixed snapshots of the default axes. They are useful for older
Android versions, APIs that specifically require `ImageVector`, or a project that
prefers vector resources over a font:

```kotlin
import io.github.hlcaptain.symbols.material.Icons
import io.github.hlcaptain.symbols.material.outlined.vectors.Home

Icon(
    imageVector = Icons.Outlined.Home,
    contentDescription = "Home",
)
```

The typed properties directly reference independent codepoint builders, which
lets a full-mode code shrinker reason about each icon separately. The dynamic
catalog bridge remains available when the icon is selected at runtime:

```kotlin
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.outlined.vectors.asOutlinedImageVector

Icon(
    imageVector = MaterialSymbols.ArrowBack.asOutlinedImageVector(autoMirror = true),
    contentDescription = "Back",
)
```

Vectors are built and cached on first access. Code-point aliases share the same
cached vector. Dynamic `MaterialSymbol` lookup reaches the pack's index and
dispatcher, so use direct `Icons.{Style}.{Name}` properties when shrinkability
matters. Vector packs intentionally do not pretend to support variable axes;
choose a font artifact when the design needs axis animation or non-default
values.

To select Outlined, Rounded, or Sharp through the composition, add
`material-vectors-themed` and import the generated property for each icon used:

```kotlin
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.vectors.themed.Home

MaterialSymbolsTheme(style = MaterialSymbolStyle.Rounded) {
    Icon(
        imageVector = Icons.Themed.Home,
        contentDescription = "Home",
    )
}
```

`Icons.Themed.*` properties are composable read-only getters. Each getter reads
`LocalMaterialSymbolStyle` and directly reaches one icon in each fixed style,
so a code shrinker does not need the catalog-wide dispatcher. Axes do not alter
these snapshots.

## Use Android drawables and Views

The `material-drawables-{outlined|rounded|sharp}` AARs contain native Android
`VectorDrawable` resources at the default axes and no font. They work anywhere
an ordinary drawable ID works, including XML:

```kotlin
dependencies {
    implementation(
        "io.github.hlcaptain:symbols-material-drawables-outlined:0.1.0-SNAPSHOT",
    )
}
```

```xml
<ImageView
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:contentDescription="@string/home"
    android:src="@drawable/material_symbols_outlined_home_ue9b2" />
```

and View code:

```kotlin
import io.github.hlcaptain.symbols.material.outlined.drawables.R as SymbolsR

binding.icon.setImageDrawable(
    context.getDrawable(SymbolsR.drawable.material_symbols_outlined_home_ue9b2),
)
```

The Android sample also exercises plain XML, `findViewById`, View Binding, Data
Binding, `setImageResource`, a custom `ImageView` attribute, and a `TextView`
compound drawable. These are legacy View-system patterns, not deprecated APIs.

## Generate selected vectors and drawables

The build plugin accepts regular fonts and fixed instances of variable fonts,
then generates only the manifest names selected by the application:

```kotlin
plugins {
    id("io.github.hlcaptain.symbol-fonts") version "0.1.0-SNAPSHOT"
}

symbolFonts {
    iconSet("AppIcons") {
        packageName.set("com.example.icons")
        manifest.set(layout.projectDirectory.file("icons/app-icons.codepoints"))
        include("check", "favorite", "home")

        style("Rounded") {
            font.set(layout.projectDirectory.file("icons/rounded-variable.ttf"))
            axis("FILL", 1f)
            axis("wght", 400f)
            imageVectors()
            androidDrawables()
            composeDrawables()
        }
    }
}
```

The input font is consumed only by the generator unless the application also
adds it as a runtime resource. Generated Kotlin has typed completion such as
`AppIcons.Rounded.Home`; native and Compose XML use one resource per unique
codepoint. Explicit `include(...)` selection avoids generating unused glyphs,
while `includeAll()` opts into the complete manifest.

See [build-time font conversion](docs/GENERATOR.md) for plugin setup, the full
DSL, output wiring, resource names, caching, and shrinker boundaries.

## Axes

| Property | Font axis | Range | Default |
| --- | --- | ---: | ---: |
| `fill` | `FILL` | `0..1` | `0` |
| `weight` | `wght` | `100..700` | `400` |
| `grade` | `GRAD` | `-50..200` | `0` |
| `opticalSize` | `opsz` | `20..48` | `24` |

Values are validated when `MaterialSymbolAxes` is constructed. Use a distinct
axis value to get a distinct remembered font; do not animate axes by rebuilding
an entire catalog.

## Modules

| Artifact suffix | Contents | Android min SDK |
| --- | --- | ---: |
| `material-core` | catalog, names, aliases, code points; no Compose and no font | 21 |
| `material-compose` | typed namespaces, axes/theme, capability gate, and font renderer; no font | 21 |
| `material-outlined` | adapter plus one Outlined variable font; rendering gated at API 26 | 21 |
| `material-rounded` | adapter plus one Rounded variable font; rendering gated at API 26 | 21 |
| `material-sharp` | adapter plus one Sharp variable font; rendering gated at API 26 | 21 |
| `material-outlined-static` | adapter plus one default-axis Outlined regular font | 21 |
| `material-rounded-static` | adapter plus one default-axis Rounded regular font | 21 |
| `material-sharp-static` | adapter plus one default-axis Sharp regular font | 21 |
| `material-vectors-outlined` | typed default-axis Outlined vectors; no font | 21 |
| `material-vectors-rounded` | typed default-axis Rounded vectors; no font | 21 |
| `material-vectors-sharp` | typed default-axis Sharp vectors; no font | 21 |
| `material-vectors-themed` | composable `Icons.Themed.*` access over all vector styles; no font | 21 |
| `material-drawables-outlined` | default-axis Outlined Android drawables; no font | 21 |
| `material-drawables-rounded` | default-axis Rounded Android drawables; no font | 21 |
| `material-drawables-sharp` | default-axis Sharp Android drawables; no font | 21 |
| `symbol-generator-core` | JVM outline generator/CLI; build-time only | — |
| `symbol-gradle-plugin` | `io.github.hlcaptain.symbol-fonts` implementation; build-time only | — |

See [architecture](docs/ARCHITECTURE.md), [performance choices](docs/PERFORMANCE.md),
[build-time generation](docs/GENERATOR.md), and
[custom font guidance](docs/CUSTOM_FONTS.md) for the trade-offs behind this split.

## Build and verify

The checked-in wrapper uses Gradle 8.14.5 with Kotlin 2.2.21 and AGP 8.11.1.
Use JDK 17 or 21 and an Android SDK with API 36; Gradle provisions the exact
JDK 17 compiler toolchain when it is not installed:

```shell
./gradlew jvmTest assembleRelease lintRelease verifyPublishedArchives
./gradlew -p tooling test \
  :symbol-gradle-plugin:validatePlugins \
  :symbol-generator-core:generatePomFileForMavenPublication \
  :symbol-gradle-plugin:generatePomFileForPluginMavenPublication \
  :symbol-gradle-plugin:generatePomFileForSymbolFontsPluginMarkerMavenPublication

python3 tools/generate_material_symbols.py --check
python3 tools/generate_material_font_namespaces.py --check
python3 -m unittest discover -s tools/tests -p "test_*.py"
```

Font and vector conformance checks use a pinned maintainer-only FontTools version:

```shell
python3 -m venv /tmp/symbols-fonttools
/tmp/symbols-fonttools/bin/python -m pip install \
  -r tools/requirements-font-verification.txt
/tmp/symbols-fonttools/bin/python tools/verify_material_fonts.py
/tmp/symbols-fonttools/bin/python tools/generate_material_static_fonts.py --check
/tmp/symbols-fonttools/bin/python tools/generate_material_vectors.py --check

./gradlew \
  :benchmarks:shrinkable-vectors:assembleUnshrunk \
  :benchmarks:shrinkable-vectors:assembleShrunk
python3 benchmarks/shrinkable-vectors/verify.py
```

`verifyPublishedArchives` byte-compares the packaged legal notices with the
root files, verifies collision-free
`META-INF/<project.name>/{LICENSE,THIRD_PARTY_NOTICES.md}` paths, and enforces
exactly one TTF in each variable- or regular-font runtime/resource archive and
none in catalog, renderer-only, vector, source, or metadata-code archives.
Tooling JARs use the same project-name legal namespace. The shrink fixture
proves that a referenced typed vector survives full-mode R8 while an
unreferenced vector class and Android resource are removed; see
[performance and size](docs/PERFORMANCE.md).

Run the interactive sample with `./gradlew :composeApp:run`. It demonstrates all
font styles, every axis, runtime search, shared-family rendering, the API 21
regular-font fallback, typed vector access, and build-generated icons.

## Asset provenance and licensing

Project source is licensed under Apache License 2.0. Bundled Material Symbols
variable fonts, derived regular fonts, and derived default-axis paths come from
Google's
[`material-design-icons`](https://github.com/google/material-design-icons)
repository and are also Apache-2.0 licensed. This project is independent and is
not endorsed by Google.

Exact font versions, hashes, paths, and trademark notice are recorded in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). See
[fonts/material/README.md](fonts/material/README.md) before updating an asset.

Contributions are welcome under [CONTRIBUTING.md](CONTRIBUTING.md). Please use the
private process in [SECURITY.md](SECURITY.md) for vulnerabilities.
