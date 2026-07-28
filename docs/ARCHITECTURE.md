# Architecture

Symbols separates catalog identity, Compose rendering, binary font assets, and
static vectors so consumers pay only for the access modes and visual styles they
select.

```text
MaterialSymbols.codepoints ──generator──> material-core
                                           │
                                           ├──> material-compose
variable font (one style) ──resource───────┴──> material-{style}
        │
        └──default-axis extraction────────────> material-vectors-{style}
```

The generated sources are repository outputs. Consumer builds do not parse a
codepoint manifest, inspect a TTF, run Python, or execute the generators.

## Catalog identity

`MaterialSymbol` is an inline index handle into the built-in catalog. Generated
properties such as `MaterialSymbols.Home` return a constant index, so bare named
access does not allocate an object or initialize the name/codepoint lookup data.
The data holder initializes when an application asks for `name`, `codePoint`,
`text`, `all`, `fromName`, or `aliases`.

Names are sorted once at generation time. `fromName` uses binary search rather
than a runtime hash map. A second pre-sorted integer index supports codepoint
alias lookup. Returned `all` and alias lists are stable lazy views.

Identity is name-based, not codepoint-based. `grade` and `star`, for example,
share a code point but remain distinct `MaterialSymbol` values with different
names. Static vectors are shape/codepoint-based, so aliases intentionally share
the cached `ImageVector`.

## Variable-font rendering

Each style module contains exactly one TTF under Compose Multiplatform resources.
`MaterialSymbolAxes` validates the four font axes, and
`rememberMaterialSymbolFontFamily` constructs the Compose `Font` with those
variation settings.

`MaterialSymbolIcon` renders one Unicode scalar through `BasicText`. Its layout
box has an explicit square size, the private-use text is cleared from semantics,
and an optional localized description is exposed with image semantics. Optional
RTL mirroring transforms the glyph inside the box.

The convenient overload that accepts `MaterialSymbolFont` remembers a family per
call site. Large collections should remember one family for a `(font, axes)` pair
and pass that shared family to the lower-level overload.

Android variable-font settings require API 26. The same Compose resource API is
used on iOS, JVM, JavaScript, and Wasm.

## Static vectors

The vector generator instantiates every variable font at the documented default
axes, converts each unique codepoint outline into a 24×24 SVG-style path, and
writes deterministic Kotlin chunks. Runtime code parses and caches only vectors
that are requested.

Every vector has a 24 dp default size and a 24×24 viewport. Paths preserve
intentional overshoot outside the viewport. A separate cached vector is used for
`autoMirror = true`; source coordinates are not rewritten.

Static vectors cannot represent variable axes. Each vector module is therefore
an explicit optional alternative, not a transitive dependency of a font style.

## Resource and publication boundaries

All public modules publish Android, JVM, JS, Wasm, iOS x64, iOS arm64, and iOS
simulator arm64 variants. `material-core` has no Compose dependency. Style
modules expose their `FontResource` and depend on the small Compose adapter.
Vector modules depend on `material-core` and Compose UI but not on a font.

Every publication packages the project license and third-party notice under
`META-INF`. Font artifacts retain only their own style TTF, so selecting Outlined
does not silently bundle Rounded or Sharp.

## Determinism and trust boundaries

The canonical inputs are:

- `fonts/material/MaterialSymbols.codepoints`;
- the three versioned variable fonts; and
- pinned invariants and checksums in `tools/verify_material_fonts.py`.

The catalog generator uses only the Python standard library. Font inspection and
vector extraction use a pinned FontTools release in an isolated maintainer/CI
environment. The verifier checks whole-file and font-table hashes, axes,
coverage, alias facts, outlines, manifest syntax, and one-font-per-style layout.

See [custom fonts](CUSTOM_FONTS.md) for derivative assets and
[performance](PERFORMANCE.md) for the cost model.
