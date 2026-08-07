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

    /** A [SymbolFont] whose OpenType variation coordinates can change at runtime. */
    @Immutable
    interface Variable : SymbolFont {
        /** Axis metadata embedded for UI discovery; empty when it is unavailable. */
        val variationAxes: List<SymbolFontAxis>
            get() = emptyList()
    }

    /** A non-variable [SymbolFont] baked at one fixed [fontSettings] point. */
    @Immutable
    interface Regular : SymbolFont {
        val fontSettings: SymbolFontSettings
            get() = SymbolFontSettings.Default
    }
}

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
