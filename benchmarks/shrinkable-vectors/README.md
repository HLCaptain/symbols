# Shrinkable typed-vector Android benchmark

This fixture measures a full `material-vectors-outlined` Android dependency
while consuming exactly one typed vector property:
`Symbols.Material.Outlined.Check`.
`MainActivity.java` calls the static JVM form of that Kotlin extension getter
and passes the resulting vector name to `Activity.setTitle`, making the value
observable to R8.

The `unshrunk` and `shrunk` build types both inherit the same release build
type, use the same source, manifest, dependencies, SDK levels, and compiler
settings, and are unsigned. They differ only as follows:

| Build type | Code minification | Resource shrinking |
| --- | ---: | ---: |
| `unshrunk` | off | off |
| `shrunk` | R8 full mode, optimized defaults | on |

The benchmark ProGuard file only preserves the *names* of surviving
`OutlinedVector*` classes. `-keepnames` permits shrinking; it does not keep an
otherwise-unused vector class.

## Verification

Build and verify both APKs:

```shell
./gradlew \
  :benchmarks:shrinkable-vectors:assembleUnshrunk \
  :benchmarks:shrinkable-vectors:assembleShrunk \
  --no-configuration-cache
python3 benchmarks/shrinkable-vectors/verify.py
```

`verify.py` uses only the Python standard library. It:

- reports APK bytes and SHA-256;
- reports compressed and uncompressed DEX bytes;
- reads DEX header counts for strings, types, fields, methods, and class
  definitions, including the exact 32-byte-per-entry class-definition table
  size;
- reports ZIP sizes for `resources.arsc`, `res/`, and native libraries;
- confirms the Check backing class `OutlinedVectorE5CA` is in both APKs;
- confirms the unrelated Home backing class `OutlinedVectorE9B2` is in the
  unshrunk APK but absent from every shrunk DEX;
- corroborates that result with R8's mapping and usage reports; and
- confirms the fixture's unused resource marker is removed and reported
  unreachable by the resource shrinker.

An analysis exits nonzero unless every retention/removal assertion holds.

## Timing and memory method

Run:

```shell
python3 benchmarks/shrinkable-vectors/measure.py
```

The script builds both variants in each measurement:

- **Cold full build:** existing daemons stopped, fresh single-use daemon,
  configuration cache and build cache disabled, and every task rerun.
- **Output-warm no-change build:** the preceding outputs/caches remain, but a
  fresh single-use daemon is used and configuration cache remains disabled.

Both commands use `--no-daemon` so the single-use Gradle daemon remains in the
measured launcher's process tree. Every 100 ms the script sums resident-set
size reported by `ps` for the launcher and all descendants, then reports the
largest sample. This is an aggregate RSS measurement: shared pages can be
counted in more than one process. It is neither JVM heap usage nor a
process-unique working-set measurement.

The cold run deliberately retains downloaded dependency and Gradle transform
caches; it is a cold daemon/full task rerun, not a freshly provisioned machine
or empty Gradle user home.

The timing script currently depends on the macOS/BSD `ps -axo` interface.
Absolute times and RSS vary with host load, filesystem state, dependency
caches, JDK, and hardware. The paired build definition, commands, and DEX/APK
measurements are the reproducible result; the numbers below are one recorded
run, not universal performance guarantees.

## Recorded result: 2026-07-29

The worktree was based on Git revision
`b579cd59ab0b7691f4135338861f7e40492a5870` and contained the feature changes
under test. Host: Mac mini, Apple M4 (10 cores), 16 GB RAM, macOS 26.5.2
arm64. Toolchain: Gradle 8.14.3, Android Gradle Plugin 8.13.0, compile SDK 36,
Temurin JDK 17.0.19+10, and Python 3.9.6.

| APK metric | Unshrunk | Shrunk | Shrunk − unshrunk |
| --- | ---: | ---: | ---: |
| APK bytes | 7,100,067 | 143,028 | -6,957,039 (-97.9855%) |
| DEX files | 2 | 1 | -1 |
| DEX compressed bytes in APK | 6,871,206 | 65,005 | -6,806,201 |
| DEX uncompressed bytes | 24,478,384 | 134,712 | -24,343,672 |
| DEX class definitions | 19,277 | 176 | -19,101 |
| Class-definition table bytes | 616,864 | 5,632 | -611,232 |
| DEX field IDs | 29,103 | 406 | -28,697 |
| DEX method IDs | 128,970 | 1,128 | -127,842 |
| Android resource bytes, uncompressed | 157,599 | 7,056 | -150,543 |
| Android resource bytes, compressed | 147,277 | 7,056 | -140,221 |
| Native-library bytes, uncompressed | 37,392 | 37,392 | 0 |

| Build measurement | Wall time | Peak sampled summed descendant RSS |
| --- | ---: | ---: |
| Cold full build | 55.845 s | 6,354,878,464 bytes |
| Output-warm no-change build | 11.915 s | 1,458,520,064 bytes |

The verifier passed: Check was retained, Home was removed, R8 reported Home as
unused, and the resource shrinker removed the unreachable marker. The APK
SHA-256 values were
`9f966af5b4d79772923b78afae95cbc488fed346e60c3839adf48ea235d944a9`
(unshrunk) and
`5b6318b426a936ef2b20a91bd427547fa7f94f525b6cb670dca3a13736e09e5c`
(shrunk).

These APKs include the Compose vector runtime and other transitive Android
dependencies. The measured delta demonstrates that this fixture can discard
the unused generated icon backing classes; it does not mean every consuming
application will save the same percentage or that the remaining APK bytes are
the cost of one icon alone. Build timings cover the complete two-variant task
graph, not runtime icon construction.
