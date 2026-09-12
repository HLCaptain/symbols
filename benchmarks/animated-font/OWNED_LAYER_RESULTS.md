# Component-owned effects and core benchmark — 2026-09-12

All `SymbolFont` descriptor overloads now use the same native renderer. Existing
value calls gain that implementation without changing their arguments; producers
add deferred state reads. The `FontFamily` overload remains a Compose text adapter.

The component owns an optional effects layer and defaults it to `ModulateAlpha`:

```kotlin
SymbolFontIcon(
    codePoint = icon.codePoint,
    font = font,
    contentDescription = null,
    fontSettings = { settings.value },
    tint = { tint.value },
    graphicsLayer = {
        alpha = opacity.value
        scaleX = scale.value
        scaleY = scale.value
        rotationZ = rotation.value
    },
)
```

This is the standard `GraphicsLayerScope`; callers do not need to choose a
compositing strategy. Omitting the block creates no layer. It wraps the modified
icon, and an explicit strategy override remains available for overlapping drawing.
An independently supplied modifier layer retains its own behavior.

## Pixel 6a measurements

The 100-icon workload retains ten staggered styles, all four changing font axes,
tint, opacity, scale, rotation and translation. The fixed-size case uses 24 dp
icons in 36 dp cells; the resizing case still animates actual size from 18–24 dp.
No effect or animation range was removed.

Android 17/API 37, five two-second iterations per core case, full AOT compilation,
minified/profileable target, and no benchmark suppressions. The phone stayed at
100% charge; battery temperature rose from 30.8°C to 32.8°C. All recorded thermal
statuses were 0, with no benchmark throttle sleep. The original screen-awake
setting, 0, was restored after testing.

### Fixed size, 100 icons

| Renderer | CPU frame P50 | CPU frame P99 | Positive overruns / samples |
| --- | --- | --- | --- |
| Compose text, family per icon | 18.318 ms | 35.220 ms | 584 / 584 |
| Compose text, shared family | 18.268 ms | 34.083 ms | 585 / 585 |
| Native core, value arguments | **14.254 ms** | 26.966 ms | 59 / 595 |
| Native core, producers | **13.719 ms** | 26.413 ms | 39 / 595 |

The normal value API is about 22% below the shared-text reference's median;
producers are about 25% below it. Producers save approximately 0.534 ms over
value arguments in this workload. Some deadline overruns remain: this is an
improvement, not a guarantee of jank-free rendering.

Both public API styles record **zero inner Compose text measurements**. The value
case still recomposes its callers when they read animation values. Producers
record zero host/group/icon recompositions, outer measurements and placements.
All cases retain complete first-icon content and layer tick coverage.

The previous native fixed-size median was 37.157 ms. This update changes layer
compositing, effect sharing, tracing overhead and the component implementation;
the overall improvement cannot be credited solely to avoiding recomposition or
to one isolated change. The explicit text references use the same efficient
compositing strategy and quieter harness for the current comparison.

### Actual resizing, 100 icons

| Native core API | CPU frame P50 | CPU frame P99 | Positive overruns / samples |
| --- | --- | --- | --- |
| Value arguments | 14.911 ms | 30.415 ms | 82 / 594 |
| Producers | 14.898 ms | 30.008 ms | 97 / 597 |

Actual size changes still require caller recomposition and outer layout; both
paths perform that work while avoiding inner Compose text layout. The previously
recorded native resizing-stress median was 37.166 ms with the older layer and
harness implementation.

## Less harness work, separate diagnostics

The full effect bundle is now memoized once per row/progress value. Consumers
still read progress in their own composition, draw or layer phase, so memoization
does not hide animation changes from Compose's observer. Counters verify exactly
**ten effect calculations per update**, versus approximately 300 before.

Normal `-core` runs keep correctness counters and frame timing, but omit per-icon
custom trace sections. A separate `diagnostics=true` mode restores update,
effects, measurement, placement, drawing and layer sections. Its `-diagnostic`
names prevent those results from being mixed with core timings.

Three native diagnostic iterations measured 14.088 ms CPU frame P50. Their
per-frame trace medians were 11.102 ms glyph drawing, 0.166 ms layer properties
and 0.049 ms effect calculations. Effects can be nested inside layer/draw sections;
do not add them again to those totals. Core timing is the primary comparison.

## The traces confirm the layer bottleneck is removed

All three native diagnostic traces have 120 animation updates and:

- Zero offscreen `drawLayer` and `flush layers` sections.
- Exactly 120 Skia operation-task executions and 120 queue submissions: one per
  update, rather than roughly 100 layer tasks per update.
- Median RenderThread drawing of 2.409 ms elapsed / 2.076 ms active CPU per update.
- Median Mali worker CPU during submission of **0.066 ms/update**, down from
  **8.768 ms/update** in the previous trace analysis.

The driver-worker comparison concerns active CPU processing, not GPU execution
time. CPU and wait sections overlap and must not be added as independent costs.
The remaining glyph work is substantial: ten continuously new font styles still
need native variation/metric/shaping work.

## Cross-platform correctness and verification

Skia's cached native drawing needed explicit alpha invalidation. A private
`DrawModifierNode` now invalidates its recorded content directly when effective
owned-layer alpha changes, without writing snapshot state during layer updates.
Skia modulates native paint alpha only when the layer does not already apply
opacity through an offscreen effect. Android uses its normal RenderNode behavior.

The Skia baseline adjustment also passed exact transformed-pixel comparisons at
24, 48 and 96 px. Actual BasicText metadata baselines were 26.6, 53.2 and 105.4 px;
the regression checks compare rendered pixels, not an assumed universal baseline
rounding formula.

Validation includes:

- The full JVM regression suite, including absolute half/quarter opacity,
  restoration, parent opacity, layer removal, value/producer parity and nine
  exact transformed-image comparisons.
- JVM sample compilation, JS/Wasm/iOS arm64 compilation, and Android release lint.
- Pixel fixed-size and resizing geometry checks for all four renderers.
- Twenty fixed-size core iterations, ten resizing core iterations and three
  diagnostic iterations, with effect budgets and animation coverage verified.

## Reproduce and inspect

See the [capture-and-analysis recipe](README.md#preserve-and-analyze-a-new-run).
For the fixed-size core timing, with a single target device connected:

```sh
./gradlew \
  :benchmarks:animated-font:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hlcaptain.symbols.benchmark.animatedfont.AnimatedFontBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.scenario=draw \
  -Pandroid.testInstrumentationRunnerArguments.renderers=baseline,shared,value,native \
  -Pandroid.testInstrumentationRunnerArguments.counts=100
```

Use the repository's compatible JDK 21. Copy the additional-output directory
before another test run, then capture geometry and run the analyzer as shown in
the recipe. Add `diagnostics=true` only for a separate diagnostic run.

This document retains the curated measurements. Raw JSON, traces, screenshots,
APK hashes and generated reports remain local and are excluded from version control.
Earlier result documents describe their original workloads and implementations.
