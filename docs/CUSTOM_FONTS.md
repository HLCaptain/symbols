# Custom fonts, code points, and SVG icons

Symbols supports custom regular and variable fonts as runtime Compose resources,
or fonts and flat SVG directories as build-time inputs for generated
`ImageVector`/drawable output. Prepare and license each input separately, then
choose whether the application should ship a runtime font or only generated
outlines.

Runtime custom-font support lives in the generic
`io.github.hlcaptain:symbols-variant-font-core` artifact. Material font artifacts
depend on it transitively; a custom-only module can add it directly:

```kotlin
implementation(
    "io.github.hlcaptain:symbols-variant-font-core:0.1.0-SNAPSHOT",
)
```

In Kotlin Multiplatform, declare this in `commonMain.dependencies`.

## Package a variable font

Place one font in the custom module:

```text
src/commonMain/composeResources/font/my_symbols_variable.ttf
```

Runtime fonts use Compose Multiplatform `FontResource`; Android `R.font`
resources are supported as outline-generator inputs but do not implement this
runtime contract. Generate the runtime descriptor and its `fvar` metadata from
the Compose resource root:

```kotlin
symbolFonts {
    composeFontResources.from(
        layout.projectDirectory.dir("src/commonMain/composeResources"),
    )
}
```

Compose Resources exposes each configured file through its standard
`Res.font.<name>` accessor. Symbols uses that accessor to add a typed descriptor
through the module's global `Res` class:

```kotlin
import my.symbols.generated.resources.Res
import my.symbols.generated.resources.symbolFonts

val mySymbols = Res.symbolFonts.my_symbols_variable
```

The generated `SymbolFont.Variable` exposes visible axes from the font's `fvar`
table. Its settings helper fills omitted axes with their declared defaults and
validates tags and ranges. Supply the result directly or inherit it through the
generic `SymbolsTheme`:

```kotlin
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolsTheme
import io.github.hlcaptain.symbols.font.fontSettings

val settings = mySymbols.fontSettings(
    mapOf("FILL" to 1f, "wdth" to 110f, "wght" to 500f),
)

SymbolsTheme(fontSettings = settings) {
    SymbolFontIcon(
        codePoint = 0xF0001,
        font = mySymbols,
        contentDescription = "Custom action",
    )
}
```

Android can apply variable-font settings from API 26. Check
`SymbolsRuntime.variableFontsSupported` before selecting this path on an app
whose minSdk is lower.

After adding or removing a runtime font,
[build or re-import the project](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html)
once so `Res` and its accessors are regenerated; changing axis values needs no
rebuild.

The focused [`custom-variable`](../samples/custom-variable/build.gradle.kts)
sample shows both paths from one custom Academmunicons source. Its build-time
path fixes `ital=0,wght=600` before generating a typed `ImageVector`; those
coordinates cannot change afterward. Its runtime path uses the generated
`Res.symbolFonts.academmunicons_variable` descriptor and applies the original
font's `ital` and `wght` axes live without regenerating symbols. The
[`image-vector-migration`](../samples/image-vector-migration/build.gradle.kts)
sample omits `axis(...)` deliberately, generating both an `ImageVector` and a
Compose painter resource from the custom font's embedded
`ital=0,wght=100` defaults. It also generates Tabler SVG vectors/resources and
uses the same optionally animated `wght` setting for a variable font and an
adjustable SVG painter. Live generic controls with an independent animation
toggle beside every axis are demonstrated with Material Rounded in
[`runtime-axes`](../samples/runtime-axes/src/commonMain/kotlin/io/github/hlcaptain/symbols/sample/runtimeaxes/RuntimeAxesSample.kt).

`symbolFontText(codePoint)` is available for a custom `BasicText` layout. Both
it and `SymbolFontIcon` reject negative values, surrogate code points, and
values above `U+10FFFF`.

## Material-compatible custom fonts

Add the Material adapter when this module does not already depend on a bundled
Material font artifact:

```kotlin
implementation(
    "io.github.hlcaptain:symbols-material-compose:0.1.0-SNAPSHOT",
)
```

`MaterialSymbolAxes` remains a validated adapter for the bundled `FILL`,
`wght`, `GRAD`, and `opsz` ranges. Its `fontSettings` property exposes the
equivalent generic settings. `MaterialSymbolsTheme` provides both its Material
style/axes locals and those generic font settings, so a custom font with the
same axis contract can inherit them:

