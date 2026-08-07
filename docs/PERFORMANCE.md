# Performance and size

Symbols exposes three intentionally different cost models:

1. a runtime variable font for many icons or live axes;
2. a runtime regular font for many default-axis icons, including Android API
   21–25; and
3. build-time or built-in vectors/drawables when independent icon reachability
   or a vector-specific API matters.

No mode is universally smallest. Font compression, code shrinking, resource
shrinking, target packaging, icon count, and keep rules determine the release
result.

## Checked-in input sizes

Each font artifact carries exactly one TTF:

| Style | Variable TTF bytes | Default-axis regular TTF bytes |
| --- | ---: | ---: |
| Outlined | 10,178,540 | 1,303,612 |
| Rounded | 14,586,584 | 1,700,344 |
| Sharp | 8,434,940 | 1,149,652 |

The variable files are unmodified upstream inputs. The regular files are
deterministic derivatives at `FILL=0, GRAD=0, opsz=24, wght=400`. These are
repository file sizes, not promises about final APK, IPA, desktop, or web
downloads. Compression and platform packaging can change both the absolute
size and the relative result.

The catalog is generated ahead of time. A named property returns an inline
integer handle. The name and codepoint arrays initialize lazily only when their
data is requested; direct property access alone does not build a runtime map.
`fromName` and `aliases` use binary search over generated sorted arrays.

## Runtime rendering

For one runtime-font icon, use the generic renderer:

```kotlin
SymbolFontIcon(
    codePoint = MaterialSymbols.Home.codePoint,
    font = MaterialSymbolsRounded,
    contentDescription = null,
)
```

For a large collection rendered from one runtime font, share a family:

```kotlin
import io.github.hlcaptain.symbols.font.rememberSymbolFontFamily

val axes = MaterialSymbolAxes(weight = 500)
val family = rememberSymbolFontFamily(
    font = MaterialSymbolsRounded,
    fontSettings = axes.fontSettings,
)

symbols.forEach { symbol ->
    SymbolFontIcon(
        codePoint = symbol.codePoint,
        fontFamily = family,
        contentDescription = null,
    )
}
```

The reusable family and capability APIs live in
`io.github.hlcaptain.symbols.font`. `SymbolsRuntime.variableFontsSupported`
reports whether the current platform can apply variable-font settings; on
Android API 21–25, use a `SymbolFont.Regular` or generated vector/drawable
instead. Material's `axes.fontSettings` adapts its four axes to the generic
`SymbolFontSettings` contract. `MaterialSymbolsTheme` supplies those generic
settings automatically inside its content.

Keep the `(font, axes.fontSettings)` pair stable across recompositions. A
regular font has one fixed settings point and never constructs variation
settings. Searching `MaterialSymbols.all` is suitable for an icon picker; a hot
application path should retain its filtered result rather than scanning all
names on every frame.

On Android, repeated instances of a large compressed variable TTF can require a
separate inflated heap buffer. Apps that hit allocation failures while changing
axes can let `Typeface.Builder` memory-map the asset instead:

```kotlin
android {
    androidResources {
        noCompress += "ttf"
    }
}
```

This trades a larger APK download for lower font-instantiation heap pressure;
the sample enables it because its Rounded variable font is 14.6 MB.

A vector builder runs on first property access and caches the resulting
`ImageVector`. Codepoint aliases share that builder and cache. This trades
first-access work and generated code for independent reachability; measure both
cold and warm access.

## Shrinkability boundaries

The build-time plugin generates every codepoint by default. Call `include(...)`
when generation time, compiler memory, or unshrunk artifact size matters:
unselected glyphs produce no Kotlin method and no XML file.

Generated `ImageVector` properties call independent per-codepoint builders.
They emit direct path operations and contain no registry, path table, reflection
hook, dispatcher, or all-icons collection. The built-in direct properties, such
as `Icons.Outlined.Home`, likewise bypass their pack-wide dynamic dispatcher.
This structure allows a full-mode code shrinker to analyze unused icon classes
as unreachable, subject to the consuming application's keep rules.

The compatibility APIs
`MaterialSymbol.asOutlinedImageVector()`,
`asRoundedImageVector()`, and `asSharpImageVector()` intentionally support
runtime-selected catalog values. They therefore reference a codepoint index and
dispatcher and can retain substantially more of a built-in pack. Prefer typed
properties when the selected icon is known at compile time.

Native Android generation emits one `res/drawable` XML file per unique
codepoint. Those files can participate in Android resource shrinking when a
release build enables both code minification and `shrinkResources`. Dynamic
resource lookup and keep files can retain additional resources. Compose
Multiplatform resource packaging differs by target; do not generalize Android
resource-shrinker behavior to every Compose output.

Runtime font files are indivisible resources from the application's point of
view. R8 does not remove glyphs from a bundled TTF. If a project needs only a
small fixed set, generate those outlines instead of expecting a code shrinker to
subset a font.

## Evidence boundary

