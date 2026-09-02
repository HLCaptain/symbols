# Architecture

Symbols separates catalog identity, Compose rendering, binary font assets, and
generated vectors/resources so consumers pay only for the access modes and
visual styles they select.

```text
symbols-core ──common Symbols root────────────────────────┐
                                                          │
variant-font-core ──generic font settings/theme/renderer──┤
                                                          │
MaterialSymbols.codepoints ──catalog generator──> material-core
                                                     │
                                                     ├──> Symbols.Material
                                                     └──> material-compose
                                                            │
variable font (one style) ──runtime resource───────────────┴──> material-{style}
        │
        ├──default-axis instancing────────────────────────> material-{style}-static
        │                                                    │
        │                                                    ├──Android XML generation
        │                                                    │  └──> material-drawables-{style}
        │                                                    └──Compose XML generation
        │                                                       └──> material-compose-drawables-{style}
        ├──default-axis outline extraction────────────────> material-vectors-{style}
        │                                                    │
        │                                                    └──> material-vectors-themed
        └──Gradle generator + app manifest────────────────> Symbols.<CustomSet> /
                                                            selected ImageVectors /
                                                            Android drawables /
                                                            Compose drawables
```

The built-in catalog, regular fonts, and vector packs are
checked-in repository outputs. Consumers of those artifacts do not parse a
codepoint manifest, inspect a TTF, run Python, or execute a generator. The
drawable AARs and Compose drawable packs are generated from checked-in static
fonts when the libraries are built; their consumers receive ordinary Android or
Compose Multiplatform resources, respectively. The
separate Gradle plugin is an opt-in application-build path for custom fonts or
smaller selected icon sets; it inspects the declared font in an isolated JVM and
does not require Python or FontTools.

## Catalog identity

`MaterialSymbol` is an inline index handle into the built-in catalog. Generated
properties such as `Symbols.Material.Home` return a constant index, so bare named
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

`symbols-variant-font-core` is independent of the Material catalog. Its
`SymbolFontSettings` stores one Compose `FontVariation.Settings` value, with a
convenience constructor for `FontWeight` and `FontStyle`. `SymbolsTheme`
supplies it through `LocalSymbolFontSettings`, and `rememberSymbolFontFamily`
constructs the Compose `Font`.

`SymbolFont` is the sealed root of the runtime-font hierarchy. Its
`SymbolFont.Variable` and `SymbolFont.Regular` children remain open interfaces so
applications can provide descriptors from other modules. Code that implemented
`SymbolFont` directly must migrate to exactly one child; implementing both
capability interfaces remains invalid.

`SymbolFont.Variable` states that a resource supports live coordinates and owns
optional `variationAxes` metadata for UI discovery. `SymbolFont.Regular` states
that a resource is baked at one immutable `fontSettings` point. A regular font
is loaded without `FontVariation.Settings`; requesting settings other than its
declared point fails.

`SymbolFontIcon` renders one Unicode scalar through `BasicText`. Its layout box
has an explicit square size, the private-use text is cleared from semantics,
and an optional localized description is exposed with image semantics. Optional
RTL mirroring transforms the glyph inside the box.

The Material adapter remains deliberately narrower. `MaterialSymbolAxes`
validates the bundled fonts' `FILL`, `wght`, `GRAD`, and `opsz` ranges and maps
them to generic `fontSettings`. `MaterialSymbolsTheme` owns Material axes and
Outlined/Rounded/Sharp style selection while also providing the mapped generic
settings to font rendering. Custom fonts with other coordinates use the generic
theme directly.

Runtime rendering uses `Symbols.Material.<Name>` with the generic
`SymbolFontIcon`; fixed vectors use `Symbols.Material.<Style>.<Name>` or
`MaterialSymbol.asThemedImageVector()`. Large
collections should remember one family for a `(font, fontSettings)` pair and
pass that shared family to `SymbolFontIcon`.

Android variable-font settings require API 26.
`SymbolsRuntime.variableFontsSupported` exposes that boundary so API
21–25 applications can choose a regular-font or vector/drawable fallback before
rendering. Variable style AARs themselves have minSdk 21 so both paths can be
present in one application. The capability is true on supported non-Android
targets.

## Regular fonts

