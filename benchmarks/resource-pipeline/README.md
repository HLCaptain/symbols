# Resource pipeline comparison

Compare committed, disposable worktrees: A is the pre-upgrade baseline, B
contains the toolchain migration, and C contains the separate Android resource
ownership and generated-source registration changes. Record the exact commit
for each run; the ownership-only size study and later source-registration study
use different C revisions. The web distribution optimization is measured
separately; it does not change Android APKs.
The runner uses the existing sample profiles and APK analyzer; it does not modify
the library API or invent a second app benchmark.

[Completed measurements](CONTROLLED_BUILDS.md) cover 144 repeated build samples
and 28 separate cache probes. See the [overall results](RESULTS.md) for APK size,
web distribution, consumer compatibility and rendering checks.

Use Python 3.11+ on Linux or macOS, the same JDK 21, and the same installed Android
SDK packages. Provision each revision's `tools/requirements-font-verification.txt`
in a separate virtualenv before measuring. Keep dependency download caches warm
and stop unrelated heavy builds. All worktrees must be clean; temporary source
edits are restored even when a build fails. Do not use an active development tree.
Before warm-up, the runner imports FontTools, PicoSVG and skia-pathops through
each selected interpreter and records their actual versions and executable path.
Virtualenv executable symlinks are preserved so this check uses the intended environment.

After this directory is added to the repository:

```bash
export JAVA_HOME=/path/to/jdk21
python3 benchmarks/resource-pipeline/run.py \
  --revision A=/tmp/symbols-upgrade-baseline \
  --revision B=/tmp/symbols-upgrade \
  --python A=/tmp/symbols-python-a/bin/python \
  --python B=/tmp/symbols-python-b/bin/python \
  --profile image-vector-migration \
  --repeat 3 --output /tmp/symbols-resource-results
```

Default coverage is deliberately bounded: `image-vector-migration`, release and
shrunk APKs, and the six cases below. Repeat `--profile`, `--variant`, or
`--scenario` to select a larger or smaller study. For representative application
measurements add shell, material-static, material-variable and all. Use the
existing `benchmarks/sample-app/build.py` and `analyze.py` for all ten profiles'
size/correctness sweep; their timings use a different method and must not be
merged with this runner's incremental measurements.

## Measurement method

Root-project outputs are cleared before one uncounted warm-up for each
profile/variant series; included tooling builds and dependency caches are preserved.
Three repetitions rotate revision ordering to reduce host/order bias; increase to five if results are
noisy. Each repetition measures:

| Case | Preparation and measured work |
| --- | --- |
| target-clean | Remove root-project `build/` outputs, then assemble; preserve included tooling builds, downloaded dependencies and transforms |
| noop | Assemble again with unchanged sources and outputs |
| code-edit | Change the consumer's Back content-description string and assemble; restore and rebuild outside the sample |
| resource-edit | Change the Tabler SVG stroke width and assemble; restore and rebuild outside the sample |
| configuration-cache | Uncounted configuration-cache seed, then measured reuse; fail if reuse is absent |
| build-cache | Uncounted build-cache seed, remove build outputs, then measure restore; fail if no task restores from cache |

Resource edits run only for profiles that consume the SVG: image-vector-migration,
android-views and all. Other profiles explicitly report that case as skipped.
Timing starts after cleanup/source editing, before launching Gradle, and ends when
the complete build command exits. APK analysis and source restoration are outside
the timing interval.

Each build uses a fresh single-use Gradle daemon, one worker and a 4 GiB heap.
Kotlin compilation runs in-process so the sampled descendants include compiler
memory. **Incremental compilation remains enabled.** Ordinary cases disable build
and configuration caches; the two cache cases opt into their respective cache.
These controls are identical across revisions and are recorded in every command;
absolute numbers are not a prediction of an IDE's long-lived daemon performance.

The profile reset addresses an observed dependency-switch failure in frozen B:
after changing `material-variable` to `shell`, `compileAndroidMain` executed, but
`AppKt` retained references to the removed `MaterialVariableNavigationModule`.
R8 correctly rejected them. That failed warm-up is excluded from timing samples.

