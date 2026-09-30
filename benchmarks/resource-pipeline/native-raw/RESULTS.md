# Native Compose XML experiment — results

The bounded prototype makes unused XML drawables shrinkable without changing
ordinary `Res.drawable` / `painterResource` calls or requiring an app-side
Symbols/Compose resources plugin. With one icon from the complete outlined pack,
the shrunk APK falls from **5,136,367 to 929,296 bytes (−81.9%)**, retaining exactly
one of 3,802 original XML files. Zero direct references retain zero files.

This is **an upstream generator model plus a real local Compose reader patch**,
not a production Symbols backend or an existing AGP/Gradle flag. The production
pipeline is unchanged. Preview/custom-reader compatibility and supported
upstream integration still gate adoption. Fonts are not subsetted.

## Full-pack APK comparison

Both controls start from the same original published AAR/sources, keep all 3,802
resources in their publications, and use the same app source, compiler, R8 and
resource-shrinking settings. Each app resolves independent Maven publications;
there is no project substitution, custom keep rule or application initializer.
Sizes below are signed APK bytes, not download estimates or resource-only bytes.

| Access / selected icons | Asset control bytes | Native candidate bytes | APK change | Native XML retained |
| --- | ---: | ---: | ---: | ---: |
| direct-0 | 5,087,184 | 879,148 | -82.7% | 0 |
| direct-1 | 5,136,367 | 929,296 | -81.9% | 1 |
| direct-25 | 5,136,576 | 950,679 | -81.5% | 25 |
| direct-200 | 5,138,521 | 1,099,012 | -78.6% | 200 |
| direct-all | 5,358,987 | 4,513,978 | -15.8% | 3,802 |
| dynamic-1 | 5,365,655 | 4,504,356 | -16.1% | 3,802 |
| raw-1 | 5,136,386 | 4,422,442 | -13.9% | 3,802 |

The asset control retains all 3,802 XMLs in every case. Native dynamic catalog
lookup and runtime raw-path access deliberately keep the owning pack: their
smaller APKs do **not** imply per-icon pruning. Native packaging also avoids long
Compose asset entry paths; the native resource table still has its own cost.

The native Android AAR is 4,123,976 bytes versus the rebuilt asset control's
4,508,249 bytes. The JVM JAR grows from 4,481,097 to 4,611,767 bytes (+2.9%) because
this model adds platform-location helpers while retaining every JVM asset.
These publication sizes are separate from APK savings and consumer build time.

## Comparison with native Android resources

A fresh September 29 comparison uses the **same Android Compose screen** with
native `androidx.compose.ui.res.painterResource(R.drawable...)`. This isolates
resource backends; it is not an Android Views-versus-Compose app-size comparison.
Both builds enable code/resource shrinking on AGP 9.4.1, Kotlin 2.4.20 and
Compose 1.12.1, using the same 3,802 source XMLs, SDK, wrapper and selections.

| Access / selected icons | Native Android `R.drawable`, bytes | CMP prototype, bytes | Prototype vs native Android | Icons retained by both |
| --- | ---: | ---: | ---: | ---: |
| direct-0 | 879,027 | 879,148 | +0.01% | 0 |
| direct-1 | 913,328 | 929,296 | +1.75% | 1 |
| direct-25 | 936,609 | 950,679 | +1.50% | 25 |
| direct-200 | 1,114,903 | 1,099,012 | −1.43% | 200 |
| direct-all | 4,697,983 | 4,513,978 | −3.92% | 3,802 |
| dynamic-1 | 4,704,742 | 4,504,356 | −4.26% | 3,802 |

**The pruning matches native Android.** The previously reported 81.9% saving is
relative to the CMP asset control, which retains the entire pack. It is not an
81.9% improvement over native Android's already-shrunk output. Native resources
use AAPT2-compiled drawable XML; the CMP prototype keeps original text XML in
`res/raw` for its existing byte/URI/qualifier APIs. Decoder/runtime code and
resource encoding therefore differ, even with identical retained icon counts.

The table preserves both packs' original license/notice contents in the APK.
This matters because the prototype's rebuilt libraries carry them through Java
resources, while the stock native AAR stores them at its root. Without this
normalization, the stock native single-icon APK is 903,502 bytes. The matched
control is 913,328 bytes; notice handling must not be attributed to renderer or
shrinker overhead. No extra CMP runtime dependency or keep rule was added to the
native control. The original and matched values, artifact hashes, exact counts
and byte checks are in [NATIVE_ANDROID_COMPARISON.json](NATIVE_ANDROID_COMPARISON.json).

