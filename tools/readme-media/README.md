# README media

The README APNGs are real headless Compose renders, using the published Symbols
2.1.0 artifacts. No icon outlines or spring curves are approximated. The capture
uses the repository's Kotlin, Compose and Material3 versions from the catalog;
it does not build the library's full generated vector packs or sample apps.

From the repository root, with JDK 21 and Pillow 12.3.0 available:

```sh
./gradlew -p tools/readme-media run --max-workers=2
python3 tools/readme-media/encode.py
```

Install the encoder dependency in a virtual environment if necessary:

```sh
python3 -m venv tools/readme-media/build/venv
tools/readme-media/build/venv/bin/pip install Pillow==12.3.0
tools/readme-media/build/venv/bin/python tools/readme-media/encode.py
```

`ImageComposeScene` drives the actual Compose animation clock at 60 Hz; every
second frame is exported for 30 fps media. `MaterialExpressiveTheme` supplies
`MaterialTheme.motionScheme.defaultSpatialSpec<Float>()` to `animateFloatAsState`.
Font settings are read inside `SymbolFontIcon`'s producer. Expressive springs may
overshoot, so each coordinate is clamped to its font's declared range. Capture
waits for visible glyphs before recording; it does not assume a font-loading delay.

Both compact layouts use black glyphs and text on white, without card boxes,
subtitles, or footer labels. The comparison retains its main titles and icon
names, with an actual Rounded `ArrowForward` ImageVector between the two sides. Material Icons
Extended 1.7.3 Rounded vectors stay fixed on the left through standard `Icon`.
On the right, Symbols' live Rounded font animates fill, weight, optical size,
and grade at the same layout size and tint. It begins and ends at
`FILL=1, wght=400, GRAD=0, opsz=24`.

The comparison runs these three segments in order at `FILL=1`, switches to
`FILL=0`, repeats all three, then returns to `FILL=1` at the default axes.
Each transition changes exactly one axis; the other axes retain their values.

| Segment | Axis targets |
| --- | --- |
| Weight | `400 → 700 → 100 → 400` |
| Optical size | `24 → 20 → 24 → 48 → 24` |
| Grade | `0 → -50 → 0 → 200 → 0` |

The 25 targets produce 24 transitions: two fill changes, six weight changes,
eight optical-size changes, and eight grade changes. The initial state holds
for 1.5 seconds. Every transition then waits for its expressive spring to finish
and holds the settled state for another 1.5 seconds before advancing. The
measured comparison lasts approximately 52.37 seconds: 1,571 captured frames
at 30 fps.

`states.csv` records each target's start (`frame`), completion-aligned hold
start (`settledFrame`), and end (`endFrame`) as native 60 Hz frame indices.
The encoder verifies that all 25 holds span 90 native frames (1.5 seconds) with
identical pixels throughout, and that all 24 transitions are visible.

The variable-font image has four columns from one font, isolating fill, weight,
grade, and optical size. Weight, grade, and optical size follow
medium → large → medium → small → medium. Fill alternates 0 → 1 → 0 → 1 → 0.
Five targets held for 1.6 seconds each make an eight-second loop.

| Axis | Small | Medium | Large |
| --- | ---: | ---: | ---: |
| `wght` | 100 | 400 | 700 |
| `GRAD` | -50 | 0 | 200 |
| `opsz` | 20 | 24 | 48 |

Medium uses the font's default weight, grade, and optical size.
Each column centers its title, glyph, and numeric axis labels; there is no
overall title or subtitle. Optical size adjusts glyph detail within a fixed
layout size. Explanatory captions live in the README. The font snapshot is
documented in
[asset provenance](../../fonts/material/README.md); Google icon attribution and
Apache 2.0 terms are in [third-party notices](../../THIRD_PARTY_NOTICES.md).

Only the two final APNGs and their still alternatives in `docs/media` are tracked.
The `.png` extension is intentional: APNG remains a standard PNG with a readable
first frame. The variable-font demo remains eight seconds. Each animation plays
twice and ends on its initial state. The encoder verifies motion in every
icon/axis, a completely static legacy panel, seamless loop endpoints, decoded
duration and repeat count. Comparison checks also cover segment order,
single-axis transitions, spring completion, and the settled holds. Frames and
Gradle outputs remain under ignored `build/`. Platform fonts may change text
rasterization across operating systems; regenerate both assets on the same host.
