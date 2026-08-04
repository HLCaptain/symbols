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
public data class SymbolFontSettings(
    public val variationSettings: FontVariation.Settings,
) {
    /** Creates settings for Compose's standard weight and style axes. */
    public constructor(
        weight: FontWeight = FontWeight.Normal,
        style: FontStyle = FontStyle.Normal,
    ) : this(FontVariation.Settings(weight, style))

    public companion object {
        /** The normal, upright font instance. */
        public val Default: SymbolFontSettings = SymbolFontSettings()
    }
}

/** A symbol font backed by one Compose Multiplatform font resource. */
@Immutable
public interface SymbolFont {
    public val familyName: String

    public val resource: FontResource
}

/** A [SymbolFont] whose OpenType variation coordinates can change at runtime. */
@Immutable
public interface SymbolVariableFont : SymbolFont

/** A non-variable [SymbolFont] baked at one fixed [fontSettings] point. */
@Immutable
public interface SymbolRegularFont : SymbolFont {
    public val fontSettings: SymbolFontSettings
        get() = SymbolFontSettings.Default
}

/** Runtime capabilities relevant to variable symbol fonts. */
public object SymbolsRuntime {
    /** Whether this platform can apply OpenType variation settings at runtime. */
    public val variableFontsSupported: Boolean
        get() = platformSupportsVariableFonts()
}

/** Loads and remembers [font] at [fontSettings]. */
@Composable
public fun rememberSymbolFontFamily(
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
    require(font is SymbolRegularFont || font is SymbolVariableFont) {
        "${font.familyName} must implement SymbolRegularFont or SymbolVariableFont"
    }
    require(font !is SymbolRegularFont || font !is SymbolVariableFont) {
        "${font.familyName} cannot be both a regular and a variable font"
    }

    if (font is SymbolRegularFont) {
        require(fontSettings.isEquivalentTo(font.fontSettings)) {
            "${font.familyName} is a regular font fixed at ${font.fontSettings}, " +
                "but $fontSettings was requested"
        }
        return null
    }

    if (!variableFontsSupported) {
        throw UnsupportedOperationException(
            "Variable fonts require Android API 26 or newer. Use a " +
                "SymbolRegularFont or a generated vector/drawable on Android " +
                "API 21–25.",
        )
    }

    return fontSettings.variationSettings
}

private fun SymbolFontSettings.isEquivalentTo(other: SymbolFontSettings): Boolean =
    variationSettings.settings.associateBy { it.axisName } ==
        other.variationSettings.settings.associateBy { it.axisName }

internal expect fun platformSupportsVariableFonts(): Boolean
