# Outlined glyph raster validation

This standalone check compares every drawable in two archives using **the same
Skiko 0.150.1 CPU renderer** at 48 and 96 pixels. It uses the XML viewport and
nonzero/even-odd fill rule, antialiasing, and black fill on a transparent surface.
Unsupported stroke/trim features fail explicitly. It does not build the library
or use the consuming app's resource loader.

Create an independent reference with the pinned static font and FontTools from
the repository's Python environment:

```bash
python benchmarks/resource-pipeline/raster/fonttools_reference.py \
  --font fonts/material/outlined-static/composeResources/font/material_symbols_outlined_regular.ttf \
  --codepoints fonts/material/MaterialSymbols.codepoints \
  --output /tmp/symbols-fonttools-reference.zip
```

The reference contains all unique outlined codepoints, uses canonical alias
names, and records the font/catalog hashes and FontTools version. Its transform
matches the generator and built-in Python vectors: viewport/em size 24, origin
0, baseline 24; FontTools' Y-up coordinates are flipped. A variable font must be
instantiated before this check. Repeated generation produces identical ZIP bytes.

With `JAVA_HOME` pointing to JDK 21, validate a published candidate AAR:

```bash
./gradlew -p benchmarks/resource-pipeline/raster run --no-daemon \
  --args='/tmp/symbols-fonttools-reference.zip /path/to/candidate.aar /tmp/symbols-raster-candidate 0.005'
```

The last argument is an explicit maximum error fraction **per glyph at each
size**, calculated as `sum(abs(alphaReference - alphaCandidate)) / (255 * size²)`.
The measured 0.005 bound allows edge coverage differences from font-coordinate
quantization, decimal serialization, and curve rasterization. It guards large
missing or filled regions; it does not claim pixel identity or replace actual
Android rendering checks. Do not increase it merely to pass an unexplained
change. The complete drawable-name sets must match before comparison begins.

For a diagnostic A/B comparison, omit the bound:

```bash
./gradlew -p benchmarks/resource-pipeline/raster run --no-daemon \
  --args='/path/to/baseline.aar /path/to/candidate.aar /tmp/symbols-raster-ab'
```

A/B differences are observations, not an acceptance test: an older artifact can
contain incorrect geometry. Acceptance compares the candidate with the independent
font reference. The JSON records `accepted: null` for diagnostic comparisons.

The output is a compact `raster-comparison.json` with input SHA-256 values,
exact-match counts, changed pixels, alpha-delta thresholds, bounds changes, and
the twelve worst glyphs. Their 192-pixel previews use an opaque white background
for review; these previews do not affect transparent-surface measurements. Keep
PNGs and generated archives outside Git and Actions artifact storage. Commit
only reviewed summaries, such as [the recorded result](../RASTER_RESULTS.md).

The recorded study validates all 3,802 glyphs and exercises both outcomes: B
passes the bound, while baseline A is rejected for 36 glyphs at each size.
