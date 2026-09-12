# Fixed-size animated axes, tint and transforms — 2026-09-12

This is the earlier externally owned layer capture. The
[component-owned layer follow-up](OWNED_LAYER_RESULTS.md) records the current core
implementation and optimized harness; the original measurements below remain intact.

`scenario=draw` keeps actual icon/font size fixed while animating all four font
axes, tint, opacity, scale, rotation and translation. The 100-icon grid retains
the stress scenario's ten staggered style groups and 36 dp cells, with 24 dp icons.
Native settings and tint are read during drawing; transforms are read inside
`graphicsLayer`. The value/shared text paths still recompose for changing font
families. All paths now avoid outer measurement and placement during animation.

The native overload accepts Compose's existing
[`ColorProducer`](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/ColorProducer):

```kotlin
SymbolFontIcon(
    codePoint = icon.codePoint,
    font = font,
    contentDescription = null,
    fontSettings = { animatedSettings.value },
    tint = { animatedTint.value },
    size = 24.dp,
)
```

Existing `tint = Color(...)` calls remain supported. Actual size changes still
require layout; visual scaling can use `Modifier.graphicsLayer { scaleX = ...;
scaleY = ... }` with animation state read inside the lambda.

## Pixel 6a measurements

Android 17/API 37, five two-second iterations per case, full AOT compilation,
minified/non-debuggable/profileable target, and no benchmark checks suppressed.
The phone was charging at 100%; battery temperature rose from 31.6°C to 33.4°C.
All recorded thermal-status checkpoints were 0 and Macrobenchmark recorded no
thermal-throttle sleep. Clocks were not locked.

| Icons | Renderer | CPU frame P50 | CPU frame P99 | Positive deadline overruns / samples |
| --- | --- | --- | --- | --- |
| 1 | Value overload | 9.057 ms | 12.433 ms | 1 / 600 |
| 1 | Shared family | 9.275 ms | 12.304 ms | 0 / 600 |
| 1 | Native producers | 9.020 ms | 12.116 ms | 0 / 599 |
| 100 | Value overload | 36.935 ms | 61.714 ms | 501 / 501 |
| 100 | Shared family | 36.585 ms | 59.837 ms | 503 / 503 |
| 100 | Native producers | 37.157 ms | 60.506 ms | 493 / 493 |

**The native path eliminates composition and layout work, but this physical-device
capture shows no frame-time improvement for 100 icons.** Every sampled frame in
the 100-icon cases exceeded its deadline. One icon remained around 9 ms for all
renderers. CPU frame duration is not the reciprocal of displayed FPS, and positive
deadline overruns are not a literal count of dropped frames.

The [trace investigation](TRACE_ANALYSIS.md) identifies native font work and the
per-icon offscreen layer pipeline, including CPU work delegated to a Mali driver
thread, as the remaining costs. It also distinguishes positive benchmark overruns
from FrameTimeline's dropped-frame and jank classifications.

Median counts per two-second iteration for the 100-icon cases:

| Renderer | Issued updates | Icon compositions | Group compositions | Outer measures / placements | Inner text measures |
| --- | --- | --- | --- | --- | --- |
| Value overload | 101 | 10,100 | 0 | 0 / 0 | 10,100 |
| Shared family | 102 | 10,200 | 1,020 | 0 / 0 | 10,200 |
| Native producers | 100 | 0 | 0 | 0 / 0 | 0 |

Native host compositions also remained zero, at both icon counts. All 30 measured
iterations had complete first-icon content and layer tick coverage. This checks
that every issued update reached those probes; it does not verify each icon's
individual pixels or guarantee compositor presentation. The driver skips animation
coordinates under load, so the number of issued updates differs between runs.

Native glyph drawing still took 12.808 ms per measured frame for 100 icons. The
value/shared paths spent 13.969/13.881 ms in inner text measurement plus
1.192/1.205 ms drawing. These trace regions describe different stages of the same
rendering work; removing Compose phases does not remove font metrics, shaping,
rasterization, or the rest of frame production.

The separately captured Pixel screenshots passed at four poses per renderer:
fixed 336×336 px icon size (128 dp), fixed 504×504 px capture cell, changing axes,
tint and transforms, and zero native phase counts. All three renderers matched
ink rectangles, foreground counts and mean RGB exactly at those poses. Timing
runs used 63×63 px icons for the 24 dp, 100-icon workload.

Compare renderers within this workload. The earlier resize-stress capture also
differs in actual sizing, native cache capacity and device conditions; its delta
cannot be attributed solely to removing recomposition.

## Preliminary emulator measurements

One two-second iteration per renderer, full AOT compilation, API 36 x86_64
emulator with four CPU cores. Only the emulator check was suppressed. This is a
diagnostic capture, not representative Pixel performance or a repeated benchmark.
The repeated physical-device measurements above supersede it for Pixel conclusions.

| 100 icons | CPU frame P50 | CPU frame P99 | Icon compositions | Outer measures / placements | Inner text measures |
| --- | --- | --- | --- | --- | --- |
| Value overload | 22.363 ms | 26.128 ms | 12,100 | 0 / 0 | 12,100 |
| Shared family | 22.045 ms | 25.326 ms | 12,100 | 0 / 0 | 12,100 |
| Native producers | 17.904 ms | 22.982 ms | 0 | 0 / 0 | 0 |

Every renderer issued 121 updates and recorded all of them in both the first
icon's content and layer properties. Native also recorded zero host and group
compositions. Deadline overruns remained high: 120/120 samples for both text
paths and 120/121 for native. Zero composition does not mean zero frame cost or
establish smooth physical-device rendering.

## Cache issue exposed by independent drawing on the emulator

The initial emulator native capture had an 83.883 ms median CPU frame time. Its trace
showed 80–81 expensive glyph draws per 100-icon update, although there were only
ten distinct styles; 98.5% of glyph draw duration was active CPU work. The old
shared Android cache held only one variation. Independent layer redraws can
repeatedly replace that entry when styles are visited out of order.

A standard Android `LruCache` now retains at most 16 recent variations per mounted
font, released with its last renderer. This reduced native drawing from 76.375
to 10.296 ms per measured frame in the diagnostic rerun. Cache misses and style
order were not directly traced, so cache thrashing is inferred from the source,
draw distribution and controlled cache change. Workloads with more than 16
concurrent styles may need a different capacity after measurement.

## Verification and reproduction

All 21 JVM tests pass, including tint-only and combined tint/axis pixel changes
without composition or layout. Native black/red pixels match the standard text
renderer exactly. JS, Wasm and iOS arm64 compilation also passed.

With the final Android cache, all three geometry tests pass on the emulator:
fixed-size colored poses across all renderers, original axes geometry, and
supported → unsupported → supported axis recovery. APK hashes match the final
timing and geometry captures. Previous resize-stress results remain historical
measurements of their recorded APKs.

See the [fixed-size draw instructions](README.md#fixed-size-draw-scenario) and
[capture-and-analysis recipe](README.md#preserve-and-analyze-a-new-run). Preserve
timing output before the geometry run, then pass the local captures to the analyzer
with `--draw-geometry`. Keep physical-device and emulator studies separate.

The curated findings above describe that historical implementation. Raw JSON,
traces, screenshots, device checkpoints, APK hashes and generated summaries stay
local and are not committed. A current checkout measures the current implementation.
The Pixel's screen-awake setting was restored to its original value, 0, after the run.
