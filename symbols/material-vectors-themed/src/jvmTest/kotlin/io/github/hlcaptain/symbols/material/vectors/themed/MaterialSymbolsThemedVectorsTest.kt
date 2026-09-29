package io.github.hlcaptain.symbols.material.vectors.themed

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.AutoMirrored
import io.github.hlcaptain.symbols.material.Home
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.MaterialSymbolStyle
import io.github.hlcaptain.symbols.material.MaterialSymbolsTheme
import io.github.hlcaptain.symbols.material.Outlined
import io.github.hlcaptain.symbols.material.Rounded
import io.github.hlcaptain.symbols.material.Sharp
import io.github.hlcaptain.symbols.material.Themed
import io.github.hlcaptain.symbols.material.outlined.vectors.ArrowBack as OutlinedArrowBack
import io.github.hlcaptain.symbols.material.outlined.vectors.Home as OutlinedHome
import io.github.hlcaptain.symbols.material.rounded.vectors.ArrowBack as RoundedArrowBack
import io.github.hlcaptain.symbols.material.rounded.vectors.Home as RoundedHome
import io.github.hlcaptain.symbols.material.sharp.vectors.ArrowBack as SharpArrowBack
import io.github.hlcaptain.symbols.material.sharp.vectors.Home as SharpHome
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MaterialSymbolsThemedVectorsTest {
    @Test
    fun themedPropertySelectsTheCurrentStyle() {
        assertSame(
            Symbols.Material.Outlined.OutlinedHome,
            themedHome(MaterialSymbolStyle.Outlined),
        )
        assertSame(
            Symbols.Material.Rounded.RoundedHome,
            themedHome(MaterialSymbolStyle.Rounded),
        )
        assertSame(
            Symbols.Material.Sharp.SharpHome,
            themedHome(MaterialSymbolStyle.Sharp),
        )
    }

    @Test
    fun symbolSelectsTheCurrentStyle() {
        assertSame(Symbols.Material.Outlined.OutlinedHome, themedSymbol(MaterialSymbolStyle.Outlined))
        assertSame(Symbols.Material.Rounded.RoundedHome, themedSymbol(MaterialSymbolStyle.Rounded))
        assertSame(Symbols.Material.Sharp.SharpHome, themedSymbol(MaterialSymbolStyle.Sharp))
    }

    @Test
    fun mirroredThemedPropertyTracksStyleChangesWithoutChangingNormalVectors() {
        val cases = listOf(
            Triple(
                MaterialSymbolStyle.Outlined,
                Symbols.Material.AutoMirrored.Outlined.OutlinedArrowBack,
                Symbols.Material.Outlined.OutlinedArrowBack,
            ),
            Triple(
                MaterialSymbolStyle.Rounded,
                Symbols.Material.AutoMirrored.Rounded.RoundedArrowBack,
                Symbols.Material.Rounded.RoundedArrowBack,
            ),
            Triple(
                MaterialSymbolStyle.Sharp,
                Symbols.Material.AutoMirrored.Sharp.SharpArrowBack,
                Symbols.Material.Sharp.SharpArrowBack,
            ),
        )
        var mirrored: ImageVector? = null
        var normal: ImageVector? = null

        withComposition { composition ->
            for ((style, expectedMirrored, expectedNormal) in cases + cases.first()) {
                composition.setContent {
                    MaterialSymbolsTheme(style = style) {
                        mirrored = Symbols.Material.AutoMirrored.Themed.ArrowBack
                        normal = Symbols.Material.Themed.ArrowBack
                    }
                }

                assertSame(expectedMirrored, mirrored, style.name)
                assertSame(expectedNormal, normal, style.name)
                assertTrue(requireNotNull(mirrored).autoMirror)
                assertFalse(requireNotNull(normal).autoMirror)
            }
        }
    }

    @Test
    fun mirroredThemedPropertyInheritsAndRestoresNestedStyles() {
        var inherited: ImageVector? = null
        var overridden: ImageVector? = null
        var restored: ImageVector? = null
        var default: ImageVector? = null

        withComposition { composition ->
            composition.setContent {
                MaterialSymbolsTheme(style = MaterialSymbolStyle.Rounded) {
                    MaterialSymbolsTheme {
                        inherited = Symbols.Material.AutoMirrored.Themed.ArrowBack
                    }
                    MaterialSymbolsTheme(style = MaterialSymbolStyle.Sharp) {
                        overridden = Symbols.Material.AutoMirrored.Themed.ArrowBack
                    }
                    restored = Symbols.Material.AutoMirrored.Themed.ArrowBack
                }
                default = Symbols.Material.AutoMirrored.Themed.ArrowBack
            }
        }

        assertSame(Symbols.Material.AutoMirrored.Rounded.RoundedArrowBack, inherited)
        assertSame(Symbols.Material.AutoMirrored.Sharp.SharpArrowBack, overridden)
        assertSame(Symbols.Material.AutoMirrored.Rounded.RoundedArrowBack, restored)
        assertSame(Symbols.Material.AutoMirrored.Outlined.OutlinedArrowBack, default)
    }

    private fun withComposition(block: (Composition) -> Unit) {
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(UnitApplier(), recomposer)
        try {
            block(composition)
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }

    private fun themedHome(style: MaterialSymbolStyle): ImageVector {
        var vector: ImageVector? = null
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(UnitApplier(), recomposer)
        try {
            composition.setContent {
                MaterialSymbolsTheme(style = style) {
                    vector = Symbols.Material.Themed.Home
                }
            }
            return requireNotNull(vector)
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }

    private fun themedSymbol(style: MaterialSymbolStyle): ImageVector {
        var vector: ImageVector? = null
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(UnitApplier(), recomposer)
        try {
            composition.setContent {
                MaterialSymbolsTheme(style = style) {
                    vector = Symbols.Material.Home.asThemedImageVector()
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
