# Combined-effects stress scenario — 2026-09-12

The new `scenario=stress` runs through the current icon API. It combines four
changing font axes with real font/icon resizing, color, opacity, scale, rotation,
and translation. No renderer API changes were made.

This capture predates the deferred-tint overload and bounded Android variation
cache described in the [fixed-size draw follow-up](DRAW_RESULTS.md). Its recorded
APK hashes and measurements are retained as historical results.

The runtime-axes sample also has a **Combined effects → Run/Stop** card: a smaller
4×4 grid of varied icons, stopped by default. The benchmark is the controlled
performance workload described below.

## Workload

The 100-icon benchmark uses the same Favorite glyph in ten staggered rows. Each
row shares a phase; rows have different phases. The shared-family baseline
resolves one family per row, so it retains useful sharing while exercising ten
simultaneous font styles.

| Property | Range |
| --- | --- |
| Weight / fill / grade / optical size | `100–700` / `0–1` / `−50–200` / `20–48` |
| Font/icon size | 18–24 dp for 100 icons; 96–128 dp for one |
| Tint | `#1B4965` ↔ `#8E244D` |
| Layer opacity / scale | `0.65–1` / `0.9–1.1` |
| Rotation | −15° to +15° |
| Translation | X/Y waves, amplitude 6% of the base size |

Each icon occupies a fixed cell 1.5 times its base size: 36 dp for the 100-icon
grid. The transformed glyphs fit inside the cells and all 100 icons remain on
screen. Different phase offsets drive the effects; they do not all reach their
extremes simultaneously.

## Pixel 6a results

Android 17/API 37, 60 Hz, 420 dpi; five two-second runs per case, full AOT
compilation, minified/non-debuggable/profileable target. No benchmark checks
were suppressed. The device was charging and clocks were not locked. A checkpoint
during the broader stress session reported mild thermal status 1, returning to 0
afterward. The final benchmark recorded no thermal-throttle sleep, but these
conditions still limit interpretation of small timing differences.

| Icons | Renderer | CPU frame P50 | CPU frame P99 | Positive deadline overruns / samples |
| --- | --- | --- | --- | --- |
| 1 | Value overload | 9.054 ms | 12.126 ms | 0 / 603 |
| 1 | Shared family | 8.848 ms | 11.649 ms | 0 / 601 |
| 1 | Native producer | 8.590 ms | 12.254 ms | 3 / 602 |
| 100 | Value overload | 39.386 ms | 65.735 ms | 447 / 447 |
| 100 | Shared family | 39.399 ms | 60.848 ms | 460 / 460 |
| 100 | Native producer | 37.166 ms | 57.652 ms | 508 / 508 |

**The 100-icon case overloads every renderer:** all captured frames exceed their
deadlines. Native has the lowest median in this capture, but the difference is
much smaller than in the axes-only case. CPU frame duration is elapsed frame
production latency, not the reciprocal of displayed FPS.

These numbers should not be used as an isolated measure of “adding a color”:
the stress case also adds ten distinct styles, variable sizing and layers, and
uses a different base size from the earlier axes-only benchmark.

## What composition and layout do

The font-settings producer defers only axis reads. `tint` and `size` are existing
value parameters, so they cause caller recomposition; changing actual size also
requires measurement and placement. Layer transforms use `graphicsLayer { ... }`.

All three stress paths recompose and measure the 100 icons on each issued update.
The shared path also recomposes its ten family-provider rows. Native still avoids
the inner Compose text measurements: zero versus 100 per update in the text paths.
Median measured work per frame was:

| 100-icon renderer | Glyph draw | Layer properties | Inner Compose text measurement |
| --- | --- | --- | --- |
| Value overload | 1.481 ms | 0.724 ms | 14.625 ms |
| Shared family | 1.459 ms | 0.709 ms | 14.454 ms |
| Native producer | 11.881 ms | 0.591 ms | 0 ms |

Native font metrics/shaping run during drawing, so its draw duration must be
interpreted alongside the text paths' measurement work.

## Verification and recording

The content probe is **inside** the graphics layer. A probe outside it counts
cached parent recordings and can miss actual content updates. Layer-property
evaluations are recorded independently. In the final 30 measured iterations,
every issued update reached both the first icon's content and its layer properties.
The time-based driver can issue fewer updates under load; that is separate from
silently losing an update already issued.

Pixel screenshots at four poses verify that every font axis, size, tint and
transform input changes. They also verify visible colored output, changing measured
dimensions, and baseline/native geometry and color agreement. The fixed capture
cell was 504×504 px while the sampled icon dimensions changed between 264×264 and
324×324 px. An additional emulator check covered all three renderer modes.

The sample compiled for JVM and Android. An axes-only native 100-icon emulator
smoke test still observed every update with zero animation-driven composition
and layout counts. The original axes-only checks remain strict; the stress
scenario reports its legitimate composition/layout costs.

## Run and inspect

With JDK 21 and `ANDROID_SERIAL` set to the device:

```sh
./gradlew :benchmarks:animated-font:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hlcaptain.symbols.benchmark.animatedfont.AnimatedFontBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.scenario=stress
```

See the [scenario instructions](README.md#combined-effects-stress-scenario) and
[capture-and-analysis recipe](README.md#preserve-and-analyze-a-new-run). Select
`scenario=stress` for timing and colored geometry, preserve each run's output, then
use the analyzer's `--stress-geometry` option.

Raw JSON, traces, screenshots and generated reports remain local and are excluded
from version control. Only the corrected probe's historical measurements are
summarized here; a current checkout measures the current renderer and harness.
The phone's screen-awake setting was restored to its original value, 0.
