# Upstream Compose generator contribution candidate

These are source patches against the published Compose Gradle plugin, not a
production Symbols backend or an official Compose feature. The matching runtime
contribution must provide `getAndroidResourcePath(Int, String)` and advertise
`org.jetbrains.compose.components:components-resources-native-xml-v1` on its
Android leaf variants. Production adoption remains blocked until supported
upstream generator/runtime releases exist. The upstream proposal remains local
and has not been submitted.

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
descriptors are stored in an initialized `val` inside a private per-resource
object. Kotlin initializes that object on first access, so the public getter
returns the same cached descriptor without a second `lazy` wrapper. This keeps
unused descriptors out of a shared file initializer. Holder names include
source-set/chunk identity; `@field:ResourceContentHash` retains the original folded
hash. Non-enrolled resources keep their original lazy delegates and annotations.
Descriptor construction performs no I/O or Android context lookup. Android
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
asset paths. The candidate05 integration below verifies false → true → false →
true transitions, configuration-cache reuse, and native task build-cache restoration.

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
  --candidate-version 1.12.2-native-xml05
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

Final local generator candidates are `1.11.2-native-xml05` and
`1.12.2-native-xml05`. The reader candidates remain `1.11.2-native-raw02` and
`1.12.2-native-raw02`. Exact source/artifact hashes and compact evidence are in
[GENERATOR_PROPOSAL_RESULTS.json](../GENERATOR_PROPOSAL_RESULTS.json).

| Check | Candidate05 evidence |
| --- | --- |
| Focused generator checks | Both baselines compile and pass two tests; default-off `Res`/accessor output matches the original plugin byte-for-byte. Ownership, physical theme files, untouched assets, and platform factories are checked. |
| Published Android/JVM producer | The 1.11.1 fixture passes disabled/on/off/on, configuration-cache reuse, native task build-cache restoration, JVM byte parity, and exact APK/AAB retention. |
| Precompiled pack upgrade | All eleven stages pass with the unchanged 1.11.1 producer AAR under UI/foundation/runtime 1.12.1 and reader `1.12.2-native-raw02`. |
| Actual-generator reader contracts | The 1.12.1 producer/consumer passes four build/reuse stages and physical light/dark, raw-read/URI/partial-read checks on an API 23 emulator. |
| Generated ABI/cache shape | All 17 public class declarations in the checked 1.11.1 fixture match candidate04. Both content hashes are preserved on backing fields; holder getters only read cached values, with no lazy delegate or lambda method. |
| Official source-tree compiler | A separate local checkout of upstream master `d337cc6` builds the complete plugin using Kotlin 2.2.0, language/API 2.0 and JVM 11, then passes two ported JUnit 5 tests against upstream's stock expected fixtures. |
| JS IR and WasmJS libraries | Both compile and package the original asset paths and byte-identical resource ZIPs: two owned XMLs, one authored XML and one unrelated file. No executable/browser test is implied. |

[verify.py](verify.py) reproduces the published-producer gate. For the precompiled
upgrade, add `--upgrade-consumer-to 1.12.1` and
`--upgrade-runtime-to 1.12.2-native-raw02` to its 1.11.1 invocation. This validates
the generated resource-pack subset, not universal framework ABI compatibility.
The [published-consumers CI job](../../../../.github/workflows/ci.yml) includes
that case. All hosted jobs passed at commit `a8b8dac` for candidate04; candidate05
hosted checks are not yet verified by this report.

One private class remains per enrolled logical resource. Candidate05 removes the
redundant lazy wrapper inside each holder; it does not claim to reduce holder
class count. The unshrunk native AAR remains larger than the asset-based AAR.
Full-pack size, retention and controlled timing results belong to the separate
[full-pack report](../GENERATOR_FULL_PACK_RESULTS.json); overlay build durations
are not performance measurements.

