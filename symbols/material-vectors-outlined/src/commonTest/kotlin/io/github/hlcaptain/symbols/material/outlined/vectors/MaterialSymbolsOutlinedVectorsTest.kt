package io.github.hlcaptain.symbols.material.outlined.vectors

import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.ArrowBack
import io.github.hlcaptain.symbols.material.AutoMirrored
import io.github.hlcaptain.symbols.material.Filled
import io.github.hlcaptain.symbols.material.Grade
import io.github.hlcaptain.symbols.material.Icons
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.Outlined
import io.github.hlcaptain.symbols.material.Search
import io.github.hlcaptain.symbols.material.Star
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MaterialSymbolsOutlinedVectorsTest {
    @Test
    fun vectorMetadataAndAutoMirrorAreStable() {
        val vector = Symbols.Material.Search.outlinedImageVector
        val mirrored = Symbols.Material.Search.asOutlinedImageVector(autoMirror = true)

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
            Symbols.Material.Search.outlinedImageVector,
            Symbols.Material.Search.asOutlinedImageVector(),
        )
        assertSame(
            Symbols.Material.Outlined.Search,
            Symbols.Material.Search.outlinedImageVector,
        )
        assertSame(Symbols.Material.Outlined.Search, Symbols.Material.Outlined.Search)
        assertEquals(Symbols.Material.Grade.codePoint, Symbols.Material.Star.codePoint)
        assertSame(Symbols.Material.Outlined.Grade, Symbols.Material.Outlined.Star)
        assertSame(
            Symbols.Material.Grade.outlinedImageVector,
            Symbols.Material.Star.outlinedImageVector,
        )
        assertSame(
            Symbols.Material.Outlined.Grade,
            Symbols.Material.Grade.outlinedImageVector,
        )
        assertSame(
            Symbols.Material.Grade.asOutlinedImageVector(autoMirror = true),
            Symbols.Material.Star.asOutlinedImageVector(autoMirror = true),
        )
    }

    @Test
    fun typedMirroredGettersShareExistingCaches() {
        val normal = Symbols.Material.Outlined.ArrowBack
        val mirrored = Symbols.Material.AutoMirrored.Outlined.ArrowBack

        assertFalse(normal.autoMirror)
        assertTrue(mirrored.autoMirror)
        assertNotSame(normal, mirrored)
        assertSame(mirrored, Symbols.Material.AutoMirrored.Outlined.ArrowBack)
        assertSame(mirrored, Symbols.Material.ArrowBack.asOutlinedImageVector(autoMirror = true))
        assertSame(
            Symbols.Material.AutoMirrored.Outlined.Grade,
            Symbols.Material.AutoMirrored.Outlined.Star,
        )
        assertSame(
            Symbols.Material.AutoMirrored.Outlined.Grade,
            Symbols.Material.Grade.asOutlinedImageVector(autoMirror = true),
        )
    }

    @Test
    fun filledHeartsHaveDistinctPathsAndShareAliasCachesAcrossEntryPoints() {
        val normal = Symbols.Material.Outlined.Favorite
        val filled = Symbols.Material.Outlined.Filled.Favorite
        val mirrored = Symbols.Material.AutoMirrored.Outlined.Filled.Favorite

        assertEquals("MaterialSymbolsOutlined.Filled.U+E87E", filled.name)
        assertEquals("MaterialSymbolsOutlined.Filled.U+E87E.AutoMirrored", mirrored.name)
        assertEquals(24.dp, filled.defaultWidth)
        assertEquals(24.dp, filled.defaultHeight)
        assertEquals(24f, filled.viewportWidth)
        assertEquals(24f, filled.viewportHeight)
        assertFalse(filled.autoMirror)
        assertTrue(mirrored.autoMirror)
        assertNotSame(normal, filled)
        assertNotSame(filled, mirrored)
        assertNotEquals((normal.root[0] as VectorPath).pathData, (filled.root[0] as VectorPath).pathData)
        assertEquals((filled.root[0] as VectorPath).pathData, (mirrored.root[0] as VectorPath).pathData)
        assertSame(filled, Icons.Outlined.Filled.Favorite)
        assertSame(filled, Symbols.Material.Outlined.Filled.FavoriteBorder)
        assertSame(mirrored, Icons.AutoMirrored.Outlined.Filled.FavoriteBorder)
        assertSame(Symbols.Material.Outlined.Filled.Grade, Symbols.Material.Outlined.Filled.Star)
    }

    @Test
    fun fillInvariantIconsReuseDefaultAndMirroredVectors() {
        assertSame(Symbols.Material.Outlined.Check, Symbols.Material.Outlined.Filled.Check)
        assertSame(
            Symbols.Material.AutoMirrored.Outlined.ArrowBack,
            Symbols.Material.AutoMirrored.Outlined.Filled.ArrowBack,
        )
    }

    @Test
    fun snapshotCoversEveryCatalogNameAndCodePoint() {
        assertEquals(4_102, Symbols.Material.size)
        assertEquals(3_802, outlinedVectorCount)
        assertTrue(
            outlinedVectorCodePoints
                .asSequence()
                .zipWithNext()
                .all { (left, right) -> left < right },
        )
        Symbols.Material.all.forEach { symbol ->
            val index = outlinedVectorIndex(symbol.codePoint)
            assertTrue(index >= 0, symbol.name)
            assertEquals(symbol.codePoint, outlinedVectorCodePoints[index])
        }
    }
}
