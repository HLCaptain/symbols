# Custom fonts and code points

Symbols supports custom regular and variable fonts as runtime Compose resources,
or as build-time inputs for generated `ImageVector`/drawable output. Prepare and
license the font separately, then choose whether the application should ship the
font itself or only selected extracted outlines.

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

Runtime fonts use the generated Compose Multiplatform `FontResource` shown
below. Android `R.font` resources are supported as generator inputs, but do not
implement this runtime contract.

Expose it through the explicit variable-font contract:

```kotlin
import io.github.hlcaptain.symbols.font.SymbolVariableFont
import my.symbols.generated.resources.Res
import my.symbols.generated.resources.my_symbols_variable

object MySymbols : SymbolVariableFont {
    override val familyName = "My Symbols"
    override val resource = Res.font.my_symbols_variable
}
```

`SymbolFontSettings` stores one Compose `FontVariation.Settings` value. Its
convenience constructor converts `FontWeight` and `FontStyle` to the standard
`wght` and `ital` axes; pass `FontVariation.Settings` directly for arbitrary
font axes. Supply either form directly or inherit it through the generic
`SymbolsTheme`:

```kotlin
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.font.SymbolsTheme

val standardSettings = SymbolFontSettings(
    weight = FontWeight.Medium,
    style = FontStyle.Italic,
)

val settings = SymbolFontSettings(
    variationSettings = FontVariation.Settings(
        FontVariation.Setting("FILL", 1f),
        FontVariation.Setting("wdth", 110f),
        FontVariation.weight(500),
    ),
)

SymbolsTheme(fontSettings = settings) {
    SymbolFontIcon(
        codePoint = 0xF0001,
        font = MySymbols,
        contentDescription = "Custom action",
    )
}
```

Android can apply variable-font settings from API 26. Check
`SymbolsRuntime.variableFontsSupported` before selecting this path on an app
whose minSdk is lower.

The sample keeps its custom-variable-font `@Preview` beside the shared UI in
`commonMain`. Its Gradle build backports the resource task dependency from
[CMP-7170](https://youtrack.jetbrains.com/issue/CMP-7170), so Android Studio
copies generated Compose resources into Android assets before preview packaging.
After adding or removing a font,
[build or re-import the project](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html)
once so `Res` and its accessors are regenerated; changing axis values needs no
rebuild. The sample Preview starts on an Academmunicons regular font frozen at
`ital=0,wght=400`; moving either control switches to its variable resource.

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
        font = MySymbols,
        contentDescription = "Custom action",
    )
}
```

Use the generic `SymbolsTheme` instead when the custom font has different tags
or ranges. `MaterialSymbolsTheme` also owns the Outlined, Rounded, and Sharp
selection used by built-in `Icons.Themed.*` properties.

## Package a regular font

Use `SymbolRegularFont` for a font baked at one immutable point:

```kotlin
import androidx.compose.ui.text.font.FontWeight
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.font.SymbolRegularFont
import my.symbols.generated.resources.Res
import my.symbols.generated.resources.my_symbols_regular

object MyRegularSymbols : SymbolRegularFont {
    override val familyName = "My Symbols"
    override val resource = Res.font.my_symbols_regular
    override val fontSettings = SymbolFontSettings(weight = FontWeight.Bold)
}

SymbolFontIcon(
    codePoint = 0xF0001,
    font = MyRegularSymbols,
    fontSettings = MyRegularSymbols.fontSettings,
    contentDescription = "Custom action",
)
```

The renderer never attaches `FontVariation.Settings` to this resource, so it can
render on Android API 21. It verifies that the requested settings equal the
declared fixed `fontSettings`; pass that value explicitly or provide it through
`SymbolsTheme`.

`tools/generate_material_static_fonts.py --input ... --output ... --axis
TAG=VALUE` can freeze any variable font at build time. Unspecified axes use the
font's declared defaults, and the output contains no variation tables. A UI can
select the regular resource at that exact settings value and switch to its
`SymbolVariableFont` only after an axis changes, as the sample does.

## Generate outlines instead of shipping a font

The `io.github.hlcaptain.symbol-fonts` Gradle plugin accepts a custom regular
font or a fixed instance of a custom variable font. It validates the manifest
and glyph coverage and can emit:

- direct common Compose `ImageVector` properties;
- native Android vector drawables; and
- Compose Multiplatform drawable resources.

Every codepoint is generated by default. Use `include(...)` when unneeded glyphs
should never be generated. A font selected from `src/main/res/font` or
`src/commonMain/composeResources/font` is also a runtime resource; use an
explicit file outside those directories when it should remain build-only. See
[build-time font conversion](GENERATOR.md) for the complete DSL, generated API,
and shrinker boundaries.

All generated `ImageVector`, Android XML, and Compose XML outputs are fixed
snapshots at the Gradle style's configured `axis(...)` coordinates. They do not
read `SymbolsTheme` or `MaterialSymbolsTheme` at runtime and do not require the
variant-font core unless the application separately renders the font itself.

Google's two-column `.codepoints` file is not universal. CSS pseudo-element
maps, Font Awesome metadata, IcoMoon/Fontello JSON, and the font's OpenType
`cmap` all describe related pieces of the same mapping but use different name
and number conventions. Normalize the chosen source into the plugin's stable
snake-case/hex manifest rather than making application builds depend on a
provider-specific parser. The checked-in
[external font samples](../fonts/samples/README.md) show five such conversions.

## Build a font from SVG sources

For a source-owned SVG set, the maintainer CLI can create both the regular TTF
and its stable manifest:

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
resulting font and manifest can be passed directly to the Gradle generator
described above. `--stroke-width` overrides stroked SVG shapes before their
outlines are baked. It produces one regular font, not an OpenType variation
axis; live stroke changes require an authored variable font or SVG/vector
rendering at runtime.

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

A custom catalog/font test suite should cover:

- first and last allocated code points;
- BMP and supplementary private-use values;
- invalid scalars and malformed manifest lines;
- duplicate names, aliases, and Kotlin identifier collisions;
- glyph coverage in every visual style;
- nonempty outlines at every required master;
- axis boundaries and defaults;
- RTL behavior for directional glyphs; and
- accessible descriptions that never expose raw private-use text.

For generated outputs, also test regular and variable inputs, selected versus
missing names, deterministic warm builds, stale-output cleanup, and the
release-artifact reachability of only the referenced icons.
