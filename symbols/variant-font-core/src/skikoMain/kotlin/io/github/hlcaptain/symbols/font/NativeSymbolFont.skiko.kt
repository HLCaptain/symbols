package io.github.hlcaptain.symbols.font

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.intl.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.rememberResourceEnvironment
import org.jetbrains.skia.Data
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontEdging
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Point
import org.jetbrains.skia.TextBlob
import org.jetbrains.skia.TextBlobBuilder
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.impl.use
import org.jetbrains.skia.shaper.HbIcuScriptRunIterator
import org.jetbrains.skia.shaper.IcuBidiRunIterator
import org.jetbrains.skia.shaper.RunHandler
import org.jetbrains.skia.shaper.RunInfo
import org.jetbrains.skia.shaper.Shaper
import org.jetbrains.skia.shaper.ShapingOptions
import org.jetbrains.skia.shaper.TrivialFontRunIterator
import org.jetbrains.skia.shaper.TrivialLanguageRunIterator
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.jetbrains.skia.FontHinting as SkiaFontHinting
import org.jetbrains.skia.FontVariation as SkiaFontVariation

@Composable
internal actual fun rememberNativeSymbolFont(font: SymbolFont): NativeSymbolFont? {
    val environment = rememberResourceEnvironment()
    return key(font.resource, environment) {
        val renderer by produceState<NativeSymbolFont?>(null) {
            val loaded = SharedSymbolTypefaces.acquire(environment, font.resource)
            value = loaded
            // Also owns a result published before the parent's disposal effect is installed.
            awaitDispose { loaded.close() }
        }
        renderer
    }
}

/** Shared by mounted icons; no cache of past animation coordinates or unloaded resources. */
private object SharedSymbolTypefaces {
    private data class Key(val environment: ResourceEnvironment, val resource: FontResource)

    private class Entry(val scope: CoroutineScope) {
        var references = 0
        var typeface: Typeface? = null
        lateinit var loading: Deferred<Typeface>
    }

    private val mutex = Mutex()
    private val entries = mutableMapOf<Key, Entry>()

    suspend fun acquire(environment: ResourceEnvironment, resource: FontResource): NativeSymbolFont {
        val key = Key(environment, resource)
        val entry = mutex.withLock {
            entries.getOrPut(key) {
                // Loading belongs to all leases, independently of the first icon's scene dispatcher.
                Entry(CoroutineScope(Dispatchers.Default + SupervisorJob())).also { entry ->
                    entry.loading = entry.scope.async {
                        val bytes = getFontResourceBytes(environment, resource)
                        currentCoroutineContext().ensureActive()
                        val typeface = Data.makeFromBytes(bytes).use { data ->
                            checkNotNull(FontMgr.default.makeFromData(data)) {
                                "Unable to load symbol font resource: $resource"
                            }
                        }
                        try {
                            mutex.withLock { entry.typeface = typeface }
                            typeface
                        } catch (failure: Throwable) {
                            typeface.close()
                            throw failure
                        }
                    }
                }
            }.also { it.references++ }
        }
        try {
            val typeface = entry.loading.await()
            return SkiaNativeSymbolFont(typeface) { release(key, entry) }
        } catch (failure: Throwable) {
            release(key, entry)
            throw failure
        }
    }

