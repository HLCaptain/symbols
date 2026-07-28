package io.github.hlcaptain.symbols.material.rounded

import io.github.hlcaptain.symbols.material.MaterialSymbolFont
import io.github.hlcaptain.symbols.material.rounded.resources.Res
import io.github.hlcaptain.symbols.material.rounded.resources.material_symbols_rounded_variable
import org.jetbrains.compose.resources.FontResource

/**
 * The Material Symbols Rounded 2.874 variable font.
 *
 * This artifact contains exactly one font file. Its supported axes are `FILL`, `wght`,
 * `GRAD`, and `opsz`.
 */
public object MaterialSymbolsRounded : MaterialSymbolFont {
    override val familyName: String = "Material Symbols Rounded"

    override val resource: FontResource
        get() = Res.font.material_symbols_rounded_variable
}
