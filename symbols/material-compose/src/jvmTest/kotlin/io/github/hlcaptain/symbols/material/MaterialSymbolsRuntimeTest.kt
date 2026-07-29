package io.github.hlcaptain.symbols.material

import kotlin.test.Test
import kotlin.test.assertTrue

class MaterialSymbolsRuntimeTest {
    @Test
    fun desktopSupportsVariableFonts() {
        assertTrue(MaterialSymbolsRuntime.variableFontsSupported)
    }
}
