# Full sample APK study

This study reuses `composeApp` instead of creating one Android application per
sample. The `symbolsSampleProfile` Gradle property selects the launcher shell,
one feature, or the complete application while retaining the same API/UI shell,
Navigation 3, Koin, and Rounded navigation vector dependencies.

Supported profiles are:

- `shell`;
- `material-static`;
- `material-variable`;
- `custom-static`;
- `custom-variable`;
- `image-vector-migration`;
- `android-views`;
- `theming`;
- `runtime-axes`; and
- `all`, the default normal launcher configuration.

`release` is the existing unsigned, unminified baseline. `shrunk` inherits the
same release configuration and adds R8 full-mode minification plus optimized
Android resource shrinking. Both variants package the same universal ABI and
density set, so their APKs are directly comparable.

The `material-static` profile intentionally packages the complete Rounded
Compose drawable resource pack beside its regular font. This measures the
standard public `Res.drawable` DevEx without Android asset pruning.

## Build

Run the complete matrix locally:

```shell
python3 benchmarks/sample-app/build.py
```

For a focused check, repeat `--profile` or `--variant` as needed:

```shell
python3 benchmarks/sample-app/build.py \
  --profile shell \
  --profile all \
  --variant release \
  --variant shrunk
```

`--execution-cold` adds `--rerun-tasks`, disables the Gradle build and
configuration caches, and records the resulting wall time. It is an execution
cost measurement with downloaded dependencies and local compiler state still
present, not a clean machine:

```shell
python3 benchmarks/sample-app/build.py \
  --profile all \
  --variant shrunk \
  --execution-cold
```

The normal app stores TTFs uncompressed for mmap-friendly Android font loading.
`--compress-fonts` measures the smaller deflated APK alternative and suffixes
the copied profile with `-compressed-fonts`:

```shell
python3 benchmarks/sample-app/build.py \
  --profile material-variable \
  --variant release --variant shrunk \
  --compress-fonts --label compressed-fonts
```

Each APK is copied immediately after its build because Gradle variants reuse
the same output path across profile-property values. Build commands, wall
times, environment facts, and copied paths are recorded in
`builds-<label>.json`. Use `--repeat` for repeated timing and `--label` to keep
separate studies. Normal cached profile builds disable Kotlin incremental
compilation and run the compiler in-process so Koin's compile-time module
collection is rebuilt from the selected dependency graph instead of reusing
another profile's registry. Execution-cold builds already force every task to
run. The first normal cached build is marked as a warm-up when a pair is repeated:

```shell
python3 benchmarks/sample-app/build.py \
  --profile shell --profile all \
  --variant release --variant shrunk \
  --repeat 4 --label warm
```

## Analyze

```shell
python3 benchmarks/sample-app/analyze.py
```

The analyzer reads APK ZIP entries and DEX headers using only the Python
standard library. It reports:

- exact APK bytes and SHA-256;
- compressed and uncompressed ZIP payload bytes;
- DEX strings, types, prototypes, fields, methods, classes, and data bytes;
- every packaged font;
- Compose resource assets grouped by namespace;
- Android `res/` and `resources.arsc` payloads; and
- native libraries grouped by ABI.

When matching pairs exist it also reports shrunk-minus-release and
profile-minus-shell deltas. Results stay local under:

```text
build/reports/apk-study/
├── apks/<profile>-<variant>.apk
├── builds-<label>.json
├── metrics.json
└── report.md
```

No build scan, remote cache, GitHub Actions artifact, or external storage is
used.

## Fair measurement

- Measure a committed, clean worktree after dependencies are already resolved.
- Keep JDK, SDK, Gradle arguments, signing, ABI/density packaging, and host power
  conditions fixed.
- Avoid another Gradle build in tandem. If the host is contended, retain the raw
  samples but do not attribute small timing differences to a profile or build
  variant. These scripts use one worker and a single-use daemon.
- Run one untimed warm-up and at least three measured repetitions; report every
  sample and the median.
- Compare APK size only between `release` and `shrunk`, never against `debug`.
- Treat profile deltas as non-additive because features share fonts and runtime
  dependencies, and R8 can inline or merge code across their boundaries.
- The universal APK is an install artifact, not a Play-delivered download-size
  estimate.
