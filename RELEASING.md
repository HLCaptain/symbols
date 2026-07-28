# Releasing Symbols

Symbols publishes its Kotlin Multiplatform artifacts to the repository's
GitHub Packages Maven registry. The workflow uses the repository-scoped
`GITHUB_TOKEN`; maintainers do not need to create a publication secret.

## Published artifacts

Every library has an umbrella multiplatform coordinate under
`io.github.hlcaptain`:

- `symbols-material-core`
- `symbols-material-compose`
- `symbols-material-outlined`
- `symbols-material-rounded`
- `symbols-material-sharp`
- `symbols-material-vectors-outlined`
- `symbols-material-vectors-rounded`
- `symbols-material-vectors-sharp`

Gradle module metadata selects the target-specific Android, JVM, JS, Wasm, or
Apple artifact for consumers.

GitHub Packages requires authentication when resolving Maven packages,
including public packages. Consumers need a personal access token (classic)
with `read:packages`, while workflows in authorized repositories can use a
`GITHUB_TOKEN`.

## Snapshot publication

`gradle.properties` holds the next development version:

```properties
VERSION_NAME=0.1.0-SNAPSHOT
```

Every push to `main` must pass the complete reusable CI workflow before that
Maven snapshot is published. The same CI workflow also runs directly for pull
requests and `main`, keeping the Android, JVM, JS, Wasm, Apple, generator, and
font checks visible as ordinary repository checks. GitHub Packages supports
Maven `-SNAPSHOT` versions.

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
   publishing the exact non-snapshot version.
6. Create the GitHub Release for the tag and copy the relevant changelog
   section into its notes.
7. On `main`, advance `VERSION_NAME` to the next development snapshot.

Do not move or reuse a release tag. Published stable Maven versions are
immutable release coordinates.

## Why publication runs on one host

Kotlin 2.2.20 can cross-compile the Apple `.klib` publications on Linux. This
project has no CocoaPods or cinterop dependencies, so the workflow can publish
all target and umbrella publications from one Ubuntu job. Publishing from one
host also prevents two jobs from attempting to upload the same multiplatform
metadata. Final Apple application binaries remain validated separately on a
macOS runner.

For a local, non-network publication check:

```shell
./gradlew publishToMavenLocal
```

For an authenticated manual GitHub Packages publication, set
`GITHUB_ACTOR` and `GITHUB_TOKEN`, then run:

```shell
./gradlew publishAllPublicationsToGitHubPackagesRepository
```
