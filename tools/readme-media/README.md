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
On the right, Symbols' live Rounded font animates fill, weight, and optical size
at the same layout size and tint. It begins and ends at
`FILL=1, wght=400, opsz=24`.

The comparison holds each target for 0.8 seconds: 19 targets make a 15.2-second
loop. Weight and optical-size transitions run first unfilled, then filled:

| Step | `FILL` | `wght` | `opsz` |
| ---: | ---: | ---: | ---: |
| 1 | 1 | 400 | 24 |
| 2 | 0 | 400 | 24 |
| 3 | 0 | 700 | 24 |
| 4 | 0 | 700 | 20 |
| 5 | 0 | 700 | 48 |
| 6 | 0 | 100 | 48 |
| 7 | 0 | 100 | 20 |
| 8 | 0 | 400 | 20 |
| 9 | 0 | 400 | 48 |
| 10 | 0 | 400 | 24 |
| 11 | 1 | 400 | 24 |
| 12 | 1 | 700 | 24 |
| 13 | 1 | 700 | 20 |
| 14 | 1 | 700 | 48 |
| 15 | 1 | 100 | 48 |
| 16 | 1 | 100 | 20 |
| 17 | 1 | 400 | 20 |
| 18 | 1 | 400 | 48 |
| 19 | 1 | 400 | 24 |

The variable-font image has four columns from one font, isolating fill, weight,
grade, and optical size. Each column centers its title, glyph, and axis label;
there is no overall title or subtitle. Optical size adjusts glyph detail within
a fixed layout size. Explanatory captions live in the README. The font snapshot is documented in
[asset provenance](../../fonts/material/README.md); Google icon attribution and
Apache 2.0 terms are in [third-party notices](../../THIRD_PARTY_NOTICES.md).

Only the two final APNGs and their still alternatives in `docs/media` are tracked.
The `.png` extension is intentional: APNG remains a standard PNG with a readable
first frame. The comparison runs for 15.2 seconds and the variable-font demo
remains eight seconds. Each animation plays twice and ends on its initial
state. The encoder verifies motion in every icon/axis, a completely static legacy
panel, seamless loop endpoints, decoded duration and repeat count. Frames and
Gradle outputs remain under ignored `build/`. Platform fonts may change text
rasterization across operating systems; regenerate both assets on the same host.
