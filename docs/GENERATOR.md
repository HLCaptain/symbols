# Build-time font and SVG conversion

The `io.github.hlcaptain.symbol-fonts` Gradle plugin converts every manifest
entry from a regular or variable OpenType font, or every icon in a flat SVG
directory, into source and resource forms:

- common Compose `ImageVector` properties;
- native Android vector drawables under `res/drawable`; and
- Compose Multiplatform drawable resources.

Generation runs during the build in an isolated JVM. It uses Skiko to read TTF,
OTF, an indexed TTC face, and SVG path data; application builds do not need
Python or FontTools. Inputs remain build inputs unless the application also
packages them separately.

## Apply the plugin

Stable releases publish the plugin marker, implementation, and generator core
to Maven Central:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    id("io.github.hlcaptain.symbol-fonts") version "0.1.0"
}
```

Development snapshots are not uploaded to a remote package registry. Inside a
source checkout, resolve the exact checked-in tooling instead:

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("tooling")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    id("io.github.hlcaptain.symbol-fonts")
}

dependencies {
    implementation("io.github.hlcaptain:symbols-core:0.1.0")
}
```

The runtime library artifacts and build plugin have independent dependency
lifecycles. Pin both deliberately; applying the generator does not add a runtime
font or Symbols library dependency.

## Declare a semantic icon set

The manifest is a stable, lowercase snake-case name followed by a hexadecimal
Unicode scalar:

```text
check e5ca
favorite e87e
home e9b2
```

This is the plugin's canonical interchange format, not an industry standard.
OpenType's `cmap` identifies code points but does not provide stable semantic
API names. Providers commonly publish those names in CSS, YAML, or JSON; convert
that metadata once into a checked-in manifest. The repository retains complete
normalized Font Awesome and Tabler manifests and binaries under
[`fonts/samples`](../fonts/samples/README.md) as pinned generator/provenance
fixtures; the launcher does not package their complete catalogs. The focused
[`custom-static`](../samples/custom-static/build.gradle.kts) and
[`custom-variable`](../samples/custom-variable/build.gradle.kts) modules
demonstrate build-time Powerline and Academmunicons vector and Compose drawable
generation. The Academmunicons module also generates a runtime descriptor from
the packaged source font so fixed resources and live axes can be compared
directly.

Configure one codepoint map per font style:

```kotlin
symbolFonts {
    iconSet("AppIcons") {
        style("Rounded") {
            codepoints.set(
                layout.projectDirectory.file("icons/rounded.codepoints"),
            )
            font("rounded-variable.ttf")
            axis("FILL", 1f)
            axis("GRAD", 0f)
            axis("opsz", 24f)
            axis("wght", 400f)

            imageVectors()
            androidDrawables()
            composeDrawables()
        }

        style("Regular") {
            codepoints.set(
                layout.projectDirectory.file("icons/regular.codepoints"),
            )
            font("rounded-regular.ttf")
            imageVectors()
        }
    }
}
```

The plugin searches `src/main/res/font`, `src/androidMain/res/font`, and
`src/commonMain/composeResources/font` (the Android and Compose directory names
are both singular). If exactly one supported TTF, OTF, or TTC exists, omit
`font(...)`; if several exist, select one by exact file name. `font.set(...)`
accepts a file from any other location and avoids packaging a build-only font
as an app resource. Keep an Android-only font in `src/main/res/font` for a
regular Android module or `src/androidMain/res/font` for a KMP module, then call
`androidDrawables()` without an argument. One shared task generates the same
native resources for every variant.

Android projects default `packageName` to `<android namespace>.generated`.
Other projects use `<group>.<project>.generated` when `group` is a valid Kotlin
package, otherwise `generated.symbols.<project>`. An explicit
`packageName.set(...)` always wins.

Every manifest entry is generated. The Gradle DSL intentionally has no
`include` or `includeAll` selection mode; split a manifest/font into a separate
icon set only when it needs a different publication or build boundary. A style
must enable at least one output. An empty `axes` map reads an ordinary font
as-is, or uses the default instance of a variable font. Repeated
`axis(tag, value)` calls select a fixed variable-font instance at generation
time; the result is not variable at runtime.

`composeDrawables()` registers the generated XML from `build/` as standard
Compose Multiplatform resources. Compose therefore generates
`Res.drawable.<resource_name>` accessors consumable with `painterResource`.

## Variant-aware Android font resources

