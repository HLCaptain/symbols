package io.github.hlcaptain.symbols.gradle

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/** Configures build-time icon generation from fonts or monochrome SVG files. */
abstract class SymbolFontsExtension @Inject constructor(
    objects: ObjectFactory,
) {
    /** Compose resource roots whose direct `font/` files receive generated descriptors. */
    val composeFontResources: ConfigurableFileCollection = objects.fileCollection()

    /** Kotlin package that receives all generated runtime symbol catalogs. */
    val catalogPackageName: Property<String> = objects.property(String::class.java)

    /** Named runtime catalogs generated from codepoint manifests. */
    val catalogs: NamedDomainObjectContainer<SymbolCatalogSpec> =
        objects.domainObjectContainer(SymbolCatalogSpec::class.java) { name ->
            objects.newInstance(SymbolCatalogSpec::class.java, name)
        }

    /** Named generated icon sets in this project. */
    val iconSets: NamedDomainObjectContainer<SymbolIconSet> =
        objects.domainObjectContainer(SymbolIconSet::class.java) { name ->
            objects.newInstance(SymbolIconSet::class.java, name)
        }

    /**
     * Declares an icon set and configures the Kotlin and resource names generated
     * for it.
     *
     * Use an icon set to group styles that share one public root, such as
     * `AppIcons.Rounded` and `AppIcons.Sharp`:
     *
     * ```kotlin
     * iconSet("AppIcons") {
     *     packageName.set("com.example.icons")
     *     style("Rounded") {
     *         // Configure the source and outputs.
     *     }
     * }
     * ```
     *
     * Calling this function again with the same [name] configures the existing
     * icon set instead of creating a duplicate. The [configure] action runs while
     * Gradle configures the project. Declaring a set also registers its generation
     * tasks and generated source directories; files are written only below the
     * project's `build` directory when those tasks run.
     *
     * [SymbolIconSet.rootName] defaults to [name]. If
     * [SymbolIconSet.packageName] is not set, the plugin derives it from the
     * Android namespace or from the Gradle project identity.
     *
     * @param name the name used to identify this set; it is also the default
     * generated icon root
     * @param configure configuration applied to the new or existing icon set
     */
    fun iconSet(name: String, configure: Action<in SymbolIconSet>) {
        configure.execute(iconSets.maybeCreate(name))
    }

    /**
     * Declares a named runtime catalog generated from a codepoint file.
     *
     * A catalog maps stable symbol names to code points without generating their
     * outlines. It is useful when an application loads the font at runtime:
     *
     * ```kotlin
     * catalogPackageName.set("com.example.icons.generated")
     * catalog("AppSymbols") {
     *     codepoints.set(file("AppSymbols.codepoints"))
     * }
     * ```
     *
     * Calling this function again with the same [name] configures the existing
     * catalog. The first catalog also adds the generated Kotlin directory to
     * Kotlin Multiplatform `commonMain` or Kotlin/JVM `main`. Plain
     * Kotlin/Android projects do not receive generated catalog sources
     * automatically. Generation writes only below `build` and reads the
     * configured [SymbolCatalogSpec.codepoints] file when the task runs.
     *
     * Set [catalogPackageName] whenever at least one catalog is declared. Catalog
     * names become module-internal Kotlin `val` properties. They must be valid,
     * distinct property identifiers. Names that differ only in the first
     * letter's case may conflict and fail the build.
     *
     * @param name the Kotlin property name used for this catalog
     * @param configure configuration applied to the new or existing catalog
     */
    fun catalog(name: String, configure: Action<in SymbolCatalogSpec>) {
        configure.execute(catalogs.maybeCreate(name))
    }
}

