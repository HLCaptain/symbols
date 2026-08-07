package io.github.hlcaptain.symbols.material.sharp.consumer

import io.github.hlcaptain.symbols.material.Check
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.Symbols
import kotlin.test.Test
import kotlin.test.assertEquals

class TypedSharpApiTest {
    @Test
    fun typedGetterPreservesTheCatalogSymbol() {
        assertEquals(MaterialSymbols.Check, Symbols.Sharp.Check.symbol)
    }
}