The published [Koin Gradle plugin 1.2.1 source](https://repo.maven.apache.org/maven2/io/insert-koin/koin-compiler-gradle-plugin/1.2.1/koin-compiler-gradle-plugin-1.2.1-sources.jar)
auto-detects aggregators with identifier-boundary patterns for `startKoin`,
`koinApplication`, and `@KoinApplication`. It does not resolve annotation aliases
or recognize `koinConfiguration<>`. Its exact pattern matches none of this app's
main sources: `@KoinApplicationDefinition` fails the annotation boundary, so the
default `strictSafety` safeguard is not auto-enabled. Koin documents
[incremental graph-discovery limitations and explicit strictSafety](https://insert-koin.io/docs/reference/koin-annotations/options/#strictsafety)
when aggregator detection misses. These facts support stale incremental DI
state as the inferred cause; an explicit `strictSafety` workaround has not been
validated for this KMP profile switch and is not introduced here.

The existing size-sweep runner disables Kotlin incremental compilation, so its
successful builds did not establish profile-switch incremental safety. This
runner clears root-project outputs only before the excluded profile warm-up and
for explicit target-clean/cache cases. It retains incremental compilation within
measured fixed-profile cases and does not suppress R8 diagnostics. This is a
sample profile-switch limitation, not evidence that normal within-profile
incremental edits fail.

Memory is the peak 100 ms sample of summed RSS for the Gradle launcher and its
process descendants, reusing `benchmarks/shrinkable-vectors/measure.py`. Unrelated
Java processes are excluded. Shared pages can be counted more than once and peaks
shorter than the interval may be missed; this is neither unique resident memory
nor Java heap usage. Sampling failures are recorded, never represented as zero.

`results.json` records revision SHAs, catalog versions, Python requirements,
commands, raw samples, medians/ranges, pairwise deltas, APK payload/DEX/resource/font
metrics, the first target-clean generated source inventory, task outcomes, and Gradle profile task
timings. Raw logs and HTML profiles stay in the chosen private output directory;
APKs remain under each checkout's ignored `build/` directory. Commit only compact
reviewed summaries and reproduction code, without uploading raw build artifacts
or depending on GitHub Actions storage.

This timing runner does not establish automatic font subsetting or successful
unused-resource removal. The independent consumers below cover usage counts;
existing shrinking assertions and runtime rendering checks remain necessary. A
shorter build or APK alone is insufficient evidence for an optimization.

The [generated Kotlin source registration change](GENERATED_SOURCES.md)
uses the public KGP API to preserve authored lint coverage while classifying build
outputs correctly. Its targeted JVM/KMP compilation, source-archive and lint-input
regression and full Material module validation pass. The controlled comparison
records the timing, memory, output-integrity and cache results.

## Fast integrity checks

```bash
python3 -m unittest discover -s benchmarks/resource-pipeline -p 'test_*.py'
```

The checks exercise restoration after failure, cleanup boundaries, exclusion of
warm-ups/failures from summaries, pairwise deltas, and Gradle task-duration parsing.
They do not execute Gradle or substitute for real measurements.

## Pixel rendering check

The separate [API 21/23 runtime smoke checks](RUNTIME_COMPATIBILITY.md) cover the
minimum Android versions for XML-only and Compose consumers.

After building B's three `dynamic-all` fixtures, install their signed shrunk APKs
on an unlocked Pixel 6a at its standard 1080×2400 / 420 dpi configuration:

```bash
for backend in android compose vectors; do
  "$ANDROID_HOME/platform-tools/adb" -s "$ANDROID_SERIAL" install -r \
    "/tmp/symbols-usage-b/$backend-dynamic-all/build/outputs/apk/shrunk/symbols-usage-$backend-dynamic-all-shrunk.apk"
done
python3 benchmarks/resource-pipeline/pixel.py \
  --adb "$ANDROID_HOME/platform-tools/adb" --serial "$ANDROID_SERIAL" \
  --output /tmp/symbols-pixel-parity
```

This optional screenshot analysis needs Pillow (12.3.0 was used); install it in
a disposable analysis environment. The check captures four catalog positions,
including the glyphs with the largest old-outline errors, and compares 48 complete
grid cells per position against the FontTools-generated vector backend. It excludes
system bars and rejects blank reference cells or a per-cell mean RGB error above
0.5%. The fixed device geometry is explicit; this is not an arbitrary-device test.
Use `--compare-only` to recheck saved captures without touching the device.
Only [compact results](pixel-icons.json) are committed; PNGs stay local.

## Independent usage-count fixtures

`fixtures.py` generates 30 Android Compose consumer projects: typed vectors,
native Android drawable XML and Compose drawable assets, each with direct and
dynamic access, using 0, 1, 25, 200 and all 3,802 distinct outlined glyphs. Aliases
are grouped by codepoint and the lexicographically first name is chosen, matching
the library's resource naming. Every backend displays the same ordered selection
in an identical black-on-white four-column grid.

Publish these complete candidate artifacts, including their transitive Symbols
modules, into an isolated local Maven repository first:

- `symbols-material-vectors-outlined`
- `symbols-material-drawables-outlined`
- `symbols-material-compose-drawables-outlined`

Generate separately for A and B (and C when applicable), passing each revision's catalog and wrapper.
The repository is exclusive for `io.github.hlcaptain` so missing local artifacts
cannot silently fall back to a published Central version. Generation reads only
the checked-in codepoint catalog; it never fetches source fonts or icons. Gradle
may still download ordinary toolchain and third-party binary dependencies.

```bash
python3 benchmarks/resource-pipeline/fixtures.py \
  --source-root /tmp/symbols-upgrade \
  --repository /tmp/symbols-maven-b --version 0.1.0-benchmark-b \
  --output /tmp/symbols-usage-b

# Bounded first check; repeat --backend/--access/--count to choose a subset.
# Omit these selectors to generate all 30 projects.
python3 benchmarks/resource-pipeline/fixtures.py \
  --source-root /tmp/symbols-upgrade \
  --repository /tmp/symbols-maven-b --version 0.1.0-benchmark-b \
  --output /tmp/symbols-usage-probe \
  --backend android --access direct --count 1

cd /tmp/symbols-usage-probe/android-direct-1
./gradlew assembleRelease assembleShrunk --no-daemon --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process
```

`--catalog` and `--wrapper-from` allow a consumer toolchain to differ from the
library's publication toolchain. The generated project applies built-in Kotlin on
AGP 9 and the Kotlin Android plugin on AGP 8. It has no project substitution or
Symbols generator plugin: it consumes the actual published libraries. Generated
projects and copied wrappers are temporary output, never repository sources.

Direct mode names only selected public vector properties, `R.drawable` fields or
`Res.drawable` accessors. Dynamic mode uses `Symbols.Material.all` plus the vector
bridge, an app-owned map of explicit `R.drawable` references, or `Res.allDrawableResources`. Its
selection is read at runtime from an asset, so retention of a complete catalog is
legitimate, including the zero-selection case. No keep rules or font-subsetting
heuristics bias the comparison. Direct dispatch is split into groups of 64 to
avoid the JVM method-size limit for the full pack.

For each built APK, record retention alongside its normal payload metrics:

```bash
python3 benchmarks/resource-pipeline/retention.py \
  --fixture /tmp/symbols-usage-probe/android-direct-1 \
  --apk /tmp/symbols-usage-probe/android-direct-1/build/outputs/apk/shrunk/symbols-usage-android-direct-1-shrunk.apk \
  --aapt2 "$ANDROID_HOME/build-tools/36.1.0/aapt2"
```

Use the actual APK filename from `build/outputs/apk/`; the example is illustrative.
Native resource checks use AAPT2's table dump because optimized APKs can rename
XML paths. Native and Compose checks fail if a selected resource is absent and
report retained unused resources without assuming that the backend supports
shrinking. Vector name markers in DEX are diagnostic, not a rendering proof:
R8 can inline builders or remove unused names.

Install and open both release variants for rendering checks. Launch with
`adb shell am start -n io.github.hlcaptain.symbols.usage.android.direct/study.MainActivity --ei first 199`
to inspect the final selected group, changing the package/backend/access and index
as appropriate. Review first and last groups and compare equivalent selections
across backends. Archive compact results only after real builds and runtime checks;
generated fixtures and passing Python tests are not benchmark results.

To build and analyze the complete generated matrix sequentially:

```bash
python3 benchmarks/resource-pipeline/usage.py \
  --fixtures /tmp/symbols-usage-b \
  --output /tmp/symbols-usage-results-b.json \
  --aapt2 "$ANDROID_HOME/build-tools/36.1.0/aapt2"
```

This builds both the unminified `release` control and R8/resource-shrunk `shrunk`
variant, checks selected resources, and saves results after each case. Completed
fixture build intermediates are removed to bound temporary disk/RAM usage; APKs,
R8 outputs, fixture sources and logs remain available. `--case`
selects a bounded subset. `--resume` reuses successful cases only when their
fixture sources, wrapper files and resulting APK hashes still match. Never
republish different bytes under a cohort's fixed Maven version; create a new
cohort/version instead. Timings from this size/retention sweep are diagnostic,
not controlled benchmark results. The separate timing runner above supplies the
repeated measurement protocol.

Preliminary native probes using `Resources.getIdentifier()` with asset-fed names
and reflection over `R.drawable` both lost referenced icons under R8/resource
shrinking. Native dynamic fixtures therefore generate an app-owned map of explicit
references to the full resource catalog, then select from it using runtime data.
That models the same complete-catalog reachability as the vector/Compose modes.
It is benchmark consumer code, not a new library feature or custom shrinker.
Arbitrary string/reflection lookup has an app-owned resource-keep contract and
must not be advertised as automatically safe under shrinking.
