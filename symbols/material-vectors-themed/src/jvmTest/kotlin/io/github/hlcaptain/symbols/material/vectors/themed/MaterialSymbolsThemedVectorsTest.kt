package io.github.hlcaptain.symbols.material.vectors.themed

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.hlcaptain.symbols.material.Icons
import io.github.hlcaptain.symbols.material.MaterialSymbolStyle
import io.github.hlcaptain.symbols.material.MaterialSymbolsTheme
import io.github.hlcaptain.symbols.material.outlined.vectors.Home as OutlinedHome
import io.github.hlcaptain.symbols.material.rounded.vectors.Home as RoundedHome
import io.github.hlcaptain.symbols.material.sharp.vectors.Home as SharpHome
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertSame

class MaterialSymbolsThemedVectorsTest {
    @Test
    fun themedPropertySelectsTheCurrentStyle() {
        assertSame(
            Icons.Outlined.OutlinedHome,
            themedHome(MaterialSymbolStyle.Outlined),
        )
        assertSame(
            Icons.Rounded.RoundedHome,
            themedHome(MaterialSymbolStyle.Rounded),
        )
        assertSame(
            Icons.Sharp.SharpHome,
            themedHome(MaterialSymbolStyle.Sharp),
        )
    }

    private fun themedHome(style: MaterialSymbolStyle): ImageVector {
        var vector: ImageVector? = null
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(UnitApplier(), recomposer)
        try {
            composition.setContent {
                MaterialSymbolsTheme(style = style) {
                    vector = Icons.Themed.Home
                }
            }
            return requireNotNull(vector)
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
