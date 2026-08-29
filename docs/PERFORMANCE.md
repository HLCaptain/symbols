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
    codePoint = Symbols.Material.Home.codePoint,
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

Keep the font descriptor stable and reuse settings while their values are
unchanged. A regular font has one fixed settings point and never constructs
variation settings. Searching `Symbols.Material.all` is suitable for an icon
picker; a hot application path should retain its filtered result rather than
scanning all names on every frame.

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
the launcher enables it because the `material-variable` and `runtime-axes`
samples use the 14.6 MB Rounded variable font.

Sample controls use continuous Material sliders (`steps = 0`) and let each axis
start or stop its example animation. An animated variable-font glyph follows
the normal Compose text path on every changed setting: its `FontFamily` changes,
the font is resolved, and `BasicText` is remeasured, laid out, and redrawn. The
runtime adds no custom variation cache; Compose and the platform font stack own
their normal reuse. This is deliberate sample simplicity, so profile a product's
real icon count and animation before choosing its update rate.

A vector builder runs on first property access and caches the resulting
`ImageVector`. Codepoint aliases share that builder and cache. This trades
first-access work and generated code for independent reachability; measure both
cold and warm access.

For stroked SVG vectors, `rememberSymbolPainter()` reuses that cached geometry
and changes only `VectorConfig.StrokeLineWidth` from the current
`SymbolsTheme.fontSettings` `wght`. Weight 100, 400, and 700 map to 0.5×, 1×,
and 1.5× the authored width. The settings-producer overload moves its snapshot
read into the painter's vector child composition, which can keep the caller
stable when it does not otherwise read that state. A stroke change still
updates the vector child composition and rasterizes it again; this is scope
isolation, not zero recomposition. It does not parse SVG or rebuild paths at
runtime. Direct `ImageVector` use and generated Android/Compose XML stay at the
authored 1× width.

## Shrinkability boundaries

The build-time plugin generates every manifest entry or direct SVG file. The
Gradle DSL has no per-icon selection mode. Keep each source directory focused,
or split a measured large set into separate modules when clean-build time,
compiler memory, or unshrunk target packaging warrants the extra boundary.

The focused `custom-static` module deliberately puts its input font in the
conventional `composeResources/font` directory to teach automatic discovery
with a minimal DSL. The `custom-variable` module packages Academmunicons
deliberately so it can compare fixed build-time vectors with the original live
runtime font. An application that needs only generated vectors should keep the
font outside Android and Compose resource roots and select it with
`font.set(...)`.

Generated `ImageVector` properties call independent per-codepoint builders.
They emit direct path operations and contain no registry, path table, reflection
hook, dispatcher, or all-icons collection. The built-in direct properties, such
as `Symbols.Material.Outlined.Home`, likewise bypass their pack-wide dynamic
dispatcher.
This structure allows a full-mode code shrinker to analyze unused icon classes
as unreachable, subject to the consuming application's keep rules.

The compatibility APIs
`MaterialSymbol.asOutlinedImageVector()`,
`asRoundedImageVector()`, and `asSharpImageVector()` intentionally support
runtime-selected catalog values. They therefore reference a codepoint index and
dispatcher and can retain substantially more of a built-in pack. Prefer typed
properties when the selected icon is known at compile time.

Native Android generation emits one `res/drawable` XML file per unique font
codepoint or SVG file. Those files can participate in Android resource shrinking
when a release build enables both code minification and `shrinkResources`.
Dynamic resource lookup and keep files can retain additional resources. Compose
Multiplatform resource packaging differs by target; do not generalize Android
resource-shrinker behavior to every Compose output.

Runtime font files are indivisible resources from the application's point of
view. R8 does not remove glyphs from a bundled TTF. If a project needs only a
small fixed set, generate those outlines instead of expecting a code shrinker to
subset a font.

## Evidence boundary

Repository tests establish structural properties: complete input generation,
direct builder calls, alias cache sharing, absence of a generated global
registry, one-resource-per-font-codepoint/SVG output, secure SVG rejection, and
deterministic regeneration. The exact font byte counts above are verified from
checked-in files. Visual font/SVG comparisons use the same-runner
[Roborazzi workflow](SCREENSHOT_TESTING.md) because pixel output varies by OS.

