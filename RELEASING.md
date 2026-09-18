# Releasing Symbols

Symbols publishes Kotlin Multiplatform libraries, Android drawable AARs,
and JVM build tooling automatically to Maven Central when a GitHub Release is
published. The Gradle plugin is also submitted to the Plugin Portal. The release
tag supplies the package version, and existing release notes are preserved.

## Published artifacts

The Kotlin Multiplatform libraries have umbrella coordinates under
`io.github.hlcaptain`:

- `symbols-core`
- `symbols-variant-font-core`
- `symbols-material-core`
- `symbols-material-compose`
- `symbols-material-outlined`
- `symbols-material-rounded`
- `symbols-material-sharp`
- `symbols-material-outlined-static`
- `symbols-material-rounded-static`
- `symbols-material-sharp-static`
- `symbols-material-compose-drawables-outlined`
- `symbols-material-compose-drawables-rounded`
- `symbols-material-compose-drawables-sharp`
- `symbols-material-vectors-outlined`
- `symbols-material-vectors-rounded`
- `symbols-material-vectors-sharp`
- `symbols-material-vectors-themed`

Gradle module metadata selects the target-specific Android, JVM, JS, Wasm, or
Apple artifact for consumers.

The native resource packs are Android-only AAR coordinates:

- `symbols-material-drawables-outlined`
- `symbols-material-drawables-rounded`
- `symbols-material-drawables-sharp`

The tooling build separately publishes `symbol-generator-core`,
`symbol-gradle-plugin`, and the `io.github.hlcaptain.symbol-fonts` plugin marker.

Artifacts released through Maven Central need only `mavenCentral()`.

## One-time credentials

The `maven-central` GitHub environment supplies all release credentials:

| Secret | Purpose |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal user-token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user-token password |
| `SIGNING_KEY` | ASCII-armored private PGP signing key |
| `SIGNING_PASSWORD` | Signing-key password |
| `GRADLE_PUBLISH_KEY` | Gradle Plugin Portal API key |
| `GRADLE_PUBLISH_SECRET` | Gradle Plugin Portal API secret |

Verify the `io.github.hlcaptain` Central namespace and publish the signing key's
public key. Create a Plugin Portal account linked to the publishing GitHub account,
then generate its API credentials in the Portal account's API Keys page. Store
credentials in GitHub secrets, never in Git or release notes. The workflow checks
all six names before publishing anything. Required environment reviewers, if
configured, still apply; omit that rule if releases should run unattended.

The Portal's [first-plugin review](https://plugins.gradle.org/docs/publish-plugin)
can delay public availability after a successful submission. Public source and
documentation must be accessible for that review. The release workflow automates
the submission; it cannot bypass Gradle's approval process. The plugin marker is
also published to Central, so consumers can resolve it through `mavenCentral()`.

## Create a release

Tag a commit already on `main` using `MAJOR.MINOR.PATCH`, optionally followed by
a lowercase alphanumeric qualifier such as `-alpha01`, `-beta01`, or `-rc01`:

```shell
git tag -a 0.1.0 -m "Symbols 0.1.0"
git push origin 0.1.0
gh release create 0.1.0 --verify-tag --generate-notes --title 0.1.0
```

Alternatively, create the tag and publish its GitHub Release in the GitHub UI.
Only the release `published` event starts automatic package publication; pushing
a tag alone does not start a second run. This includes prereleases (use
`gh release create ... --prerelease --latest=false` for a qualifier).
Tags have no `v` prefix, and the three numeric components cannot have leading
zeros. Qualifiers start with a lowercase letter; `SNAPSHOT` (in any case) and build metadata (`+...`) are rejected. For example,
`1.0.0-alpha01` publishes version `1.0.0-alpha01` to both repositories. The complete
tag supplies `VERSION_NAME` for publication, so no separate edit to
`gradle.properties` is necessary. Its snapshot version remains the default for
local development and `publishToMavenLocal`.

The **Publish packages** workflow:

