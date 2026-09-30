# Controlled build comparisons

Three rotated repetitions per ordinary case, one separate cache probe per case. All runs use one worker, a 4 GiB single-use Gradle JVM, JDK 21, and in-process incremental Kotlin compilation. Warm-ups and source-restoration builds are excluded. All measured series start from target-clean outputs. See [method](README.md) and [complete compact data](CONTROLLED_BUILDS.json).

Host: AMD Ryzen 9 7950X3D (32 logical CPUs), 61.94 GiB kernel-reported RAM, Linux 7.2.6, Zulu JDK 21.0.12.1. Other agent builds and emulators were stopped; ordinary desktop activity remained. No CPU-frequency or thermal controls were changed.

## A / B: toolchain upgrade

A: `20339e8bb5bc787a569516dc78dda0cce5bdb6da`, B: `3aea238f2b6877fef005e26d8547cd7fcebfc129`

| Profile | Case | A median seconds (range) | B median seconds (range) | Change |
| --- | --- | ---: | ---: | ---: |
| shell | target-clean | 92.164 (89.052–92.853) | 108.387 (108.099–113.433) | +17.6% |
| shell | noop | 12.848 (12.738–12.900) | 10.791 (10.583–11.332) | -16.0% |
| shell | code-edit | 38.056 (37.455–38.406) | 32.832 (32.749–33.137) | -13.7% |
| material-static | target-clean | 107.357 (106.073–108.047) | 127.300 (126.918–129.821) | +18.6% |
| material-static | noop | 14.483 (13.687–15.285) | 12.196 (11.969–12.333) | -15.8% |
| material-static | code-edit | 40.174 (39.719–42.780) | 38.461 (35.056–47.717) | -4.3% |
| material-variable | target-clean | 94.462 (93.941–95.116) | 111.601 (109.378–113.526) | +18.1% |
| material-variable | noop | 13.458 (13.029–13.823) | 11.102 (10.812–11.164) | -17.5% |
| material-variable | code-edit | 38.937 (37.765–39.508) | 33.082 (32.709–33.463) | -15.0% |
| image-vector-migration | target-clean | 104.506 (100.769–120.359) | 118.283 (116.862–152.276) | +13.2% |
| image-vector-migration | noop | 13.740 (13.396–14.117) | 11.751 (11.323–18.807) | -14.5% |
| image-vector-migration | code-edit | 40.224 (40.133–40.397) | 35.489 (35.074–56.890) | -11.8% |
| image-vector-migration | resource-edit | 44.310 (44.151–44.819) | 42.387 (42.198–42.909) | -4.3% |
| all | target-clean | 120.538 (120.299–122.845) | 143.751 (141.562–145.514) | +19.3% |
| all | noop | 15.828 (15.825–15.940) | 13.344 (13.019–13.587) | -15.7% |
| all | code-edit | 44.100 (43.830–44.115) | 37.924 (37.530–39.841) | -14.0% |
| all | resource-edit | 49.123 (48.374–49.205) | 46.340 (45.785–46.587) | -5.7% |

### Clean-build memory and APK size

| Profile | A peak tree RSS MiB | B peak tree RSS MiB | RSS change | A APK bytes | B APK bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| shell | 6036.6 | 6766.6 | +12.1% | 1,495,397 | 1,509,422 |
| material-static | 6972.8 | 7657.6 | +9.8% | 7,711,960 | 7,747,649 |
| material-variable | 6330.8 | 6885.1 | +8.8% | 16,131,536 | 16,145,561 |
| image-vector-migration | 6446.3 | 6856.7 | +6.4% | 16,299,006 | 16,313,031 |
| all | 7945.9 | 7095.8 | -10.7% | 22,967,644 | 22,980,545 |

### Clean-build task attribution

| Profile | Rounded-vector compile seconds A / B | Rounded-vector lint seconds A / B | App R8 seconds A / B |
| --- | ---: | ---: | ---: |
| shell | 33.334 / 33.765 | 2.095 / 24.056 | 16.063 / 14.378 |
| material-static | 32.439 / 33.462 | 1.958 / 22.876 | 16.936 / 15.165 |
| material-variable | 32.735 / 32.969 | 1.986 / 23.297 | 16.105 / 14.742 |
| image-vector-migration | 34.350 / 33.607 | 1.954 / 23.290 | 17.956 / 15.803 |
| all | 32.795 / 33.450 | 1.955 / 23.192 | 18.932 / 16.130 |

