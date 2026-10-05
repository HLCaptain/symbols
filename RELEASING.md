# Releasing Symbols

Testing snapshots are pinned to one commit and published **only to GitHub
Packages**, at `https://maven.pkg.github.com/hlcaptain/symbols`, using the exact
`MAJOR.MINOR.PATCH-SNAPSHOT-<8 lowercase hex digits>` format. This testing channel
covers the Kotlin Multiplatform libraries, Android drawable AARs, and JVM build
tooling. Normal releases continue to use Maven Central and the Gradle Plugin
Portal.

The immutable source tag supplies the complete package version. Existing release
notes are preserved. A tag alone does not establish package availability; check
the publication job and authenticated registry verification before consuming it.

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

Each style also has independent `-filled`, `-automirrored`, and
`-automirrored-filled` AARs. For example,
`symbols-material-drawables-rounded-automirrored-filled` contains only the
Rounded mirrored-filled family. No native pack depends on another family.
All twelve native coordinates participate in Central publication and release
availability checks. See [variant dependencies](docs/GENERATOR.md#native-drawable-variants).

The tooling build separately publishes `symbol-generator-core`,
`symbol-gradle-plugin`, and the `io.github.hlcaptain.symbol-fonts` plugin marker.
Its marker coordinates remain
`io.github.hlcaptain.symbol-fonts:io.github.hlcaptain.symbol-fonts.gradle.plugin`;
the dotted artifact ID is unchanged in GitHub Packages.

Normal artifacts released through Maven Central need only `mavenCentral()`.
Testing snapshots require the authenticated repository configuration below.

## One-time credentials

For normal releases, the `maven-central` GitHub environment supplies:

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
all six names before normal-release publication. Required environment reviewers,
if configured, still apply; omit that rule if releases should run unattended.

The Portal's [first-plugin review](https://plugins.gradle.org/docs/publish-plugin)
can delay public availability after a successful submission. Public source and
documentation must be accessible for that review. The release workflow automates
the submission; it cannot bypass Gradle's approval process. The plugin marker is
also published to Central, so consumers can resolve it through `mavenCentral()`.

Testing-snapshot jobs instead use the workflow's `GITHUB_TOKEN` with
`packages: write`. They pass the actor/token to Gradle through the
`GitHubPackagesUsername` and `GitHubPackagesPassword` producer properties. They
do not enter the `maven-central` environment or receive Central credentials,
Portal credentials, or release signing keys. Publication verification still
includes the existing isolated test with a disposable signing key.

## Create a normal release

Tag a commit already on `main` using `MAJOR.MINOR.PATCH`, optionally followed by
a lowercase alphanumeric qualifier such as `-alpha01`, `-beta01`, or `-rc01`:

```shell
git tag -a 2.1.0 -m "Symbols 2.1.0"
git push origin 2.1.0
gh release create 2.1.0 --verify-tag --generate-notes --title 2.1.0
```

Alternatively, create the tag and publish its GitHub Release in the GitHub UI.
For manually created releases, the release `published` event starts package
publication; pushing a tag alone does not start a second run. This includes
prereleases (use `gh release create ... --prerelease --latest=false` for a qualifier).
Tags have no `v` prefix, and the three numeric components cannot have leading
zeros. Qualifiers start with a lowercase letter, except for the exact generated
`-SNAPSHOT-<8 lowercase hex digits>` format described below. A trailing
`-SNAPSHOT` (in any case) and build metadata (`+...`) are rejected. For example,
`1.0.0-alpha01` publishes version `1.0.0-alpha01` to both repositories. The complete
tag supplies `VERSION_NAME` for publication, so no separate edit to
`gradle.properties` is necessary. Its snapshot version remains the default for
local development and `publishToMavenLocal`.

For normal releases, the **Publish packages** workflow:

1. Resolves the tag to an immutable commit and checks its ancestry on `main`.
2. Checks existing successful publication jobs and public registry coordinates,
   and validates the release credentials before any upload.
3. Verifies generators, build tooling, library JVM tests/Android lint, and every
   platform's publication archives against that exact commit. Sample applications,
   production sample bundles/frameworks, and benchmark APKs stay in PR/main CI;
   they are not compiled as part of publication verification.
4. Uploads both Central aggregations with NMCP `AUTOMATIC` publishing and waits
   up to 30 minutes for validation. After validation, Gradle exits and a separate
   read-only step waits up to two hours for public Maven coordinates. Central can
   remain in `PUBLISHING` while its asynchronous publication completes.
5. Submits `io.github.hlcaptain.symbol-fonts` to the Gradle Plugin Portal after its
   generator dependency is available on Central.
6. Creates a GitHub Release with generated notes. Existing public release notes
   are preserved; an existing draft is published. The tag determines the release
   type: tags with a qualifier become prereleases and are not marked latest.
   If creating a prerelease in the UI, select **Set as a pre-release** too; it
   already exists while the packages are being published.

No tags are moved. PRs and pushes to branches other than `main` do not publish
packages or create releases. Use a new version for subsequent releases; registry
versions are immutable. Update `CHANGELOG.md` before tagging; generated GitHub
notes summarize merged PRs.

## Main commit snapshots

`snapshots.yml` starts one testing-snapshot run for each push's head commit on
`main`. It does not backfill historical commits or enumerate intermediate commits
within a push. Runs are keyed by commit SHA and are not
cancelled by newer pushes. They start directly from the push event, independently
of normal CI's cancellation behavior.

The version is `<current-release>-SNAPSHOT-<8sha>`, for example
`2.0.0-SNAPSHOT-b3ad4223`. The release prefix is the highest numeric stable
`MAJOR.MINOR.PATCH` tag that is an ancestor of the pushed commit.
The suffix is the first eight lowercase hexadecimal characters of the
full commit SHA. Each testing snapshot is pinned to that commit; consumers select
the complete version explicitly.

The workflow creates that tag at the exact pushed commit, then calls
`publish.yml` with the tag. Preflight validates the full source SHA, its ancestry
on `main`, and the eight-character suffix. The same publication-only verification
runs against that source. A publisher-owned Gradle init script adds the GitHub
repository to the source build's existing Maven publications; both the library
and tooling builds use native
`publishAllPublicationsToGitHubPackagesRepository` tasks. The tooling publication
includes its canonical plugin marker. No custom uploader is used.

These versions are sent only to GitHub Packages. They are not uploaded to Maven
Central or submitted to the Plugin Portal, and their spelling is not rewritten
to get around either registry's policy. The earlier
[publication attempt for `2.0.0-SNAPSHOT-b3ad4223`](https://github.com/HLCaptain/symbols/actions/runs/37058789197)
was rejected by Central with **"The version cannot be a SNAPSHOT"** for all 122
library and three tooling coordinates; no Portal submission followed. Testing
snapshots therefore have a separate registry channel with their requested names
preserved.

After successful GitHub Packages publication and authenticated availability
checks, the workflow creates a GitHub prerelease with `latest=false`. The failed
Central attempt and the existing tag do not prove this testing version is
available from GitHub Packages.

A retry reuses the existing snapshot tag only when it resolves to the same
commit; it never moves a tag. Tag creation precedes package verification, so a
failed run can leave its tag without packages or a GitHub Release. Follow the
recovery procedure below for that exact tag. Changing the publisher or these
instructions does not move the source tag or publish packages by itself.

### Consuming testing snapshots

GitHub Packages requires authentication even for public Maven packages. Give an
authorized account a personal access token (classic) with `read:packages`, and
keep these consumer properties in your private `~/.gradle/gradle.properties`:

```properties
symbolsSnapshotsUsername=YOUR_GITHUB_USERNAME
symbolsSnapshotsPassword=YOUR_CLASSIC_PAT_WITH_READ_PACKAGES
```

In consumer CI, supply the same values through
`ORG_GRADLE_PROJECT_symbolsSnapshotsUsername` and
`ORG_GRADLE_PROJECT_symbolsSnapshotsPassword` from protected secrets. Do not
commit credentials. A consumer workflow can use `GITHUB_TOKEN` only when GitHub
grants it read access to the package; the producer's token is not a credential
to copy into other projects. See [GitHub's Gradle authentication guidance](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry#authenticating-to-github-packages).

Configure library resolution and, if using the generator plugin, plugin
resolution in `settings.gradle.kts`. The version filters keep normal releases
on their existing repositories and include the plugin marker's separate group:

```kotlin
import org.gradle.api.credentials.PasswordCredentials

pluginManagement {
    repositories {
        maven {
            name = "symbolsSnapshots"
            url = uri("https://maven.pkg.github.com/hlcaptain/symbols")
            credentials(PasswordCredentials::class)
            content {
                includeVersionByRegex("io\\.github\\.hlcaptain", ".*", ".*-SNAPSHOT-[0-9a-f]{8}")
                includeVersionByRegex("io\\.github\\.hlcaptain\\.symbol-fonts", ".*", ".*-SNAPSHOT-[0-9a-f]{8}")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "symbolsSnapshots"
            url = uri("https://maven.pkg.github.com/hlcaptain/symbols")
            credentials(PasswordCredentials::class)
            content {
                includeVersionByRegex("io\\.github\\.hlcaptain", ".*", ".*-SNAPSHOT-[0-9a-f]{8}")
            }
        }
        google()
        mavenCentral()
    }
}
```

After confirming the requested version is available, select it explicitly:

```kotlin
dependencies {
    implementation("io.github.hlcaptain:symbols-material-vectors-rounded:2.0.0-SNAPSHOT-b3ad4223")
}
```

The generator plugin, when needed, uses the same complete version in
`plugins { id("io.github.hlcaptain.symbol-fonts") version "2.0.0-SNAPSHOT-b3ad4223" }`.
Built-in vector consumers do not need that plugin. Do not use `snapshotsOnly()`
for this repository: `-SNAPSHOT-<hash>` does not end with Maven's special
`-SNAPSHOT` suffix. Select the full testing version to keep the consumer pinned
to the intended commit.

## Retries and partial failures

Always start a **fresh workflow run** after a publication failure so preflight
rechecks the destination. **Re-run failed jobs** can reuse stale outputs that
still say a now-existing publication is absent. Versions are serialized with
`cancel-in-progress: false`; no retry moves a tag, deletes packages, or overwrites
an existing testing-snapshot file.

### Testing snapshots

Snapshot recovery may use reviewed newer publisher code while the existing tag
continues to identify the exact artifact sources. Replace
`REVIEWED_PUBLISHER_REF` with a reviewed branch or tag containing the GitHub
Packages publisher:

```shell
gh workflow run publish.yml --ref REVIEWED_PUBLISHER_REF \
  -f tag=2.0.0-SNAPSHOT-b3ad4223
```

The workflow checks out artifact sources from the immutable tag and publisher
tools from its own `github.workflow_sha` in a separate directory. Verification
and both builds still use the tag's full source SHA. This permits recovery of
the old `b3ad422306ef82df13750a8bfc9ca6a73ce3a4ce` sources without moving their
tag or using newer library code under the old version.

Only a successful GitHub publication job matching the backend, component,
complete version, and full source SHA permits skipping a completed component.
Receipt lookup also covers successful manual recovery jobs whose workflow SHA
differs from the artifact SHA. A prior Central job is not a GitHub receipt.

By default, without that receipt, any existing expected POM aborts preflight, even if every
POM is visible. Before each remaining component uploads, Gradle generates a
manifest of its declared artifact, POM, and module-metadata paths. Any existing
file in that manifest aborts publication as an unconfirmed partial upload.
Authentication failures, rate limits, and registry errors are not interpreted
as missing files. Inspect an uncertain publication before taking further action;
there is no automatic overwrite/delete or skip-by-existence recovery path.

#### Recover entirely absent library publications

After inspecting a partial library upload, an operator can explicitly select
only publications whose versioned files are entirely absent. The optional
`workflow_dispatch` input `library-publications` is a newline-separated list of
exact native `PublishToMavenRepository` task paths. This mode is allowed only for
manual testing-snapshot recovery; normal releases and automatic snapshot runs
retain the default behavior above.

Create a reviewed task-list file from the configured publication inventory and
a fresh authenticated absence check. Use full paths, such as
`:modules:symbols-core:publishAndroidPublicationToGitHubPackagesRepository`,
not aggregate tasks, artifact names, wildcards, or Gradle options. Then pass the
file as a workflow input:

```shell
gh workflow run publish.yml --ref REVIEWED_PUBLISHER_REF \
  -f tag=2.0.0-SNAPSHOT-b3ad4223 \
  -F library-publications=@absent-library-publications.txt
```

Preflight still validates the immutable tag, full source SHA, `main` ancestry,
and hash suffix. The explicit selection permits existing library publications
to remain in place; it does not mark the library component complete or skip full
source verification. The init script validates each selected task against the
configured GitHub Maven publication tasks and includes only those publications'
files in the manifest. The existing absence guard rejects the upload if any
selected file already exists. The workflow executes only the validated task
paths read back from that manifest, using native Gradle publication.

Completed or partially present publications must not be selected. Existing
versioned publication files and the source tag are preserved; native Maven
publishing maintains its shared repository metadata when adding new publications.
After the selected tasks finish, the workflow still checks **all 122 library
POMs** before recording a successful whole-library job and allowing the GitHub
Release. Publishing a smaller subset does not complete the snapshot release.
If recovery stops partway, refresh the inventory and task list
before another run.

The [GitHub Packages attempt for the same testing version](https://github.com/HLCaptain/symbols/actions/runs/37075726956)
passed source verification and published the tooling component, but its library
publication step timed out after 90 minutes. The subsequent inventory found
105 of 122 library POMs present and these 17 missing publications:

| Module | Missing publications |
| --- | --- |
| `material-vectors-themed` | Common/root (`kotlinMultiplatform`), JVM, Wasm JS |
| `symbols-core` | All seven: common/root, Android, iOS device, iOS simulator, JS, JVM, Wasm JS |
| `variant-font-core` | All seven: common/root, Android, iOS device, iOS simulator, JS, JVM, Wasm JS |

At **2026-10-03 01:28:46 UTC**, all 97 declared manifest paths for those 17
publications returned authenticated 404 responses, with no authentication or
network errors. This records the inspected partial state, not a completed
publication or permission to reuse that absence result indefinitely. The 105
existing POMs were not a new verification of every existing binary. Every
recovery run checks the selected files again immediately before upload.

### Normal releases

After an upload or publication timeout, inspect the existing deployment in Central
Portal and wait until it is published. Then start a **fresh workflow run** for the
same tag so preflight rechecks the registries and skips completed uploads.

Run manual recovery from the same tag ref so GitHub's publication checks remain
attached to the released commit:

```shell
gh workflow run publish.yml --ref 0.1.0 -f tag=0.1.0
```

A normal-release manual run from a different commit is rejected.
Successful publication jobs on the same commit and tag prevent duplicate uploads,
including a Portal submission still awaiting its first review. Public coordinates
provide a fallback if old workflow history is gone. A partly visible Central bundle
fails preflight rather than attempting to overwrite existing versions.

Re-running an old normal release uses the workflow from its original tag,
including its timeouts. Workflow fixes merged later into `main` apply to new tags. Do not move an
existing release tag to pick up a fix; publish a new version from the fixed commit.

If Central accepted an upload but a publishing/visibility timeout occurred, inspect
the deployment in Central Portal and wait for it to finish before rerunning. The two
registries and two Central aggregations cannot form one atomic transaction: already
published components remain published when a later component fails. Never retag a
failure to another commit. A successful Portal submission may still need approval;
retain its successful workflow run until the Portal version is publicly visible.

## Storage and validation

Normal packages go directly to Maven Central and the Plugin Portal; testing
snapshots go directly to GitHub Packages. GitHub Release notes are text only.
There are no uploaded Actions artifacts, intermediary bundle
transfers, or GitHub Actions dependency caches. Build outputs stay on the runner;
CI jobs rebuild from the same commit rather than sharing artifact storage.

For normal releases, the standard `ubuntu-24.04` GitHub runner publishes the
complete library aggregation, including Apple KLIB artifacts, in one job. The library upload/build
step has a 90-minute limit; the separate
public-coordinate wait has its own two-hour budget. Overall library/tooling jobs
allow 240/210 minutes to accommodate Central's remote queue, without changing the
90-minute CI build limits.

GitHub Packages uses separate Ubuntu jobs for libraries and tooling. Libraries
have a 180-minute job limit and a 150-minute native publication step; tooling
retains its 120-minute job limit and 90-minute publication step. Both have a
15-minute authenticated availability-check step. The longer library budget
addresses the observed 90-minute partial upload without relaxing verification
or existing-file checks.

Publication verification and tooling publication also use Ubuntu; the macOS sample
framework is checked by normal PR/main CI. No self-hosted runner is needed. Hosted provisioning
and the public-repository handoff are documented in [CI](docs/CI.md).

Local checks that do not publish:

```shell
python3 -m unittest discover -s tools/tests -p test_release.py
python3 tools/check_signed_publications.py
./tooling/gradlew -p tooling -PVERSION_NAME=0.1.0 \
  :symbol-gradle-plugin:validatePlugins \
  :symbol-gradle-plugin:generatePomFileForPluginMavenPublication \
  :symbol-gradle-plugin:generatePomFileForSymbolFontsPluginMarkerMavenPublication \
  :symbol-generator-core:generatePomFileForMavenPublication
```

The signed-publication check requires JDK 21, the Android SDK, Git, and GnuPG.
It copies tracked working-tree sources into a temporary directory, overrides signing
with a disposable key, and checks that signing tasks have distinct output files.
It then publishes all seven `material-compose` variants to an isolated Maven local
repository and verifies their signatures. Temporary sources, artifacts, and keys
are removed afterwards. CI runs this check before publication verification, without
release secrets or uploads. Each publication has its own local Javadoc JAR so its
signature cannot collide with another platform's signature; Maven coordinates and
classifiers are unchanged.

`publishPlugins --validate-only` additionally checks Portal publishing configuration
and requires Portal credentials, even though it does not upload. The
[NMCP configuration](https://gradleup.com/nmcp/) now uses automatic release with
a 30-minute validation timeout and `publishingTimeout = Duration.ZERO`. Zero skips
NMCP's `PUBLISHED`-status polling, not validation or automatic publication. The
workflow still requires publicly visible coordinates before proceeding. Running
`publishAggregationToCentralPortal` with real credentials therefore starts
automatic publication; use `publishToMavenLocal` for offline packaging checks.
