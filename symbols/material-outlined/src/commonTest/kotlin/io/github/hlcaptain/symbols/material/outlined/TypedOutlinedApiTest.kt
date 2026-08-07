package io.github.hlcaptain.symbols.material.outlined.consumer

import io.github.hlcaptain.symbols.material.Check
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.Symbols
import kotlin.test.Test
import kotlin.test.assertEquals

class TypedOutlinedApiTest {
    @Test
    fun typedGetterPreservesTheCatalogSymbol() {
        assertEquals(MaterialSymbols.Check, Symbols.Outlined.Check.symbol)
    }
}