### Full sample APK matrix

[`benchmarks/sample-app`](../benchmarks/sample-app/README.md) builds the same
launcher shell with either one sample feature or all eight features. This avoids
comparing unrelated application scaffolds: every profile retains Material 3,
Navigation 3, Koin, AppCompat, the shared sample API/UI, and the Rounded
navigation vector. `release` is unsigned and unminified; `shrunk` is the same
release configuration with full-mode R8 and optimized Android resource
shrinking.

The following universal APKs were measured on 2026-08-29. The marginal columns
subtract the matching shell APK; they are not additive because features share
fonts and dependencies and R8 optimizes the final graph as a whole.

| Profile | Sample usage | Release bytes | Shrunk bytes | Release − shell | Shrunk − shell |
| --- | --- | ---: | ---: | ---: | ---: |
| `shell` | Launcher only | 14,155,317 | 1,495,397 | 0 | 0 |
| `custom-static` | 8 Powerline vectors, Compose drawables, packaged OTF | 14,244,621 | 1,551,929 | 89,304 | 56,532 |
| `custom-variable` | 50 fixed Academmunicons resources plus 97 KB live font | 14,514,447 | 1,772,603 | 359,130 | 277,206 |
| `material-static` | Rounded regular font | 15,954,394 | 3,245,318 | 1,799,077 | 1,749,921 |
| `theming` | Rounded regular font with theme inheritance | 15,954,394 | 3,245,318 | 1,799,077 | 1,749,921 |
| `material-variable` | Rounded variable font at defaults | 28,840,612 | 16,131,536 | 14,685,295 | 14,636,139 |
| `runtime-axes` | Rounded variable font with live controls | 28,840,612 | 16,164,304 | 14,685,295 | 14,668,907 |
| `image-vector-migration` | Variable font, vectors, 53 Compose XML assets, 3 native XML icons | 33,531,119 | 16,299,006 | 19,375,802 | 14,803,609 |
| `android-views` | Migration payload plus AppCompat/data binding and 3,802 native drawables | 37,136,232 | 16,473,460 | 22,980,915 | 14,978,063 |
| `all` | All eight samples | 39,104,951 | 18,474,947 | 24,949,634 | 16,979,550 |

The complete app shrank by 20,630,004 bytes (52.7555%). Its payload explains
where that reduction stops:

| Full-app payload | Release compressed bytes | Shrunk compressed bytes | Shrunk raw bytes/count |
| --- | ---: | ---: | ---: |
| DEX | 17,922,744 | 1,111,108 | 2,209,664 bytes / 2,675 classes |
| Font assets | 16,385,751 | 16,385,751 | 16,386,688 bytes / 4 files |
| Compose drawable assets | 133,821 | 133,821 | 330,619 bytes / 111 files |
| Android `res/` plus `resources.arsc` | 4,080,983 | 643,775 | 718,553 bytes / 296 files |
| Native libraries | 37,392 | 37,392 | 37,392 bytes / 4 ABI entries |

The focused vector verifier below proves that R8 removes unreferenced backing
classes. For scale, the Rounded vector runtime `classes.jar` is 35,687,897
bytes, while the complete shrunk application's raw DEX totals 2,209,664 bytes;
those cross-format sizes are only a coarse bound, not byte attribution to the
vector module. Android's resource report retains only the generated Outlined
Home and Favorite drawables plus all three reached Tabler drawables. By contrast, Compose
resources are Android assets, so all 8 Powerline, 50 fixed Academmunicons, 50
migration Academmunicons, and 3 Tabler XML files remain byte-for-byte in both
variants. `composeDrawables()` is therefore appropriate for focused sets, but a
full Material Compose-drawable pack would not gain Android resource shrinking.

Fonts are also indivisible. The four full-app font assets account for 88.6917%
of the shrunk APK. The regular and variable Material profiles demonstrate the
fixed cost directly: 1,749,921 and 14,636,139 shrunk bytes above the shell,
respectively, regardless of how many glyphs the sample renders. This favors a
font for a large icon vocabulary or live axes and favors independently
reachable vectors/drawables for a small fixed set.