### Cache probes: one run, not three-repeat medians

| Profile | Configuration reuse seconds A / B | Output restore seconds A / B | FROM-CACHE tasks A / B |
| --- | ---: | ---: | ---: |
| shell | 4.668 / 4.677 | 15.491 / 13.439 | 92 / 68 |
| material-static | 6.044 / 5.817 | 18.547 / 15.625 | 164 / 120 |
| material-variable | 5.030 / 4.906 | 16.463 / 13.673 | 147 / 107 |
| image-vector-migration | 5.128 / 4.892 | 16.162 / 14.948 | 153 / 113 |
| all | 7.066 / 6.496 | 20.608 / 18.029 | 309 / 248 |

Integrity: 102 ordinary samples and 20 measured cache probes passed. All 42 edited APKs changed hash; 72 no-op/restoration builds and 20 cache probes reproduced the clean APK hash. All 204 original HTML profiles were checked with project subtotal rows excluded. No build, cache-contract or RSS-sampling failures occur in these selected result sets.

## B / C2: resource optimization

B: `3aea238f2b6877fef005e26d8547cd7fcebfc129`, C2: `108fa6dc42563b3dc244375f9de376b2e89dce65`

| Profile | Case | B median seconds (range) | C2 median seconds (range) | Change |
| --- | --- | ---: | ---: | ---: |
| shell | target-clean | 109.922 (109.566–112.308) | 87.704 (87.413–87.736) | -20.2% |
| shell | noop | 10.780 (10.684–11.601) | 11.102 (10.561–11.362) | +3.0% |
| shell | code-edit | 32.755 (32.472–33.061) | 32.974 (32.602–33.531) | +0.7% |
| android-views | target-clean | 123.688 (122.849–128.810) | 92.216 (91.509–92.460) | -25.4% |
| android-views | noop | 11.877 (11.805–12.017) | 11.327 (11.155–11.350) | -4.6% |
| android-views | code-edit | 35.217 (34.730–35.432) | 34.026 (33.396–34.340) | -3.4% |
| android-views | resource-edit | 44.287 (43.258–46.591) | 31.279 (31.217–31.522) | -29.4% |

### Clean-build memory and APK size

| Profile | B peak tree RSS MiB | C2 peak tree RSS MiB | RSS change | B APK bytes | C2 APK bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| shell | 6790.3 | 5883.8 | -13.4% | 1,509,422 | 1,509,422 |
| android-views | 7079.0 | 5816.0 | -17.8% | 16,457,984 | 1,808,407 |

### Clean-build task attribution

| Profile | Rounded-vector compile seconds B / C2 | Rounded-vector lint seconds B / C2 | App R8 seconds B / C2 |
| --- | ---: | ---: | ---: |
| shell | 34.113 / 33.449 | 24.127 / 2.011 | 14.669 / 14.772 |
| android-views | 33.317 / 33.033 | 24.396 / 2.013 | 15.015 / 14.503 |

### Cache probes: one run, not three-repeat medians

| Profile | Configuration reuse seconds B / C2 | Output restore seconds B / C2 | FROM-CACHE tasks B / C2 |
| --- | ---: | ---: | ---: |
| shell | 4.476 / 4.469 | 13.197 / 13.392 | 68 / 68 |
| android-views | 5.391 / 5.168 | 15.738 / 14.381 | 156 / 113 |

Integrity: 42 ordinary samples and 8 measured cache probes passed. All 18 edited APKs changed hash; 30 no-op/restoration builds and 8 cache probes reproduced the clean APK hash. All 84 original HTML profiles were checked with project subtotal rows excluded. No build, cache-contract or RSS-sampling failures occur in these selected result sets.

## Interpretation and boundaries

A/B measures the complete toolchain/dependency upgrade, not AGP alone. Clean-build medians increase by 13.2–19.3%. The consistent task difference is rounded-vector lint: about 2 seconds in A becomes 23–24 seconds in B, while vector compilation stays around 33 seconds. B includes authored common and generated Kotlin absent from A’s legacy Android lint model. Clean-build R8 medians decrease in every profile, so these clean regressions are not a sustained R8 slowdown.