    private fun release(key: Key, entry: Entry) {
        entry.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                if (--entry.references == 0) {
                    entries.remove(key)
                    entry.scope.cancel()
                    entry.typeface?.close()
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
internal class SkiaNativeSymbolFont(
    private val baseTypeface: Typeface,
    private val releaseBase: () -> Unit,
) : NativeSymbolFont {
    private val paint = Paint().apply { isAntiAlias = true }
    private val font = Font(baseTypeface).apply {
        val rasterization = FontRasterizationSettings.PlatformDefault
        isSubpixel = rasterization.subpixelPositioning
        isAutoHintingForced = rasterization.autoHintingForced
        edging = when (rasterization.smoothing) {
            FontSmoothing.None -> FontEdging.ALIAS
            FontSmoothing.AntiAlias -> FontEdging.ANTI_ALIAS
            FontSmoothing.SubpixelAntiAlias -> FontEdging.SUBPIXEL_ANTI_ALIAS
        }
        hinting = when (rasterization.hinting) {
            FontHinting.None -> SkiaFontHinting.NONE
            FontHinting.Slight -> SkiaFontHinting.SLIGHT
            FontHinting.Normal -> SkiaFontHinting.NORMAL
            FontHinting.Full -> SkiaFontHinting.FULL
        }
    }
    private val shaper = Shaper.makeShapeDontWrapOrReorder(FontMgr.default)
    private val language = Locale.current.toLanguageTag()
    private var variedTypeface: Typeface? = null
    private var blob: TextBlob? = null
    private var previousSettings: FontVariation.Settings? = null
    private var previousAxisValues = FloatArray(0)
    private var previousText: String? = null
    private var previousFontSize = Float.NaN
    private var width = 0f
    private var height = 0f
    private var ascent = 0f
    private var closed = false
    private var ownedLayerAlpha = 1f

    override fun updateLayerProperties(scope: GraphicsLayerScope?): Boolean {
        // Match SkiaGraphicsLayer's offscreen conditions: these effects already apply layer alpha
        // when compositing, so baking it into the glyph as well would apply opacity twice.
        val alpha = if (
            scope != null && scope.compositingStrategy == CompositingStrategy.ModulateAlpha &&
            scope.renderEffect == null && scope.colorFilter == null && scope.blendMode == BlendMode.SrcOver
        ) {
            scope.alpha.coerceIn(0f, 1f)
        } else {
            1f
        }
        if (ownedLayerAlpha == alpha) return false
        ownedLayerAlpha = alpha
        return true
    }

    override fun draw(
        scope: DrawScope,
        text: String,
        fontSize: Float,
        settings: FontVariation.Settings?,
        tint: Color,
    ) {
        check(!closed) { "Symbol font renderer is closed" }
        if (fontSize == 0f) return

        val variations = settings?.settings.orEmpty()
        val previousVariations = previousSettings?.settings.orEmpty()
        val settingsChanged = variations.size != previousVariations.size ||
            variations.indices.any { index ->
                variations[index].axisName != previousVariations[index].axisName ||
                    variations[index].toVariationValue(scope) != previousAxisValues[index]
            }
        val axisValues = if (settingsChanged) {
            FloatArray(variations.size) { variations[it].toVariationValue(scope) }
        } else {
            previousAxisValues
        }
        if (settingsChanged) {
            val nextTypeface = variations.takeIf { it.isNotEmpty() }?.let {
                baseTypeface.makeClone(
                    Array(variations.size) { SkiaFontVariation(variations[it].axisName, axisValues[it]) },
                )
            }
            font.setTypeface(nextTypeface ?: baseTypeface)
            variedTypeface?.close()
            variedTypeface = nextTypeface
        }

        if (settingsChanged || previousText != text || previousFontSize != fontSize) {
            font.size = fontSize
            val nextBlob = shapeSymbol(text)
            blob?.close()
            blob = nextBlob
            previousSettings = settings
            previousAxisValues = axisValues
            previousText = text
            previousFontSize = fontSize
        }

        val layerAlpha = ownedLayerAlpha
        val color = tint.takeOrElse { Color.Black }
        // Native Skia drawing bypasses the Compose Paint modulation used while recording a layer.
        paint.color = color.copy(alpha = color.alpha * layerAlpha).toArgb()
        blob?.let {
            // Match BasicText's constrained child size, centered paragraph, and overflow clip.
            val paragraphWidth = ceil(width)
            val layoutWidth = paragraphWidth.coerceAtMost(scope.size.width)
            val layoutHeight = height.coerceAtMost(scope.size.height)
            val x = ((scope.size.width - layoutWidth) / 2f).roundToInt().toFloat()
            val y = ((scope.size.height - layoutHeight) / 2f).roundToInt().toFloat()
            val canvas = scope.drawContext.canvas
            val clip = layoutWidth < paragraphWidth || layoutHeight < height
            if (clip) {
                canvas.save()
                canvas.clipRect(x, y, x + layoutWidth, y + layoutHeight)
            }
            try {
                canvas.skiaCanvas.drawTextBlob(
                    it,
                    x + (paragraphWidth - width) / 2f,
                    y + ascent,
                    paint,
                )
            } finally {
                if (clip) canvas.restore()
            }
        }
    }

    private fun shapeSymbol(text: String): TextBlob? {
        // Keep system fallback for an uncovered scalar. Covered symbols still use full HarfBuzz
        // shaping, but skip shapeLine's creation of a new platform font manager on every call.
        if (font.getStringGlyphs(text).any { it.toInt() == 0 }) {
            return shaper.shapeLine(text, font).use { line ->
                width = line.width
                height = ceil(line.height)
                ascent = (-line.ascent).roundToInt().toFloat()
                line.textBlob
            }
        }
        val run = SymbolRun(font)
        return run.builder.use { builder ->
            IcuBidiRunIterator(text, -2).use { bidi ->
                HbIcuScriptRunIterator(text).use { script ->
                    shaper.shape(
                        text,
                        TrivialFontRunIterator(text, font),
                        bidi,
                        script,
                        TrivialLanguageRunIterator(text, language),
                        ShapingOptions.DEFAULT,
                        Float.POSITIVE_INFINITY,
                        run,
                    )
                }
            }
            val metrics = font.metrics
            width = run.width
            height = ceil(metrics.descent - metrics.ascent + metrics.leading)
            ascent = (-metrics.ascent).roundToInt().toFloat()
            builder.build()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        blob?.close()
        shaper.close()
        font.close()
        variedTypeface?.close()
        paint.close()
        releaseBase()
    }
}

/** One Unicode scalar has one font/script run, including any glyphs produced by GSUB. */
private class SymbolRun(private val font: Font) : RunHandler {
    val builder = TextBlobBuilder()
    var width = 0f

    override fun beginLine() = Unit
    override fun runInfo(info: RunInfo?) {
        width += checkNotNull(info).advanceX
    }
    override fun commitRunInfo() = Unit
    override fun runOffset(info: RunInfo?): Point = Point.ZERO
    override fun commitRun(
        info: RunInfo?,
        glyphs: ShortArray?,
        positions: Array<Point?>?,
        clusters: IntArray?,
    ) {
        builder.appendRunPos(
            font,
            checkNotNull(glyphs),
            checkNotNull(positions).map { checkNotNull(it) }.toTypedArray(),
        )
    }
    override fun commitLine() = Unit
}
