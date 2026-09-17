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
Ordinary CI uses read-only tokens. Roborazzi may publish generated PNG reports
only to temporary `roborazzi-pr-<number>` branches and one PR comment for trusted
same-repository PRs; never add images to development branches. Fork PR checks stay
read-only. Preserve the human screenshot approval gate and delete report branches
when PRs close.
