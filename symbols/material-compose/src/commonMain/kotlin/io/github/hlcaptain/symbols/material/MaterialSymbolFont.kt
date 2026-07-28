package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

/**
 * A single Material Symbols style backed by exactly one variable-font resource.
 */
@Immutable
public interface MaterialSymbolFont {
    public val familyName: String

    public val resource: FontResource
}

/**
 * Loads and remembers a Material Symbols font family at [axes].
 */
@Composable
public fun rememberMaterialSymbolFontFamily(
    font: MaterialSymbolFont,
    axes: MaterialSymbolAxes = MaterialSymbolAxes(),
): FontFamily {
    val resourceFont = Font(
        resource = font.resource,
        weight = axes.fontWeight,
        variationSettings = axes.variationSettings(),
    )
    return remember(resourceFont) {
        FontFamily(resourceFont)
    }
}
