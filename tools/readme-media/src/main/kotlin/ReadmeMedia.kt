@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons as LegacyIcons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.fontSettings
import io.github.hlcaptain.symbols.material.*
import io.github.hlcaptain.symbols.material.rounded.vectors.Home
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

private val Font = Symbols.Material.Rounded.font
private const val Width = 1000
private const val Height = 200
private const val FrameNanos = 16_666_667L
private const val SettledHoldFrames = 90 // 1.5 seconds after the spring finishes, at 60 Hz.

private data class Example(val legacy: ImageVector, val codePoint: Int) {
    val name: String get() = legacy.name.substringAfterLast('.')
}
private val Examples = listOf(
    Example(LegacyIcons.Rounded.Home, Symbols.Material.Home.codePoint),
    Example(LegacyIcons.Rounded.AccountTree, Symbols.Material.AccountTree.codePoint),
    Example(LegacyIcons.Rounded.Favorite, Symbols.Material.Favorite.codePoint),
    Example(LegacyIcons.Rounded.VolumeOff, Symbols.Material.VolumeOff.codePoint),
)

private data class Axes(
    val fill: Float = 0f,
    val weight: Float = 400f,
    val grade: Float = 0f,
    val opticalSize: Float = 24f,
)

private val AxisSegments = listOf(
    Axes(weight = 100f),
    Axes(),
    Axes(weight = 700f),
    Axes(),
    Axes(opticalSize = 20f),
    Axes(),
    Axes(opticalSize = 48f),
    Axes(),
    Axes(grade = -50f),
    Axes(),
    Axes(grade = 200f),
    Axes(),
)
private val ComparisonStates = buildList {
    var fill = 1f
    add(Axes(fill = fill))
    for (axes in AxisSegments) {
        add(axes.copy(fill = fill))
        fill = 1f - fill
        add(axes.copy(fill = fill))
    }
}
private val VariableStates = listOf(
    Axes(),
    Axes(fill = 1f, weight = 100f, grade = -50f, opticalSize = 20f),
    Axes(),
    Axes(fill = 1f, weight = 700f, grade = 200f, opticalSize = 48f),
    Axes(),
)

// Real Compose state animations, driven by the capture scene's deterministic frame clock.
@Composable
private fun animatedAxes(target: Axes, finishedListener: ((Float) -> Unit)? = null): List<State<Float>> {
    val motion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    return listOf(
        animateFloatAsState(target.fill, motion, label = "Fill", finishedListener = finishedListener),
        animateFloatAsState(target.weight, motion, label = "Weight", finishedListener = finishedListener),
        animateFloatAsState(target.grade, motion, label = "Grade", finishedListener = finishedListener),
        animateFloatAsState(target.opticalSize, motion, label = "Optical size", finishedListener = finishedListener),
    )
}

@Composable
private fun animatedTint(step: Float, finishedListener: ((Float) -> Unit)? = null): Color {
    val animatedStep = animateFloatAsState(
        step, MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
        label = "Hue", finishedListener = finishedListener,
    ).value
    val hue = (animatedStep * 360f / ComparisonStates.lastIndex % 360f + 360f) % 360f
    return Color.hsv(hue, 1f, 0.6f)
}

@Composable
private fun animatedArrow(step: Float, finishedListener: (() -> Unit)? = null): Float {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(step) {
        if (step != 0f) {
            offset.animateTo(8f, tween(600, easing = EaseInOutQuart))
            offset.animateTo(0f, tween(700, easing = EaseOutQuart))
        }
        finishedListener?.invoke()
    }
    return offset.value.also { check(it in 0f..8f) { "Arrow nudge must not overshoot" } }
}

