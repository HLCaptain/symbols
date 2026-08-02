package io.github.hlcaptain.symbols.material.sharp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.MaterialSymbolVariableFont
import io.github.hlcaptain.symbols.material.SymbolsTheme
import io.github.hlcaptain.symbols.material.SharpMaterialSymbol
import io.github.hlcaptain.symbols.material.sharp.resources.Res
import io.github.hlcaptain.symbols.material.sharp.resources.material_symbols_sharp_variable
import org.jetbrains.compose.resources.FontResource
import io.github.hlcaptain.symbols.material.MaterialSymbolIcon as BaseMaterialSymbolIcon

/**
 * The Material Symbols Sharp 2.874 variable font.
 *
 * This artifact contains exactly one font file. Its supported axes are `FILL`, `wght`,
 * `GRAD`, and `opsz`.
 */
public object MaterialSymbolsSharp : MaterialSymbolVariableFont {
    override val familyName: String = "Material Symbols Sharp"

    override val resource: FontResource
        get() = Res.font.material_symbols_sharp_variable
}

/**
 * Renders the style-typed [symbol] with the bundled Sharp variable font.
 *
 * Axes inherit from [SymbolsTheme] unless explicitly overridden.
 */
@Composable
public fun MaterialSymbolIcon(
    symbol: SharpMaterialSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    axes: MaterialSymbolAxes = SymbolsTheme.axes,
    tint: Color = Color.Black,
    size: Dp = 24.dp,
    autoMirror: Boolean = false,
) {
    BaseMaterialSymbolIcon(
        symbol = symbol.symbol,
        font = MaterialSymbolsSharp,
        contentDescription = contentDescription,
        modifier = modifier,
        axes = axes,
        tint = tint,
        size = size,
        autoMirror = autoMirror,
    )
}
