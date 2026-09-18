# GitHub-hosted CI

All workflows use standard GitHub-hosted runners. No jobs request the Mac mini
or another self-hosted host.

| Jobs | Runner | Reason |
| --- | --- | --- |
| Generators, JVM/Android, web, publication, release bookkeeping | `ubuntu-24.04` | 4 CPUs/16 GB RAM for this public repository |
| Apple sample framework and screenshots | `macos-15-intel` | Apple SDK and consistent screenshot baselines |

Apple library archives can be cross-compiled on Linux with the current Kotlin
configuration. The Apple verification job uses Ubuntu for publication-only runs;
normal PR/main runs use macOS to also link the sample framework. See GitHub's
[runner specifications](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
and Kotlin's [publication host requirements](https://kotlinlang.org/docs/multiplatform-publish-lib.html#host-requirements).

## Fresh-runner setup

- Install Python 3.13 and pinned FontTools through `setup-vector-python`.
- Install JDK 17/21 through the pinned Java action, leaving JDK 21 active.
- Install Android command-line tools, the version-catalog compile SDK, and Build
  Tools 35.0.0 (the AGP 8.13 default). Root Gradle builds configure Android projects
  even when the selected task targets web or Apple.
- Select Xcode 26.0.1 through `DEVELOPER_DIR` on macOS. It matches the Kotlin 2.3.21
  [compatibility line](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html).
- Configure one Gradle worker and in-process Kotlin compilation. Heavy jobs have
  a 6 GB shared heap; Ubuntu tooling publication uses 4 GB. These overrides are
  written into the disposable runner's Gradle user properties, not developer setup.

Web compilation/linking uses 8 GB in separate, terminating Gradle invocations.
The linking command excludes `:composeApp:wasmJsProductionExecutableCompileSync`:
that finalizer otherwise invokes Binaryen while the large compiler JVM is alive.
The following webpack invocation performs optimization with a fresh 2 GB Gradle
heap, `BINARYEN_CORES=2`, and a 4 GB Node heap. Kotlin's optimization passes and the
full production sample are retained.

JVM/Android and web verification have 90-minute limits. Library publication has a
120-minute limit, including Central validation/publication waits. The build commands
wrapped by `tools/ci_metrics.py` stream normal output and preserve failure status;
they report elapsed time and sampled combined Java/wasm-opt RSS and wasm-opt RSS
in logs and job summaries. Samples cover those processes on the isolated runner,
not reserved heap sizes, and may miss short-lived peaks. Missing metrics never hide
a build failure or fail an otherwise successful build.

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

## Storage

Actions cache reads/writes are disabled. There are no `upload-artifact`,
`download-artifact`, or dependency-snapshot uploads, and no automatic Build Scans.
Python/pip does not use an Actions cache. Gradle can reuse local outputs within
one job, but they disappear with the VM; other jobs rebuild from source.

Existing stored artifacts/caches are not deleted by this migration. It avoids
adding that storage dependency rather than spending the remaining quota or
silently removing old results. Logs and text job summaries remain available.

Roborazzi publishes PNG galleries/diffs to short-lived orphan report branches for
same-repository PRs, using Git storage instead of Actions artifact storage. One PR
comment links public immutable image URLs; PR closure removes the report branch.
No images enter development-branch history. Fork PRs run read-only comparisons.
The `ui-review-approved` human gate remains; [screenshot testing](SCREENSHOT_TESTING.md)
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
