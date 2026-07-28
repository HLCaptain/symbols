# Symbols

Variable-font Material Symbols for Compose Multiplatform, with typed resource-like
access and optional `ImageVector` packs.

[![CI](https://github.com/HLCaptain/symbols/actions/workflows/ci.yml/badge.svg)](https://github.com/HLCaptain/symbols/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

> Symbols is preparing its first `0.1.0` release. Snapshot APIs may still change.

The normal icon path ships one variable TrueType font per selected style, not
thousands of vector declarations or resource files. All 4,102 upstream names are
available as Kotlin properties such as `MaterialSymbols.Home`, while runtime lookup,
aliases, raw code points, and static `ImageVector` access remain available when an
application needs them.

## Highlights

- Outlined, Rounded, and Sharp style artifacts; exactly one font resource in each.
- Four live axes: fill, weight, grade, and optical size.
- 4,102 typed names covering 3,802 unique code points, including aliases.
- Allocation-light inline catalog handles and lazy lookup tables.
- Optional static vector packs at `FILL=0, GRAD=0, opsz=24, wght=400`.
- Android, iOS, JVM/Desktop, JavaScript, and Wasm targets.
- Deterministic generators and strict font/provenance verification.
- Apache-2.0 project code and Apache-2.0 Google Material Symbols assets.

## Add a dependency

Snapshots are published to GitHub Packages from `main`. GitHub's
[Maven registry documentation](https://docs.github.com/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)
requires an authenticated package read, so configure a token with
`read:packages` in `GITHUB_TOKEN`:

```kotlin
repositories {
    mavenCentral()
    google()
    maven {
        url = uri("https://maven.pkg.github.com/hlcaptain/symbols")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}
```

Add only the styles or access modes the application uses:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(
                "io.github.hlcaptain:symbols-material-outlined:0.1.0-SNAPSHOT",
            )

            // Optional static ImageVector pack; it does not include a font.
            implementation(
                "io.github.hlcaptain:symbols-material-vectors-outlined:0.1.0-SNAPSHOT",
            )
        }
    }
}
```

Replace `outlined` with `rounded` or `sharp` for another style. Font-backed
artifacts require Android API 26 or newer because Android's
[variable-font APIs](https://developer.android.com/develop/ui/compose/text/fonts#variable-fonts)
start there. The catalog and static vector packs support Android API 21.

## Render a variable-font icon

Named catalog entries are extension properties. Import the package wildcard or
the individual property:

```kotlin
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.outlined.MaterialSymbolsOutlined

MaterialSymbolIcon(
    symbol = MaterialSymbols.Home,
    font = MaterialSymbolsOutlined,
    contentDescription = "Home",
    axes = MaterialSymbolAxes(
        fill = 1f,
        weight = 500,
        grade = 0f,
        opticalSize = 24f,
    ),
    tint = MaterialTheme.colorScheme.primary,
)
```

Use `contentDescription = null` for a decorative icon. The renderer removes the
private-use character from semantics and exposes only a supplied, localized
description.

For a grid or list, build the `FontFamily` once and share it:

```kotlin
val axes = MaterialSymbolAxes(weight = 500)
val family = rememberMaterialSymbolFontFamily(MaterialSymbolsOutlined, axes)

LazyRow {
    items(symbols) { symbol ->
        MaterialSymbolIcon(
            symbol = symbol,
            fontFamily = family,
            contentDescription = symbol.name,
            axes = axes,
        )
    }
}
```

The public `MaterialSymbolsOutlined.resource` property also exposes the underlying
Compose
[`FontResource`](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html#fonts)
for custom text layouts. It is intentionally a multiplatform resource rather
than a second Android-only `R.font` copy.

## Lookup, aliases, and raw code points

```kotlin
val search = MaterialSymbols.fromName("search")
val allStarNames = MaterialSymbols.aliases(MaterialSymbols.Star)
val everyName = MaterialSymbols.all

