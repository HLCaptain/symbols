# Symbols

A migration-friendly alternative to Compose's `material-icons-extended`: keep
the familiar `Icon(Icons.Rounded.Home, ...)` shape while choosing shrinkable
`ImageVector`s, Android XML drawables, regular fonts, or live variable fonts.
Use Material Symbols, another icon font, or generate a typed icon set from your
own font or SVG assets.

[![CI](https://github.com/HLCaptain/symbols/actions/workflows/ci.yml/badge.svg)](https://github.com/HLCaptain/symbols/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

> Symbols is preparing its first `0.1.0` release. Snapshot APIs may still change.

The Material catalog contains all 4,102 upstream names across Outlined,
Rounded, and Sharp styles. The same project also supports generated custom icon
sets, Compose Multiplatform, Android API 21+, and legacy Android Views and XML.

## Add Symbols

The first stable release is configured for Maven Central, so release builds only
need the repositories most Android and Kotlin projects already use:

```kotlin
repositories {
    google()
    mavenCentral()
}
```

While `0.1.0` is in development, snapshots are published from `main` to GitHub
Packages. GitHub requires authentication even for public Maven packages:

```kotlin
repositories {
    google()
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/hlcaptain/symbols")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}
```

Pick the artifact that matches how the application renders icons:

| Need | Artifact | Runtime payload |
| --- | --- | --- |
| Familiar Compose `Icon(ImageVector, ...)` | `symbols-material-vectors-{outlined|rounded|sharp}` | Fixed vectors for one style; unused typed vectors can be removed by R8 |
| Runtime style selection in Compose | `symbols-material-vectors-themed` | Fixed vectors for all three styles |
| Android XML and Views | `symbols-material-drawables-{outlined|rounded|sharp}` | Native `VectorDrawable` resources; unused resources can be removed by Android resource shrinking |
| Font icons on Android API 21+ | `symbols-material-{outlined|rounded|sharp}-static` | One indivisible regular font at the default axes |
| Live fill, weight, grade, or optical size | `symbols-material-{outlined|rounded|sharp}` | One indivisible variable font; variable axes require Android API 26+ |

For example:

```kotlin
dependencies {
    implementation(
        "io.github.hlcaptain:symbols-material-vectors-rounded:0.1.0-SNAPSHOT",
    )
}
```

Kotlin Multiplatform projects put the same dependency in
`commonMain.dependencies`.

Replace the snapshot version with `0.1.0` after the first stable release.

## Migrate from `material-icons-extended`

The call site stays conventional. Change the dependency and imports, then keep
using the standard Compose `Icon` composable:

```kotlin
import androidx.compose.material3.Icon
import io.github.hlcaptain.symbols.material.Icons
import io.github.hlcaptain.symbols.material.rounded.vectors.Home

Icon(
    imageVector = Icons.Rounded.Home,
    contentDescription = "Home",
)
```

The call shape matches `material-icons-extended`; the artwork is Material
Symbols at `FILL=0, GRAD=0, opsz=24, wght=400`, so it is not always visually
identical to the legacy icon. Generate another fixed-axis snapshot or use the
font renderer below when the design needs different axes.

Each typed property directly reaches one vector builder. There is no reflective
registry on this path, so full-mode R8 can reason about icons independently.
Vectors are created and cached on first use; aliases that share a code point
share the cached vector.

Use the themed vector artifact when style is selected at runtime:

```kotlin
import androidx.compose.material3.Icon
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.vectors.themed.Home

SymbolsTheme(style = MaterialSymbolStyle.Rounded) {
    Icon(
        imageVector = Icons.Themed.Home,
        contentDescription = "Home",
    )
}
```

`Icons.Themed.*` values are composable read-only properties. `SymbolsTheme`
selects their Outlined, Rounded, or Sharp snapshot. Its axes also supply defaults
to font renderers, including custom fonts that follow the Material axis contract;
axes do not alter fixed vectors.

When an icon is selected dynamically rather than referenced by name, use the
catalog bridge:

```kotlin
import io.github.hlcaptain.symbols.material.outlined.vectors.asOutlinedImageVector

Icon(
    imageVector = MaterialSymbols.ArrowBack.asOutlinedImageVector(
        autoMirror = true,
    ),
    contentDescription = "Back",
)
```

Dynamic lookup keeps the pack index and dispatcher reachable. Prefer direct
`Icons.{Style}.{Name}` properties when minimum APK size matters.

## Use Android Views and XML

The drawable artifacts are ordinary Android AARs with no font dependency. Add
one style to an Android module:

```kotlin
dependencies {
    implementation(
        "io.github.hlcaptain:symbols-material-drawables-outlined:0.1.0-SNAPSHOT",
    )
}
```

Use a generated resource directly from XML:

```xml
<ImageView
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:contentDescription="@string/home"
    android:src="@drawable/material_symbols_outlined_home_ue9b2" />
```

The same ID works with View Binding, Data Binding, `findViewById`,
`setImageResource`, or `Context.getDrawable`:

```kotlin
import io.github.hlcaptain.symbols.material.outlined.drawables.R as SymbolsR

binding.icon.setImageResource(
    SymbolsR.drawable.material_symbols_outlined_home_ue9b2,
)

binding.favorite.setImageDrawable(
    context.getDrawable(
        SymbolsR.drawable.material_symbols_outlined_favorite_ue87e,
    ),
)
```

The sample includes [plain XML](composeApp/src/androidMain/res/layout/activity_legacy_views.xml),
[Data Binding](composeApp/src/androidMain/res/layout/data_binding_icon.xml),
[View Binding and `findViewById`](composeApp/src/androidMain/kotlin/io/github/hlcaptain/symbols/sample/LegacyViewsActivity.kt),
a [custom `ImageView`](composeApp/src/androidMain/kotlin/io/github/hlcaptain/symbols/sample/GeneratedSymbolView.kt),
and a `TextView` compound drawable. These are legacy View-system integrations,
not deprecated Android APIs.

## Generate a custom icon set

The Gradle plugin converts a regular font or a fixed variable-font instance into
typed Compose vectors, Android drawables, Compose drawable resources, or any
combination:

Plugin markers resolve in `pluginManagement`, not the dependency repository
block. Stable releases need `mavenCentral()` there; snapshots need the same
authenticated GitHub Maven repository shown above. The complete settings block
is in the [generator guide](docs/GENERATOR.md).

```kotlin
plugins {
    id("io.github.hlcaptain.symbol-fonts") version "0.1.0-SNAPSHOT"
}

symbolFonts {
    iconSet("AppIcons") {
        style("Rounded") {
            codepoints.set(
                layout.projectDirectory.file("icons/app-icons.codepoints"),
            )
            font("rounded-variable.ttf")
            axis("FILL", 1f)
            axis("wght", 400f)
            imageVectors()
            androidDrawables()
            composeDrawables()
        }
    }
}
```

`font("rounded-variable.ttf")` searches `src/main/res/font` and
`src/commonMain/composeResources/font`. Omit it when those directories contain
only one supported font, or use `font.set(...)` for another path. Android
projects default to `<android namespace>.generated` for generated Kotlin.
Fonts inside resource directories are also packaged at runtime. When a font is
only a generator input, keep it elsewhere and use `font.set(...)` so the app
does not ship both the font and its generated vectors or drawables.

Every codepoint is generated by default. Use `include("home", "favorite")`
when generation time, compiler memory, or unshrunk output matters. R8 handles
generated Kotlin vectors on Android; Android's resource shrinker handles native
drawables. Neither can remove individual glyphs from a packaged font file.

Generated vectors use the same familiar call shape:

```kotlin
import com.example.app.generated.AppIcons
import com.example.app.generated.rounded.Home

Icon(
    imageVector = AppIcons.Rounded.Home,
    contentDescription = "Home",
)
```

The sample also generates typed vectors from Font Awesome Free Solid, Tabler
Icons Filled, and Powerline Symbols. Their YAML, CSS, and font-only metadata are
normalized under [`fonts/samples`](fonts/samples/README.md); the input fonts are
not packaged in the sample app.

For a source-owned SVG directory, the
[SVG icon font CLI](tools/README.md#svg-icon-font-generation) creates a
deterministic regular TTF and stable manifest that feed this same pipeline.

See [build-time generation](docs/GENERATOR.md) for plugin resolution, the full
DSL, generated resource names, caching, and shrinker boundaries. See
[custom fonts](docs/CUSTOM_FONTS.md) for the codepoint manifest format and the
Material-axis compatibility contract.

## Catalog lookup

The generated Material catalog keeps aliases and raw code points available
without requiring a rendering artifact:

```kotlin
val search = MaterialSymbols.fromName("search")
val allStarNames = MaterialSymbols.aliases(MaterialSymbols.Star)
val everyName = MaterialSymbols.all

check(search?.codePoint == 0xE8B6)
check(MaterialSymbols.size == 4_102)
```

Aliases remain distinct names even when they share a code point. Unknown names
return `null`; unknown code points return an empty alias list. Code points are
stored as `Int`, including valid Unicode scalars outside the BMP.

## Advanced: render directly from a font

`MaterialSymbolIcon` renders a glyph with `BasicText`. Prefer regular Compose
`Icon` and generated vectors for migration-style usage; choose the font renderer
when the design needs variable axes or a runtime-selected catalog.

```kotlin
import androidx.compose.material3.MaterialTheme
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.rounded.MaterialSymbolIcon as RoundedMaterialSymbolIcon

SymbolsTheme(
    axes = MaterialSymbolAxes(
        fill = 1f,
        weight = 500,
        grade = 0f,
        opticalSize = 24f,
    ),
) {
    RoundedMaterialSymbolIcon(
        symbol = Symbols.Rounded.Home,
        contentDescription = "Home",
        tint = MaterialTheme.colorScheme.primary,
    )
}
```

Use `contentDescription = null` for a decorative icon. The private-use glyph is
removed from semantics; only a supplied localized description is exposed.
An explicit `axes` argument overrides `SymbolsTheme`.

Variable-font APIs start on Android API 26. A regular-font artifact provides a
fixed default-axis fallback on Android API 21–25:

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

For a list or grid, build one family and share it:

```kotlin
import io.github.hlcaptain.symbols.material.*
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

A Material-axis-compatible custom font can implement
`MaterialSymbolVariableFont` or `MaterialSymbolRegularFont`. Custom glyphs do
not need to belong to the Material catalog:

```kotlin
MaterialSymbolIcon(
    codePoint = 0xF0001,
    font = MySymbols,
    contentDescription = "Custom action",
)
```

`SymbolsTheme` currently models `FILL`, `wght`, `GRAD`, and `opsz`; arbitrary
custom variable-axis schemas are not supported.

## Material axes

| Property | Font axis | Range | Default |
| --- | --- | ---: | ---: |
| `fill` | `FILL` | `0..1` | `0` |
| `weight` | `wght` | `100..700` | `400` |
| `grade` | `GRAD` | `-50..200` | `0` |
| `opticalSize` | `opsz` | `20..48` | `24` |

Values are validated when `MaterialSymbolAxes` is constructed. A distinct axis
combination produces a distinct remembered font family.

## Reference and development

- [Architecture and module map](docs/ARCHITECTURE.md)
- [Performance and APK-size trade-offs](docs/PERFORMANCE.md)
- [Build-time generator](docs/GENERATOR.md)
- [Custom font guidance](docs/CUSTOM_FONTS.md)
- [Release process](RELEASING.md)
- [Contributing](CONTRIBUTING.md)

Run `./gradlew :composeApp:run` for the interactive sample. Maintainer build,
generator, font-conformance, and shrink-test commands live in the linked guides
and CI workflow.

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

Please use the private process in [SECURITY.md](SECURITY.md) for vulnerabilities.