Use Android resource-overlay mode only when a build type, product flavor, or
full variant intentionally replaces the font. The overload is the per-style
opt-in toggle; no global Gradle property or different plugin is required. It
supports `com.android.application`, `com.android.library`, and
`com.android.kotlin.multiplatform.library`. The native KMP plugin has a single
Android variant; build types and flavor overlays belong in ordinary Android
application/library modules. Android resource processing is enabled automatically
when native drawables are requested. Without an Android plugin, the generator
warns that variant native drawables cannot be attached.

```kotlin
symbolFonts {
    iconSet("AppIcons") {
        style("Rounded") {
            codepoints.set(
                layout.projectDirectory.file("icons/app-icons.codepoints"),
            )
            androidDrawables(fontResource = "app_icons.ttf")
        }
    }
}
```

Keep the same lowercase Android resource filename in every overlay:

```text
src/main/res/font/app_icons.ttf
src/debug/res/font/app_icons.ttf
src/free/res/font/app_icons.ttf
src/freeDebug/res/font/app_icons.ttf
```

The plugin resolves the first `font/app_icons.ttf` from AGP's ordered static
resource layers and registers one cacheable generation task per Android
variant. A missing file or two matches at the same priority fail with the
variant and searched roots. The generated XML is attached through AGP's public
generated-resource API, remains below `build/`, and belongs to the module's
normal Android namespace. Do not set `packageName` merely to use `R.drawable`.
Only local static resource directories participate; dependency AARs and
task-generated resource directories are not font inputs.

Creating or removing an overlay invalidates the current Gradle configuration
cache entry once so AGP can rebuild its source graph; subsequent unchanged
builds reuse the new entry. Together with one generator task per variant, this
is the development-cost reason the mode is explicit rather than automatic.

`androidDrawables()` clears this mode and returns to shared Android source-set
behavior. Prefer that no-argument form unless overlays are intentional: it has
one generation task instead of one per variant and keeps every variant on the
same source of truth. The runnable Android Views sample uses this default. The
[`shrinkable-vectors`](../benchmarks/shrinkable-vectors/build.gradle.kts)
fixture is the focused `androidDrawables(fontResource = ...)` example.

Mixed output modes remain supported. Native Android drawables follow variant
overlays, while `imageVectors()` and `composeDrawables()` continue to use the
configured shared `font.set(...)`, `font(...)`, or conventional main font. The
plugin warns because incompatible glyph maps could make the representations
diverge and enabling both native and Compose drawables packages both forms.
`svgDirectory` remains the source for every output and causes the font-resource
argument to be ignored with a warning.

The source font is still an ordinary `R.font` resource. An Android application
with code minification and `shrinkResources` can remove the entire font when it
is unreferenced after generation, along with unused generated drawables. The
plugin does not enable shrinking or subset font files, and an AAR may retain
resources until its consuming application is shrunk. Compose Multiplatform
`Res.drawable` assets are a separate representation and do not become native
Android resources through this mode. In a KMP module, native `R.drawable`
access therefore stays in Android source sets; common code continues to use
`composeDrawables()` and `Res.drawable`.

Optional settings include `packageName`, `rootName`, `fontIndex`,
`resourcePrefix`, `symbolsPerFile`, `precision`, `viewportWidth`, and
`viewportHeight`. Defaults are a 24×24 viewport, four decimal places, and 64
unique code points per Kotlin file.

The default outline transform maps one font em into the smaller viewport
dimension, starts at `originX = 0`, and places the font baseline at the viewport
bottom (`baselineY = viewportHeight`). Normally omit all three values. Skia
returns baseline-relative, y-down paths, so `baselineY` is the translation that
puts `y = 0` at the output baseline; it is not a Compose layout baseline and has
no runtime effect. Set it only when an upstream font's metrics otherwise clip or
misalign the whole style:

```kotlin
style("Regular") {
    font.set(layout.projectDirectory.file("icons/symbols.otf"))
    emSize.set(21.145374f)
    originX.set(6.533921f)
    baselineY.set(20.130396f)
    imageVectors()
}
```

These are one transform per style, not automatic per-glyph fitting. Choose a
consistent upstream font face or split incompatible metrics into separate
styles.

## Declare a flat SVG icon set

Point one style at a flat directory instead of configuring `font` and
`codepoints`:

