# Resource pipeline results

The upgrade and resource optimizations are measured separately. Public icon
accessors and the Symbols DSL stay unchanged.

The [controlled build report](CONTROLLED_BUILDS.md) contains all 144 repeated
samples and 28 separate cache probes, with ranges, task attribution, memory and
integrity checks. The upgrade alone adds 13.2–19.3% to clean-build medians because
the native Android plugin includes more Kotlin in lint; compilation stays similar.
The generated-source change below addresses that overhead while retaining authored
common/Android lint inputs.

| Change | Scope and result |
| --- | --- |
| [Generated Kotlin registration](GENERATED_SOURCES.md) | Time for clean shell builds falls 20.2% and peak process-tree RSS falls 13.4%, with byte-identical APKs and authored lint coverage retained. |
| [Android Views resource ownership](ANDROID_VIEWS_RESOURCES.md) | The sample's shrunk APK falls from 16,457,984 to 1,808,407 bytes (−89.0%) by removing an unused transitive font; generated XML is identical. |
| [Web distribution cleanup](WEB_DISTRIBUTION.md) | Removes an unused duplicate WASM file: 8,640,316 deployed bytes, with the hashed runtime retained and browser-verified. |

Neither the upgrade nor these changes automatically subset fonts or make Compose
drawable asset packs shrinkable. The full sample still includes the fonts used
by its enabled features.

The [published-consumer matrix](../../tooling/compatibility/README.md) passes all
nine configurations, including AGP 9.1.0 Android Compose/KMP consumers and the
legacy AGP 8.13.2 Java/XML consumer. Minimum-Android runtime checks and Pixel
rendering results are recorded below.

## Upgrade-only usage comparison

Both cohorts completed all 30 usage cases: **60 cases and 120 APKs** across 0, 1,
25, 200 and all 3,802 glyphs, three backends, and direct/dynamic access. APK hashes
and retention counts were verified after the builds. These are size and archive
checks; build timings collected under concurrent load are excluded.

| Cohort | Publication revision | Toolchain | Local artifact version |
| --- | --- | --- | --- |
| A | `20339e8` | Gradle 8.14.5 / AGP 8.13.2 / Kotlin 2.3.21 / Compose 1.11.1 | `0.0.0-study-a` |
| B | `8fb5fce` | Gradle 9.7.0 / AGP 9.4.1 / Kotlin 2.4.20 / Compose 1.12.1 | `0.0.0-study-b` |

Both use JDK 21, the same signing key and the same glyph selections. B artifacts
were published on September 25 from `8fb5fce`. The checkout advanced to
`3aea238` on September 28 for separate sample/timing runs without republishing
these binaries; those later runs retain their own revision metadata. [Reproduction](README.md). Exact bytes,
hashes and counts: [A](BASELINE_A.json), [B](UPGRADE_B.json),
[A/B deltas](APK_COMPARISON.json).

## R8-shrunk APK bytes

### Cohort A

| Backend / access | 0 | 1 | 25 | 200 | All 3,802 |
| --- | ---: | ---: | ---: | ---: | ---: |
| vectors / direct | 856,410 | 872,833 | 889,434 | 940,531 | 2,373,419 |
| vectors / dynamic | 2,396,503 | 2,396,546 | 2,396,760 | 2,398,712 | 2,438,949 |
| android / direct | 856,412 | 907,998 | 931,165 | 1,092,039 | 4,673,909 |
| android / dynamic | 4,664,231 | 4,664,267 | 4,664,490 | 4,666,429 | 4,706,671 |
| compose / direct | 5,042,018 | 5,091,211 | 5,091,422 | 5,109,758 | 5,313,852 |
| compose / dynamic | 5,320,560 | 5,320,594 | 5,320,808 | 5,322,750 | 5,362,996 |

### Cohort B

| Backend / access | 0 | 1 | 25 | 200 | All 3,802 |
| --- | ---: | ---: | ---: | ---: | ---: |
| vectors / direct | 869,212 | 885,635 | 885,853 | 953,326 | 2,386,210 |
| vectors / dynamic | 2,409,303 | 2,409,350 | 2,409,563 | 2,411,509 | 2,451,747 |
| android / direct | 869,196 | 903,507 | 926,785 | 1,105,071 | 4,688,170 |
| android / dynamic | 4,694,876 | 4,694,927 | 4,695,131 | 4,697,081 | 4,737,311 |
| compose / direct | 5,077,335 | 5,126,534 | 5,126,745 | 5,128,685 | 5,349,154 |
| compose / dynamic | 5,355,770 | 5,355,815 | 5,356,023 | 5,357,963 | 5,398,216 |

## Upgrade deltas for one selected icon

| Backend / access | B minus A bytes | Change |
| --- | ---: | ---: |
| vectors / direct | +12,802 | +1.47% |
| vectors / dynamic | +12,804 | +0.53% |
| android / direct | -4,491 | -0.49% |
| android / dynamic | +30,660 | +0.66% |
| compose / direct | +35,323 | +0.69% |
| compose / dynamic | +35,221 | +0.66% |

These are aggregate upgrade effects, including changed dependencies and corrected
font-derived XML outlines. They do not demonstrate an additional Android resource
shrinking optimization from upgrading Gradle.

## Retention findings

- Direct native XML retains exactly the selected resource count after shrinking
  in both cohorts. Direct vectors retain exactly that many vector-name markers
  in DEX; markers are diagnostic rather than a pixel correctness proof.
- Dynamic cases retain all 3,802 glyphs at every selected count, including zero.
  They keep a complete catalog reachable and load the selection at runtime.
- Compose XML assets retain all 3,802 files even with zero direct references in
  both cohorts. The toolchain upgrade does not make these assets shrinkable.
- Preliminary native `getIdentifier()` and reflection-based catalogs lost
  referenced icons under R8. Final native dynamic cases use an app-owned map of
  explicit `R.drawable` references. This is consumer fixture code; arbitrary
  runtime-string lookup still requires an app-owned resource-keep contract.

## Geometry validation

A/B XML path strings differ for 3,787 glyphs. Independent FontTools outlines and
raster comparisons show that upgraded B corrects filled-area errors already
present in old Skia output; the source TTF is byte-identical. This is a visual
correction, not a claim that every A/B bitmap is identical. See the complete
[raster findings and reproduction](RASTER_RESULTS.md).

No APKs, raw logs, profiles or traces are included here.

## Minimum Android runtime checks

All five [runtime smoke cases](RUNTIME_COMPATIBILITY.md) passed: Java/XML on
API 21 with AGP 8.13.2, and native XML, typed vectors, Compose drawable assets
and the static-font sample on API 23. The expected icons rendered, and captured
logs contained no app crash/resource/linkage exceptions. These selected cases
establish runtime compatibility, not full-glyph parity or frame-time performance.

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
