package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * The Material-compatible axes inherited by variable-font icon renderers.
 *
 * This is a dynamic composition local because axis values can be animated or
 * otherwise changed at runtime. Regular-font adapters, build-time generated
 * vectors, and drawables are fixed snapshots and do not read this local.
 */
public val LocalMaterialSymbolAxes: ProvidableCompositionLocal<MaterialSymbolAxes> =
    compositionLocalOf { MaterialSymbolAxes.Default }

/** Available styles for theme-selected, fixed-axis [Icons] properties. */
public enum class MaterialSymbolStyle {
    Outlined,
    Rounded,
    Sharp,
}

/** Supplies the style inherited by theme-selected [Icons] properties. */
public val LocalMaterialSymbolStyle: ProvidableCompositionLocal<MaterialSymbolStyle> =
    compositionLocalOf { MaterialSymbolStyle.Outlined }

/**
 * Access to symbol values supplied by [SymbolsTheme].
 */
public object SymbolsTheme {
    /**
     * The axes at the current position in the composition.
     *
     * Outside a provider this returns [MaterialSymbolAxes.Default].
     */
    public val axes: MaterialSymbolAxes
        @Composable
        @ReadOnlyComposable
        get() = LocalMaterialSymbolAxes.current

    /** The style used by composable `Icons.Themed.*` vector properties. */
    public val style: MaterialSymbolStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMaterialSymbolStyle.current
}

/**
 * Supplies [axes] to Material-axis-compatible variable-font symbols in
 * [content].
 *
 * Nesting inherits the current axes by default. An explicit axes argument on a
 * renderer still takes precedence over this provider.
 */
@Composable
public fun SymbolsTheme(
    axes: MaterialSymbolAxes = SymbolsTheme.axes,
    content: @Composable () -> Unit,
) {
    SymbolsTheme(
        style = SymbolsTheme.style,
        axes = axes,
        content = content,
    )
}

/**
 * Supplies a fixed [style] to themed vectors and [axes] to font-backed symbols.
 *
 * The vector snapshots stay fixed at the documented default axes; only their
 * Outlined, Rounded, or Sharp style is selected dynamically.
 */
@Composable
public fun SymbolsTheme(
    style: MaterialSymbolStyle,
    axes: MaterialSymbolAxes = SymbolsTheme.axes,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMaterialSymbolAxes provides axes,
        LocalMaterialSymbolStyle provides style,
        content = content,
    )
}
