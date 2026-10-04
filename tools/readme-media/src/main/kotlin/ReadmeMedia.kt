@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
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
import io.github.hlcaptain.symbols.material.rounded.vectors.ArrowRight
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
private const val Width = 1120
private const val Height = 256
private const val FrameNanos = 16_666_667L

private data class Example(val name: String, val legacy: ImageVector, val codePoint: Int)
private val Examples = listOf(
    Example("Home", LegacyIcons.Rounded.Home, Symbols.Material.Home.codePoint),
    Example("Account tree", LegacyIcons.Rounded.AccountTree, Symbols.Material.AccountTree.codePoint),
    Example("Favorite", LegacyIcons.Rounded.Favorite, Symbols.Material.Favorite.codePoint),
    Example("Volume off", LegacyIcons.Rounded.VolumeOff, Symbols.Material.VolumeOff.codePoint),
)

private data class Axes(
    val fill: Float = 0f,
    val weight: Float = 400f,
    val grade: Float = 0f,
    val opticalSize: Float = 24f,
)

// Real Compose state animations, driven by the capture scene's deterministic frame clock.
@Composable
private fun animatedAxes(target: Axes): List<State<Float>> {
    val motion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    return listOf(
        animateFloatAsState(target.fill, motion, label = "Fill"),
        animateFloatAsState(target.weight, motion, label = "Weight"),
        animateFloatAsState(target.grade, motion, label = "Grade"),
        animateFloatAsState(target.opticalSize, motion, label = "Optical size"),
    )
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
private fun Glyph(codePoint: Int, x: Int, y: Int, size: Int, axes: List<State<Float>>, onlyAxis: Int? = null) {
    SymbolFontIcon(
        codePoint = codePoint,
        font = Font,
        contentDescription = null,
        modifier = Modifier.offset(x.dp, y.dp),
        size = size.dp,
        tint = Color.Black,
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
private fun Comparison(target: Axes) {
    val axes = animatedAxes(target)
    Box(Modifier.size(Width.dp, Height.dp).background(Color.White)) {
        Label("Material Icons Extended", 68, 30, 25, bold = true)
        Label("Symbols", 676, 30, 25, bold = true)
        Icon(Icons.Rounded.ArrowRight, null, Modifier.offset(532.dp, 114.dp).size(56.dp), tint = Color.Black)
        Examples.forEachIndexed { i, icon ->
            Icon(icon.legacy, null, Modifier.offset((64 + i * 108).dp, 110.dp).size(64.dp), tint = Color.Black)
            Glyph(icon.codePoint, 672 + i * 108, 110, 64, axes)
            listOf(48, 656).forEach { start ->
                CenteredLabel(icon.name, start + i * 108, 194, 96, 12)
            }
        }
    }
}

@Composable
private fun VariableFonts(target: Axes) {
    val axes = animatedAxes(target)
    Box(Modifier.size(Width.dp, Height.dp).background(Color.White)) {
        val labels = listOf("Fill", "Weight", "Grade", "Optical size")
        val ranges = listOf("Outline ↔ filled", "Light ↔ bold", "Fine ↔ strong", "Small ↔ large detail")
        val codepoints = listOf(Symbols.Material.Favorite, Symbols.Material.Home, Symbols.Material.AccountTree, Symbols.Material.VolumeOff)
        for (i in 0..3) {
            val x = 24 + i * 276
            CenteredLabel(labels[i], x, 22, 248, 20, bold = true)
            Glyph(codepoints[i].codePoint, x + 80, 86, 88, axes, onlyAxis = i)
            CenteredLabel(ranges[i], x, 198, 248, 15)
        }
    }
}

private fun Image.png(): ByteArray = encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes }

private fun glyphsReady(png: ByteArray, comparison: Boolean): Boolean {
    val image = ImageIO.read(png.inputStream())
    return (0..3).all { i ->
        val left = if (comparison) 672 + i * 108 else 104 + i * 276
        val top = if (comparison) 110 else 86
        val side = if (comparison) 64 else 88
        var ink = 0
        for (y in top until top + side) for (x in left until left + side) {
            if ((image.getRGB(x, y) and 0xFF) < 120) ink++
        }
        ink > 100
    }
}

private suspend fun capture(name: String, comparison: Boolean) {
    val output = File("build/frames/$name").apply { mkdirs() }
    val initial = if (comparison) Axes(fill = 1f) else Axes()
    val target = mutableStateOf(initial)
    val scene = ImageComposeScene(Width, Height, coroutineContext = Dispatchers.Unconfined) {
        MaterialExpressiveTheme {
            if (comparison) Comparison(target.value) else VariableFonts(target.value)
        }
    }
    var time = 0L
    fun frame(): ByteArray {
        Snapshot.sendApplyNotifications()
        return scene.render(time).use { it.png() }.also { time += FrameNanos }
    }
    try {
        // Font loading is asynchronous. Never record an empty first frame or guess a sleep time.
        withTimeout(20_000) {
            while (!glyphsReady(frame(), comparison)) delay(5)
        }
        repeat(5) { frame() }
        for (index in 0 until 480) {
            val next = if (comparison) when (index) {
                60 -> Axes(fill = 1f, weight = 600f)
                156 -> Axes(fill = 0f, weight = 600f)
                252 -> Axes(fill = 0f, weight = 300f)
                348 -> initial
                else -> null
            } else when (index) {
                60 -> Axes(1f, 650f, 180f, 44f)
                180 -> Axes(0f, 150f, -40f, 22f)
                300 -> Axes(1f, 600f, 140f, 40f)
                420 -> Axes()
                else -> null
            }
            if (next != null) Snapshot.withMutableSnapshot { target.value = next }
            val png = frame()
            if (index % 2 == 0) File(output, "%04d.png".format(index / 2)).writeBytes(png)
        }
        println("Captured $name: 240 frames, 8 seconds, 30 fps")
    } finally {
        scene.close()
    }
}

fun main() = runBlocking {
    // Compile-check the migration-first README call against the released vector artifact too.
    check(Icons.Rounded.Home.viewportWidth > 0)
    capture("icons-comparison", comparison = true)
    capture("variable-fonts", comparison = false)
}
