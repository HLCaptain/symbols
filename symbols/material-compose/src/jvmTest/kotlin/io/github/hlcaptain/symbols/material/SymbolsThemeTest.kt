package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class SymbolsThemeTest {
    @Test
    fun defaultNestedAndRestoredAxesAreVisible() {
        val outer = MaterialSymbolAxes(fill = 1f, weight = 500)
        val inner = MaterialSymbolAxes(grade = 100f, opticalSize = 48f)
        var defaultAxes: MaterialSymbolAxes? = null
        var outerAxes: MaterialSymbolAxes? = null
        var innerAxes: MaterialSymbolAxes? = null
        var restoredAxes: MaterialSymbolAxes? = null

        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(UnitApplier(), recomposer)
        try {
            composition.setContent {
                defaultAxes = SymbolsTheme.axes
                SymbolsTheme(axes = outer) {
                    outerAxes = SymbolsTheme.axes
                    SymbolsTheme(axes = inner) {
                        innerAxes = SymbolsTheme.axes
                    }
                    restoredAxes = SymbolsTheme.axes
                }
            }

            assertEquals(MaterialSymbolAxes.Default, defaultAxes)
            assertEquals(outer, outerAxes)
            assertEquals(inner, innerAxes)
            assertEquals(outer, restoredAxes)
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }

    @Test
    fun defaultNestedAndRestoredStylesAreVisible() {
        var defaultStyle: MaterialSymbolStyle? = null
        var outerStyle: MaterialSymbolStyle? = null
        var innerStyle: MaterialSymbolStyle? = null
        var restoredStyle: MaterialSymbolStyle? = null

        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(UnitApplier(), recomposer)
        try {
            composition.setContent {
                defaultStyle = SymbolsTheme.style
                SymbolsTheme(style = MaterialSymbolStyle.Rounded) {
                    outerStyle = SymbolsTheme.style
                    SymbolsTheme(style = MaterialSymbolStyle.Sharp) {
                        innerStyle = SymbolsTheme.style
                    }
                    restoredStyle = SymbolsTheme.style
                }
            }

            assertEquals(MaterialSymbolStyle.Outlined, defaultStyle)
            assertEquals(MaterialSymbolStyle.Rounded, outerStyle)
            assertEquals(MaterialSymbolStyle.Sharp, innerStyle)
            assertEquals(MaterialSymbolStyle.Rounded, restoredStyle)
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
