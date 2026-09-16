package io.github.hlcaptain.symbols.material.rounded.vectors

import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.Grade
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.Rounded
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
        val vector = Symbols.Material.Search.roundedImageVector
        val mirrored = Symbols.Material.Search.asRoundedImageVector(autoMirror = true)

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
            Symbols.Material.Search.roundedImageVector,
            Symbols.Material.Search.asRoundedImageVector(),
        )
        assertSame(
            Symbols.Material.Rounded.Search,
            Symbols.Material.Search.roundedImageVector,
        )
        assertSame(Symbols.Material.Rounded.Search, Symbols.Material.Rounded.Search)
        assertEquals(Symbols.Material.Grade.codePoint, Symbols.Material.Star.codePoint)
        assertSame(Symbols.Material.Rounded.Grade, Symbols.Material.Rounded.Star)
        assertSame(
            Symbols.Material.Grade.roundedImageVector,
            Symbols.Material.Star.roundedImageVector,
        )
        assertSame(
            Symbols.Material.Rounded.Grade,
            Symbols.Material.Grade.roundedImageVector,
        )
        assertSame(
            Symbols.Material.Grade.asRoundedImageVector(autoMirror = true),
            Symbols.Material.Star.asRoundedImageVector(autoMirror = true),
        )
    }

    @Test
    fun snapshotCoversEveryCatalogNameAndCodePoint() {
        assertEquals(4_102, Symbols.Material.size)
        assertEquals(3_802, roundedVectorCount)
        assertTrue(
            roundedVectorCodePoints
                .asSequence()
                .zipWithNext()
                .all { (left, right) -> left < right },
        )
        Symbols.Material.all.forEach { symbol ->
            val index = roundedVectorIndex(symbol.codePoint)
            assertTrue(index >= 0, symbol.name)
            assertEquals(symbol.codePoint, roundedVectorCodePoints[index])
        }
    }
}
