package io.github.hlcaptain.symbols.material.vectors.themed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.hlcaptain.symbols.material.MaterialSymbol
import io.github.hlcaptain.symbols.material.MaterialSymbolStyle
import io.github.hlcaptain.symbols.material.MaterialSymbolsTheme
import io.github.hlcaptain.symbols.material.outlined.vectors.asOutlinedImageVector
import io.github.hlcaptain.symbols.material.rounded.vectors.asRoundedImageVector
import io.github.hlcaptain.symbols.material.sharp.vectors.asSharpImageVector

/** Returns this symbol's fixed-axis vector in the current Material style. */
@Composable
@ReadOnlyComposable
fun MaterialSymbol.asThemedImageVector(
    autoMirror: Boolean = false,
): ImageVector = when (MaterialSymbolsTheme.style) {
    MaterialSymbolStyle.Outlined -> asOutlinedImageVector(autoMirror)
    MaterialSymbolStyle.Rounded -> asRoundedImageVector(autoMirror)
    MaterialSymbolStyle.Sharp -> asSharpImageVector(autoMirror)
}
