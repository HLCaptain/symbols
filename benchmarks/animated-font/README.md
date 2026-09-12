# Animated font rendering benchmark

Current implementation: [component-owned effects and core results](OWNED_LAYER_RESULTS.md),
including the unchanged value API, producer API, quieter harness and rendering traces.

Recorded measurements: [Pixel 6a](PIXEL6A_RESULTS.md) and
[emulator](RESULTS.md), including frame times, style-dependent ink dimensions,
and composition/layout counters.

The [combined-effects stress results](STRESS_RESULTS.md) cover simultaneous style,
size, color and transform changes on the Pixel 6a.
The [fixed-size draw results](DRAW_RESULTS.md) verify deferred tint and zero native
composition/layout work, with repeated Pixel 6a measurements and preliminary emulator timing.
The [Pixel trace analysis](TRACE_ANALYSIS.md) breaks down the remaining font,
layer and graphics-driver costs and identifies changes to test next.

The standalone Android target compares four paths:

- `baseline`: a Compose text reference, resolving its font family per icon.
- `shared`: the same text reference, sharing one resolved family per style group.
- `value`: the public `SymbolFontIcon(fontSettings = value)` call.
- `native`: the public settings/tint producer call.

Both public descriptor calls share the native core. Text references explicitly use the
legacy `FontFamily` adapter so they remain useful independent comparisons. Every path renders
Material Rounded Favorite using the same font, layout, axis formula and
wall-clock-driven triangle sweep on Compose's frame clock, using the same clock as normal
Compose animations. The font is stored uncompressed in all builds. The
benchmark APK is minified, non-debuggable, profileable and signed with the debug key.

The target requires Android 26+. Use Android 31+ for frame deadline/overrun metrics and a
physical device for representative performance. Results from an emulator measure that
emulator's CPU/rendering behavior, not a phone's jank rate.

## Run

Use the repository's compatible JDK 21, select one device with `ANDROID_SERIAL`, and run:

```sh
./gradlew :benchmarks:animated-font:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hlcaptain.symbols.benchmark.animatedfont.AnimatedFontBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.axes=all \
  -Pandroid.testInstrumentationRunnerArguments.counts=1,100
```

This focused run compares both renderers at both counts with all four axes changing
together. Five iterations of a two-second animation are captured per case, excluding
startup and initial font loading from the measured block. Both apps use full ahead-of-time
compilation (`CompilationMode.Full`). A fresh target process starts before each iteration.

Omit `axes` for the full matrix of `wght,FILL,GRAD,opsz,all`. Further optional arguments:

| Instrumentation argument | Default | Meaning |
| --- | --- | --- |
| `renderers` | `baseline,native` | Subset of `baseline,shared,value,native` |
| `axes` | `wght,FILL,GRAD,opsz,all` | Individual axis sweeps or `all` together |
| `counts` | `1,100` | One 128 dp glyph or a 10 by 10 grid of 32 dp glyphs |
| `iterations` | `5` | Repeated measurements per case (1–20) |
| `durationMs` | `2000` | Duration of each animation (100–60000 ms) |
| `scenario` | `axes` | `axes`, combined-effects `stress`, or fixed-size `draw` |
| `diagnostics` | `false` | Enable per-icon custom phase traces for a separate diagnostic run |

For the optimized 100-icon comparison, pass `renderers=shared,native` and `counts=100`.

Normal runs retain phase counters, first-icon content/layer update coverage and screenshot
checks, but omit per-icon custom trace sections. They collect frame timing and existing
Compose text-layout trace metrics. Set `diagnostics=true` for custom update, effects,
measure, placement, draw and layer trace timings. New result names end in `-core` or
`-diagnostic`; keep the two modes separate when reporting performance. Historical captures
without these suffixes remain readable by the analyzer.

For an emulator-only functional run, append
`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`.
This suppression is deliberately not enabled by default. Keep device, refresh rate,
emulator configuration, thermal/battery state and compilation mode with any reported results.

## Combined effects stress scenario

`scenario=stress` changes all four font axes together with icon/font size, tint, layer
opacity, scale, rotation and X/Y translation using the existing renderer APIs. A 100-icon
grid has ten staggered rows: icons within a row share values, while rows have different
phases. The `shared` renderer resolves one family per row; the other renderers retain
their per-icon APIs. Each row/progress pair computes its effect bundle only once, shared
by all settings, tint and layer consumers. `effectEvaluations` verifies this bound. The
one-icon case has one group. This exercises a more varied
workload than the synchronized axes-only grid.

Each icon sits in a fixed cell 1.5 times its base size. The base size is 24 dp for 100 icons
and 128 dp for one; the measured icon/font size varies between 75% and 100% of that base.
Size and tint remain value arguments, so native composition and layout activity is
expected in this scenario. The benchmark reports their actual cost without imposing the
axes-only zero-composition/zero-layout assertions.

```sh
./gradlew :benchmarks:animated-font:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hlcaptain.symbols.benchmark.animatedfont.AnimatedFontBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.scenario=stress
```

