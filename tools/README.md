# Catalog generation

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
two names that would produce the same simple PascalCase Kotlin property.

An alternate package expects the small catalog runtime from
`MaterialSymbol.kt` to be present in that package. It is intended for a source
fork/custom catalog module, not as code injection into an arbitrary consumer.

## Vector generation

`generate_material_vectors.py` instantiates each bundled variable font at
`FILL=0, GRAD=0, opsz=24, wght=400` and generates the three optional
`ImageVector` packs. It reads every unique manifest code point, preserves
aliases through the shared catalog identity, and writes path data in stable
chunks.

Install the pinned maintainer dependency in an isolated environment:

```shell
python3 -m venv /tmp/symbols-fonttools
/tmp/symbols-fonttools/bin/python -m pip install \
  -r tools/requirements-font-verification.txt
```

Regenerate every style or verify that checked-in output is current:

```shell
/tmp/symbols-fonttools/bin/python tools/generate_material_vectors.py
/tmp/symbols-fonttools/bin/python tools/generate_material_vectors.py --check
```

The generated APIs use 24×24 viewports, retain up to four decimal places, and
preserve intentional outline overshoot. Code-point lookup is portable common
Kotlin. Vector caches are split into lazy 128-entry chunks so first use does not
allocate a cache wrapper for all 3,802 shapes.

Do not hand-edit generated vector files. An axis, font, manifest, rounding, or
chunk-layout change must update the generator and tests in the same change.

## Font conformance

`verify_material_fonts.py` validates that the fonts, manifest, version, axes,
coverage, hashes, tables, aliases, and representative outlines are the pinned
snapshot expected by this repository:

```shell
/tmp/symbols-fonttools/bin/python tools/verify_material_fonts.py
```

See [FONT_VERIFICATION.md](FONT_VERIFICATION.md) for the complete trust boundary
and intentional-update procedure.
