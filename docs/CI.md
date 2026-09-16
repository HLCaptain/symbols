# GitHub-hosted CI

All workflows use standard GitHub-hosted runners. No jobs request the Mac mini
or another self-hosted host.

| Jobs | Runner | Reason |
| --- | --- | --- |
| Generator/font checks, release preflight, tooling publication, GitHub release | `ubuntu-24.04` | Lightweight checks or JVM-only tooling |
| JVM/Android, web compilation, Apple, screenshots, library publication | `macos-15-intel` | 14 GB RAM in both private and public repositories; native Apple SDK support |

The standard Apple Silicon runner has 7 GB RAM. The Intel choice gives the large
vector compilations room without selecting a paid larger runner. See GitHub's
[runner specifications](https://docs.github.com/en/actions/reference/runners/github-hosted-runners).
Hosted jobs still consume the account's runner allowance while the repository is
private; standard public-repository jobs follow GitHub's public-runner policy.

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
Webpack then uses a 2 GB Gradle heap and a 4 GB Node heap with those compilation
outputs already built. This avoids overlapping a large compiler heap with a large
webpack heap. The exact Kotlin production-link/optimization tasks are explicit.

## Storage

Actions cache reads/writes are disabled. There are no `upload-artifact`,
`download-artifact`, or dependency-snapshot uploads, and no automatic Build Scans.
Python/pip does not use an Actions cache. Gradle can reuse local outputs within
one job, but they disappear with the VM; other jobs rebuild from source.

Existing stored artifacts/caches are not deleted by this migration. It avoids
adding that storage dependency rather than spending the remaining quota or
silently removing old results. Logs and text job summaries remain available.

Screenshots are also ephemeral. The existing `ui-review-approved` human gate is
preserved; [screenshot testing](SCREENSHOT_TESTING.md) explains how to reproduce
base/head images locally. No PNGs, traces, or binary reports are committed or uploaded.

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
