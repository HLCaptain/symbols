package example

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.AutoMirrored
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.MaterialSymbolStyle
import io.github.hlcaptain.symbols.material.MaterialSymbolsTheme
import io.github.hlcaptain.symbols.material.Rounded
import io.github.hlcaptain.symbols.material.Themed
import io.github.hlcaptain.symbols.material.rounded.vectors.ArrowBack as RoundedArrowBack
import io.github.hlcaptain.symbols.material.vectors.themed.ArrowBack
import kotlin.coroutines.EmptyCoroutineContext

// A String result keeps both published icon paths reachable from Java and an Apple framework.
fun builtInVectorSummary(): String {
    val rounded: ImageVector = Symbols.Material.AutoMirrored.Rounded.RoundedArrowBack
    var themed: ImageVector? = null
    val recomposer = Recomposer(EmptyCoroutineContext)
    val composition = Composition(UnitApplier(), recomposer)
    try {
        composition.setContent {
            MaterialSymbolsTheme(style = MaterialSymbolStyle.Rounded) {
                themed = Symbols.Material.AutoMirrored.Themed.ArrowBack
            }
        }
        val selected = requireNotNull(themed)
        check(rounded.autoMirror && selected.autoMirror)
        check(selected === rounded)
        return "${rounded.name}:${selected.name}"
    } finally {
        composition.dispose()
        recomposer.cancel()
    }
}

private class UnitApplier : AbstractApplier<Unit>(Unit) {
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
    override fun remove(index: Int, count: Int) = Unit
}
