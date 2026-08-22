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
| Material names and code points only | `symbols-material-core` | Typed catalog; no Compose or bundled font |
| Familiar Compose `Icon(ImageVector, ...)` | `symbols-material-vectors-{outlined|rounded|sharp}` | Fixed vectors for one style; unused typed vectors can be removed by R8 |
| Runtime style selection in Compose | `symbols-material-vectors-themed` | Fixed vectors for all three styles |
| Android XML and Views | `symbols-material-drawables-{outlined|rounded|sharp}` | Native `VectorDrawable` resources; unused resources can be removed by Android resource shrinking |
| Runtime custom regular or variable fonts | `symbols-variant-font-core` | Generic Compose theme and code-point renderer; no bundled font or catalog |
| Material catalog adapters and theming only | `symbols-material-compose` | Material axes, style theme, and typed font namespaces; no bundled font |
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

MaterialSymbolsTheme(style = MaterialSymbolStyle.Rounded) {
    Icon(
        imageVector = Icons.Themed.Home,
        contentDescription = "Home",
    )
}
```

`Icons.Themed.*` values are composable read-only properties.
`MaterialSymbolsTheme` selects their Outlined, Rounded, or Sharp snapshot and
supplies Material axes to variable and Material-compatible font renderers. Axes
do not alter fixed vectors or regular fonts.

For a symbol selected dynamically, use the equivalent composable-scoped lookup:

```kotlin
Icon(
    imageVector = symbol.asThemedImageVector(),
    contentDescription = symbol.name,
)
```

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

The focused Android sample includes
[plain XML](samples/android-views/src/androidMain/res/layout/android_views_content.xml),
[Data Binding](samples/android-views/src/androidMain/res/layout/data_binding_icon.xml),
[View Binding inside Compose](samples/android-views/src/androidMain/kotlin/io/github/hlcaptain/symbols/sample/androidviews/AndroidViewsPlatformContent.android.kt),
and a [custom `ImageView`](samples/android-views/src/androidMain/kotlin/io/github/hlcaptain/symbols/sample/androidviews/GeneratedSymbolView.kt).
Compose hosts the View hierarchy with `AndroidView`; no second Activity is
needed. These are Android View-system integrations, not deprecated Android APIs.

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

The `axis(...)` values are applied while generating the output. Generated
`ImageVector` and XML resources are fixed snapshots: they do not read
`SymbolsTheme` or change at runtime. Use the generic runtime font API below when
a custom font needs live variation coordinates.

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

The focused custom samples keep their build files intentionally small.
[`custom-static`](samples/custom-static/build.gradle.kts) reads Powerline from
its conventional `composeResources/font` directory, while
[`custom-variable`](samples/custom-variable/build.gradle.kts) fixes
Academmunicons at `ital=0,wght=600` before generating typed vectors. Live axis
changes and a continuously animated weight axis with matching sliders/current
values are demonstrated separately by
[`runtime-axes`](samples/runtime-axes/src/commonMain/kotlin/io/github/hlcaptain/symbols/sample/runtimeaxes/RuntimeAxesSample.kt).
The [`image-vector-migration`](samples/image-vector-migration/src/commonMain/kotlin/io/github/hlcaptain/symbols/sample/imagevectormigration/ImageVectorMigrationSample.kt)
screen keeps the old Material Icons Extended call beside the generated Symbols
call, then shows custom Academmunicons `ImageVector` and painter outputs at the
font's embedded defaults (`ital=0,wght=100`).
Font Awesome and Tabler remain pinned provider fixtures under
[`fonts/samples`](fonts/samples/README.md); the launcher does not package them.

For a source-owned SVG directory, the
[SVG icon font CLI](tools/README.md#svg-icon-font-generation) creates a
deterministic regular TTF and stable manifest that feed this same pipeline.

See [build-time generation](docs/GENERATOR.md) for plugin resolution, the full
DSL, generated resource names, caching, and shrinker boundaries. See
[custom fonts](docs/CUSTOM_FONTS.md) for the codepoint manifest format and
runtime regular/variable font contracts.

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

Prefer regular Compose `Icon` and generated vectors for migration-style usage.
Use a runtime font when an icon is selected dynamically or its variation axes
must change without regenerating a fixed vector.

### Custom runtime fonts

The generic APIs live in `io.github.hlcaptain.symbols.font`. Point the Gradle
plugin at the Compose resource root to generate typed descriptors and OpenType
axis metadata from every direct `font/*.ttf`, `*.otf`, or `*.ttc` resource:

```kotlin
symbolFonts {
    composeFontResources.from(
        layout.projectDirectory.dir("src/commonMain/composeResources"),
    )
}
```

Use the generated descriptor through the module's global `Res` class:

```kotlin
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolsTheme
import io.github.hlcaptain.symbols.font.fontSettings
import my.symbols.generated.resources.Res
import my.symbols.generated.resources.symbolFonts

val mySymbols = Res.symbolFonts.my_symbols_variable

SymbolsTheme(
    fontSettings = mySymbols.fontSettings(
        mapOf("FILL" to 1f, "wdth" to 110f, "wght" to 500f),
    ),
) {
    SymbolFontIcon(
        codePoint = 0xF0001,
        font = mySymbols,
        contentDescription = "Custom action",
    )
}
```

`SymbolFont` is the sealed root of the runtime-font contract. Applications
extend its open `SymbolFont.Variable` or `SymbolFont.Regular` interface rather
than implementing the root directly; existing direct implementations should
migrate to the matching interface. A descriptor must not implement both.

Generated variable descriptors expose visible `fvar` axes as `variationAxes`.
Their `fontSettings(...)` helper fills omitted coordinates with the font's
declared defaults and validates tags and ranges. Manual descriptors can still
override `variationAxes`; an empty list means metadata was not embedded and
does not change the variable-font capability.

`SymbolFontIcon` removes the private-use glyph from semantics and exposes only a
supplied, localized description. Use `contentDescription = null` for a
decorative icon. A `SymbolFont.Regular` declares one fixed `fontSettings` point;
it renders on Android API 21 and rejects other requested settings.

Variable-font APIs start on Android API 26. Check
`SymbolsRuntime.variableFontsSupported` before selecting a variable font, and
use a regular font or generated vector/drawable as the Android API 21–25
fallback.

### Material Symbols font renderer

`MaterialSymbolsTheme` adds validated Material axes and supplies their equivalent
generic `SymbolFontSettings`. Render the shared catalog through the generic font
API when axes must remain live:

```kotlin
import androidx.compose.material3.MaterialTheme
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.rounded.MaterialSymbolsRounded

MaterialSymbolsTheme(
    axes = MaterialSymbolAxes(
        fill = 1f,
        weight = 500,
        grade = 0f,
        opticalSize = 24f,
    ),
) {
    SymbolFontIcon(
        codePoint = MaterialSymbols.Home.codePoint,
        font = MaterialSymbolsRounded,
        contentDescription = "Home",
        tint = MaterialTheme.colorScheme.primary,
    )
}
```

A regular Material font artifact provides a fixed default-axis fallback on
Android API 21–25:

Add both `symbols-material-rounded` and `symbols-material-rounded-static` when
the application selects between these paths.

```kotlin
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolsRuntime
import io.github.hlcaptain.symbols.material.Home
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.rounded.MaterialSymbolsRounded
import io.github.hlcaptain.symbols.material.rounded.staticfont.MaterialSymbolsRoundedStatic

if (SymbolsRuntime.variableFontsSupported) {
    SymbolFontIcon(
        MaterialSymbols.Home.codePoint,
        MaterialSymbolsRounded,
        contentDescription = "Home",
    )
} else {
    SymbolFontIcon(
        MaterialSymbols.Home.codePoint,
        MaterialSymbolsRoundedStatic,
        contentDescription = "Home",
        fontSettings = MaterialSymbolsRoundedStatic.fontSettings,
    )
}
```

For a list or grid, build one family and share it:

```kotlin
import io.github.hlcaptain.symbols.font.rememberSymbolFontFamily
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.outlined.MaterialSymbolsOutlined

val axes = MaterialSymbolAxes(weight = 500)
MaterialSymbolsTheme(axes = axes) {
    val family = rememberSymbolFontFamily(MaterialSymbolsOutlined)

    LazyRow {
        items(symbols) { symbol ->
            SymbolFontIcon(
                codePoint = symbol.codePoint,
                fontFamily = family,
                contentDescription = symbol.name,
            )
        }
    }
}
```

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
- [Runnable sample modules](samples/README.md)
- [Release process](RELEASING.md)
- [Contributing](CONTRIBUTING.md)

Run `./gradlew :composeApp:run` for the desktop sample launcher, then select the
focused module to preview. Use `./gradlew :composeApp:assembleDebug` for the
Android launcher and its Android Views sample. Maintainer generator,
font-conformance, and shrink-test commands live in the linked guides and CI.

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
