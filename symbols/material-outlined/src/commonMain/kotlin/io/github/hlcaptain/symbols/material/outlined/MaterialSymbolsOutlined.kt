package io.github.hlcaptain.symbols.material.outlined

import io.github.hlcaptain.symbols.font.SymbolFont
import io.github.hlcaptain.symbols.font.SymbolFontAxis
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.outlined.resources.Res
import io.github.hlcaptain.symbols.material.outlined.resources.symbolFonts

/**
 * The Material Symbols Outlined 2.874 variable font.
 *
 * This artifact contains exactly one font file. Its supported axes are `FILL`, `wght`,
 * `GRAD`, and `opsz`.
 */
object MaterialSymbolsOutlined :
    SymbolFont.Variable by Res.symbolFonts.material_symbols_outlined_variable {
    override val familyName: String = "Material Symbols Outlined"

    override val variationAxes: List<SymbolFontAxis> = MaterialSymbolAxes.VariationAxes
}
