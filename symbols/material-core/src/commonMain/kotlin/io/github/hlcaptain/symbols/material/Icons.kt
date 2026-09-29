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

    /** Explicitly opt-in vectors that mirror in right-to-left layouts. */
    object AutoMirrored {
        /** Theme-selected Material Symbols vectors with automatic RTL mirroring. */
        object Themed

        /** Material Symbols Outlined vectors with automatic RTL mirroring. */
        object Outlined

        /** Material Symbols Rounded vectors with automatic RTL mirroring. */
        object Rounded

        /** Material Symbols Sharp vectors with automatic RTL mirroring. */
        object Sharp
    }
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

/** Explicitly opt-in vectors that mirror in right-to-left layouts. */
val MaterialSymbols.AutoMirrored: Icons.AutoMirrored
    get() = Icons.AutoMirrored

/** Theme-selected Material Symbols vectors with automatic RTL mirroring. */
val Icons.AutoMirrored.Themed: Icons.AutoMirrored.Themed
    get() = Icons.AutoMirrored.Themed

/** Material Symbols Outlined vectors with automatic RTL mirroring. */
val Icons.AutoMirrored.Outlined: Icons.AutoMirrored.Outlined
    get() = Icons.AutoMirrored.Outlined

/** Material Symbols Rounded vectors with automatic RTL mirroring. */
val Icons.AutoMirrored.Rounded: Icons.AutoMirrored.Rounded
    get() = Icons.AutoMirrored.Rounded

/** Material Symbols Sharp vectors with automatic RTL mirroring. */
val Icons.AutoMirrored.Sharp: Icons.AutoMirrored.Sharp
    get() = Icons.AutoMirrored.Sharp
