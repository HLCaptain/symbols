package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import io.github.hlcaptain.symbols.font.SymbolsTheme

/** Material Symbols axes inherited by Material font adapters. */
val LocalMaterialSymbolAxes: ProvidableCompositionLocal<MaterialSymbolAxes> =
    compositionLocalOf { MaterialSymbolAxes.Default }

/** Available styles for theme-selected, fixed-axis Material vectors. */
enum class MaterialSymbolStyle {
    Outlined,
    Rounded,
    Sharp,
}

/** Material style inherited by theme-selected Material vector properties. */
val LocalMaterialSymbolStyle: ProvidableCompositionLocal<MaterialSymbolStyle> =
    compositionLocalOf { MaterialSymbolStyle.Outlined }

/** Access to Material-specific values supplied by [MaterialSymbolsTheme]. */
object MaterialSymbolsTheme {
    /** The Material axes at the current position in the composition. */
    val axes: MaterialSymbolAxes
        @Composable
        @ReadOnlyComposable
        get() = LocalMaterialSymbolAxes.current

    /** The style used by composable `Icons.Themed.*` vector properties. */
    val style: MaterialSymbolStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMaterialSymbolStyle.current
}

/** Supplies [axes] to Material variable-font symbols in [content]. */
@Composable
fun MaterialSymbolsTheme(
    axes: MaterialSymbolAxes = MaterialSymbolsTheme.axes,
    content: @Composable () -> Unit,
) {
    MaterialSymbolsTheme(
        style = MaterialSymbolsTheme.style,
        axes = axes,
        content = content,
    )
}

/** Supplies Material [style] and [axes], plus their generic font settings. */
@Composable
fun MaterialSymbolsTheme(
    style: MaterialSymbolStyle,
    axes: MaterialSymbolAxes = MaterialSymbolsTheme.axes,
    content: @Composable () -> Unit,
) {
    SymbolsTheme(fontSettings = axes.fontSettings) {
        CompositionLocalProvider(
            LocalMaterialSymbolAxes provides axes,
            LocalMaterialSymbolStyle provides style,
            content = content,
        )
    }
}