check(search?.codePoint == 0xE8B6)
check(MaterialSymbols.size == 4_102)
```

Aliases remain distinct named entries even when they share a code point. Unknown
names return `null`; unknown code points return an empty alias list. Code points
are `Int`, and text encoding supports the full Unicode scalar range rather than
assuming every future glyph stays in the BMP.

A custom font glyph can bypass the generated catalog:

```kotlin
MaterialSymbolIcon(
    codePoint = 0xF0001,
    font = MySymbols,
    contentDescription = "Custom action",
)
```

`MySymbols` implements `MaterialSymbolFont` and points at the custom Compose
font resource. Invalid Unicode scalar values fail immediately.

## Use an ImageVector

Vector packs are fixed snapshots of the default axes. They are useful for older
Android versions, APIs that specifically require `ImageVector`, or a project that
prefers vector resources over a font:

```kotlin
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.outlined.vectors.outlinedImageVector
import io.github.hlcaptain.symbols.material.outlined.vectors.asOutlinedImageVector

Icon(
    imageVector = MaterialSymbols.Home.outlinedImageVector,
    contentDescription = "Home",
)

Icon(
    imageVector = MaterialSymbols.ArrowBack.asOutlinedImageVector(autoMirror = true),
    contentDescription = "Back",
)
```

Vectors are built and cached on first access. Code-point aliases share the same
cached vector. Vector packs intentionally do not pretend to support variable
axes; choose a font artifact when the design needs axis animation or non-default
values.

## Axes

| Property | Font axis | Range | Default |
| --- | --- | ---: | ---: |
| `fill` | `FILL` | `0..1` | `0` |
| `weight` | `wght` | `100..700` | `400` |
| `grade` | `GRAD` | `-50..200` | `0` |
| `opticalSize` | `opsz` | `20..48` | `24` |

Values are validated when `MaterialSymbolAxes` is constructed. Use a distinct
axis value to get a distinct remembered font; do not animate axes by rebuilding
an entire catalog.

## Modules

| Artifact suffix | Contents | Android min SDK |
| --- | --- | ---: |
| `material-core` | catalog, names, aliases, code points; no Compose and no font | 21 |
| `material-compose` | axis model and font renderer; no font | 26 |
| `material-outlined` | compose adapter plus one Outlined variable font | 26 |
| `material-rounded` | compose adapter plus one Rounded variable font | 26 |
| `material-sharp` | compose adapter plus one Sharp variable font | 26 |
| `material-vectors-outlined` | default-axis Outlined vectors; no font | 21 |
| `material-vectors-rounded` | default-axis Rounded vectors; no font | 21 |
| `material-vectors-sharp` | default-axis Sharp vectors; no font | 21 |

See [architecture](docs/ARCHITECTURE.md), [performance choices](docs/PERFORMANCE.md),
and [custom font guidance](docs/CUSTOM_FONTS.md) for the trade-offs behind this
split.

## Build and verify

The checked-in wrapper uses Gradle 8.14.3. Use JDK 17 or 21 and an Android SDK
with API 36:

```shell
./gradlew jvmTest assembleRelease lintRelease verifyPublishedArchives

python3 tools/generate_material_symbols.py --check
python3 -m unittest discover -s tools/tests -p "test_*.py"
```

Font and vector conformance checks use a pinned maintainer-only FontTools version:

```shell
python3 -m venv /tmp/symbols-fonttools
/tmp/symbols-fonttools/bin/python -m pip install \
  -r tools/requirements-font-verification.txt
/tmp/symbols-fonttools/bin/python tools/verify_material_fonts.py
/tmp/symbols-fonttools/bin/python tools/generate_material_vectors.py --check
```

`verifyPublishedArchives` byte-compares the packaged legal notices with the
root files and enforces exactly one TTF in each style runtime archive and none
in core, renderer-only, vector, or metadata archives.

Run the interactive sample with `./gradlew :composeApp:run`. It demonstrates all
font styles, every axis, runtime search, shared-family rendering, and vector
access.

## Asset provenance and licensing

Project source is licensed under Apache License 2.0. Bundled Material Symbols
fonts and derived default-axis paths come from Google's
[`material-design-icons`](https://github.com/google/material-design-icons)
repository and are also Apache-2.0 licensed. This project is independent and is
not endorsed by Google.

Exact font versions, hashes, paths, and trademark notice are recorded in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). See
[fonts/material/README.md](fonts/material/README.md) before updating an asset.

Contributions are welcome under [CONTRIBUTING.md](CONTRIBUTING.md). Please use the
private process in [SECURITY.md](SECURITY.md) for vulnerabilities.
