# Symbols usage reference

Start with the [quick start](../README.md) for a migration from Material Icons
Extended. This reference covers artifact choices, alternate renderers, catalog
lookup, and runtime font configuration.

## Choose an artifact

All coordinates use the `io.github.hlcaptain` group and version `2.1.0`.
Kotlin Multiplatform consumers add dependencies to `commonMain.dependencies`.
See the [consumer toolchain requirements](TOOLCHAIN_UPGRADE.md) before upgrading
from Symbols 1.x.

| Need | Artifact | Runtime payload |
| --- | --- | --- |
| Common built-in/generated namespace only | `symbols-core` | Zero-dependency `Symbols` entry point |
| Material names and code points only | `symbols-material-core` | Typed catalog; no Compose or bundled font |
| Familiar Compose `Icon(ImageVector, ...)` | `symbols-material-vectors-{outlined|rounded|sharp}` | Fixed vectors for one style; unused typed vectors can be removed by R8 |
| Runtime style selection in Compose | `symbols-material-vectors-themed` | Fixed vectors for all three styles |
| Standard Compose `painterResource(Res.drawable...)` | `symbols-material-compose-drawables-{outlined|rounded|sharp}` | Fixed Compose Multiplatform XML resources for one complete style |
| Android XML and Views | `symbols-material-drawables-{outlined|rounded|sharp}` | Native `VectorDrawable` resources; unused resources can be removed by Android resource shrinking |
| Runtime custom regular or variable fonts | `symbols-variant-font-core` | Generic Compose theme and code-point renderer; no bundled font or catalog |
| Material vector style and font-settings theming | `symbols-material-compose` | Material style plus generic font settings; no bundled font |
| Font icons on Android API 23+ | `symbols-material-{outlined|rounded|sharp}-static` | One indivisible regular font at the default axes |
| Live fill, weight, grade, or optical size | `symbols-material-{outlined|rounded|sharp}` | One indivisible variable font; variable axes require Android API 26+ |

## Migrate from `material-icons-extended`

The call site stays conventional. Change the dependency and imports, then keep
using the standard Compose `Icon` composable:

```kotlin
import androidx.compose.material3.Icon
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.Rounded
import io.github.hlcaptain.symbols.material.rounded.vectors.Home

Icon(
    imageVector = Symbols.Material.Rounded.Home,
    contentDescription = "Home",
)
```

The call shape matches `material-icons-extended`; the artwork is Material
Symbols at `FILL=0, GRAD=0, opsz=24, wght=400`, so it is not always visually
identical to the legacy icon. Rounded also provides the explicit filled
snapshot below. Generate another fixed-axis snapshot or use the font renderer
below when the design needs other axes.

Each typed property directly reaches one icon's cache. There is no reflective
registry on this path, so full-mode R8 can reason about icons independently.
Vectors are created and cached on first use; aliases that share a code point
share the cached vector.

For a named icon that should mirror in right-to-left layouts, opt in through
the typed `AutoMirrored` namespace:

```kotlin
import androidx.compose.material3.Icon
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.AutoMirrored
import io.github.hlcaptain.symbols.material.Icons
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.Outlined
import io.github.hlcaptain.symbols.material.outlined.vectors.ArrowBack

Icon(
    imageVector = Symbols.Material.AutoMirrored.Outlined.ArrowBack,
    contentDescription = "Back",
)

// Equivalent namespace for migration from material-icons-extended:
val back = Icons.AutoMirrored.Outlined.ArrowBack
```

Every named icon has an opt-in mirrored getter in Outlined, Rounded, and Sharp.
Import it from the same style's `vectors` package as its ordinary getter; use
the existing per-style artifact. Choose mirroring only when the icon's meaning
should follow layout direction. Ordinary `Symbols.Material.{Style}.{Name}` and
`Icons.{Style}.{Name}` getters remain unmirrored.

Typed mirrored getters directly reach the same per-icon cache with
`autoMirror = true`, preserving independent R8 removal of unused icons. Mirrored
and unmirrored instances are cached separately, with aliases sharing each
variant's cache. This adds no runtime dispatcher, renderer, or artifact.

### Filled Rounded vectors

Symbols 2.1.0 adds `FILL=1` vectors to the existing Rounded
artifact. Keep the standard Compose `Icon` API and import `Filled` plus the
same generated icon properties:

```kotlin
import androidx.compose.material3.Icon
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.AutoMirrored
import io.github.hlcaptain.symbols.material.Filled
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.Rounded
import io.github.hlcaptain.symbols.material.rounded.vectors.Favorite
import io.github.hlcaptain.symbols.material.rounded.vectors.VolumeOff

Icon(Symbols.Material.Rounded.Filled.Favorite, contentDescription = "Favorite")
Icon(Symbols.Material.AutoMirrored.Rounded.Filled.VolumeOff, contentDescription = "Muted")
```

