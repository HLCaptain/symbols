package io.github.hlcaptain.symbols.material.rounded

import io.github.hlcaptain.symbols.font.SymbolFont
import io.github.hlcaptain.symbols.font.SymbolFontAxis
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.rounded.resources.Res
import io.github.hlcaptain.symbols.material.rounded.resources.material_symbols_rounded_variable
import org.jetbrains.compose.resources.FontResource

/**
 * The Material Symbols Rounded 2.874 variable font.
 *
 * This artifact contains exactly one font file. Its supported axes are `FILL`, `wght`,
 * `GRAD`, and `opsz`.
 */
object MaterialSymbolsRounded : SymbolFont.Variable {
    override val familyName: String = "Material Symbols Rounded"

    override val variationAxes: List<SymbolFontAxis> = MaterialSymbolAxes.VariationAxes

    override val resource: FontResource
        get() = Res.font.material_symbols_rounded_variable
}
