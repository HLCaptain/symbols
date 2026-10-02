# Android ART and cached-rendering results

Measured 2026-10-02 on a dedicated disposable Android emulator. All **six tests passed**, with five iterations each and 30 captured Perfetto traces. The ordinary-vector comparison uses identical benchmark source, getter selection and catalog inputs for public Symbols 2.0.0 and the current compact snapshot. Filled is a separate fixture/profile; public 2.0.0 does not expose that API.

| Profile | First Info median, ms | Remaining 100 names median, ms | Paired total median [min–max], ms | Cached indexed getter + sink, ns | Frame CPU P50 / P99, ms |
|---|---:|---:|---:|---:|---:|
| Public 2.0.0 ordinary | 0.530 | 1.356 | 2.043 [1.671–2.288] | 11.45 | 17.97 / 35.76 |
| Current compact ordinary | 0.745 | 1.218 | 2.119 [1.764–3.035] | 10.09 | 18.12 / 36.53 |
| Current compact Filled | 0.735 | 1.255 | 1.998 [1.761–2.288] | 13.88 | 18.01 / 37.47 |

Paired totals add first and remaining construction time **within each iteration**, then take the median; they are not sums of independently selected medians. The 101 names represent 99 codepoints. The remaining group includes two aliases. Cached figures divide the measured 101,000-call batch by its call count and include indexed function dispatch, cached integer boxing and the volatile sink. They are not isolated property-access latency.

The ordinary compact median total differs from public by approximately **0.075 ms** in this run, with overlapping ranges. Cached figures are also small and vary. Frame distributions are close, around 18 ms P50 and 36 ms P99 for this intentionally busy 101-icon grid. These results support comparable behavior for this workload; they do **not** establish a speed improvement, a universal regression bound, or physical-phone jank performance. The Filled row measures different geometry and is not a same-shape speed comparison with public ordinary vectors.

The public and current ordinary **960 × 1056 pixel** static grid captures match exactly: **zero changed pixels**. Captures exclude system bars and status text and occur after timing in an explicitly stopped/restarted target process. Filled rendering was captured and visually inspected separately.

## Environment and controls

- Android 16, API 36 from the installed Android 36.1 Google Play x86_64 image; build `BE4B.251210.005`, ART Mainline `361153460`.
- Android Emulator 37.2.5, four virtual CPU cores, 4096 MB RAM (the emulator raised the requested 2048 MB minimum), SwiftShader software graphics, 1280 × 2856 at 480 dpi.
- CPU clocks were unlocked. The emulator reported AC power, 100% battery and 25°C; these are virtual values. Macrobenchmark reported zero thermal-throttle sleep for all six tests. No other team Gradle or CPU-heavy jobs ran during capture.
- `CompilationMode.Full` for the target; its final dexopt state was `speed`, reason `cmdline`. The runner itself reports `run-from-apk` in context metadata; that describes the test package, not the target compilation setting.
- AGP 9.4.1, Kotlin/Compose compiler 2.4.20, Compose 1.12.1, Macrobenchmark 1.5.0, UI Automator 2.4.0, JDK 21 and Gradle 9.7. Minified, non-debuggable/profileable target; no extra keep rules.
- The only suppressed benchmark condition was `EMULATOR`. The disposable AVD and all writable image files lived under the workspace; no physical device or user AVD/application data was modified.

## Artifact and execution evidence

The exact resolved Rounded Android inputs were:

| Input | AAR bytes | SHA-256 |
|---|---:|---|
| Public 2.0.0 | 15,513,985 | `5a637fc48275dacc3251d22e1a0d0ee92dc3f9da9550fa18836c2ccc039ecede` |
| Current 0.1.0-SNAPSHOT | 7,518,448 | `5766fe5a1fca2417f9003f05df0c4f64d5c7d069525ece6a44664e4d4cb3209d` |

All three Symbols AAR inputs per profile were checked against their public download or isolated source publication. The current snapshot came from the production generator without an experimental backend switch.

Optimized DEX inspection verified the 1,000-pass loop still invokes the selected getter, writes a `STATIC VOLATILE` sink inside the inner loop, retains both backward branches, and reads the sink after the trace. The final identity check prevents R8 from discarding a write-only sink. Each measured iteration validates a fresh PID, 101 vectors and 101,000 cache calls. Every animation must complete and produce frames.

Raw evidence is retained locally under the experiment’s `android-art/{public,current-outline,current-filled}` directories: APK/AAR hashes, source hashes, R8 mappings and cached-loop excerpts, complete instrumentation logs, custom counters, Macrobenchmark JSON, cropped screenshots and ten Perfetto traces per profile. Generated APKs, screenshots and traces are not committed. The [README](README.md) documents the reproducible build/run procedure; `analyze.py` rejects missing, duplicate or incomplete result matrices. A physical-device run remains appropriate before making device-wide performance claims.
