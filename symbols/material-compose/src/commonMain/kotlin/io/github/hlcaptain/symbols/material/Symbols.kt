package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.Immutable
import kotlin.jvm.JvmInline

/**
 * Compose-style namespace for font-backed Material Symbols.
 *
 * Generated properties such as `Symbols.Rounded.Check` retain their visual
 * style in the Kotlin type while exposing the shared [MaterialSymbol].
 */
object Symbols {
    /** Material Symbols Outlined font glyphs. */
    object Outlined

    /** Material Symbols Rounded font glyphs. */
    object Rounded

    /** Material Symbols Sharp font glyphs. */
    object Sharp
}

/** An allocation-free, style-typed Outlined font glyph. */
@Immutable
@JvmInline
value class OutlinedMaterialSymbol(val symbol: MaterialSymbol)

/** An allocation-free, style-typed Rounded font glyph. */
@Immutable
@JvmInline
value class RoundedMaterialSymbol(val symbol: MaterialSymbol)

/** An allocation-free, style-typed Sharp font glyph. */
@Immutable
@JvmInline
value class SharpMaterialSymbol(val symbol: MaterialSymbol)
