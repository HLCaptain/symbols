# Contributing

Thank you for helping improve Symbols. The project is still under active
development and has not published its first stable release, so APIs and module
boundaries may change while the initial design is completed.

By participating, you agree to follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
Security vulnerabilities must be reported through the private process in
[SECURITY.md](SECURITY.md), not through a public issue.

## Before starting

For bugs, search the issue tracker before opening a report. For substantial API
changes, new modules, dependency changes, generated-code changes, font updates,
or custom glyphs, open a proposal first. Agreeing on the behavior and the
performance trade-offs before implementation avoids wasted work.

Small corrections and focused tests can be submitted directly.

## Development environment

The build uses the checked-in Gradle wrapper. A compatible JDK and the Android
SDK required by the current version catalog are needed for the full build.
Apple-target builds additionally require macOS and Xcode.

Source builds of the four Material vector modules also need Python and the pinned
FontTools version. Set up the environment once (JDK 21 is recommended):

```shell
python3 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements-font-verification.txt
export SYMBOLS_PYTHON="$PWD/.venv/bin/python"
```

On Windows use `.venv/Scripts/python.exe`. For IDE builds, set
`symbolsPython=/absolute/path/to/.venv/bin/python` in your user
`~/.gradle/gradle.properties` (or pass `-PsymbolsPython=...`). The Gradle property
takes precedence over `SYMBOLS_PYTHON`; otherwise the build uses `python3` from PATH.

Each vector module's cacheable `generateMaterialVectors` task writes only to
`build/generated/materialVectors/commonMain/kotlin`. Compilation, source archives,
and IDE import depend on these sources automatically. To prepare APIs without
compilation, run `./gradlew generateMaterialVectors`; IDE import also invokes it
through `prepareKotlinIdeaImport`. Do not commit the resulting Kotlin files.
Published dependency consumers need no Python and keep using the same icon APIs.

Start by checking that the project configures successfully:

```shell
./gradlew tasks
```

Run the relevant checks while developing and the complete verification before
submitting:

```shell
./gradlew check
```

Visible sample changes also use generated Roborazzi baselines. The focused
record, verify, compare, output locations, and CI review flow are documented in
[docs/SCREENSHOT_TESTING.md](docs/SCREENSHOT_TESTING.md).

Use `gradlew.bat` instead on Windows. If a platform toolchain is unavailable,
state exactly which tasks you ran and which tasks remain unverified in the pull
request.

## Change guidelines

- Follow the repository's [coding conventions](CONVENTIONS.md).
- Keep exported APIs small, stable, and documented.
- Preserve Kotlin Multiplatform behavior; do not add platform-specific behavior
  to common APIs without tests and a documented reason.
- Add tests for observable behavior, regressions, boundary values, aliases, and
  malformed input where applicable.
- Keep generated output deterministic. Generated source changes must include the
  generator or input change that produced them.
- Avoid adding work to published-dependency consumers. Built-in vectors are
  generated during the library source build; consumers receive compiled artifacts.
- Apply the narrowest existing `build-logic` convention instead of repeating
  targets, SDK/toolchain settings, Compose setup, common test dependencies, or
  publication wiring. Keep namespaces, resource packages, generator inputs,
  and module-specific dependencies in the module script.
- Normal Gradle generation must write through the owning module's
  `layout.buildDirectory`; generated build output is disposable and must not be
  written under `src` or committed.
- Do not commit build output, local configuration, IDE state, signing material,
  credentials, or downloaded caches.
- Add an entry under `Unreleased` in [CHANGELOG.md](CHANGELOG.md) for
  user-visible changes.

## Fonts, codepoints, and generated catalogs

Bundled Google Material Symbols assets have special provenance requirements.
Do not replace a font, edit a codepoint map, add a glyph, or regenerate a
catalog without:

1. identifying and pinning the input revision;
2. recording the font version and cryptographic hashes;
3. updating [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md);
4. documenting whether each asset is an unmodified upstream file or a
   derivative;
5. verifying all catalog entries and aliases against the fonts; and
6. running the generator and font-conformance tests.

Custom glyphs must have an origin, license, stable codepoint allocation, source
artwork, and tests. Do not assume that an outline added to a variable font
supports every variation axis.

The maintainer checks for generated sources and fonts are:

```shell
python3 tools/generate_material_symbols.py --check
/tmp/symbols-fonttools/bin/python -m unittest discover -s tools/tests -p "test_*.py"

/tmp/symbols-fonttools/bin/python tools/verify_material_fonts.py
/tmp/symbols-fonttools/bin/python tools/generate_material_static_fonts.py --check
/tmp/symbols-fonttools/bin/python tools/generate_material_vectors.py
/tmp/symbols-fonttools/bin/python tools/generate_material_vectors.py --check

./gradlew -p tooling test \
  :symbol-gradle-plugin:validatePlugins \
  :symbol-generator-core:generatePomFileForMavenPublication \
  :symbol-gradle-plugin:generatePomFileForPluginMavenPublication \
  :symbol-gradle-plugin:generatePomFileForSymbolFontsPluginMarkerMavenPublication

./gradlew \
  :benchmarks:shrinkable-vectors:assembleUnshrunk \
  :benchmarks:shrinkable-vectors:assembleShrunk
python3 benchmarks/shrinkable-vectors/verify.py
```

Install the pinned FontTools environment as described in
[`tools/FONT_VERIFICATION.md`](tools/FONT_VERIFICATION.md) before running the
`/tmp/symbols-fonttools` commands. A change to a derived regular font must update
its generation method and hash as well as its upstream source provenance.

## Commits and pull requests

Keep commits focused and use an imperative summary such as `Add alias collision
test`. A pull request should:

- explain the problem and the chosen behavior;
- link related issues or proposals;
- describe API, binary-size, build-time, and compatibility impact where
  relevant;
- list the exact verification commands and results;
- include screenshots or recordings for visible UI changes; and
- call out generated files, bundled assets, and provenance changes.

Maintainers may ask for a change to be split when it mixes unrelated concerns.
Submission does not guarantee acceptance, but reviews should explain technical
or project-scope reasons for requested changes.

## Licensing

Unless explicitly stated otherwise, a contribution intentionally submitted for
inclusion in this repository is provided under the Apache License 2.0, as
described by section 5 of [LICENSE](LICENSE). Only submit work that you have the
right to contribute. Preserve third-party copyright, license, and attribution
notices.
