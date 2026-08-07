package io.github.hlcaptain.symbols.font

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolsThemeTest {
    @Test
    fun defaultNestedAndRestoredSettingsAreVisible() {
        val outer = SymbolFontSettings(
            weight = FontWeight.Medium,
        )
        val inner = SymbolFontSettings(
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("wdth", 80f),
            ),
        )
        var defaultSettings: SymbolFontSettings? = null
        var outerSettings: SymbolFontSettings? = null
        var innerSettings: SymbolFontSettings? = null
        var restoredSettings: SymbolFontSettings? = null

        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(UnitApplier(), recomposer)
        try {
            composition.setContent {
                defaultSettings = SymbolsTheme.fontSettings
                SymbolsTheme(fontSettings = outer) {
                    outerSettings = SymbolsTheme.fontSettings
                    SymbolsTheme(fontSettings = inner) {
                        innerSettings = SymbolsTheme.fontSettings
                    }
                    restoredSettings = SymbolsTheme.fontSettings
                }
            }

            assertEquals(SymbolFontSettings.Default, defaultSettings)
            assertEquals(outer, outerSettings)
            assertEquals(inner, innerSettings)
            assertEquals(outer, restoredSettings)
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }

    @Test
    fun desktopSupportsVariableFonts() {
        assertTrue(SymbolsRuntime.variableFontsSupported)
    }
}

private class UnitApplier : AbstractApplier<Unit>(Unit) {
    override fun insertBottomUp(index: Int, instance: Unit) = Unit

    override fun insertTopDown(index: Int, instance: Unit) = Unit

    override fun move(from: Int, to: Int, count: Int) = Unit

    override fun onClear() = Unit

    override fun remove(index: Int, count: Int) = Unit
}
