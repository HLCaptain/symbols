# Animated font rendering — 2026-09-12

The native producer eliminates per-frame caller recomposition and Compose text
measurement. With 100 icons, median CPU frame time was **27% lower than the value
overload**, and **24% lower than the shared-FontFamily baseline**. One-icon median
time was effectively unchanged. The native 100-icon case had worse tail latency
in this run, so these results do not establish a universal jank improvement.

Measured on the Pixel_10_Pro x86_64 emulator, Android API 36, four virtual CPUs,
1280×2856, 480 dpi, 60 Hz. These are emulator measurements; the subsequent
[Pixel 6a capture](PIXEL6A_RESULTS.md) contains physical-device timings.
The target APK was minified, non-debuggable, profileable,
and fully AOT-compiled with `CompilationMode.Full`. Each case ran five two-second
sweeps of `wght`, `FILL`, `GRAD`, and `opsz` together, with a fresh target process
per iteration. Startup/font loading was outside the measured interval. No
concurrent builds or other profiling workloads were started for this task during
the final capture.

## Frame times

`frameDurationCpuMs` includes the elapsed UI/RenderThread frame-production path.
It is distinct from FPS, CPU time actually scheduled, and GPU presentation time.

| Icons | Size | Value overload P50 / P99 | Shared family P50 / P99 | Native producer P50 / P99 |
| --- | --- | --- | --- | --- |
| 1 | 128 dp | 2.579 / 4.753 ms | 2.761 / 12.170 ms | 2.566 / 5.734 ms |
| 100 | 32 dp | 5.726 / 7.832 ms | 5.522 / 7.825 ms | 4.179 / 17.888 ms |

For 100 icons, positive frame-deadline overruns occurred in 3/600 value-overload
frames, 0/603 shared-family frames, and 28/600 native frames. Twenty-two of the
native overruns were concentrated in one iteration. All three renderers drew
**every issued animation update in every iteration**; no comparison gains come
from silently rendering fewer animation updates.

Inspection of the local traces found substantial
RenderThread buffer-release waits and scheduling contention in the slow native
iteration, with no recorded app GC. One frame took 15.703 ms elapsed on
RenderThread but ran for only 2.725 ms, spending 12.768 ms awaiting a buffer.
The text baseline also had buffer-release waits. Some native frames did perform
expensive flush/upload work, and the exact cause of two kernel waits remains
unresolved. The measured native tail regression remains part of the result.

## Work when FPS stays within budget

The single-icon value and native cases each had zero positive overruns. Their
traces still expose the actual rendering cost. The following are medians per
iteration; milliseconds are normalized by each iteration's measured frame count.

| 100-icon renderer | Icon recompositions | Internal text measurements | Outer measurements / placements | Draw work per frame | Text-measure work per frame |
| --- | --- | --- | --- | --- | --- |
| Value overload | 12,100 | 12,100 | 0 / 0 | 0.409 ms | 1.985 ms |
| Shared family | 12,100 | 12,100 | 0 / 0 | 0.408 ms | 2.009 ms |
| Native producer | 0 | 0 | 0 / 0 | 1.662 ms | 0 ms |

The outer square already avoided remeasurement in the text baseline. The native
path removes its inner Compose text work and updates native font metrics/shaping
inside drawing. Native draw time consequently includes work that the text path
performs during measurement; comparing draw time alone would be misleading.
Trace sections are `SymbolBenchmark.draw`, `TextStringSimpleNode::measure`, and
`TextLayout:initLayout`; the latter two are tied to the recorded Compose versions.

## Geometry on style changes

The 128 dp icon stayed **384×384 px** at the same position for every style value.
Both implementations produced the same measured ink position, dimensions, and
dark-pixel count at all 15 axis/combined-axis endpoints and midpoints.

| Weight | Visible ink width × height | Dark pixels | Outer width × height |
| --- | --- | --- | --- |
| 100 | 278×237 px | 9,589 | 384×384 px |
| 400 | 320×283 px | 28,586 | 384×384 px |
| 700 | 348×310 px | 45,779 | 384×384 px |

Changing `FILL` from 0 to 1 kept the ink rectangle at 320×283 px while increasing
dark pixels from 28,586 to 64,552. Thus unchanged layout/bounds do not imply an
unchanged image. Native composition, outer measurement, and placement counters
remained zero during the style updates. A separate JVM check verifies that an
explicit icon-size change does remeasure and resize the glyph.

## Validation and reproduction

- 21 JVM tests passed, including real-font pixels, phase counts, density/font scale,
  constrained sizing, RTL mirroring, tint, resource lifecycle, and size/content changes.
- Android, JVM, JS, Wasm, and iOS arm64 compiled; Android lint reported no issues.
- Six macrobenchmark cases passed, with five iterations each.
- Both Android geometry tests passed, including exact pixel restoration after
  supported settings → an unsupported axis → the original supported settings.

See the [capture-and-analysis recipe](README.md#preserve-and-analyze-a-new-run)
to generate a new local study. For this axes-only workload, select `scenario=axes`,
`axes=all`, and `counts=1,100`, then use `AnimatedFontGeometryTest` and the analyzer's
`--geometry` option. Emulator runs require the documented `EMULATOR` suppression.

These are historical measurements of the renderer at that stage. A current
checkout measures the current implementation. Raw JSON, Perfetto traces,
screenshots, APK hashes and generated summaries remain local and are not committed.
