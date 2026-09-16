# Maintainer generation

The checked-in Material Symbols artifacts have three generation stages:

1. the upstream codepoint manifest produces the catalog;
2. the upstream variable fonts produce default-axis regular fonts; and
3. the manifest plus variable fonts produce built-in typed vector packs.

Normal consumers of the published runtime artifacts do not execute these
scripts. The separate Gradle font converter for application-owned icons is
described under [Build-time font conversion](#build-time-font-conversion).

## Catalog generation

`generate_material_symbols.py` converts the canonical Material Symbols
codepoints map into the allocation-light Kotlin catalog used by
`:modules:material-core`. It uses only the Python standard library.

From the repository root:

```shell
python3 tools/generate_material_symbols.py
python3 tools/generate_material_symbols.py --check
```

The defaults are:

- input: `fonts/material/MaterialSymbols.codepoints`
- output:
  `symbols/material-core/src/commonMain/kotlin/io/github/hlcaptain/symbols/material/MaterialSymbols.generated.kt`
- package: `io.github.hlcaptain.symbols.material`

All three can be overridden explicitly:

```shell
python3 tools/generate_material_symbols.py \
  --input path/to/symbols.codepoints \
  --output path/to/MaterialSymbols.generated.kt \
  --package example.symbols
```

The generated order is deterministic by canonical symbol name. Generation
rejects malformed names, duplicate names, non-scalar Unicode values, and any
two names that would produce the same Kotlin identifier.

An alternate package expects the small catalog runtime from
`MaterialSymbol.kt` to be present in that package. It is intended for a source
fork/custom catalog module, not as code injection into an arbitrary consumer.

## Static font generation

`generate_material_static_fonts.py` instantiates each pinned variable font at
`FILL=0, GRAD=0, opsz=24, wght=400`. It writes the API-21-compatible regular
fonts used by the three `material-{style}-static` modules. The files are
modified derivatives, and their hashes and generation method are recorded in
[`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).

Install the pinned maintainer dependency in an isolated environment:

```shell
python3 -m venv /tmp/symbols-fonttools
/tmp/symbols-fonttools/bin/python -m pip install \
  -r tools/requirements-font-verification.txt
```

Regenerate every style or byte-compare the checked-in results:

```shell
/tmp/symbols-fonttools/bin/python tools/generate_material_static_fonts.py
/tmp/symbols-fonttools/bin/python tools/generate_material_static_fonts.py --check
```

The script requires FontTools 4.60.2 exactly, removes variable tables, disables
timestamp recalculation, and writes a stable table order.

The same tool can freeze one custom variable font before runtime. Every omitted
axis uses that font's own default; `--family-name` is useful when a derivative
must not retain an upstream Reserved Font Name:

```shell
/tmp/symbols-fonttools/bin/python tools/generate_material_static_fonts.py \
  --input samples/custom-variable/src/commonMain/composeResources/font/academmunicons_variable.ttf \
  --output fonts/samples/academmunicons/academmunicons-regular.ttf \
  --axis ital=0 --axis wght=400 \
  --family-name "Symbols Academic Icons"
```

Pass the same arguments with `--check` to byte-compare a checked-in result.

## SVG icon font generation

`generate_svg_icon_font.py` builds a regular TrueType icon font and the same
two-column manifest consumed by the build-time vector/drawable generator. Each
SVG filename becomes a snake-case name, and new names receive stable BMP
Private Use Area assignments:

```shell
/tmp/symbols-fonttools/bin/python tools/generate_svg_icon_font.py \
  --input-dir path/to/icons \
  --font build/AppIcons.ttf \
  --manifest path/to/AppIcons.codepoints \
  --family-name "App Icons" \
  --stroke-width 1.5
```

Keep the generated manifest under version control. Later runs preserve every
assignment, allocate only new names, and fail if an SVG disappears instead of
silently recycling its code point. `--check` byte-compares both outputs for CI.

The input directory is intentionally flat. Basic shapes, paths, groups,
transforms, `<use>`, clip paths, and strokes such as Tabler's are flattened to
monochrome outlines through PicoSVG. Text, images, masks, filters, gradients,
patterns, multiple paint colors, and opacity fail because a monochrome font
cannot preserve them. Every glyph is fitted without distortion into a
1000-unit square and emitted as an unhinted regular TTF.

`--stroke-width` replaces inherited, explicit, inline-style, and `<use>` stroke
widths before conversion. It bakes one regular font and does not invent an
OpenType variation axis; omit it to preserve each SVG's authored width.

This command rebuilds a source-owned icon font; it does not patch an arbitrary
third-party, CFF, or variable font binary. The source icons' licenses continue
to govern the generated font. A future Figma exporter or IntelliJ UI can use
this manifest/font contract without owning a second compiler.

## Vector generation

`generate_material_vectors.py` instantiates each bundled variable font at
`FILL=0, GRAD=0, opsz=24, wght=400` and generates the three optional
`ImageVector` packs. It reads every unique manifest code point, preserves aliases
through shared per-codepoint builders/caches, and writes direct Compose path
operations in stable chunks. It also writes the composable
`Symbols.Material.Themed.*`
getters that select direct Outlined, Rounded, or Sharp properties from the
theme's style composition local.

The four library modules invoke this emitter through cacheable Gradle
`generateMaterialVectors` tasks. Kotlin is written under each module's
`build/generated/materialVectors/commonMain/kotlin`, included in source archives,
and prepared during IDE import. Configure the interpreter as described in
[Contributing](../CONTRIBUTING.md#development-environment).

Generate every style, then verify the generated output is current:

```shell
/tmp/symbols-fonttools/bin/python tools/generate_material_vectors.py
/tmp/symbols-fonttools/bin/python tools/generate_material_vectors.py --check
```

Use `--style outlined`, `rounded`, `sharp`, or `themed` to generate only that
module (repeat `--style` to select several). `--output /path/to/kotlin` redirects
one selected style's source root; Gradle uses it to honor `layout.buildDirectory`.
Themed generation uses the standard library and does not inspect fonts. Stale
files bearing the generator's header are removed only from selected output packages.

The generated APIs use 24×24 viewports, retain up to four decimal places, and
preserve intentional outline overshoot. Typed access uses the shared
`Icons.{Style}.{Name}` namespace and directly reaches an independent codepoint
builder. The compatibility `MaterialSymbol` lookup keeps a portable common
Kotlin index/dispatcher for dynamic selection.

Do not hand-edit generated vector files. An axis, font, manifest, rounding, or
chunk-layout change must update the generator and tests in the same change.

## Build-time font conversion

The Kotlin tooling build contains:

- `symbol-generator-core`, an engine-neutral outline model, Skiko font reader,
  deterministic Kotlin/XML renderers, stale-safe writer, and CLI; and
- `symbol-gradle-plugin`, the cacheable
  `io.github.hlcaptain.symbol-fonts` integration.

Unlike the repository-maintainer Python scripts, this path is intended for
application builds. It accepts regular or variable TTF/OTF/TTC input and emits
selected `ImageVector`, native Android drawable, and Compose drawable output
without packaging the input font. See
[`docs/GENERATOR.md`](../docs/GENERATOR.md) for setup and the DSL.

Run the tooling tests from the repository root:

```shell
./gradlew -p tooling :symbol-generator-core:test \
  :symbol-gradle-plugin:test
```

## Font conformance

`verify_material_fonts.py` validates that the fonts, manifest, version, axes,
coverage, hashes, tables, aliases, and representative outlines are the pinned
snapshot expected by this repository:

```shell
/tmp/symbols-fonttools/bin/python tools/verify_material_fonts.py
```

See [FONT_VERIFICATION.md](FONT_VERIFICATION.md) for the complete trust boundary
and intentional-update procedure.

## Python tests and CI checks

Run the complete maintainer test suite:

```shell
/tmp/symbols-fonttools/bin/python -m unittest discover -s tools/tests -p "test_*.py"
```

CI runs that suite, generates disposable vectors, and runs all three relevant
`--check` modes:

```shell
python3 tools/generate_material_symbols.py --check
/tmp/symbols-fonttools/bin/python tools/generate_material_static_fonts.py --check
/tmp/symbols-fonttools/bin/python tools/generate_material_vectors.py
/tmp/symbols-fonttools/bin/python tools/generate_material_vectors.py --check
```

It also runs `verify_material_fonts.py` against the pinned unmodified inputs.
