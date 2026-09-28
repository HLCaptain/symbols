# Published plugin compatibility

The tooling wrapper stays on Gradle 8.14.5 so the published plugin can run on
both Gradle 8 and 9. The root build uses the current application toolchain.
Always build the release tooling artifact with `tooling/gradlew -p tooling`.

Publish a disposable version to a private local Maven directory first:

```shell
./tooling/gradlew -p tooling publishToMavenLocal \
  -PVERSION_NAME=0.0.0-upgrade-test -Dmaven.repo.local=/tmp/symbols-upgrade-maven
./gradlew :modules:symbols-core:publishToMavenLocal \
  :modules:material-drawables-outlined:publishToMavenLocal \
  :modules:variant-font-core:publishToMavenLocal \
  -PVERSION_NAME=0.0.0-upgrade-test -Dmaven.repo.local=/tmp/symbols-upgrade-maven
```

`check.py` creates independent Kotlin DSL consumers under `build/reports`,
resolves the plugin marker and runtime from that Maven directory, compiles the
generated vector getter and Android resource reference, and requires a second
build to reuse the configuration cache. It uses no composite build, injected
TestKit classpath, generator classpath override, or snapshot of generated code.

Run each of `android-app`, `android-java`, `android-library`, `kmp`, and `jvm`
with the current catalog versions. `android-java` consumes native XML without
Kotlin or Compose dependencies, including the published Outlined drawable pack.
Both app profiles build an R8-minified release and verify that used drawables
survive while unused drawables are removed. The `kmp` case publishes its generated
library to another local Maven repository and compiles a separate Android app
against that AAR, exercising the complete transitive resource boundary:

```shell
python3 tooling/compatibility/check.py --profile kmp --gradle ./gradlew \
  --repository /tmp/symbols-upgrade-maven --version 0.0.0-upgrade-test
python3 tooling/compatibility/check.py --profile kmp --compose-resources --gradle ./gradlew \
  --repository /tmp/symbols-upgrade-maven --version 0.0.0-upgrade-test
```

The second KMP mode generates only Compose resources, with no native drawable
request or manual Android resource opt-in. It adds a font through a task-backed
resource root, compiles the generated `Res.symbolFonts` descriptor, and checks
that the exact font bytes and drawable XML survive both AAR publication and
downstream APK packaging. Both the library and consuming app must reuse the
configuration cache.

The hosted `published-consumers` job also checks the advertised AGP 9.1 minimum
with the same published candidate, root Gradle 9.7 wrapper, compile SDK 37, and
current Kotlin/Compose catalog versions:

| Toolchain | Consumer cases |
| --- | --- |
| Current catalog / root wrapper | Android app, Java/XML app, Android library, KMP, KMP Compose resources, JVM |
| AGP 9.1.0 / Gradle 9.7.0 | Android Compose app |
| AGP 9.1.0 / Gradle 9.7.0 | KMP Compose-resource publisher and separate consuming Android app |
| AGP 8.13.2 / Gradle 8.14.5 | Java/XML app |

All nine cases passed on September 28 at `9e4f43e`, including configuration-cache
reuse. [Hosted verification](https://github.com/HLCaptain/symbols/actions/runs/36418737452/job/108916523301).

Reproduce the two minimum-version cases without overwriting the latest-toolchain
or legacy results:

```shell
python3 tooling/compatibility/check.py --profile android-app --gradle ./gradlew \
  --repository /tmp/symbols-upgrade-maven --version 0.0.0-upgrade-test \
  --agp-version 9.1.0 --compile-sdk 37 \
  --output build/reports/tooling-compatibility-agp91
python3 tooling/compatibility/check.py --profile kmp --compose-resources --gradle ./gradlew \
  --repository /tmp/symbols-upgrade-maven --version 0.0.0-upgrade-test \
  --agp-version 9.1.0 --compile-sdk 37 \
  --output build/reports/tooling-compatibility-agp91
```

The harness gives these cases distinct `android-app` and `kmp-compose` directories.
Both require configuration-cache reuse; the app checks release shrinking, while
the KMP case checks generated font/drawable bytes through publication and APK
packaging. AGP 9.1.0's [published API](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle-api/9.1.0/gradle-api-9.1.0-sources.jar)
includes the native KMP components extension, resource opt-in and generated-source
directory APIs used by the plugin. The hosted cases exercise those APIs through
actual compilation, publication and downstream packaging.

Test the same plugin artifact with the legacy supported Java/XML toolchain
(Gradle 8.14.5, AGP 8.13.2, API 21). Current Compose artifacts require AGP 9.1+
and API 23, so legacy KMP/Compose consumers are not part of this contract:

```shell
python3 tooling/compatibility/check.py --profile android-java \
  --gradle ./tooling/gradlew --repository /tmp/symbols-upgrade-maven \
  --version 0.0.0-upgrade-test --agp-version 8.13.2 \
  --compile-sdk 36 --output build/reports/tooling-compatibility-legacy
```

Modern Kotlin/Compose consumers use current catalog versions, compile SDK 37,
and API 23. The Java/XML fixture has no Kotlin or Compose runtime dependency.
Use separate `--output` directories when comparing toolchains. Set `JAVA_HOME`
to JDK 21 and `ANDROID_HOME` to the SDK installation before running. The script
keeps logs, timings, and the exact command locally; do not upload its generated
build artifacts to Actions storage. These tiny fixtures test compatibility,
not full-catalog performance or shrinker effectiveness.