`Icons.Rounded.Filled.*` and `Icons.AutoMirrored.Rounded.Filled.*` are equivalent
entry points. All named Rounded icons support both getters. They snapshot
`FILL=1, GRAD=0, opsz=24, wght=400`; ordinary getters retain `FILL=0`. Choose the
fill explicitly for each state. Catalog aliases such as `Favorite` and
`FavoriteBorder` share the same outline, so changing the name alone does not
select a different fill.

Rounded outlines are stored as separate per-icon SVG path strings and parsed
by Compose when that vector is first requested. A shared builder owns each
icon's cached normal and mirrored instances; there is no runtime font asset or
catalog-wide path table. Filled aliases share their codepoint's cache, and
fill-invariant icons such as `Check` and `ArrowBack` reuse the ordinary vector.
The themed getters continue to select the default `FILL=0` snapshots.

### Theme-selected vectors

Use the themed vector artifact when style is selected at runtime:

```kotlin
import androidx.compose.material3.Icon
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.vectors.themed.ArrowBack
import io.github.hlcaptain.symbols.material.vectors.themed.Home

MaterialSymbolsTheme(style = MaterialSymbolStyle.Rounded) {
    Icon(
        imageVector = Symbols.Material.Themed.Home,
        contentDescription = "Home",
    )
    Icon(
        imageVector = Symbols.Material.AutoMirrored.Themed.ArrowBack,
        contentDescription = "Back",
    )
}
```

`Symbols.Material.Themed.*` and `Symbols.Material.AutoMirrored.Themed.*` are
composable read-only properties that return standard `ImageVector` values.
`MaterialSymbolsTheme` selects their Outlined, Rounded, or Sharp fixed snapshot;
font settings do not change vector axes. Every named icon supports opt-in
mirroring through `AutoMirrored.Themed` (also available as
`Icons.AutoMirrored.Themed`); ordinary themed getters remain unmirrored. These
typed getters select cached per-style vectors directly, without dynamic catalog
dispatch.

The theme also forwards generic font settings to runtime font renderers. Axis
definitions come from each font descriptor; regular fonts accept only the
settings declared by their descriptor. The mirrored vector API does not change
font rendering.

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
    imageVector = symbol.asOutlinedImageVector(
        autoMirror = true,
    ),
    contentDescription = symbol.name,
)
```

Dynamic lookup keeps the pack index and dispatcher reachable. Prefer direct
`Symbols.Material.{Style}.{Name}` or
`Symbols.Material.AutoMirrored.{Style}.{Name}` properties when the icon is known
at compile time and minimum APK size matters.

## Use Compose Multiplatform drawable resources

Choose one style when standard `Res.drawable` and `painterResource` integration
is more convenient than an `ImageVector`:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(
            "io.github.hlcaptain:symbols-material-compose-drawables-rounded:2.1.0",
        )
    }
}
```

The generated `Res` class and drawable accessors are public:

```kotlin
import androidx.compose.material3.Icon
import io.github.hlcaptain.symbols.material.rounded.compose.drawables.resources.Res
import io.github.hlcaptain.symbols.material.rounded.compose.drawables.resources.material_symbols_rounded_home_ue9b2
import org.jetbrains.compose.resources.painterResource

Icon(
    painter = painterResource(
        Res.drawable.material_symbols_rounded_home_ue9b2,
    ),
    contentDescription = "Home",
)
```

Each artifact publishes one complete default-axis style on Android, JVM, JS,
Wasm, and iOS. The 4,102 semantic names resolve to 3,802 unique-codepoint
resources per style; aliases sharing a code point share the canonical generated
resource name. Compose packages these resources as assets on Android, so the
Android resource shrinker does not remove unused files from this representation.
Use a typed `ImageVector` pack when per-icon R8 reachability and minimum Android
APK size are the primary concerns.

## Use Android Views and XML

The drawable artifacts are ordinary Android AARs with no font dependency. Add
one style to an Android module:

```kotlin
dependencies {
    implementation(
        "io.github.hlcaptain:symbols-material-drawables-outlined:2.1.0",
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
[plain XML](../samples/android-views-platform/src/main/res/layout/android_views_content.xml),
[Data Binding](../samples/android-views-platform/src/main/res/layout/data_binding_icon.xml),
[View Binding inside Compose](../samples/android-views-platform/src/main/kotlin/io/github/hlcaptain/symbols/sample/androidviews/AndroidViewsContent.kt),
and a [custom `ImageView`](../samples/android-views-platform/src/main/kotlin/io/github/hlcaptain/symbols/sample/androidviews/GeneratedSymbolView.kt).
Compose hosts the View hierarchy with `AndroidView`; no second Activity is
needed. The same layout consumes generated Tabler SVG resources through plain
XML, `AppCompatResources`, and the custom View attribute. These are Android
View-system integrations, not deprecated Android APIs.

## Catalog lookup

The generated Material catalog keeps aliases and raw code points available
without requiring a rendering artifact:

```kotlin
val search = Symbols.Material.fromName("search")
val allStarNames = Symbols.Material.aliases(Symbols.Material.Star)
val everyName = Symbols.Material.all

