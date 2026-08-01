package io.github.hlcaptain.symbols.gradle

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.file.RegularFileProperty

/** Configures build-time icon generation from regular or variable fonts. */
public abstract class SymbolFontsExtension @Inject constructor(
    objects: ObjectFactory,
) {
    /** Named generated icon sets in this project. */
    public val iconSets: NamedDomainObjectContainer<SymbolIconSet> =
        objects.domainObjectContainer(SymbolIconSet::class.java) { name ->
            objects.newInstance(SymbolIconSet::class.java, name)
        }

    /** Declares or configures a generated icon set. */
    public fun iconSet(name: String, configure: Action<in SymbolIconSet>) {
        configure.execute(iconSets.maybeCreate(name))
    }
}

/** A semantic manifest and its generated, strongly typed root namespace. */
public abstract class SymbolIconSet @Inject constructor(
    private val setName: String,
    objects: ObjectFactory,
) : Named {
    override fun getName(): String = setName

    /** Kotlin package that receives the generated API. */
    public val packageName: Property<String> = objects.property(String::class.java)

    /** Public root object, for example `AppIcons`. Defaults to this set's name. */
    public val rootName: Property<String> =
        objects.property(String::class.java).convention(setName)

    /** Stable `<snake_case_name> <hex_code_point>` semantic manifest. */
    public val manifest: RegularFileProperty = objects.fileProperty()

    /** Explicitly selected manifest names. This is the fast default. */
    public val includedNames: SetProperty<String> =
        objects.setProperty(String::class.java).convention(emptySet())

    /** Whether every manifest entry should be generated. */
    public val allSymbols: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    /** Named visual/font styles, for example `Rounded`. */
    public val styles: NamedDomainObjectContainer<SymbolFontStyle> =
        objects.domainObjectContainer(SymbolFontStyle::class.java) { name ->
            objects.newInstance(SymbolFontStyle::class.java, name)
        }

    /** Adds manifest names without replacing an earlier selection. */
    public fun include(vararg names: String) {
        includedNames.addAll(names.toList())
    }

    /** Opts into generating the complete manifest. */
    public fun includeAll() {
        allSymbols.set(true)
    }

    /** Declares or configures a visual/font style. */
    public fun style(name: String, configure: Action<in SymbolFontStyle>) {
        configure.execute(styles.maybeCreate(name))
    }
}

/** One regular font or one fixed instance of a variable font. */
public abstract class SymbolFontStyle @Inject constructor(
    private val styleName: String,
    objects: ObjectFactory,
) : Named {
    override fun getName(): String = styleName

    /** TTF, OTF, or indexed TTC input. */
    public val font: RegularFileProperty = objects.fileProperty()

    /** TTC face index. */
    public val fontIndex: Property<Int> =
        objects.property(Int::class.java).convention(0)

    /**
     * OpenType variation coordinates. Leave empty for an ordinary font or the
     * default instance of a variable font.
     */
    public val axes: MapProperty<String, Float> =
        objects.mapProperty(String::class.java, Float::class.java)
            .convention(emptyMap())

    /** Generates a shrinker-friendly common Compose `ImageVector` API. */
    public val generateImageVectors: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    /** Generates native Android vector drawables under `res/drawable`. */
    public val generateAndroidDrawables: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    /** Generates cross-platform Compose drawable resources. */
    public val generateComposeDrawables: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    /** Android/Compose resource prefix. Defaults to the icon-set root name. */
    public val resourcePrefix: Property<String> =
        objects.property(String::class.java)

    /** Number of unique code points grouped into each generated Kotlin file. */
    public val symbolsPerFile: Property<Int> =
        objects.property(Int::class.java).convention(64)

    /** Stable decimal precision for path coordinates. */
    public val precision: Property<Int> =
        objects.property(Int::class.java).convention(4)

    /** Generated vector viewport width. */
    public val viewportWidth: Property<Float> =
        objects.property(Float::class.java).convention(24f)

    /** Generated vector viewport height. */
    public val viewportHeight: Property<Float> =
        objects.property(Float::class.java).convention(24f)

    /** Viewport units occupied by one font em. Defaults to the smaller viewport dimension. */
    public val emSize: Property<Float> =
        objects.property(Float::class.java).convention(
            viewportWidth.zip(viewportHeight, ::minOf),
        )

    /** Horizontal viewport position of the font origin. */
    public val originX: Property<Float> =
        objects.property(Float::class.java).convention(0f)

    /** Vertical viewport position of the font baseline. Defaults to the viewport height. */
    public val baselineY: Property<Float> =
        objects.property(Float::class.java).convention(viewportHeight)

    /** Adds or replaces one OpenType variation coordinate. */
    public fun axis(tag: String, value: Float) {
        axes.put(tag, value)
    }

    /** Enables the common Kotlin `ImageVector` output. */
    public fun imageVectors() {
        generateImageVectors.set(true)
    }

    /** Enables native Android `res/drawable` output. */
    public fun androidDrawables() {
        generateAndroidDrawables.set(true)
    }

    /** Enables Compose Multiplatform drawable-resource output. */
    public fun composeDrawables() {
        generateComposeDrawables.set(true)
    }
}
