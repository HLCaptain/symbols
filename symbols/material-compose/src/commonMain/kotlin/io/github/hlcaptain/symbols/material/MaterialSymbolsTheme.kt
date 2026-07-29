package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * The Material Symbols axes inherited by variable-font icon renderers.
 *
 * This is a dynamic composition local because axis values can be animated or
 * otherwise changed at runtime. Regular-font adapters, build-time generated
 * vectors, and drawables are fixed snapshots and do not read this local.
 */
public val LocalMaterialSymbolAxes: ProvidableCompositionLocal<MaterialSymbolAxes> =
    compositionLocalOf { MaterialSymbolAxes.Default }

/**
 * Access to Material Symbols values supplied by [MaterialSymbolsTheme].
 */
public object MaterialSymbolsTheme {
    /**
     * The axes at the current position in the composition.
     *
     * Outside a provider this returns [MaterialSymbolAxes.Default].
     */
    public val axes: MaterialSymbolAxes
        @Composable
        @ReadOnlyComposable
        get() = LocalMaterialSymbolAxes.current
}

/**
 * Supplies [axes] to variable-font Material Symbols in [content].
 *
 * Nesting inherits the current axes by default. An explicit axes argument on a
 * renderer still takes precedence over this provider.
 */
@Composable
public fun MaterialSymbolsTheme(
    axes: MaterialSymbolAxes = MaterialSymbolsTheme.axes,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMaterialSymbolAxes provides axes,
        content = content,
    )
}