For Compose Multiplatform, this result applies to **Android output using the
prototype's generator/runtime integration**. Its common `Res` API and published
KMP intermediary are already covered by the compatibility checks. Stock CMP
1.12.1 continues to package these drawable XMLs as assets, so production behavior
is unchanged. R8 does not provide the same resource pruning on iOS, desktop or
web, and this study does not subset font glyphs. Dynamic catalogs keep every
entry reachable on both backends.

Native shrinking is the normal
[Android code/resource optimization pipeline](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).
The extra benchmark option below only preserves legal files for the comparison;
it is not a consuming-app integration requirement.

To reproduce, use the original native AAR and matching Maven version alongside
the already measured CMP controls. Keep generated fixtures outside the checkout:

```bash
NATIVE_ANDROID_REPO=/path/to/stock-maven
NATIVE_ANDROID_VERSION=0.0.0-study-b
NATIVE_ANDROID_AAR="$NATIVE_ANDROID_REPO/io/github/hlcaptain/symbols-material-drawables-outlined/$NATIVE_ANDROID_VERSION/symbols-material-drawables-outlined-$NATIVE_ANDROID_VERSION.aar"
NATIVE_ANDROID_CASES=/path/to/fresh/native-android-controls
python3 benchmarks/resource-pipeline/fixtures.py \
  --repository "$NATIVE_ANDROID_REPO" --version "$NATIVE_ANDROID_VERSION" \
  --output "$NATIVE_ANDROID_CASES" --backend android --access direct \
  --preserve-aar-notices "$NATIVE_ANDROID_AAR"
python3 benchmarks/resource-pipeline/fixtures.py \
  --repository "$NATIVE_ANDROID_REPO" --version "$NATIVE_ANDROID_VERSION" \
  --output "$NATIVE_ANDROID_CASES" --backend android --access dynamic --count 1 \
  --preserve-aar-notices "$NATIVE_ANDROID_AAR"
for native_android_case in "$NATIVE_ANDROID_CASES"/*; do
  "$native_android_case/gradlew" -p "$native_android_case" assembleShrunk \
    --no-daemon --no-build-cache --no-configuration-cache --max-workers=1 \
    -Pkotlin.compiler.execution.strategy=in-process
  python3 benchmarks/resource-pipeline/retention.py --fixture "$native_android_case" \
    --apk "$native_android_case/build/outputs/apk/shrunk/"*.apk \
    --aapt2 "$ANDROID_HOME/build-tools/36.1.0/aapt2"
done
```

The six original controls passed release/shrunk checks, and all six normalized
controls passed exact shrink counts and notice-byte checks. This addition is a
size/retention comparison; no new runtime-speed or build-time claim is made.

## Consumer build measurements

The 25-icon consumer was measured with three alternating repetitions per backend,
after one excluded warm-up each: **12 measured builds plus two warm-ups**. The
owned emulator and other benchmark builds were stopped. Both backends used a
fresh single-use daemon, one worker, a 4 GiB heap and in-process Kotlin; build and
configuration caches were disabled, with downloaded dependencies/transforms warm.
Producer compilation is outside this comparison.

| Measurement | Asset median [min–max] | Native median [min–max] | Median change |
| --- | ---: | ---: | ---: |
| Clean build, seconds | 27.665 [27.615–28.051] | 27.291 [27.139–27.998] | −1.4% |
| No-change build, seconds | 5.054 [4.862–5.067] | 4.973 [4.818–5.077] | −1.6% |
| Clean peak RSS, MiB | 3,246.8 [3,222.0–3,268.4] | 2,757.6 [2,469.8–2,951.6] | −15.1% |
| No-change peak RSS, MiB | 894.5 [878.9–975.9] | 889.5 [863.5–912.6] | −0.6% |

The wall-time ranges overlap: **no material build-speed improvement is claimed**.
Sampled peak RSS is the summed process-tree maximum at 100 ms intervals, not Java
heap or unique resident memory. These are three repetitions on one Linux host.
Every sample verifies retention; APK sizes and retained XML bytes remain stable.
Whole signed-APK hashes vary across clean builds. One additional **unmeasured**
rebuild isolated the difference to encrypted SDK dependency metadata in the APK
signing block: all 105 ZIP-entry contents, metadata/order and signing files were
identical. The v2 signing block was also unchanged. See
[APK_REPRODUCIBILITY.json](APK_REPRODUCIBILITY.json); the ordinary dependency-info
setting remains enabled. This is not a claim of bit-identical whole APK output.

Gradle task profiles explain why the size reduction does not become a comparable
build-time reduction:

| Clean task, median seconds | Assets | Native |
| --- | ---: | ---: |
| R8 minification/resource optimization | 11.816 | 12.127 |
| Kotlin compilation | 3.286 | 3.332 |
| Merge assets | 0.359 | 0.073 |
| Compress assets | 0.764 | 0.009 |
| Process Android resources | 0.145 | 0.508 |
| Package APK | 0.259 | 0.122 |