The launcher normally stores TTFs without ZIP compression so Android can
memory-map them. A benchmark-only configuration measured the opposite tradeoff:

| Rounded variable-font packaging | Release bytes | Shrunk bytes | Font entry |
| --- | ---: | ---: | ---: |
| Stored (`noCompress += "ttf"`) | 28,840,612 | 16,131,536 | 14,586,584 |
| Deflated | 20,598,304 | 7,889,224 | 6,344,274 |
| Difference | -8,242,308 | -8,242,312 | -8,242,310 |

Deflation saves 28.5788% of the unminified APK and 51.0944% of the shrunk APK,
but repeated variable-font instances then require inflated buffers instead of
the mmap-friendly stored asset. The default remains uncompressed because the
runtime-axis samples intentionally exercise many changing font instances.

Generated build output also scales with the selected representation. This is a
workspace snapshot taken after the all-profile study; the analyzer does not tie
these trees to an individual APK. Counts include per-task outputs and the merged
Compose copy, so they describe build work rather than unique final APK entries:

| Producer | Generated files | Generated bytes |
| --- | ---: | ---: |
| Powerline custom sample | 22 | 25,500 |
| Academmunicons custom-variable sample | 108 | 709,127 |
| Academmunicons/Tabler migration sample | 162 | 690,967 |
| Full Outlined native drawable pack | 3,803 | 5,426,528 |

The build study used a Ryzen 9 7950X3D (16 cores/32 threads), 64 GiB RAM,
Linux 7.2 x86-64, Azul JDK 21.0.12.1, Gradle 8.14.5, AGP 8.13.2, Kotlin 2.3.21,
Compose Multiplatform 1.11.1, one Gradle worker, and a fresh single-use daemon
per invocation. Another Gradle project was active, so these are contended local
observations rather than clean comparative benchmarks. Normal cached profile
builds also set `kotlin.incremental=false` and compile in-process to force Koin
to recollect the selected dependency graph; they do not represent the default
incremental developer configuration.

| Build path | Samples | Median wall time |
| --- | --- | ---: |
| Full release, caches disabled and all tasks rerun | 96.154 s, 96.908 s, 94.108 s | 96.154 s |
| Full shrunk, caches disabled and all tasks rerun | 110.559 s, 111.763 s, 110.132 s | 110.559 s |
| Shell release, warm no-change | 4.788 s, 4.713 s, 4.745 s | 4.745 s |
| Shell shrunk, warm no-change | 4.739 s, 4.527 s, 4.752 s | 4.739 s |
| Full release, warm no-change | 6.637 s, 6.386 s, 6.492 s | 6.492 s |
| Full shrunk, warm no-change | 6.575 s, 6.347 s, 6.184 s | 6.347 s |

The forced full-execution medians differed by 14.405 seconds and the observed
full-versus-shell no-change medians differed by roughly 1.7 seconds. Host
contention prevents attributing either difference to R8 or the sample graph.
The table remains useful as a reproducible order-of-magnitude observation;
generator tasks were up-to-date in the no-change path and the configuration
cache was reused. The one-pass profile timings and exact task commands remain
in `build/reports/apk-study`; they are deliberately not promoted to comparative
medians.

The practical result is:

- keep `composeDrawables()` opt-in and use it for focused cross-platform sets;
- prefer typed `ImageVector` properties for a few compile-time-known icons when
  a minified release is guaranteed;
- prefer native Android drawables when Android resource shrinking is valuable;
- prefer the regular font for a large fixed vocabulary and the variable font
  only when runtime axes justify its much larger indivisible payload; and
- keep generated or task-produced fonts under standard `font/` resources so
  Compose owns `Res.font`, with `Res.symbolFonts` remaining a typed metadata
  layer rather than another packaged copy.

Reproduce the local-only study without a build scan, upload, Actions cache, or
artifact storage:

