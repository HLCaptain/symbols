package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.Font as resourceFont

/**
 * A single Material Symbols style backed by one font resource.
 *
 * This was the original variable-font contract. Implementations that only
 * implement this interface continue to be treated as variable fonts for source
 * compatibility. New implementations should also implement either
 * [MaterialSymbolVariableFont] or [MaterialSymbolRegularFont] to state their
 * capabilities explicitly.
 */
@Immutable
public interface MaterialSymbolFont {
    public val familyName: String

    public val resource: FontResource
}

/**
 * An explicitly variable Material Symbols font.
 *
 * On Android, direct variable-font rendering requires API 26 or newer.
 */
@Immutable
public interface MaterialSymbolVariableFont : MaterialSymbolFont

/**
 * A non-variable Material Symbols font baked at one fixed [axes] point.
 *
 * The renderer never attaches [FontVariation.Settings] to this font. Requesting
 * axes other than [axes] fails instead of pretending that a regular font can
 * vary. The default represents the standard Material Symbols instance.
 */
@Immutable
public interface MaterialSymbolRegularFont : MaterialSymbolFont {
    public val axes: MaterialSymbolAxes
        get() = MaterialSymbolAxes.Default
}

/**
 * Runtime capabilities relevant to font-backed Material Symbols.
 */
public object MaterialSymbolsRuntime {
    /**
     * Whether this platform runtime can render a variable Material Symbols font.
     *
     * This is `false` on Android API 21–25 and `true` on Android API 26 or
     * newer. It is also `true` on the currently supported non-Android targets.
     * Apps can use this before choosing a variable-font, regular-font, or
     * generated vector/drawable path.
     */
    public val variableFontsSupported: Boolean
        get() = platformSupportsMaterialSymbolVariableFonts()
}

/**
 * Loads and remembers a Material Symbols font family at [axes].
 *
 * [MaterialSymbolRegularFont] uses the non-variable Compose font overload and
 * therefore works on Android API 21. Variable fonts are rejected on Android
 * API 21–25 instead of silently rendering at an unrelated font instance.
 */
@Composable
public fun rememberMaterialSymbolFontFamily(
    font: MaterialSymbolFont,
    axes: MaterialSymbolAxes = MaterialSymbolsTheme.axes,
): FontFamily {
    val variationSettings = materialSymbolVariationSettings(
        font = font,
        axes = axes,
        variableFontsSupported = MaterialSymbolsRuntime.variableFontsSupported,
    )
    val resourceFont: Font = if (variationSettings == null) {
        resourceFont(
            resource = font.resource,
            weight = axes.fontWeight,
        )
    } else {
        resourceFont(
            resource = font.resource,
            weight = axes.fontWeight,
            variationSettings = variationSettings,
        )
    }
    return remember(resourceFont) {
        FontFamily(resourceFont)
    }
}

internal fun materialSymbolVariationSettings(
    font: MaterialSymbolFont,
    axes: MaterialSymbolAxes,
    variableFontsSupported: Boolean,
): FontVariation.Settings? {
    require(font !is MaterialSymbolRegularFont || font !is MaterialSymbolVariableFont) {
        "${font.familyName} cannot be both a regular and a variable font"
    }

    if (font is MaterialSymbolRegularFont) {
        require(axes == font.axes) {
            "${font.familyName} is a regular font fixed at ${font.axes}, " +
                "but $axes was requested"
        }
        return null
    }

    if (!variableFontsSupported) {
        throw UnsupportedOperationException(
            "Variable Material Symbols fonts require Android API 26 or newer. " +
                "Use a MaterialSymbolRegularFont or a generated vector/drawable " +
                "on Android API 21–25.",
        )
    }

    return axes.variationSettings()
}

internal expect fun platformSupportsMaterialSymbolVariableFonts(): Boolean
