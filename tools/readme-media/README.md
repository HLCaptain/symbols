# README media

The README APNGs are real headless Compose renders, using the published Symbols
2.1.0 artifacts. No icon outlines or spring curves are approximated. The capture
uses the repository's Kotlin, Compose and Material3 versions from the catalog;
it does not build the library's full generated vector packs or sample apps.

From the repository root, with JDK 21, FFmpeg, and Pillow 12.3.0 available:

```sh
./gradlew -p tools/readme-media run --max-workers=2
python3 tools/readme-media/encode.py
```

Pass `icons-comparison` to the encoder to update only that animation and its still.
Pillow only inspects pixels and timing; FFmpeg encodes the original Compose frames.

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

Both compact layouts use black text on white, without card boxes,
subtitles, or footer labels. The comparison retains its main titles and icon
names: `Home`, `AccountTree`, `Favorite`, and `VolumeOff`. A Rounded
`KeyboardDoubleArrowRight` glyph at weight 700 sits between the two sides. Material Icons Extended 1.7.3 Rounded vector shapes stay fixed and black
on the left through standard `Icon`.
Labels use the legacy vector's `name.substringAfterLast('.')`, matching the
rendered reference names.
On the right, Symbols' live Rounded font animates fill, weight, optical size,
and grade at the same requested icon size and tint. Only Symbols changes tint.
Each axis transition advances the unwrapped hue
by 15 degrees through the same expressive spatial motion scheme, using HSV
saturation `1` and value `0.6`. Color and shape stop changing during the settled
holds; text and the arrow stay black. This tint keeps every hue at least 3:1
against white. The variable-font demo keeps its black glyphs. The comparison begins and ends at
`FILL=1, wght=400, GRAD=0, opsz=24` and its initial red tint.

Starting filled at the default axes, the comparison runs these three segments
in order. After each of the 12 axis steps, it toggles fill before advancing to
the next axis step. Each transition changes exactly one axis; the other axes
retain their values. The final fill toggle returns to `FILL=1` at the defaults.

| Segment | Axis targets |
| --- | --- |
| Weight | `400 → 100 → 400 → 700 → 400` |
| Optical size | `24 → 20 → 24 → 48 → 24` |
| Grade | `0 → -50 → 0 → 200 → 0` |

The 25 targets produce 24 transitions: 12 fill changes and four changes each
to weight, optical size, and grade. The initial state holds for 1.5 seconds.
Every transition waits for the axis spring, hue spring, and arrow motion to
finish, then holds the complete settled frame for 1.5 seconds. The 24 color
steps complete one rainbow cycle and return to the initial red. The comparison
lasts 62.3 seconds: 1,869 exported frames at 30 fps.

The arrow moves 8 dp right in 350 ms using `FastOutSlowInEasing`, then returns
left in 650 ms using `CubicBezierEasing(0.42, 0, 0.58, 1)`. It stays on the same
horizontal baseline and rests during every settled hold. The one-second motion
uses Compose `Animatable` keyframes driven by the same capture clock.

`states.csv` records each target's start (`frame`), completion-aligned hold
start (`settledFrame`), and end (`endFrame`) as native 60 Hz frame indices.
The encoder verifies that all 25 holds span 90 native frames (1.5 seconds),
the hue returns to red, and all 24 axis transitions are visible.
It checks pixel-static geometry during holds against additional black Compose
captures, since Skia's font antialiasing changes with tint. The entire legacy
half stays pixel-identical throughout. Checks require all four Symbols glyphs
to reach the expected color, tint to animate during transitions, complete colored
frames to stay pixel-static during holds, and the arrow to move only horizontally
within 8 pixels and return to rest. Text and background pixels stay unchanged.

Capture glyphs use 1.25-em drawing boxes, adding `size / 8` on each edge for the
pinned Rounded font's 1.2-em line height. Font size stays unchanged; this adjusts
only the capture layout, not the released renderer. The clipping guard rejected
the original square bounds. The padded capture then matched a reference with
another 20 pixels on each edge for every comparison and variable-demo native
frame, verifying the icons and moving arrow against roomier drawing bounds.

The variable-font image has four columns from one font, isolating fill, weight,
grade, and optical size. Weight, grade, and optical size follow
medium → small → medium → large → medium. Fill alternates 0 → 1 → 0 → 1 → 0.
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
first frame. The variable-font demo remains eight seconds (240 exported frames).
Each animation plays twice and ends on its initial state. The encoder verifies
motion in every icon/axis, static legacy shapes, seamless color and shape loop
endpoints, decoded duration and repeat count. Comparison checks cover segment order,
single-axis transitions, spring completion, the arrow excursion, and the settled
holds. Every decoded frame must match its native capture exactly. Frames and
Gradle outputs remain under ignored `build/`. Platform fonts may change text
rasterization across operating systems; regenerate both assets on the same host.