The three `material-{style}-static` modules each carry one font instantiated at
`FILL=0, GRAD=0, opsz=24, wght=400`. They depend on the API-21-compatible
renderer. Callers select the catalog entry through `Symbols.Material` and pass
the chosen regular or variable font separately. Regular fonts contain no
variable tables and cannot animate or override axes.

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

Direct properties such as `Symbols.Material.Outlined.Home` reference an independent
per-codepoint builder and cache. They do not reach the pack-wide codepoint index
or dispatcher, preserving code-shrinker reachability. The legacy dynamic bridge
from `MaterialSymbol` intentionally uses that index and dispatcher and can retain
more of the pack. Aliases share their per-codepoint backing object and cache.

Static vectors cannot represent variable axes. Each vector module is therefore
an explicit optional alternative, not a transitive dependency of a font style.
Neither `SymbolsTheme` nor `MaterialSymbolsTheme` changes a built-in vector;
theme-selected vectors switch only among fixed Outlined, Rounded, and Sharp
snapshots.

## Build-time generated vectors and drawables

The `io.github.hlcaptain.symbol-fonts` plugin consumes either a codepoint map and
font or one flat directory of monochrome SVG files per style. Every manifest
entry or direct SVG file is generated implicitly. The shared generator model
then emits direct Compose `ImageVector` builders, native Android vector XML,
Compose drawable XML, or any combination.

Generated Kotlin contributes `Symbols.<IconSet>.<Style>.<Name>` branches and
per-codepoint nullable caches, with no registry, dispatcher, reflection hook,
or all-icons collection. Android
resources are registered with the Android Components variant API; Compose XML
is registered as a generated `commonMain` custom resource directory. Tasks
declare their source files, axes where applicable, rendering options, generator
classpath, and outputs and are cacheable.

Each Gradle style's `axis(...)` declarations are build-time coordinates. All
generated geometry and XML outputs are fixed snapshots at those coordinates.
Compose callers can render stroked `ImageVector` paths through
`rememberSymbolPainter()` so the existing `SymbolsTheme` `wght` setting scales
their authored stroke width; this does not regenerate or reshape path geometry.
Legacy XML remains fixed at the authored width.

For runtime Compose fonts, the plugin separately scans configured resource
roots and emits `Res.symbolFonts` descriptors. It reads variable-axis metadata
at build time, so common code can build controls and validated settings without
a platform font parser or handwritten per-font axis declarations. A separate
manifest-only task can emit runtime `SymbolCatalogEntry` lists without loading
fonts or coupling full catalogs to vector generation.

See [build-time font conversion](GENERATOR.md) for the DSL and resource names.

## Resource and publication boundaries

Kotlin Multiplatform runtime modules publish Android, JVM, JS, Wasm, iOS arm64,
and iOS simulator arm64 variants. Compose Multiplatform 1.11 no longer
publishes Apple x86_64 artifacts. The native drawable packs are Android-only
AARs; the Compose drawable packs publish public `Res.drawable` accessors and
resource variants for every supported target. `symbols-core` contains only the
common `Symbols` namespace.
`material-core` contains the catalog and has no Compose dependency.
`symbols-variant-font-core` contains the generic font contracts, settings theme,
renderer, and platform capability check; it has no Material catalog or bundled
font. `material-compose` adds the Material style/axes theme. Variable and regular
style modules keep Compose's generated `Res` class internal and expose font resources
only through the public `MaterialSymbols*` adapters. Fixed vector modules
depend on `material-core` and Compose UI but not on a font; the themed vector
module adds Material composition-local style selection over all three packs.
Drawable AARs contain only generated Android XML resources. Compose drawable
packs contain the 3,802 unique-codepoint resources for one complete fixed style
and no font; aliases sharing a code point share a canonical resource. Their
resource filenames and public accessors are published API. Build-time tooling is
a JVM/Gradle concern and does not become a runtime dependency.

Every runtime and JVM tooling archive packages the project license and
third-party notice at
`META-INF/<project.name>/{LICENSE,THIRD_PARTY_NOTICES.md}`. The project-name
namespace lets several Symbols dependencies coexist in one Android package
without duplicate-entry collisions. Font artifacts retain only their own style
and variable/regular TTF, so selecting Outlined does not silently bundle Rounded
or Sharp, and selecting a variable font does not silently add its regular
instance.

## Build conventions

