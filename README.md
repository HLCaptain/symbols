# Symbols

**Modern Material icons. Less maintenance.**

Variable fonts, your own fonts and SVGs, and familiar Compose Multiplatform APIs.<br>
Shrinkable vectors and Android drawables that help keep APKs small.

[![CI](https://github.com/HLCaptain/symbols/actions/workflows/ci.yml/badge.svg)](https://github.com/HLCaptain/symbols/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

![Home, AccountTree, Favorite, and VolumeOff compared side by side. Classic Material Icons Extended vectors keep their shapes on the left; the Rounded Material Symbols variable font is on the right. Material Icons Extended stays black. Symbols changes rainbow color during each transition and holds its color between transitions. Both columns start filled. Symbols varies weight, optical size, and grade, toggling fill after each axis step with a pause after every transition. Two bold single chevrons make a slow quartic nudge toward Symbols and return to rest during each transition, with synchronized timing and the left chevron moving slightly farther than the right. The sequence returns to filled icons at default weight, grade, optical size, and its initial color.](docs/media/icons-comparison.png)

Replace the deprecated `material-icons-extended` library while keeping Compose's standard `Icon`.
Add a pack and use typed names—without per-icon downloads or hand-maintained
generated sources. Prebuilt packs need no Symbols Gradle plugin.
Choose `Outlined`, `Rounded`, or `Sharp` Material Symbols, including filled `Rounded`
vectors in 2.1.0. The catalog has 4,102 names covering 3,802 unique codepoints
per style. Use the same `Symbols` root for generated icons from your own assets.

## Add Symbols

Releases are on Maven Central. Add the style you use:

```kotlin
repositories {
    google()
    mavenCentral()
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation("io.github.hlcaptain:symbols-material-vectors-rounded:2.1.0")
    }
}
```

For an Android-only module, put `implementation(...)` in `dependencies`.

[![Compose Multiplatform: Android, iOS, desktop JVM, JS, and Wasm](https://img.shields.io/badge/Compose%20Multiplatform-Android%20%7C%20iOS%20%7C%20JVM%20%7C%20JS%20%7C%20Wasm-7F52FF?logo=kotlin&logoColor=white)](docs/TOOLCHAIN_UPGRADE.md)
[![Android Compose minimum API: 23](https://img.shields.io/badge/Android%20Compose-API%2023%2B-3DDC84?logo=android&logoColor=white)](docs/TOOLCHAIN_UPGRADE.md)
[![Android Compose minimum AGP: 9.1.1](https://img.shields.io/badge/AGP-9.1.1%2B-3DDC84)](docs/TOOLCHAIN_UPGRADE.md)
[![Android Compose compile SDK: 37](https://img.shields.io/badge/compile%20SDK-37-3DDC84)](docs/TOOLCHAIN_UPGRADE.md)
[![Kotlin: 2.4](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](docs/TOOLCHAIN_UPGRADE.md)

See the [full requirements](docs/TOOLCHAIN_UPGRADE.md) when upgrading from 1.x.

## Migrate from `material-icons-extended`

Change the dependency and imports; the `Icon` call stays familiar:

<a id="filled-rounded-vectors"></a>

```kotlin
import androidx.compose.material3.Icon
import io.github.hlcaptain.symbols.material.Icons
import io.github.hlcaptain.symbols.material.rounded.vectors.Home

Icon(Icons.Rounded.Home, contentDescription = "Home")

// AutoMirrored: opt-in mirroring for right-to-left layouts.
Icon(Icons.AutoMirrored.Rounded.Home, contentDescription = "Home")

// Filled: the filled style used by some legacy Material Icons.
Icon(Icons.Rounded.Filled.Home, contentDescription = "Home")
```

`Symbols.Material.Rounded.Home` is the equivalent unified namespace. Artwork,
available styles, and some names differ; review the visual result when migrating.

| | Material Icons Extended | Symbols |
| --- | --- | --- |
| Artwork | Classic Material Icons | Modern Material Symbols |
| Compose API | `Icon(Icons.Rounded.Home, …)` | Same call shape, new imports |
| Customization | Predefined vector variants | Fixed vectors, variable font, your own fonts and SVGs |

Direct named vectors are cached on first use and allow R8 to remove unused
icons independently. The artwork comes from Google's
[Material Symbols repository](https://github.com/google/material-design-icons).
Ordinary getters use `FILL=0`; `.Filled` selects `FILL=1`.

<a id="theme-selected-vectors"></a>

For runtime style selection, [themed vectors](docs/USAGE.md#theme-selected-vectors)
follow `MaterialSymbolsTheme` while retaining fixed, unfilled geometry.

In a [recorded Android fixture (September 2026)](benchmarks/shrinkable-vectors/README.md#recorded-resource-overlay-result-2026-09-02-check-only),
R8 and resource shrinking reduced the APK from **8.01 MB to 220 KB**, retaining
the used Symbols vector and native drawable while removing unused ones. These
historical whole-APK figures include transitive code and resources; application
savings vary. See [shrinking boundaries](docs/PERFORMANCE.md#shrinkability-boundaries).

<a id="advanced-render-directly-from-a-font"></a>
<a id="material-symbols-font-renderer"></a>
<a id="material-font-axes"></a>
<a id="animate-variable-fonts"></a>

## Variable font styling

Animate fill, weight, grade, or optical size without generating another vector.
Add `io.github.hlcaptain:symbols-material-rounded:2.1.0` for the Rounded variable
font, then use your Material 3 motion scheme:

![Four columns demonstrate fill, weight, grade, and optical size using one Rounded variable font. Weight, grade, and optical size move together from medium to small, medium, large, and medium; fill alternates between outline and filled. Numeric axis values appear below each icon. The eight-second loop uses Material 3 Expressive default spatial motion. Optical size changes glyph detail within a fixed layout size.](docs/media/variable-fonts.png)

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.fontSettings
import io.github.hlcaptain.symbols.material.*

@Composable
fun FavoriteSymbol(selected: Boolean) {
    MaterialExpressiveTheme {
        val font = Symbols.Material.Rounded.font
        val fill = animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
            label = "Favorite fill",
        )
        SymbolFontIcon(
            codePoint = Symbols.Material.Favorite.codePoint,
            font = font,
            fontSettings = { font.fontSettings(mapOf("FILL" to fill.value.coerceIn(0f, 1f))) },
            contentDescription = "Favorite",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
```

Read animation state inside the producer to redraw without recomposing or
laying out the fixed icon square. Clamping keeps spring overshoot within the
font's valid range. Variable axes require Android API 26+; use a
[regular font or vector fallback](docs/USAGE.md#material-symbols-font-renderer)
on API 23–25. Runtime fonts ship as a whole font, with no per-glyph shrinking.

## Generate a custom icon set

Bring a regular or variable icon font plus its codepoint manifest, or a flat
directory of monochrome SVGs. Both produce the same typed `ImageVector` API:

```kotlin
plugins {
    id("io.github.hlcaptain.symbol-fonts") version "2.1.0"
}

symbolFonts {
    iconSet("AppIcons") {
        packageName.set("com.example.generated")
        style("Outline") {
            svgDirectory.set(layout.projectDirectory.dir("icons"))
            imageVectors()
        }
    }
}
```

Add `io.github.hlcaptain:symbols-core:2.1.0` to your application dependencies.
With `icons/home.svg`, import the generated properties and render it normally:

```kotlin
import androidx.compose.material3.Icon
import com.example.generated.AppIcons
import com.example.generated.outline.Home
import io.github.hlcaptain.symbols.Symbols

Icon(Symbols.AppIcons.Outline.Home, contentDescription = "Home")
```

For font input, replace `svgDirectory` with `font.set(...)` and
`codepoints.set(...)`; use `axis(...)` for a fixed variable-font instance.
Add `androidDrawables()` or `composeDrawables()` when you need XML output.
The [generator guide](docs/GENERATOR.md) covers setup and the supported SVG
subset.

<a id="custom-runtime-fonts"></a>

[Custom runtime fonts](docs/CUSTOM_FONTS.md) can also expose live axes
through the same `SymbolFontIcon` renderer.

## Use Android Views and XML

Native drawable artifacts work with Views, XML, and Android's resource shrinker:

```kotlin
implementation("io.github.hlcaptain:symbols-material-drawables-outlined:2.1.0")
```

```xml
<ImageView
    android:layout_width="24dp"
    android:layout_height="24dp"
    android:contentDescription="@string/home"
    android:src="@drawable/material_symbols_outlined_home_ue9b2" />
```

Enable normal release shrinking to remove unused native resources:

```kotlin
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}
```

XML-only consumers support API 21+ with the
[documented toolchain](docs/TOOLCHAIN_UPGRADE.md#consumer-requirements).

## Use Compose Multiplatform drawable resources

For `Res.drawable` and `painterResource`, use
`symbols-material-compose-drawables-{outlined|rounded|sharp}`. These packs ship
one complete style as Android assets, which Android's resource shrinker does
not prune. See [resource usage](docs/USAGE.md#use-compose-multiplatform-drawable-resources)
and [size trade-offs](docs/PERFORMANCE.md) to choose your representation.

## Reference and development

<a id="catalog-lookup"></a>

- [Usage reference](docs/USAGE.md): artifacts, theming, mirroring, catalog lookup, and font axes.
- [Runnable samples](samples/README.md), [generator](docs/GENERATOR.md), and [custom fonts](docs/CUSTOM_FONTS.md).
- [Contributing](CONTRIBUTING.md), [testing snapshots](RELEASING.md#consuming-testing-snapshots), and [releases](CHANGELOG.md).

## Asset provenance and licensing

Symbols and its bundled Material Symbols assets are Apache-2.0 licensed. Material
artwork comes from Google's [Material Symbols](https://github.com/google/material-design-icons);
this project is independent and is not endorsed by Google. See
[third-party notices](THIRD_PARTY_NOTICES.md) for exact asset versions and licenses,
and [security reporting](SECURITY.md) for the private vulnerability process.
