package io.github.hlcaptain.symbols.font

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.resolveAsTypeface
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal actual fun rememberNativeSymbolFont(font: SymbolFont): NativeSymbolFont? {
    val baseSettings = when (font) {
        is SymbolFont.Regular -> font.fontSettings
        is SymbolFont.Variable -> SymbolFontSettings(FontVariation.Settings())
    }
    // Compose shares this unchanging resource/typeface across icons. Animated coordinates never
    // enter its resource-font cache.
    val family = rememberSymbolFontFamily(font, baseSettings)
    val typeface = LocalFontFamilyResolver.current.resolveAsTypeface(
        fontFamily = family,
        fontSynthesis = FontSynthesis.None,
    ).value
    return remember(typeface) { AndroidNativeSymbolFont(typeface) }
}

private class AndroidNativeSymbolFont(private val baseTypeface: Typeface) : NativeSymbolFont {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = baseTypeface }
    private var lastVariations: String? = null
    private var lastText: String? = null
    private var advance = 0f
    private var ascent = 0
    private var descent = 0
    private var sharedTypeface: SharedAndroidTypefaces.Entry? = null

    override fun draw(
        scope: DrawScope,
        text: String,
        fontSize: Float,
        settings: FontVariation.Settings?,
        tint: Color,
    ) {
        val variations = settings?.settings?.joinToString(",") {
            "'${it.axisName}' ${it.toVariationValue(scope)}"
        }
        var metricsChanged = false
        if (variations != lastVariations) {
            paint.typeface = if (Build.VERSION.SDK_INT >= 26 && variations != null) {
                val shared = sharedTypeface ?: SharedAndroidTypefaces.acquire(baseTypeface).also {
                    sharedTypeface = it
                }
                shared.resolve(variations)
            } else {
                baseTypeface
            }
            lastVariations = variations
            metricsChanged = true
        }
        if (paint.textSize != fontSize) {
            paint.textSize = fontSize
            metricsChanged = true
        }
        if (metricsChanged || text != lastText) {
            advance = if (Build.VERSION.SDK_INT >= 23) {
                paint.getRunAdvance(text, 0, text.length, 0, text.length, false, text.length)
            } else {
                paint.measureText(text)
            }
            val metrics = paint.fontMetricsInt
            ascent = metrics.ascent
            descent = metrics.descent
            lastText = text
        }
        paint.color = tint.takeOrElse { Color.Black }.toArgb()
        // BasicText centers its integer-sized, unpadded single-line paragraph in the icon box.
        val width = ceil(advance)
        val height = (descent - ascent).toFloat()
        val measuredWidth = width.coerceAtMost(scope.size.width)
        val measuredHeight = height.coerceAtMost(scope.size.height)
        val left = ((scope.size.width - measuredWidth) / 2f).roundToInt().toFloat()
        val top = ((scope.size.height - measuredHeight) / 2f).roundToInt().toFloat()
        // Android Layout.ALIGN_CENTER uses an even integer run extent. measureText() rounds
        // advances up and loses the fractional value needed to reproduce that alignment.
        val x = left + ((width.toInt() - (advance.toInt() and -2)) / 2)
        val baseline = top - ascent
        val canvas = scope.drawContext.canvas.nativeCanvas
        val clips = measuredWidth < width || measuredHeight < height
        if (clips) {
            canvas.save()
            canvas.clipRect(left, top, left + measuredWidth, top + measuredHeight)
        }
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                canvas.drawTextRun(text, 0, text.length, 0, text.length, x, baseline, false, paint)
            } else {
                canvas.drawText(text, x, baseline, paint)
            }
        } finally {
            if (clips) canvas.restore()
        }
    }

    override fun close() {
        sharedTypeface?.let { SharedAndroidTypefaces.release(baseTypeface, it) }
        sharedTypeface = null
    }
}

/** Android compositions/draws run on the main thread; only mounted renderers hold leases. */
private object SharedAndroidTypefaces {
    class Entry(private val base: Typeface) {
        var references = 0
        private val paint = Paint().apply { typeface = base }
        // Independent layer invalidations can draw icons out of order.
        // ponytail: share 16 recent styles; tune if larger concurrent style sets cause churn.
        private val variationsCache = LruCache<String, Typeface>(16)

        @RequiresApi(26)
        fun resolve(variations: String): Typeface {
            variationsCache.get(variations)?.let { return it }
            paint.typeface = base
            if (!paint.setFontVariationSettings(variations)) {
                // An unsupported axis leaves Paint's old settings string intact. Clear it so
                // returning to that old setting actually reapplies it to the base typeface.
                paint.setFontVariationSettings(null)
                paint.typeface = base
            }
            return paint.typeface.also { variationsCache.put(variations, it) }
        }
    }

    // At most 16 recent variations per mounted font; release the cache with its last renderer.
    private val entries = mutableMapOf<Typeface, Entry>()

    fun acquire(base: Typeface): Entry = entries.getOrPut(base) { Entry(base) }.also {
        it.references++
    }

    fun release(base: Typeface, entry: Entry) {
        if (--entry.references == 0) entries.remove(base)
    }
}
