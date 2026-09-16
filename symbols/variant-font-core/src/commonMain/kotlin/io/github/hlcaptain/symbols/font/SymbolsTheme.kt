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

/**
 * Supplies symbol-font settings to [content].
 *
 * Renderers such as [SymbolFontIcon] and [rememberSymbolFontFamily] use this value when callers do
 * not pass settings directly. The value is limited to this composable subtree; it does not change
 * global state. A nested call that omits [fontSettings] inherits the value already in effect.
 *
 * Changing [fontSettings] updates descendants that read it. Runtime font text may then be measured,
 * laid out, and drawn again, while a symbol vector painter updates its affected vector subtree.
 *
 * @param fontSettings settings inherited by symbol renderers in [content]
 * @param content composable content that should receive the settings
 */
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
