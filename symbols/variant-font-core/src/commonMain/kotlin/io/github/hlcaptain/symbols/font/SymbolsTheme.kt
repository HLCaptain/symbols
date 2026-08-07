package io.github.hlcaptain.symbols.font

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/** Settings inherited by regular and variable symbol-font renderers. */
val LocalSymbolFontSettings: ProvidableCompositionLocal<SymbolFontSettings> =
    compositionLocalOf { SymbolFontSettings.Default }

/** Access to generic symbol-font values supplied by [SymbolsTheme]. */
object SymbolsTheme {
    /** The font settings at the current position in the composition. */
    val fontSettings: SymbolFontSettings
        @Composable
        @ReadOnlyComposable
        get() = LocalSymbolFontSettings.current
}

/** Supplies [fontSettings] to regular and variable symbol fonts in [content]. */
@Composable
fun SymbolsTheme(
    fontSettings: SymbolFontSettings = SymbolsTheme.fontSettings,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSymbolFontSettings provides fontSettings,
        content = content,
    )
}
