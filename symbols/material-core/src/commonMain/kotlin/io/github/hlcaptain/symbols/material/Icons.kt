package io.github.hlcaptain.symbols.material

import io.github.hlcaptain.symbols.Symbols

/**
 * Style markers for shrinkable, fixed-axis Material Symbol vectors.
 *
 * Add the artifact for each style you use, then import that artifact's generated
 * properties through the common root, for example
 * `Symbols.Material.Rounded.Check`.
 */
object Icons {
    /** Theme-selected Material Symbols vectors. */
    object Themed

    /** Material Symbols Outlined vectors. */
    object Outlined {
        /** Outlined vectors at FILL=1, GRAD=0, opsz=24, wght=400. */
        object Filled
    }

    /** Material Symbols Rounded vectors. */
    object Rounded {
        /** Rounded vectors at FILL=1, GRAD=0, opsz=24, wght=400. */
        object Filled
    }

    /** Material Symbols Sharp vectors. */
    object Sharp {
        /** Sharp vectors at FILL=1, GRAD=0, opsz=24, wght=400. */
        object Filled
    }

    /** Explicitly opt-in vectors that mirror in right-to-left layouts. */
    object AutoMirrored {
        /** Theme-selected Material Symbols vectors with automatic RTL mirroring. */
        object Themed

        /** Material Symbols Outlined vectors with automatic RTL mirroring. */
        object Outlined {
            /** Filled Outlined vectors with automatic RTL mirroring. */
            object Filled
        }

        /** Material Symbols Rounded vectors with automatic RTL mirroring. */
        object Rounded {
            /** Filled Rounded vectors with automatic RTL mirroring. */
            object Filled
        }

        /** Material Symbols Sharp vectors with automatic RTL mirroring. */
        object Sharp {
            /** Filled Sharp vectors with automatic RTL mirroring. */
            object Filled
        }
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

/** Outlined vectors at FILL=1; all other axes retain their defaults. */
val Icons.Outlined.Filled: Icons.Outlined.Filled
    get() = Icons.Outlined.Filled

/** Filled Outlined vectors with automatic RTL mirroring. */
val Icons.AutoMirrored.Outlined.Filled: Icons.AutoMirrored.Outlined.Filled
    get() = Icons.AutoMirrored.Outlined.Filled

/** Rounded vectors at FILL=1; all other axes retain their defaults. */
val Icons.Rounded.Filled: Icons.Rounded.Filled
    get() = Icons.Rounded.Filled

/** Filled Rounded vectors with automatic RTL mirroring. */
val Icons.AutoMirrored.Rounded.Filled: Icons.AutoMirrored.Rounded.Filled
    get() = Icons.AutoMirrored.Rounded.Filled

/** Sharp vectors at FILL=1; all other axes retain their defaults. */
val Icons.Sharp.Filled: Icons.Sharp.Filled
    get() = Icons.Sharp.Filled

/** Filled Sharp vectors with automatic RTL mirroring. */
val Icons.AutoMirrored.Sharp.Filled: Icons.AutoMirrored.Sharp.Filled
    get() = Icons.AutoMirrored.Sharp.Filled