```kotlin
symbolFonts {
    iconSet("Tabler") {
        packageName.set("com.example.app.generated")
        style("Outline") {
            svgDirectory.set(layout.projectDirectory.dir("icons/tabler"))
            imageVectors()
            androidDrawables()
            composeDrawables()
        }
    }
}
```

Every direct lowercase `.svg` file is generated in deterministic filename
order; nested directories are not scanned. Filenames must be lowercase words
separated by `-` or `_`. Hyphens normalize to underscores, and normalization
collisions fail the build. SVG mode is mutually exclusive with `font`,
`codepoints`, `fontIndex`, and `axis(...)`. Each source `viewBox` is uniformly
scaled and centered in the configured viewport.

The accepted subset is deliberately small and monochrome:

- an `<svg>` root with a finite, positive `viewBox`, optional width/height
  metadata, and optional `preserveAspectRatio="xMidYMid meet"`;
- direct `<path>` children with `d` path data;
- root/per-path `fill`, `stroke`, opacity, fill/stroke opacity, fill rule,
  stroke width, line cap, line join, and miter limit; and
- paint values `currentColor`, black, `none`, or `transparent`.

The inert root `class` metadata shipped by Tabler is ignored. Groups,
transforms, inline `style`, path classes, shapes other than `path`, `use`,
gradients, masks, clipping, text, images, external references, unsupported
namespaces, and unknown attributes are rejected with the source path and
location. The XML parser disables DTDs, external entities, external schemas,
XInclude, and entity expansion; generation never resolves content referenced by
an SVG. This strict boundary keeps untrusted XML features out of builds while
making unsupported artwork fail visibly instead of rendering approximately.

## Generate runtime font descriptors

The same plugin can inspect packaged Compose font resources and generate
regular or variable `SymbolFont` descriptors:

```kotlin
symbolFonts {
    composeFontResources.from(
        layout.projectDirectory.dir("src/commonMain/composeResources"),
    )
}
```

Each configured root must contain direct `font/*.ttf`, `*.otf`, or `*.ttc`
files. Roots may be conventional source directories or task-backed providers;
Compose generates the standard `Res.font.<normalized_file_name>` accessor for
either. Compose keeps its conventional source root; the plugin merges any other
configured roots into one generated Compose resource directory. The cacheable
`generateSymbolFontDescriptors` task emits
`Res.symbolFonts.<normalized_file_name>` into common Kotlin. Variable fonts get
their visible axis ranges and defaults from `fvar`; static fonts become regular
descriptors. Configuring a root makes Compose generate `Res` even when its
resources dependency is transitive. Descriptor visibility follows Compose
Resources' `publicResClass`, whose default is internal.

Every detected font also receives a public extension such as
`Symbols.MySymbolsVariable`. Its defaults are the global `Symbols` receiver, an
UpperCamelCase property derived from the resource name, the module's Compose
resource package, no destructuring component, and no explicit fixed axes. Use
`fontAccessor` only to override the values a shared namespace needs:

```kotlin
fontAccessor("my_symbols_regular") {
    packageName.set("com.example.icons")
    receiver.set("com.example.icons.AppIcons.Rounded")
    propertyName.set("staticFont")
    componentIndex.set(2)
    fixedAxisValues.putAll(mapOf("wght" to 400f, "opsz" to 24f))
}
```

Generated public properties delegate to the module-internal `Res.symbolFonts`
descriptor. Duplicate property or `componentN` signatures fail generation.
All generated Kotlin stays under `build`, is compiled into the library, and is
included in its source publication; consumers do not run the generator.

Compose exposes one custom resource directory per source set. When another
generator also contributes `commonMain` resources, register its task-backed
provider with Symbols instead of making a second `customDirectory` call:

```kotlin
symbolFonts {
    composeResourceRoots.from(
        otherGenerator.flatMap { task -> task.outputDirectory },
    )
}
```

Symbols combines those roots with conventional resources, font roots, and
generated Compose drawables. Identical roots are deduplicated; different roots
that contain the same relative resource path fail rather than silently choosing
one. ImageVector-only and Android-only generation leave Compose resource
configuration untouched.

File names follow Compose's hyphen-to-underscore normalization. Qualifier
subdirectories are not scanned, and TTC input currently reads face zero.

## Generate runtime catalogs

Runtime font browsers can generate compact name/codepoint data without
extracting outlines or checking in a large source file:

```kotlin
symbolFonts {
    catalogPackageName.set("com.example.sample")
    catalog("MySymbolsCatalog") {
        codepoints.set(layout.projectDirectory.file("icons/my-symbols.codepoints"))
    }
}
```

