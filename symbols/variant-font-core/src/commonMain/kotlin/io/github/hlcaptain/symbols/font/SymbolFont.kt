package io.github.hlcaptain.symbols.font

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.Font as resourceFont

/**
 * Font settings that can be shared by symbol-font renderers.
 *
 * Create this type directly when a font uses custom settings, or use the `weight` and `style`
 * constructor for Compose's standard weight and style controls. Creating settings does not load a
 * font or check whether a particular font supports them. Use
 * [SymbolFont.fontSettings] when values should be checked against a descriptor's declared axes;
 * directly supplied settings are otherwise passed to a variable font unchanged.
 *
 * @property variationSettings settings passed to Compose when a variable font is rendered
 */
@Immutable
data class SymbolFontSettings(
    val variationSettings: FontVariation.Settings,
) {
    /**
     * Creates settings from Compose's standard weight and style controls.
     *
     * @param weight requested font weight
     * @param style requested upright or italic style
     */
    constructor(
        weight: FontWeight = FontWeight.Normal,
        style: FontStyle = FontStyle.Normal,
    ) : this(
        FontVariation.Settings(
            FontVariation.Setting("wght", weight.weight.toFloat()),
            FontVariation.italic(style.value.toFloat()),
        ),
    )

    /**
     * Returns a copy with the supplied font variations applied.
     *
     * A variation replaces an existing setting with the same four-character axis name. Settings
     * for other axes remain unchanged, and new axes are appended. When the same axis appears more
     * than once in [variations], the last value wins. This function does not check whether a font
     * supports the axes or values; use [SymbolFont.fontSettings] for descriptor-aware validation.
     * Calling it without variations returns this instance.
     *
     * @param variations settings to add or replace
     * @return these settings with [variations] applied
     */
    fun withVariations(
        vararg variations: FontVariation.Setting,
    ): SymbolFontSettings {
        if (variations.isEmpty()) return this

        val merged = (variationSettings.settings + variations)
            .associateBy(FontVariation.Setting::axisName)
            .values
            .toTypedArray()
        return SymbolFontSettings(FontVariation.Settings(*merged))
    }

    companion object {
        /** Settings for normal weight and upright style. */
        val Default: SymbolFontSettings = SymbolFontSettings()
    }
}

/**
 * Describes one value that callers can adjust on a [SymbolFont.Variable].
 *
 * This is metadata for validation and user-interface controls. It does not inspect or change the
 * font file. Values passed to [SymbolFont.fontSettings] must be within [minValue] and [maxValue];
 * an omitted value uses [defaultValue].
 *
 * @property tag four-character name stored in the font, such as `wght`
 * @property minValue smallest accepted value
 * @property defaultValue value used when the caller does not provide one
 * @property maxValue largest accepted value
 * @property label human-readable name suitable for a control label
 * @throws IllegalArgumentException if [tag] is not four printable ASCII characters, a numeric
 * value is not finite, the values are not ordered as minimum, default, maximum, or [label] is blank
 */
@Immutable
data class SymbolFontAxis(
    val tag: String,
    val minValue: Float,
    val defaultValue: Float,
    val maxValue: Float,
    val label: String = tag,
) {
    init {
        require(tag.length == 4 && tag.all { it.code in 0x20..0x7E }) {
            "tag must contain exactly four printable ASCII characters, but was '$tag'"
        }
        require(
            minValue.isFinite() &&
                defaultValue.isFinite() &&
                maxValue.isFinite() &&
                minValue <= defaultValue &&
                defaultValue <= maxValue,
        ) {
            "axis $tag must have finite min <= default <= max values"
        }
        require(label.isNotBlank()) { "axis $tag label must not be blank" }
    }
}

/**
 * A symbol font backed by one Compose Multiplatform font resource.
 *
 * Implement [SymbolFont.Variable] or [SymbolFont.Regular] to add a font descriptor.
 */
@Immutable
sealed interface SymbolFont {
    val familyName: String

    val resource: FontResource