The `build-logic` included build owns configuration that is identical across
library modules. Convention plugins are exposed through the main version
catalog, so module build files compose aliases and keep module-specific settings
such as namespaces, public dependency surfaces, resource packages, and
generator inputs.

| Convention | Shared responsibility |
| --- | --- |
| `libs.plugins.symbolsKotlinMultiplatformLibrary` | Android/JVM/JS/Wasm/iOS library targets, JDK 17, JVM 11 bytecode, Android SDK levels, hierarchy, and `kotlin-test` |
| `libs.plugins.symbolsComposeMultiplatformLibrary` | The base multiplatform convention plus Compose Multiplatform and its compiler plugin |
| `libs.plugins.symbolsKmpPublishing` | Maven publication and the Android release variant for a multiplatform library |
| `libs.plugins.symbolsMaterialFontLibrary` | Published Compose convention plus the Material Compose API and Compose resources used by all six font artifacts |
| `libs.plugins.symbolsMaterialVectorLibrary` | Published base convention plus explicit API, Material catalog, and Compose UI used by the three fixed vector packs |
| `libs.plugins.symbolsPublishedAndroidLibrary` | Android library defaults and a release sources/publication pair for the three drawable packs |
| `libs.plugins.symbolsSampleFeature` | Compose convention, Android minSdk 23, Koin compiler/dependencies, sample UI/API dependencies, and `SampleBuildConfig.MODULE_PATH` |

Application-only behavior remains local: `composeApp` owns executable web
targets and iOS frameworks, the shrink benchmark owns its build types, and the
separate `tooling` included build owns its JVM/plugin setup. Semantic namespaces,
generator DSL inputs, Compose resource packages, and module-specific API
dependencies likewise stay visible in the consuming module rather than being
derived from Gradle paths. The root build script retains repository-wide
coordinates, POM/signing/legal-archive policy, repositories, and publication
archive verification.

## Sample modules

`composeApp` is only the multiplatform launcher. Each common feature module
owns the dependency and input needed for its example and contributes one
qualified `SampleItem` from an annotated Koin `@Module @Configuration`;
feature modules have no runtime dependencies on each other:

```text
composeApp
├── samples/api
├── samples/ui/components
├── samples/material-static
├── samples/material-variable
├── samples/custom-static
├── samples/custom-variable
├── samples/image-vector-migration
├── samples/theming
├── samples/runtime-axes
└── samples/android-views
```

The Koin compiler discovers those feature modules for the launcher's typed
`@KoinApplication`. The API module defines the sealed `SampleEntry` root and
its `SampleList` and `SampleItem` subclasses. The launcher resolves all items
with Koin `getAll`, keeps a typed `SampleEntry` back stack, and renders every
destination through one polymorphic `NavEntry`. The Android Views item has one
common Compose root; Android embeds the legacy hierarchy through `AndroidView`,
and other targets show an unavailable message inside the same route. A Material 3
`Scaffold` owns system-bar insets and its top app bar owns back navigation, so
content applies the scaffold padding exactly once. The shared UI module has no
Symbols dependency.

The sample-feature convention layers its feature dependencies and BuildConfig
field over the shared Compose convention. BuildConfig generates
`SampleBuildConfig.MODULE_PATH` from `project.path` in each module, keeping
Gradle paths out of source. Build-created files live only under each module's
`build` directory. The Koin compiler transforms Kotlin IR and
therefore emits no visible generated source or resource files.
See the [sample module map](../samples/README.md) for targets, output paths, and
run commands.

## Determinism and trust boundaries

The canonical inputs are:

- `fonts/material/MaterialSymbols.codepoints`;
- the three versioned variable fonts; and
- pinned invariants and checksums in `tools/verify_material_fonts.py`.

The catalog generator uses only the Python standard library.
Static font instancing, built-in vector extraction, and font inspection use a
pinned FontTools release in an isolated maintainer/CI environment. The verifier
checks the unmodified upstream inputs; separate generator `--check` modes
byte-compare the derived regular fonts and source outputs.

The opt-in Gradle generator uses an engine-neutral outline model with Skiko as
its font and SVG-path reader. It validates manifests, SVG structure, axes,
glyph coverage, and output paths before synchronizing only files it owns.

See [custom fonts](CUSTOM_FONTS.md) for derivative assets and
[performance](PERFORMANCE.md) for the cost model and shrinker evidence boundary.
