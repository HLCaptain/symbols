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

## Built-in vector consumers

`--built-in-vectors` additionally resolves the published Rounded and Themed packs.
The consumer uses ordinary `ImageVector` values from
`Symbols.Material.AutoMirrored.Rounded.ArrowBack` and
`Symbols.Material.AutoMirrored.Themed.ArrowBack`. The JVM case runs the existing
public themed-vector tests against those artifacts, checking mirror flags, cached
identity, changing styles, and nested theme inheritance. The Android app references
both paths from its activity so R8 must retain the used code.

The regular seven-case plugin matrix remains unchanged. The JVM/Android CI job
publishes only Kotlin metadata, JVM, and Android variants after compiling the
libraries; the Apple job publishes only Kotlin metadata and the two iOS variants.
Both use the eight modules `symbols-core`, `variant-font-core`, `material-core`,
`material-compose`, and the four `material-vectors-*` packs. This reuses each job's
compiled outputs and avoids adding web/native publication to the 45-minute plugin
matrix. Candidate artifacts remain in the workspace's ignored `build/` directory.

After publishing those candidates, run:

```shell
python3 tooling/compatibility/check.py --profile jvm --built-in-vectors \
  --gradle ./gradlew --repository build/vector-consumer-maven \
  --version 0.0.0-consumer-check --output build/reports/builtin-compatibility
JAVA_HOME=/path/to/jdk-17 python3 tooling/compatibility/check.py --profile android-app --built-in-vectors \
  --gradle /path/to/gradle-9.3.1/bin/gradle --agp-version 9.1.1 --compile-sdk 37 \
  --repository build/vector-consumer-maven --version 0.0.0-consumer-check \
  --output build/reports/builtin-compatibility-minimum
```

The minimum Android case retains API 23 and the existing resource-shrinking
assertions. It qualifies the vendor-supported AGP 9.1.1 / Gradle 9.3.1 boundary
separately from the repository's newer producer toolchain. Dependency metadata
permits AGP 9.1.0, but that version warns that SDK 37 exceeds its tested SDK 36.1
maximum. CI runs this consumer on JDK 17 while building the candidate libraries
and plugin on JDK 21.

On macOS with the repository's Xcode version:

```shell
python3 tooling/compatibility/check.py --profile ios --gradle ./gradlew \
  --repository build/apple-consumer-maven --version 0.0.0-consumer-check \
  --output build/reports/builtin-compatibility
```

The iOS case consumes compiled libraries without applying the Symbols generator
plugin. Its exported `String` function keeps the fixed and themed vector calls
reachable when linking device and simulator frameworks. `xcrun vtool` must report
the Kotlin 2.4 default minimum of iOS 15.0 for both binaries; no deployment-target
override is used. This verifies native linking and deployment metadata, not
execution on an iOS 15 device. Every case still requires configuration-cache reuse.
