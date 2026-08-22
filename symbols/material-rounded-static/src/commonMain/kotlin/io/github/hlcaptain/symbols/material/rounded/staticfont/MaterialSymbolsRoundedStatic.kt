package io.github.hlcaptain.symbols.material.rounded.staticfont

import io.github.hlcaptain.symbols.font.SymbolFont
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.rounded.staticfont.resources.Res
import io.github.hlcaptain.symbols.material.rounded.staticfont.resources.symbolFonts

/**
 * A static Material Symbols Rounded 2.874 font at the default axis point.
 *
 * This non-variable font works on Android API 21. Its default-axis point is
 * fixed; requesting different axes fails with a clear error.
 */
object MaterialSymbolsRoundedStatic :
    SymbolFont.Regular by Res.symbolFonts.material_symbols_rounded_regular {
    override val familyName: String = "Material Symbols Rounded"

    val axes: MaterialSymbolAxes = MaterialSymbolAxes.Default

    override val fontSettings: SymbolFontSettings = axes.fontSettings
}