```kotlin
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.MaterialSymbolStyle
import io.github.hlcaptain.symbols.material.MaterialSymbolsTheme

MaterialSymbolsTheme(
    style = MaterialSymbolStyle.Rounded,
    axes = MaterialSymbolAxes(fill = 1f, weight = 500),
) {
    SymbolFontIcon(
        codePoint = 0xF0001,
        font = mySymbols,
        contentDescription = "Custom action",
    )
}
```

Use the generic `SymbolsTheme` instead when the custom font has different tags
or ranges. `MaterialSymbolsTheme` also owns the Outlined, Rounded, and Sharp
selection used by built-in `Symbols.Material.Themed.*` properties.

## Package a regular font

Static resources get a generated `SymbolFont.Regular` descriptor at default
settings:

```kotlin
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import my.symbols.generated.resources.Res
import my.symbols.generated.resources.symbolFonts

SymbolFontIcon(
    codePoint = 0xF0001,
    font = Res.symbolFonts.my_symbols_regular,
    contentDescription = "Custom action",
)
```

If a static file was frozen at a non-default coordinate that its tables no
longer describe, keep that value explicit:

```kotlin
import androidx.compose.ui.text.font.FontWeight
import io.github.hlcaptain.symbols.font.SymbolFont
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import my.symbols.generated.resources.Res
import my.symbols.generated.resources.my_symbols_bold

val myBoldSymbols = SymbolFont.regular(
    familyName = "My Symbols Bold",
    resource = Res.font.my_symbols_bold,
    fontSettings = SymbolFontSettings(weight = FontWeight.Bold),
)
```

The renderer never attaches `FontVariation.Settings` to a regular resource, so
it can render on Android API 21. It verifies that requested settings equal the
declared fixed value; pass it explicitly or provide it through `SymbolsTheme`.

`tools/generate_material_static_fonts.py --input ... --output ... --axis
TAG=VALUE` can freeze any variable font at build time. Unspecified axes use the
font's declared defaults, and the output contains no variation tables.

## Generate outlines instead of shipping a font

The `io.github.hlcaptain.symbol-fonts` Gradle plugin accepts a custom regular
font, a fixed instance of a custom variable font, or a flat SVG directory. It
validates the source and can emit:

- direct common Compose `ImageVector` properties;
- native Android vector drawables; and
- Compose Multiplatform drawable resources.

`composeDrawables()` keeps its generated XML below `build/` and exposes each
resource through the normal `Res.drawable.<name>` accessor for
`painterResource`.

Every manifest entry or direct SVG file is generated; the Gradle DSL has no
`include`/`includeAll` selection mode. A font selected from `src/main/res/font`
or `src/commonMain/composeResources/font` is also a runtime resource; use an
explicit file outside those directories when it should remain build-only. See
[build-time font and SVG conversion](GENERATOR.md) for the complete DSL,
generated API, supported SVG subset, and shrinker boundaries.

Font-derived `ImageVector`, Android XML, and Compose XML outputs are fixed
snapshots at the Gradle style's configured `axis(...)` coordinates. Direct SVG
vectors preserve their authored stroke when used as `ImageVector`s, while
generated XML always preserves that authored width.

Google's two-column `.codepoints` file is not universal. CSS pseudo-element
maps, Font Awesome metadata, IcoMoon/Fontello JSON, and the font's OpenType
`cmap` all describe related pieces of the same mapping but use different name
and number conventions. Normalize the chosen source into the plugin's stable
snake-case/hex manifest rather than making application builds depend on a
provider-specific parser. The checked-in
[provider fixtures](../fonts/samples/README.md) show five such conversions. The
runnable Powerline and Academmunicons modules keep focused copies of their
canonical manifests beside their own assets and show typed vectors beside
generated `Res.drawable` painters.

## Generate directly from SVG sources

Use `svgDirectory` when the source artwork is already a flat set of monochrome,
path-based SVG files:

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

Names come from filenames: `hierarchy-2.svg` becomes the Kotlin property
`Symbols.Tabler.Outline.Hierarchy2`, stable `ImageVector.name`
`Tabler.Outline.Hierarchy2`, and Android/Compose resource
`tabler_outline_hierarchy_2`. A font, codepoint allocation, and runtime SVG
parser are unnecessary.

For a theme-aware Compose painter, use the existing weight setting rather than
a second stroke API:

```kotlin
import io.github.hlcaptain.symbols.Symbols
import my.icons.generated.Tabler
import my.icons.generated.outline.Hierarchy2

SymbolsTheme(fontSettings = settings) {
    Icon(
        painter = Symbols.Tabler.Outline.Hierarchy2.rememberSymbolPainter(),
        contentDescription = "Hierarchy",
    )
}
```

