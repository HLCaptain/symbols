package io.github.hlcaptain.symbols.benchmark.animatedfont

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Trace
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.font.rememberSymbolFontFamily
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.Rounded
import io.github.hlcaptain.symbols.material.Favorite
import io.github.hlcaptain.symbols.material.font
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A separate release app keeps navigation, sliders, and live labels out of frame measurements. */
class BenchmarkActivity : ComponentActivity() {
    private val progress = mutableFloatStateOf(0f)
    private val stats = RenderStats()
    private lateinit var status: TextView
    private lateinit var composeView: ComposeView
    private lateinit var renderer: String
    private lateinit var axis: String
    private lateinit var scenario: String
    private var diagnostics = false
    private var count = 1
    private var sizeDp = 32
    private var animation: Job? = null
    private var renderScope: CoroutineScope? = null
    private val effectsProgress = FloatArray(10) { Float.NaN }
    private val effectsCache = arrayOfNulls<StressEffects>(10)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            animation?.cancel()
            status.contentDescription = "running"
            stats.resetCounts()
            animation = requireNotNull(renderScope).launch {
                when (intent.action) {
                    "$PACKAGE.START" -> {
                        val durationMs = intent.getIntExtra("duration_ms", 2_000)
                        require(durationMs in 100..60_000)
                        val start = withFrameNanos { it }
                        do {
                            val elapsed = withFrameNanos { it } - start
                            val fraction = (elapsed / (durationMs * 1_000_000.0)).coerceAtMost(1.0)
                            measured("SymbolBenchmark.update") {
                                // Time-based triangle: slow frames skip coordinates instead of prolonging the run.
                                stats.updates++
                                progress.floatValue = (1.0 - kotlin.math.abs(2.0 * fraction - 1.0)).toFloat()
                            }
                        } while (fraction < 1.0)
                    }
                    "$PACKAGE.SNAPSHOT" -> {
                        val value = intent.getFloatExtra("progress", 0f)
                        require(value.isFinite() && value in 0f..1f)
                        stats.updates++
                        progress.floatValue = value
                    }
                }
                // Publish once the final state has been laid out and drawn, outside the animation.
                repeat(3) { withFrameNanos { } }
                publishStats()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderer = intent.getStringExtra("renderer") ?: "native"
        axis = intent.getStringExtra("axis") ?: "all"
        scenario = intent.getStringExtra("scenario") ?: "axes"
        diagnostics = intent.getBooleanExtra("diagnostics", false)
        count = intent.getIntExtra("count", 1)
        sizeDp = intent.getIntExtra("size_dp", if (count == 1) 128 else if (scenario != "axes") 24 else 32)
        require(renderer in listOf("baseline", "shared", "value", "native"))
        require(axis in listOf("wght", "FILL", "GRAD", "opsz", "all", "unsupported"))
        require(scenario in listOf("axes", "stress", "draw"))
        require(scenario == "axes" || axis == "all") { "Combined effects animate all axes together" }
        require(count == 1 || count == 100)
        require(sizeDp in 1..256)
        progress.floatValue = intent.getFloatExtra("progress", 0f).also {
            require(it.isFinite() && it in 0f..1f)
        }
        status = TextView(this).apply {
            text = "$renderer / $axis / $count / $scenario"
            contentDescription = "loading"
        }
        composeView = ComposeView(this).apply { setContent { Icons() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)
            addView(composeView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
        val filter = IntentFilter().apply {
            addAction("$PACKAGE.START")
            addAction("$PACKAGE.SNAPSHOT")
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    @Composable
    private fun Icons() {
        val scope = rememberCoroutineScope()
        SideEffect { renderScope = scope }
        LaunchedEffect(Unit) {
            repeat(5) { withFrameNanos { } }
            publishStats()
        }
        SideEffect { stats.hostCompositions++ }
        val sharedFamily = if (renderer == "shared" && scenario == "axes") {
            rememberSymbolFontFamily(Symbols.Material.Rounded.font, currentSettings(recordTick = true))
        } else null
        Column(Modifier.background(Color.White).padding(16.dp)) {
            repeat(if (count == 1) 1 else 10) { row ->
                if (scenario != "axes") {
                    StressRow(row)
                } else {
                    Row {
                        repeat(if (count == 1) 1 else 10) { column ->
                            MeasuredIcon(row * 10 + column, sharedFamily)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StressRow(row: Int) {
        SideEffect { stats.groupCompositions++ }
        val family = if (renderer == "shared") {
            rememberSymbolFontFamily(
                Symbols.Material.Rounded.font,
                currentSettings(recordTick = row == 0, group = row),
            )
        } else null
        Row {
            repeat(if (count == 1) 1 else 10) { column ->
                val index = row * 10 + column
                Box(
                    Modifier.size((sizeDp * 1.5f).dp).onGloballyPositioned {
                        stats.captureBounds[index] = screenBounds(it)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    MeasuredIcon(index, family)
                }
            }
        }
    }

    @Composable
    private fun MeasuredIcon(index: Int, sharedFamily: FontFamily?) {
        SideEffect { stats.iconCompositions++ }
        val instrumentation = remember(index) {
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measured("SymbolBenchmark.measure") { measurable.measure(constraints) }
                    stats.measures++
                    stats.measuredSizes[index] = intArrayOf(placeable.width, placeable.height)
                    layout(placeable.width, placeable.height) {
                        measured("SymbolBenchmark.place") { placeable.place(0, 0) }
                        stats.placements++
                    }
                }
                .onGloballyPositioned { coordinates ->
                    stats.bounds[index] = screenBounds(coordinates)
                }
        }
        val drawInstrumentation = remember(index) {
            Modifier.drawWithContent {
                measured("SymbolBenchmark.draw") { drawContent() }
                stats.draws++
                if (index == 0) {
                    stats.drawnTicks.add(stats.preparedTick)
                    stats.drawnProgress.add(stats.preparedProgress)
                }
            }
        }
        val group = index / 10
        // The original stress case reads size/tint here. The fixed-size native case defers tint
        // until drawing; neither its settings nor its layer properties are read in composition.
        val effects = if (scenario == "stress" || scenario == "draw" && renderer != "native") {
            currentEffects(group)
        } else null
        val iconSize = effects?.fontSizeDp?.dp ?: sizeDp.dp
        val tint = effects?.color ?: Color.Black
        val layer: (GraphicsLayerScope.() -> Unit)? = if (scenario != "axes") {
            {
                measured("SymbolBenchmark.layer") {
                    val value = currentEffects(group)
                    alpha = value.alpha
                    scaleX = value.scale
                    scaleY = value.scale
                    rotationZ = value.rotationDegrees
                    translationX = value.translationXDp.dp.toPx()
                    translationY = value.translationYDp.dp.toPx()
                    stats.layerUpdates++
                    if (index == 0) stats.layerTicks.add(stats.updates)
                }
            }
        } else null
        // A cached layer can rerecord its content without rerecording its parent. Count the
        // actual glyph content inside the layer, and layer-property updates separately.
        val modifier = instrumentation.then(drawInstrumentation)
        val settings = remember(index) { { currentSettings(recordTick = index == 0, group = group) } }
        if (renderer == "baseline" || renderer == "shared") {
            // Explicit Compose text references remain independent of the library's native core.
            val family = sharedFamily ?: rememberSymbolFontFamily(Symbols.Material.Rounded.font, settings())
            val referenceLayer = if (layer == null) Modifier else Modifier.graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                layer()
            }
            SymbolFontIcon(
                codePoint = Symbols.Material.Favorite.codePoint,
                fontFamily = family,
                contentDescription = null,
                size = iconSize,
                tint = tint,
                modifier = referenceLayer.then(modifier),
            )
        } else if (renderer == "value") {
            SymbolFontIcon(
                codePoint = Symbols.Material.Favorite.codePoint,
                font = Symbols.Material.Rounded.font,
                contentDescription = null,
                fontSettings = settings(),
                size = iconSize,
                tint = tint,
                modifier = modifier,
                graphicsLayer = layer,
            )
        } else if (scenario == "draw") {
            SymbolFontIcon(
                codePoint = Symbols.Material.Favorite.codePoint,
                font = Symbols.Material.Rounded.font,
                contentDescription = null,
                fontSettings = settings,
                size = iconSize,
                tint = { currentEffects(group).color },
                modifier = modifier,
                graphicsLayer = layer,
            )
        } else {
            SymbolFontIcon(
                codePoint = Symbols.Material.Favorite.codePoint,
                font = Symbols.Material.Rounded.font,
                contentDescription = null,
                fontSettings = settings,
                size = iconSize,
                tint = tint,
                modifier = modifier,
                graphicsLayer = layer,
            )
        }
    }

    private fun currentSettings(recordTick: Boolean, group: Int = 0): SymbolFontSettings {
        val value = progress.floatValue
        if (recordTick) {
            stats.preparedTick = stats.updates
            stats.preparedProgress = value
        }
        return if (scenario != "axes") currentEffects(group).settings
            else settingsAt(value, axis)
    }

    private fun currentEffects(group: Int): StressEffects {
        val value = progress.floatValue
        // Every consumer still observes progress in its own phase. Only the deterministic
        // calculation is shared; size/scenario are fixed for this Activity's lifetime.
        if (effectsProgress[group] != value) {
            effectsCache[group] = measured("SymbolBenchmark.effects") {
                stressEffectsAt(value, group, sizeDp, fixedSize = scenario == "draw")
            }
            effectsProgress[group] = value
            stats.effectEvaluations++
        }
        return requireNotNull(effectsCache[group])
    }

    private inline fun <T> measured(name: String, block: () -> T): T =
        if (diagnostics) traced(name, block) else block()

    private fun screenBounds(coordinates: LayoutCoordinates): IntArray {
        val origin = IntArray(2)
        composeView.getLocationOnScreen(origin)
        val position = coordinates.positionInRoot()
        return intArrayOf(
            origin[0] + position.x.toInt(), origin[1] + position.y.toInt(),
            coordinates.size.width, coordinates.size.height,
        )
    }

    private fun publishStats() {
        val report = JSONObject()
            .put("renderer", renderer).put("axis", axis).put("count", count)
            .put("scenario", scenario)
            .put("diagnostics", diagnostics).put("effectEvaluations", stats.effectEvaluations)
            .put("progress", progress.floatValue).put("sizeDp", sizeDp)
            .put("hostCompositions", stats.hostCompositions).put("iconCompositions", stats.iconCompositions)
            .put("groupCompositions", stats.groupCompositions)
            .put("measures", stats.measures).put("placements", stats.placements)
            .put("draws", stats.draws).put("updates", stats.updates)
            .put("layerUpdates", stats.layerUpdates).put("layerTicks", JSONArray(stats.layerTicks))
            .put("distinctLayerUpdates", stats.layerTicks.filter { it > 0 }.distinct().size)
            .put("drawnTicks", JSONArray(stats.drawnTicks))
            .put("drawnProgress", JSONArray(stats.drawnProgress))
            .put("distinctDrawnUpdates", stats.drawnTicks.filter { it > 0 }.distinct().size)
            .put("bounds", JSONArray(stats.bounds.toSortedMap().values.map { JSONArray(it.toList()) }))
            .put("measuredSizes", JSONArray(stats.measuredSizes.toSortedMap().values.map { JSONArray(it.toList()) }))
            .put("captureBounds", JSONArray(
                (if (scenario != "axes") stats.captureBounds else stats.bounds)
                    .toSortedMap().values.map { JSONArray(it.toList()) },
            ))
        if (scenario != "axes") {
            val effects = currentEffects(0)
            report.put("effects", JSONObject()
                .put("axes", JSONObject().apply {
                    effects.settings.variationSettings.settings.forEach {
                        put(it.axisName, it.toVariationValue(null))
                    }
                })
                .put("fontSizeDp", effects.fontSizeDp).put("colorArgb", effects.color.toArgb())
                .put("alpha", effects.alpha).put("scale", effects.scale)
                .put("rotationDegrees", effects.rotationDegrees)
                .put("translationXDp", effects.translationXDp).put("translationYDp", effects.translationYDp))
        }
        status.contentDescription = "stats:$report"
    }
}

private fun settingsAt(progress: Float, axis: String): SymbolFontSettings {
    if (axis == "unsupported") {
        val setting = if (progress in 0.25f..0.75f) FontVariation.Setting("ZZZZ", 1f)
            else FontVariation.Setting("wght", 700f)
        return SymbolFontSettings(FontVariation.Settings(setting))
    }
    fun value(tag: String, min: Float, max: Float, default: Float) =
        if (axis == tag || axis == "all") min + (max - min) * progress else default
    return SymbolFontSettings(FontVariation.Settings(
        FontVariation.Setting("wght", value("wght", 100f, 700f, 400f)),
        FontVariation.Setting("FILL", value("FILL", 0f, 1f, 0f)),
        FontVariation.Setting("GRAD", value("GRAD", -50f, 200f, 0f)),
        FontVariation.Setting("opsz", value("opsz", 20f, 48f, 24f)),
    ))
}

private data class StressEffects(
    val settings: SymbolFontSettings,
    val fontSizeDp: Float,
    val color: Color,
    val alpha: Float,
    val scale: Float,
    val rotationDegrees: Float,
    val translationXDp: Float,
    val translationYDp: Float,
)

private fun stressEffectsAt(progress: Float, group: Int, baseSizeDp: Int, fixedSize: Boolean = false): StressEffects {
    val phase = 2 * PI * (progress + group / 10.0)
    fun wave(offset: Double) = ((1 - cos(phase + offset * 2 * PI)) / 2).toFloat()
    return StressEffects(
        settings = SymbolFontSettings(FontVariation.Settings(
            FontVariation.Setting("wght", 100f + 600f * wave(0.0)),
            FontVariation.Setting("FILL", wave(0.25)),
            FontVariation.Setting("GRAD", -50f + 250f * wave(0.5)),
            FontVariation.Setting("opsz", 20f + 28f * wave(0.75)),
        )),
        fontSizeDp = if (fixedSize) baseSizeDp.toFloat() else baseSizeDp * (0.75f + 0.25f * wave(0.125)),
        color = lerp(Color(0xFF1B4965), Color(0xFF8E244D), wave(0.375)),
        alpha = 0.65f + 0.35f * wave(0.625),
        scale = 0.9f + 0.2f * wave(0.875),
        rotationDegrees = -15f + 30f * wave(0.5),
        translationXDp = baseSizeDp * 0.06f * sin(phase).toFloat(),
        translationYDp = baseSizeDp * 0.06f * cos(phase).toFloat(),
    )
}

private class RenderStats {
    var hostCompositions = 0
    var iconCompositions = 0
    var groupCompositions = 0
    var measures = 0
    var placements = 0
    var draws = 0
    var layerUpdates = 0
    var updates = 0
    var preparedTick = 0
    var preparedProgress = 0f
    var effectEvaluations = 0
    val drawnTicks = ArrayList<Int>(128)
    val drawnProgress = ArrayList<Float>(128)
    val layerTicks = ArrayList<Int>(128)
    val bounds = mutableMapOf<Int, IntArray>()
    val measuredSizes = mutableMapOf<Int, IntArray>()
    val captureBounds = mutableMapOf<Int, IntArray>()
    fun resetCounts() {
        hostCompositions = 0
        iconCompositions = 0
        groupCompositions = 0
        measures = 0
        placements = 0
        draws = 0
        layerUpdates = 0
        updates = 0
        preparedTick = 0
        effectEvaluations = 0
        drawnTicks.clear()
        drawnProgress.clear()
        layerTicks.clear()
    }
}

private inline fun <T> traced(name: String, block: () -> T): T {
    Trace.beginSection(name)
    return try { block() } finally { Trace.endSection() }
}

private const val PACKAGE = "io.github.hlcaptain.symbols.benchmark.animatedfont"
