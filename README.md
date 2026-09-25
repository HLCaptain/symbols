# Symbols

A migration-friendly alternative to Compose's `material-icons-extended`: use
one `Symbols` root while choosing shrinkable
`ImageVector`s, Android XML drawables, regular fonts, or live variable fonts.
Use Material Symbols, another icon font, or generate a typed icon set from your
own font or SVG assets.

[![CI](https://github.com/HLCaptain/symbols/actions/workflows/ci.yml/badge.svg)](https://github.com/HLCaptain/symbols/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

> Symbols is preparing its first `0.1.0` release. Snapshot APIs may still change.

The Material catalog contains all 4,102 upstream names across Outlined,
Rounded, and Sharp styles. The same project also supports generated custom icon
sets, Compose Multiplatform (Android API 23+), and legacy Android Views and XML (API 21+).
Compose consumers require AGP 9.1+, compile SDK 37 and Kotlin 2.4; XML-only
consumers can keep AGP 8.13/Gradle 8. See the [toolchain migration](docs/TOOLCHAIN_UPGRADE.md).

## Add Symbols

The first stable release is configured for Maven Central, so release builds only
need the repositories most Android and Kotlin projects already use:

```kotlin
repositories {
    google()
    mavenCentral()
}
```

When building or publishing from source, configure the [Python/FontTools environment](CONTRIBUTING.md#development-environment) first.

Development snapshots are intentionally not uploaded to a remote package
registry. To consume the current source from another checkout, publish it to
the local Maven repository:

```shell
./gradlew publishToMavenLocal
./tooling/gradlew -p tooling publishToMavenLocal
```

Add `mavenLocal()` before `mavenCentral()` while testing that snapshot.

Pick the artifact that matches how the application renders icons:

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
identical to the legacy icon. Generate another fixed-axis snapshot or use the
font renderer below when the design needs different axes.

Each typed property directly reaches one vector builder. There is no reflective
registry on this path, so full-mode R8 can reason about icons independently.
Vectors are created and cached on first use; aliases that share a code point
share the cached vector.

Use the themed vector artifact when style is selected at runtime:

```kotlin
import androidx.compose.material3.Icon
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.vectors.themed.Home

MaterialSymbolsTheme(style = MaterialSymbolStyle.Rounded) {
    Icon(
        imageVector = Symbols.Material.Themed.Home,
        contentDescription = "Home",
    )
}
```

`Symbols.Material.Themed.*` values are composable read-only properties.
`MaterialSymbolsTheme` selects their Outlined, Rounded, or Sharp snapshot and
can forward generic font settings to a runtime renderer. Axis definitions come
from each font descriptor. Fixed vectors ignore font settings; regular fonts
accept only the settings declared by their descriptor.

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
    imageVector = Symbols.Material.ArrowBack.asOutlinedImageVector(
        autoMirror = true,
    ),
    contentDescription = "Back",
)
```

Dynamic lookup keeps the pack index and dispatcher reachable. Prefer direct
`Symbols.Material.{Style}.{Name}` properties when minimum APK size matters.

## Use Compose Multiplatform drawable resources

Choose one style when standard `Res.drawable` and `painterResource` integration
is more convenient than an `ImageVector`:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(
            "io.github.hlcaptain:symbols-material-compose-drawables-rounded:0.1.0-SNAPSHOT",
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
[plain XML](samples/android-views-platform/src/main/res/layout/android_views_content.xml),
[Data Binding](samples/android-views-platform/src/main/res/layout/data_binding_icon.xml),
[View Binding inside Compose](samples/android-views-platform/src/main/kotlin/io/github/hlcaptain/symbols/sample/androidviews/AndroidViewsContent.kt),
and a [custom `ImageView`](samples/android-views-platform/src/main/kotlin/io/github/hlcaptain/symbols/sample/androidviews/GeneratedSymbolView.kt).
Compose hosts the View hierarchy with `AndroidView`; no second Activity is
needed. The same layout consumes generated Tabler SVG resources through plain
XML, `AppCompatResources`, and the custom View attribute. These are Android
View-system integrations, not deprecated Android APIs.

## Generate a custom icon set

The Gradle plugin converts a regular font, a fixed variable-font instance, or a
flat directory of monochrome SVGs into typed Compose vectors, Android
drawables, Compose drawable resources, or any combination:

Plugin markers resolve in `pluginManagement`, not the dependency repository
block. Stable releases resolve through `gradlePluginPortal()` or `mavenCentral()` there; source checkouts can use an
included `tooling` build. The complete settings blocks are in the
[generator guide](docs/GENERATOR.md).

```kotlin
plugins {
    id("io.github.hlcaptain.symbol-fonts") version "0.1.0"
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

SVG input needs neither a font nor a codepoint manifest:

```kotlin
symbolFonts {
    iconSet("Tabler") {
        style("Outline") {
            svgDirectory.set(layout.projectDirectory.dir("icons/tabler"))
            imageVectors()
            androidDrawables()
            composeDrawables()
        }
    }
}
```

Every manifest entry or direct `.svg` file is generated implicitly; the Gradle
DSL has no `include`/`includeAll` selection mode. SVG filenames become semantic
names, so `hierarchy-2.svg` produces `Symbols.Tabler.Outline.Hierarchy2` and
`tabler_outline_hierarchy_2`. R8 handles generated Kotlin vectors on Android,
and Android's resource shrinker handles native drawables. Split a very large
source set into separate icon sets only when measured build or packaging cost
requires that boundary.

`composeDrawables()` writes XML below `build/` and registers ordinary Compose
Multiplatform resources, so callers use the generated `Res.drawable.<name>`
with `painterResource`.

The `axis(...)` values are applied while generating font outlines. Those font
vectors and all XML resources are fixed snapshots. A stroked SVG `ImageVector`
also keeps its authored width when passed directly to `Icon`; pass it to
`rememberSymbolPainter()` to reuse the current `SymbolsTheme` `wght` setting at
render time. Weight 100, 400, and 700 map to 0.5×, 1×, and 1.5× the authored
stroke width. The `rememberSymbolPainter { settingsState.value }` overload
keeps that snapshot read inside the painter's vector child composition, so the
calling composition does not need to observe it. A changed stroke still updates
and rasterizes the vector subtree. Legacy Android and Compose XML remain fixed
at 1×.

`font("rounded-variable.ttf")` searches `src/main/res/font`,
`src/androidMain/res/font`, and `src/commonMain/composeResources/font`. Omit it
when those directories contain only one supported font, or use `font.set(...)`
for another path. Android
projects default to `<android namespace>.generated` for generated Kotlin.
Fonts inside resource directories are also packaged at runtime. When a font is
only a generator input, keep it elsewhere and use `font.set(...)` so the app
does not ship both the font and its generated vectors or drawables.

Keep `androidDrawables()` argument-free when every Android variant uses the
main source-set font. Projects with intentional `res/font` build-type or flavor
overlays can opt into
[`androidDrawables(fontResource = "app_icons.ttf")`](docs/GENERATOR.md#variant-aware-android-font-resources).
That variant-aware mode uses normal Android namespaces and generated `R.drawable`
resources; it needs neither another package nor another plugin.

Generated vectors use the same familiar call shape:

```kotlin
import com.example.app.generated.AppIcons
import com.example.app.generated.rounded.Home
import io.github.hlcaptain.symbols.Symbols

Icon(
    imageVector = Symbols.AppIcons.Rounded.Home,
    contentDescription = "Home",
)
```

Generated namespaces require the tiny `symbols-core` runtime dependency. The
Gradle plugin deliberately does not add application dependencies itself.

The focused custom samples keep their build files intentionally small.
[`custom-static`](samples/custom-static/build.gradle.kts) reads Powerline from
its conventional `composeResources/font` directory and shows its typed vector
beside the generated `Res.drawable` painter, while
[`custom-variable`](samples/custom-variable/build.gradle.kts) compares
Academmunicons fixed at `ital=0,wght=600` before vector generation with the same
custom variable font adjusted live through a generated runtime descriptor and
the fixed Compose drawable.
Continuous Material sliders (`steps = 0`) with a start/stop control for every
axis are demonstrated by
[`runtime-axes`](samples/runtime-axes/src/commonMain/kotlin/io/github/hlcaptain/symbols/sample/runtimeaxes/RuntimeAxesSample.kt).
The [`image-vector-migration`](samples/image-vector-migration/src/commonMain/kotlin/io/github/hlcaptain/symbols/sample/imagevectormigration/ImageVectorMigrationSample.kt)
screen keeps the old Material Icons Extended call beside the generated Symbols
call, then shows Academmunicons defaults and three pinned Tabler `v3.46.0` SVGs
as a typed vector, theme-aware painter, Compose drawable, and legacy Android
XML resource. Its slider can animate one `SymbolsTheme` `wght` value for both a
variable font and the SVG painter. Roborazzi covers the font/SVG visual contract;
see [screenshot testing](docs/SCREENSHOT_TESTING.md).

The complete Font Awesome and Tabler font fixtures remain under
[`fonts/samples`](fonts/samples/README.md); the launcher only packages the three
focused Tabler SVG samples. The optional
[SVG icon font CLI](tools/README.md#svg-icon-font-generation) remains available
when a product specifically needs a TTF and stable codepoint manifest instead
of direct vectors.

See [build-time generation](docs/GENERATOR.md) for plugin resolution, the full
DSL, generated resource names, caching, and shrinker boundaries. See
[custom fonts](docs/CUSTOM_FONTS.md) for the codepoint manifest format and
runtime regular/variable font contracts.

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

Configured conventional and task-backed roots use the standard `font/`
directory. Compose Resources generates `Res.font.<name>`, while Symbols adds the typed
`Res.symbolFonts.<name>` descriptor, embedded axis metadata, and an automatic
public accessor such as `Symbols.MySymbolsVariable`. Use `fontAccessor` only
when a library needs to override its package, receiver, property name, fixed
settings, or destructuring component.

Use the generated public accessor after importing it from the configured
package:

```kotlin
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolsTheme
import io.github.hlcaptain.symbols.font.fontSettings
import my.symbols.generated.resources.MySymbolsVariable

val mySymbols = Symbols.MySymbolsVariable

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

Use `settings.withVariations(FontVariation.Setting("wght", 525f))` to overlay
raw Compose variations while retaining the other settings. This convenience is
font-agnostic; prefer `fontSettings(...)` when values need descriptor validation.

`SymbolFontIcon` removes the private-use glyph from semantics and exposes only a
supplied, localized description. Use `contentDescription = null` for a
decorative icon. A `SymbolFont.Regular` declares one fixed `fontSettings` point;
it renders on Android API 23 and rejects other requested settings.

Variable-font APIs start on Android API 26. Check
`SymbolsRuntime.variableFontsSupported` before selecting a variable font, and
use a regular font or generated vector/drawable as the Android API 23–25
fallback.

For animated axes, pass a settings producer to the same `SymbolFontIcon` API:

```kotlin
import androidx.compose.animation.core.animateFloatAsState

val weight = animateFloatAsState(
    targetValue = if (emphasized) 700f else 100f,
    label = "Symbol weight",
)
SymbolFontIcon(
    codePoint = 0xF0001,
    font = mySymbols,
    contentDescription = "Custom action",
    fontSettings = { mySymbols.fontSettings(mapOf("wght" to weight.value)) },
)
```

Read animation state inside the non-composable producer. Symbols reads it while
drawing, so changing an axis redraws the glyph without recomposing or laying out
the fixed icon square. The native font still applies the new variation and
shapes/rasterizes the glyph. Value and producer calls using a `SymbolFont` share
the same native core; the `FontFamily` overload remains a Compose text adapter.
See [rendering performance](docs/PERFORMANCE.md)
for the implementation and measurement boundaries.

For animated color too, pass `tint = { animatedColor.value }` to the native
settings-producer overload. This uses Compose's `ColorProducer` and reads color
during drawing. Keep `size` fixed and use the component's `graphicsLayer = { ... }`
block for animated visual scale, opacity, rotation and translation. It uses the
standard `GraphicsLayerScope` and defaults to efficient per-draw alpha compositing:

```kotlin
SymbolFontIcon(
    codePoint = icon.codePoint,
    font = mySymbols,
    contentDescription = null,
    fontSettings = { animatedSettings.value },
    tint = { animatedColor.value },
    graphicsLayer = {
        alpha = opacity.value
        scaleX = scale.value
        scaleY = scale.value
        rotationZ = rotation.value
    },
)
```

Existing value calls remain supported and use the same renderer. The component
owns the layer's default compositing strategy; callers do not have to select it.

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

For a list or grid, build one family and share it:

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

## Reference and development

- [Architecture and module map](docs/ARCHITECTURE.md)
- [Performance and APK-size trade-offs](docs/PERFORMANCE.md)
- [Build-time generator](docs/GENERATOR.md)
- [Custom font guidance](docs/CUSTOM_FONTS.md)
- [Runnable sample modules](samples/README.md)
- [Release process](RELEASING.md)
- [Contributing](CONTRIBUTING.md)

For source builds, first configure the pinned Python/FontTools environment in
[Contributing](CONTRIBUTING.md#development-environment); built-in vector sources are
generated under `build/`. Published artifact consumers need no Python.

Run `./gradlew :composeApp:run` for the desktop sample launcher, then select the
focused module to preview. Use `./gradlew :androidApp:assembleDebug` for the
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
