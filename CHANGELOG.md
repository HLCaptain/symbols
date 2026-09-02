# Changelog

All notable user-visible changes to this project will be documented in this
file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project intends to use [Semantic Versioning](https://semver.org/) once
artifacts are released.

## [Unreleased]

### Changed

- Unified built-in and generated entry points under the zero-dependency
  `Symbols` root, including `Symbols.Material.Search`,
  `Symbols.Material.Rounded.Search`, and
  `Symbols.Academmunicons.Semibold.Orcid`. Removed the unused generated
  Material font-wrapper namespace before the first release.
- Extracted reusable regular/variable symbol-font Compose APIs into the generic
  `variant-font-core` module under `io.github.hlcaptain.symbols.font`, while
  keeping `MaterialSymbolsTheme` as the Material axes/style adapter. Reorganized
  the README around migration from `material-icons-extended`, standard Compose
  `Icon`, and Android Views/XML before the advanced font renderer.
- Sealed the `SymbolFont` root while keeping `SymbolFont.Regular` and
  `SymbolFont.Variable` open as its nested extension interfaces. Direct root
  implementations must migrate to exactly one of them; implementing both
  remains invalid. Variable-font axis metadata now lives on
  `SymbolFont.Variable`.
- Font-generation codepoint maps now belong to each style. The Gradle plugin
  generates all entries implicitly, removes the `include`/`includeAll` DSL,
  derives a package name, and discovers fonts in conventional Android and
  Compose resource directories.
- Runtime font descriptors and visible variable-axis metadata are generated
  from Compose font resources. Material modules keep Compose's `Res` API
  internal by default. Configured font roots are packaged through the plugin's
  single generated Compose resource directory.
- Runtime-axis samples now provide a start/stop toggle beside each continuous
  Material slider (`steps = 0`). The custom
  Academmunicons sample compares fixed build-time generation with live runtime
  axes from the same packaged font.
- Custom Powerline and Academmunicons samples now generate standard Compose
  Multiplatform `Res.drawable` resources below `build/` alongside their typed
  `ImageVector` APIs. Font binaries remain standard `Res.font` resources, with
  `Res.symbolFonts` providing typed metadata without another packaged copy.
- Added reproducible per-sample release/shrunk APK profiles and local-only size
  and build-time analysis under `benchmarks/sample-app`.
- Split the interactive sample into a Material 3 edge-to-edge launcher, shared
  API/UI modules and eight focused multiplatform features. Koin collects one
  metadata-rich `SampleItem` per feature; a sealed navigation-entry hierarchy
  gives the launcher one renderer, and Android Views now run inline through
  Compose-View interop. Generated BuildConfig constants expose feature paths.
- Updated the supported Gradle 8, AGP 8.13, Kotlin 2.3, Compose 1.11, Android
  API 21, and JVM 11 dependency lines, and enabled JDK 17 toolchain provisioning
  in the included tooling build. Compose 1.11 removes the iOS x64 target while
  retaining iOS arm64 device and simulator variants. Launcher previews now use
  AndroidX Preview annotations and tooling artifacts.

### Added

- Variant-aware Android font resources through
  `androidDrawables(fontResource = "app_icons.ttf")`, using AGP resource-overlay
  precedence and one generated-resource task per variant. Mixed shared outputs
  remain supported with a divergence warning; the no-argument main-source-set
  mode remains the default.
- Public Outlined, Rounded, and Sharp Compose Multiplatform drawable-resource
  packs for standard `painterResource(Res.drawable...)` usage on Android, JVM,
  JS, Wasm, and iOS.
- Typed catalog access for all 4,102 Material Symbols 2.874 names, including
  runtime lookup, alias preservation, full Unicode scalar encoding, and custom
  codepoint rendering.
- Outlined, Rounded, and Sharp Compose Multiplatform artifacts backed by one
  variable font resource per style.
- API-21-compatible Outlined, Rounded, and Sharp regular-font artifacts
  deterministically instantiated at the default axes.
- Fill, weight, grade, and optical-size axis support plus accessible and
  optionally auto-mirrored font rendering.
- Reusable `SymbolFont`, `SymbolFontAxis`, `SymbolFont.Regular`,
  `SymbolFont.Variable`, `SymbolFontSettings`, `SymbolFontIcon`,
  `rememberSymbolFontFamily`, `SymbolsTheme`, and `SymbolsRuntime` APIs for
  arbitrary Compose Multiplatform symbol fonts.
- Shared `Symbols.Material.{Style}.{Name}` vector APIs and inherited Material
  axes/style through `MaterialSymbolsTheme`.
- Optional shrinker-friendly Outlined, Rounded, and Sharp `ImageVector` packs at
  the default axis position, while preserving dynamic catalog lookup.
- Composable `Symbols.Material.Themed.{Name}` vectors selected by a style
  composition local.
- Outlined, Rounded, and Sharp Android `R.drawable` AARs plus XML, View Binding,
  Data Binding, custom View, and programmatic View examples.
- A cacheable Gradle plugin that converts complete regular/variable font
  manifests or flat monochrome SVG directories into typed `ImageVector`, native
  Android drawable, and Compose drawable output below `build`.
- Focused samples for static and variable Material fonts, regular and variable
  custom build-time vectors, side-by-side `material-icons-extended` and Android
  XML migration, generated custom-font painters, independent theme inheritance,
  and animated live runtime axes with matching sliders.
- A standard Material 3 common-source sample list, top app bar, and back button
  shared by the launcher and feature modules.
- A reproducible Android fixture that verifies typed-vector removal by
  full-mode R8 and unused-resource removal by the Android resource shrinker.
- Deterministic catalog/static-font/vector generators, secure direct
  SVG parsing with strict unsupported-feature rejection, and pinned font/artwork
  provenance verification.
- A theme-aware `rememberSymbolPainter()` that maps the existing
  `SymbolsTheme` `wght=100/400/700` setting to 0.5×/1×/1.5× authored SVG stroke
  width while leaving generated Android/Compose XML static. Its settings
  producer overload can isolate snapshot reads to the vector child composition;
  stroke changes still update and rasterize that vector subtree.
- Pinned Tabler Icons `v3.46.0` SVG samples under MIT, legacy Views/XML
  integration, and same-runner Roborazzi font/SVG screenshot comparisons.
- Cacheable runtime catalog generation into the build directory from checked-in
  manifests, without provider-specific generation scripts.
- Android, JVM, JS, Wasm, and iOS publication targets, an interactive
  multiplatform sample, CI/release automation, and open-source project
  governance.
- Signed Maven Central staging for stable release tags while retaining GitHub
  Packages snapshots.

No versioned release has been published yet.
