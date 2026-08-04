package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.font.SymbolsTheme
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialSymbolsThemeTest {
    @Test
    fun materialAxesAlsoReachTheGenericFontTheme() {
        val axes = MaterialSymbolAxes(fill = 1f, weight = 500)
        var materialAxes: MaterialSymbolAxes? = null
        var genericSettings: SymbolFontSettings? = null

        compose {
            MaterialSymbolsTheme(axes = axes) {
                materialAxes = MaterialSymbolsTheme.axes
                genericSettings = SymbolsTheme.fontSettings
            }
        }

        assertEquals(axes, materialAxes)
        assertEquals(axes.fontSettings, genericSettings)
    }

    @Test
    fun nestedMaterialStyleAndAxesAreRestored() {
        val outerAxes = MaterialSymbolAxes(fill = 1f)
        val innerAxes = MaterialSymbolAxes(grade = 100f, opticalSize = 48f)
        var innerStyle: MaterialSymbolStyle? = null
        var restoredStyle: MaterialSymbolStyle? = null
        var restoredAxes: MaterialSymbolAxes? = null

        compose {
            MaterialSymbolsTheme(
                style = MaterialSymbolStyle.Rounded,
                axes = outerAxes,
            ) {
                MaterialSymbolsTheme(
                    style = MaterialSymbolStyle.Sharp,
                    axes = innerAxes,
                ) {
                    innerStyle = MaterialSymbolsTheme.style
                }
                restoredStyle = MaterialSymbolsTheme.style
                restoredAxes = MaterialSymbolsTheme.axes
            }
        }

        assertEquals(MaterialSymbolStyle.Sharp, innerStyle)
        assertEquals(MaterialSymbolStyle.Rounded, restoredStyle)
        assertEquals(outerAxes, restoredAxes)
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
