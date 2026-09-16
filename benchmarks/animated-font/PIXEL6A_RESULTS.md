# Pixel 6a animated font measurements — 2026-09-12

On this physical Pixel 6a, native rendering reduced the 100-icon median CPU frame
time by **24.7%** versus the existing value overload and **24.4%** versus the
shared-FontFamily baseline. It also improved the 100-icon P99 and recorded no
positive frame-deadline overruns in that case. Single-icon median improvement
was smaller, about 2.7%.

## Device and protocol

- Google Pixel 6a, Android 17/API 37, build `CP2A.260705.006`.
- 1080×2400, 420 dpi, 60 Hz; eight CPU cores, with clocks left unlocked.
- Connected to power: battery 27% before testing and 33% afterward; battery
  temperature 33.2°C → 35.6°C. Global thermal status was 0 before and after;
  Macrobenchmark recorded zero thermal-throttle sleep for every case.
- Same target renderer APK as the emulator capture, minified, non-debuggable,
  profileable, with `CompilationMode.Full`. No benchmark errors were suppressed.
- Five two-second runs for each renderer/count pair, with all four axes animated
  together. Font loading/startup was outside the measured block. Geometry and
  screenshots were collected separately.

The temporary screen-awake setting was restored to its original value, 0.
The phone and emulator differ in hardware, Android version and density; compare
renderers within each capture, rather than treating their absolute timings as
a controlled hardware comparison.

## Frame times

| Icons | Renderer | CPU P50 | CPU P99 | Positive deadline overruns / sampled frames |
| --- | --- | --- | --- | --- |
| 1 | Value overload | 9.342 ms | 12.768 ms | 1 / 600 |
| 1 | Shared family | 9.336 ms | 13.181 ms | 3 / 600 |
| 1 | Native producer | **9.089 ms** | 13.045 ms | 2 / 599 |
| 100 | Value overload | 12.311 ms | 15.979 ms | 17 / 594 |
| 100 | Shared family | 12.253 ms | 21.315 ms | 17 / 599 |
| 100 | Native producer | **9.266 ms** | **11.735 ms** | **0 / 600** |

Every issued animation update was prepared and drawn in every iteration. All
native host/icon recomposition, outer measurement, and placement counters were
zero during animation. Thus the reduction does not come from dropping updates.

For 100 icons, median native draw work was 5.353 ms per measured frame. The value
baseline used 1.749 ms for drawing plus 5.500 ms for inner Compose text measurement;
the shared-family baseline used 1.875 ms plus 5.644 ms. Native shaping and font
metrics run inside drawing, so comparing draw time alone would misrepresent the
work. Both text baselines already kept the outer layout stable, but still performed
roughly 12,000 inner text measurements per two-second iteration; native performed
zero Compose text measurements.

## Geometry and the physical-device test adjustment

The 128 dp icon stayed **336×336 px** at position `(42,42)` for all tested styles.
Measured ink positions, dimensions and dark-pixel counts matched the text baseline
at all 15 axis/combined-axis endpoints and midpoints.

| Weight | Visible ink width × height | Dark pixels |
| --- | --- | --- |
| 100 | 244×207 px | 7,070 |
| 400 | 280×247 px | 21,596 |
| 700 | 304×270 px | 34,761 |

`FILL=0 → 1` retained a 280×247 px ink rectangle while increasing dark pixels
from 21,596 to 49,258. The 100-icon timing grid used 32 dp / 84×84 px squares.

The initial unsupported-axis round-trip test failed its exact `Bitmap.sameAs`
assertion. The heavy glyph had restored correctly: only 13 of 112,896 pixels
differed, exclusively at antialiased edges, by at most three gray levels. Valid
baseline/native control captures showed the same variation. The check now requires
an identical ink mask (`RGB < 200`) and maximum RGB-channel error of three; it also
requires the intermediate unsupported setting to change the ink mask. Both geometry
tests passed on rerun. **The production renderer was unchanged.**

## Evidence and reproduction

Six macrobenchmark cases / 30 measured iterations and both final geometry tests
passed. See the [capture-and-analysis recipe](README.md#preserve-and-analyze-a-new-run).
With JDK 21 and a single target device connected,
rerun the six timing cases using:

```sh
./gradlew :benchmarks:animated-font:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hlcaptain.symbols.benchmark.animatedfont.AnimatedFontBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.axes=all \
  -Pandroid.testInstrumentationRunnerArguments.counts=1,100 \
  -Pandroid.testInstrumentationRunnerArguments.renderers=native,shared,baseline \
  -Pandroid.testInstrumentationRunnerArguments.iterations=5
```

Copy the additional-output directory before the geometry run. Use
`AnimatedFontGeometryTest` and the analyzer's `--geometry` option with those local
captures. Raw JSON, traces, screenshots, failure evidence and device/APK records
are excluded from version control. This document retains the historical findings;
running the current checkout measures the current renderer.
The earlier [emulator results](RESULTS.md) remain available for comparison.
