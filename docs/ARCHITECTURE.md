# Architecture

Symbols separates catalog identity, Compose rendering, binary font assets, and
generated vectors/resources so consumers pay only for the access modes and
visual styles they select.

```text
MaterialSymbols.codepoints ──catalog generator──> material-core
              │                                      │
              └──typed namespace generator──────────> material-compose
                                                        │
variable font (one style) ──runtime resource────────────┴──> material-{style}
        │
        ├──default-axis instancing────────────────────────> material-{style}-static
        ├──default-axis outline extraction────────────────> material-vectors-{style}
        │                                                    │
        │                                                    └──> material-vectors-themed
        ├──default-axis Android XML generation─────────────> material-drawables-{style}
        └──Gradle generator + app manifest────────────────> selected ImageVectors /
                                                            Android drawables /
                                                            Compose drawables
```

The built-in catalog, typed namespaces, regular fonts, and vector packs are
checked-in repository outputs. Consumers of those artifacts do not parse a
codepoint manifest, inspect a TTF, run Python, or execute a generator. The
drawable AARs are generated from checked-in static fonts when the library is
built; their consumers receive ordinary Android resources. The
separate Gradle plugin is an opt-in application-build path for custom fonts or
smaller selected icon sets; it inspects the declared font in an isolated JVM and
does not require Python or FontTools.

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

Each variable style module contains exactly one TTF under Compose Multiplatform
resources. `MaterialSymbolAxes` validates the four font axes, and
`rememberMaterialSymbolFontFamily` constructs the Compose `Font` with those
variation settings.

`MaterialSymbolVariableFont` states that a resource supports live axes.
`MaterialSymbolRegularFont` states that a resource is baked at one immutable
axis point. The older `MaterialSymbolFont` interface remains source compatible
and is treated as variable unless an implementation opts into the explicit
regular contract. A regular font is loaded without
`FontVariation.Settings`; requesting axes other than its declared point fails.

`MaterialSymbolIcon` renders one Unicode scalar through `BasicText`. Its layout
box has an explicit square size, the private-use text is cleared from semantics,
and an optional localized description is exposed with image semantics. Optional
RTL mirroring transforms the glyph inside the box.

`Symbols.Outlined`, `Symbols.Rounded`, and `Symbols.Sharp` provide
allocation-free style-typed handles. Each style artifact contributes a matching
`MaterialSymbolIcon` overload, so `Symbols.Rounded.Home` cannot accidentally
select the Sharp font. The convenient overload remembers a family per call site.
Large collections should remember one family for a `(font, axes)` pair and pass
that shared family to the lower-level overload.

`MaterialSymbolsTheme` supplies axes through `LocalMaterialSymbolAxes`; explicit
renderer axes take precedence. Android variable-font settings require API 26.
`MaterialSymbolsRuntime.variableFontsSupported` exposes that boundary so API
21–25 applications can choose a regular-font or vector/drawable fallback before
rendering. Variable style AARs themselves have minSdk 21 so both paths can be
present in one application. The capability is true on supported non-Android
targets.

## Regular fonts

The three `material-{style}-static` modules each carry one font instantiated at
`FILL=0, GRAD=0, opsz=24, wght=400`. They depend on the API-21-compatible
renderer and use the same style-typed symbol namespace as the variable modules.
They contain no variable tables and cannot animate or override axes.

These TTFs are deterministic, checked-in derivatives of the pinned Google
variable fonts. They are generated only in an explicit maintainer workflow with
FontTools 4.60.2; normal Gradle builds package the already generated result.
Their modification status, hashes, and exact commands are recorded in
[`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).

## Static vectors

The built-in vector generator instantiates every variable font at the documented
default axes, converts each unique codepoint outline into direct 24×24 Compose
path operations, and writes deterministic Kotlin chunks. No path string is
parsed at runtime.

Every vector has a 24 dp default size and a 24×24 viewport. Paths preserve
intentional overshoot outside the viewport. A separate cached vector is used for
`autoMirror = true`; source coordinates are not rewritten.

Direct properties such as `Icons.Outlined.Home` reference an independent
per-codepoint builder and cache. They do not reach the pack-wide codepoint index
or dispatcher, preserving code-shrinker reachability. The legacy dynamic bridge
from `MaterialSymbol` intentionally uses that index and dispatcher and can retain
more of the pack. Aliases share their per-codepoint backing object and cache.

Static vectors cannot represent variable axes. Each vector module is therefore
an explicit optional alternative, not a transitive dependency of a font style.

## Build-time generated vectors and drawables

The `io.github.hlcaptain.symbol-fonts` plugin consumes a semantic manifest and a
regular font or fixed variable-font instance. Explicit `include(...)` selection
is the default; `includeAll()` is an opt-in. The shared generator model then
emits direct Compose `ImageVector` builders, one native Android vector XML per
codepoint, one Compose drawable XML per codepoint, or any combination.

Generated Kotlin has style-typed namespaces and per-codepoint nullable caches,
with no registry, dispatcher, reflection hook, or all-icons collection. Android
resources are registered with the Android Components variant API; Compose XML
is registered as a generated `commonMain` custom resource directory. Tasks
declare their font, manifest, axes, selection, options, generator classpath, and
outputs and are cacheable.

See [build-time font conversion](GENERATOR.md) for the DSL and resource names.

## Resource and publication boundaries

Kotlin Multiplatform runtime modules publish Android, JVM, JS, Wasm, iOS x64,
iOS arm64, and iOS simulator arm64 variants. The native drawable packs are
Android-only AARs. `material-core` has no Compose dependency.
Variable and regular style modules expose their `FontResource` and depend on the
small Compose adapter. Fixed vector modules depend on `material-core` and
Compose UI but not on a font; the themed vector module adds composition-local
style selection over all three packs. Drawable AARs contain only generated
Android XML resources. Build-time tooling is a JVM/Gradle concern and does not
become a runtime dependency.

Every runtime and JVM tooling archive packages the project license and
third-party notice at
`META-INF/<project.name>/{LICENSE,THIRD_PARTY_NOTICES.md}`. The project-name
namespace lets several Symbols dependencies coexist in one Android package
without duplicate-entry collisions. Font artifacts retain only their own style
and variable/regular TTF, so selecting Outlined does not silently bundle Rounded
or Sharp, and selecting a variable font does not silently add its regular
instance.

## Determinism and trust boundaries

The canonical inputs are:

- `fonts/material/MaterialSymbols.codepoints`;
- the three versioned variable fonts; and
- pinned invariants and checksums in `tools/verify_material_fonts.py`.

The catalog and typed namespace generators use only the Python standard library.
Static font instancing, built-in vector extraction, and font inspection use a
pinned FontTools release in an isolated maintainer/CI environment. The verifier
checks the unmodified upstream inputs; separate generator `--check` modes
byte-compare the derived regular fonts and source outputs.

The opt-in Gradle generator uses an engine-neutral outline model with Skiko as
its font reader. It validates manifests, selection, axes, glyph coverage, and
output paths before synchronizing only files it owns.

See [custom fonts](CUSTOM_FONTS.md) for derivative assets and
[performance](PERFORMANCE.md) for the cost model and shrinker evidence boundary.
