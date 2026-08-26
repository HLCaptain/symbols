package io.github.hlcaptain.symbols.material.sharp.vectors

import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.Grade
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.Sharp
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
        val vector = Symbols.Material.Search.sharpImageVector
        val mirrored = Symbols.Material.Search.asSharpImageVector(autoMirror = true)

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
            Symbols.Material.Search.sharpImageVector,
            Symbols.Material.Search.asSharpImageVector(),
        )
        assertSame(
            Symbols.Material.Sharp.Search,
            Symbols.Material.Search.sharpImageVector,
        )
        assertSame(Symbols.Material.Sharp.Search, Symbols.Material.Sharp.Search)
        assertEquals(Symbols.Material.Grade.codePoint, Symbols.Material.Star.codePoint)
        assertSame(Symbols.Material.Sharp.Grade, Symbols.Material.Sharp.Star)
        assertSame(
            Symbols.Material.Grade.sharpImageVector,
            Symbols.Material.Star.sharpImageVector,
        )
        assertSame(
            Symbols.Material.Sharp.Grade,
            Symbols.Material.Grade.sharpImageVector,
        )
        assertSame(
            Symbols.Material.Grade.asSharpImageVector(autoMirror = true),
            Symbols.Material.Star.asSharpImageVector(autoMirror = true),
        )
    }

    @Test
    fun snapshotCoversEveryCatalogNameAndCodePoint() {
        assertEquals(4_102, Symbols.Material.size)
        assertEquals(3_802, sharpVectorCount)
        assertTrue(
            sharpVectorCodePoints
                .asSequence()
                .zipWithNext()
                .all { (left, right) -> left < right },
        )
        Symbols.Material.all.forEach { symbol ->
            val index = sharpVectorIndex(symbol.codePoint)
            assertTrue(index >= 0, symbol.name)
            assertEquals(symbol.codePoint, sharpVectorCodePoints[index])
        }
    }
}
