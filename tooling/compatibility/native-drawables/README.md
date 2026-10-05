# Native drawable pack verification

This standalone Java Android app explicitly consumes all twelve locally built
Outlined, Rounded, and Sharp AARs directly. It has no Kotlin or Compose runtime dependency and uses
the repository's version catalog and root Gradle wrapper. Nothing is published.

From the repository root, with JDK 21 and the Android SDK configured:

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk

native_tasks=()
for style in outlined rounded sharp; do
  for variant in "" -filled -automirrored -automirrored-filled; do
    module=":modules:material-drawables-$style$variant"
    native_tasks+=("$module:assembleRelease" "$module:generatePomFileForReleasePublication")
  done
done
./gradlew "${native_tasks[@]}"

./gradlew -p tooling/compatibility/native-drawables \
  testDebugUnitTest assembleDebug assembleRelease

python3 tooling/compatibility/native-drawables/verify.py \
  "$ANDROID_HOME/build-tools/36.1.0/aapt2"
```

Use an installed build-tools version for the final command. The first run needs
network access for any uncached Gradle dependencies and Robolectric's Android 15
runtime; subsequent cached runs can use Gradle's `--offline` option.

For AAR compatibility checks, download the public 2.1.0 baselines once:

```sh
native_fixture=tooling/compatibility/native-drawables
mkdir -p "$native_fixture/build/baseline"
for style in outlined rounded sharp; do
  native_artifact="symbols-material-drawables-$style"
  curl --fail --location \
    --output "$native_fixture/build/baseline/$native_artifact-2.1.0.aar" \
    "https://repo.maven.apache.org/maven2/io/github/hlcaptain/$native_artifact/2.1.0/$native_artifact-2.1.0.aar"
done

python3 tooling/compatibility/native-drawables/verify_aars.py
```

The script pins the baseline SHA-256 hashes, requires exactly one family and
3,802 matching `R.txt` names in every candidate AAR, requires dependency-free POMs
and byte-identical existing ordinary XML, and checks mirror flags, unchanged mirrored geometry, differing filled
geometry, and absence of bundled font files. It reads local files only.

For each style, Robolectric's native Android API 35 renderer draws asymmetric
VolumeOff in LTR and RTL for all four variants through an inflated ImageView.
Rounded mirrored-filled VolumeOff is loaded directly from XML `android:src`;
other variants use `ImageView.setImageResource`. The manifest enables
`supportsRtl`, and the test sets `View.LAYOUT_DIRECTION_RTL` on the parent host,
then verifies that both the ImageView and its drawable inherit that direction.
No test calls `Drawable.setLayoutDirection` directly.

Mirroring requires both `android:autoMirrored="true"` on the drawable and an RTL
host view. Tests require ordinary variants to stay fixed, mirrored variants to
reflect horizontally, and filled geometry to differ. Reflection permits two
alpha levels of antialiasing roundoff per pixel. The fixture retains minSdk 21,
but these are API 35 native renderer tests, not API 21/device instrumentation
tests.

Each existing `symbols-material-drawables-{style}` artifact contains ordinary
resources only. Add `-filled`, `-automirrored`, or `-automirrored-filled` explicitly
for that family; none of these artifacts pulls in another pack. Their `R`
packages append `.filled`, `.automirrored`, or `.automirrored.filled` to the
original `io.github.hlcaptain.symbols.material.{style}.drawables` namespace.
Resource names remain `material_symbols_{style}_*`,
`material_symbols_{style}_filled_*`, `material_symbols_automirrored_{style}_*`,
and `material_symbols_automirrored_{style}_filled_*` respectively.

This fixture opts into every pack so the debug APK must contain all 45,624
drawable resource names. The release app
uses full-mode R8 and resource shrinking; it must retain exactly the three
referenced mirrored-filled VolumeOff resources, one per style. No keep rules
are added. Update the expected full-pack count in `verify.py` when the pinned
Material catalog grows.

All generated evidence stays in this fixture's ignored `build/` directory:
JUnit results in `test-results/testDebugUnitTest/`, 24 white-backed PNG previews
in `rendered/`, APK inventories in `debug-resources.txt` and
`release-resources.txt`, and counts/sizes in `shrink-results.json`.

Run the focused native APK size regression after assembling packs and downloading
the pinned baselines:

```sh
python3 tooling/compatibility/native-drawables/check_sizes.py \
  "$ANDROID_HOME/build-tools/36.1.0/aapt2"
```

It compares identical ordinary-only baseline/candidate consumers at minSdk 21
and 26 and requires equal final release APK and resource-table sizes. It also
builds each optional family independently and a mixed-family consumer with one
wholly unused pack. Every debug inventory must match the selected packs; every
release must retain exactly its three referenced resources. Logs, inventories,
and `results.json` stay in `build/size-regression/`.