`rememberSymbolPainter()` maps `wght=100/400/700` to
`0.5×/1×/1.5×` the authored stroke width and clamps values outside that range.
Settings without `wght` preserve the source. The generated Android and Compose
XML drawables stay static at the authored 1× width, which keeps legacy Views
predictable. For state-driven animation,
`rememberSymbolPainter { settingsState.value }` reads the snapshot state in the
internal vector child composition instead of the caller. Stroke changes still
update and rasterize that vector subtree; the overload does not make drawing
static. Its producer is non-composable, so use the no-argument overload to read
the current `SymbolsTheme`. A future per-symbol override map can use each stable
`ImageVector.name` as its key; that map API is intentionally not implemented
until a concrete theming use case requires it.

The exact accepted path/style subset and XML parser security boundary are in
[the generator guide](GENERATOR.md#declare-a-flat-svg-icon-set). The focused
sample vendors three unchanged Tabler Icons `v3.46.0` SVGs under the MIT
license and exercises typed vectors, painters, Compose resources, and Android
Views/XML.

## Optional: build a font from SVG sources

Use the maintainer CLI only when a source-owned SVG set must become a regular
TTF and stable codepoint manifest:

```shell
python tools/generate_svg_icon_font.py \
  --input-dir icons/svg \
  --font icons/AppIcons.ttf \
  --manifest icons/AppIcons.codepoints \
  --family-name "App Icons" \
  --stroke-width 1.5
```

Names come from SVG filenames. Existing manifest assignments are preserved,
and deleting an assigned SVG is rejected so a released code point is not
reused accidentally. See the
[maintainer generation guide](../tools/README.md#svg-icon-font-generation) for
supported SVG features, deterministic `--check` mode, and installation. The
resulting font and manifest can be passed to the Gradle generator.
`--stroke-width` overrides stroked SVG shapes before their outlines are baked.
It produces one regular font, not an OpenType variation axis; prefer direct SVG
generation plus `rememberSymbolPainter()` when only vector/drawable output and
live stroke changes are needed.

## Allocate code points deliberately

Prefer the Unicode Private Use Areas for glyphs that have no standard Unicode
character. Maintain a checked-in manifest mapping a stable canonical name to
each scalar. Never recycle a released assignment for a different drawing:
persisted text, screenshots, tests, and downstream generated sources may depend
on it.

Supplementary Private Use Area assignments are supported, but verify that every
target's text stack and any surrounding application code preserve surrogate
pairs. Do not convert code points to `Char`.

Aliases are valid when several names intentionally address one glyph. Duplicate
names are not. Treat a collision between generated Kotlin identifiers as an
error rather than silently renaming one property.

## Generate a forked runtime catalog

`tools/generate_material_symbols.py` accepts explicit input, output, and package
arguments:

```shell
python3 tools/generate_material_symbols.py \
  --input my-symbols.codepoints \
  --output src/commonMain/kotlin/my/symbols/MySymbols.generated.kt \
  --package my.symbols
```

The generated file expects the catalog runtime types from `MaterialSymbol.kt` in
the same package. This mode is intended for a source fork or a custom module that
adapts that small runtime; it does not inject types into an arbitrary consuming
project. Run the generator's `--check` mode in CI. A consumer that only needs
typed vectors/drawables should normally use the Gradle plugin instead of forking
the built-in runtime catalog.

If a custom glyph is added to the bundled Google font snapshot, the result is a
derivative font. Record:

- the upstream revision and unmodified source hash;
- source artwork and author/license for the new glyph;
- the tool and reproducible command used to build the derivative;
- the new full-file and table hashes;
- axis behavior for every master; and
- a stable codepoint manifest and conformance tests.

Do not describe a derivative as an unmodified Google asset. Update
`THIRD_PARTY_NOTICES.md` and retain all required upstream notices.

## Test the edge cases

A custom catalog/font/SVG test suite should cover:

- first and last allocated code points;
- BMP and supplementary private-use values;
- invalid scalars and malformed manifest lines;
- duplicate names, aliases, and Kotlin identifier collisions;
- glyph coverage in every visual style;
- nonempty outlines at every required master;
- axis boundaries and defaults;
- RTL behavior for directional glyphs; and
- accessible descriptions that never expose raw private-use text.

For generated outputs, also test regular, variable, and SVG inputs,
deterministic warm builds, stale-output cleanup, rejected SVG features, and the
release-artifact reachability of only the referenced icons.
