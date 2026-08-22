package io.github.hlcaptain.symbols.material.sharp

import io.github.hlcaptain.symbols.font.SymbolFont
import io.github.hlcaptain.symbols.font.SymbolFontAxis
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.sharp.resources.Res
import io.github.hlcaptain.symbols.material.sharp.resources.symbolFonts

/**
 * The Material Symbols Sharp 2.874 variable font.
 *
 * This artifact contains exactly one font file. Its supported axes are `FILL`, `wght`,
 * `GRAD`, and `opsz`.
 */
object MaterialSymbolsSharp :
    SymbolFont.Variable by Res.symbolFonts.material_symbols_sharp_variable {
    override val familyName: String = "Material Symbols Sharp"

    override val variationAxes: List<SymbolFontAxis> = MaterialSymbolAxes.VariationAxes
}
