package io.github.hlcaptain.symbols.material.sharp.consumer

import androidx.compose.runtime.Composable
import io.github.hlcaptain.symbols.material.Check
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.Symbols
import io.github.hlcaptain.symbols.material.sharp.MaterialSymbolIcon
import kotlin.test.Test
import kotlin.test.assertEquals

class TypedSharpApiTest {
    @Test
    fun typedGetterPreservesTheCatalogSymbol() {
        assertEquals(MaterialSymbols.Check, Symbols.Sharp.Check.symbol)
    }
}

@Suppress("unused")
@Composable
private fun typedRendererCallCompiles() {
    MaterialSymbolIcon(
        symbol = Symbols.Sharp.Check,
        contentDescription = null,
    )
}
