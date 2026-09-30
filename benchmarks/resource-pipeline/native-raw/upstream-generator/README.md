# Upstream Compose generator contribution candidate

These are source patches against the published Compose Gradle plugin, not a
production Symbols backend or an official Compose feature. The matching runtime
contribution must provide `getAndroidResourcePath(Int, String)` and advertise
`org.jetbrains.compose.components:components-resources-native-xml-v1` on its
Android leaf variants. Production adoption remains blocked until supported
upstream generator/runtime releases exist.

## Proposed producer API

```kotlin
compose.resources {
    experimentalAndroidNativeXmlResources(
        "commonMain",
        ownedXmlTask.flatMap { it.outputDirectory },
    )
}
```

This hook registers ownership; it does not add a resource directory to Compose.
The registered directory must already participate in the source set's effective
resource root. Multiple calls append task-backed providers. Symbols can call it
only when its default-false experimental option is enabled, before AGP finalizes
variants. Unregistered/default builds retain the stock resource pipeline.

Only XML files directly below the enrolled directories' `drawable` or qualified
drawable folders are eligible. Ownership requires matching prepared-resource
paths and bytes; duplicate ownership or a conflicting source-set overlay fails.
Fonts, other files, and unrelated authored drawables are not enrolled implicitly.
Each physical XML, including theme/locale/density variants, receives its own
stable private `R.raw` name. Empty registered inputs generate no native XML.

The contribution changes private `ResourceItem` construction to call per-file
platform factories. Public `Res` getters, existing facade filenames, qualifiers,
content hashes and collectors retain their original generator behavior. Enrolled
descriptors keep their lazy delegate inside a private per-resource object; the
public getter references that holder. This prevents a shared file initializer
from retaining unused lazy lambdas and their native resource references. Holder
names include source-set/chunk identity, and their delegates retain the original
`ResourceContentHash` values and annotation target. Android
factories call the runtime helper with a direct `R.raw` reference and the original
logical path. Other target factories return the existing asset path. A separate
raw-path dispatcher supports `Res.readBytes/getUri`, conservatively retaining the
owning pack when the path is not statically known; typed getters do not call it.
Factories and dispatch functions are split across source files to avoid JVM
method/class-size limits.

Android enrollment adds a normal dependency on the Android resource-runtime leaf
with the required native-XML capability. A higher runtime without that capability
must fail dependency resolution. Common and other platform dependencies remain
unchanged. No application plugin, initializer, resolution rule or keep rule is
introduced. A consumer still enables ordinary R8/resource shrinking to remove
unused native resources.

The capability promises the new native-XML helper and reader protocol, not all
Compose APIs across release families. The capability-resolution check proves
dependency selection only; a separate integration below verifies a precompiled
1.11.1 generated resource pack under a 1.12.1 consumer. A strict framework-wide
1.11.1-to-1.12.1 comparison detects upstream changes to the public four-argument
`ResourceEnvironment` constructor and internal `AsyncCache` classes/accessors.
Each proposal still preserves its own baseline's ABI; see
[runtime proposal evidence](../RUNTIME_PROPOSAL_RESULTS.json).

Accessor/packaging tasks track the enrollment manifests as inputs. Their output
locations are separate from outline generation; toggling the backend does not
change the original XML geometry. Enabling removes the enrolled copies from the
asset-copy task's own output, while disabling restores the stock accessor and
asset paths. Incremental false → true → false behavior needs the integration gate
below; directory wiring alone is not evidence that the transition works.

## Reproduce the local candidate

Use JDK 21. Preparation downloads checksum-pinned source/binary/metadata artifacts
from Maven Central and applies the tiny baseline import patch and shared behavior
patch with `--fuzz=0`. It does not
run Gradle unless `--build` is specified.

```bash
python3 benchmarks/resource-pipeline/native-raw/upstream-generator/bootstrap.py \
  --baseline 1.12.1 --output /tmp/compose-native-xml-1121
python3 benchmarks/resource-pipeline/native-raw/upstream-generator/bootstrap.py \
  --baseline 1.11.1 --output /tmp/compose-native-xml-1111

python3 benchmarks/resource-pipeline/native-raw/upstream-generator/bootstrap.py \
  --baseline 1.12.1 --output /tmp/compose-native-xml-1121 --build \
  --repository /tmp/compose-native-xml-maven \
  --candidate-version 1.12.2-native-xml04
```

