# Releasing Symbols

Symbols publishes stable Kotlin Multiplatform libraries, Android drawable AARs,
and JVM build tooling to Central Portal from release tags. They become available
from Maven Central after a maintainer releases both staged deployments.

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

## Central Portal setup

Before the first stable release:

1. Create a publisher account at the Central Portal and verify the
   `io.github.hlcaptain` namespace.
2. Create a Central Portal user token.
3. Create a password-protected PGP signing key and publish its public key.
4. Create a protected GitHub environment named `maven-central` with these
   secrets:

   - `MAVEN_CENTRAL_USERNAME`: Central Portal token username
   - `MAVEN_CENTRAL_PASSWORD`: Central Portal token password
   - `SIGNING_KEY`: ASCII-armored private PGP key
   - `SIGNING_PASSWORD`: private-key password

The NMCP settings plugin reuses the project's existing Maven publications,
adds checksums, and uploads through the Central Portal API. Runtime and tooling
are separate Gradle builds, so a release creates two user-managed deployments.

## Development versions

`gradle.properties` holds the next development version:

```properties
VERSION_NAME=0.1.0-SNAPSHOT
```

Development snapshots are not uploaded to a remote package registry. Pull
requests and `main` still run the complete CI workflow, keeping the Android,
JVM, JS, Wasm, Apple, generator, and font checks visible as ordinary repository
checks. Use `publishToMavenLocal` for local cross-checkout testing.

## Stable release

1. Confirm the `main` branch is clean and all required CI jobs pass.
2. Confirm `CHANGELOG.md`, documentation, licenses, and third-party notices
   describe the release.
3. Confirm `VERSION_NAME` is the intended release followed by `-SNAPSHOT`.
   For example, release `0.1.0` from `0.1.0-SNAPSHOT`.
4. Create and push an annotated semantic-version tag:

   ```shell
   git tag -a v0.1.0 -m "Symbols 0.1.0"
   git push origin v0.1.0
   ```

5. Watch the **Publish packages** workflow. It accepts only
   `vMAJOR.MINOR.PATCH`, requires the tag to point to a commit on `origin/main`,
   and requires the tag version to match `VERSION_NAME`. It then reuses the
   complete CI workflow—generated inputs, JVM tests, Android lint/release
   assembly, production JS/Wasm bundles, and Apple compilation/linking—before
   staging the exact non-snapshot version in Central Portal.
6. In Central Portal, inspect and manually release both the `symbols` and
   `symbols-tooling` deployments. User-managed staging prevents a partial build
   from becoming public automatically.
7. Create the GitHub Release for the tag and copy the relevant changelog
   section into its notes.
8. On `main`, advance `VERSION_NAME` to the next development snapshot.

Do not move or reuse a release tag. Published stable Maven versions are
immutable release coordinates.

## Why publication runs on one host

The configured Kotlin version can publish every target from the self-hosted
macOS runner. One host prevents multiple jobs from uploading the same
multiplatform metadata and also covers the Apple publications natively.

For a local, non-network publication check:

```shell
./gradlew publishToMavenLocal
./gradlew -p tooling publishToMavenLocal
```

For a manual Central staging upload, set these environment-backed Gradle
properties:

- `ORG_GRADLE_PROJECT_mavenCentralUsername`
- `ORG_GRADLE_PROJECT_mavenCentralPassword`
- `ORG_GRADLE_PROJECT_signingInMemoryKey`
- `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`

Then run:

```shell
./gradlew -PVERSION_NAME=0.1.0 publishAggregationToCentralPortal
./gradlew -p tooling -PVERSION_NAME=0.1.0 publishAggregationToCentralPortal
```
