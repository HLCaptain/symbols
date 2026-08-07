package io.github.hlcaptain.symbols.material.outlined

import io.github.hlcaptain.symbols.font.SymbolFont
import io.github.hlcaptain.symbols.font.SymbolFontAxis
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.outlined.resources.Res
import io.github.hlcaptain.symbols.material.outlined.resources.material_symbols_outlined_variable
import org.jetbrains.compose.resources.FontResource

/**
 * The Material Symbols Outlined 2.874 variable font.
 *
 * This artifact contains exactly one font file. Its supported axes are `FILL`, `wght`,
 * `GRAD`, and `opsz`.
 */
object MaterialSymbolsOutlined : SymbolFont.Variable {
    override val familyName: String = "Material Symbols Outlined"

    override val variationAxes: List<SymbolFontAxis> = MaterialSymbolAxes.VariationAxes

    override val resource: FontResource
        get() = Res.font.material_symbols_outlined_variable
}