check(search?.codePoint == 0xE8B6)
check(Symbols.Material.size == 4_102)
```

Aliases remain distinct names even when they share a code point. Unknown names
return `null`; unknown code points return an empty alias list. Code points are
stored as `Int`, including valid Unicode scalars outside the BMP.

## Advanced: render directly from a font

Use runtime fonts when an icon is selected dynamically or its axes must change
without regenerating a vector. Every font artifact contains one indivisible
font; unused glyphs are not removed individually. `SymbolFontIcon` defaults to
black, so pass a tint from your UI theme. Use a localized `contentDescription`,
or `null` when the icon is decorative.

For animated settings, tint, and layer transforms, see
[rendering performance](PERFORMANCE.md#animated-font-axes). Settings producers
defer state reads to drawing; changing axes still shapes and rasterizes glyphs.

### Material Symbols font renderer

Each Material font exposes the same generic descriptor API as a custom font.
Read its `variationAxes` metadata and create validated settings with
`fontSettings(...)`; `MaterialSymbolsTheme` combines those generic settings with
the style used by theme-selected vectors:

```kotlin
import androidx.compose.material3.MaterialTheme
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.fontSettings
import io.github.hlcaptain.symbols.material.*

val font = Symbols.Material.Rounded.font
val settings = font.fontSettings(
    mapOf("FILL" to 1f, "wght" to 500f, "GRAD" to 0f, "opsz" to 24f),
)

MaterialSymbolsTheme(
    style = MaterialSymbolStyle.Rounded,
    fontSettings = settings,
) {
    SymbolFontIcon(
        codePoint = Symbols.Material.Home.codePoint,
        font = font,
        contentDescription = "Home",
        tint = MaterialTheme.colorScheme.primary,
    )
}
```

A regular Material font artifact provides a fixed default-axis fallback on
Android API 23–25:

Add both `symbols-material-rounded` and `symbols-material-rounded-static` when
the application selects between these paths.

```kotlin
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolsRuntime
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.*

val (font, staticFont) = Symbols.Material.Rounded

if (SymbolsRuntime.variableFontsSupported) {
    SymbolFontIcon(
        Symbols.Material.Home.codePoint,
        font,
        contentDescription = "Home",
    )
} else {
    SymbolFontIcon(
        Symbols.Material.Home.codePoint,
        staticFont,
        contentDescription = "Home",
        fontSettings = staticFont.fontSettings,
    )
}
```

Descriptor-based `SymbolFontIcon` calls share their native base font
automatically. When interoperating through the Compose `FontFamily` overload,
remember one family and share it across a list or grid:

```kotlin
import io.github.hlcaptain.symbols.font.rememberSymbolFontFamily
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.fontSettings
import io.github.hlcaptain.symbols.material.*

val font = Symbols.Material.Outlined.font
val settings = font.fontSettings(mapOf("wght" to 500f))
MaterialSymbolsTheme(fontSettings = settings) {
    val family = rememberSymbolFontFamily(font)

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

## Material font axes

| Meaning | Font axis | Range | Default |
| --- | --- | ---: | ---: |
| `fill` | `FILL` | `0..1` | `0` |
| `weight` | `wght` | `100..700` | `400` |
| `grade` | `GRAD` | `-50..200` | `0` |
| `opticalSize` | `opsz` | `20..48` | `24` |

The generated variable-font descriptor reads these definitions from the
bundled font rather than a Material-only Kotlin type. `fontSettings(...)`
validates supplied values against that metadata. A distinct axis combination
produces a distinct remembered font family.

## Custom runtime fonts

Use [custom fonts](CUSTOM_FONTS.md) for packaged regular or variable fonts,
generated descriptors, axis validation, and Android API 23–25 fallbacks.
Material fonts and custom fonts use the same `SymbolFontIcon` and `SymbolsTheme`
APIs; each descriptor declares its own supported axes.

`SymbolFont` is the sealed runtime contract. Manual descriptors implement its
open `SymbolFont.Variable` or `SymbolFont.Regular` interface, never both.
Generated variable descriptors expose visible `fvar` axes as `variationAxes`;
`fontSettings(...)` validates supplied tags and ranges and fills omitted axes
with the font's defaults. A regular descriptor declares one fixed settings
point and rejects requests for different settings.

## Generate a custom icon set

The [generator guide](GENERATOR.md) covers plugin resolution, fonts and
codepoint manifests, the supported SVG subset, Android resource variants,
generated namespaces, and output locations. Font vectors and XML resources are
fixed snapshots. A stroked SVG can instead use the
[theme-aware painter](GENERATOR.md#consume-the-outputs) to vary stroke width.

Use `font.set(...)` with a path outside packaged resource directories when the
font is only a generation input. That avoids shipping both its font file and
its generated outlines.

## Samples and source builds

Explore [runnable sample modules](../samples/README.md), including migration,
Android Views, custom fonts, and runtime axes. Source contributors should set
up the pinned [Python/FontTools environment](../CONTRIBUTING.md#development-environment);
published dependency consumers need no Python. Use the
[release guide](../RELEASING.md#consuming-testing-snapshots) for authenticated,
commit-pinned testing snapshots, or `publishToMavenLocal` in the root and
`tooling` builds for local development.
