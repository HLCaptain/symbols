# Custom fonts and code points

Symbols supports a custom variable font without making custom glyph processing
part of an application build. Prepare and license the font separately, package it
as a Compose Multiplatform resource, and use the low-level codepoint API.

## Package a font

Place one font in the custom module:

```text
src/commonMain/composeResources/font/my_symbols_variable.ttf
```

Expose it through `MaterialSymbolFont`:

```kotlin
import io.github.hlcaptain.symbols.material.MaterialSymbolFont
import my.symbols.generated.resources.Res
import my.symbols.generated.resources.my_symbols_variable

object MySymbols : MaterialSymbolFont {
    override val familyName = "My Symbols"
    override val resource = Res.font.my_symbols_variable
}
```

The font must use the same `FILL`, `wght`, `GRAD`, and `opsz` contracts if it is
rendered with `MaterialSymbolAxes`. A static font or a variable font with
different axes needs its own renderer instead of pretending to implement this
contract.

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

## Generate a forked catalog

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
project. Run the generator's `--check` mode in CI.

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
