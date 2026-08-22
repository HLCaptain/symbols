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

    /** Declares or configures a generated icon set. */
    fun iconSet(name: String, configure: Action<in SymbolIconSet>) {
        configure.execute(iconSets.maybeCreate(name))
    }

    /** Declares or configures one generated runtime catalog. */
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

    /** Declares or configures a visual/font style. */
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

    /** Adds or replaces one OpenType variation coordinate. */
    fun axis(tag: String, value: Float) {
        axes.put(tag, value)
    }

    /**
     * Selects a font by name from a conventional Android or Compose font
     * directory.
     */
    fun font(fileName: String) {
        require(
            fileName.isNotBlank() &&
                '/' !in fileName &&
                '\\' !in fileName &&
                fileName.substringAfterLast('.', "").lowercase() in
                SupportedFontExtensions
        ) {
            "Font name must be a .ttf, .otf, or .ttc file name: $fileName"
        }
        conventionalFontName.set(fileName)
    }

    /** Enables the common Kotlin `ImageVector` output. */
    fun imageVectors() {
        generateImageVectors.set(true)
    }

    /** Enables native Android `res/drawable` output. */
    fun androidDrawables() {
        generateAndroidDrawables.set(true)
    }

    /** Enables Compose Multiplatform drawable-resource output. */
    fun composeDrawables() {
        generateComposeDrawables.set(true)
    }
}

internal val SupportedFontExtensions: Set<String> =
    setOf("ttf", "otf", "ttc")
