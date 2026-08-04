package io.github.hlcaptain.symbols.font

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

/** Renders [codePoint] from a regular or variable [font]. */
@Composable
public fun SymbolFontIcon(
    codePoint: Int,
    font: SymbolFont,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fontSettings: SymbolFontSettings = SymbolsTheme.fontSettings,
    tint: Color = Color.Black,
    size: Dp = 24.dp,
    autoMirror: Boolean = false,
) {
    val fontFamily = rememberSymbolFontFamily(font, fontSettings)
    SymbolFontIcon(
        codePoint = codePoint,
        fontFamily = fontFamily,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        size = size,
        autoMirror = autoMirror,
    )
}

/** Renders [codePoint] with a caller-owned [fontFamily]. */
@Composable
public fun SymbolFontIcon(
    codePoint: Int,
    fontFamily: FontFamily,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    size: Dp = 24.dp,
    autoMirror: Boolean = false,
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
            text = symbolFontText(codePoint),
            modifier = if (shouldMirror) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier,
            style = TextStyle(
                color = tint,
                fontSize = fontSize,
                fontFamily = fontFamily,
                fontSynthesis = FontSynthesis.None,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Encodes one Unicode scalar value as symbol-font text. */
public fun symbolFontText(codePoint: Int): String {
    require(
        codePoint in 0..0x10FFFF &&
            codePoint !in 0xD800..0xDFFF
    ) {
        "Not a Unicode scalar value: U+${codePoint.toString(16).uppercase()}"
    }

    if (codePoint <= 0xFFFF) {
        return codePoint.toChar().toString()
    }

    val supplementary = codePoint - 0x10000
    val highSurrogate = 0xD800 + (supplementary ushr 10)
    val lowSurrogate = 0xDC00 + (supplementary and 0x3FF)
    return charArrayOf(highSurrogate.toChar(), lowSurrogate.toChar()).concatToString()
}
