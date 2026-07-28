package io.github.hlcaptain.symbols.material

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MaterialSymbolsTest {
    @Test
    fun catalogPreservesEveryCanonicalNameAndAliasIdentity() {
        assertEquals(4102, MaterialSymbols.size)
        assertEquals(4102, MaterialSymbols.all.size)
        assertEquals(4102, MaterialSymbols.all.mapTo(mutableSetOf()) { it.name }.size)

        val grade = MaterialSymbols.Grade
        val star = MaterialSymbols.Star
        assertEquals(0xF09A, grade.codePoint)
        assertEquals(grade.codePoint, star.codePoint)
        assertNotEquals(grade, star)
        assertEquals("grade", grade.name)
        assertEquals("star", star.name)

        val aliases = MaterialSymbols.aliases(0xF09A)
        assertEquals(
            listOf(
                "grade",
                "star",
                "star_border",
                "star_border_purple500",
                "star_outline",
                "star_purple500",
            ),
            aliases.map { it.name },
        )
        assertEquals(aliases, MaterialSymbols.aliases(star))
    }

    @Test
    fun directPropertiesAndNameLookupReturnTheSameIdentity() {
        assertEquals(MaterialSymbols.Home, MaterialSymbols.fromName("home"))
        assertEquals(MaterialSymbols.ZoomOutMap, MaterialSymbols.fromName("zoom_out_map"))
        assertEquals(MaterialSymbols._10k, MaterialSymbols.fromName("10k"))
        assertEquals(MaterialSymbols._3dRotation, MaterialSymbols.fromName("3d_rotation"))
        assertEquals(MaterialSymbols._360, MaterialSymbols.fromName("360"))

        assertNull(MaterialSymbols.fromName(""))
        assertNull(MaterialSymbols.fromName("Home"))
        assertNull(MaterialSymbols.fromName("not_a_material_symbol"))
    }

    @Test
    fun allIsAStableLazyViewInCanonicalNameOrder() {
        assertSame(MaterialSymbols.all, MaterialSymbols.all)
        assertEquals("10k", MaterialSymbols.all.first().name)
        assertEquals("zoom_out_map", MaterialSymbols.all.last().name)
        assertTrue(
            MaterialSymbols.all.zipWithNext().all { (left, right) ->
                left.name < right.name
            },
        )

        assertFailsWith<IndexOutOfBoundsException> { MaterialSymbols.all[-1] }
        assertFailsWith<IndexOutOfBoundsException> {
            MaterialSymbols.all[MaterialSymbols.size]
        }
    }

    @Test
    fun everyEntryRoundTripsThroughBothLookupIndexes() {
        val entriesByCodePoint = MaterialSymbols.all.groupBy { it.codePoint }

        for (symbol in MaterialSymbols.all) {
            assertEquals(symbol, MaterialSymbols.fromName(symbol.name), symbol.name)
            assertEquals(
                materialSymbolText(symbol.codePoint),
                symbol.text,
                symbol.name,
            )
        }

        for ((codePoint, expectedAliases) in entriesByCodePoint) {
            assertEquals(
                expectedAliases,
                MaterialSymbols.aliases(codePoint),
                "U+${codePoint.toString(16).uppercase()}",
            )
        }
    }

    @Test
    fun aliasesHandleUniqueAndUnknownCodePoints() {
        assertEquals(listOf(MaterialSymbols.Search), MaterialSymbols.aliases(0xE8B6))
        assertTrue(MaterialSymbols.aliases(-1).isEmpty())
        assertTrue(MaterialSymbols.aliases(0xD800).isEmpty())
        assertTrue(MaterialSymbols.aliases(0x10FFFF).isEmpty())

        val aliases = MaterialSymbols.aliases(0xF09A)
        assertFailsWith<IndexOutOfBoundsException> { aliases[-1] }
        assertFailsWith<IndexOutOfBoundsException> { aliases[aliases.size] }
    }

    @Test
    fun textUsesTheAssignedScalar() {
        assertEquals("\uE9B2", MaterialSymbols.Home.text)
        assertEquals(MaterialSymbols.Grade.text, MaterialSymbols.Star.text)
        assertEquals(1, MaterialSymbols.Home.text.length)
    }

    @Test
    fun unicodeScalarEncodingCoversBmpSupplementaryAndBoundaries() {
        assertEquals("\u0000", materialSymbolText(0))
        assertEquals("\uD7FF", materialSymbolText(0xD7FF))
        assertEquals("\uE000", materialSymbolText(0xE000))
        assertEquals("\uDBFF\uDFFF", materialSymbolText(0x10FFFF))
        assertEquals("\uD83D\uDE00", materialSymbolText(0x1F600))
        assertEquals(2, materialSymbolText(0x1F600).length)

        for (invalid in listOf(-1, 0xD800, 0xDFFF, 0x110000, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException>("U+${invalid.toString(16)}") {
                materialSymbolText(invalid)
            }
        }
    }
}