Code-edit medians improve by 13.7–15.0% in shell, material-variable and all, with disjoint observed ranges. Material-static and image-vector-migration have overlapping code-edit ranges; their median reductions do not establish a reliable speedup. The image-vector clean/no-op results also contain broad ranges. Its slow samples affected multiple stages, while vector lint stayed near 23 seconds. GC was not traced separately and normal desktop activity remained; the precise source of those outliers is unproven. All samples are retained.

Both revisions intentionally retain Material Icons Extended 1.7.3 for the old/new migration example. Its Android dependency is 1.7.6; the cached AAR contains 11,105 class files (37,425,981 bytes of classes.jar, 85,390,331 bytes of uncompressed classes). That large unchanged input helps explain why shrinking is substantial work; it is not evidence that the upgrade introduced the graph.

B/C2 shell isolates [generated-source classification](GENERATED_SOURCES.md): clean builds improve by 20.2%, peak sampled RSS by 13.4%, and rounded-vector lint falls from 24.127 to 2.011 seconds. Compilation stays similar at 34.113 versus 33.449 seconds. The clean APK is byte-identical and the generated inventory remains 69 files, 16,873,485 bytes and 413,160 Kotlin lines. The public experimental generatedKotlin API retains compilation and source publication; authored common/Android lint inputs remain present. Separate compilation/source-JAR/lint-model regressions establish this boundary.

B/C2 [Android Views](ANDROID_VIEWS_RESOURCES.md) combines that classification with replacing an unrelated sample dependency by native SVG generation through the existing Symbols DSL. Clean builds improve by 25.4%, SVG edits by 29.4%, and the shrunk APK falls from 16,457,984 to 1,808,407 bytes (89.0%). The removed transitive font explains most of the size reduction. Generated outputs fall from 4,137 files / 67,184,420 bytes / 420,324 Kotlin lines to 3,919 / 22,435,605 / 413,167. This sample dependency correction is not automatic glyph-level font subsetting.

These timings cover the monorepo sample app and its project dependencies, not the cost of consuming precompiled Maven artifacts. Target-clean preserves included tooling builds and downloaded dependencies. The two cohorts remain separate; B observations from different collection periods are not pooled. Three repetitions expose ranges but are not a broad statistical population. Cache probes establish reuse/restoration once per case and are not repeated timing claims.

Task durations are elapsed task measurements, not CPU time or an additive decomposition of wall time. Project subtotal rows are excluded. Process-tree RSS includes shared pages and is not Java heap or unique resident memory. Resource edits are N/A for profiles that do not consume the SVG. No compiler optimization, lint, R8 or runtime assertions were relaxed; raw logs, Gradle profiles and APKs remain local.

## Reproduce the selected cases

Create clean disposable worktrees at the exact SHAs above and provision each revision’s Python requirements as described in the [method](README.md). Point both revisions at the same JDK and Android SDK, then run ordinary cases and cache probes separately:

```bash
export JAVA_HOME=/path/to/jdk21
export ANDROID_HOME=/path/to/android-sdk
study_args=(
  --revision A=/tmp/symbols-A --revision B=/tmp/symbols-B
  --python A=/tmp/symbols-python-A/bin/python
  --python B=/tmp/symbols-python-B/bin/python
  --profile shell --profile material-static --profile material-variable
  --profile image-vector-migration --profile all
  --variant shrunk --max-workers 1
)
python3 benchmarks/resource-pipeline/run.py "${study_args[@]}" \
  --scenario target-clean --scenario noop --scenario code-edit \
  --scenario resource-edit --repeat 3 --output /tmp/symbols-ab-ordinary
python3 benchmarks/resource-pipeline/run.py "${study_args[@]}" \
  --scenario configuration-cache --scenario build-cache \
  --repeat 1 --output /tmp/symbols-ab-caches
```

For B/C2, replace the revision/Python entries with B and C2 and select only `--profile shell --profile android-views`. Use fresh output directories and run sequentially without competing builds. The recorded A/B collection preserves two completed earlier profile groups and a later three-profile continuation; only complete groups enter the report.
