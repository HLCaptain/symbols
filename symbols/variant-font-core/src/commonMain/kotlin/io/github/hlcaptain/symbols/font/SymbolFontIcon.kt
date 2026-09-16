package io.github.hlcaptain.symbols.font

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
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
 * All descriptor overloads share the native renderer. The font is loaded independently of
 * [fontSettings]; changing settings updates native metrics and drawing without remeasuring the
 * icon's square. Reading animated values before this call still recomposes the caller; use the
 * settings and tint producers to defer those reads to drawing. On asynchronous platforms the
 * square remains empty until the font is loaded.
 *
 * The icon occupies a square of [size]. Its tint defaults to black rather than a Material theme
 * color. [autoMirror] flips the glyph horizontally only in a right-to-left layout.
 *
 * The glyph text is not exposed to accessibility services. A non-null
 * [contentDescription] is exposed as the description of an image; `null` makes the icon
 * decorative.
 *
 * [graphicsLayer] optionally transforms the whole modified icon. Its state reads update layer
 * properties without recomposition or layout. The component defaults to
 * [CompositingStrategy.ModulateAlpha], which applies alpha to each drawing operation and avoids
 * the automatic alpha offscreen buffer for a single glyph. Overlapping drawing added by
 * [modifier] follows these per-operation alpha semantics; the block can explicitly override the
 * strategy when needed. A null block creates no layer. Layers supplied separately in [modifier]
 * retain their own compositing behavior.
 *
 * @param codePoint Unicode scalar value assigned to the glyph
 * @param font font descriptor and Compose resource to render
 * @param contentDescription localized description, or `null` for a decorative icon
 * @param modifier modifier applied to the icon's square container
 * @param fontSettings settings applied to [font]; defaults to the current [SymbolsTheme]
 * @param tint glyph color; defaults to [Color.Black]
 * @param size width and height of the icon
 * @param autoMirror whether to mirror the glyph in right-to-left layouts
 * @param graphicsLayer optional layer properties around the entire modified icon
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
    graphicsLayer: (GraphicsLayerScope.() -> Unit)? = null,
) {
    SymbolFontIcon(
        codePoint = codePoint,
        font = font,
        contentDescription = contentDescription,
        modifier = modifier,
        fontSettings = { fontSettings },
        tint = { tint },
        size = size,
        autoMirror = autoMirror,
        graphicsLayer = graphicsLayer,
    )
}

/**
 * Renders animated font settings without observing them in composition or layout.
 *
 * Pass animation state inside [fontSettings], for example
 * `fontSettings = { font.fontSettings(mapOf("wght" to weight.value)) }`.
 * The producer is non-composable, must be free of side effects, and may run more than once.
 * Reading animation state before calling this function still recomposes that caller.
 *
 * The native font is loaded independently of the animated settings. Each changed setting can
 * still update native font metrics, shape, and rasterize the glyph, but does not remeasure the
 * icon's square. On asynchronous platforms the square remains empty until its font is loaded.
 * The value overload uses this same renderer; the [FontFamily] overload adapts Compose text.
 *
 * Use the [ColorProducer] tint overload to defer animated color reads as well. Actual [size]
 * changes still require composition and layout; use [graphicsLayer] for visual scaling.
 * The optional component-owned layer surrounds the entire modified icon and defaults to
 * [CompositingStrategy.ModulateAlpha]. Its block is evaluated in the layer phase, and a null
 * block creates no layer.
 * Mirroring, accessibility, and font-setting validation follow the other overloads.
 * A variable font requires Android API 26 or newer; regular fonts also support API 21–25.
 */
@Composable
fun SymbolFontIcon(
    codePoint: Int,
    font: SymbolFont,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fontSettings: () -> SymbolFontSettings,
    tint: Color = Color.Black,
    size: Dp = 24.dp,
    autoMirror: Boolean = false,
    graphicsLayer: (GraphicsLayerScope.() -> Unit)? = null,
) {
    SymbolFontIcon(
        codePoint = codePoint,
        font = font,
        contentDescription = contentDescription,
        modifier = modifier,
        fontSettings = fontSettings,
        tint = { tint },
        size = size,
        autoMirror = autoMirror,
        graphicsLayer = graphicsLayer,
    )
}

/**
 * Renders animated font settings and tint with state reads deferred until drawing.
 *
 * For example, pass `tint = { animatedColor.value }` and keep [size] fixed. Both producers
 * must be free of side effects and may run more than once. Reading their state in the caller
 * still recomposes that caller. Font loading, geometry, and semantics match the native
 * settings-producer overload with a [Color] tint.
 *
 * [graphicsLayer] wraps the whole modified icon and defaults to
 * [CompositingStrategy.ModulateAlpha]. Read animated transforms inside this block to update only
 * layer properties. The block can override the strategy for overlapping drawing; null creates
 * no layer. Layers in [modifier] retain their own compositing behavior.
 */
@Composable
fun SymbolFontIcon(
    codePoint: Int,
    font: SymbolFont,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fontSettings: () -> SymbolFontSettings,
    tint: ColorProducer,
    size: Dp = 24.dp,
    autoMirror: Boolean = false,
    graphicsLayer: (GraphicsLayerScope.() -> Unit)? = null,
) {
    require(size >= 0.dp) { "size must not be negative, but was $size" }
    val text = remember(codePoint) { symbolFontText(codePoint) }
    val nativeFont = rememberNativeSymbolFont(font)
    val drawState = remember { SymbolFontDrawState() }
    DisposableEffect(nativeFont) {
        onDispose { nativeFont?.close() }
    }
    SideEffect {
        if (graphicsLayer == null && nativeFont?.updateLayerProperties(null) == true) {
            drawState.invalidate()
        }
    }
    val layer = if (graphicsLayer == null) Modifier else Modifier.graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        graphicsLayer.invoke(this)
        if (nativeFont?.updateLayerProperties(this) == true) drawState.invalidate()
    }
    Box(
        modifier = layer.then(modifier)
            .size(size)
            .clearAndSetSemantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                    role = Role.Image
                }
            }
            .symbolFontDraw(drawState) {
                val settings = symbolFontVariationSettings(
                    font, fontSettings(), SymbolsRuntime.variableFontsSupported, this,
                )
                val mirror = autoMirror && layoutDirection == LayoutDirection.Rtl
                scale(scaleX = if (mirror) -1f else 1f, scaleY = 1f) {
                    nativeFont?.draw(this, text, size.toPx(), settings, tint())
                }
            },
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
