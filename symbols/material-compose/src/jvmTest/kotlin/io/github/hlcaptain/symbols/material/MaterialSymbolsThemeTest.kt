package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.text.font.FontVariation
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.font.SymbolsTheme
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialSymbolsThemeTest {
    @Test
    fun materialThemeSuppliesStyleAndGenericFontSettings() {
        val settings = SymbolFontSettings.Default.withVariations(
            FontVariation.Setting("wght", 525f),
        )
        var materialStyle: MaterialSymbolStyle? = null
        var genericSettings: SymbolFontSettings? = null

        compose {
            SymbolsTheme(fontSettings = settings) {
                MaterialSymbolsTheme(style = MaterialSymbolStyle.Rounded) {
                    materialStyle = MaterialSymbolsTheme.style
                    genericSettings = SymbolsTheme.fontSettings
                }
            }
        }

        assertEquals(MaterialSymbolStyle.Rounded, materialStyle)
        assertEquals(settings, genericSettings)
    }

    @Test
    fun nestedMaterialStyleAndFontSettingsAreRestored() {
        val outerSettings = SymbolFontSettings.Default.withVariations(
            FontVariation.Setting("wght", 500f),
        )
        val innerSettings = SymbolFontSettings.Default.withVariations(
            FontVariation.Setting("wght", 700f),
        )
        var inheritedStyle: MaterialSymbolStyle? = null
        var inheritedSettings: SymbolFontSettings? = null
        var overriddenStyle: MaterialSymbolStyle? = null
        var overriddenStyleSettings: SymbolFontSettings? = null
        var restoredStyle: MaterialSymbolStyle? = null
        var restoredSettings: SymbolFontSettings? = null

        compose {
            MaterialSymbolsTheme(
                style = MaterialSymbolStyle.Rounded,
                fontSettings = outerSettings,
            ) {
                MaterialSymbolsTheme(fontSettings = innerSettings) {
                    inheritedStyle = MaterialSymbolsTheme.style
                    inheritedSettings = SymbolsTheme.fontSettings
                }
                MaterialSymbolsTheme(style = MaterialSymbolStyle.Sharp) {
                    overriddenStyle = MaterialSymbolsTheme.style
                    overriddenStyleSettings = SymbolsTheme.fontSettings
                }
                restoredStyle = MaterialSymbolsTheme.style
                restoredSettings = SymbolsTheme.fontSettings
            }
        }

        assertEquals(MaterialSymbolStyle.Rounded, inheritedStyle)
        assertEquals(innerSettings, inheritedSettings)
        assertEquals(MaterialSymbolStyle.Sharp, overriddenStyle)
        assertEquals(outerSettings, overriddenStyleSettings)
        assertEquals(MaterialSymbolStyle.Rounded, restoredStyle)
        assertEquals(outerSettings, restoredSettings)
    }

    private fun compose(content: @Composable () -> Unit) {
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(UnitApplier(), recomposer)
        try {
            composition.setContent(content)
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }
}

private class UnitApplier : AbstractApplier<Unit>(Unit) {
    override fun insertBottomUp(index: Int, instance: Unit) = Unit

    override fun insertTopDown(index: Int, instance: Unit) = Unit

    override fun move(from: Int, to: Int, count: Int) = Unit

    override fun onClear() = Unit

    override fun remove(index: Int, count: Int) = Unit
}