Repository tests establish structural properties: selected generation, direct
builder calls, alias cache sharing, absence of a generated global registry,
one-resource-per-codepoint output, and deterministic regeneration. The exact
font byte counts above are verified from checked-in files.

### Android R8/resource-shrinker fixture

[`benchmarks/shrinkable-vectors`](../benchmarks/shrinkable-vectors/README.md)
is a minimal Android application that references exactly the typed
`Icons.Outlined.Check` getter. Its `unshrunk` build disables minification and
resource shrinking; its otherwise equivalent `shrunk` build enables full-mode
R8 and `shrinkResources`. A `-keepnames` rule preserves the original names of
surviving vector backing classes without keeping unreachable classes.

The fixture produced these local release artifacts on 2026-07-29:

| Metric | Unshrunk | Shrunk | Delta |
| --- | ---: | ---: | ---: |
| APK bytes | 7,100,067 | 143,028 | -6,957,039 (-97.9855%) |
| Uncompressed DEX bytes | 24,478,384 | 134,712 | -24,343,672 |
| DEX class definitions | 19,277 | 176 | -19,101 |
| Uncompressed Android resource bytes | 157,599 | 7,056 | -150,543 |

The byte-level verifier confirms that:

- the referenced `OutlinedVectorE5CA` (`Check`) backing class survives;
- the unreferenced `OutlinedVectorE9B2` (`Home`) class is present without R8
  and absent after R8; and
- an unreferenced `unused_resource_marker` is present without resource
  shrinking and absent after resource shrinking.

This proves the intended code/resource reachability for the fixture. The
97.9855% APK delta is **not** a claim that one icon always saves that percentage:
R8 also removes unused transitive Android/Compose code and the resource shrinker
removes unrelated fixture resources. Product dependency graphs, keep rules, and
usage determine a different result.

The measured environment was a 10-core Apple M4 Mac mini with 16 GB RAM, macOS
26.5.2, Temurin JDK 17.0.19, Gradle 8.14.3, AGP 8.13.0, R8 8.13.6, Kotlin
2.2.20, Compose Multiplatform 1.9.0, and Android compile/target SDK 36 with
minSdk 21.

The same machine measured one invocation that builds both fixture variants:

| Build mode | Conditions | Wall time | Peak sampled descendant RSS |
| --- | --- | ---: | ---: |
| Cold forced rerun | fresh single-use daemon, build cache disabled, every task rerun, configuration cache disabled | 55.845 s | 6,354,878,464 B (5.918 GiB) |
| Warm no-change | fresh single-use daemon, existing outputs, configuration cache disabled | 11.915 s | 1,458,520,064 B (1.358 GiB) |

RSS is the maximum 100 ms sample of the sum for the Gradle launcher and all
descendant processes; it is neither JVM heap usage nor unique resident memory.
Wall time includes Gradle startup. The cold result intentionally defeats
incremental execution, while the warm result exercises no-change task checks.
These local build-time measurements are workload and machine specific, not
runtime icon-rendering benchmarks.

Reproduce and inspect the current artifacts with:

```shell
./gradlew \
  :benchmarks:shrinkable-vectors:assembleUnshrunk \
  :benchmarks:shrinkable-vectors:assembleShrunk
python3 benchmarks/shrinkable-vectors/verify.py
JAVA_HOME=/path/to/jdk17 \
  python3 benchmarks/shrinkable-vectors/measure.py
```

CI reruns those builds and requires the used/unused class and resource facts to
remain true. The repository does not yet claim cross-platform render-time or
runtime-allocation numbers; the fixture measures release build output, not
on-device frames.

## Reproducible measurement

Compare equivalent release applications rather than library source size alone:

1. an empty Compose application;
2. the application plus `material-core`;
3. one variable-font style using one icon and then hundreds of icons;
4. the matching regular-font style at the default axes;
5. direct built-in vector properties for one, ten, and hundreds of icons;
6. the legacy dynamic vector bridge for the same visual set;
7. build-generated `ImageVector` output for an explicit selection;
8. build-generated Android XML with minification/resource shrinking off and on;
9. Compose drawable resources on each target that matters; and
10. all three styles only when that reflects a real product.

For build cost, record:

- clean generation and compilation wall time;
- a no-change warm build and whether generator tasks are `UP-TO-DATE` or loaded
  from the build cache;
- configuration-cache store/reuse status;
- peak Gradle and generator-process memory;
- generated source/XML count and bytes; and
- published module plus final release-artifact size.

For runtime cost, record:

- device/runtime and OS/API level;
- release/minified mode and Compose version;
- warmup count and measurement framework;
- first-icon and repeated-icon render time;
- allocation count/bytes for first and repeated access; and
- memory retained after a representative icon screen closes.

For shrinker evidence, retain the exact R8/AGP versions and rules, compare the
mapping/usage and resource-shrinker reports, and inspect the final archive for
the referenced and unreferenced icon classes/resources. Report compressed and
uncompressed entry sizes separately. Re-run the comparison after dependency or
toolchain upgrades; shrinker behavior is not an API guarantee.
