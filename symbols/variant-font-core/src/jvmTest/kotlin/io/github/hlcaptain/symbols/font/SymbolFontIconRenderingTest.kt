package io.github.hlcaptain.symbols.font

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ResourceItem
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Surface
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class SymbolFontIconRenderingTest {
    @Test
    fun axesTintAndOwnedLayerUpdatePixelsWithoutRecomposingMeasuringOrPlacingTheIcon() = runBlocking {
        val weight = mutableFloatStateOf(100f)
        val tint = mutableStateOf(Color.Black)
        val opacity = mutableFloatStateOf(1f)
        val parentOpacity = mutableFloatStateOf(1f)
        val layerEnabled = mutableStateOf(true)
        val layerProgress = mutableFloatStateOf(0f)
        val layer: GraphicsLayerScope.() -> Unit = {
            testLayer(layerProgress.floatValue)
            alpha *= opacity.floatValue
        }
        val codePoint = mutableIntStateOf(0xe5c4) // arrow_back is asymmetric.
        val iconSize = mutableIntStateOf(IconSize)
        var compositions = 0
        var measurements = 0
        var placements = 0
        val modifier = Modifier.layout { measurable, constraints ->
            measurements++
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placements++
                placeable.place(0, 0)
            }
        }

        IconScene().use { scene ->
            scene.setContent {
                SideEffect { compositions++ }
                Box(Modifier.graphicsLayer { alpha = parentOpacity.floatValue }) {
                    SymbolFontIcon(
                        codePoint = codePoint.intValue,
                        font = TestFont,
                        contentDescription = "Back",
                        modifier = modifier,
                        fontSettings = { settings(weight.floatValue) },
                        tint = { tint.value },
                        size = iconSize.intValue.dp,
                        graphicsLayer = if (layerEnabled.value) layer else null,
                    )
                }
            }
            val first = scene.awaitPixels()
            val initialCounts = Triple(compositions, measurements, placements)
            val rendered = mutableListOf(first)
            for (value in listOf(300f, 500f, 700f)) {
                Snapshot.withMutableSnapshot { weight.floatValue = value }
                rendered += scene.frame()
                assertEquals(initialCounts, Triple(compositions, measurements, placements))
            }
            rendered.zipWithNext().forEach { (before, after) ->
                assertFalse(before.contentEquals(after), "Every new weight must reach the renderer")
            }
            val heavy = rendered.last()
            assertTrue(heavy.alphaSum() > first.alphaSum(), "The heavy glyph must gain ink")

            Snapshot.withMutableSnapshot { tint.value = Color.Red }
            val red = scene.frame()
            assertEquals(initialCounts, Triple(compositions, measurements, placements))
            assertFalse(heavy.contentEquals(red), "Tint changes must update pixels")
            assertTrue(red.any { (it and 0x00ffffff) == 0x00ff0000 }, "The glyph must turn red")
            assertEquals(heavy.inkBounds(IconSize), red.inkBounds(IconSize))
            val textHeavy = renderIcon(native = false, weight = 700f, tint = Color.Black)
            val textRed = renderIcon(native = false, weight = 700f, tint = Color.Red)
            // Skia text edge coverage depends on color. Compare actual text rendering, not
            // an invariant alpha mask that BasicText itself does not preserve when tint changes.
            assertTrue(
                heavy.contentEquals(textHeavy) && red.contentEquals(textRed),
                "Native tint rendering must match BasicText at both colors",
            )

            Snapshot.withMutableSnapshot {
                weight.floatValue = 100f
                tint.value = Color.Blue
            }
            val blue = scene.frame()
            assertEquals(initialCounts, Triple(compositions, measurements, placements))
            assertTrue(blue.alphaSum() < red.alphaSum(), "Combined updates must apply the lighter weight")
            assertTrue(blue.any { (it and 0x00ffffff) == 0x000000ff }, "Combined updates must apply blue tint")

            Snapshot.withMutableSnapshot { tint.value = Color.Unspecified }
            val restored = scene.frame()
            assertEquals(initialCounts, Triple(compositions, measurements, placements))
            assertTrue(first.contentEquals(restored), "Unspecified producer tint must restore black")

            Snapshot.withMutableSnapshot { opacity.floatValue = 0.5f }
            val faded = scene.frame()
            assertEquals(initialCounts, Triple(compositions, measurements, placements))
            // Bound 8-bit rounding by one alpha level per covered pixel. This absolute check
            // also catches a modulation bug shared by the native and BasicText implementations.
            val roundingBound = restored.count { (it ushr 24) > 0 }
            assertTrue(
                abs(faded.alphaSum() - restored.alphaSum() * 0.5) <= roundingBound,
                "Half layer alpha must halve glyph coverage: ${restored.alphaSum()} -> ${faded.alphaSum()}",
            )
            Snapshot.withMutableSnapshot { parentOpacity.floatValue = 0.5f }
            val nestedFade = scene.frame()
            assertEquals(initialCounts, Triple(compositions, measurements, placements))
            assertTrue(
                abs(nestedFade.alphaSum() - restored.alphaSum() * 0.25) <= roundingBound,
                "Parent Auto and owned half alpha must multiply: ${restored.alphaSum()} -> ${nestedFade.alphaSum()}",
            )
            Snapshot.withMutableSnapshot {
                opacity.floatValue = 1f
                parentOpacity.floatValue = 1f
            }
            assertTrue(restored.contentEquals(scene.frame()), "Restoring opacity must restore the glyph")
            assertEquals(initialCounts, Triple(compositions, measurements, placements))

            for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
                Snapshot.withMutableSnapshot { layerProgress.floatValue = fraction }
                val transformed = scene.frame()
                assertEquals(initialCounts, Triple(compositions, measurements, placements))
                assertFalse(restored.contentEquals(transformed), "Owned layer updates must affect pixels")
                val reference = renderIcon(
                    native = false,
                    weight = 100f,
                    tint = Color.Black.copy(alpha = 1f - fraction * 0.5f),
                    modifier = Modifier.graphicsLayer {
                        testLayer(fraction)
                        // Match per-draw modulation through text paint alpha. Avoid BasicText's
                        // ModulateAlpha bypass and Auto's alpha-triggered offscreen rasterization.
                        alpha = 1f
                        compositingStrategy = CompositingStrategy.Auto
                    },
                )
                assertTrue(
                    transformed.contentEquals(reference),
                    "Owned alpha/scale/rotation/translation must match the BasicText layer at $fraction",
                )
            }
            Snapshot.withMutableSnapshot { layerProgress.floatValue = 0f }
            assertTrue(restored.contentEquals(scene.frame()), "Resetting the layer must restore the glyph")
            assertEquals(initialCounts, Triple(compositions, measurements, placements))

            Snapshot.withMutableSnapshot { codePoint.intValue = 0xe88a } // home
            val changedGlyph = scene.frame()
            assertFalse(restored.contentEquals(changedGlyph), "Content changes must replace the glyph")

            Snapshot.withMutableSnapshot { iconSize.intValue = 32 }
            val resized = scene.frame()
            assertTrue(measurements > initialCounts.second, "Changing size must remeasure the icon")
            assertTrue(placements > initialCounts.third, "Changing size must place the resized icon")
            assertTrue(resized.alphaSum() < changedGlyph.alphaSum(), "The glyph must shrink with its square")

            Snapshot.withMutableSnapshot { opacity.floatValue = 0.5f }
            assertTrue(scene.frame().alphaSum() < resized.alphaSum(), "The smaller glyph must also fade")
            Snapshot.withMutableSnapshot { layerEnabled.value = false }
            assertTrue(resized.contentEquals(scene.frame()), "Removing the owned layer must reset its alpha")
        }
    }

    @Test
    fun descriptorValueAndProducerOverloadsShareOwnedLayerRendering() = runBlocking {
        val layer: GraphicsLayerScope.() -> Unit = { testLayer(0.5f) }
        val producer = renderIcon(native = true, weight = 700f, tint = Color.Red, graphicsLayer = layer)
        val value = IconScene().use { scene ->
            scene.setContent {
                SymbolFontIcon(
                    codePoint = 0xe5c4,
                    font = TestFont,
                    contentDescription = null,
                    fontSettings = settings(700f),
                    tint = Color.Red,
                    size = IconSize.dp,
                    graphicsLayer = layer,
                )
            }
            scene.awaitPixels()
        }
        assertTrue(value.contentEquals(producer), "Value settings must use the same native layer renderer")
    }

    @Test
    fun transformedGlyphsMatchActualTextLayoutAtSeveralPixelSizes() = runBlocking {
        val baselines = mutableListOf<String>()
        val mismatches = mutableListOf<String>()
        for (density in listOf(Density(0.5f), Density(1f), Density(2f))) {
            val side = (IconSize * density.density).toInt()
            val textLayout = textLayout(density)
            baselines += "fontSizePx=$side firstBaseline=${textLayout.firstBaseline} size=${textLayout.size}"
            for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
                val actual = renderIcon(
                    density = density,
                    native = true,
                    weight = 100f,
                    graphicsLayer = { testLayer(fraction) },
                )
                val expected = renderIcon(
                    density = density,
                    native = false,
                    weight = 100f,
                    tint = Color.Black.copy(alpha = 1f - fraction * 0.5f),
                    modifier = Modifier.graphicsLayer {
                        testLayer(fraction)
                        alpha = 1f
                    },
                )
                if (!actual.contentEquals(expected)) {
                    actual.saveImage("native-transform-$side-$fraction", side)
                    expected.saveImage("text-transform-$side-$fraction", side)
                    val changedPixels = actual.indices.count { actual[it] != expected[it] }
                    val maxAlphaError = actual.indices.maxOf {
                        abs((actual[it] ushr 24) - (expected[it] ushr 24))
                    }
                    mismatches += "${side}px/$fraction: $changedPixels changed pixels, " +
                        "max alpha error=$maxAlphaError, baseline=${textLayout.firstBaseline}"
                }
            }
        }
        File("build/reports/native-symbol-rendering").apply { mkdirs() }
            .resolve("text-layout-baselines.txt").writeText(baselines.joinToString("\n"))
        assertTrue(mismatches.isEmpty(), mismatches.joinToString("\n"))
    }

    @Test
    fun nativeOutputMatchesTextBoundsAcrossWeightsDensityFontScaleAndConstraints() = runBlocking {
        val configurations = listOf(Density(1f) to IconSize, Density(1f) to 32, Density(2f, 1.75f) to IconSize)
        for ((density, containerSize) in configurations) {
            for (weight in listOf(100f, 700f)) {
                val modifier = Modifier.size(containerSize.dp)
                val expected = renderIcon(density = density, native = false, weight = weight, modifier = modifier)
                val actual = renderIcon(density = density, native = true, weight = weight, modifier = modifier)
                val side = (IconSize * density.density).toInt()
                val expectedBounds = expected.inkBounds(side)
                val actualBounds = actual.inkBounds(side)
                expectedBounds.zip(actualBounds).forEach { (textEdge, nativeEdge) ->
                    assertTrue(
                        abs(textEdge - nativeEdge) <= 1,
                        "Native bounds $actualBounds differ from BasicText $expectedBounds at $density, " +
                            "wght=$weight, container=${containerSize}dp",
                    )
                }
                val inkRatio = actual.alphaSum().toDouble() / expected.alphaSum()
                assertTrue(inkRatio in 0.95..1.05, "Native glyph ink differs from BasicText: $inkRatio")
            }
        }
    }

    @Test
    fun mirroringAndTintKeepTheExistingIconContract() = runBlocking {
        val ltr = renderIcon(native = true, tint = Color.Red)
        val rtl = renderIcon(native = true, tint = Color.Red, direction = LayoutDirection.Rtl)
        val textLtr = renderIcon(native = false, tint = Color.Red)
        val textRtl = renderIcon(native = false, tint = Color.Red, direction = LayoutDirection.Rtl)
        val nativeDifference = ltr.mirrorDifference(rtl)
        val textDifference = textLtr.mirrorDifference(textRtl)
        // Reflected antialiasing can round differently; require the same bounds as BasicText.
        val matchesTextRaster = nativeDifference.first <= textDifference.first &&
            nativeDifference.second <= textDifference.second
        if (!matchesTextRaster) {
            ltr.saveImage("native-ltr")
            rtl.saveImage("native-rtl")
            textLtr.saveImage("text-ltr")
            textRtl.saveImage("text-rtl")
        }
        assertFalse(ltr.contentEquals(rtl), "An asymmetric glyph must mirror in RTL")
        assertTrue(
            matchesTextRaster,
            "Mirror alpha difference (max,total): native=$nativeDifference; BasicText=$textDifference",
        )
        ltr.filter { (it ushr 24) > 0 }.forEach { pixel ->
            assertEquals(0, pixel and 0x00ffff, "Tint must remain red")
        }
        assertTrue(
            renderIcon(native = true, tint = Color.Unspecified)
                .contentEquals(renderIcon(native = true, tint = Color.Black)),
            "Unspecified tint must resolve to the same black as BasicText",
        )
    }

    @Test
    fun sharedFontSurvivesOneConsumerClosingAndReloadsAfterAllClose() = runBlocking {
        val content: @Composable () -> Unit = {
            SymbolFontIcon(
                codePoint = 0xe5c4,
                font = TestFont,
                contentDescription = null,
                fontSettings = { settings(400f) },
                size = IconSize.dp,
            )
        }
        IconScene().use { survivor ->
            IconScene().use { first ->
                first.setContent(content)
                survivor.setContent(content)
                first.frame()
                survivor.frame()
            }
            assertTrue(survivor.awaitPixels().alphaSum() > 0)
        }
        IconScene().use { remounted ->
            remounted.setContent(content)
            assertTrue(remounted.awaitPixels().alphaSum() > 0)
        }
    }

    private suspend fun renderIcon(
        density: Density = Density(1f),
        native: Boolean,
        weight: Float = 400f,
        tint: Color = Color.Black,
        direction: LayoutDirection = LayoutDirection.Ltr,
        modifier: Modifier = Modifier,
        graphicsLayer: (GraphicsLayerScope.() -> Unit)? = null,
    ): IntArray = IconScene(density, direction).use { scene ->
        scene.setContent {
            if (native) {
                SymbolFontIcon(
                    codePoint = 0xe5c4,
                    font = TestFont,
                    contentDescription = null,
                    modifier = modifier,
                    fontSettings = { settings(weight) },
                    tint = tint,
                    size = IconSize.dp,
                    autoMirror = true,
                    graphicsLayer = graphicsLayer,
                )
            } else {
                val family = rememberSymbolFontFamily(TestFont, settings(weight))
                SymbolFontIcon(
                    codePoint = 0xe5c4,
                    fontFamily = family,
                    contentDescription = null,
                    modifier = modifier,
                    tint = tint,
                    size = IconSize.dp,
                    autoMirror = true,
                )
            }
        }
        scene.awaitPixels()
    }

    private suspend fun textLayout(density: Density): TextLayoutResult = IconScene(density).use { scene ->
        var result: TextLayoutResult? = null
        scene.setContent {
            val family = rememberSymbolFontFamily(TestFont, settings(100f))
            val fontSize = with(LocalDensity.current) { IconSize.dp.toSp() }
            Box(Modifier.size(IconSize.dp), contentAlignment = Alignment.Center) {
                BasicText(
                    text = symbolFontText(0xe5c4),
                    style = TextStyle(
                        color = Color.Black,
                        fontSize = fontSize,
                        fontFamily = family,
                        fontSynthesis = FontSynthesis.None,
                        textAlign = TextAlign.Center,
                    ),
                    onTextLayout = { result = it },
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        scene.awaitPixels()
        requireNotNull(result)
    }
}

@OptIn(InternalComposeUiApi::class)
private class IconScene(
    density: Density = Density(1f),
    direction: LayoutDirection = LayoutDirection.Ltr,
) : AutoCloseable {
    private val side = (IconSize * density.density).toInt()
    private val surface = Surface.makeRasterN32Premul(side, side)
    private val frameRecomposer = FrameRecomposer(Dispatchers.Unconfined)
    private val scene = CanvasLayersComposeScene(
        frameRecomposer = frameRecomposer,
        density = density,
        layoutDirection = direction,
        size = IntSize(side, side),
    )
    private var time = 0L

    fun setContent(content: @Composable () -> Unit) = scene.setContent(content = content)

    fun frame(): IntArray {
        Snapshot.sendApplyNotifications()
        surface.canvas.clear(0)
        frameRecomposer.performFrame(time)
        scene.measureAndLayout()
        scene.draw(surface.canvas.asComposeCanvas())
        time += 16_666_667L
        return surface.makeImageSnapshot().use { image ->
            Bitmap.makeFromImage(image).use { bitmap ->
                IntArray(side * side) { bitmap.getColor(it % side, it / side) }
            }
        }
    }

    suspend fun awaitPixels(): IntArray = withTimeout(10_000) {
        var pixels = frame()
        while (pixels.alphaSum() == 0L) {
            delay(1)
            pixels = frame()
        }
        pixels
    }

    override fun close() {
        scene.close()
        frameRecomposer.close()
        surface.close()
    }
}

private const val IconSize = 48

private fun GraphicsLayerScope.testLayer(fraction: Float) {
    alpha = 1f - fraction * 0.5f
    scaleX = 1f - fraction * 0.2f
    scaleY = scaleX
    rotationZ = fraction * 16f
    translationX = fraction * 4f
    translationY = fraction * -3f
}

@OptIn(InternalResourceApi::class)
private val TestFont = SymbolFont.variable(
    familyName = "Material Symbols Outlined test",
    resource = FontResource(
        "native-rendering-test-outlined",
        setOf(ResourceItem(emptySet(), "font/material_symbols_outlined_variable.ttf", -1, -1)),
    ),
    variationAxes = listOf(SymbolFontAxis("wght", 100f, 400f, 700f)),
)

private fun settings(weight: Float) = TestFont.fontSettings(mapOf("wght" to weight))

private fun IntArray.alphaSum(): Long = sumOf { (it ushr 24).toLong() }

private fun IntArray.mirrorDifference(other: IntArray): Pair<Int, Long> {
    val differences = indices.map { index ->
        val x = index % IconSize
        val y = index / IconSize
        abs((this[index] ushr 24) - (other[y * IconSize + IconSize - 1 - x] ushr 24))
    }
    return differences.max() to differences.sumOf(Int::toLong)
}

private fun IntArray.saveImage(name: String, side: Int = IconSize) {
    val directory = File("build/reports/native-symbol-rendering").apply { mkdirs() }
    val image = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, side, side, this, 0, side)
    ImageIO.write(image, "png", File(directory, "$name.png"))
}

private fun IntArray.inkBounds(side: Int): List<Int> {
    val indices = indices.filter { (this[it] ushr 24) > 16 }
    assertTrue(indices.isNotEmpty(), "A real font glyph must be rendered")
    return listOf(
        indices.minOf { it % side }, indices.minOf { it / side },
        indices.maxOf { it % side }, indices.maxOf { it / side },
    )
}
