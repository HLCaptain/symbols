# Changelog

All notable user-visible changes to this project will be documented in this
file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project intends to use [Semantic Versioning](https://semver.org/) once
artifacts are released.

## [Unreleased]

### Changed

- Extracted reusable regular/variable symbol-font Compose APIs into the generic
  `variant-font-core` module under `io.github.hlcaptain.symbols.font`, while
  keeping `MaterialSymbolsTheme` as the Material axes/style adapter. Reorganized
  the README around migration from `material-icons-extended`, standard Compose
  `Icon`, and Android Views/XML before the advanced font renderer.
- Font-generation codepoint maps now belong to each style. The Gradle plugin
  generates all entries by default, derives a package name, and discovers fonts
  in conventional Android and Compose resource directories.
- Updated the supported Gradle 8, Kotlin 2.2, Compose 1.9, Android API 21, and
  JVM 11 dependency lines, and enabled JDK 17 toolchain provisioning in the
  included tooling build.

### Added

- Typed catalog access for all 4,102 Material Symbols 2.874 names, including
  runtime lookup, alias preservation, full Unicode scalar encoding, and custom
  codepoint rendering.
- Outlined, Rounded, and Sharp Compose Multiplatform artifacts backed by one
  variable font resource per style.
- API-21-compatible Outlined, Rounded, and Sharp regular-font artifacts
  deterministically instantiated at the default axes.
- Fill, weight, grade, and optical-size axis support plus accessible and
  optionally auto-mirrored font rendering.
- Reusable `SymbolFont`, `SymbolRegularFont`, `SymbolVariableFont`,
  `SymbolFontSettings`, `SymbolFontIcon`, `rememberSymbolFontFamily`,
  `SymbolsTheme`, and `SymbolsRuntime` APIs for arbitrary Compose Multiplatform
  symbol fonts.
- Style-typed `Symbols.{Style}.{Name}` font APIs, shared
  `Icons.{Style}.{Name}` vector APIs, and inherited Material axes/style through
  `MaterialSymbolsTheme`.
- Optional shrinker-friendly Outlined, Rounded, and Sharp `ImageVector` packs at
  the default axis position, while preserving dynamic catalog lookup.
- Composable `Icons.Themed.{Name}` vectors selected by a style composition local.
- Outlined, Rounded, and Sharp Android `R.drawable` AARs plus XML, View Binding,
  Data Binding, custom View, and programmatic View examples.
- A cacheable Gradle plugin that converts selected regular or variable font
  glyphs into typed `ImageVector`, native Android drawable, and Compose drawable
  output.
- Runnable complete-catalog Font Awesome, Tabler, and Academmunicons samples,
  including Tabler Outline/Filled styles, 1/1.5/2 px static strokes, live
  Academmunicons custom axes with a regular-first default, provider-metadata
  normalization, and configurable font-outline placement.
- A common-source Interactive Preview that renders the same live Material and
  custom fonts and variable-axis controls as the running sample.
- A reproducible Android fixture that verifies typed-vector removal by
  full-mode R8 and unused-resource removal by the Android resource shrinker.
- Deterministic catalog/namespace/static-font/vector generators, build-time SVG
  stroke-width baking, and strict pinned font and provenance verification.
- Android, JVM, JS, Wasm, and iOS publication targets, an interactive
  multiplatform sample, CI/release automation, and open-source project
  governance.
- Signed Maven Central staging for stable release tags while retaining GitHub
  Packages snapshots.

No versioned release has been published yet.
