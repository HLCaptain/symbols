# Build-time font conversion

The `io.github.hlcaptain.symbol-fonts` Gradle plugin converts selected glyphs
from a regular or variable OpenType font into source and resource forms that
ship without the input font:

- common Compose `ImageVector` properties;
- native Android vector drawables under `res/drawable`; and
- Compose Multiplatform drawable resources.

Generation runs during the build in an isolated JVM. It uses Skiko to read TTF,
OTF, or an indexed TTC face; application builds do not need Python or FontTools.
The input font is a build input, not a runtime dependency unless the application
also adds a font artifact separately.

## Apply the plugin

Snapshots publish the plugin marker, implementation, and generator core to this
repository's GitHub Packages registry. Because the repository/package is
currently private, plugin resolution needs the same authenticated
`read:packages` credentials as the runtime libraries:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/hlcaptain/symbols")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    id("io.github.hlcaptain.symbol-fonts") version "0.1.0-SNAPSHOT"
}
```

For development inside a source checkout, resolve the exact checked-in tooling
instead:

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
that metadata once into a checked-in manifest. The runnable sample normalizes
Font Awesome YAML, Tabler CSS, and Powerline assignments under
[`fonts/samples`](../fonts/samples/README.md).

Configure one or more font styles against that manifest:

```kotlin
symbolFonts {
    iconSet("AppIcons") {
        packageName.set("com.example.icons")
        manifest.set(layout.projectDirectory.file("icons/app-icons.codepoints"))

        // Explicit selection is the size-conscious default.
        include("check", "favorite", "home")

        style("Rounded") {
            font.set(layout.projectDirectory.file("icons/rounded-variable.ttf"))
            axis("FILL", 1f)
            axis("GRAD", 0f)
            axis("opsz", 24f)
            axis("wght", 400f)

            imageVectors()
            androidDrawables()
            composeDrawables()
        }

        style("Regular") {
            font.set(layout.projectDirectory.file("icons/rounded-regular.ttf"))
            imageVectors()
        }
    }
}
```

Use `includeAll()` only when the complete manifest is intentional. A style must
enable at least one output. An empty `axes` map reads an ordinary font as-is, or
uses the default instance of a variable font. Repeated `axis(tag, value)` calls
select a fixed variable-font instance at generation time; the result is not
variable at runtime.

Optional settings include `rootName`, `fontIndex`, `resourcePrefix`,
`symbolsPerFile`, `precision`, `viewportWidth`, and `viewportHeight`. Defaults
are a 24×24 viewport, four decimal places, and 64 unique code points per Kotlin
file.

The default outline transform maps one font em into the smaller viewport
dimension, starts at `originX = 0`, and places the baseline at the viewport
bottom. Fonts with different ascent, descent, or side-bearing conventions can
set `emSize`, `originX`, and `baselineY` explicitly:

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

## Consume the outputs

Generated Kotlin uses a Compose-style, strongly typed namespace:

```kotlin
import com.example.icons.AppIcons
import com.example.icons.rounded.Home

Icon(
    imageVector = AppIcons.Rounded.Home,
    contentDescription = "Home",
)
```

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
share a codepoint, the lexicographically first selected semantic name supplies
the segment and every alias converges on that one file.

The plugin wires:

- generated Kotlin into Kotlin Multiplatform `commonMain` or Kotlin/JVM `main`;
- native vector XML into every Android variant through the Android Components
  generated-resources API; and
- Compose XML into a generated `commonMain` custom resource directory.

## Shrinking and size control

Selecting only required manifest names is the strongest and most predictable
size control because unused glyphs are never generated.

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

Generation tasks declare the manifest, font bytes, axes, selection, rendering
options, generator classpath, and output directories to Gradle and are marked
cacheable. Input file paths are not part of the content identity. Outputs are
written deterministically, and files previously owned by the generator are
removed when a selection or output mode changes.

The generator rejects malformed manifests, duplicate names, invalid Unicode
scalars, missing glyphs, Kotlin identifier collisions, invalid font indices,
unsupported axis requests, and unsafe output paths. Regular and variable fonts
share the same outline and renderer test suite in
`tooling/symbol-generator-core`.

From a source checkout:

```shell
./gradlew -p tooling :symbol-generator-core:test \
  :symbol-gradle-plugin:test
```

The lower-level CLI is available through the core application for integrations
that cannot use the Gradle plugin:

```shell
./gradlew -p tooling :symbol-generator-core:run --args="--help"
```

Its required inputs are `--font`, `--manifest`, `--package`, `--set`, and
`--style`; choose exactly one of repeated `--include` or `--include-all`, and at
least one of `--kotlin-output`, `--android-output`, or `--compose-output`.

Only generate from fonts and artwork the application is licensed to use. The
generator does not change or grant rights to its inputs or derived vector
outlines.
