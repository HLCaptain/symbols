# Pixel 6a stress trace analysis — 2026-09-12

The subsequent [implementation and benchmark results](OWNED_LAYER_RESULTS.md)
verify the component-owned compositing default and shared effect calculations.
This report preserves the diagnosis of the earlier capture.

The remaining slowdown has two large contributors: ten continuously changing
font styles on the UI thread, and roughly 100 separately rasterized translucent
layers in the rendering backend. Avoiding recomposition leaves both costs in
place. The traces also show duplicated effect calculations in the harness, but
do not isolate their duration.

This analysis uses the saved physical-device traces, including all five native
100-icon iterations from the axes, resizing-stress and fixed-size-draw scenarios,
and all five baseline/shared 100-icon fixed-size iterations. No new timing run
or rendering change was made for this investigation.

## Where the time goes

Numbers below are medians of each iteration's total section duration divided by
its issued animation updates. They differ slightly from the earlier report's
normalization by Macrobenchmark frame samples. All columns are milliseconds.

| Native, 100 icons | Axes only: wall | Fixed-size effects: wall | Fixed-size effects: active CPU |
| --- | --- | --- | --- |
| Glyph drawing on UI thread | 5.309 | 12.673 | 12.649 |
| Layer-property updates | 0 | 0.441 | 0.440 |
| RenderThread `flush layers` | 0 | 6.917 | 6.301 |
| RenderThread `flush commands` | 1.615 | 9.510 | 0.461 |
| Mali worker during `QueueSubmit` | — | — | 8.768 |
| UI `postAndWait` | 0.408 | 5.362 | 0.023 |

The Mali worker's corresponding axes-only CPU time was **0.085 ms/update**.
The resizing-stress trace also has expensive layer flushing (6.311 ms wall /
5.782 ms CPU) and final command flushing (10.465 ms wall / 0.446 ms CPU).

**These rows cannot be added.** The driver runs while RenderThread waits, and
RenderThread work can overlap UI waiting. Some trace sections are nested.
Macrobenchmark 1.4.1 computes `frameDurationCpuMs` from the UI frame start to
RenderThread frame end, including waits; it is not total active CPU time.
This was checked in the dependency's `FrameTimingQuery.kt`, lines 102–106.

Axes-only and stress are different workloads: one shared style versus ten
staggered styles, different base sizes, layers, and recorded renderer versions.
Their difference locates additional work, not an isolated estimate of the cost
of alpha or recomposition.

## 1. Changing font styles still costs real CPU time

Across all **500 fixed-size native updates**, each update had 100 glyph draw
sections and **exactly ten sections longer than 0.5 ms**. In representative
iteration 000, those ten draws per update account for 85.3% of glyph draw time;
the median individual draw is only 0.019 ms. The axes-only comparison has one
such expensive draw per 100-icon update.

This distribution matches one expensive first draw for each new row style. The
16-entry cache is sharing the current styles successfully; the previous emulator
pattern of around 80 expensive draws per update is gone. Each animation update
still introduces new variation coordinates.

Native glyph drawing is **99.8% active CPU time** in the five-run median, not a
long wait disguised as rendering. The renderer applies variations and, whenever
they change, calls `getRunAdvance` and `fontMetricsInt` before `drawTextRun`.
Those operations are inside one trace section, so these traces cannot separate
typeface construction, native glyph measurement and drawing precisely.

The text paths have similar remaining work:

| Fixed-size, 100 icons | Inner text measure: wall / CPU | Glyph draw: wall / CPU |
| --- | --- | --- |
| Value overload | 13.833 / 13.723 | 1.180 / 1.180 |
| Shared family | 13.744 / 13.629 | 1.193 / 1.193 |
| Native producers | 0 / 0 | 12.673 / 12.649 |

Native saves some UI work, but all renderers feed the expensive layer pipeline.
The shared text path waits 1.453 ms/update in `postAndWait`; native waits
5.362 ms. Faster UI preparation does not remove the downstream work.

## 2. Per-icon opacity creates a costly layer pipeline