    companion object {
        /**
         * Creates a descriptor for a font baked at one fixed setting.
         *
         * This function only stores the resource and metadata. The font is loaded later by
         * [rememberSymbolFontFamily] or [SymbolFontIcon]. A regular font can only be rendered with
         * the same [fontSettings]; requesting other settings fails instead of silently changing
         * its appearance.
         *
         * @param familyName name used in diagnostics and developer tools
         * @param resource Compose Multiplatform font resource to load when rendered
         * @param fontSettings fixed settings already baked into the font file
         * @return a reusable regular-font descriptor
         */
        fun regular(
            familyName: String,
            resource: FontResource,
            fontSettings: SymbolFontSettings = SymbolFontSettings.Default,
        ): Regular = RegularSymbolFont(familyName, resource, fontSettings)

        /**
         * Creates a descriptor for a font whose settings can change at runtime.
         *
         * The supplied [variationAxes] are copied and used by [SymbolFont.fontSettings] to fill in
         * defaults and validate caller values. This function does not load the resource or compare
         * the metadata with the axes actually stored in the font file.
         *
         * @param familyName name used in diagnostics and developer tools
         * @param resource Compose Multiplatform font resource to load when rendered
         * @param variationAxes adjustable values exposed to callers, in the order passed to Compose
         * @return a reusable variable-font descriptor
         * @throws IllegalArgumentException if more than one axis uses the same tag
         */
        fun variable(
            familyName: String,
            resource: FontResource,
            variationAxes: List<SymbolFontAxis>,
        ): Variable {
            require(variationAxes.map(SymbolFontAxis::tag).distinct().size == variationAxes.size) {
                "$familyName contains duplicate variation-axis tags"
            }
            return VariableSymbolFont(familyName, resource, variationAxes.toList())
        }
    }

    /** A [SymbolFont] whose OpenType variation coordinates can change at runtime. */
    @Immutable
    interface Variable : SymbolFont {
        /** Axis metadata embedded for UI discovery; empty when it is unavailable. */
        val variationAxes: List<SymbolFontAxis>
            get() = emptyList()

        /** Settings at the defaults embedded in this font. */
        val defaultFontSettings: SymbolFontSettings
            get() = fontSettings()
    }

    /** A non-variable [SymbolFont] baked at one fixed [fontSettings] point. */
    @Immutable
    interface Regular : SymbolFont {
        val fontSettings: SymbolFontSettings
            get() = SymbolFontSettings.Default
    }
}

/**
 * Creates settings for this font from values selected by the caller.
 *
 * For a variable font, omitted values use the defaults in [SymbolFont.Variable.variationAxes].
 * Every supplied tag must be known and every value must be finite and within its declared range.
 * For a regular font, [axisValues] must be empty and the font's fixed settings are returned.
 *
 * This function only creates settings. It does not load the font or check whether the current
 * platform can render variable fonts.
 *
 * @param axisValues values keyed by their four-character font tag
 * @return settings containing every declared variable axis, or the fixed regular-font settings
 * @throws IllegalArgumentException if the descriptor is both regular and variable, a regular font
 * receives an axis value, variable-axis metadata contains duplicate tags, a tag is unknown, or a
 * value is not finite or is outside its declared range
 */
fun SymbolFont.fontSettings(
    axisValues: Map<String, Float> = emptyMap(),
): SymbolFontSettings {
    require(this !is SymbolFont.Regular || this !is SymbolFont.Variable) {
        "$familyName cannot be both a regular and a variable font"
    }
    return when (this) {
        is SymbolFont.Regular -> {
            require(axisValues.isEmpty()) {
                "$familyName is a regular font and defines no variation axes"
            }
            fontSettings
        }

        is SymbolFont.Variable -> {
            val axesByTag = variationAxes.associateBy(SymbolFontAxis::tag)
            require(axesByTag.size == variationAxes.size) {
                "$familyName contains duplicate variation-axis tags"
            }
            val unknownTags = axisValues.keys - axesByTag.keys
            require(unknownTags.isEmpty()) {
                "$familyName does not define axes: " + unknownTags.sorted().joinToString()
            }
            axisValues.forEach { (tag, value) ->
                val axis = axesByTag.getValue(tag)
                require(value.isFinite() && value in axis.minValue..axis.maxValue) {
                    "$tag=$value is outside ${axis.minValue}..${axis.maxValue} in $familyName"
                }
            }
            SymbolFontSettings(
                FontVariation.Settings(
                    *variationAxes.map { axis ->
                        FontVariation.Setting(
                            axis.tag,
                            axisValues[axis.tag] ?: axis.defaultValue,
                        )
                    }.toTypedArray(),
                ),
            )
        }
    }
}

