# Material vector generation: measured tradeoffs

Implementation update: the four vector modules now use build-time generation.
Their checked-in snapshots have been removed; see [source-build setup](../CONTRIBUTING.md#development-environment).
The measurements and audit below describe the pre-migration baseline.

Study date: 2026-09-14. Baseline: `02ff132ae044343352318306448edc5c17b6792d`.
The [source footprint audit](SOURCE_FOOTPRINT_AUDIT.md) contains the complete per-module PR LOC summary and other removable-file candidates.

Scope: `material-vectors-outlined`, `material-vectors-rounded`,
`material-vectors-sharp`, and `material-vectors-themed`.

Build-time generation is primarily a repository maintenance choice. It removes over
one million checked-in Kotlin lines and about 3.94 MB from a compressed full source
snapshot. It does not make the published vector library smaller: the measured JVM
JARs, Android AARs, and JVM source JARs are byte-identical to the static version.

## Three implementations

| Approach | Committed representation | Work before compilation | Main compromise |
| --- | --- | --- | --- |
| Static | Complete generated Kotlin | None | Large source diff and checkout; fully browsable API and implementation |
| Build-time generation | Fonts, manifest, generator, Gradle wiring | Python/FontTools emits the same Kotlin into `build/` | Contributor tool dependency; sources absent until preparation |
| Middle ground | Public Kotlin API plus compressed, pre-generated path implementation | Gradle extracts three archives into `build/` | Extra representation to maintain; hidden implementation; little compressed-download saving |

The middle ground is deliberately conservative: it retains the same public getters
and individual glyph objects so normal usage and shrinking can be compared. It is
not a new runtime path interpreter. Moving path decoding into runtime would be a
different architecture with initialization, allocation, and R8 tradeoffs; this study
does not infer those results from source compression.

## Source footprint

All numbers below are measured bytes, using decimal MB where abbreviated.
Kotlin line counts include handwritten code and tests in the four modules; generated
outputs under `build/` are excluded. The baseline contains 251 generated Kotlin files,
1,116,173 generated lines, and 43,966,669 generated source bytes.

| Metric | Static | Build-time generation | Middle ground |
| --- | ---: | ---: | ---: |
| Tracked Kotlin lines in the four modules | 1,116,546 | 373 | 90,103 |
| Tracked Kotlin files | 256 | 5 | 256 |
| Tracked Kotlin bytes | 43,981,096 | 14,427 | 4,042,215 |
| Four-module source tree bytes, including archives/wiring | 43,982,620 | 19,578 | 7,540,450 |
| Public named getters visible before preparation | 16,408 | 0 | 16,408 |
| Four-module source ZIP bytes | 4,115,785 | 8,412 | 3,991,157 |
| Full repository source `.tar.gz` bytes | 23,653,282 | 19,717,060 | 23,558,962 |

Per-module tracked Kotlin lines:

| Module | Static | Build-time generation | Middle ground | What changes |
| --- | ---: | ---: | ---: | --- |
| Outlined | 345,589 | 80 | 13,322 | Path objects move to generated output; hybrid retains named getters and dynamic index |
| Rounded | 413,240 | 80 | 13,322 | Same change; rounded geometry has the largest source footprint |
| Sharp | 307,580 | 80 | 13,322 | Same change for sharp geometry |
| Themed | 50,137 | 133 | 50,137 | Full generation moves delegating getters; hybrid keeps this module static |

For context, the baseline PR versus merge base
`f3a333d8fc5370d9336f1b105d511128babe4b3d` adds 1,153,537 and deletes 18,638 text
lines across the repository. Removing only these four generated source snapshots
would reduce additions to 38,483 and increase deletions to 19,757, **before new
build wiring**. This is a PR diff projection, not the remaining module LOC in the
tables. The separately generated Material catalog is outside this experiment.

Full generation saves 3,936,222 bytes (16.64%) of the full source archive. The middle
ground saves 94,320 bytes (0.40%), although its checked-in Kotlin LOC falls by 91.93%.
Kotlin path statements already compress well: deleting 44 MB of text does not save
44 MB of network transfer.

The generated mode's 8 KB four-module ZIP is **not self-contained**. The three variable
fonts, codepoint manifest, and generator form a separate 14,821,248-byte ZIP. These
inputs already exist in the full repository; they become additional requirements
for someone copying only the four vector modules. The pinned FontTools 4.60.2 wheel
used on Linux/CPython 3.12 was 4,949,713 bytes. Its first download is separate from
Gradle preparation time. Maintainers running existing font verification already need
this dependency; ordinary static-vector source builds do not.

These normalized local archives compare fresh source snapshots, not the repository's
complete Git history. A later deletion commit leaves earlier vector blobs in history;
normal full clones retain them. A squash before merge or a shallow checkout has a
different download profile. Published Maven dependencies are another category again.

## Published artifacts

All 12 JVM JAR, Android release AAR, and JVM source JAR files were compared by SHA-256.
The static and fully generated variants matched byte for byte, including filenames.
All 251 regenerated Kotlin files also matched the original source hashes.

| Module | JVM JAR bytes (static and generated) | Android AAR bytes | JVM source JAR bytes |
| --- | ---: | ---: | ---: |
| Outlined | 15,203,647 | 14,460,799 | 1,247,643 |
| Rounded | 16,358,224 | 15,642,727 | 1,802,697 |
| Sharp | 14,665,543 | 13,960,986 | 1,066,667 |
| Themed | 830,861 | 786,757 | 228,800 |

| Sum across the four modules | Static / generated | Middle ground | Middle-ground increase |
| --- | ---: | ---: | ---: |
| JVM JAR bytes | 47,058,275 | 47,377,374 | 319,099 (0.68%) |
| Android AAR bytes | 44,851,269 | 45,094,172 | 242,903 (0.54%) |
| JVM source JAR bytes | 4,345,807 | 4,389,254 | 43,447 (1.00%) |

These are built local publication artifacts, without Maven metadata or transitive
dependencies. An Android consumer downloads the selected AAR variants, not both AAR
and JVM JAR columns. Source attachments are a separate optional IDE download.

The middle ground changes implementation bytecode: objects that were file-private
become `internal` so checked-in getters can reference generated files. Public Kotlin
getters retain their original facade filenames, but implementation visibility and
source navigation differ. This is not claimed to be byte-identical or a blanket ABI
guarantee.

## Build timings

Seconds below are **median [minimum–maximum], three trials per approach**. These
are descriptive results on the shared host described below, not statistically
established speedups. The target-clean workload builds JVM and Android outputs in
one invocation; it is not a single-platform or whole-repository clean build.

| Scenario | Static | Build-time generation | Middle ground |
| --- | ---: | ---: | ---: |
| Target-clean wall time | 202.94 [188.77–210.79] | 210.40 [202.99–211.69] | 197.42 [192.01–209.61] |
| No-op wall time, configuration cache disabled | 5.05 [4.76–5.16] | 5.12 [5.01–5.15] | 5.19 [5.00–5.29] |
| No-op wall time, configuration cache reused | 0.636 [0.618–0.671] | 0.623 [0.622–0.626] | 0.623 [0.622–0.624] |
| Handwritten themed body edit | 17.47 [16.67–17.52] | 17.09 [16.94–17.36] | 17.10 [16.76–17.71] |
| Restore original body | 18.31 [17.87–19.75] | 18.73 [18.11–19.40] | 18.70 [18.46–18.92] |
| Vector compilation task total in clean builds | 193.58 [179.97–200.32] | 190.75 [183.22–191.82] | 188.00 [181.69–199.76] |
| Source preparation task total in clean builds | 0 | 10.37 [10.25–10.43] | 0.127 [0.123–0.129] |

Gradle profiles show where the cost sits: compiling the vector packs for JVM and
Android accounts for roughly 188–194 seconds at the median. Full generation adds
about 10.4 seconds of preparation. Extracting the middle-ground archives takes about
0.13 seconds, but it leaves essentially the same compilation workload.

The observed clean-wall median difference is +7.47 seconds for full generation and
−5.52 seconds for the middle ground versus static. The overlapping ranges and known
host load do **not** establish either number as an isolated causal effect. In
particular, the slightly lower compilation medians cannot be attributed to moving
identical source files. No-op and handwritten incremental builds are very similar.
Font or manifest edits were not included in the incremental scenario; those can
regenerate affected packs and have a different invalidation scope.

### Cache restoration

After seeding the host's shared local build cache and deleting the four vector
modules' build directories again, wall times were **9.77 seconds static, 7.00 seconds
generated, and 9.27 seconds hybrid**. These are single probes, not repeated medians;
cache warmth/order differs, so their ordering is not a speed ranking.

All eight vector JVM/Android compilation tasks reported `FROM-CACHE`. The generated
variant also restored all four source-generation tasks from cache. Seeding was not a
cold-cache comparison: full generation reused compilation entries created by the
static variant, while the hybrid's different implementation needed compilation.
The meaningful result is that caching compiler outputs cuts the roughly 200-second
workload to roughly 7–10 seconds in all three approaches.

The repository enables configuration caching by default. In a separate probe, the
same artifact request reused its configuration cache successfully in all modes;
three no-op repeats took about **0.62–0.64 seconds** at the median, as shown above.
The roughly five-second no-op row is the deliberately cache-disabled comparison,
not the normal configured workflow. Build-cache restoration and configuration-cache
reuse are separate measurements.

### Shrunk application check

The existing Android fixture directly calls the Outlined `Check` getter from Java,
and exercises drawable/font removal. All **20 existing R8/resource assertions passed
in each approach**, including keeping the selected icon and removing unused `Home`.

| Fixture metric | Static | Build-time generation | Middle ground |
| --- | ---: | ---: | ---: |
| Unshrunk APK bytes | 8,009,164 | 8,009,164 | 7,992,780 |
| Shrunk APK bytes | 220,213 | 220,213 | 220,213 |
| Unshrunk DEX bytes | 25,808,876 | 25,808,876 | 25,816,336 |
| Unshrunk DEX class definitions | 20,363 | 20,363 | 20,423 |
| Shrunk DEX bytes | 137,392 | 137,392 | 137,144 |
| Shrunk DEX class definitions | 183 | 183 | 181 |

Static and full-generation APKs are byte-identical for both variants. The hybrid
APKs differ in content, despite the same shrunk file size; packaging/compression and
R8 change the relationship between class count and APK bytes. This single-icon fixture
supports preserved shrinking, not a promise about every application's retained set.
It is a build/artifact check, not a new on-device rendering benchmark.

## API availability and developer workflow

A developer using a published dependency writes the same code in all three approaches:

```kotlin
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.Outlined
import io.github.hlcaptain.symbols.material.outlined.vectors.Home

val icon = Symbols.Material.Outlined.Home
```

After dependency resolution the API is available from the compiled library. Application
builds do not need Python or regenerate the dependency's vectors. Moving generation
inside the library build does not defer that work to every consuming app.

For a contributor opening this repository:

- **Static:** named API and implementation exist immediately on disk. The IDE still
  needs its normal Gradle/dependency sync to resolve Compose and project dependencies.
- **Generated:** named vector sources do not exist in a fresh checkout. Run source
  preparation or wire it into IDE import; a full compilation is not inherently
  necessary. After successful preparation the IDE has ordinary Kotlin source roots.
- **Middle ground:** named declarations exist immediately, but their references to
  implementation objects remain unresolved until extraction. This improves browsing
  and code search before preparation; it is not a completely resolved pre-sync project.

The prototype uses a task-backed `commonMain` source directory, with preparation
attached to `prepareKotlinIdeaImport` where available. JetBrains documents this import
hook in its [generated-source tracking issue](https://youtrack.jetbrains.com/issue/KT-45161).
After deleting their generated source roots, both variants successfully ran the
four module-level `prepareKotlinIdeaImport` tasks. The generated mode took **14.68
seconds** and the hybrid **2.60 seconds** in one probe each. Neither invoked the
vector JVM/Android compilation tasks. This verifies command-line import-task wiring;
actual Android Studio indexing and completion latency were not measured.

Explicit `prepareVectorSources` invocations, with generated roots removed before each
of three repeats and both caches disabled, took **12.47 [12.44–12.68] seconds** for
full generation and **2.22 [2.21–2.23] seconds** for the hybrid, including Gradle startup
and configuration. The remaining 373 handwritten Kotlin lines in the fully generated
checkout therefore do not require a roughly 210-second compilation just to populate
its API sources.

Android XML drawable packs and Compose drawable-resource packs are separate modules.
They already generate their resources/accessors under `build/`; this proposal neither
removes checked-in drawable APIs nor newly imposes generation on those modules.

## Measurement method

The [reproduction harness](../benchmarks/vector-generation/README.md) creates three
isolated copies of the same committed baseline and changes only source distribution
and task wiring inside those copies. No production module migration is included.

Host: AMD Ryzen 9 7950X3D, 32 logical CPUs, about 62 GiB RAM; Linux; Zulu JDK 21.0.12.1;
Gradle 8.14.5; Kotlin 2.3.21; FontTools 4.60.2. Work directories were on `/tmp` tmpfs.
An 8 GB Gradle heap, one worker, and in-process Kotlin compilation were held constant.
Dependency/configuration bootstrap was excluded from the repeated comparison.
This was a shared workstation with other CPU-intensive applications active during
the run, not an idle benchmark host. Rotating order and reporting ranges help expose
variation; small timing differences must not be treated as isolated causal effects.

A target-clean trial deletes only the four vector modules' build directories and
builds all four JVM JARs, Android release AARs, and JVM source JARs. Dependent module
outputs and downloaded dependencies remain warm. No-op trials repeat that command;
incremental trials change one handwritten themed function body, then restore it.
The main series disables configuration and build caches; separate cache probes
measure restoration. Gradle's [build cache](https://docs.gradle.org/current/userguide/build_cache.html)
reuses declared task outputs, so cache hits must be distinguished from source generation.

This measures contributor builds, not drawing speed, Android Studio indexing, peak
memory, cold dependency provisioning, or default parallel/CI wall time. The three
approaches still compile approximately the same Kotlin geometry.

## Validation

- All 251 fully generated Kotlin files matched the baseline SHA-256 hashes; all
  16,408 named getter declarations matched across the three approaches.
- The four existing JVM suites passed **11 tests per approach, 33 total**, covering
  vector metadata, all catalog names/codepoints, aliases, cached identity, mirroring,
  and theme selection.
- All 12 local JVM JAR/AAR/source JAR artifacts and both fixture APKs were byte-identical
  between static and full generation. Hybrid differences are reported above.
- The existing 20 R8/resource checks passed in each approach; cache restoration and
  command-line IDE source preparation succeeded in all applicable probes.

These checks cover the measured JVM/Android builds. JS/Wasm/iOS publication,
actual Android Studio completion, and on-device rendering are not newly validated
by this source-distribution experiment.

## Recommendation

For reducing this PR's generated-code footprint, full build-time generation is the
useful option: almost all of the four modules' checked-in Kotlin disappears while
ordinary library usage stays the same. Treat the benefit as cleaner review and a
smaller source tree, with a modest compressed source-download reduction. Do not sell
it as smaller published libraries or elimination of compilation work.

Keep static files if immediately browsable source and a contributor build without
Python/FontTools are more valuable. There is no runtime or consumer-download penalty
relative to generating those exact files during the library build.

The measured middle ground is a poor trade solely for download size: it saves only
0.40% of the full source archive, slightly grows compiled artifacts, and introduces
archive maintenance plus implementation visibility changes. It is reasonable only
if retaining named declarations in Git while hiding geometry is itself a requirement.
Its extraction approach avoids Python in normal builds, but font updates still need
the pinned maintainer generator to refresh the archives.

Before adopting full generation, make the task wiring production-ready: declare
portable inputs/outputs and tool versions, remove stale outputs when catalog entries
are deleted, provision FontTools in every source-building CI job, attach generated
sources to every compilation/publication target, and validate a fresh IDE import.
Retain source attachments, independent per-glyph ownership, alias identity, mirroring,
and the existing dynamic lookup behavior. The isolated experiment proves source
identity and measures the tradeoff; it does not migrate production or establish all
multiplatform publication and IDE guarantees.
