# Releasing Symbols

Symbols publishes stable Kotlin Multiplatform libraries, Android drawable AARs,
and JVM build tooling automatically to Maven Central from release tags. The Gradle
plugin is also submitted to the Plugin Portal, and GitHub release notes are created
after the publication jobs succeed.

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

Merge the release automation and hosted-runner PRs before opening the repository.
Then tag a commit already on `main` using canonical `vMAJOR.MINOR.PATCH`:

```shell
git tag -a v0.1.0 -m "Symbols 0.1.0"
git push origin v0.1.0
```

Alternatively, create and publish a GitHub Release for that tag in the GitHub UI.
Both events use the same serialized workflow. Stable tags are supported; snapshot
and prerelease tag suffixes are rejected. The tag supplies `VERSION_NAME` for
publication, so no separate edit to `gradle.properties` is necessary. Its snapshot
version remains the default for local development and `publishToMavenLocal`.

The **Publish packages** workflow:

1. Resolves the tag to an immutable commit and checks its ancestry on `main`.
2. Checks existing successful publication jobs and public registry coordinates,
   and validates the release credentials before any upload.
3. Runs the complete CI workflow against that exact commit.
4. Publishes both Central aggregations with NMCP `AUTOMATIC` publishing, waiting
   for validation/publication and for public Maven coordinates to become visible.
5. Submits `io.github.hlcaptain.symbol-fonts` to the Gradle Plugin Portal after its
   generator dependency is available on Central.
6. Creates a GitHub Release with generated notes. Existing public release notes
   are preserved; an existing draft is published. If the release was created in
   the UI, it already exists while the packages are being published.

No tags are moved and no release is created by PR or branch pushes. Use a new
version for subsequent releases; registry versions are immutable. Update
`CHANGELOG.md` before tagging; generated GitHub notes summarize merged PRs.

## Retries and partial failures

Prefer **Re-run failed jobs**. The manual workflow trigger also accepts an existing
tag, allowing recovery if the final GitHub Release step failed. Run it from the
same tag ref so GitHub's publication checks remain attached to the released commit:

```shell
gh workflow run publish.yml --ref v0.1.0 -f tag=v0.1.0
```

A manual run from a different commit is rejected. Tag and release
events for the same version are serialized, with active publication never cancelled.
Successful publication jobs on the same commit and tag prevent duplicate uploads,
including a Portal submission still awaiting its first review. Public coordinates
provide a fallback if old workflow history is gone. A partly visible Central bundle
fails preflight rather than attempting to overwrite existing versions.

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

The repository's existing macOS runner handles all Apple publications in one
library publication job. The follow-up runner migration replaces that host without
changing the publication boundary.

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