@Immutable
private data class RegularSymbolFont(
    override val familyName: String,
    override val resource: FontResource,
    override val fontSettings: SymbolFontSettings,
) : SymbolFont.Regular

@Immutable
private data class VariableSymbolFont(
    override val familyName: String,
    override val resource: FontResource,
    override val variationAxes: List<SymbolFontAxis>,
) : SymbolFont.Variable

/** Runtime capabilities relevant to variable symbol fonts. */
object SymbolsRuntime {
    /** Whether this platform can apply OpenType variation settings at runtime. */
    val variableFontsSupported: Boolean
        get() = platformSupportsVariableFonts()
}

/**
 * Requests [font] through Compose Resources and remembers a [FontFamily] for [fontSettings].
 *
 * Call this inside a composition when several icons should share one family, such as icons in a
 * list or grid. Android creates the packaged font synchronously. On non-Android targets, Compose
 * Resources may initially return a fallback while the resource loads, then recompose with the
 * requested font; a symbol glyph can therefore briefly appear missing. The family is reused while
 * Compose returns the same font. When loading completes or [fontSettings] changes, text using the
 * returned family can be measured, laid out, and drawn again. This runtime adds no separate cache
 * for past setting values.
 *
 * The default settings come from [SymbolsTheme.fontSettings]. A regular font accepts only the
 * settings baked into its descriptor. Variable fonts require Android API 26 or newer; use
 * [SymbolsRuntime.variableFontsSupported] to select a regular font or generated vector on older
 * Android versions.
 *
 * @param font regular or variable symbol-font descriptor to load
 * @param fontSettings settings to apply; defaults to the value supplied by [SymbolsTheme]
 * @return a remembered family containing the currently resolved font, which may be a temporary
 * fallback while a non-Android resource loads
 * @throws IllegalArgumentException if [font] is both regular and variable, or a regular font is
 * requested with settings other than its fixed settings
 * @throws UnsupportedOperationException if a variable font is requested on an unsupported platform
 */
@Composable
fun rememberSymbolFontFamily(
    font: SymbolFont,
    fontSettings: SymbolFontSettings = SymbolsTheme.fontSettings,
): FontFamily {
    val density = LocalDensity.current
    val variationSettings = symbolFontVariationSettings(
        font = font,
        fontSettings = fontSettings,
        variableFontsSupported = SymbolsRuntime.variableFontsSupported,
        density = density,
    )
    val loadedFont: Font = resourceFont(
        resource = font.resource,
        variationSettings = variationSettings ?: FontVariation.Settings(),
    )
    return remember(loadedFont) {
        FontFamily(loadedFont)
    }
}

internal fun symbolFontVariationSettings(
    font: SymbolFont,
    fontSettings: SymbolFontSettings,
    variableFontsSupported: Boolean,
    density: Density,
): FontVariation.Settings? {
    require(font !is SymbolFont.Regular || font !is SymbolFont.Variable) {
        "${font.familyName} cannot be both a regular and a variable font"
    }

    return when (font) {
        is SymbolFont.Regular -> {
            require(fontSettings.isEquivalentTo(font.fontSettings, density)) {
                "${font.familyName} is a regular font fixed at ${font.fontSettings}, " +
                    "but $fontSettings was requested"
            }
            null
        }

        is SymbolFont.Variable -> {
            if (!variableFontsSupported) {
                throw UnsupportedOperationException(
                    "Variable fonts require Android API 26 or newer. Use a " +
                        "SymbolFont.Regular or a generated vector/drawable on Android " +
                        "API 21–25.",
                )
            }
            fontSettings.variationSettings
        }
    }
}

private fun SymbolFontSettings.isEquivalentTo(
    other: SymbolFontSettings,
    density: Density,
): Boolean {
    val settingsByAxis = variationSettings.settings.associateBy(FontVariation.Setting::axisName)
    val otherSettingsByAxis =
        other.variationSettings.settings.associateBy(FontVariation.Setting::axisName)
    return settingsByAxis.keys == otherSettingsByAxis.keys &&
        settingsByAxis.all { (axisName, setting) ->
            setting.toVariationValue(density) ==
                otherSettingsByAxis.getValue(axisName).toVariationValue(density)
        }
}

internal expect fun platformSupportsVariableFonts(): Boolean
