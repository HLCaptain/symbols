package io.github.hlcaptain.symbols.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Renders [symbol] directly from a regular or variable font resource.
 *
 * The underlying private-use Unicode text is always removed from semantics. When
 * [contentDescription] is non-null, that localized description is exposed with image semantics.
 */
@Composable
public fun MaterialSymbolIcon(
    symbol: MaterialSymbol,
    font: MaterialSymbolFont,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    axes: MaterialSymbolAxes = SymbolsTheme.axes,
    tint: Color = Color.Black,
    size: Dp = 24.dp,
    autoMirror: Boolean = false,
) {
    val fontFamily = rememberMaterialSymbolFontFamily(font, axes)
    MaterialSymbolIcon(
        symbol = symbol,
        fontFamily = fontFamily,
        contentDescription = contentDescription,
        modifier = modifier,
        axes = axes,
        tint = tint,
        size = size,
        autoMirror = autoMirror,
    )
}

/**
 * Renders a custom/private-use [codePoint] from [font].
 *
 * This overload supports glyphs that are intentionally outside the generated
 * Material Symbols catalog while retaining the same axis and accessibility
 * behavior.
 */
@Composable
public fun MaterialSymbolIcon(
    codePoint: Int,
    font: MaterialSymbolFont,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    axes: MaterialSymbolAxes = SymbolsTheme.axes,
    tint: Color = Color.Black,
    size: Dp = 24.dp,
    autoMirror: Boolean = false,
) {
    val fontFamily = rememberMaterialSymbolFontFamily(font, axes)
    MaterialSymbolIcon(
        codePoint = codePoint,
        fontFamily = fontFamily,
        contentDescription = contentDescription,
        modifier = modifier,
        axes = axes,
        tint = tint,
        size = size,
        autoMirror = autoMirror,
    )
}

/**
 * Renders [symbol] with a caller-owned [fontFamily].
 *
 * Use this overload for lists or grids: create the family once with
 * [rememberMaterialSymbolFontFamily], then share it across every icon. [axes]
 * must be the same value used to create [fontFamily].
 */
@Composable
public fun MaterialSymbolIcon(
    symbol: MaterialSymbol,
    fontFamily: FontFamily,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    axes: MaterialSymbolAxes = SymbolsTheme.axes,
    tint: Color = Color.Black,
    size: Dp = 24.dp,
    autoMirror: Boolean = false,
) {
    MaterialSymbolGlyph(
        text = symbol.text,
        fontFamily = fontFamily,
        contentDescription = contentDescription,
        modifier = modifier,
        axes = axes,
        tint = tint,
        size = size,
        autoMirror = autoMirror,
    )
}

/**
 * Renders a custom/private-use [codePoint] with a shared [fontFamily].
 *
 * [axes] must be the same value used to create [fontFamily].
 */
@Composable
public fun MaterialSymbolIcon(
    codePoint: Int,
    fontFamily: FontFamily,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    axes: MaterialSymbolAxes = SymbolsTheme.axes,
    tint: Color = Color.Black,
    size: Dp = 24.dp,
    autoMirror: Boolean = false,
) {
    MaterialSymbolGlyph(
        text = materialSymbolText(codePoint),
        fontFamily = fontFamily,
        contentDescription = contentDescription,
        modifier = modifier,
        axes = axes,
        tint = tint,
        size = size,
        autoMirror = autoMirror,
    )
}

@Composable
private fun MaterialSymbolGlyph(
    text: String,
    fontFamily: FontFamily,
    contentDescription: String?,
    modifier: Modifier,
    axes: MaterialSymbolAxes,
    tint: Color,
    size: Dp,
    autoMirror: Boolean,
) {
    require(size >= 0.dp) {
        "size must not be negative, but was $size"
    }

    val fontSize = with(LocalDensity.current) { size.toSp() }
    val shouldMirror =
        autoMirror && LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                    role = Role.Image
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            modifier = if (shouldMirror) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier,
            style = TextStyle(
                color = tint,
                fontSize = fontSize,
                fontFamily = fontFamily,
                fontWeight = axes.fontWeight,
                fontSynthesis = FontSynthesis.None,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}
