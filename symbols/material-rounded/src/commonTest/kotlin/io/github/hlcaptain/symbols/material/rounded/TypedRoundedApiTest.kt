package io.github.hlcaptain.symbols.material.rounded.consumer

import androidx.compose.runtime.Composable
import io.github.hlcaptain.symbols.material.Check
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.Symbols
import io.github.hlcaptain.symbols.material.rounded.MaterialSymbolIcon
import kotlin.test.Test
import kotlin.test.assertEquals

class TypedRoundedApiTest {
    @Test
    fun typedGetterPreservesTheCatalogSymbol() {
        assertEquals(MaterialSymbols.Check, Symbols.Rounded.Check.symbol)
    }
}

@Suppress("unused")
@Composable
private fun typedRendererCallCompiles() {
    MaterialSymbolIcon(
        symbol = Symbols.Rounded.Check,
        contentDescription = null,
    )
}
