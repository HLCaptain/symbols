# Conventions

## Kotlin visibility

Use Kotlin's implicit public visibility. Do not write the `public` modifier on
classes, functions, properties, constructors, or nested declarations.

Write `internal`, `protected`, or `private` when an API needs narrower
visibility. This convention applies equally to handwritten code, generator
templates, and checked-in generated Kotlin sources.

## Compose previews

Keep previews beside the composable they exercise. Use `@PreviewSymbolsScreen`
for compact and expanded screen sizes, and add `@PreviewScreenshotBaseline` only
when the preview is a stable Roborazzi visual contract. Do not create separate
baseline packages or duplicate production UI in screenshot-only composables.

## CI resource use

Use the configured self-hosted runners. Do not upload Actions artifacts or
enable GitHub Actions dependency caches; generated reports stay in each
workflow's local build directory.
