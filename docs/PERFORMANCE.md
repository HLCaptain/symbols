# Performance and size

Symbols is optimized around an explicit choice: use a variable font when an
application wants many icons or live axes, and add a static vector pack only when
an `ImageVector` is the better integration.

## Cost model

The catalog is generated ahead of time. A named property returns an inline
integer handle. The name and codepoint arrays initialize lazily only when their
data is requested; direct property access alone does not build a runtime map.
`fromName` and `aliases` use binary search over generated sorted arrays.

A style artifact carries exactly one variable font:

| Style | Bundled TTF bytes |
| --- | ---: |
| Outlined | 10,178,540 |
| Rounded | 14,586,584 |
| Sharp | 8,434,940 |

These are source artifact sizes, not a promise about a final APK/IPA/JS download.
Packaging, compression, target format, minification, and store processing change
the delivered size. Measure the release artifact for the target that matters.

The optional vector packs include a path for all 3,802 unique codepoints at one
axis position. A vector is parsed and built on first access and then cached;
aliases reuse the codepoint cache. They are intentionally separate artifacts so
font users do not pay this source/binary/build cost.

## Rendering many icons

For a single icon, the convenience overload is appropriate:

```kotlin
MaterialSymbolIcon(
    MaterialSymbols.Home,
    MaterialSymbolsOutlined,
    contentDescription = null,
)
```

For a collection, share one family:

```kotlin
val axes = MaterialSymbolAxes(weight = 500)
val family = rememberMaterialSymbolFontFamily(MaterialSymbolsOutlined, axes)

symbols.forEach { symbol ->
    MaterialSymbolIcon(
        symbol = symbol,
        fontFamily = family,
        contentDescription = null,
        axes = axes,
    )
}
```

Keep the `(style, axes)` pair stable across recompositions. Searching
`MaterialSymbols.all` is suitable for an icon picker; a hot application path
should retain its filtered result rather than scanning all names on every frame.

## Choosing an access mode

Choose a font style when:

- the application uses many different symbols;
- fill, weight, grade, or optical size changes;
- axes are animated; or
- one shared font resource is preferable to many vector objects.

Choose a vector pack when:

- an API specifically accepts `ImageVector`;
- Android API 21–25 must be supported;
- only the documented default-axis appearance is needed; or
- the application accepts the full static vector pack in its dependency graph.

Do not add both forms by habit. Inspect the dependency graph and release output.
The generic vector dispatcher references every generated chunk, so consumers
should assume the full selected vector pack is retained. Only claim shrinker
savings after measuring the actual release artifact.

## Reproducible measurement

Performance claims should include the commit, target, release/debug mode, JDK,
Gradle/Kotlin/Compose versions, device/runtime, warmup, and measurement tool.
Useful repository comparisons are:

1. an empty Compose app;
2. the app plus `material-core`;
3. one font style with one icon and with hundreds of distinct icons;
4. one vector pack with the same accessed icon set; and
5. all three styles only when that reflects a real product.

Record at least:

- clean and warm compilation time;
- published module and final application size;
- first-icon and warm-icon render time;
- allocation count/bytes for first and repeated access; and
- memory retained after a representative icon screen closes.

This repository does not publish synthetic benchmark numbers as guarantees.
The architecture, exact input sizes, and verification commands are provided so
consumers can measure their own release configuration.