Candidate04's matched and precompiled-upgrade Pixel 6a/API 37 checks remain
[historical evidence](https://github.com/HLCaptain/symbols/blob/a8b8dacee527486706256c6be74733180f8a87b5/benchmarks/resource-pipeline/native-raw/GENERATOR_PROPOSAL_RESULTS.json).
Those APKs are not relabeled as candidate05. Candidate05's device result is the
API 23 reader/qualifier fixture described below.

Actual IDE preview/Hot Reload, Apple targets, and JS/Wasm browser execution remain
pending. Static Hot Reload source analysis supports field content hashes and
class-initializer invalidation; it does not replace a UI test. Custom readers,
test compilations, and split-delivery configurations also need explicit coverage.
Direct `AssetManager` access to removed owned asset paths is not preserved.

The source seams and scope are described in the parent
[prototype documentation](../README.md). Local source-tree handoff patches and
JUnit 5 exports remain ignored local artifacts. A locally passing contribution
candidate does not establish official upstream availability.

## Actual generated-resource contracts

[contracts.py](contracts.py) builds a small real-generator fixture against the
1.12.1 Compose baseline. It reuses the existing reader-contract Activity but
creates no handwritten `Res` facade or platform factory. Two Symbols outputs and
physical `drawable-light/contract.xml` / `drawable-dark/contract.xml` files pass
through the candidate generator. Runtime-supplied raw paths deliberately retain
all four XML files; the archive checks require each exactly once and no duplicate
owned XML assets. The test-only Application observes descriptor creation before
AndroidContextProvider; it does not initialize the backend.

The command below only builds and checks archives/cache reuse, so it can run
without a device. Use JDK21, `ANDROID_HOME`, and repositories containing the
previously published Symbols and matching local Compose candidates. The default
generator is `1.12.2-native-xml05`; use `--plugin-version 1.12.2-native-xml04` with a
fresh output directory for the earlier implementation. Runtime and Gradle paths
can be supplied through `--runtime-version` and `--gradle`.

```bash
CONTRACT_OUTPUT="$PWD/build/resource-integration/generator-contracts-05"
python3 benchmarks/resource-pipeline/native-raw/upstream-generator/contracts.py \
  --repository "$PWD/build/resource-integration/maven" \
  --upstream-repository /tmp/compose-native-xml-maven \
  --symbols-version 0.0.0-pruning-api02 \
  --output "$CONTRACT_OUTPUT"
```

For the separate runtime gate, create a disposable API23 emulator. The following
commands keep its AVD/user state inside the fixture output and use emulator port
5584. They do not target a connected phone. Install the system image first if it
is absent:

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'system-images;android-23;default;x86_64'
export ANDROID_USER_HOME="$CONTRACT_OUTPUT/emulator/user"
export ANDROID_AVD_HOME="$CONTRACT_OUTPUT/emulator/avds"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
  --name symbols_generator_contract_api23 \
  --package 'system-images;android-23;default;x86_64' \
  --path "$ANDROID_AVD_HOME/contract23.avd" --device pixel <<<'no'
"$ANDROID_HOME/emulator/emulator" -avd symbols_generator_contract_api23 \
  -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect \
  -memory 1536 -cores 2 -port 5584 >"$CONTRACT_OUTPUT/emulator.log" 2>&1 &

python3 - "$ANDROID_HOME/platform-tools/adb" "$CONTRACT_OUTPUT" <<'PY'
import json, subprocess, sys, time
from pathlib import Path
adb, output = sys.argv[1], Path(sys.argv[2])
report = json.loads((output / "result.json").read_text())
def call(*args):
    return subprocess.run([adb, "-s", "emulator-5584", *args],
                          capture_output=True, text=True, check=True).stdout
try:
    for attempt in range(60):
        try:
            if call("shell", "getprop", "sys.boot_completed").strip() == "1":
                break
        except subprocess.CalledProcessError:
            pass
        time.sleep(1)
    else:
        raise AssertionError("API23 emulator did not boot")
    assert call("shell", "getprop", "ro.build.version.sdk").strip() == "23"
    assert "Success" in call("install", "-r", report["apk"])
    call("logcat", "-c")
    assert "Status: ok" in call("shell", "am", "start", "-W", "-n", report["launch_component"])
    for attempt in range(20):
        log = call("logcat", "-d", "-v", "threadtime", "NativeRawContract:I", "AndroidRuntime:E", "*:S")
        (output / "device-contracts.log").write_text(log)
        assert "FATAL EXCEPTION" not in log, log
        if all(marker in log for marker in report["required_log_markers"]):
            break
        time.sleep(2)
    else:
        raise AssertionError("Missing contract markers; inspect device-contracts.log")
    shot = subprocess.run([adb, "-s", "emulator-5584", "exec-out", "screencap", "-p"],
                          capture_output=True, check=True).stdout
    (output / "device-contracts.png").write_bytes(shot)
    print("API23 reader and physical qualifier contracts passed")
finally:
    subprocess.run([adb, "-s", "emulator-5584", "uninstall", "io.github.hlcaptain.symbols.generatorcontracts"],
                   capture_output=True)
    subprocess.run([adb, "-s", "emulator-5584", "emu", "kill"], capture_output=True)
PY
```

Both theme markers and the final marker must pass: qualifier-selected bytes,
`Res.readBytes`, `Res.getUri`/ContentResolver parity, partial/zero/EOF reads,
malformed-path failures and untouched asset fallback. Inspect the screenshot for
the separate black LIGHT and blue DARK drawings. The CLI's `result.json` remains
explicitly archive-only; device logs/screenshots are separate local evidence.
Candidate05 passes these gates on API23. This does not establish IDE preview,
Hot Reload, split-delivery or official upstream support.

## Full-pack measurements with the actual generator

[GENERATOR_FULL_PACK_RESULTS.json](../GENERATOR_FULL_PACK_RESULTS.json) records
candidate05 using the complete 3,802-icon Outlined pack, the same font/codepoint
inputs, byte-identical XML, and identical legal notices on both sides.
The frozen asset control uses generator04 with pruning disabled; the native arm
uses generator05. The default-off generator output is unchanged, and the full
XML inventories match. Existing successful publications were not overwritten.

| Metric | Asset control | Native candidate05 |
| --- | ---: | ---: |
| One-icon shrunk APK | 5,136,367 B | 958,803 B (81.33% smaller) |
| All-icons shrunk APK | 5,358,987 B | 4,755,264 B (11.27% smaller) |
| 25-icon clean-build median | 33.814 s | 33.039 s |
| 25-icon sampled peak RSS median | 3,692.8 MiB | 2,629.0 MiB |
| Published Android AAR | 4,510,014 B | 8,580,874 B |
| Classes in the published AAR | 46 | 3,948 |

The timing study has two excluded warm-ups and twelve measured builds,
alternating backend order over three repeats with one worker and no build or
configuration cache. Dependencies/transforms remain warm. Build-time ranges
overlap, so this does **not establish a material speedup**. Sampled peak RSS was
28.81% lower at the median; these are summed process samples, not unique system
memory. Producer build time has not been measured under controlled repetition.

The larger unshrunk AAR is a real tradeoff of per-resource holders and native
factories. Initializing each holder field directly removes the redundant inner
`lazy`, reducing the native AAR from 9,539,719 to 8,580,874 bytes (10.05%) while
retaining cached identity. It remains 90.26% larger than the asset-control AAR;
APK savings require ordinary Android shrinking. Do not infer smaller downloads
of the library itself, or equivalent savings when shrinking is disabled.

Direct access retains exactly 0/1/25/200/all resources. Dynamic catalog and raw
lookup preserve all 3,802 owning-pack resources. Every retained native XML file
is checked against the actual published AAR through the APK resource table,
including shortened/shared filenames; owned duplicate assets are rejected.
The three reported asset-control APKs also match all original XML hashes.

### Reproduce the full-pack comparison

Use the same JDK/SDK setup as the parent document and choose a fresh disk-backed
work directory. Nothing below publishes remotely. The Compose artifacts are
explicit local contribution candidates, never production dependencies.

```bash
export JAVA_HOME=/path/to/jdk21
export ANDROID_HOME=/path/to/android-sdk
PACK_WORK=/path/on/disk/symbols-generator-study
PACK_SCRIPTS="$PWD/benchmarks/resource-pipeline/native-raw"
PACK_VERSION=0.0.0-generator-study
PACK_AAPT2="$ANDROID_HOME/build-tools/36.0.0/aapt2"

./tooling/gradlew -p tooling publishToMavenLocal \
  -PVERSION_NAME="$PACK_VERSION" -Dmaven.repo.local="$PACK_WORK/maven" \
  --no-daemon --max-workers=1
python3 "$PACK_SCRIPTS/runtime.py" --upstream-version 1.12.1 \
  --output "$PACK_WORK/runtime" --repository "$PACK_WORK/maven" --gradle ./gradlew
python3 "$PACK_SCRIPTS/upstream-generator/bootstrap.py" --baseline 1.12.1 \
  --output "$PACK_WORK/generator" --repository "$PACK_WORK/maven" --build \
  --candidate-version 1.12.2-native-xml05

for backend in assets native; do
  producer="$PACK_WORK/producer-$backend"
  python3 "$PACK_SCRIPTS/upstream-generator/material.py" --source-root "$PWD" \
    --output "$producer" --repository "$PACK_WORK/maven" \
    --plugin-repository "$PACK_WORK/maven" --symbols-version "$PACK_VERSION" \
    --version "0.0.0-generator-$backend-05" --backend "$backend"
  ./gradlew -p "$producer" publishAllPublicationsToProbeRepository \
    --no-daemon --max-workers=1 --configuration-cache --no-build-cache
  python3 "$PACK_SCRIPTS/upstream-generator/material.py" --source-root "$PWD" \
    --output "$producer" --repository "$PACK_WORK/maven" \
    --plugin-repository "$PACK_WORK/maven" --symbols-version "$PACK_VERSION" \
    --version "0.0.0-generator-$backend-05" --backend "$backend" --collect
  aar=("$producer"/build/outputs/aar/*.aar)
  test "${#aar[@]}" -eq 1
  for access in direct-0 direct-1 direct-25 direct-200 direct-all dynamic-1 raw-1; do
    python3 "$PACK_SCRIPTS/consumer.py" --publisher "$producer" \
      --repository "$PACK_WORK/maven" --output "$PACK_WORK/consumers/$backend-$access" \
      --access "${access%-*}" --count "${access##*-}" --preserve-aar-notices "${aar[0]}"
  done
done
python3 "$PACK_SCRIPTS/run.py" --fixtures "$PACK_WORK/consumers" \
  --repository "$PACK_WORK/maven" --aapt2 "$PACK_AAPT2" --output "$PACK_WORK/retention.json"
python3 "$PACK_SCRIPTS/timing.py" --assets "$PACK_WORK/consumers/assets-direct-25" \
  --native "$PACK_WORK/consumers/native-direct-25" --repository "$PACK_WORK/maven" \
  --aapt2 "$PACK_AAPT2" --output "$PACK_WORK/timing" --repeat 3
```

Run the repeated timing step after other builds/emulators have stopped. Keep
candidate publications immutable within a cohort. The default test suite uses
small fixtures; this full corpus is an explicit local measurement to avoid
inflating routine CI. Raw logs, profiles, APKs, images and generated packs remain
local; only reproduction code and compact results belong in Git.
