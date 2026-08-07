package io.github.hlcaptain.symbols.material.outlined

import io.github.hlcaptain.symbols.font.SymbolVariableFont
import io.github.hlcaptain.symbols.material.outlined.resources.Res
import io.github.hlcaptain.symbols.material.outlined.resources.material_symbols_outlined_variable
import org.jetbrains.compose.resources.FontResource

/**
 * The Material Symbols Outlined 2.874 variable font.
 *
 * This artifact contains exactly one font file. Its supported axes are `FILL`, `wght`,
 * `GRAD`, and `opsz`.
 */
public object MaterialSymbolsOutlined : SymbolVariableFont {
    override val familyName: String = "Material Symbols Outlined"

    override val resource: FontResource
        get() = Res.font.material_symbols_outlined_variable
}
