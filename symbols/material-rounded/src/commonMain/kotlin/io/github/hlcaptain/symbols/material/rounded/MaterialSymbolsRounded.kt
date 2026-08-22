package io.github.hlcaptain.symbols.material.rounded

import io.github.hlcaptain.symbols.font.SymbolFont
import io.github.hlcaptain.symbols.font.SymbolFontAxis
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.rounded.resources.Res
import io.github.hlcaptain.symbols.material.rounded.resources.symbolFonts

/**
 * The Material Symbols Rounded 2.874 variable font.
 *
 * This artifact contains exactly one font file. Its supported axes are `FILL`, `wght`,
 * `GRAD`, and `opsz`.
 */
object MaterialSymbolsRounded :
    SymbolFont.Variable by Res.symbolFonts.material_symbols_rounded_variable {
    override val familyName: String = "Material Symbols Rounded"

    override val variationAxes: List<SymbolFontAxis> = MaterialSymbolAxes.VariationAxes
}
