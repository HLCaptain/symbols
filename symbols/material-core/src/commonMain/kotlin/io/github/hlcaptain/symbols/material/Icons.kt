package io.github.hlcaptain.symbols.material

import io.github.hlcaptain.symbols.Symbols

/**
 * Style markers for shrinkable, default-axis Material Symbol vectors.
 *
 * Add the artifact for each style you use, then import that artifact's generated
 * properties through the common root, for example
 * `Symbols.Material.Rounded.Check`.
 */
object Icons {
    /** Theme-selected Material Symbols vectors. */
    object Themed

    /** Material Symbols Outlined vectors. */
    object Outlined

    /** Material Symbols Rounded vectors. */
    object Rounded

    /** Material Symbols Sharp vectors. */
    object Sharp
}

/** Material catalog entry point. */
val Symbols.Material: MaterialSymbols
    get() = MaterialSymbols

/** Theme-selected Material vectors. */
val MaterialSymbols.Themed: Icons.Themed
    get() = Icons.Themed

/** Material Symbols Outlined vectors. */
val MaterialSymbols.Outlined: Icons.Outlined
    get() = Icons.Outlined

/** Material Symbols Rounded vectors. */
val MaterialSymbols.Rounded: Icons.Rounded
    get() = Icons.Rounded

/** Material Symbols Sharp vectors. */
val MaterialSymbols.Sharp: Icons.Sharp
    get() = Icons.Sharp
