package io.github.hlcaptain.symbols.material.rounded.vectors

import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.material.Grade
import io.github.hlcaptain.symbols.material.Icons
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.Search
import io.github.hlcaptain.symbols.material.Star
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MaterialSymbolsRoundedVectorsTest {
    @Test
    fun vectorMetadataAndAutoMirrorAreStable() {
        val vector = MaterialSymbols.Search.roundedImageVector
        val mirrored = MaterialSymbols.Search.asRoundedImageVector(autoMirror = true)

        assertEquals("MaterialSymbolsRounded.U+E8B6", vector.name)
        assertEquals(24.dp, vector.defaultWidth)
        assertEquals(24.dp, vector.defaultHeight)
        assertEquals(24f, vector.viewportWidth)
        assertEquals(24f, vector.viewportHeight)
        assertFalse(vector.autoMirror)
        assertEquals(
            "MaterialSymbolsRounded.U+E8B6.AutoMirrored",
            mirrored.name,
        )
        assertTrue(mirrored.autoMirror)
        assertNotSame(vector, mirrored)
    }

    @Test
    fun repeatedAndAliasAccessShareCachedInstances() {
        assertSame(
            MaterialSymbols.Search.roundedImageVector,
            MaterialSymbols.Search.asRoundedImageVector(),
        )
        assertSame(
            Icons.Rounded.Search,
            MaterialSymbols.Search.roundedImageVector,
        )
        assertSame(Icons.Rounded.Search, Icons.Rounded.Search)
        assertEquals(MaterialSymbols.Grade.codePoint, MaterialSymbols.Star.codePoint)
        assertSame(Icons.Rounded.Grade, Icons.Rounded.Star)
        assertSame(
            MaterialSymbols.Grade.roundedImageVector,
            MaterialSymbols.Star.roundedImageVector,
        )
        assertSame(
            Icons.Rounded.Grade,
            MaterialSymbols.Grade.roundedImageVector,
        )
        assertSame(
            MaterialSymbols.Grade.asRoundedImageVector(autoMirror = true),
            MaterialSymbols.Star.asRoundedImageVector(autoMirror = true),
        )
    }

    @Test
    fun snapshotCoversEveryCatalogNameAndCodePoint() {
        assertEquals(4_102, MaterialSymbols.size)
        assertEquals(3_802, roundedVectorCount)
        assertTrue(
            roundedVectorCodePoints
                .asSequence()
                .zipWithNext()
                .all { (left, right) -> left < right },
        )
        MaterialSymbols.all.forEach { symbol ->
            val index = roundedVectorIndex(symbol.codePoint)
            assertTrue(index >= 0, symbol.name)
            assertEquals(symbol.codePoint, roundedVectorCodePoints[index])
        }
    }
}