1. Resolves the tag to an immutable commit and checks its ancestry on `main`.
2. Checks existing successful publication jobs and public registry coordinates,
   and validates the release credentials before any upload.
3. Verifies generators, build tooling, library JVM tests/Android lint, and every
   platform's publication archives against that exact commit. Sample applications,
   production sample bundles/frameworks, and benchmark APKs stay in PR/main CI;
   they are not compiled as part of publication verification.
4. Publishes both Central aggregations with NMCP `AUTOMATIC` publishing, waiting
   for validation/publication and for public Maven coordinates to become visible.
5. Submits `io.github.hlcaptain.symbol-fonts` to the Gradle Plugin Portal after its
   generator dependency is available on Central.
6. Creates a GitHub Release with generated notes. Existing public release notes
   are preserved; an existing draft is published. The tag determines the release
   type: tags with a qualifier become prereleases and are not marked latest.
   If creating a prerelease in the UI, select **Set as a pre-release** too; it
   already exists while the packages are being published.

No tags are moved and no release is created by PR or branch pushes. Use a new
version for subsequent releases; registry versions are immutable. Update
`CHANGELOG.md` before tagging; generated GitHub notes summarize merged PRs.

## Retries and partial failures

Prefer **Re-run failed jobs**. The manual workflow trigger also accepts an existing
tag, allowing recovery if the final GitHub Release step failed. Run it from the
same tag ref so GitHub's publication checks remain attached to the released commit:

```shell
gh workflow run publish.yml --ref 0.1.0 -f tag=0.1.0
```

A manual run from a different commit is rejected. Release events and manual retries
for the same version are serialized, with active publication never cancelled.
Successful publication jobs on the same commit and tag prevent duplicate uploads,
including a Portal submission still awaiting its first review. Public coordinates
provide a fallback if old workflow history is gone. A partly visible Central bundle
fails preflight rather than attempting to overwrite existing versions.

Re-running an old release uses the workflow from its original tag, including its
timeouts. Workflow fixes merged later into `main` apply to new tags. Do not move an
existing release tag to pick up a fix; publish a new version from the fixed commit.

If Central accepted an upload but a publishing/visibility timeout occurred, inspect
the deployment in Central Portal and wait for it to finish before rerunning. The two
registries and two Central aggregations cannot form one atomic transaction: already
published components remain published when a later component fails. Never retag a
failure to another commit. A successful Portal submission may still need approval;
retain its successful workflow run until the Portal version is publicly visible.

## Storage and validation

Packages go directly to Maven Central and the Plugin Portal. GitHub Release notes
are text only. There are no uploaded Actions artifacts, intermediary bundle
transfers, or GitHub Actions dependency caches. Build outputs stay on the runner;
CI jobs rebuild from the same commit rather than sharing artifact storage.

The standard `ubuntu-24.04` GitHub runner publishes the complete library
aggregation, including Apple KLIB artifacts, in one job with a 120-minute limit.
Publication verification and tooling publication also use Ubuntu; the macOS sample
framework is checked by normal PR/main CI. No self-hosted runner is needed. Hosted provisioning
and the public-repository handoff are documented in [CI](docs/CI.md).

Local checks that do not publish:

```shell
python3 -m unittest discover -s tools/tests -p test_release.py
./gradlew -p tooling -PVERSION_NAME=0.1.0 \
  :symbol-gradle-plugin:validatePlugins \
  :symbol-gradle-plugin:generatePomFileForPluginMavenPublication \
  :symbol-gradle-plugin:generatePomFileForSymbolFontsPluginMarkerMavenPublication \
  :symbol-generator-core:generatePomFileForMavenPublication
```

`publishPlugins --validate-only` additionally checks Portal publishing configuration
and requires Portal credentials, even though it does not upload. The
[NMCP configuration](https://gradleup.com/nmcp/) now uses automatic release with
30-minute validation and publication timeouts. Running
`publishAggregationToCentralPortal` with real credentials therefore publishes
immediately; use `publishToMavenLocal` for offline packaging checks.
