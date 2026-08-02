# Changelog

All notable user-visible changes to this project will be documented in this
file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project intends to use [Semantic Versioning](https://semver.org/) once
artifacts are released.

## [Unreleased]

### Changed

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
- Style-typed `Symbols.{Style}.{Name}` font APIs, shared
  `Icons.{Style}.{Name}` vector APIs, inherited axes through
  `MaterialSymbolsTheme`, and an Android variable-font capability gate.
- Optional shrinker-friendly Outlined, Rounded, and Sharp `ImageVector` packs at
  the default axis position, while preserving dynamic catalog lookup.
- Composable `Icons.Themed.{Name}` vectors selected by a style composition local.
- Outlined, Rounded, and Sharp Android `R.drawable` AARs plus XML, View Binding,
  Data Binding, custom View, and programmatic View examples.
- A cacheable Gradle plugin that converts selected regular or variable font
  glyphs into typed `ImageVector`, native Android drawable, and Compose drawable
  output.
- Runnable Font Awesome, Tabler Icons, and Powerline samples, including
  provider-metadata normalization and configurable font-outline placement.
- A reproducible Android fixture that verifies typed-vector removal by
  full-mode R8 and unused-resource removal by the Android resource shrinker.
- Deterministic catalog/namespace/static-font/vector generators and strict
  pinned font and provenance verification.
- Android, JVM, JS, Wasm, and iOS publication targets, an interactive
  multiplatform sample, CI/release automation, and open-source project
  governance.

No versioned release has been published yet.
