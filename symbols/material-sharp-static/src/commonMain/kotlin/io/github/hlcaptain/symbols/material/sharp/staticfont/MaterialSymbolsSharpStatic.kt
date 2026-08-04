package io.github.hlcaptain.symbols.material.sharp.staticfont

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.font.SymbolRegularFont
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.SharpMaterialSymbol
import io.github.hlcaptain.symbols.material.sharp.staticfont.resources.Res
import io.github.hlcaptain.symbols.material.sharp.staticfont.resources.material_symbols_sharp_regular
import org.jetbrains.compose.resources.FontResource
import io.github.hlcaptain.symbols.material.MaterialSymbolIcon as BaseMaterialSymbolIcon

/**
 * A static Material Symbols Sharp 2.874 font at the default axis point.
 *
 * This non-variable font works on Android API 21. Its default-axis point is
 * fixed; requesting different axes fails with a clear error.
 */
public object MaterialSymbolsSharpStatic : SymbolRegularFont {
    override val familyName: String = "Material Symbols Sharp"

    override val resource: FontResource
        get() = Res.font.material_symbols_sharp_regular

    public val axes: MaterialSymbolAxes = MaterialSymbolAxes.Default

    override val fontSettings: SymbolFontSettings = axes.fontSettings
}

/** Renders [symbol] with the bundled API-21-compatible static Sharp font. */
@Composable
public fun MaterialSymbolIcon(
    symbol: SharpMaterialSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    axes: MaterialSymbolAxes = MaterialSymbolsSharpStatic.axes,
    tint: Color = Color.Black,
    size: Dp = 24.dp,
    autoMirror: Boolean = false,
) {
    BaseMaterialSymbolIcon(
        symbol = symbol.symbol,
        font = MaterialSymbolsSharpStatic,
        contentDescription = contentDescription,
        modifier = modifier,
        axes = axes,
        tint = tint,
        size = size,
        autoMirror = autoMirror,
    )
}
