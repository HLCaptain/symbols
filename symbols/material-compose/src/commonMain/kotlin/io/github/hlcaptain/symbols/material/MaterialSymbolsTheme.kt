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

    /** The style used by composable `Symbols.Material.Themed.*` vectors. */
    val style: MaterialSymbolStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMaterialSymbolStyle.current
}

/**
 * Supplies Material symbol [axes] to [content] while keeping the current symbol style.
 *
 * This value is limited to the composable subtree and does not change global state. It also
 * supplies [MaterialSymbolAxes.fontSettings] through the generic `SymbolsTheme`, so generic
 * symbol-font renderers can read the same settings.
 *
 * Changing [axes] updates descendants that read them. Runtime font glyphs may be measured, laid
 * out, and drawn again. Generated Material vectors are fixed at their documented default axes and
 * do not change shape when [axes] changes.
 *
 * @param axes Material font appearance inherited by [content]
 * @param content composable content that should receive the axes
 */
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

/**
 * Supplies a Material symbol [style] and [axes] to [content].
 *
 * The values are limited to this composable subtree and do not change global state. [axes] are also
 * supplied through the generic `SymbolsTheme`. Changing [style] recomposes themed-vector readers
 * and selects the corresponding cached Outlined, Rounded, or Sharp vector. Changing [axes] updates
 * runtime font renderers and may cause their text to be measured, laid out, and drawn again.
 *
 * Generated vectors remain fixed at their default axes; only their style changes. Runtime variable
 * fonts use all supplied axes.
 *
 * @param style Outlined, Rounded, or Sharp style used by theme-selected vectors
 * @param axes Material font appearance inherited by [content]
 * @param content composable content that should receive the style and axes
 */
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