The stress defaults are `renderers=baseline,shared,value,native`, `axes=all`, `counts=1,100`,
five iterations each. Test names and counter files end in `-stress-core` by default.
Run screenshot checks separately:

```sh
./gradlew :benchmarks:animated-font:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hlcaptain.symbols.benchmark.animatedfont.AnimatedFontStressTest
```

This captures one baseline/native icon at progress 0, 0.25, 0.5 and 0.75; pass
`renderers=baseline,shared,value,native` to include every mode. Separate
`stress-geometry.json` and PNGs record actual untransformed `measuredSizes`, icon `bounds`,
fixed-cell `captureBounds`, and first-icon `effects` values including all four `effects.axes`
coordinates. Every axis and other effect must change across the four poses and match
between renderers. The foreground detector
selects any RGB channel below 230 against white and records mean RGB plus chromatic pixel
count, so a color change is verified from the screenshot as well as its input metadata.
The test verifies changing measured size and every effect, visible colored ink, stable
capture cells, identical inputs/layout across renderers, and comparable ink positions
(within two pixels) and mean RGB (within three levels).

Use the [capture-and-analysis recipe](#preserve-and-analyze-a-new-run) to preserve
the timing output before collecting geometry. Select `scenario=stress` and the
analyzer's `--stress-geometry` option.

The analyzer retains separate scenario labels, includes group/host/icon composition and
layout counters, and adds the measured colors, sizes and transforms. The content draw probe
runs inside the graphics layer; diagnostic `SymbolBenchmark.layer` sections and
`layerUpdates`/`layerTicks`/
`distinctLayerUpdates` track layer-property updates separately. A cached parent draw can
be reused while its child layer changes, so parent draw frequency is not evidence of lost
animation updates. Reports show content and layer update coverage separately; fewer
displayed updates cannot be treated as a rendering speedup.

## Fixed-size draw scenario

`scenario=draw` retains the staggered rows, all four changing font axes, animated tint,
opacity, scale, rotation and translation from `stress`, while keeping the actual icon/font
size fixed at its base size. Native rendering receives tint through Compose's
`ColorProducer` and font settings through their producer; both read animation state during
drawing. Layer properties read state in the component-owned `graphicsLayer` block; the
component selects `ModulateAlpha` by default. The text references explicitly use the same
strategy and layer order for a fair comparison. The `value` mode exercises the same native
core with value arguments, retaining caller recomposition but avoiding text layout.

```sh
./gradlew :benchmarks:animated-font:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hlcaptain.symbols.benchmark.animatedfont.AnimatedFontBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.scenario=draw

./gradlew :benchmarks:animated-font:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hlcaptain.symbols.benchmark.animatedfont.AnimatedFontStressTest \
  -Pandroid.testInstrumentationRunnerArguments.scenario=draw
```

Timing defaults are eight cases (four renderers, one and 100 icons), five iterations
each. `-draw-core` names and counter files are separate from prior scenarios. The reused
colored-geometry test writes `draw-geometry.json` and `*-draw-*.png`: it asserts fixed
untransformed measured sizes, changing visible colors/axes/layer effects, baseline/native
visual parity, and zero native host/group/icon compositions, measurements and placements.

The [capture-and-analysis recipe](#preserve-and-analyze-a-new-run) runs both commands,
copies their output before another run can replace it, and passes those local files
to the analyzer with `--draw-geometry`.

The analyzer requires complete content and layer tick coverage for draw timings and zero
native composition/layout work. This separates the intended elimination of those phases
from native shaping, glyph drawing and layer updates, whose frame and trace costs remain
measured (custom trace costs only in diagnostic mode). Layer scaling changes visible size
without changing measured font/icon size. Effects must evaluate no more than once per
row/update, and the value path must not produce inner Compose text measurements.

## Metrics and geometry

`FrameTimingMetric` records actual CPU frame durations and, where available, deadline
overruns. A zero dropped-frame count is not a zero rendering cost. Diagnostic custom sections
separately report the sum of `SymbolBenchmark.update`, `.measure`, `.place` and `.draw`
work. Upstream Compose trace sections `TextStringSimpleNode::measure` and
`TextLayout:initLayout` additionally measure the text node's own measurement and Android
text-layout construction when those sections occur. These names were verified in the
Compose 1.11.1/Android 1.11.4 sources; they are not assumed to be stable across upgrades.
Each `*-counts.json` records updates, caller/host compositions, outer measurement,
placement and draw counts, plus actual screen-space icon bounds for every iteration.
Compare per-frame timings and normalize summed work by frame/update counts: slower
renderers intentionally skip coordinates in the same elapsed-time sweep.
`drawnTicks`, `drawnProgress` and `distinctDrawnUpdates` record the first icon's settings
when prepared and then drawn, without extra snapshot reads in the draw instrumentation.
Check these against `updates`: lower per-frame cost is not sufficient evidence of an
improvement if one renderer displays substantially fewer of the animation updates.

The measurement/placement counters observe the **outer icon modifier**, not `BasicText`'s
private paragraph layout or every child measure. Native shaping and typeface work still
belongs to CPU frame/draw cost even when Compose's outer layout stays unchanged. A missing
trace metric can mean no matching sections; the counters explicitly distinguish zero work.
The same counters and selected trace mode apply to every renderer.

Run screenshot/geometry checks separately, without screenshot readback in the timing run:

```sh
./gradlew :benchmarks:animated-font:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hlcaptain.symbols.benchmark.animatedfont.AnimatedFontGeometryTest
```

The geometry test updates an existing icon at each axis's minimum, midpoint and maximum.
It asserts unchanged outer position/width/height, no caller recompositions or outer
remeasurements/placements for the native producer, and a nonempty glyph. `geometry.json`
records each crop's visible ink rectangle and dark-pixel count (`RGB < 200`). The count
detects fill changes that leave the bounding rectangle unchanged. Full-screen PNGs accompany
the report. Ink coordinates are relative to the measured icon; outer bounds are screen
pixels. The test reports the measurements for comparison rather than assuming font axes
must preserve ink bounds. When `baseline` is included, it also verifies identical outer
bounds and ink width/height across renderers, permits at most one pixel of ink-position
difference, and requires dark-pixel coverage within 1%.

The same test class also exercises `wght=700 → ZZZZ=1 → wght=700` on one native renderer,
requiring the unsupported coordinate to restore base-font appearance and the final valid
coordinate to reproduce the initial ink mask, allowing at most three RGB levels of
antialiasing variation observed in valid Pixel 6a control captures. Its three
`unsupported-axis-*.png` artifacts and `unsupported-axis-comparison.json` are separate
from the regular geometry matrix. The target's test-only
`axis=unsupported` scenario is excluded from every default animation/geometry axis list.

## Preserve and analyze a new run

The repository contains code and curated result documents. Generated JSON, Perfetto traces,
screenshots, APKs and generated reports stay local and are excluded from version control.
A clean checkout creates these inputs by running the tests; the analyzer does not depend
on archived captures. Historical result documents describe their recorded implementations,
while a new run measures the current code.

Use JDK 21 and one connected target device. From the repository root, this complete recipe
captures fixed-size timing and geometry, copying each output tree **before the next test
run can replace it**:

```sh
(
set -eu
benchmark_module=benchmarks/animated-font
benchmark_outputs="$benchmark_module/build/outputs/connected_android_test_additional_output/benchmark/connected"
mkdir -p "$benchmark_module/build/measurements"
benchmark_capture=$(mktemp -d "$PWD/$benchmark_module/build/measurements/run-XXXXXX")

./gradlew :benchmarks:animated-font:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hlcaptain.symbols.benchmark.animatedfont.AnimatedFontBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.scenario=draw \
  -Pandroid.testInstrumentationRunnerArguments.renderers=baseline,shared,value,native \
  -Pandroid.testInstrumentationRunnerArguments.counts=100
cp -R "$benchmark_outputs" "$benchmark_capture/timing"

./gradlew :benchmarks:animated-font:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hlcaptain.symbols.benchmark.animatedfont.AnimatedFontStressTest \
  -Pandroid.testInstrumentationRunnerArguments.scenario=draw \
  -Pandroid.testInstrumentationRunnerArguments.renderers=baseline,shared,value,native
cp -R "$benchmark_outputs" "$benchmark_capture/geometry"

python3 "$benchmark_module/analyze.py" "$benchmark_capture/timing" \
  --draw-geometry "$benchmark_capture/geometry" \
  --output "$benchmark_capture/summary"
)
```

Stop if a test command fails; retain its outputs for diagnosis rather than presenting them
as a successful capture. For a separate diagnostic timing study, add
`-Pandroid.testInstrumentationRunnerArguments.diagnostics=true` and use a new capture
directory. For resizing, select `scenario=stress` in both test commands and analyze with
`--stress-geometry`. For axes-only timing, select `scenario=axes` and `axes=all`, run
`AnimatedFontGeometryTest`, and analyze with `--geometry`.

The timing input must contain exactly one `*-benchmarkData.json` with its sibling
`*-counts.json` files. The script writes `summary.md` and `summary.json` in the selected
output directory, preserves device context and recorded percentiles, normalizes trace sums
by each iteration's actual frame count, and verifies animation coverage. Keep devices and
core/diagnostic modes in separate capture directories. Geometry inputs add ink/color
comparisons and repeat the applicable composition/layout assertions.

For direct `am instrument` runs, set `additionalTestOutputDir` or retrieve custom files
from the test APK's external files directory before running another test.

## Target app controls

Launch `io.github.hlcaptain.symbols.benchmark.animatedfont/.BenchmarkActivity` with intent
extras `renderer`, `axis`, `count`, `size_dp`, and `progress` (0–1). Send package-scoped
broadcast `io.github.hlcaptain.symbols.benchmark.animatedfont.START` with integer
`duration_ms` to animate, or `.SNAPSHOT` with float `progress` to change one static value.
After pending frames settle, an Android status view exposes `stats:<JSON>` as its content
description. Counters reset for each broadcast; they never update Compose state or a live
label during the animation.

Setup and metric definitions follow the official Android
[Macrobenchmark guide](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
and [metrics reference](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics).
