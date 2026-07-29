package io.github.hlcaptain.symbols.material

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SymbolsTest {
    @Test
    fun styleTypedGettersPreserveCatalogIdentity() {
        assertEquals(MaterialSymbols.Check, Symbols.Outlined.Check.symbol)
        assertEquals(MaterialSymbols.Check, Symbols.Rounded.Check.symbol)
        assertEquals(MaterialSymbols.Check, Symbols.Sharp.Check.symbol)

        assertNotEquals(Symbols.Rounded.Grade.symbol, Symbols.Rounded.Star.symbol)
        assertEquals(
            Symbols.Rounded.Grade.symbol.codePoint,
            Symbols.Rounded.Star.symbol.codePoint,
        )
    }
}
