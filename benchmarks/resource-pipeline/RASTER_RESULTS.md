# Outlined resource geometry: Skiko upgrade

Measured on 2026-09-28. The upgraded pack **B corrects filled regions present in
baseline A**. Preserving A's pixels would preserve those errors. No production
generator or public model API change was needed.

Both builds read the same 1,303,612-byte static Outlined TTF, SHA-256
`f73c7bcb7dbeab41fe741cbc3dd73f8fd3d0569c386ca82ceee60ccf5c1fbc52`.
A uses Skiko 0.9.22.2; B uses 0.150.1. Of 3,802 native XML drawables, 3,787 change
textually. XML/path-operation differences alone therefore prompted this rendering
check; they were not treated as visual regressions.

## Independent reference and controls

FontTools 4.66.0 reads the static TTF directly through `SVGPathPen` and
`TransformPen`. Units per em are 960; the transform is
`(0.025, 0, 0, -0.025, 0, 24)`, matching the library's 24-unit viewport, origin 0,
and baseline 24. Every unique codepoint is compared, with aliases deduplicated.

All comparisons below use one Skiko 0.150.1 renderer for **both** inputs, at 48
and 96 pixels. Error is mean absolute alpha difference, normalized by the whole
canvas's maximum alpha. The maximum shown is the worst single glyph, not an
average over the catalog.

| Comparison | Maximum error at 48 px | Maximum error at 96 px | Glyphs above 0.5%, 48/96 px |
| --- | ---: | ---: | ---: |
| FontTools reference → A | 8.7076% | 8.7040% | 36 / 36 |
| FontTools reference → B | 0.3598% | 0.2271% | 0 / 0 |
| Raw old-Skia paths → A XML | 0.0977% | 0.0400% | 0 / 0 |
| Raw new-Skia paths → B XML | 0.0953% | 0.0400% | 0 / 0 |

The raw-path control captured `Font.getPath()` from each native Skia version,
preserving its fill rule in serialized paths, then rendered those paths through
the same comparison engine after the same viewport transform. The old raw paths
already contain the incorrect filled regions; the XML extractor did not introduce
the large differences. The new raw paths and generated B XML agree closely with
the independent font geometry.

Across all 4,102 catalog names, old Skia reported 38 even-odd paths and 4,064
nonzero paths; new Skia reported 4,102 nonzero paths. The worst changed glyphs,
including `nest_thermostat_gen_3`, `target`, and `remove_red_eye`, are nonzero in
both engines. Ignoring a changed fill-rule label does not explain their filled
centers. This study makes no claim about every external font's fill modes.

## A/B observations

| Metric | 48 px | 96 px |
| --- | ---: | ---: |
| Exact pixel matches | 3,366 / 3,802 | 3,349 / 3,802 |
| Glyphs with any changed pixels | 436 | 453 |
| Glyphs with alpha delta greater than 128 | 38 | 37 |
| Glyphs with changed nontransparent bounds | 4 | 5 |
| Maximum changed pixels in one glyph | 261 | 948 |
| Maximum alpha delta | 255 | 255 |

White-background review of the worst cases confirms the reference geometry:
`nest_thermostat_gen_3` and `target` have hollow centers in FontTools and B,
while A fills those centers. Smaller remaining reference/B differences are edge
coverage changes. B is not pixel-identical to FontTools: its maximum alpha delta
is 135 at 48 px and 98 at 96 px, with worst whole-canvas error below 0.36%.

The explicit acceptance guard is 0.5% mean absolute alpha error for **every
glyph at both sizes**. B passes; the same executable guard rejects A as a
negative control. This is a bound for this pinned outlined dataset and these
sizes, not a promise of exact pixels, identical rendering on every platform, or
equivalence of layouts, tint, mirroring, and runtime font rendering.

## Reproduction and retained evidence

Use [the standalone raster check](raster/README.md) with the published native
Outlined AARs. It emits compact measurements and local worst-case PNGs; it does
not modify sources, generate a second renderer implementation, or require a new
testing framework. [Compact numeric evidence](raster-results.json) records the
archive/font hashes and summary metrics. Raw paths, full per-glyph diagnostics,
generated reference archives, and PNGs remain local.

## Android device check

The signed, R8-shrunk B fixtures were also checked on the Pixel 6a (API 37) on
2026-09-28. Native XML and Compose XML matched the typed-vector reference across
four catalog positions / 192 complete cells per backend, including thermostat,
highlighter, developer-board and no-luggage cases. The largest cell mean RGB
difference was **0.052%**, within the documented 0.5% bound. Native and Compose
XML produced identical compared pixels. The corrected hollow centers are visible
on the physical device. See [pixel-icons.json](pixel-icons.json) and the
[reproduction instructions](README.md#pixel-rendering-check).
