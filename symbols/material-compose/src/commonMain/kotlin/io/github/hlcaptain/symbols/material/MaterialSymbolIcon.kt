package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.font.SymbolFont
import io.github.hlcaptain.symbols.font.SymbolFontIcon as BaseSymbolFontIcon

/** Renders [symbol] directly from a regular or variable Material font. */
@Composable
public fun MaterialSymbolIcon(
    symbol: MaterialSymbol,
    font: SymbolFont,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    axes: MaterialSymbolAxes = MaterialSymbolsTheme.axes,
    tint: Color = Color.Black,
    size: Dp = 24.dp,
    autoMirror: Boolean = false,
) {
    BaseSymbolFontIcon(
        codePoint = symbol.codePoint,
        font = font,
        contentDescription = contentDescription,
        modifier = modifier,
        fontSettings = axes.fontSettings,
        tint = tint,
        size = size,
        autoMirror = autoMirror,
    )
}

/** Renders [symbol] with a caller-owned [fontFamily]. */
@Composable
public fun MaterialSymbolIcon(
    symbol: MaterialSymbol,
    fontFamily: FontFamily,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    size: Dp = 24.dp,
    autoMirror: Boolean = false,
) {
    BaseSymbolFontIcon(
        codePoint = symbol.codePoint,
        fontFamily = fontFamily,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        size = size,
        autoMirror = autoMirror,
    )
}