/** One named runtime catalog generated from a codepoint manifest. */
abstract class SymbolCatalogSpec @Inject constructor(
    private val catalogName: String,
    objects: ObjectFactory,
) : Named {
    @Input
    override fun getName(): String = catalogName

    /** Stable `<snake_case_name> <hex_code_point>` map for this catalog. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val codepoints: RegularFileProperty = objects.fileProperty()
}

/** A generated, strongly typed icon namespace. */
abstract class SymbolIconSet @Inject constructor(
    private val setName: String,
    objects: ObjectFactory,
) : Named {
    override fun getName(): String = setName

    /** Kotlin package that receives the generated API. */
    val packageName: Property<String> = objects.property(String::class.java)

    /** Public root object, for example `AppIcons`. Defaults to this set's name. */
    val rootName: Property<String> =
        objects.property(String::class.java).convention(setName)

    /** Named visual/font styles, for example `Rounded`. */
    val styles: NamedDomainObjectContainer<SymbolFontStyle> =
        objects.domainObjectContainer(SymbolFontStyle::class.java) { name ->
            objects.newInstance(SymbolFontStyle::class.java, name)
        }

    /**
     * Declares one visual style inside this icon set.
     *
     * Configure either a font plus [SymbolFontStyle.codepoints], or one flat
     * [SymbolFontStyle.svgDirectory]. Then enable at least one output such as
     * [SymbolFontStyle.imageVectors], [SymbolFontStyle.androidDrawables], or
     * [SymbolFontStyle.composeDrawables]. Do not combine an SVG directory with
     * [SymbolFontStyle.codepoints], [SymbolFontStyle.font], `font(...)`, a
     * nonzero [SymbolFontStyle.fontIndex], or [SymbolFontStyle.axes]. The
     * `androidDrawables(fontResource = ...)` overload is accepted with SVG input,
     * but its font argument is ignored and the plugin warns about it.
     *
     * ```kotlin
     * style("Rounded") {
     *     codepoints.set(file("AppIcons.codepoints"))
     *     font("app_icons.ttf")
     *     imageVectors()
     * }
     * ```
     *
     * Calling this function again with the same [name] configures the existing
     * style. Declaring a style prepares its build task and connects the enabled
     * outputs to supported Kotlin or resource source sets. Generated files remain
     * below the project's `build` directory.
     *
     * @param name a Kotlin type name such as `Rounded` or `Sharp`
     * @param configure configuration applied to the new or existing style
     */
    fun style(name: String, configure: Action<in SymbolFontStyle>) {
        configure.execute(styles.maybeCreate(name))
    }
}

/** One font source or one flat directory of path-based monochrome SVG files. */
abstract class SymbolFontStyle @Inject constructor(
    private val styleName: String,
    objects: ObjectFactory,
) : Named {
    override fun getName(): String = styleName

    /** Stable `<snake_case_name> <hex_code_point>` map for this font. */
    val codepoints: RegularFileProperty = objects.fileProperty()

    /** TTF, OTF, or indexed TTC input. */
    val font: RegularFileProperty = objects.fileProperty()

    /** Flat SVG input directory. Mutually exclusive with [font] and [codepoints]. */
    val svgDirectory: DirectoryProperty = objects.directoryProperty()

    internal val conventionalFontName: Property<String> =
        objects.property(String::class.java).convention("")

    internal val androidFontResourceName: Property<String> =
        objects.property(String::class.java).convention("")

    /** TTC face index. Ignored when [svgDirectory] is configured. */
    val fontIndex: Property<Int> =
        objects.property(Int::class.java).convention(0)

    /**
     * OpenType variation coordinates. Leave empty for an ordinary font or the
     * default instance of a variable font. Ignored for SVG sources.
     */
    val axes: MapProperty<String, Float> =
        objects.mapProperty(String::class.java, Float::class.java)
            .convention(emptyMap())

    /** Generates a shrinker-friendly common Compose `ImageVector` API. */
    val generateImageVectors: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    /** Generates native Android vector drawables under `res/drawable`. */
    val generateAndroidDrawables: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    /** Generates cross-platform Compose drawable resources. */
    val generateComposeDrawables: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    /** Android/Compose resource prefix. Defaults to the icon-set root name. */
    val resourcePrefix: Property<String> =
        objects.property(String::class.java)

    /** Number of unique code points grouped into each generated Kotlin file. */
    val symbolsPerFile: Property<Int> =
        objects.property(Int::class.java).convention(64)

    /** Stable decimal precision for path coordinates. */
    val precision: Property<Int> =
        objects.property(Int::class.java).convention(4)

    /** Generated vector viewport width. */
    val viewportWidth: Property<Float> =
        objects.property(Float::class.java).convention(24f)

    /** Generated vector viewport height. */
    val viewportHeight: Property<Float> =
        objects.property(Float::class.java).convention(24f)

    /**
     * Viewport units occupied by one font em. Defaults to the smaller viewport
     * dimension and is ignored for SVG sources.
     */
    val emSize: Property<Float> =
        objects.property(Float::class.java).convention(
            viewportWidth.zip(viewportHeight, ::minOf),
        )

    /** Horizontal viewport position of the font origin. Ignored for SVG sources. */
    val originX: Property<Float> =
        objects.property(Float::class.java).convention(0f)

    /**
     * Vertical viewport position of the font baseline. Defaults to the
     * viewport height and is ignored for SVG sources.
     */
    val baselineY: Property<Float> =
        objects.property(Float::class.java).convention(viewportHeight)

    /**
     * Selects one value on a variable font axis for generated outlines.
     *
     * The value is applied at build time, so the generated icon is fixed and
     * does not change when the application runs. Calling this
     * function again with the same [tag] replaces the previous value.
     *
     * ```kotlin
     * axis("wght", 600f)
     * axis("FILL", 1f)
     * ```
     *
     * The font must define the requested axis and [value] must be inside that
     * axis's range. Axis tags contain exactly four printable ASCII characters.
     * Generation fails instead of clamping an invalid value. Axes cannot be used
     * with [svgDirectory].
     *
     * @param tag the four-character OpenType axis tag, such as `wght`
     * @param value the fixed axis value used while extracting every glyph
     */
    fun axis(tag: String, value: Float) {
        axes.put(tag, value)
    }

    /**
     * Selects one font by its exact file name from the conventional resource
     * directories.
     *
     * The plugin searches direct files in `src/main/res/font`,
     * `src/androidMain/res/font`, and
     * `src/commonMain/composeResources/font`. Use this shortcut when a font
     * already belongs to one of those resource sets:
     *
     * ```kotlin
     * font("app_icons.ttf")
     * ```
     *
     * Pass only a file name, not a path. The name must end in `.ttf`, `.otf`, or
     * `.ttc`. Generation requires exactly one matching file and reports all
     * available fonts when none or more than one match. Calling this function
     * again replaces the previous selection. An explicitly configured [font]
     * property takes precedence over this shortcut.
     *
     * Variant-aware `androidDrawables(fontResource = ...)` uses the Android
     * variant's `res/font` resource instead; this selection continues to supply
     * shared ImageVector or Compose drawable output.
     *
     * @param fileName the exact font file name to select
     * @throws IllegalArgumentException if [fileName] is blank, contains a path
     * separator, or does not use a supported font extension
     */
    fun font(fileName: String) {
        requireSupportedFontName(fileName)
        conventionalFontName.set(fileName)
    }

    /**
     * Enables strongly typed Compose `ImageVector` source generation for this
     * style.
     *
     * Generated Kotlin is written below `build` and is added to `commonMain` in
     * Kotlin Multiplatform projects or `main` in Kotlin/JVM projects. Plain
     * Kotlin/Android projects do not receive these generated Kotlin sources
     * automatically; use [androidDrawables] there. Each icon is exposed through
     * the configured icon-set root and style, and unused icon implementations
     * can be removed by normal code shrinking.
     *
     * This output uses the explicit [font] property, the conventional file chosen
     * by `font(...)`, or [svgDirectory], and applies [axes] while building. When it
     * is combined with variant-aware `androidDrawables(fontResource = ...)`,
     * Android resource overlays affect only the native drawables; the plugin warns
     * about that difference. Calling this function more than once has no
     * additional effect.
     */
    fun imageVectors() {
        generateImageVectors.set(true)
    }

    /**
     * Enables native Android vector drawable generation from one shared source.
     *
     * This is the recommended mode when every Android variant uses the same icon
     * outlines. The plugin generates XML below `build`, adds it to each Android
     * application or library variant as `res/drawable`, and exposes the results
     * through the module's normal `R.drawable` class. Android's resource shrinker
     * can remove generated drawables that the final application does not use.
     *
     * The source is the explicit [font] property, the file selected with
     * `font(...)`, the sole conventional font, or [svgDirectory]. Every variant
     * receives the same generated files. Calling this overload after
     * `androidDrawables(fontResource = ...)` turns variant-aware font lookup off
     * and returns the style to shared-source mode.
     *
     * A supported Android application or library plugin must be applied for the
     * generated directory to be added to Android variants. Calling this function
     * more than once has no additional effect.
     */
    fun androidDrawables() {
        androidFontResourceName.set("")
        generateAndroidDrawables.set(true)
    }

    /**
     * Enables variant-aware native Android drawable generation from a font in
     * `res/font`.
     *
     * This mode is useful when Android resource overlays intentionally replace a
     * font, or when Android's resource shrinker should be able to identify and
     * remove an unused input font. Keep one file name across source sets, for
     * example:
     *
     * ```text
     * src/main/res/font/app_icons.ttf
     * src/debug/res/font/app_icons.ttf
     * src/free/res/font/app_icons.ttf
     * ```
     *
     * ```kotlin
     * androidDrawables(fontResource = "app_icons.ttf")
     * ```
     *
     * The plugin resolves `font/[fontResource]` separately for every Android
     * application or library variant using Android's normal source-set priority.
     * It searches only static resource directories in the current module; it
     * does not read dependency AARs or resource directories produced by another
     * task. The variant's generation task fails if no matching font exists or if
     * several matching files have the same source-set priority.
     * It registers one generation task per variant and adds the generated XML
     * below `build` to that variant's `res/drawable` resources. The original font
     * remains available as `R.font`; a final release build may remove it only when
     * resource shrinking proves that it is unused.
     *
     * ImageVector and Compose drawable outputs remain shared. They do not follow
     * Android overlays, and the plugin warns when those outputs are mixed with
     * this mode. If [svgDirectory] is configured, the plugin warns, ignores
     * [fontResource], and uses the shared SVG source. Without an Android
     * application or library plugin, no variant drawables are registered and a
     * warning is shown. Call `androidDrawables()` without an argument to return to
     * shared-source mode.
     *
     * @param fontResource a lowercase Android font resource file name ending in
     * `.ttf`, `.otf`, or `.ttc`
     * @throws IllegalArgumentException if [fontResource] is not a valid Android
     * font resource file name
     */
    fun androidDrawables(fontResource: String) {
        require(AndroidFontResourceName.matches(fontResource)) {
            "Android font resource must use a lowercase Android resource " +
                "file name ending in .ttf, .otf, or .ttc: $fontResource"
        }
        androidFontResourceName.set(fontResource)
        generateAndroidDrawables.set(true)
    }

    /**
     * Enables Compose Multiplatform drawable resource generation for this style.
     *
     * The plugin writes XML below `build`, merges it into the generated
     * `commonMain` Compose resource directory, and exposes each icon through
     * `Res.drawable`. The Compose plugin must be applied for that directory to be
     * registered automatically. A duplicate target path in another Compose
     * resource directory fails the merge instead of silently replacing a file.
     *
     * This output uses the explicit [font] property, the conventional file chosen
     * by `font(...)`, or [svgDirectory]. It does not follow Android variant
     * overlays, so combining it with variant-aware
     * `androidDrawables(fontResource = ...)` produces a warning. On Android,
     * Compose resources are packaged separately from native `R.drawable`
     * resources and are not removed by Android's native resource shrinker.
     * Calling this function more than once has no additional effect.
     */
    fun composeDrawables() {
        generateComposeDrawables.set(true)
    }
}

internal val SupportedFontExtensions: Set<String> =
    setOf("ttf", "otf", "ttc")

private fun requireSupportedFontName(fileName: String) {
    require(
        fileName.isNotBlank() &&
            '/' !in fileName &&
            '\\' !in fileName &&
            fileName.substringAfterLast('.', "").lowercase() in
            SupportedFontExtensions
    ) {
        "Font name must be a .ttf, .otf, or .ttc file name: $fileName"
    }
}

private val AndroidFontResourceName =
    Regex("^[a-z][a-z0-9_]*\\.(ttf|otf|ttc)$")
