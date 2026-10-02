# Android vector runtime benchmark

This standalone build reuses the repository version catalog, Gradle wrapper and
Macrobenchmark setup without changing the font benchmark or published modules.
It measures actual Android ART and Compose rendering. Desktop JVM or Robolectric
timings are not substituted for Android measurements.

See [the recorded Android emulator results](RESULTS.md) for the measured comparison
and its device/performance limitations.

The fixed selection contains 101 representative app icon names (99 codepoints).
Every implementation uses the same ordered selection with `Info` first, the same
indexed getter dispatch and a volatile result sink. No reflection or production
cache-reset hook is required.

- `constructAndReadCache`: fresh process for every iteration. `Vector.first`
  includes the first Info getter and initial Compose/vector framework loading;
  `Vector.remaining` constructs the other 100 names, including two aliases.
  After untimed warmup, `Vector.cached` measures 101,000 cached getter calls.
  Cache identity is checked outside the measured sections.
- `renderCachedVectors`: initializes all vectors before capture, then changes
  tint and translation on a 101-icon Compose `Image` grid for two seconds.
  `FrameTimingMetric` measures CPU frame duration and, on Android 31+, overrun.
  A fresh static grid screenshot is taken after timing. This measures cached rendering,
  not path parsing during every frame.

Both tests use `CompilationMode.Full`, five iterations by default, a minified,
non-debuggable/profileable target, and no extra keep rules. A fresh process is
verified by its PID; every rendered run must finish and produce animated frames.
The app and runner use dedicated `io.github.hlcaptain.symbols.benchmark.vectorruntime`
packages. They do not touch the Symbols sample or consumer apps.

Run from the Symbols repository with JDK 21 and a selected `ANDROID_SERIAL`:

```sh
./gradlew -p benchmarks/vector-runtime \
  :app:assembleBenchmark :benchmark:assembleBenchmark :app:recordVectorArtifact

./gradlew -p benchmarks/vector-runtime :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hlcaptain.symbols.benchmark.vectorruntime.VectorRuntimeBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.fills=false
```

The default dependency is public Symbols 2.0.0. For a separately published current
candidate, pass the same properties to both commands:

```sh
-PvectorVersion=0.1.0-SNAPSHOT \
-PvectorRepository=/absolute/path/to/isolated-maven-repository
```

Keep `vectorFilled=false` (the default) for both public and current ordinary-vector
comparisons, so both use exactly the same getter source and reachable fixture.
Build a **separate** current Filled profile with `-PvectorFilled=true` and run it
with `-Pandroid.testInstrumentationRunnerArguments.fills=true`.
Use `fills=false+true` only when comparing two implementations that both support
the Filled-capable fixture. For direct versus compact Filled,
publish each implementation to a different isolated Maven repository/version and
run exactly the same Filled-capable fixture against each. Public 2.0.0 has no
Filled API, so its comparison covers ordinary rounded vectors only.

Use a physical device for representative performance. An emulator run must add
`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`
explicitly and report its CPU/graphics configuration. Do not suppress unrelated
benchmark errors. Do not measure while other builds or benchmarks run.

Archive each run's APK hashes, `app/build/vector-artifacts.tsv` plus the referenced
AAR hashes, test XML, `*-benchmarkData.json`, custom JSON/PNGs and Perfetto traces
before switching candidates. Outputs are in
`benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected`.
For direct `adb shell am instrument` execution, set `additionalTestOutputDir` and
pull that directory after the run. `analyze.py` checks the expected test matrix and
keeps construction/cache timing separate from frame distributions.

```sh
python3 benchmarks/vector-runtime/analyze.py /path/to/capture \
  --fills false --output /path/to/summary.json
```

Use `--fills true` for the separate Filled profile, or `--fills false+true` only
when that complete two-fill matrix was intentionally captured.

Treat cached timing as getter dispatch plus volatile-sink cost, not pure lookup
latency. Construction includes first-use class/framework work. Emulated frame
timings are evidence about that emulator, not physical-phone jank predictions.
