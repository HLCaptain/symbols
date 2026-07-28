package io.github.hlcaptain.symbols.material.outlined.vectors

import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.material.Grade
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.Search
import io.github.hlcaptain.symbols.material.Star
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MaterialSymbolsOutlinedVectorsTest {
    @Test
    fun vectorMetadataAndAutoMirrorAreStable() {
        val vector = MaterialSymbols.Search.outlinedImageVector
        val mirrored = MaterialSymbols.Search.asOutlinedImageVector(autoMirror = true)

        assertEquals("MaterialSymbolsOutlined.U+E8B6", vector.name)
        assertEquals(24.dp, vector.defaultWidth)
        assertEquals(24.dp, vector.defaultHeight)
        assertEquals(24f, vector.viewportWidth)
        assertEquals(24f, vector.viewportHeight)
        assertFalse(vector.autoMirror)
        assertEquals(
            "MaterialSymbolsOutlined.U+E8B6.AutoMirrored",
            mirrored.name,
        )
        assertTrue(mirrored.autoMirror)
        assertNotSame(vector, mirrored)
    }

    @Test
    fun repeatedAndAliasAccessShareCachedInstances() {
        assertSame(
            MaterialSymbols.Search.outlinedImageVector,
            MaterialSymbols.Search.asOutlinedImageVector(),
        )
        assertEquals(MaterialSymbols.Grade.codePoint, MaterialSymbols.Star.codePoint)
        assertSame(
            MaterialSymbols.Grade.outlinedImageVector,
            MaterialSymbols.Star.outlinedImageVector,
        )
        assertSame(
            MaterialSymbols.Grade.asOutlinedImageVector(autoMirror = true),
            MaterialSymbols.Star.asOutlinedImageVector(autoMirror = true),
        )
    }

    @Test
    fun snapshotCoversEveryCatalogNameAndCodePoint() {
        assertEquals(4_102, MaterialSymbols.size)
        assertEquals(3_802, outlinedVectorCount)
        assertTrue(
            outlinedVectorCodePoints
                .asSequence()
                .zipWithNext()
                .all { (left, right) -> left < right },
        )
        MaterialSymbols.all.forEach { symbol ->
            val index = outlinedVectorIndex(symbol.codePoint)
            assertTrue(index >= 0, symbol.name)
            assertEquals(symbol.codePoint, outlinedVectorCodePoints[index])
        }
    }

    @Test
    fun generatedPathsKeepPrecisionAndViewportOvershoot() {
        val decimals = Regex("""\.(\d+)""")
        repeat(outlinedVectorCount) { index ->
            val path = outlinedVectorPathAt(index)
            assertTrue(path.isNotEmpty(), "empty path at $index")
            decimals.findAll(path).forEach { match ->
                assertTrue(match.groupValues[1].length <= 4, "path $index: $path")
            }
        }

        val face2 = outlinedVectorPathAt(outlinedVectorIndex(0xF8DA))
        assertTrue("-0.3" in face2)
        assertTrue("24.35" in face2)
    }
}