The cacheable `generateSymbolCatalogs` task writes `SymbolCatalogEntry` and the
named `List<SymbolCatalogEntry>` to `build/generated/symbolFonts/catalogs` and
wires it into common source. Catalog generation always reads the complete
manifest.

## Consume the outputs

With Android namespace `com.example.app`, generated Kotlin uses a Compose-style,
strongly typed namespace:

```kotlin
import com.example.app.generated.AppIcons
import com.example.app.generated.rounded.Home
import io.github.hlcaptain.symbols.Symbols

Icon(
    imageVector = Symbols.AppIcons.Rounded.Home,
    contentDescription = "Home",
)
```

Each generated icon set contributes one extension branch to the shared
`Symbols` object. Keep `rootName` globally distinctive across imported
dependencies; Kotlin cannot disambiguate two same-named extension branches from
different packages without an import alias.

Aliases in the manifest get separate semantic properties and share the same
per-codepoint builder and nullable cache. Each codepoint builder is directly
reachable from its property. The generated API contains no all-icons
collection, reflection hook, path registry, or global dispatcher, so an
unreferenced property does not create a reachability edge to every other icon.

Android and Compose drawable output creates one XML file per unique code point.
With the default prefix from the example, `home` is
`app_icons_rounded_home_ue9b2`. Android code can use
`R.drawable.app_icons_rounded_home_ue9b2`; Compose Multiplatform code uses the
corresponding generated `Res.drawable` property. The semantic segment keeps
resources recognizable, while the U+ suffix prevents collisions. When aliases
share a codepoint, the lexicographically first semantic name supplies the
segment and every alias converges on that one file.

SVG properties follow the same Kotlin namespace without a codepoint. For
example, `hierarchy-2.svg` in the `Tabler`/`Outline` declaration produces
`Symbols.Tabler.Outline.Hierarchy2`, whose stable `ImageVector.name` remains
`Tabler.Outline.Hierarchy2`. Its Android and Compose resource name is
`tabler_outline_hierarchy_2`, with no U+ suffix.

Passing an SVG vector directly to `Icon(imageVector = ...)` preserves its
authored stroke. `rememberSymbolPainter()` instead reads the existing
`SymbolsTheme.fontSettings` `wght` value and scales every authored stroke: 100,
400, and 700 map to 0.5×, 1×, and 1.5×. It changes paint configuration without
re-parsing the SVG or rebuilding geometry. For state-driven values,
`rememberSymbolPainter { settingsState.value }` isolates the snapshot read to
the vector child composition, although a stroke change still updates and
rasterizes that subtree. Generated Android and Compose XML remain static at the
authored 1× width. A future per-symbol theme can key an override map by the
stable `ImageVector.name`; no such map API is implemented yet.

The plugin wires:

- generated Kotlin into Kotlin Multiplatform `commonMain` or Kotlin/JVM `main`;
- native vector XML into every Android variant through the Android Components
  generated-resources API; and
- Compose XML into a generated `commonMain` custom resource directory.

## Generated output locations

Every file created by a normal Gradle generation task stays below the owning
module's `build` directory:

| Output | Directory below the module |
| --- | --- |
| Typed icon namespace | `build/generated/symbolFonts/<set>/namespace/kotlin` |
| `ImageVector` sources | `build/generated/symbolFonts/<set>/<style>/kotlin` |
| Android vector XML in an Android project | `build/generated/res/<generation-task>/drawable` |
| Standalone Android XML task convention | `build/generated/symbolFonts/<set>/<style>/androidRes` |
| Compose drawable XML | `build/generated/symbolFonts/<set>/<style>/composeResources` |
| Runtime font descriptors | `build/generated/symbolFonts/fontDescriptors/kotlin` |
| Runtime catalogs | `build/generated/symbolFonts/catalogs/kotlin` |
| Merged Compose inputs | `build/generated/symbolFonts/composeResources` |

These directories are task outputs: `clean` removes them, the next relevant
build recreates them, and they must not be edited or committed. Compose and AGP
may copy those files into other intermediate paths under `build`; those copies
remain disposable too.

## Why generation remains a Gradle task

The production backend deliberately stays outside Kotlin compilation. One
cacheable task reads each font/SVG input once, writes common Kotlin and both
resource formats, and lets every Kotlin Multiplatform target consume the same
outputs. A no-change build skips the task without loading Skiko.

