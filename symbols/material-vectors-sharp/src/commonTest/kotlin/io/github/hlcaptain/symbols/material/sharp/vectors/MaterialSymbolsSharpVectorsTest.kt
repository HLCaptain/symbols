package io.github.hlcaptain.symbols.material.sharp.vectors

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

class MaterialSymbolsSharpVectorsTest {
    @Test
    fun vectorMetadataAndAutoMirrorAreStable() {
        val vector = MaterialSymbols.Search.sharpImageVector
        val mirrored = MaterialSymbols.Search.asSharpImageVector(autoMirror = true)

        assertEquals("MaterialSymbolsSharp.U+E8B6", vector.name)
        assertEquals(24.dp, vector.defaultWidth)
        assertEquals(24.dp, vector.defaultHeight)
        assertEquals(24f, vector.viewportWidth)
        assertEquals(24f, vector.viewportHeight)
        assertFalse(vector.autoMirror)
        assertEquals(
            "MaterialSymbolsSharp.U+E8B6.AutoMirrored",
            mirrored.name,
        )
        assertTrue(mirrored.autoMirror)
        assertNotSame(vector, mirrored)
    }

    @Test
    fun repeatedAndAliasAccessShareCachedInstances() {
        assertSame(
            MaterialSymbols.Search.sharpImageVector,
            MaterialSymbols.Search.asSharpImageVector(),
        )
        assertEquals(MaterialSymbols.Grade.codePoint, MaterialSymbols.Star.codePoint)
        assertSame(
            MaterialSymbols.Grade.sharpImageVector,
            MaterialSymbols.Star.sharpImageVector,
        )
        assertSame(
            MaterialSymbols.Grade.asSharpImageVector(autoMirror = true),
            MaterialSymbols.Star.asSharpImageVector(autoMirror = true),
        )
    }

    @Test
    fun snapshotCoversEveryCatalogNameAndCodePoint() {
        assertEquals(4_102, MaterialSymbols.size)
        assertEquals(3_802, sharpVectorCount)
        assertTrue(
            sharpVectorCodePoints
                .asSequence()
                .zipWithNext()
                .all { (left, right) -> left < right },
        )
        MaterialSymbols.all.forEach { symbol ->
            val index = sharpVectorIndex(symbol.codePoint)
            assertTrue(index >= 0, symbol.name)
            assertEquals(symbol.codePoint, sharpVectorCodePoints[index])
        }
    }

    @Test
    fun generatedPathsKeepPrecisionAndViewportOvershoot() {
        val decimals = Regex("""\.(\d+)""")
        repeat(sharpVectorCount) { index ->
            val path = sharpVectorPathAt(index)
            assertTrue(path.isNotEmpty(), "empty path at $index")
            decimals.findAll(path).forEach { match ->
                assertTrue(match.groupValues[1].length <= 4, "path $index: $path")
            }
        }

        val face2 = sharpVectorPathAt(sharpVectorIndex(0xF8DA))
        assertTrue("-0.3" in face2)
        assertTrue("24.35" in face2)
    }
}
