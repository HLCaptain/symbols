# Conventions

## Kotlin visibility

Use Kotlin's implicit public visibility. Do not write the `public` modifier on
classes, functions, properties, constructors, or nested declarations.

Write `internal`, `protected`, or `private` when an API needs narrower
visibility. This convention applies equally to handwritten code, generator
templates, and checked-in generated Kotlin sources.

## Gradle references

Use `libs`, `libs.plugins`, and generated `projects` accessors in the
repository's Kotlin Gradle scripts. Do not repeat plugin IDs, dependency
coordinates, or catalog-backed versions as strings. Keep string notation only
when Gradle has no static accessor, such as a host-derived artifact name or a
settings-plugin declaration.

## Compose previews

Keep previews beside the composable they exercise. Use `@PreviewSymbolsScreen`
for compact and expanded screen sizes, and add `@PreviewScreenshotBaseline` only
when the preview is a stable Roborazzi visual contract. Do not create separate
baseline packages or duplicate production UI in screenshot-only composables.

## CI resource use

Use the configured standard GitHub-hosted runners. Do not add self-hosted runner
labels, upload Actions artifacts, or enable GitHub Actions dependency caches.
Generated reports remain in the ephemeral job workspace. Keep PR tokens read-only
and preserve the manual screenshot review gate; reproduce visual output locally
when review is needed.
