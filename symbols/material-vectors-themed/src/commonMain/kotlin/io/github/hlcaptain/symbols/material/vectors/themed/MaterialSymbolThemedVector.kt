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

/**
 * Returns this symbol's vector in the current [MaterialSymbolsTheme] style.
 *
 * The vector is fixed at `FILL=0`, `GRAD=0`, `opsz=24`, and `wght=400`. Changing the
 * theme's axes does not change it. Changing the theme style recomposes the caller and selects the
 * cached Outlined, Rounded, or Sharp vector. The first access builds that vector; later access
 * reuses it.
 *
 * When [autoMirror] is `true`, the returned vector mirrors itself in a right-to-left layout. The
 * normal and auto-mirrored vectors are cached separately.
 *
 * Because the selected symbol is only known at runtime, code shrinking may keep a large part of
 * all three vector packs. Prefer a generated typed property such as `Symbols.Material.Themed.Home`
 * when the icon is known at compile time.
 *
 * @param autoMirror whether the vector should mirror itself in right-to-left layouts
 * @return the cached vector for the current Material style
 * @throws IllegalArgumentException if this symbol is absent from the generated vectors for the
 * selected style
 */
@Composable
@ReadOnlyComposable
fun MaterialSymbol.asThemedImageVector(
    autoMirror: Boolean = false,
): ImageVector = when (MaterialSymbolsTheme.style) {
    MaterialSymbolStyle.Outlined -> asOutlinedImageVector(autoMirror)
    MaterialSymbolStyle.Rounded -> asRoundedImageVector(autoMirror)
    MaterialSymbolStyle.Sharp -> asSharpImageVector(autoMirror)
}