The benchmark sets each icon's `graphicsLayer.alpha` between 0.65 and 1 while
leaving the compositing strategy at `Auto`. Compose rasterizes a layer into an
offscreen buffer when alpha is below 1 under that strategy.
[Compose graphics documentation](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers#compositing-strategy).

In native iteration 000, the trace contains:

- 9,810 `drawLayer [graphicsLayer] 63.0 x 63.0` sections.
- 10,021 Skia operation-task executions, versus 121 in the axes-only trace.
- About 100 layer tasks per animation update.
- 239 `QueueSubmit` sections across 102 updates, versus 121 across 121
  axes-only updates. There are not 100 submissions per update; each submission
  processes a much larger collection of layer commands.

Layer contents change on every update because the glyph axes and tint change.
These offscreen layers therefore cannot simply reuse static content while being
transformed.

The long final flush is mostly waiting **for CPU work in a Mali driver worker**.
One measured example follows the dependency precisely:

1. UI `postAndWait` slice 67404 sleeps for 13.384 ms and is woken by RenderThread
   (utid 2778).
2. That UI wait overlaps RenderThread `QueueSubmit` slice 67352 for 12.716 ms.
3. The submission lasts 13.958 ms, using only 0.123 ms of RenderThread CPU and
   spending 13.517 ms asleep.
4. During exactly that RenderThread wait, app thread `mali-cmar-backe`
   (utid 2789) runs for 12.922 ms on CPU 5, then wakes RenderThread.

The UI wait, RenderThread submission and driver worker form a dependency chain.
Across five runs, this driver's CPU work during submission is 8.768 ms/update
for native, 9.874 for baseline and 9.543 for shared. It is a common rendering
cost, not something eliminated by changing the composable's renderer.

The worker has no internal trace sections or sampled call stacks, and `gpu_slice`
is empty. The trace establishes expensive driver-side CPU processing; it does
**not** establish GPU execution saturation or identify a particular driver
function. The layer workload is strongly implicated by source and trace counts.
Its exact causal contribution needs a controlled compositing-strategy comparison.

## 3. The harness repeats work it could share

For native fixed-size rendering, each icon builds the complete effect bundle
for its font settings, again for its tint, and again for its layer properties.
That is approximately **300 full calculations per update for ten distinct row
values**. Each includes trigonometry, variation settings, color and transform
objects. The baseline does approximately 300 and shared approximately 210.

This is confirmed by call sites and probe counts. Its time is not separately
traced, so it should not be credited with the entire slowdown. The layer-property
block itself is only about 0.44 ms/update in the native path.

## Improvements to test, in order

1. **Remove unnecessary offscreen opacity compositing.** For this single-glyph
   icon, test the existing `CompositingStrategy.ModulateAlpha` API:

   ```kotlin
   Modifier.graphicsLayer {
       compositingStrategy = CompositingStrategy.ModulateAlpha
       alpha = animatedAlpha.value
       // Keep the existing scale, rotation and translation reads here.
   }
   ```

   It applies alpha to drawing commands without the automatic opacity buffer.
   Compare geometry/color snapshots and repeat the same Pixel workload. Group
   opacity and per-command opacity can differ for overlapping drawing commands;
   this benchmark draws a single monochrome glyph per icon. This is a candidate,
   not a measured improvement yet.

2. **Share the effect calculation per row.** Reuse one computed effect bundle
   for a given row/progress across the settings, tint and layer producers.
   Keep changing-state reads in those late phases to retain zero recomposition.

3. **Split the native glyph trace before further cache changes.** Measure
   typeface resolution, advance/font-metric lookup and `drawTextRun` separately.
   Only then choose whether to share more glyph measurements or shaped output.
   Increasing the existing cache alone will not remove ten continuously new
   styles per update.

## Other checks and measurement limits

The representative native trace has no application GC events. Its UI thread is
runnable/preempted for only about 0.75% of the animation window, so CPU scheduling
starvation does not explain the steady slowdown. Recorded thermal checkpoints
were 0 with no benchmark throttle sleep. The longest unrelated Wi-Fi Binder wait
does not lie on the app's dependency chain.

Positive Macrobenchmark overruns should not be read as an equal number of dropped
or app-deadline-missed frames. The representative trace has 107 app FrameTimeline
rows, including 65 marked `Buffer Stuffing`, 32 with app-jank classification and
7 dropped frames. Categories can overlap, and this population differs from the
benchmark's matched sample set. Queued buffers add latency even when a frame is
eventually displayed. [Perfetto FrameTimeline documentation](https://perfetto.dev/docs/data-sources/frametimeline).

## Reproducible evidence

The original analysis used Perfetto v58.2. Its raw traces, query outputs and
scratchpads remain local; this document retains the reviewed findings and example
SQL. Generate a fresh local diagnostic capture with the
[benchmark recipe](README.md#preserve-and-analyze-a-new-run), setting
`diagnostics=true` for the timing run. For example, this query compares wall time
with active CPU for native drawing and waiting regions:

```sql
INCLUDE PERFETTO MODULE slices.with_context;
INCLUDE PERFETTO MODULE slices.cpu_time;
SELECT s.name, COUNT(*) AS calls,
       SUM(IIF(s.dur = -1, trace_end() - s.ts, s.dur)) / 1e6 AS wall_ms,
       SUM(c.cpu_time) / 1e6 AS running_ms
FROM thread_slice s
LEFT JOIN thread_slice_cpu_time c USING (id)
WHERE s.process_name = 'io.github.hlcaptain.symbols.benchmark.animatedfont'
  AND s.name IN ('SymbolBenchmark.draw', 'postAndWait',
                 'flush layers', 'flush commands', 'QueueSubmit')
GROUP BY s.name;
```

Run the SQL against the resulting local Perfetto trace. Current code includes the
subsequent optimizations, so the historical costs above are not expected to recur.
Generated trace CSVs and captures are excluded from version control. Nested
sections and cross-thread wait overlaps must not be added together as independent
frame costs.
