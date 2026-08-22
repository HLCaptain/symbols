package io.github.hlcaptain.symbols.font

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorProperty
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SymbolImageVectorTest {
    @Test
    fun weightScalesAuthoredStrokeAndClampsToSupportedRange() {
        assertEquals(0.5f, settings(-100f).symbolStrokeScale())
        assertEquals(0.5f, settings(100f).symbolStrokeScale())
        assertEquals(0.75f, settings(250f).symbolStrokeScale())
        assertEquals(1f, settings(400f).symbolStrokeScale())
        assertEquals(1.5f, settings(700f).symbolStrokeScale())
        assertEquals(1.5f, settings(900f).symbolStrokeScale())

        var scale = settings(100f).symbolStrokeScale()
        val config = SymbolWeightVectorConfig { scale }
        assertEquals(1f, config.getOrDefault(VectorProperty.StrokeLineWidth, 2f))
        val fill = SolidColor(Color.Red)
        assertEquals(fill, config.getOrDefault(VectorProperty.Fill, fill))

        scale = settings(700f).symbolStrokeScale()
        assertEquals(3f, config.getOrDefault(VectorProperty.StrokeLineWidth, 2f))
    }

    @Test
    fun missingWeightPreservesAuthoredWidthAndNonFiniteWeightIsRejected() {
        val noWeight = SymbolFontSettings(
            FontVariation.Settings(FontVariation.Setting("wdth", 80f)),
        )

        assertEquals(1f, noWeight.symbolStrokeScale())
        assertFailsWith<IllegalArgumentException> {
            settings(Float.NaN).symbolStrokeScale()
        }
    }

    @Test
    fun namedAndNestedPathsReceiveTheSameWeightConfig() {
        val image = ImageVector.Builder(
            name = "Named paths",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                name = "outer",
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
            ) {
                moveTo(2f, 2f)
                lineTo(22f, 22f)
            }
            group(name = "nested") {
                path(
                    name = "inner",
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1f,
                ) {
                    moveTo(22f, 2f)
                    lineTo(2f, 22f)
                }
            }
        }.build()

        assertEquals(setOf("outer", "inner"), image.root.symbolPathNames())
    }

    private fun settings(weight: Float): SymbolFontSettings = SymbolFontSettings(
        FontVariation.Settings(FontVariation.Setting("wght", weight)),
    )
}