Asset handling and packaging become cheaper, while native resource processing
and the combined R8 task cost more. The large Kotlin/R8/lint workload remains.
Task profiles do not isolate R8's internal code/resource phases. Functional-matrix
durations are excluded because some checks ran concurrently.

## Correctness and compatibility

- **14 full-pack usage cases** pass exact retention and configuration-cache reuse.
  Direct selections cover 0, 1, 25, 200 and all 3,802 icons; dynamic/raw cases
  select one at runtime and retain the full pack. Four further consumers cover
  AGP 9.1, a published KMP intermediary, a precompiled caller and reader contracts.
- **Source → publication → APK bytes:** all 3,802 XMLs (5,291,056 bytes) in native
  Android/JVM and asset-control Android/JVM publications exactly match the
  original stock AAR. Retained APK XMLs match their published AAR. Native Android
  contains no duplicate owned Compose assets; original license notices survive.
- **JVM ABI:** every original public/protected member and class header is retained
  across the pack's 46 classes / 3,860 members and runtime's 117 classes / 460
  members. Kotlin module names survive. One internal-helper JVM facade is added.
  `abi.py` also rejected an earlier faulty prototype missing six `$stable` fields
  and the original module name. This is not a claim of byte-identical metadata.
- **Dependency-only consumption:** a Java helper compiled against the actual
  stock AAR/runtime calls the unchanged original getter in the candidate. A
  separately published KMP bridge works in Android and a JVM executable; the
  latter verifies unchanged asset bytes and readable resource URIs. Neither
  consuming app applies a resource backend plugin.
- **Android versions:** AGP 9.4.1 and the accepted Compose floor, AGP 9.1.0, both
  build and reuse configuration caches. API 23 installs and runs the native,
  transitive and binary-consumer apps. Existing XML-only AGP 8.13 support is
  untouched; this prototype does not lower Compose's AGP/API floor.
- **Reader/qualifier contracts:** independent light/dark physical XML IDs select
  correctly through Compose's resource environment. Raw bytes, ContentResolver
  URIs, partial/empty/EOF reads, malformed/missing IDs and unrelated asset fallback
  pass. A test-only observer can construct the descriptor before provider/context
  initialization; it does not initialize the library.
- **AAB delivery:** the unshrunk control retains all 3,802 XMLs. The shrunk AAB is
  2,229,165 bytes including bundle metadata; its API-23 device APK set installs,
  and `base-master.apk` retains one byte-identical XML. Installed splits render
  the same icon as the asset control. AAB and standalone APK sizes are not directly
  comparable; arbitrary qualified splits/dynamic features remain outside this gate.
- **Rendering:** API-23 checks cover direct, dynamic, raw, precompiled, transitive,
  minimum-AGP and installed-bundle paths, plus the full pack's first/last pages.
  The light/dark contract renders black/blue variants correctly. This is not a
  new frame-time benchmark or a claim that every icon was visually inspected.
- **Fast checks:** seven native-prototype Python checks plus eleven resource-pipeline
  checks pass. CI runs the small helpers on normal PR verification, without
  adding this local prototype build matrix to publishing jobs.

## Scope and evidence

Accepted library cohorts are `0.0.0-assets-full07` and `0.0.0-native-full07`;
`1.12.2-native-raw01` is a **local-only patched runtime coordinate**, not an
upstream release. Small compatibility checks use `0.0.0-native-small04` and a
separate contract pack. [RESULTS.json](RESULTS.json) records input/artifact hashes,
retention and repeated timing evidence; [COMPATIBILITY.json](COMPATIBILITY.json)
records the ABI and runtime gates. Raw logs, APKs, AABs, traces,
HTML profiles and screenshots stay local. No GitHub artifact storage is required.

Earlier parser/build-fixture failures and the ABI-invalid full03 cohort were
excluded before acceptance. They are not size/performance samples. Fresh versions
were used after producer changes; no accepted publication was overwritten.

The native runtime patch is real; `publisher.py` models upstream-generated source
output rather than replacing the Compose Gradle plugin itself. Android/JVM are
implemented in this model. Apple/web packaging, IDE previews, custom resource
readers, arbitrary resource-name collisions and other resource types remain
upstream adoption gates. A hardcoded AssetManager path or assumed asset-URI
scheme cannot be preserved when bytes move to native resources. Keep these
boundaries explicit before selecting a production default.

See [reproduction and upstream integration](README.md). Ordinary callers keep the
existing API; production rollout waits for supported generator/runtime integration.
