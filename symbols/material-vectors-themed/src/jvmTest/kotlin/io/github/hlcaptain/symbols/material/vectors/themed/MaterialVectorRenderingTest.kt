package io.github.hlcaptain.symbols.material.vectors.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.material.Icons
import io.github.hlcaptain.symbols.material.outlined.vectors.VolumeOff
import io.github.hlcaptain.symbols.material.rounded.vectors.VolumeOff
import io.github.hlcaptain.symbols.material.sharp.vectors.VolumeOff
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaterialVectorRenderingTest {
    @Test
    fun everyStyleRendersFilledGeometryAndMirrorsOnlyOptedInVectors() {
        val styles = listOf(
            "Outlined" to listOf(
                Icons.Outlined.VolumeOff, Icons.AutoMirrored.Outlined.VolumeOff,
                Icons.Outlined.Filled.VolumeOff, Icons.AutoMirrored.Outlined.Filled.VolumeOff,
            ),
            "Rounded" to listOf(
                Icons.Rounded.VolumeOff, Icons.AutoMirrored.Rounded.VolumeOff,
                Icons.Rounded.Filled.VolumeOff, Icons.AutoMirrored.Rounded.Filled.VolumeOff,
            ),
            "Sharp" to listOf(
                Icons.Sharp.VolumeOff, Icons.AutoMirrored.Sharp.VolumeOff,
                Icons.Sharp.Filled.VolumeOff, Icons.AutoMirrored.Sharp.Filled.VolumeOff,
            ),
        )
        for ((style, vectors) in styles) {
            val (normal, mirrored, filled, mirroredFilled) = vectors
            val outlinePixels = verifyMirroring(normal, mirrored, "$style outline")
            val filledPixels = verifyMirroring(filled, mirroredFilled, "$style filled")
            assertFalse(outlinePixels.contentEquals(filledPixels), "$style fill must change visible geometry")
        }
    }

    private fun verifyMirroring(normal: ImageVector, mirrored: ImageVector, label: String): IntArray {
        val ltr = render(normal, LayoutDirection.Ltr)
        assertTrue(ltr.any { it ushr 24 > 0 }, "$label must render visible pixels")
        assertTrue(ltr.contentEquals(render(normal, LayoutDirection.Rtl)), "$label ordinary vector must stay fixed")
        assertTrue(ltr.contentEquals(render(mirrored, LayoutDirection.Ltr)), "$label mirror flag must preserve LTR")
        val rtl = render(mirrored, LayoutDirection.Rtl)
        assertFalse(ltr.contentEquals(rtl), "$label asymmetric icon must change in RTL")
        for (y in 0 until Side) for (x in 0 until Side) {
            val expectedAlpha = ltr[y * Side + Side - 1 - x] ushr 24
            val actualAlpha = rtl[y * Side + x] ushr 24
            assertTrue(abs(expectedAlpha - actualAlpha) <= 2, "$label must reflect horizontally at $x,$y")
        }
        return ltr
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(vector: ImageVector, direction: LayoutDirection): IntArray {
        val scene = ImageComposeScene(
            width = Side,
            height = Side,
            layoutDirection = direction,
            coroutineContext = Dispatchers.Unconfined,
        ) {
            Image(rememberVectorPainter(vector), contentDescription = null, modifier = Modifier.size(Side.dp))
        }
        try {
            scene.render(0L).close()
            return scene.render(16_666_667L).use { image ->
                Bitmap.makeFromImage(image).use { bitmap ->
                    IntArray(Side * Side) { bitmap.getColor(it % Side, it / Side) }
                }
            }
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val Side = 96
    }
}