A custom Kotlin compiler plugin is not an equivalent faster backend here. It
would need to participate in each configured target compilation while the
existing Gradle tasks still generate and wire Android/Compose resources. It
would also couple Symbols to an API that Kotlin documents as unstable across
compiler releases. Its generated declarations require matching IDE compiler
support. See Kotlin's
[custom compiler plugin guidance](https://kotlinlang.org/docs/custom-compiler-plugins.html).

KSP is a source-processing API, so it can generate another Kotlin source form
but cannot replace outline extraction or the resource wiring. Its incremental
dependency model associates outputs with Kotlin source files, while Symbols'
primary inputs are external TTF/OTF/TTC, manifest, and SVG files. KSP also
creates processing tasks for each KMP target where it is configured. See the
official
[KSP overview](https://kotlinlang.org/docs/ksp-overview.html),
[incremental model](https://kotlinlang.org/docs/ksp-incremental.html), and
[multiplatform setup](https://kotlinlang.org/docs/ksp-multiplatform.html); KSP's
[external-file input request](https://github.com/google/ksp/issues/2008) is
closed as not planned.

For those reasons this repository does not ship a Kotlin-only compiler-plugin
or KSP comparator that silently omits Android and Compose drawables. Add an
experimental backend only with an equivalent pinned corpus and end-to-end
measurement showing a material compile-time improvement after the extra
per-target work. The current measured baseline and commands are in
[performance](PERFORMANCE.md#build-time-generator-baseline).

Built-in Material vectors use the pinned Python/FontTools emitter through the
library's cacheable `generateMaterialVectors` tasks. Their Kotlin lives under each
module's `build/` directory and is included in compiled artifacts and source JARs.
Regular font instances remain checked-in maintainer outputs. Published-dependency
consumers do not regenerate either or require Python/FontTools; repository source
builders configure Python as described in [Contributing](../CONTRIBUTING.md#development-environment). Their provenance and verification commands are
documented in [architecture](ARCHITECTURE.md#determinism-and-trust-boundaries)
and [third-party notices](../THIRD_PARTY_NOTICES.md).

## Shrinking and size control

The plugin generates the complete manifest or flat SVG directory. Keep a source
set focused, or split it into separately consumed modules when measured build
or unshrunk packaging cost justifies the boundary.

Generated `ImageVector` properties are structured so full-mode R8 can discard
unreachable getters and backing classes. Native Android XML participates in the
normal Android resource shrinker when the consuming release build enables both
code minification and resource shrinking. Compose resource packaging and
shrinking vary by target and toolchain; do not assume the Android resource
shrinker applies to every Compose target.

These are reachability properties, not a universal byte-saving guarantee. Keep
rules, reflection, dynamic resource lookup, the chosen output modes, AGP/R8
versions, and packaging all affect a final artifact. Verify the release APK,
App Bundle, desktop distribution, or web bundle used by the product. See
[performance and size](PERFORMANCE.md) for a reproducible measurement checklist.

## Reproducibility and validation

Generation tasks declare the manifest/font or SVG directory, axes, rendering
options, generator classpath, and output directories to Gradle and are marked
cacheable. Input file paths are not part of the content identity. Outputs are
written deterministically, and files previously owned by the generator are
removed when an input or output mode changes.

The generator rejects malformed manifests, duplicate names, invalid Unicode
scalars, missing glyphs, Kotlin identifier collisions, invalid font indices,
unsupported axis requests, unsafe output paths, malformed SVGs, and every SVG
feature outside the documented secure subset. Regular fonts, variable fonts,
and SVGs share the renderer tests in `tooling/symbol-generator-core`.

From a source checkout:

```shell
./tooling/gradlew -p tooling :symbol-generator-core:test \
  :symbol-gradle-plugin:test
```

The lower-level CLI is available through the core application for integrations
that cannot use the Gradle plugin:

```shell
./tooling/gradlew -p tooling :symbol-generator-core:run --args="--help"
```

Its required metadata is `--package`, `--set`, and `--style`. Choose exactly one
source mode: `--svg-directory`, or both `--font` and `--manifest`. Every icon is
generated. Enable at least one of `--kotlin-output`, `--android-output`, or
`--compose-output`.

Only generate from fonts and artwork the application is licensed to use. The
generator does not change or grant rights to its inputs or derived vector
outlines.
