# Source-generation and PR LOC audit — 2026-09-14

Implementation update: the four vector modules now use build-time generation.
Their checked-in snapshots have been removed; see [source-build setup](../CONTRIBUTING.md#development-environment).
The measurements and audit below describe the pre-migration baseline.

PR #6: `agent/static-font-generation`, HEAD `02ff132ae044343352318306448edc5c17b6792d`, compared with merge base `f3a333d8fc5370d9336f1b105d511128babe4b3d` of refreshed `origin/main`. The GitHub PR API and local diff agree.

**Current diff: +1,153,537 / −18,638 lines; net +1,134,899; 581 changed files, including 13 binary changes.** Counts are Git text-line additions/deletions, including comments, documentation and generated code—not executable-only SLOC. Binaries do not contribute LOC.

**252 tracked generated Kotlin files contain 1,133,299 current lines (44,422,098 uncompressed bytes) and account for 1,119,156 PR additions: 97.02% of the total.** Their current generator outputs were verified byte-for-byte.

The full per-module table is below. The follow-up [measured vector-generation study](VECTOR_GENERATION_TRADEOFFS.md) compares source downloads, compilation, and an API-preserving middle ground. Counts in this audit describe the baseline commit, before adding these study documents and scripts.

## Highest-value removal candidates

Only remove these source snapshots after introducing build-task dependencies; today normal builds still read the tracked files. Preserve the generated APIs and compiled contents.

| Module | Generated files | Current generated lines | PR additions removed | Generator |
| --- | ---: | ---: | ---: | --- |
| `symbols/material-vectors-rounded` | 62 | 413,160 | 412,787 | `tools/generate_material_vectors.py` |
| `symbols/material-vectors-outlined` | 62 | 345,509 | 345,136 | `tools/generate_material_vectors.py` |
| `symbols/material-vectors-sharp` | 62 | 307,500 | 307,127 | `tools/generate_material_vectors.py` |
| `symbols/material-vectors-themed` | 65 | 50,004 | 50,004 | `tools/generate_material_vectors.py` |
| `symbols/material-core` | 1 | 17,126 | 4,102 | `tools/generate_material_symbols.py` |

| Cleanup stage | Projected additions | Projected deletions | Projected net |
| --- | ---: | ---: | ---: |
| Current PR | 1,153,537 | 18,638 | +1,134,899 |
| Generate the four vector modules during build | 38,483 | 19,757 | +18,726 |
| Also generate the Material catalog during build | 34,381 | 32,781 | +1,600 |

These are mechanical projections **before new build-wiring code**. Deletions grow because some generated files already exist on main. Current-file lines and PR-added lines are different quantities. This reduces Git review/source-checkout size; it does not inherently reduce compiler work, published binary size, or source-jar contents.

## Full per-module change summary

`:modules:*` maps to `symbols/*`. Included builds are labelled `tooling/` or `build-logic/`. Support directories are included as separate rows so the totals reconcile. Unchanged modules, including the iOS host app, are omitted.

| Module / area | Added | Deleted | Net | Generated additions removable | Brief change |
| --- | ---: | ---: | ---: | ---: | --- |
| `:modules:material-vectors-rounded` | 412,812 | 4,281 | +408,531 | 412,787 | Generated Rounded ImageVector paths, named accessors and dynamic index; dominant removable source snapshot. |
| `:modules:material-vectors-outlined` | 345,161 | 4,281 | +340,880 | 345,136 | Generated Outlined ImageVector paths, named accessors and dynamic index; dominant removable source snapshot. |
| `:modules:material-vectors-sharp` | 307,152 | 4,281 | +302,871 | 307,127 | Generated Sharp ImageVector paths, named accessors and dynamic index; dominant removable source snapshot. |
| `:modules:material-vectors-themed` | 50,154 | 0 | +50,154 | 50,004 | Generated theme-selected getters delegating to the three style packs; small handwritten adapter/tests. |
| `fonts (inputs and assets)` | 8,714 | 10 | +8,704 | 0 | Pinned manifests/licenses, derived regular fonts and external provider fixtures; binary size is separate from LOC. |
| `tooling/:symbol-generator-core` | 5,412 | 0 | +5,412 | 0 | Font/SVG extraction, Kotlin/XML rendering, descriptors/catalogs and tests. |
| `:modules:material-core` | 4,201 | 4,171 | +30 | 4,102 | Material namespace, indexed catalog/lookup and generated named accessors. |
| `tooling/:symbol-gradle-plugin` | 4,165 | 0 | +4,165 | 0 | Cacheable generation tasks, DSL, variant/resource/source wiring and functional tests. |
| `:modules:variant-font-core` | 2,547 | 0 | +2,547 | 0 | Generic font contracts/settings/theme, native rendering, component-owned effects and regression tests. |
| `tools (Python generators/tests)` | 1,885 | 152 | +1,733 | 0 | Maintainer catalog/vector/static-font/SVG generators and conformance tests. |
| `:benchmarks:animated-font` | 1,839 | 0 | +1,839 | 0 | Macrobenchmarks, geometry checks, analyzer and curated result/reproduction docs. |
| `docs` | 1,741 | 127 | +1,614 | 0 | Performance, size, screenshot and architecture/developer documentation. |
| `repository root / other` | 1,529 | 303 | +1,226 | 0 | Root docs, changelog/notices, Gradle configuration, sample README and Yarn lockfile changes. |
| `benchmark scripts/docs` | 758 | 0 | +758 | 0 | Non-Gradle sample-app build/size/memory measurement scripts and docs. |
| `:benchmarks:shrinkable-vectors` | 749 | 0 | +749 | 0 | R8/resource shrinking fixture and verification/measurement scripts. |
| `:samples:ui:components` | 570 | 0 | +570 | 0 | Reusable sample pages/cards and axis controls. |
| `:samples:image-vector-migration` | 567 | 0 | +567 | 0 | ImageVector/drawable migration examples and SVG inputs. |
| `:benchmarks:animated-font-app` | 531 | 0 | +531 | 0 | Profileable Android target and controlled animated icon workloads. |
| `build-logic/:convention` | 390 | 0 | +390 | 0 | Shared Kotlin/Compose/Android/publication and font/sample conventions. |
| `:samples:android-views` | 365 | 0 | +365 | 0 | Android XML/View Binding/Data Binding/custom-view font and drawable integrations. |
| `:samples:custom-variable` | 345 | 0 | +345 | 0 | Academmunicons runtime/generated comparison with its variable-font input. |
| `CI/repository templates` | 284 | 46 | +238 | 0 | Self-hosted CI/release and repository workflow configuration. |
| `:samples:runtime-axes` | 262 | 0 | +262 | 0 | Interactive font-axis controls and combined animated effects. |
| `:composeApp` | 223 | 288 | -65 | 0 | Modular sample launcher/navigation replacing earlier inline sample UI. |
| `:modules:material-compose` | 167 | 381 | -214 | 0 | Material theme/style adapters; generic font functionality extracted to variant-font-core. |
| `tooling root` | 158 | 0 | +158 | 0 | Included tooling build configuration. |
| `:samples:custom-static` | 126 | 0 | +126 | 0 | Powerline font/conversion example, manifest and font fixture. |
| `:samples:material-static` | 126 | 0 | +126 | 0 | Regular-font and generated resource/vector examples. |
| `:samples:theming` | 101 | 0 | +101 | 0 | Theme-selected icon styles and generic font settings example. |
| `:samples:material-variable` | 90 | 0 | +90 | 0 | Runtime Material variable-font example. |
| `Gradle wrapper/catalog` | 78 | 23 | +55 | 0 | Version catalog and wrapper configuration changes. |
| `:samples:api` | 69 | 0 | +69 | 0 | Sample metadata, availability and navigation contracts. |
| `:modules:material-compose-drawables-outlined` | 43 | 0 | +43 | 0 | Compose drawable-resource artifact; XML/accessors already generated under build/. |
| `:modules:material-compose-drawables-rounded` | 43 | 0 | +43 | 0 | Compose drawable-resource artifact; XML/accessors already generated under build/. |
| `:modules:material-compose-drawables-sharp` | 43 | 0 | +43 | 0 | Compose drawable-resource artifact; XML/accessors already generated under build/. |
| `build-logic root` | 29 | 0 | +29 | 0 | Included-build settings and dependency/plugin setup. |
| `:modules:material-drawables-outlined` | 28 | 0 | +28 | 0 | Android drawable AAR; XML already generated under build/. |
| `:modules:material-drawables-rounded` | 28 | 0 | +28 | 0 | Android drawable AAR; XML already generated under build/. |
| `:modules:material-drawables-sharp` | 28 | 0 | +28 | 0 | Android drawable AAR; XML already generated under build/. |
| `:modules:symbols-core` | 12 | 0 | +12 | 0 | Shared zero-dependency Symbols namespace. |
| `:modules:material-outlined-static` | 3 | 0 | +3 | 0 | Regular-font artifact module using the shared convention. |
| `:modules:material-rounded-static` | 3 | 0 | +3 | 0 | Regular-font artifact module using the shared convention. |
| `:modules:material-sharp-static` | 3 | 0 | +3 | 0 | Regular-font artifact module using the shared convention. |
| `:modules:material-outlined` | 1 | 98 | -97 | 0 | Generated font-accessor convention replaces handwritten font-wrapper source. |
| `:modules:material-rounded` | 1 | 98 | -97 | 0 | Generated font-accessor convention replaces handwritten font-wrapper source. |
| `:modules:material-sharp` | 1 | 98 | -97 | 0 | Generated font-accessor convention replaces handwritten font-wrapper source. |

## Build wiring required before deleting snapshots

1. Keep the existing Python emitters initially. Add an output-root option to the vector generator: it currently hardcodes `src/commonMain/kotlin` and only accepts `--style`/`--check`. The catalog generator already accepts `--output`. Generate into each module’s `build/generated/` tree.
2. Add cacheable Gradle generation tasks with declared manifest/font/script/version inputs and output directories. The existing plugin’s `wireGeneratedKotlin` task-provider wiring is a usable pattern; the Material vector convention and `material-core` do not currently register their snapshot generators.
3. Wire common compilation, metadata, source archives and publication to those outputs. Every checkout-based CI job that compiles these modules must generate them or provision the pinned Python/FontTools environment; the current generator CI job only checks committed snapshots. Do not rely on a separate job mutating a checkout that other jobs cannot see.
4. Keep current independent per-codepoint vector owners, aliases, mirroring, themed getters, compatibility lookup and indexed Material catalog representation. The generic Kotlin catalog emitter produces lists of entry objects, and the generic vector emitter has a different API layout; neither is a drop-in replacement for these snapshots.
5. Change CI verification to validate deterministic generated outputs, then run catalog/vector contracts, clean multiplatform builds, the R8 benchmark and publication archive checks. Only then untrack the old source snapshots.

Maven consumers should continue receiving precompiled APIs/resources without running maintainer generators. Generated source may still belong in published source JARs for IDE navigation. Do not replace this work with one giant runtime registry merely to reduce source lines: that can undo shrinkability and allocation improvements.

## Additional byte/file reductions

| Candidate | Potential saving | Assessment |
| --- | ---: | --- |
| Three Material `*-static` TTFs | 4,153,608 bytes; 0 LOC | Reproducible with `generate_material_static_fonts.py`; used by static-font and drawable modules. Generate and wire resource providers first; keep legal provenance and expected hashes. |
| Academmunicons regular derivative | 94,048 bytes; 0 LOC | Reproducible from the checked-in variable TTF with `ital=0,wght=400` and renamed family `Symbols Academic Icons`; generator/descriptor tests currently consume it. Generate it before those tests. |
| Duplicate Powerline font copies | 4,528 redundant bytes | Three identical copies. One canonical input plus build-time resource copies could remove two; keep discovery/Android sample behavior and verifier paths working. |
| Duplicate manifests | 74 redundant lines | Powerline: four identical eight-line copies; Academmunicons: two identical 50-line copies. Reference canonical inputs rather than reconstructing semantic names from fonts. |
| Full Font Awesome / Tabler font reference fixtures | 9,231,568 binary bytes; optionally 8,216 manifest lines | No build/test consumer of the complete provider-font files/manifests was found; they are documented reference fixtures. Pruning them is a separate scope decision, not automatic regeneration from local inputs. Keep the actually used small Tabler SVGs and their license. |

## Keep as source inputs / already handled

- Keep the three pinned upstream Material variable TTFs and canonical Material codepoint manifest. These are generation inputs, not generated noise; aliases and public names cannot safely be reconstructed from font cmap data alone.
- Keep original custom font/SVG inputs, licenses, THIRD_PARTY_NOTICES and dependency lockfiles. Auto-written lockfiles serve reproducibility and should not be removed just because a tool creates them.
- The six Android/Compose drawable-pack modules already generate XML/resources during the build: their changes are only +28 or +43 source lines per module. No tracked generated drawable XML backlog was found.
- Res accessors, typed font descriptors, sample vectors and normal build outputs already live under ignored build directories. No tracked raw benchmark traces, measurement folders, APKs or node_modules were found. Existing launcher PNGs are unchanged in this PR and do not explain its growth.

## Merge/history and publication implications

Untracking newly added generated files reduces the current PR diff, but earlier branch commits retain their blobs. A squash merge of the cleaned final tree avoids bringing those intermediate generated blobs into main’s new commit history. Rewriting or force-pushing the existing branch history is a separate action, not necessary for this report.

Published AAR/JAR/KLIB resources and public APIs must remain intact. The goal is to generate required content before publishing, not publish incomplete libraries. Moving source files under build/ alone does not shrink compiled artifacts or eliminate the million-line compilation workload.

## Verification performed

- Refreshed `origin/main`; compared `git diff --numstat -z $(git merge-base origin/main HEAD) HEAD` with GitHub PR additions/deletions. All module rows sum to the same totals.
- Scanned all tracked paths for generated-source headers, binaries, duplicated font/manifest hashes and generated-output directories.
- `python3 tools/generate_material_symbols.py --check` passed.
- With FontTools 4.60.2 in a temporary environment, `generate_material_vectors.py --check` passed for all three styles and themed output: 43,966,669 source bytes.
- `generate_material_static_fonts.py --check` passed for all three Material derivatives.
- The documented Academmunicons static-generation command passed with `--check`, including the renamed family and expected SHA-256.

This audit did not remove production files or change build wiring. Detailed local inventories remain under ignored `build/reports/source-footprint-audit/`; the report preserves the curated results without committing raw measurement data.
