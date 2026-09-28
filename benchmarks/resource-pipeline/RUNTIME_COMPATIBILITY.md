# API 21 and API 23 runtime smoke checks

All five smoke cases passed on 2026-09-28. These checks verify installation,
launch, visible rendering, and absence of app crash/resource/linkage exceptions;
they are not frame-time benchmarks or complete visual-equivalence tests.

| Android image | Shrunk test APK | Observed rendering |
| --- | --- | --- |
| API 21 / Android 5.0.2 | Java/XML consumer built with Gradle 8.14.5 and AGP 8.13.2 | Generated native SVG and published Material Home drawable |
| API 23 / Android 6.0 | Native XML, direct one-icon consumer | Expected 10K icon |
| API 23 / Android 6.0 | Typed ImageVector, direct one-icon consumer | Expected 10K icon |
| API 23 / Android 6.0 | Compose resources, direct one-icon consumer | Expected 10K icon loaded from packaged resources |
| API 23 / Android 6.0 | Static Material sample profile | Font-rendered Home, Search, Favorite, and Compose-resource Home |

The API 23 one-icon screens contained 6,299–6,300 dark pixels in the inspected
icon rectangle. All four static-sample icon rectangles contained visible
foreground pixels and were visually reviewed. Captured logcat output contained
no `FATAL EXCEPTION`, missing-resource, class-loading, method-linkage, native-link,
or inflation exceptions for these runs. Foreground activity state was checked
alongside screenshots.

The API 21 consumer was copied into a disposable directory, its existing test
activity was exported, and the shrunk release was debug-signed. Its code and
resource inputs were otherwise unchanged. The static-profile APK was signed as
a separate copy. Frozen benchmark APKs and the physical Pixel were not modified.

## Artifact provenance

- The three API 23 one-icon consumers use B libraries published from
  `8fb5fce52537d300bf1127fdc8361f9b15c0d620` as `0.0.0-study-b`. Recreate them with
  the [published-artifact fixture recipe](README.md#independent-usage-count-fixtures),
  selecting each backend with `--access direct --count 1`, then build `assembleShrunk`.
- The static-font APK comes from `3aea238f2b6877fef005e26d8547cd7fcebfc129`:
  use the [sample APK recipe](../sample-app/README.md) with
  `--profile material-static --variant shrunk`, then sign a copy.
- The API 21 app uses the [legacy Java/XML consumer recipe](../../tooling/compatibility/README.md)
  and local plugin/library version `0.0.0-upgrade-test`. The plugin's verified
  SHA-256 is `9d810b1c9c2b709247a8752f6a4807ac7cdcdf3a576ca7d81108a8108e85111f`;
  its compiled main sources match `3aea238`. Copy the generated fixture, export
  its existing test activity, set release signing to the debug signing config,
  and run `assembleRelease` with the tooling Gradle 8.14.5 wrapper.

The JSON records original/signed APK hashes where applicable, exact library
coordinates and Android archive hashes, and the fixture selections.

## Reproduce

Use the official default x86_64 images: API 21 revision 5 and API 23 revision 10.
The recorded runs used emulator 37.2.5.0 with KVM and SwiftShader, a Nexus 5
profile (1080×1920 at 480 dpi), 1,536 MB RAM, two virtual CPU cores, and a 1 GB
userdata partition. Run one emulator at a time and stop it before controlled
build measurements.

```bash
android sdk install system-images/android-21/default/x86_64@5.0.0
android sdk install system-images/android-23/default/x86_64@10.0.0
android sdk install cmdline-tools/latest
export ANDROID_AVD_HOME=/tmp/symbols-runtime-avds
avdmanager create avd --name symbols_api23_smoke \
  --path /tmp/symbols-runtime-avds/symbols_api23_smoke.avd \
  --package 'system-images;android-23;default;x86_64' --device 'Nexus 5'
```

Set the temporary AVD's `config.ini` to the memory/core/userdata values above.
For API 21, substitute its name and package. The CLI's AVD browser did not honor
the temporary AVD directory, so the SDK emulator was started directly:

```bash
emulator -avd symbols_api23_smoke -port 5580 -no-window -no-snapshot \
  -no-audio -no-boot-anim -gpu swiftshader -accel on
adb -s emulator-5580 shell getprop ro.build.version.sdk
android run --device=emulator-5580 --apks=/path/to/signed.apk \
  --activity=study.MainActivity
android layout --device=emulator-5580 --pretty -o /tmp/symbols-layout.json
android screen capture --device=emulator-5580 -o /tmp/symbols-screen.png
adb -s emulator-5580 logcat -d -v brief > /tmp/symbols-logcat.txt
adb -s emulator-5580 emu kill
```

Use `example.ConsumerActivity` for the legacy consumer and
`io.github.hlcaptain.symbols.sample.MainActivity` for the static sample. Open the
Static Material Symbols screen in the latter. Sign test copies with v1 signing
enabled for these Android versions. Android CLI's layout instrumentation helper
could not install on API 21; that case used foreground activity state and direct
screen capture instead.

Both temporary emulators were stopped and their AVD data removed. Installed SDK
images remain available for future checks. [Compact evidence](runtime-compatibility.json)
records image fingerprints, APK hashes, rendering observations, and error checks;
raw screenshots and logs stay local. The older-API checks cover these selected
icons and the static-font path, not every glyph, variable-font animation, or
device configuration.
