# Native drawable pack verification

This standalone Java Android app consumes the locally built Outlined, Rounded,
and Sharp AARs directly. It has no Kotlin or Compose runtime dependency and uses
the repository's version catalog and root Gradle wrapper. Nothing is published.

From the repository root, with JDK 21 and the Android SDK configured:

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk

./gradlew :modules:material-drawables-outlined:assembleRelease \
  :modules:material-drawables-rounded:assembleRelease \
  :modules:material-drawables-sharp:assembleRelease

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

The script pins the baseline SHA-256 hashes, verifies all four families and
`R.txt` names in every candidate AAR, requires byte-identical existing ordinary
XML, and checks mirror flags, unchanged mirrored geometry, differing filled
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

The debug APK must contain all 45,624 drawable resource names. The release app
uses full-mode R8 and resource shrinking; it must retain exactly the three
referenced mirrored-filled VolumeOff resources, one per style. No keep rules
are added. Update the expected full-pack count in `verify.py` when the pinned
Material catalog grows.

All generated evidence stays in this fixture's ignored `build/` directory:
JUnit results in `test-results/testDebugUnitTest/`, 24 white-backed PNG previews
in `rendered/`, APK inventories in `debug-resources.txt` and
`release-resources.txt`, and counts/sizes in `shrink-results.json`.
