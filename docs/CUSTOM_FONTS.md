# Custom fonts and code points

Symbols supports custom regular and variable fonts as runtime Compose resources,
or as build-time inputs for generated `ImageVector`/drawable output. Prepare and
license the font separately, then choose whether the application should ship the
font itself or only selected extracted outlines.

## Package a variable font

Place one font in the custom module:

```text
src/commonMain/composeResources/font/my_symbols_variable.ttf
```

Expose it through the explicit variable-font contract:

```kotlin
import io.github.hlcaptain.symbols.material.MaterialSymbolVariableFont
import my.symbols.generated.resources.Res
import my.symbols.generated.resources.my_symbols_variable

object MySymbols : MaterialSymbolVariableFont {
    override val familyName = "My Symbols"
    override val resource = Res.font.my_symbols_variable
}
```

The font must use the same `FILL`, `wght`, `GRAD`, and `opsz` contracts if it is
rendered with `MaterialSymbolAxes`. A variable font with different tags or
ranges needs its own renderer instead of pretending to implement this contract.
The older `MaterialSymbolFont` interface remains source compatible, but is
treated as variable; new implementations should state their capability
explicitly.

Render a private-use or supplementary scalar directly:

```kotlin
MaterialSymbolIcon(
    codePoint = 0xF0001,
    font = MySymbols,
    contentDescription = "Custom action",
)
```

`materialSymbolText(codePoint)` is available for a custom `BasicText` layout.
Both APIs reject negative values, surrogate code points, and values above
`U+10FFFF`.

## Package a regular font

Use `MaterialSymbolRegularFont` for a font baked at one immutable point:

```kotlin
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.MaterialSymbolRegularFont
import my.symbols.generated.resources.Res
import my.symbols.generated.resources.my_symbols_regular

object MyRegularSymbols : MaterialSymbolRegularFont {
    override val familyName = "My Symbols"
    override val resource = Res.font.my_symbols_regular
    override val axes = MaterialSymbolAxes.Default
}
```

The renderer never attaches `FontVariation.Settings` to this resource, so it can
render on Android API 21. It verifies that the requested axes equal the declared
fixed point. In a non-default `MaterialSymbolsTheme`, pass
`axes = MyRegularSymbols.axes` explicitly or provide a style-specific overload
that does so.

## Generate outlines instead of shipping a font

The `io.github.hlcaptain.symbol-fonts` Gradle plugin accepts a custom regular
font or a fixed instance of a custom variable font. It validates the manifest
and glyph coverage and can emit:

- direct common Compose `ImageVector` properties;
- native Android vector drawables; and
- Compose Multiplatform drawable resources.

Use explicit `include(...)` entries so unneeded glyphs are never generated. The
input font remains a build input and is not packaged unless another dependency
adds it as a resource. See [build-time font conversion](GENERATOR.md) for the
complete DSL, generated API, and shrinker boundaries.

Google's two-column `.codepoints` file is not universal. CSS pseudo-element
maps, Font Awesome metadata, IcoMoon/Fontello JSON, and the font's OpenType
`cmap` all describe related pieces of the same mapping but use different name
and number conventions. Normalize the chosen source into the plugin's stable
snake-case/hex manifest rather than making application builds depend on a
provider-specific parser. The checked-in [external font samples](../fonts/samples/README.md)
show three such conversions.

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
