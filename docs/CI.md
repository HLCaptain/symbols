# GitHub-hosted CI

All workflows use standard GitHub-hosted runners. No jobs request the Mac mini
or another self-hosted host.

| Jobs | Runner | Reason |
| --- | --- | --- |
| Generators, JVM/Android, web, publication, release bookkeeping | `ubuntu-24.04` | 4 CPUs/16 GB RAM for this public repository |
| Apple sample framework and screenshots | `macos-26-intel` | Apple SDK and consistent screenshot baselines |

Apple library archives can be cross-compiled on Linux with the current Kotlin
configuration. The Apple verification job uses Ubuntu for publication-only runs;
normal PR/main runs use macOS to also link the sample framework. See GitHub's
[runner specifications](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
and Kotlin's [publication host requirements](https://kotlinlang.org/docs/multiplatform-publish-lib.html#host-requirements).

## Fresh-runner setup

- Install Python 3.13 and pinned FontTools through `setup-vector-python`.
- Install JDK 17/21 through the pinned Java action, leaving JDK 21 active.
- Install Android command-line tools, the version-catalog compile SDK, and Build
  Tools 35.0.0/36.0.0 (AGP 8 compatibility fixtures and AGP 9 builds). Root Gradle builds configure Android projects
  even when the selected task targets web or Apple.
- Select Xcode 26.4.1 through `DEVELOPER_DIR` on macOS. It matches the Kotlin 2.4.20
  [compatibility line](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html).
- Configure one Gradle worker and in-process Kotlin compilation. Heavy jobs have
  a 6 GB shared heap; Central tooling publication uses 4 GB. These overrides are
  written into the disposable runner's Gradle user properties, not developer setup.

Web compilation/linking uses 8 GB in separate, terminating Gradle invocations.
The linking command excludes `:composeApp:wasmJsProductionExecutableCompileSync`:
that finalizer otherwise invokes Binaryen while the large compiler JVM is alive.
The following webpack invocation performs optimization with a fresh 2 GB Gradle
heap, `BINARYEN_CORES=2`, and a 4 GB Node heap. Kotlin's optimization passes and the
full production sample are retained. The sample also caps Binaryen's combined
inlining size at 32 KiB to avoid quadratic local-variable coalescing on generated
initializers. Against the same linked Wasm input, this reduced observed optimizer
RSS from about 20.5 GiB to 1.3 GiB; the output grew by only 7 bytes. The direct
optimizer test took 65 seconds; the actual Gradle optimization invocation took
1m23s versus 6m36s before the inlining bound. Thread limits alone did not solve that
memory spike.
This setting affects the sample executable, not published library code.

JVM/Android and web verification have 90-minute limits. Central library upload/build also
has a 90-minute step limit. Central publication is asynchronous: after validated
upload, a separate read-only step waits up to two hours for public coordinates.
The library/tooling publication jobs allow 240/210 minutes for this server-side
queue; the CI build limits remain unchanged. The build commands
wrapped by `tools/ci_metrics.py` stream normal output and preserve failure status;
they report elapsed time and sampled combined Java/wasm-opt RSS and wasm-opt RSS
in logs and job summaries. Samples cover those processes on the isolated runner,
not reserved heap sizes, and may miss short-lived peaks. Missing metrics never hide
a build failure or fail an otherwise successful build. GitHub Packages testing
snapshots allow 180 minutes for the library job, including a 150-minute native
Gradle publication step. Tooling retains 120/90-minute job/publication limits.
Both finish with a 15-minute authenticated availability check.

## Verification coverage

Archive checks run beside the matching platform compilation instead of pulling
all platforms into the JVM job:

| Gradle task | Current archive count | Coverage |
| --- | --- | --- |
| `verifyJvmAndAndroidPublishedArchives` | 105 | JVM, Android and common metadata |
| `verifyWebPublishedArchives` | 92 | JS and Wasm libraries/resources |
| `verifyApplePublishedArchives` | 126 | iOS device and simulator libraries/resources |

`verifyPublishedArchives` remains the aggregate of all three tasks. Every selected
archive keeps the existing legal-notice, font-count and drawable checks. Unknown
archive types fail configuration rather than silently entering the wrong job.
`verifyLibraryJvm` combines the JVM/Android archive verifier with published modules'
JVM tests and Android lint, excluding repository sample and benchmark projects.

JVM/Android CI also runs `python3 tools/check_signed_publications.py` with a
disposable signing key and isolated Maven local repository. This checks signature
output ownership across all publications and exercises seven signed publications
of a small real KMP library, without release credentials or sample compilation.

PR and main CI retain all sample compilation, production web bundling, Apple
framework linking, and Android shrinking checks. Publication calls the same
workflow with `publication-only: true`: it verifies generators, convention/plugin
tooling, library tests/lint, and every platform's archives, without compiling sample
apps, linking sample executables/frameworks, or building benchmark APKs. This input
defaults to false, so normal CI coverage is unchanged. The manual CI trigger exposes
this same input for testing publication verification without uploading packages:

```shell
gh workflow run ci.yml --ref BRANCH -f publication-only=true
```

Local profiling of PR #19 before this optimization used fresh source checkouts,
one worker, in-process Kotlin, and disabled build/configuration caches. Dependency
downloads were already cached. On a Ryzen 9 7950X3D with 64 GB RAM, full local
publication took 14m37s and JS/Wasm compilation took 5m16s; the previous Intel-hosted
web compilation took 38m. All 122 local publication coordinates, including 34 Apple
KLIBs, were checked. These measurements establish feasibility, not predicted hosted
timings. Hosted checks must finish within the stated limits before merging.

## Main commit snapshots

`snapshots.yml` starts a testing-snapshot publication pinned to each main push's
head SHA, without historical backfill. Its concurrency group
includes the SHA and uses `cancel-in-progress: false`; newer pushes do not replace
an older commit's run. The trigger is `push`, not completion of normal CI, so a
cancelled ordinary CI run does not suppress that commit's publication verification.

The workflow creates an immutable source tag named
`<current-release>-SNAPSHOT-<8sha>`, such as
`2.0.0-SNAPSHOT-b3ad4223`, using the highest numeric stable ancestor tag as its
release prefix. It calls `publish.yml` through `workflow_call` with that tag,
retaining publication-only verification of the tag's full source SHA. Preflight
also checks `main` ancestry and the eight-character hash suffix. These testing
versions go **only to GitHub Packages** at
`https://maven.pkg.github.com/hlcaptain/symbols`, through existing native Gradle
Maven publications in both builds, including the dotted plugin marker.

Snapshot publisher jobs use `GITHUB_TOKEN` with `packages: write`; they do not
enter the `maven-central` environment or receive Central/Portal credentials or
release signing keys. Normal releases retain their Central/Portal route and
credentials. Central rejected the earlier
[`2.0.0-SNAPSHOT-b3ad4223` attempt](https://github.com/HLCaptain/symbols/actions/runs/37058789197)
with "The version cannot be a SNAPSHOT". The testing channel preserves the
requested version spelling rather than renaming it to bypass that policy.

Tag existence is not publication success. After authenticated package checks
succeed, the resulting GitHub Release is a prerelease with `latest=false`.
Snapshot recovery can run a newer reviewed publisher ref while checking out the
unchanged source tag separately. Successful GitHub job receipts are matched to
the component, version, and full artifact source SHA, not just the workflow's
head SHA. By default, without such a receipt, any existing expected POM or
declared publication file aborts the upload.

Manual testing-snapshot recovery can supply `library-publications`: a
newline-separated list of exact configured native GitHub Maven publication
task paths. Full source verification remains required. The init script validates
the selection and produces its file manifest; any existing selected file still
aborts publication. Only the validated native tasks are executed, leaving
existing versioned publications and the source tag unchanged. The final check
still requires all 122 library POMs before a whole-library success receipt or
GitHub Release. Normal releases and automatic snapshots do not use this mode.

The [first GitHub recovery run](https://github.com/HLCaptain/symbols/actions/runs/37075726956)
timed out at the former 90-minute library limit after source verification and
tooling publication passed. Inspection found 105/122 library POMs present;
Themed common/JVM/Wasm and all Core/Variant Font Core publications were absent.
All 97 declared paths for those 17 publications returned authenticated 404 at
2026-10-03 01:28:46 UTC. This is a partial publication, not success. The larger
library budget and explicit absent-publication recovery preserve the existing
verification and no-overwrite guards. See
[the recovery procedure](../RELEASING.md#recover-entirely-absent-library-publications).

Snapshot tag creation/release bookkeeping need `contents: write`; snapshot
publication needs `packages: write`. Normal PR CI remains read-only. A failed
verification can leave the tag in place; recover its exact sources without
moving it. See
[Releasing](../RELEASING.md#main-commit-snapshots) for version selection and manual
recovery, and [consumer setup](../RELEASING.md#consuming-testing-snapshots) for
authenticated dependency and plugin resolution. New pushes use this route after
the updated publisher reaches `main`; these instructions do not establish that
any particular testing version is already available.

## Storage

Actions cache reads/writes are disabled. There are no `upload-artifact`,
`download-artifact`, or dependency-snapshot uploads, and no automatic Build Scans.
Python/pip does not use an Actions cache. Gradle can reuse local outputs within
one job, but they disappear with the VM; other jobs rebuild from source.

Existing stored artifacts/caches are not deleted by this migration. It avoids
adding that storage dependency rather than spending the remaining quota or
silently removing old results. Logs and text job summaries remain available.

Roborazzi publishes only changed, added, and removed screenshot profiles to
short-lived orphan report branches for same-repository PRs, using Git storage
instead of Actions artifact storage. One PR comment groups the changes and links
public immutable images; unchanged previews are omitted. A passing no-change run
updates an existing comment without creating a new one and removes any stale
report branch. PR closure also removes the branch. No images enter
development-branch history. Fork PRs run read-only comparisons. Adding or removing
the human `ui-review-approved` label reruns screenshot verification; technical
failures cannot be approved away. [Screenshot testing](SCREENSHOT_TESTING.md)
describes report access, approval, cleanup, and local reproduction.

## Before making the repository public

1. Merge the release PR, then this runner migration, and verify hosted CI results.
2. Remove this repository's self-hosted runner registration (or restrict its runner
   group so public PR workflows cannot select it). Merely changing `runs-on` in
   today's workflows does not remove the registered machine.
3. Keep fork PR approval controls and read-only PR tokens. No publication secrets
   are passed to normal PR CI, and workflows do not use `pull_request_target`.
4. Add the two Plugin Portal secrets documented in [Releasing](../RELEASING.md),
   then change repository visibility when ready. No release should be tagged until
   the public source/documentation and publisher accounts are ready.

This PR changes workflow code; it does not change repository visibility, delete
runner registrations, accept visual changes, or publish a release.