@Composable
private fun Label(text: String, x: Int, y: Int, size: Int = 16, bold: Boolean = false) {
    Text(
        text,
        Modifier.offset(x.dp, y.dp),
        color = Color.Black,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun CenteredLabel(text: String, x: Int, y: Int, width: Int, size: Int, bold: Boolean = false) {
    Box(Modifier.offset(x.dp, y.dp).size(width.dp, 32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Black, fontSize = size.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun Glyph(
    codePoint: Int, x: Int, y: Int, size: Int, axes: List<State<Float>>,
    onlyAxis: Int? = null, extraDrawingSpace: Int = 0, tint: Color = Color.Black,
) {
    // The pinned font's line height is 1.2 em. Give it a 1.25 em drawing box,
    // centered on the same slot, while keeping the requested font size unchanged.
    val padding = size / 8 + extraDrawingSpace
    SymbolFontIcon(
        codePoint = codePoint,
        font = Font,
        contentDescription = null,
        modifier = Modifier.offset((x - padding).dp, (y - padding).dp)
            .size((size + padding * 2).dp),
        size = size.dp,
        tint = tint,
        fontSettings = {
            // Expressive spatial springs can overshoot; a font's declared axis bounds cannot.
            Font.fontSettings(mapOf(
                "FILL" to (if (onlyAxis == null || onlyAxis == 0) axes[0].value.coerceIn(0f, 1f) else 0f),
                "wght" to (if (onlyAxis == null || onlyAxis == 1) axes[1].value.coerceIn(100f, 700f) else 400f),
                "GRAD" to (if (onlyAxis == null || onlyAxis == 2) axes[2].value.coerceIn(-50f, 200f) else 0f),
                "opsz" to (if (onlyAxis == null || onlyAxis == 3) axes[3].value.coerceIn(20f, 48f) else 24f),
            ))
        },
    )
}

@Composable
private fun Comparison(target: Axes, tint: Color, arrowOffset: Float = 0f, extraDrawingSpace: Int = 0, finishedListener: (Float) -> Unit) {
    val axes = animatedAxes(target, finishedListener)
    Box(Modifier.size(Width.dp, Height.dp).background(Color.White)) {
        Label("Material Icons Extended", 24, 8, 28, bold = true)
        Label("Symbols", 592, 8, 28, bold = true)
        val arrowPadding = 60 / 8 + extraDrawingSpace
        listOf(480.75f + arrowOffset, 460.75f + arrowOffset * 11f / 8f).forEach { x ->
            SymbolFontIcon(
                codePoint = Symbols.Material.KeyboardArrowRight.codePoint,
                font = Font,
                contentDescription = null,
                modifier = Modifier.offset((x - arrowPadding).dp, (68 - arrowPadding).dp)
                    .size((60 + arrowPadding * 2).dp),
                size = 60.dp,
                tint = Color.Black,
                fontSettings = { Font.fontSettings(mapOf("wght" to 700f)) },
            )
        }
        Examples.forEachIndexed { i, icon ->
            Icon(icon.legacy, null, Modifier.offset((20 + i * 104).dp, 58.dp).size(80.dp), tint = Color.Black)
            Glyph(icon.codePoint, 588 + i * 104, 58, 80, axes, extraDrawingSpace = extraDrawingSpace, tint = tint)
            listOf(10, 578).forEach { start ->
                CenteredLabel(icon.name, start + i * 104, 150, 100, 15)
            }
        }
    }
}

@Composable
private fun VariableFonts(target: Axes, extraDrawingSpace: Int = 0) {
    val axes = animatedAxes(target)
    Box(Modifier.size(Width.dp, Height.dp).background(Color.White)) {
        val labels = listOf("Fill", "Weight", "Grade", "Optical size")
        val ranges = listOf("0 ↔ 1", "100 ↔ 400 ↔ 700", "−50 ↔ 0 ↔ 200", "20 ↔ 24 ↔ 48")
        val codepoints = listOf(Symbols.Material.Favorite, Symbols.Material.Home, Symbols.Material.AccountTree, Symbols.Material.VolumeOff)
        for (i in 0..3) {
            val x = i * 250
            CenteredLabel(labels[i], x, 4, 250, 24, bold = true)
            Glyph(codepoints[i].codePoint, x + 73, 44, 104, axes, onlyAxis = i, extraDrawingSpace = extraDrawingSpace)
            CenteredLabel(ranges[i], x, 160, 250, 18)
        }
    }
}

private fun Image.png(): ByteArray = encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes }

private fun glyphsReady(png: ByteArray, comparison: Boolean): Boolean {
    val image = ImageIO.read(png.inputStream())
    return (0..3).all { i ->
        val left = if (comparison) 588 + i * 104 else 73 + i * 250
        val top = if (comparison) 58 else 44
        val side = if (comparison) 80 else 104
        var ink = 0
        for (y in top until top + side) for (x in left until left + side) {
            if ((image.getRGB(x, y) and 0xFF) < 120) ink++
        }
        ink > 100
    }
}

private suspend fun capture(name: String, comparison: Boolean) {
    val output = File("build/frames/$name").apply { mkdirs() }
    val shapeOutput = if (comparison) File(output, "shapes").apply { mkdirs() } else null
    // The comparison duration can change; don't mix this capture with older frames.
    listOfNotNull(output, shapeOutput).forEach { directory ->
        directory.listFiles()?.filter { it.name.matches(Regex("\\d{4}\\.png")) }?.forEach { it.delete() }
    }
    val states = if (comparison) ComparisonStates else VariableStates
    val initial = states.first()
    val target = mutableStateOf(initial)
    val hueStep = mutableStateOf(0f)
    var animationFinished = true
    var hueFinished = true
    var arrowFinished = true
    if (comparison) {
        // One completion callback is sufficient only when exactly one axis changes.
        check(states.zipWithNext().all { (a, b) ->
            listOf(a.fill != b.fill, a.weight != b.weight, a.grade != b.grade, a.opticalSize != b.opticalSize)
                .count { it } == 1
        })
    }
    val scene = ImageComposeScene(Width, Height, coroutineContext = Dispatchers.Unconfined) {
        MaterialExpressiveTheme {
            if (comparison) {
                Comparison(
                    target.value,
                    animatedTint(hueStep.value) { hueFinished = true },
                    animatedArrow(hueStep.value) { arrowFinished = true },
                ) {
                    animationFinished = true
                }
            } else VariableFonts(target.value)
        }
    }
    // Same released renderer and font size, with a roomier layout to reveal internal clipping.
    val reference = ImageComposeScene(Width, Height, coroutineContext = Dispatchers.Unconfined) {
        MaterialExpressiveTheme {
            if (comparison) Comparison(target.value, animatedTint(hueStep.value), animatedArrow(hueStep.value), extraDrawingSpace = 20) {}
            else VariableFonts(target.value, extraDrawingSpace = 20)
        }
    }
    // Skia's font antialiasing depends on tint; verify settled geometry in its original black.
    val shapes = if (comparison) ImageComposeScene(Width, Height, coroutineContext = Dispatchers.Unconfined) {
        MaterialExpressiveTheme { Comparison(target.value, Color.Black, animatedArrow(hueStep.value)) {} }
    } else null
    var time = 0L
    var referencePng = byteArrayOf()
    var shapePng = byteArrayOf()
    var verifyClipping = false
    fun frame(): ByteArray {
        Snapshot.sendApplyNotifications()
        val png = scene.render(time).use { it.png() }
        referencePng = reference.render(time).use { it.png() }
        shapePng = shapes?.render(time)?.use { it.png() } ?: byteArrayOf()
        if (verifyClipping && !png.contentEquals(referencePng)) {
            val failure = File("build/clipping-check").apply { mkdirs() }
            File(failure, "$name-clipped.png").writeBytes(png)
            File(failure, "$name-unclipped-reference.png").writeBytes(referencePng)
            error("Glyph rendering differs with additional drawing space at $time ns ($name)")
        }
        time += FrameNanos
        return png
    }
    try {
        // Font loading is asynchronous. Never record an empty first frame or guess a sleep time.
        withTimeout(20_000) {
            while (true) {
                val png = frame()
                if (glyphsReady(png, comparison) && glyphsReady(referencePng, comparison)
                    && (!comparison || glyphsReady(shapePng, comparison))) break
                delay(5)
            }
        }
        repeat(5) { frame() }
        verifyClipping = true
        check(states.last() == initial)
        var frameCount = 0
        fun recordFrame() {
            val png = frame()
            if (frameCount % 2 == 0) {
                val filename = "%04d.png".format(frameCount / 2)
                File(output, filename).writeBytes(png)
                if (shapeOutput != null) File(shapeOutput, filename).writeBytes(shapePng)
            }
            frameCount++
        }
        val timeline = mutableListOf("frame,settledFrame,endFrame,fill,weight,grade,opticalSize")
        for ((index, axes) in states.withIndex()) {
            val startFrame = frameCount
            var settledFrame: Int? = null
            animationFinished = index == 0
            hueFinished = index == 0
            arrowFinished = index == 0
            Snapshot.withMutableSnapshot {
                target.value = axes
                hueStep.value = index.toFloat()
            }
            if (comparison) {
                while (!animationFinished || !hueFinished || !arrowFinished) {
                    check(frameCount - startFrame < 600) { "Animation did not finish: $axes" }
                    recordFrame()
                }
                // Align the hold to an exported 30 fps frame, preserving all 1.5 seconds.
                if (frameCount % 2 != 0) recordFrame()
                settledFrame = frameCount
                repeat(SettledHoldFrames) { recordFrame() }
            } else {
                repeat(96) { recordFrame() }
            }
            timeline += "$startFrame,${settledFrame ?: ""},$frameCount,${axes.fill},${axes.weight},${axes.grade},${axes.opticalSize}"
        }
        File(output, "states.csv").writeText(timeline.joinToString("\n"))
        println("Captured $name: ${frameCount / 2} frames, ${frameCount / 60.0} seconds, 30 fps")
        println("Verified all $frameCount frames against the unclipped reference")
    } finally {
        scene.close()
        reference.close()
        shapes?.close()
    }
}

fun main() = runBlocking {
    // Compile-check the migration-first README call against the released vector artifact too.
    check(Icons.Rounded.Home.viewportWidth > 0)
    check(Examples.map { it.name } == listOf("Home", "AccountTree", "Favorite", "VolumeOff"))
    capture("icons-comparison", comparison = true)
    capture("variable-fonts", comparison = false)
}
