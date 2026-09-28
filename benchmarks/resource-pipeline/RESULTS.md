# Resource usage baseline A

All 30 baseline consumers built successfully: 60 APKs covering 0, 1, 25, 200 and
all 3,802 glyphs, three backends, and direct/dynamic access. Counts and artifact
hashes were verified after the builds. This is APK/archive validation, not device
rendering validation or a controlled timing benchmark.

Revision: `20339e8bb5bc787a569516dc78dda0cce5bdb6da`; locally published version
`0.0.0-study-a`. Toolchain: Gradle 8.14.5, AGP 8.13.2, Kotlin 2.3.21, Compose
Multiplatform 1.11.1 and JDK 21. Sources and commands are in the [README](README.md);
exact APK bytes, hashes and retention counts are in [BASELINE_A.json](BASELINE_A.json).

## R8-shrunk APK bytes

| Backend / access | 0 | 1 | 25 | 200 | All 3,802 |
|---|---:|---:|---:|---:|---:|
|vectors/direct|856,410|872,833|889,434|940,531|2,373,419|
|vectors/dynamic|2,396,503|2,396,546|2,396,760|2,398,712|2,438,949|
|android/direct|856,412|907,998|931,165|1,092,039|4,673,909|
|android/dynamic|4,664,231|4,664,267|4,664,490|4,666,429|4,706,671|
|compose/direct|5,042,018|5,091,211|5,091,422|5,109,758|5,313,852|
|compose/dynamic|5,320,560|5,320,594|5,320,808|5,322,750|5,362,996|

## Retention findings

- Direct native XML retains exactly the selected resource count after shrinking;
  direct vectors retain exactly that many vector-name markers in DEX. The markers
  are diagnostic and do not prove pixel correctness.
- Dynamic cases retain all 3,802 glyphs at every selected count, including zero.
  They keep a complete catalog reachable and load the selection at runtime.
- Compose XML assets retain all 3,802 files even with zero direct references.
  Updating Gradle alone is not proof that R8 can shrink assets.
- Preliminary native `getIdentifier()` and reflection-based catalogs lost
  referenced icons under R8. Final native dynamic cases use an app-owned map of
  explicit `R.drawable` references. This is consumer fixture code; arbitrary
  runtime-string lookup still requires an app-owned resource-keep contract.

## Remaining comparison gates

The six initial B one-icon probes built and passed archive checks, but the full
B matrix is pending font-outline validation. A/B XML path strings differ for
3,787 glyphs; contour ordering alone is not a pixel comparison. B results must
not be interpreted as acceptance of visual parity until the independent font
reference, raster and device checks finish.

No APKs, raw logs, profiles or traces are included here. Original build timings
were collected under concurrent load and are excluded from this summary.

## Pixel 6a animation regression check

Six cases per revision, five iterations each, on Android API 37. The same fixed-size scenario animates tint, all font axes and transforms. These small timing differences do not establish a speedup. [Compact measurements](PIXEL.json).

| Renderer | Icons | A CPU P50 ms | B CPU P50 ms | Change |
| --- | ---: | ---: | ---: | ---: |
| shared | 1 | 8.749 | 9.008 | +2.96% |
| shared | 100 | 18.028 | 18.043 | +0.08% |
| value | 1 | 9.007 | 9.212 | +2.27% |
| value | 100 | 14.232 | 14.286 | +0.38% |
| native | 1 | 8.928 | 8.940 | +0.14% |
| native | 100 | 13.754 | 13.657 | -0.70% |

Native producer composition, measurement and placement counters stayed zero at both icon counts. Draw/layer update coverage stayed complete. All four renderer modes matched the BasicText control at four fixed animation positions, with identical measured ink bounds and mean RGB in these captures.

AGP 9 truncated comma-separated arguments and even returned success after a malformed shell command. The benchmark accepts shell-safe `+` lists and validates actual result counts; incomplete runs were excluded. Raw traces and screenshots remain local.
