# Dependency-only Compose drawable shrinking prototype

**Production integration is deferred**, tracked in [feature request #41](https://github.com/HLCaptain/symbols/issues/41).
The [upcoming feature plan](#upcoming-feature-deferred-producer-opt-in) proposes a
disabled-by-default producer flag. That property is not implemented; the current
sample apps and published libraries do not use this backend.

This is an isolated upstream experiment for Android XML drawables consumed through
Compose Multiplatform's existing resource API. The normal call remains:

```kotlin
Image(
    painterResource(Res.drawable.material_symbols_outlined_10k_ue951),
    contentDescription = null,
)
```

The consuming app adds the published resource-library dependency. It does not
apply a Symbols/backend plugin, select a backend, add keep rules, or initialize a
reader. Its ordinary Android and Compose compiler plugins remain necessary.
Resource shrinking must already be enabled, as it is in the study's `shrunk`
build type. Existing opt-ins on experimental Compose raw-resource APIs still
apply; this experiment adds no backend opt-in.

The producer script's `--backend` argument only constructs the experiment's asset
control and native candidate. It is not a consuming-app flag or production DSL.

Production Symbols generation and the upstream Compose distribution are unchanged.
Measured outcomes and the status of individual gates belong in [RESULTS.md](RESULTS.md)
and [RESULTS.json](RESULTS.json). First-build durations printed by `run.py` are
diagnostic; repeated consumer timings are collected separately by `timing.py`.

## What the prototype changes

There are two separate parts:

| Part | Actual implementation here | Boundary |
| --- | --- | --- |
| Producer model: `publisher.py` | Reads a stock published Symbols Android AAR and its sources JAR, retains the selected common `Res` getter declarations/file layout, and generates per-resource `expect`/`actual` location functions. | It rewrites an existing generator's output. It is not a patched or released Compose Gradle plugin. The parser intentionally targets this published outlined XML pack. |
| Runtime patch: `runtime.py` and `resource-reader.patch` | Fetches checksum-pinned Compose 1.12.1 sources/artifacts and rebuilds its Android resource-reader AAR with a small source patch. | This is an actual runtime change. Common metadata and non-Android runtime variants still come from upstream 1.12.1. |

On Android, each location function references one generated `R.raw` field and
returns `compose-android-resource://<resource-id>/drawable.xml`. Original XML
bytes move from `assets/composeResources/...` into `res/raw`; no duplicate copy
of this pack remains in Android assets. They stay text XML rather than becoming
Android's compiled drawable XML. The `.xml` suffix keeps Compose's existing
`painterResource`/`vectorResource` decoding path.

The default Android reader resolves the ID with `Resources.openRawResource` when
reading. `readPart` goes through the same stream path. `getUri` checks the resource
and returns `android.resource://<installed-application-id>/<resource-id>`; it does
not use the library namespace as the URI authority. Ordinary asset/classloader
paths retain the existing fallback.

Constructing a descriptor only reads an `R.raw` ID; it needs no `Context`. The
reader obtains the context at read time through Compose's existing content
provider and preview-context lifecycle. No application initializer is installed
by the prototype.

The resulting reference graph is visible to R8 and Android resource shrinking:

```text
used Res getter -> its platform location function -> R.raw entry -> XML bytes
```

Direct access can discard unused getters, functions and resources. Dynamic
`Res.allDrawableResources` access intentionally keeps the catalog reachable.
`Res.readBytes(path)` and `Res.getUri(path)` use a generated raw-path dispatcher
that conservatively references the owning pack; they do not promise per-file
shrinking for arbitrary paths. Dispatch is chunked to avoid JVM method-size
limits. These rules affect the owning pack, not every resource in the app.

The model publisher emits Android and JVM variants. Its JVM implementation keeps
the stock `composeResources/...` paths and bytes. Preserving asset behavior on
Apple and web is part of the upstream design; this model does not publish those
producer variants. Source and binary compatibility require separate verification
of the generated library and the patched runtime, not merely unchanged Kotlin
call syntax.

The basic publisher handles unqualified XML. The separate contract fixture adds
two `ResourceItem` variants with distinct raw IDs and keeps selection in Compose.
A production generator must preserve selection by the explicit Compose resource
environment; delegating it entirely to Android's current configuration would
change behavior for callers selecting a different environment.

## Supported experiment and remaining boundaries

| Area | Scope |
| --- | --- |
| Input | Published outlined drawable XML and generated sources from the same stock Symbols version. `--limit 3` bounds the first gate; omitting the limit models all 3,802 distinct glyphs. |
| Consumers | Separate Gradle projects resolving Maven publications, with no project substitution or app-side generator/backend plugin. Direct-zero, direct-one, dynamic-one and raw-path cases have explicit retention contracts. |
| Runtime selection | `org.jetbrains.compose.components:components-resources:1.12.2-native-raw01`, resolved transitively through the native pack. This version is local-only, not a JetBrains release or a coordinate to upload. |
| Toolchain | The prepared runtime uses Kotlin 2.4.20, AGP 9.4.1, compile SDK 37 and min SDK 23. Producer/consumer versions come from this checkout's catalog; additional supported-version checks need their own evidence. |
| Qualifiers and lifecycle | `contracts.py` prepares a separate small light/dark, pre-context descriptor, byte-range, missing-resource, URI and asset-fallback fixture. Its results do not establish support for every locale/density qualifier, overlay or split configuration. |
| Preview and custom readers | Existing lifecycle hooks remain in place, but IDE previews and callers replacing `LocalResourceReader` remain separate gates. A custom reader receiving the prototype location token is not automatically compatible. |
| External Android access | Code hardcoding `AssetManager.open("composeResources/...")` cannot read a resource moved out of assets. Code assuming a `file:///android_asset/` URI also needs consideration. The supported path is the Compose API or a consumer of the returned URI through `ContentResolver`; URI schemes must not be assumed unchanged. |
| Other resource types | Fonts, glyph subsetting, strings, plurals, arbitrary files and raster formats are outside this XML-drawable model. Unrelated assets must remain intact. |
| Publication coverage | APK/AAB delivery, transitive and precompiled binary consumers, complete API/ABI checks, resource-name collision handling and other KMP targets require explicit gates before adoption. See the results for which checks have actually completed. |

`verify.py` checks the resource table rather than assuming APK filenames survive
optimization. It requires the exact retained raw-resource set, compares retained
bytes with the published AAR, and rejects duplicated owned Compose assets. The
producer manifest records each stock XML hash and the input AAR/sources hashes.
`run.py` additionally checks configuration-cache reuse. Neither script installs
an APK or proves that it renders correctly on a device.

Keep versions and repositories immutable within a cohort. `--resume` checks the
fixture inputs and local pack/runtime publication bytes before accepting an old
successful APK. Use fresh output directories and coordinates when changing a
publisher or reader; changing local Maven bytes in place can also interact with
Gradle's dependency cache. Preserve failed evidence instead of overwriting it.

## Reproduce the initial gate

Run from this checkout with Python 3.11+, `patch`, JDK 21, the JDK 17 compiler
toolchain, Android platform 37 and an installed `aapt2`. Provision the repository's
Python font-generation requirements if producing the stock input below. Choose a
fresh work directory on a disk with sufficient space; full-pack fixtures and old
benchmark outputs can exhaust a RAM-backed `/tmp`.

```bash
export JAVA_HOME=/path/to/jdk21
export ANDROID_HOME=/path/to/android-sdk
NATIVE_RAW_WORK=/path/on/disk/native-raw-repro
NATIVE_RAW_SCRIPTS="$PWD/benchmarks/resource-pipeline/native-raw"
NATIVE_RAW_AAPT2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
mkdir -p "$NATIVE_RAW_WORK"
```

Start with an ordinary published AAR and its matching Android sources JAR. The
recorded study used `0.0.0-study-b` from the isolated stock repository
`/tmp/symbols-study-maven-B`; both input hashes are recorded by each publisher.
To produce a fresh stock input from this checkout instead:

```bash
./gradlew :modules:material-compose-drawables-outlined:publishAndroidPublicationToMavenLocal \
  -PVERSION_NAME=0.0.0-stock-raw-probe \
  -Dmaven.repo.local="$NATIVE_RAW_WORK/stock-maven" \
  --no-daemon --max-workers=1
NATIVE_RAW_STOCK="$NATIVE_RAW_WORK/stock-maven/io/github/hlcaptain/symbols-material-compose-drawables-outlined-android/0.0.0-stock-raw-probe"
NATIVE_RAW_AAR="$NATIVE_RAW_STOCK/symbols-material-compose-drawables-outlined-android-0.0.0-stock-raw-probe.aar"
NATIVE_RAW_SOURCES="$NATIVE_RAW_STOCK/symbols-material-compose-drawables-outlined-android-0.0.0-stock-raw-probe-sources.jar"
```

Use the equivalent paths for an existing stock publication. The prototype does
not need its original generator in the consuming app. Build the local reader,
then publish an asset control and native candidate from exactly the same inputs:

```bash
python3 "$NATIVE_RAW_SCRIPTS/runtime.py" \
  --output "$NATIVE_RAW_WORK/runtime" \
  --repository "$NATIVE_RAW_WORK/maven" --gradle "$PWD/gradlew"

for native_raw_backend in assets native; do
  python3 "$NATIVE_RAW_SCRIPTS/publisher.py" \
    --aar "$NATIVE_RAW_AAR" --sources "$NATIVE_RAW_SOURCES" \
    --repository "$NATIVE_RAW_WORK/maven" \
    --output "$NATIVE_RAW_WORK/publisher-$native_raw_backend" \
    --version "0.0.0-$native_raw_backend-probe01" \
    --backend "$native_raw_backend" --limit 3
  "$NATIVE_RAW_WORK/publisher-$native_raw_backend/gradlew" \
    -p "$NATIVE_RAW_WORK/publisher-$native_raw_backend" \
    publishAllPublicationsToProbeRepository --no-daemon --max-workers=1
done
```

`runtime.py --prepare-only` only downloads, verifies and prepares the runtime
project; it does not build or publish it. The regular command writes
`publication.json` with upstream checksums, patch identity and rebuilt artifact
hashes. Nothing in this sequence contacts a remote publishing service.

Generate matching dependency-only apps and run the APK/cache gate:

```bash
for native_raw_backend in assets native; do
  for native_raw_case in direct-0 direct-1 dynamic-1 raw-1; do
    python3 "$NATIVE_RAW_SCRIPTS/consumer.py" \
      --publisher "$NATIVE_RAW_WORK/publisher-$native_raw_backend" \
      --repository "$NATIVE_RAW_WORK/maven" \
      --output "$NATIVE_RAW_WORK/consumers/$native_raw_backend-$native_raw_case" \
      --access "${native_raw_case%-*}" --count "${native_raw_case##*-}"
  done
done
python3 "$NATIVE_RAW_SCRIPTS/run.py" \
  --fixtures "$NATIVE_RAW_WORK/consumers" \
  --repository "$NATIVE_RAW_WORK/maven" --aapt2 "$NATIVE_RAW_AAPT2" \
  --output "$NATIVE_RAW_WORK/gate.json"
```

`--case native-direct-1` selects one case; repeat `--case` as needed. Use
`--resume` only with the same frozen cohort. `--access raw` exercises the existing
read/URI contract without enumerating the drawable catalog, so enumeration cannot
hide a broken raw-path retention graph.

For a full-pack comparison, generate both publishers again without `--limit`,
using fresh versions/directories, and regenerate their consumers. Publishing is
outside the measured interval. After their retention gates pass, the repeated
consumer-only comparison is:

```bash
python3 "$NATIVE_RAW_SCRIPTS/timing.py" \
  --assets "$NATIVE_RAW_WORK/consumers/assets-direct-1" \
  --native "$NATIVE_RAW_WORK/consumers/native-direct-1" \
  --repository "$NATIVE_RAW_WORK/maven" --aapt2 "$NATIVE_RAW_AAPT2" \
  --repeat 3 --output "$NATIVE_RAW_WORK/timing-direct-1"
```

Both fixtures must use the same corpus, selection, access mode and toolchain.
The harness alternates backend order, excludes warm-ups, and measures clean and
no-change builds with build/configuration caches disabled. It retains warm
dependency downloads and transforms. RSS is a sampled process-tree sum, not Java
heap or unique resident memory. Keep raw logs, APKs, HTML profiles and device
captures local; commit only reproduction code and reviewed compact results.

## Additional published-consumer gates

Use the exact current publisher/repository from the results, not outputs from a
superseded iteration. These examples take paths as inputs and each create a fresh
small consumer. They do not rerun the full usage matrix. A prepared fixture or a
successful build is not itself an accepted API/ABI or device-rendering result.

```bash
NATIVE_RAW_SMALL=/path/to/current/one-to-three-icon-native-publisher
NATIVE_RAW_PACK=/path/to/current/native-publisher
NATIVE_RAW_REPOSITORY=/path/to/its/local-maven-repository
NATIVE_RAW_PUBLISHED_AAR=/path/to/its/published-android.aar
NATIVE_RAW_STOCK_RUNTIME="$NATIVE_RAW_WORK/runtime/downloads/components-resources-android-1.12.1.aar"
NATIVE_RAW_STDLIB=/path/to/kotlin-stdlib-2.4.20.jar
NATIVE_RAW_ADB="$ANDROID_HOME/platform-tools/adb"
NATIVE_RAW_SERIAL=your-device-serial
```

**Precompiled getter alias.** `alias.py` uses `javac --release 11` with the stock
pack/runtime AARs and Kotlin stdlib to compile a Java caller of the original JVM
getter facade. The consuming app uses that unchanged helper JAR with the native
publication; it does not recompile the helper against the candidate.

```bash
python3 "$NATIVE_RAW_SCRIPTS/alias.py" \
  --publisher "$NATIVE_RAW_PACK" --repository "$NATIVE_RAW_REPOSITORY" \
  --stock-pack-aar "$NATIVE_RAW_AAR" --stock-runtime-aar "$NATIVE_RAW_STOCK_RUNTIME" \
  --stdlib "$NATIVE_RAW_STDLIB" --jdk "$JAVA_HOME" \
  --output "$NATIVE_RAW_WORK/extra-consumers/alias"
python3 "$NATIVE_RAW_SCRIPTS/run.py" \
  --fixtures "$NATIVE_RAW_WORK/extra-consumers" --case alias \
  --repository "$NATIVE_RAW_REPOSITORY" --aapt2 "$NATIVE_RAW_AAPT2" \
  --output "$NATIVE_RAW_WORK/alias-gate.json"
```

This checks a real precompiled call site, not every binary API or Kotlin metadata
contract. Installation and rendering must be checked separately.

`abi.py` checks all original public/protected JVM declarations and Kotlin module
names. Run it for the pack and again for the stock/patched Android resource
runtime. Additive helpers are allowed; removed or changed members fail the check.

```bash
python3 "$NATIVE_RAW_SCRIPTS/abi.py" \
  --stock-aar "$NATIVE_RAW_AAR" --candidate-aar "$NATIVE_RAW_PUBLISHED_AAR" \
  --javap "$JAVA_HOME/bin/javap"
```

Independently compare every published XML byte and license notice against the
stock AAR, supplying the native/control Android and JVM artifacts:

```bash
PYTHONPATH="$NATIVE_RAW_SCRIPTS" python3 - \
  "$NATIVE_RAW_AAR" "$NATIVE_RAW_PUBLISHED_AAR" \
  /path/to/native-jvm.jar /path/to/control-android.aar /path/to/control-jvm.jar <<'PY'
import json, sys
from verify import publication_bytes
print(json.dumps(publication_bytes(*sys.argv[1:]), indent=2))
PY
```

**Published KMP bridge and JVM fallback.** The generated Android app depends only
on the intermediate KMP library. The separate JVM executable obtains bytes and a
URI through that published bridge and checks both against the stock XML hash.

```bash
python3 "$NATIVE_RAW_SCRIPTS/compatibility.py" \
  --publisher "$NATIVE_RAW_PACK" --repository "$NATIVE_RAW_REPOSITORY" \
  --version 0.0.0-native-bridge-probe01 --output "$NATIVE_RAW_WORK/transitive"
"$NATIVE_RAW_WORK/transitive/library/gradlew" -p "$NATIVE_RAW_WORK/transitive/library" \
  publishAllPublicationsToProbeRepository --no-daemon --max-workers=1
python3 "$NATIVE_RAW_SCRIPTS/run.py" \
  --fixtures "$NATIVE_RAW_WORK/transitive" --case android \
  --repository "$NATIVE_RAW_REPOSITORY" --aapt2 "$NATIVE_RAW_AAPT2" \
  --output "$NATIVE_RAW_WORK/transitive-android-gate.json"
"$NATIVE_RAW_WORK/transitive/jvm/gradlew" -p "$NATIVE_RAW_WORK/transitive/jvm" \
  run --no-daemon --max-workers=1
```

Require the JVM assertions and `JVM_TRANSITIVE_OK` marker, then check the Android
app on a device. `consumer.py --agp <version>` can generate a separate consumer
toolchain case; its successful results must not be inferred from the producer's
AGP version. Only versions compatible with the Compose dependency floor are
meaningful candidates.

**Reader and qualifier contract.** This clones a one-to-three-icon native
publisher, adds a second physical XML variant, and generates a test-only
Application that observes a descriptor before provider initialization. It is a
test observer, not an application integration requirement.

```bash
python3 "$NATIVE_RAW_SCRIPTS/contracts.py" \
  --publisher "$NATIVE_RAW_SMALL" --repository "$NATIVE_RAW_REPOSITORY" \
  --version 0.0.0-native-contract-probe01 --output "$NATIVE_RAW_WORK/contracts"
"$NATIVE_RAW_WORK/contracts/publisher/gradlew" -p "$NATIVE_RAW_WORK/contracts/publisher" \
  publishAllPublicationsToProbeRepository --no-daemon --max-workers=1
python3 "$NATIVE_RAW_SCRIPTS/run.py" \
  --fixtures "$NATIVE_RAW_WORK/contracts" --case consumer \
  --repository "$NATIVE_RAW_REPOSITORY" --aapt2 "$NATIVE_RAW_AAPT2" \
  --output "$NATIVE_RAW_WORK/contracts-gate.json"
"$NATIVE_RAW_ADB" -s "$NATIVE_RAW_SERIAL" install -r \
  "$NATIVE_RAW_WORK/contracts/consumer/build/outputs/apk/shrunk/"*.apk
"$NATIVE_RAW_ADB" -s "$NATIVE_RAW_SERIAL" shell am start -S -W \
  -n io.github.hlcaptain.symbols.usage.contracts/study.MainActivity
```

Capture this launch's log and inspect its rendered variants. Require
`PRE_CONTEXT_DESCRIPTOR_OK`, both `CONTRACT_OK theme=LIGHT/DARK` messages and
`CONTRACT_ALL_OK`, with no application exception. Old log messages from another
launch do not establish success. `contracts.json` and the generated fixture
manifest record the expected component, byte hashes and markers.

**Unshrunk control and app-bundle delivery.** Supply the checksum-pinned official
`bundletool-all-1.18.3.jar`, absolute input/output paths, and a connected device.
`bundle.py` builds the unshrunk APK and shrunk AAB, checks the full unshrunk pack,
creates device-specific APKs, verifies the owned raw entries in `base-master.apk`,
and installs that APK set.

```bash
python3 "$NATIVE_RAW_SCRIPTS/bundle.py" \
  --fixture /absolute/path/to/native-direct-1-consumer \
  --aar "$NATIVE_RAW_PUBLISHED_AAR" \
  --bundletool /absolute/path/to/bundletool-all-1.18.3.jar \
  --java "$JAVA_HOME/bin/java" --adb "$NATIVE_RAW_ADB" \
  --serial "$NATIVE_RAW_SERIAL" --aapt2 "$NATIVE_RAW_AAPT2" \
  --output "$NATIVE_RAW_WORK/bundle-direct-1"
```

Installation is not a rendering assertion: launch and inspect the installed app
separately. This gate covers the current raw XML corpus in the base module; it
does not validate arbitrary qualifier/configuration splits or dynamic features.

Fast generator/parser contract checks do not invoke Gradle:

```bash
python3 -m unittest discover -s "$NATIVE_RAW_SCRIPTS" -p 'test_*.py'
```

## Upcoming feature: deferred producer opt-in

Status: **upcoming feature; implementation deferred**. This plan does not enable
pruning, introduce a production Compose fork, or change sample applications.
Existing prototype code and measurements remain experimental evidence.

Proposed API in the existing `symbolFonts` extension, **not currently available**:

```kotlin
symbolFonts {
    experimentalComposeResourcePruning.set(true)
}
```

The proposed property is a `Property<Boolean>` with a default of `false`.

- Unset or `false` preserves existing packaging and compatibility, without
  requiring the experimental backend's runtime support.
- `true` selects shrinkable Android packaging for this project's generated
  Compose XML drawables, using native `res/raw` references behind the existing
  `Res.drawable` and `DrawableResource` APIs. Actual unused-resource removal still
  requires the consuming app's normal code/resource shrinking configuration.
- Enabling the backend with unsupported official Compose runtime/generator APIs
  must fail with an actionable compatibility message. It must not silently fall
  back, install a fork, or add dependency substitutions.
- This is a **producer setting**. It cannot retrofit an already published AAR.
  Apps consuming opted-in library versions need no producer flag, reader wrapper,
  initializer or extra plugin; their existing resource calls remain unchanged.
  An application generating its own resources is also a producer and would opt in
  in that generating project.
- Fonts, native `androidDrawables()` output, unrelated resources and non-Android
  packaging remain unchanged. This does not provide font glyph subsetting.

Production adoption still requires compatible official Compose integration while
retaining Android API 23 and the existing toolchain support boundaries. Neither
the existing local runtime coordinate nor a maintained Compose fork is selected
for production. The opt-in is a future producer-only exception to the earlier
version-only plan; consumers of published libraries still use ordinary version
upgrades. Upgrading versions alone does not activate this unimplemented feature.

Future acceptance checks:

- Verify default-off packaging and compatibility, explicit opt-in, and clear
  errors for unsupported backend/runtime combinations.
- Test published Outlined, Rounded and Sharp packs plus custom font/SVG-to-XML
  generation in independent Android/KMP, transitive and precompiled consumers.
- Preserve public API/ABI, qualifiers, raw reads, usable URIs, previews and
  delegating custom readers; verify APK/AAB retention and non-Android behavior.
- Keep dynamic/raw access conservative and preserve unrelated resources.
- Track the flag as a generation-task input and test configuration/build caches
  and `false -> true -> false` transitions without stale or duplicate resources.

Only this plan and its feature request are being updated now. No production
implementation, sample configuration or dependency change is part of this work.

## Upstream integration proposal

The source of truth for the investigated seams is the published
[Compose 1.12.1 Gradle-plugin sources](https://repo.maven.apache.org/maven2/org/jetbrains/compose/compose-gradle-plugin/1.12.1/compose-gradle-plugin-1.12.1-sources.jar)
and [Android resource-runtime sources](https://repo.maven.apache.org/maven2/org/jetbrains/compose/components/components-resources-android/1.12.1/components-resources-android-1.12.1-sources.jar).

1. Extend `GenerateResourceAccessorsTask` and `GeneratedResClassSpec.kt`
   (`getAccessorsSpecs`/`getChunkFileSpec`) to emit platform location factories
   while retaining public getters, resource IDs, content hashes, qualifiers,
   original facade names and collector behavior. Integrate raw-path dispatch in
   `getResFileSpec`, which currently prefixes paths in `Res.readBytes/getUri`.
   Replace the model's source-text rewriting with generator-level tests.
2. Wire task-backed Android `res/raw` output through AGP's generated-resource
   source API for both Android and native Android KMP variants.
   `AndroidResources.kt` currently registers
   `CopyResourcesToAndroidAssetsTask` through
   `componentSources.assets.addGeneratedSourceDirectory`. Partition owned XML
   resources there, retaining unrelated assets and non-Android packaging; do not
   add an app plugin or post-process assembled APKs.
3. Integrate the reader change with supported runtime/generator versioning.
   `ResourceReader.android.kt` is the read/URI seam;
   `AndroidContextProvider.kt` and `currentOrPreview` own context lifecycle.
   `ImageResources.kt` selects XML decoding from the location suffix. The current
   string token is a prototype encoding, not a promised public format. Resolve
   preview, custom-reader, URI and external asset-access compatibility before
   making the proposed producer opt-in available. Keep it disabled by default.
4. Gate adoption on published-artifact retention and byte parity, raw/URI and
   qualifier contracts, unchanged public/binary APIs, transitive consumers,
   supported AGP/Kotlin configurations, APK/AAB delivery and actual rendering.
   Keep dynamic/raw access conservative and verify unrelated resources survive.
   Expand other targets and resource types only with their own evidence.

This preserves the ordinary
[Compose resource usage APIs](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html)
for typed callers while making the Android reference graph visible to shrinking.
Production adoption waits for supported Compose generator/runtime integration.
