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
    font = Symbols.Material.Rounded.font,
    contentDescription = null,
)
```

All `SymbolFont` descriptor calls share a native rendering core, including the
value overload. The base font is shared automatically. The `FontFamily` overload
is retained for interoperability with callers that already own a Compose text
family; when using that adapter for many icons, share the family:

```kotlin
import io.github.hlcaptain.symbols.font.rememberSymbolFontFamily
import io.github.hlcaptain.symbols.font.fontSettings

val font = Symbols.Material.Rounded.font
val family = rememberSymbolFontFamily(
    font = font,
    fontSettings = font.fontSettings(mapOf("wght" to 500f)),
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
Android API 23–25, use a `SymbolFont.Regular` or generated vector/drawable
instead. Material descriptors use the same `variationAxes` and
`SymbolFontSettings` contract as custom fonts. `MaterialSymbolsTheme` can
supply generic settings while selecting a theme-aware vector style.

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

### Animated font axes

See the [Pixel 6a frame and geometry comparison](../benchmarks/animated-font/PIXEL6A_RESULTS.md)
and [emulator results](../benchmarks/animated-font/RESULTS.md) for the current
implementation's benefits and limitations.

The settings-producer overload of `SymbolFontIcon` reads state during drawing:

```kotlin
val weight = animateFloatAsState(
    targetValue = if (emphasized) 700f else 100f,
    label = "Symbol weight",
)
SymbolFontIcon(
    codePoint = Symbols.Material.Home.codePoint,
    font = Symbols.Material.Rounded.font,
    contentDescription = null,
    fontSettings = {
        Symbols.Material.Rounded.font.fontSettings(mapOf("wght" to weight.value))
    },
)
```

The producer is non-composable and must be free of side effects. Read changing
state inside it; a value read before the call still invalidates that caller's
composition. Capture `SymbolsTheme.fontSettings` outside the producer if an
animation should overlay the current theme's settings.

With `size` unchanged, font-axis animation keeps the icon's square fixed. Size,
tint, mirroring, accessibility, and font-setting validation retain their existing
contract. Its native renderer loads the base font
independently of variation changes. Android shares up to 16 recently used varied
typefaces between mounted icons using the same font. This bounded cache supports
independently redrawn icons with different styles and is released with the font's
last renderer. Skia-backed targets keep full native
shaping while reusing the platform font manager. Changed coordinates update native
font state and glyph shaping, and unchanged coordinates reuse the current
native result. This avoids Compose text measurement and placement on every
animation frame; it does not eliminate native shaping, rasterization, or all
allocations. There is no unbounded cache of past animation values.

To defer color reads too, use `tint = { animatedColor.value }` with the native
settings producer. This overload uses Compose's standard
[`ColorProducer`](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/ColorProducer)
and reads both producers during drawing. Existing `tint = Color(...)` calls still
work. Keep `size` fixed and read transforms in `SymbolFontIcon`'s
`graphicsLayer = { ... }` block
to animate axes, tint, opacity, scale, rotation and translation without
animation-driven composition or layout. The benchmark's `scenario=draw` checks
these phase counts and verifies visible color changes; see the
[fixed-size measurements](../benchmarks/animated-font/DRAW_RESULTS.md).
The [component-owned layer results](../benchmarks/animated-font/OWNED_LAYER_RESULTS.md)
cover the current shared native core and the quieter benchmark.

Animating a `tint` value read in composition still recomposes its caller, and
animating the actual `size` also requests measurement and placement. The
[combined-effects stress scenario](../benchmarks/animated-font/README.md#combined-effects-stress-scenario)
exercises these value arguments together and reports their real costs; see the
[Pixel 6a stress measurements](../benchmarks/animated-font/STRESS_RESULTS.md). The runtime-axes
sample includes a stopped-by-default **Combined effects** card with Run/Stop.

On asynchronous targets, the fixed square stays empty until the font is ready.
Variable fonts retain the Android API 26 minimum; use a regular font or generated
vector on API 23–25. This renderer draws a single symbol codepoint, not arbitrary
paragraph text or independently animated characters.

The component owns that layer and defaults to `CompositingStrategy.ModulateAlpha`,
avoiding the automatic opacity buffer for a single glyph. The optional block uses
standard `GraphicsLayerScope` properties and wraps the entire modified icon; no
block means no added layer. An explicit strategy override is supported when a
caller adds overlapping drawing that needs group-opacity semantics. Independent
layers supplied through `modifier` retain their own behavior.

Passing a `SymbolFontSettings` value uses this same native core. The caller still
recomposes if it reads changing state before the call, but it does not switch to
Compose paragraph layout. Use producers to defer those reads as well.

The [Android animated-font benchmark](../benchmarks/animated-font/README.md)
compares explicit per-icon/shared Compose text references, the native value
overload, and native producers with the
same font, axis sweep, and icon count. It measures actual frame timing and text
layout trace sections, and checks fixed outer bounds separately from visible
glyph ink. Its normal mode retains correctness counters but omits per-icon custom
trace sections; `diagnostics=true` enables detailed phase tracing separately.
Run it on the device and workload that matter before treating lower
Compose phase counts as a frame-time improvement.

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

The following universal APKs were measured on 2026-08-29. The `material-static`
and `all` profiles were rebuilt on 2026-09-01 after adding the public Rounded
Compose drawable pack. The marginal columns subtract the matching shell APK;
they are not additive because features share fonts and dependencies and R8
optimizes the final graph as a whole.

| Profile | Sample usage | Release bytes | Shrunk bytes | Release − shell | Shrunk − shell |
| --- | --- | ---: | ---: | ---: | ---: |
| `shell` | Launcher only | 14,155,317 | 1,495,397 | 0 | 0 |
| `custom-static` | 8 Powerline vectors, Compose drawables, packaged OTF | 14,244,621 | 1,551,929 | 89,304 | 56,532 |
| `custom-variable` | 50 fixed Academmunicons resources plus 97 KB live font | 14,514,447 | 1,772,603 | 359,130 | 277,206 |
| `material-static` | Rounded regular font and full Compose drawable pack | 21,141,932 | 7,711,960 | 6,986,615 | 6,216,563 |
| `theming` | Rounded regular font with theme inheritance | 15,954,394 | 3,245,318 | 1,799,077 | 1,749,921 |
| `material-variable` | Rounded variable font at defaults | 28,840,612 | 16,131,536 | 14,685,295 | 14,636,139 |
| `runtime-axes` | Rounded variable font with live controls | 28,840,612 | 16,164,304 | 14,685,295 | 14,668,907 |
| `image-vector-migration` | Variable font, vectors, 53 Compose XML assets, 3 native XML icons | 33,531,119 | 16,299,006 | 19,375,802 | 14,803,609 |
| `android-views` | Migration payload plus AppCompat/data binding and 3,802 native drawables | 37,136,232 | 16,473,460 | 22,980,915 | 14,978,063 |
| `all` | All eight samples | 44,210,627 | 22,908,821 | 30,055,310 | 21,413,424 |

The complete app shrank by 21,301,806 bytes (48.1825%). Its payload explains
where that reduction stops:

| Full-app payload | Release bytes | Shrunk bytes | Removed | Change | Shrunk raw/count |
| --- | ---: | ---: | ---: | ---: | ---: |
| APK | 44,210,627 | 22,908,821 | 21,301,806 | -48.1825% | — |
| ZIP entry payload | 42,251,526 | 21,335,658 | 20,915,868 | -49.5032% | 27,062,310 bytes |
| DEX | 18,586,066 | 1,116,169 | 17,469,897 | -93.9946% | 2,238,028 bytes / 2,680 classes |
| Font assets | 16,385,751 | 16,385,751 | 0 | 0% | 16,386,688 bytes / 4 files |
| Compose drawable assets | 3,112,891 | 3,112,891 | 0 | 0% | 7,561,301 bytes / 3,913 files |
| Android `res/` plus `resources.arsc` | 4,080,983 | 643,775 | 3,437,208 | -84.2250% | 718,553 bytes / 296 files |
| Native libraries | 37,392 | 37,392 | 0 | 0% | 37,392 bytes / 4 ABI entries |

The APK row includes ZIP directory and header overhead. Other category rows are
compressed entry payload bytes.

The compressed entry mix shifts from 43.99% to 5.23% DEX, 38.78% to
76.80% fonts, 7.37% to 14.59% Compose assets, and 9.66% to 3.02% Android
resources. ZIP entries fall from 8,213 to 4,321 (-47.39%); the 3,802 Rounded
Compose XML entries remain in both variants.

Raw DEX falls from 68,474,812 to 2,238,028 bytes (-96.7316%). Class definitions
fall from 45,310 to 2,680 (-94.0852%), method IDs from 248,774 to 15,270
(-93.8619%), and string IDs from 282,900 to 10,738 (-96.2043%).

The focused vector verifier below proves that R8 removes unreferenced backing
classes. For scale, the Rounded vector runtime `classes.jar` is 35,687,897
bytes, while the complete shrunk application's raw DEX totals 2,238,028 bytes;
those cross-format sizes are only a coarse bound, not byte attribution to the
vector module. Android's resource report retains only the generated Outlined
Home and Favorite drawables plus all three reached Tabler drawables. By contrast,
Compose resources are Android assets, so all 8 Powerline, 50 fixed
Academmunicons, 50 migration Academmunicons, 3 Tabler, and 3,802 Rounded Material
XML files remain byte-for-byte in both variants. The full public Rounded pack
contributes 2,979,070 bytes of compressed asset-entry payload. The measured
`material-static` integration grows by 5,187,538 release bytes and 4,466,642
shrunk bytes versus its previous profile; that delta also includes the sample
card, DEX, ZIP names, and container overhead. This is the deliberate current
tradeoff for standard `painterResource(Res.drawable...)` DevEx; no Android asset
pruning is applied.

Fonts are also indivisible. The four full-app font assets account for 71.5259%
of the shrunk APK, while Compose drawable assets account for another 13.5882%.
The variable Material profile remains 14,636,139 shrunk bytes above the shell
regardless of how many glyphs it renders. This favors a font for a large icon
vocabulary or live axes, a complete Compose drawable pack when standard resource
DevEx is paramount, and independently reachable vectors/native drawables for a
small fixed set.

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
| Full Outlined Compose drawable pack | 34,267 | 48,894,692 |
| Full Rounded Compose drawable pack | 34,267 | 67,104,161 |
| Full Sharp Compose drawable pack | 34,267 | 43,854,509 |
| Full Outlined native drawable pack | 3,803 | 5,426,528 |

The Compose pack counts above include nine generated or target-specific copies
of each XML. Each runtime resource archive shown below contains 3,802 unique
resources and no font:

| Pack | Android AAR | JVM JAR | KMP resource ZIP |
| --- | ---: | ---: | ---: |
| Outlined | 4,451,042 | 4,421,321 | 3,695,854 |
| Rounded | 5,146,101 | 5,116,406 | 4,391,132 |
| Sharp | 4,194,943 | 4,165,949 | 3,442,375 |

The pre-Compose-pack timing baseline used a Ryzen 9 7950X3D (16 cores/32 threads), 64 GiB RAM,
Linux 7.2 x86-64, Azul JDK 21.0.12.1, Gradle 8.14.5, AGP 8.13.2, Kotlin 2.3.21,
Compose Multiplatform 1.11.1, one Gradle worker, and a fresh single-use daemon
per invocation. Another Gradle project was active, so these are contended local
observations rather than clean comparative benchmarks. Normal cached profile
builds also set `kotlin.incremental=false` and compile in-process to force Koin
to recollect the selected dependency graph; they do not represent the default
incremental developer configuration.

| Pre-pack build path | Samples | Median wall time |
| --- | --- | ---: |
| Full release, caches disabled and all tasks rerun | 96.154 s, 96.908 s, 94.108 s | 96.154 s |
| Full shrunk, caches disabled and all tasks rerun | 110.559 s, 111.763 s, 110.132 s | 110.559 s |
| Shell release, warm no-change | 4.788 s, 4.713 s, 4.745 s | 4.745 s |
| Shell shrunk, warm no-change | 4.739 s, 4.527 s, 4.752 s | 4.739 s |
| Full release, warm no-change | 6.637 s, 6.386 s, 6.492 s | 6.492 s |
| Full shrunk, warm no-change | 6.575 s, 6.347 s, 6.184 s | 6.347 s |

These medians predate the full Compose drawable artifacts and are retained only
as historical baseline data; they do not measure the current generator graph.
The forced full-execution medians differed by 14.405 seconds and the observed
full-versus-shell no-change medians differed by roughly 1.7 seconds. Host
contention prevents attributing either difference to R8 or the sample graph.
The table remains useful as a reproducible order-of-magnitude observation;
generator tasks were up-to-date in the no-change path and the configuration
cache was reused. The one-pass profile timings and exact task commands remain
in `build/reports/apk-study`; they are deliberately not promoted to comparative
medians.

The practical result is:

- keep custom `composeDrawables()` output opt-in and use it for focused sets;
- choose a published full Compose drawable pack only when standard public
  `Res.drawable` DevEx outweighs its indivisible asset payload;
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
estimates. The original matrix was based on commit `a8a96f7`; the refreshed
`material-static` and `all` profiles were measured from commit `6a22238` plus
the changes under test. Exact commands, Git status, SHA-256 values, DEX headers,
font entries, namespaces, and raw/compressed ZIP metrics are retained under
`build/reports/apk-study`.

### Android R8/resource-shrinker fixture

[`benchmarks/shrinkable-vectors`](../benchmarks/shrinkable-vectors/README.md)
is a minimal Android application that references exactly the typed
`Symbols.Material.Outlined.Check` getter and one generated native Powerline
drawable. Powerline is also the variant-aware `res/font` generator input, but
the application never references its `R.font`. The `unshrunk` build disables
minification and resource shrinking; its otherwise equivalent `shrunk` build
enables full-mode R8 and `shrinkResources`. A `-keepnames` rule preserves the
original names of surviving vector backing classes without keeping unreachable
classes.

The expanded fixture produced these local release artifacts on 2026-09-02:

| Metric | Unshrunk | Shrunk | Delta |
| --- | ---: | ---: | ---: |
| APK bytes | 8,009,164 | 220,213 | -7,788,951 (-97.2505%) |
| Uncompressed DEX bytes | 25,808,876 | 137,392 | -25,671,484 |
| DEX class definitions | 20,363 | 183 | -20,180 |
| Uncompressed Android resource bytes | 171,859 | 8,868 | -162,991 |

The byte-level verifier confirms that:

- the referenced `OutlinedVectorE5CA` (`Check`) backing class survives;
- the unreferenced `OutlinedVectorE9B2` (`Home`) class is present without R8
  and absent after R8;
- an unreferenced `unused_resource_marker` is present without resource
  shrinking and absent after resource shrinking;
- the exact 2,264-byte font input is present without resource shrinking and
  absent after resource shrinking; and
- the referenced generated drawable remains in the shrunk APK while another is
  absent, corroborated by the optimized resource report.

This proves the intended code/resource reachability for the fixture. The
97.2505% APK delta is **not** a claim that one icon always saves that percentage:
R8 also removes unused transitive Android/Compose code and the resource shrinker
removes unrelated fixture resources. Product dependency graphs, keep rules, and
usage determine a different result.

The historical build-time environment was a 10-core Apple M4 Mac mini with
16 GB RAM, macOS 26.5.2, Temurin JDK 17.0.19, Gradle 8.14.3, AGP 8.13.0,
R8 8.13.6, Kotlin 2.2.20, Compose Multiplatform 1.9.0, and Android
compile/target SDK 36 with minSdk 21.

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
