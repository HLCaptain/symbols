package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.font.SymbolsTheme

/** Available styles for theme-selected, fixed-axis Material vectors. */
enum class MaterialSymbolStyle {
    Outlined,
    Rounded,
    Sharp,
}

/** Material style inherited by theme-selected Material vector properties. */
val LocalMaterialSymbolStyle: ProvidableCompositionLocal<MaterialSymbolStyle> =
    compositionLocalOf { MaterialSymbolStyle.Outlined }

/** Access to the Material style supplied by [MaterialSymbolsTheme]. */
object MaterialSymbolsTheme {
    /** The style used by composable `Symbols.Material.Themed.*` vectors. */
    val style: MaterialSymbolStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMaterialSymbolStyle.current
}

/**
 * Supplies a Material symbol [style] and generic [fontSettings] to [content].
 *
 * Both values are limited to this composable subtree and do not change global state. The style
 * selects Outlined, Rounded, or Sharp theme-aware vectors. The font settings are forwarded to
 * [SymbolsTheme] for regular and variable symbol fonts.
 *
 * Changing [style] recomposes themed-vector readers and selects the corresponding cached vector.
 * Changing [fontSettings] updates runtime font renderers and may cause their text to be measured,
 * laid out, and drawn again. Generated vectors remain fixed at their default font settings; only
 * their style changes. A regular font rejects settings other than the fixed values declared by its
 * descriptor.
 *
 * Omitting either value inherits the one already in effect, which makes nested themes useful for
 * changing only the style or only the font settings.
 *
 * @param style Outlined, Rounded, or Sharp style used by theme-selected vectors
 * @param fontSettings settings inherited by symbol-font renderers
 * @param content composable content that should receive the style and settings
 */
@Composable
fun MaterialSymbolsTheme(
    style: MaterialSymbolStyle = MaterialSymbolsTheme.style,
    fontSettings: SymbolFontSettings = SymbolsTheme.fontSettings,
    content: @Composable () -> Unit,
) {
    SymbolsTheme(fontSettings = fontSettings) {
        CompositionLocalProvider(
            LocalMaterialSymbolStyle provides style,
            content = content,
        )
    }
}
