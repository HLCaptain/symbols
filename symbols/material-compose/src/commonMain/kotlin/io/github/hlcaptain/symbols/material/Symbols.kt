package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.Immutable
import kotlin.jvm.JvmInline

/**
 * Compose-style namespace for font-backed Material Symbols.
 *
 * Generated properties such as `Symbols.Rounded.Check` retain their visual
 * style in the Kotlin type while exposing the shared [MaterialSymbol].
 */
public object Symbols {
    /** Material Symbols Outlined font glyphs. */
    public object Outlined

    /** Material Symbols Rounded font glyphs. */
    public object Rounded

    /** Material Symbols Sharp font glyphs. */
    public object Sharp
}

/** An allocation-free, style-typed Outlined font glyph. */
@Immutable
@JvmInline
public value class OutlinedMaterialSymbol(public val symbol: MaterialSymbol)

/** An allocation-free, style-typed Rounded font glyph. */
@Immutable
@JvmInline
public value class RoundedMaterialSymbol(public val symbol: MaterialSymbol)

/** An allocation-free, style-typed Sharp font glyph. */
@Immutable
@JvmInline
public value class SharpMaterialSymbol(public val symbol: MaterialSymbol)