```shell
JAVA_HOME=/path/to/jdk21 \
  python3 benchmarks/sample-app/build.py --label isolated-matrix
JAVA_HOME=/path/to/jdk21 \
  python3 benchmarks/sample-app/build.py \
    --profile shell --profile all \
    --variant release --variant shrunk \
    --repeat 4 --label warm
JAVA_HOME=/path/to/jdk21 \
  python3 benchmarks/sample-app/build.py \
    --profile all --variant release --variant shrunk \
    --repeat 3 --execution-cold --label execution-cold
JAVA_HOME=/path/to/jdk21 \
  python3 benchmarks/sample-app/build.py \
    --profile material-variable --variant release --variant shrunk \
    --compress-fonts --label compressed-fonts
python3 benchmarks/sample-app/analyze.py
```

The APKs are unsigned universal install artifacts, not Play-delivered download
estimates. The study was based on commit `a8a96f7` plus the resource/profile
changes under test; the exact commands, Git status, SHA-256 values, DEX headers,
font entries, namespaces, and raw/compressed ZIP metrics are retained under
`build/reports/apk-study`.

### Android R8/resource-shrinker fixture

[`benchmarks/shrinkable-vectors`](../benchmarks/shrinkable-vectors/README.md)
is a minimal Android application that references exactly the typed
`Symbols.Material.Outlined.Check` getter. Its `unshrunk` build disables minification and
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

### Build-time generator baseline

On 2026-08-22, the `image-vector-migration` fixture measured the production
Gradle backend with both representative styles: 50 Academmunicons glyphs from
one variable font and three Tabler SVGs. Together they produced 59 task files
and 472,129 bytes below `build/generated/symbolFonts`, plus four Android task
files and 8,239 bytes below `build/generated/res`. No generated file was
written into `src`.

The measurement used a Ryzen 9 7950X3D host with 64 GiB RAM, Linux x86-64,
Corretto 21.0.11, Gradle 8.14.5, one worker, a fresh single-use daemon for each
invocation, the local configuration cache, and the build cache disabled. Each
path received one unmeasured warmup. Another Gradle build was active on the
host, so the raw samples and median are retained rather than presenting the
fastest run alone.

| Mode | Wall-time samples | Median |
| --- | --- | ---: |
| Delete both task outputs, then generate | 2.884 s, 2.780 s, 2.710 s | 2.780 s |
| No-change task check | 2.322 s, 2.368 s, 2.334 s | 2.334 s |

The approximate median cost above the same Gradle startup/configuration path
was 0.446 s for both generators. A following normal invocation reported both
tasks `UP-TO-DATE` and reused the configuration cache. This small mixed fixture
does not support a throughput claim for the 3,802-icon Material packs; it does
show that moving extraction into every compiler or KSP target would optimize
the wrong boundary for the current architecture.

Reproduce the clean-output and no-change paths without a remote build scan,
artifact upload, or GitHub Actions cache:

```shell
./gradlew \
  :samples:image-vector-migration:cleanGenerateAcademmuniconsDefaultSymbolFonts \
  :samples:image-vector-migration:cleanGenerateTablerOutlineSymbolFonts \
  :samples:image-vector-migration:generateAcademmuniconsDefaultSymbolFonts \
  :samples:image-vector-migration:generateTablerOutlineSymbolFonts \
  --no-daemon --max-workers=1 --no-build-cache

./gradlew \
  :samples:image-vector-migration:generateAcademmuniconsDefaultSymbolFonts \
  :samples:image-vector-migration:generateTablerOutlineSymbolFonts \
  --no-daemon --max-workers=1 --no-build-cache
```

Python is not on this consumer-build path. It remains a maintainer tool for
pinned Material publication snapshots, FontTools static-font instancing, and
independent verification. KSP still emits source, and a compiler plugin would
not replace those binary-font operations, so neither alternative reduces that
Python boundary by itself.

## Reproducible measurement

Compare equivalent release applications rather than library source size alone:

1. an empty Compose application;
2. the application plus `material-core`;
3. one variable-font style using one icon and then hundreds of icons;
4. the matching regular-font style at the default axes;
5. direct built-in vector properties for one, ten, and hundreds of icons;
6. the legacy dynamic vector bridge for the same visual set;
7. build-generated font and direct-SVG `ImageVector` output for the same set;
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
