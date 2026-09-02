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

/**
 * Renders one glyph from a regular or variable symbol font.
 *
 * This overload resolves and remembers the [font] at [fontSettings]. Changing the settings can
 * resolve a new font family and cause the glyph to be measured, laid out, and drawn again. When
 * many icons use the same family, call [rememberSymbolFontFamily] once and use the [FontFamily]
 * overload instead.
 *
 * The icon occupies a square of [size]. Its tint defaults to black rather than a Material theme
 * color. [autoMirror] flips the glyph horizontally only in a right-to-left layout.
 *
 * The glyph text is not exposed to accessibility services. A non-null
 * [contentDescription] is exposed as the description of an image; `null` makes the icon
 * decorative.
 *
 * @param codePoint Unicode scalar value assigned to the glyph
 * @param font font descriptor and Compose resource to render
 * @param contentDescription localized description, or `null` for a decorative icon
 * @param modifier modifier applied to the icon's square container
 * @param fontSettings settings applied to [font]; defaults to the current [SymbolsTheme]
 * @param tint glyph color; defaults to [Color.Black]
 * @param size width and height of the icon
 * @param autoMirror whether to mirror the glyph in right-to-left layouts
 * @throws IllegalArgumentException if [codePoint] is not a Unicode scalar value, [size] is
 * negative or unspecified, [font] is both regular and variable, or a regular
 * font receives different settings
 * @throws UnsupportedOperationException if a variable font is used on an unsupported platform
 */
@Composable
fun SymbolFontIcon(
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

/**
 * Renders one glyph with a [fontFamily] prepared by the caller.
 *
 * Use this overload when several icons share the result of [rememberSymbolFontFamily]. It does not
 * read [SymbolsTheme], load a [SymbolFont], or apply variation settings; the supplied family fully
 * determines the font appearance.
 *
 * The icon occupies a square of [size]. Its tint defaults to black rather than a Material theme
 * color. [autoMirror] flips the glyph horizontally only in a right-to-left layout.
 *
 * The glyph text is not exposed to accessibility services. A non-null
 * [contentDescription] is exposed as the description of an image; `null` makes the icon
 * decorative.
 *
 * @param codePoint Unicode scalar value assigned to the glyph
 * @param fontFamily caller-owned family containing the symbol font
 * @param contentDescription localized description, or `null` for a decorative icon
 * @param modifier modifier applied to the icon's square container
 * @param tint glyph color; defaults to [Color.Black]
 * @param size width and height of the icon
 * @param autoMirror whether to mirror the glyph in right-to-left layouts
 * @throws IllegalArgumentException if [codePoint] is not a Unicode scalar value
 * or [size] is negative or unspecified
 */
@Composable
fun SymbolFontIcon(
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

/**
 * Encodes one Unicode scalar value as text that can be rendered by a symbol font.
 *
 * Values in the Basic Multilingual Plane produce one UTF-16 character. Supplementary values
 * produce a surrogate pair. This function does not load a font or check whether a font contains
 * the requested glyph.
 *
 * @param codePoint Unicode scalar value to encode, including private-use values
 * @return a one-code-point string suitable for a text renderer
 * @throws IllegalArgumentException if [codePoint] is outside the Unicode range or is a surrogate
 * code point
 */
fun symbolFontText(codePoint: Int): String {
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
