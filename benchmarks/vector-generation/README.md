# Vector source distribution study

Compares the four Material ImageVector modules in isolated copies of a pinned pre-migration commit:

- `static`: current checked-in Kotlin.
- `generated`: identical Kotlin emitted into `build/` by the existing Python/FontTools generator.
- `hybrid`: checked-in public Kotlin getters; compressed, pre-generated implementation extracted by Gradle.

This is a measurement harness, not a production migration. Findings are in
[Vector generation tradeoffs](../../docs/VECTOR_GENERATION_TRADEOFFS.md).
It never edits the production vector modules. Use an empty work directory outside
the checkout or under an ignored `build/` directory; do not commit its contents.

## Reproduce

Requires JDK 21, Android SDK, Python 3.12, and the repository's normal Gradle dependencies.
The measured baseline was `02ff132ae044343352318306448edc5c17b6792d`.
The preparation command defaults to that pre-migration baseline; `--ref` can select
another commit that still contains the static snapshots, without changing the checkout. The command below reproduces the measured baseline.

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/Android/Sdk
python3 -m venv /tmp/vector-study-python
/tmp/vector-study-python/bin/pip install -r tools/requirements-font-verification.txt
python3 benchmarks/vector-generation/study.py prepare --work /tmp/vector-study \
  --ref 02ff132ae044343352318306448edc5c17b6792d
python3 benchmarks/vector-generation/study.py measure \
  --work /tmp/vector-study --python /tmp/vector-study-python/bin/python --repeats 3
python3 benchmarks/vector-generation/study.py validate \
  --work /tmp/vector-study --python /tmp/vector-study-python/bin/python --repeats 3
python3 benchmarks/vector-generation/analyze.py /tmp/vector-study
```

Allow roughly an hour and several GB of temporary disk space. Builds run sequentially
with one Gradle worker, an 8 GB Gradle heap, and in-process Kotlin compilation.
Keep other CPU-intensive work idle. The three modes rotate order between trials.
Dependency downloads/configuration bootstrap precede the measured trials.

Each trial builds all four JVM JARs, Android release AARs, and JVM source JARs:

1. **Target-clean:** delete only the four vector modules' `build/` directories;
   dependent modules and Gradle/dependency caches remain warm. Build cache disabled.
2. **No-op:** repeat the same request with outputs present.
3. **Incremental edit:** change one handwritten themed adapter's function body,
   build, restore the original body, and build again. This is not a font/manifest
   update or a public API change.
4. After the repeated trials, seed the local build cache, delete target outputs,
   and measure restoration. The host's existing local Gradle cache is shared.
5. Build the existing unshrunk/shrunk Android fixture in each copy and run its
   `verify.py`; this checks the existing Java getter and per-icon R8 removal.

`validate` then runs all four existing JVM contract suites in each copy, checks
source identity and named declarations, probes IDE import after deleting generated
sources, measures explicit source preparation, and measures no-op reuse with
configuration caching enabled. IDE probe failures remain in the logs and do not
suppress the explicit-preparation comparison.

The main series disables configuration caching to make task execution comparable.
It does not measure a cold dependency download, default parallel builds, Android
Studio indexing, application startup, or runtime drawing performance.

`build` runs one named probe using the same settings. For example, source preparation
without compilation:

```bash
python3 benchmarks/vector-generation/study.py build \
  --work /tmp/vector-study --python /tmp/vector-study-python/bin/python \
  --mode generated --label prepare-only
```

Use repeated `--task :modules:material-vectors-outlined:jvmTest` arguments to select
other tasks. Logs, command lines, wall times, Gradle HTML profiles, normalized source
archives, and copied artifacts remain in the work directory. `analyze.py` summarizes
only completed successful runs; its sample counts must be checked before interpreting
an interrupted experiment. A failed build stops the series; inspect its log before
resuming individual probes.

## What the prototype changes

`prepare_sources.py` redirects the existing generator's output paths. It preserves
source filenames and bytes, so full generation can be checked against the source
hashes recorded by `prepare` in `inventory.json`.

The hybrid keeps named getters, catalog indexes, and themed source visible in Git.
It stores path-building objects in three deterministic `vector-geometry.tar.gz`
archives. Gradle extracts these into registered source roots. Splitting Kotlin files
requires the previously file-private objects to become `internal`; this changes
implementation bytecode visibility and adds file metadata. The comparison therefore
includes actual compiled artifacts and shrinker behavior, not just compressed text.

Both generated variants register task-backed `commonMain` sources and hook source
preparation into `prepareKotlinIdeaImport` where that task exists. This is a prototype
for IDE import; a successful command-line preparation is not proof of fresh Android
Studio completion/indexing behavior. Neither approach removes Kotlin compilation.

Archives use normalized paths/timestamps and gzip/ZIP compression level 9. Their sizes
are comparable local source distributions, not exact GitHub CDN payloads. The
four-module ZIP for full generation excludes the required fonts and tools elsewhere
in the repository and is not a standalone build distribution.

`prepare` also writes `vector-generation-inputs.zip` for the font/manifest/generator
payload comparison. To measure the separate generator-tool download on your platform:

```bash
/tmp/vector-study-python/bin/python -m pip download --no-deps --only-binary=:all: \
  fonttools==4.60.2 --dest /tmp/vector-study/downloads
wc -c /tmp/vector-study/downloads/*.whl
```

The report's wheel size is specific to Linux/CPython 3.12. Record your CPU, memory,
JDK/Python versions, storage type, and competing host load alongside any timing rerun.
