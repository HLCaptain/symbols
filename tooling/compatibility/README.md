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
```

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