The build uses this checkout's `tooling/gradlew` unless `--gradle` is supplied.
It recompiles the complete resource-generator package, runs focused tests, and
uses the same KotlinPoet 2.1.0 / Shadow 9.1.0 relocation as upstream. The overlay
replaces only the original plugin's resource-package classes. Unrelated classes,
resources and original module metadata are retained; the `compose` Kotlin module
name is preserved. This avoids vendoring or rebuilding unrelated plugin modules.
The local source archive contains the unrelocated, upstream-shaped source patch.

Only a passing build is published to the specified local repository, including
the `org.jetbrains.compose` plugin marker. Existing candidate coordinates are
rejected. `build.log`, archived attempt logs, and versioned `publication-*.json`
reports record compiler results and input, patch and artifact identities.
`publication.json` points to the latest local result. Use an explicit local candidate version and a
fresh version/repository after changing source; never upload these coordinates.

The fixture must explicitly select a matching candidate runtime until the
upstream release exists. The plugin's normal Compose dependency string still
describes its original baseline; the new capability requirement does not invent
or publish a supported runtime version.

## Validation status and boundaries

- The same behavior patch applies to checksum-verified 1.12.1 and 1.11.1 inputs.
  Their only patch adapter is an import hunk: 1.11.1 predates upstream's
  `PathSensitive` imports. Each baseline retains its own qualifier implementation.
  Preparation records the canonical effective diff, and both resulting source
  trees match the previously separate patches byte-for-byte.
- Both baselines compile and pass both focused tests using JDK 21 and Gradle
  8.14.5. Final local candidates are `1.11.2-native-xml04` and
  `1.12.2-native-xml04`; exact source/artifact identities are in
  [GENERATOR_PROPOSAL_RESULTS.json](../GENERATOR_PROPOSAL_RESULTS.json).
  Candidate 03 retained an unused native resource on 1.11.1 through a shared
  lazy initializer, which is why candidate 04 isolates native holders.
- The focused test compares disabled `Res` and accessor output byte-for-byte with
  the original plugin loaded separately. It also exercises generic ownership,
  two physical theme files, untouched fonts/authored XML, and Android/JVM factory
  generation. It does not substitute for published-consumer validation.
- [verify.py](verify.py) prepares the separate published Symbols producer and
  consumer gate, including disabled/on/off/on transitions, configuration-cache
  reuse, normal Compose painter calls, APK/AAB bytes and unrelated assets. Its
  nine stages pass on both final candidates: native tasks also restore from the
  build cache, JVM resource bytes remain unchanged, and APK/AAB output contains
  the used owned XML exactly once and no unused owned XML. Authored drawable and
  unrelated file bytes remain intact. The final 1.12.1 candidate also passes a
  fresh install and cold launch on the Pixel 6a at API 37: the selected icon is
  visually confirmed, with no resource or fatal errors in the app-scoped log.
  This is functional evidence, not a performance measurement or API 23 result.
- Holders add one private class per enrolled logical resource. The checked
  1.11.1 fixture adds two private classes with the same ten generated Kotlin files
  and unchanged public drawable-facade signatures. Full-pack build timings and
  sizes have not been remeasured with this holder implementation; earlier model
  measurements must not be presented as measurements of this generator.
- A precompiled 1.11.1 resource pack also passes an ordinary consumer upgrade to
  UI/foundation/runtime 1.12.1 and resource candidate `1.12.2-native-raw02`.
  All eleven stages pass, including unchanged producer AAR bytes, exact APK/AAB
  used/unused XML retention, and configuration-cache reuse. Reproduce this
  generated-pack subset by adding `--upgrade-consumer-to 1.12.1` and
  `--upgrade-runtime-to 1.12.2-native-raw02` to the 1.11.1 [verify.py](verify.py)
  invocation. The [published-consumers CI job](../../../../.github/workflows/ci.yml)
  includes the same case; its hosted status is independent of these local results.
  The upgraded consumer also passes a cold launch and icon-render check on the
  Pixel 6a at API 37, with no resource or fatal errors in the app-scoped log.
  This validates the generated pack subset, not framework-wide ABI compatibility.
- Actual-generator consumer checks for physical qualifiers, raw/URI/partial-read
  APIs, previews/Hot Reload, Android API 23, and Apple/web targets remain pending.
  The earlier output-model fixtures do not close these gates. Custom readers,
  test compilations, and split-delivery configurations also require explicit
  coverage before a production rollout.
- Direct `AssetManager` access to old owned asset paths is not preserved. Runtime
  helper/URI compatibility remains subject to the upstream reader contract.

The source seams and scope are described in the parent
[prototype documentation](../README.md). A locally passing contribution candidate
does not establish official upstream availability.
