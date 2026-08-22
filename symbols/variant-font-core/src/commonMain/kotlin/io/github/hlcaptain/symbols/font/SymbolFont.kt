package io.github.hlcaptain.symbols.font

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.Font as resourceFont

/** A reusable point in an arbitrary OpenType variable-font design space. */
@Immutable
data class SymbolFontSettings(
    val variationSettings: FontVariation.Settings,
) {
    /** Creates settings for Compose's standard weight and style axes. */
    constructor(
        weight: FontWeight = FontWeight.Normal,
        style: FontStyle = FontStyle.Normal,
    ) : this(FontVariation.Settings(weight, style))

    companion object {
        /** The normal, upright font instance. */
        val Default: SymbolFontSettings = SymbolFontSettings()
    }
}

/** One user-adjustable OpenType variation axis exposed by a [SymbolFont.Variable]. */
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
        /** Creates a regular symbol-font descriptor. */
        fun regular(
            familyName: String,
            resource: FontResource,
            fontSettings: SymbolFontSettings = SymbolFontSettings.Default,
        ): Regular = RegularSymbolFont(familyName, resource, fontSettings)

        /** Creates a variable symbol-font descriptor from generated axis metadata. */
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

/** Resolves this font at [axisValues], using embedded defaults when omitted. */
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

/** Loads and remembers [font] at [fontSettings]. */
@Composable
fun rememberSymbolFontFamily(
    font: SymbolFont,
    fontSettings: SymbolFontSettings = SymbolsTheme.fontSettings,
): FontFamily {
    val variationSettings = symbolFontVariationSettings(
        font = font,
        fontSettings = fontSettings,
        variableFontsSupported = SymbolsRuntime.variableFontsSupported,
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
): FontVariation.Settings? {
    require(font !is SymbolFont.Regular || font !is SymbolFont.Variable) {
        "${font.familyName} cannot be both a regular and a variable font"
    }

    return when (font) {
        is SymbolFont.Regular -> {
            require(fontSettings.isEquivalentTo(font.fontSettings)) {
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

private fun SymbolFontSettings.isEquivalentTo(other: SymbolFontSettings): Boolean =
    variationSettings.settings.associateBy { it.axisName } ==
        other.variationSettings.settings.associateBy { it.axisName }

internal expect fun platformSupportsVariableFonts(): Boolean
