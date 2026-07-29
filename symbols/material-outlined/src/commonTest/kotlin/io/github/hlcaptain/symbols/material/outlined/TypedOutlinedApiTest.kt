package io.github.hlcaptain.symbols.material.outlined.consumer

import androidx.compose.runtime.Composable
import io.github.hlcaptain.symbols.material.Check
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.Symbols
import io.github.hlcaptain.symbols.material.outlined.MaterialSymbolIcon
import kotlin.test.Test
import kotlin.test.assertEquals

class TypedOutlinedApiTest {
    @Test
    fun typedGetterPreservesTheCatalogSymbol() {
        assertEquals(MaterialSymbols.Check, Symbols.Outlined.Check.symbol)
    }
}

@Suppress("unused")
@Composable
private fun typedRendererCallCompiles() {
    MaterialSymbolIcon(
        symbol = Symbols.Outlined.Check,
        contentDescription = null,
    )
}
