package io.github.hlcaptain.symbols.material

import kotlin.jvm.JvmInline

/**
 * A stable named entry in the Material Symbols catalog.
 *
 * Identity belongs to the catalog entry rather than only its code point:
 * aliases with the same [codePoint] remain distinct values with distinct
 * [name] properties.
 */
@JvmInline
value class MaterialSymbol internal constructor(private val catalogIndex: Int) {
    /** The canonical snake_case name from the Material Symbols codepoints map. */
    val name: String
        get() = MATERIAL_SYMBOL_NAMES[catalogIndex]

    /** The Unicode scalar value assigned to this named symbol. */
    val codePoint: Int
        get() = MATERIAL_SYMBOL_CODE_POINTS[catalogIndex]

    /**
     * This symbol encoded as Unicode text.
     *
     * Supplementary Unicode scalar values are encoded as a UTF-16 surrogate
     * pair, so this API is not limited to the Basic Multilingual Plane.
     */
    val text: String
        get() = materialSymbolText(codePoint)

    /** Returns the exact snake_case [name] of this catalog entry. */
    override fun toString(): String = name
}

/**
 * Namespace and lookup API for the generated Material Symbols catalog.
 *
 * Named extension properties are generated from the canonical codepoints map
 * and exposed through entry points such as `Symbols.Material.Home`. They
 * return an inline catalog handle and do not construct a new symbol object on
 * each access.
 */
object MaterialSymbols {
    /** Every named entry in canonical name order, including aliases. */
    val all: List<MaterialSymbol>
        get() = AllMaterialSymbols

    /** Number of named entries, including aliases. */
    val size: Int
        get() = MATERIAL_SYMBOL_NAMES.size

    /**
     * Returns the exact named catalog entry, or `null` if [name] is unknown.
     *
     * Use this for a name read from storage, navigation, or other runtime input. Names are
     * case-sensitive snake_case values; this function does not trim, normalize, or change case.
     *
     * @param name exact snake_case catalog name to find
     * @return the matching named entry, or `null` when no exact match exists
     */
    fun fromName(name: String): MaterialSymbol? {
        var low = 0
        var high = MATERIAL_SYMBOL_NAMES.lastIndex
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val comparison = MATERIAL_SYMBOL_NAMES[middle].compareTo(name)
            when {
                comparison < 0 -> low = middle + 1
                comparison > 0 -> high = middle - 1
                else -> return symbolAt(middle)
            }
        }
        return null
    }

    /**
     * Returns all named entries assigned to [codePoint].
     *
     * Several names can identify the same glyph. The read-only result preserves each distinct name
     * and is ordered by name. An unassigned value, including an integer that is not a
     * valid Unicode scalar value, returns an empty list rather than throwing.
     *
     * @param codePoint Unicode value whose named entries should be returned
     * @return every matching catalog entry, or an empty list when the value is unassigned
     */
    fun aliases(codePoint: Int): List<MaterialSymbol> {
        val start = lowerCodePointBound(codePoint)
        if (
            start == MATERIAL_SYMBOL_CODE_POINT_ORDER.size ||
            codePointAtOrder(start) != codePoint
        ) {
            return emptyList()
        }
        val end = upperCodePointBound(codePoint, start)
        return MaterialSymbolAliases(start, end)
    }

    /**
     * Returns every named entry sharing [symbol]'s code point.
     *
     * The read-only result includes [symbol] and any differently named aliases, ordered by
     * name.
     *
     * @param symbol catalog entry whose aliases should be returned
     * @return all entries that use the same code point
     */
    fun aliases(symbol: MaterialSymbol): List<MaterialSymbol> =
        aliases(symbol.codePoint)

    internal fun symbolAt(index: Int): MaterialSymbol = MaterialSymbol(index)

    private fun codePointAtOrder(orderIndex: Int): Int =
        MATERIAL_SYMBOL_CODE_POINTS[MATERIAL_SYMBOL_CODE_POINT_ORDER[orderIndex]]

    private fun lowerCodePointBound(codePoint: Int): Int {
        var low = 0
        var high = MATERIAL_SYMBOL_CODE_POINT_ORDER.size
        while (low < high) {
            val middle = (low + high).ushr(1)
            if (codePointAtOrder(middle) < codePoint) {
                low = middle + 1
            } else {
                high = middle
            }
        }
        return low
    }

    private fun upperCodePointBound(codePoint: Int, lowerBound: Int): Int {
        var low = lowerBound
        var high = MATERIAL_SYMBOL_CODE_POINT_ORDER.size
        while (low < high) {
            val middle = (low + high).ushr(1)
            if (codePointAtOrder(middle) <= codePoint) {
                low = middle + 1
            } else {
                high = middle
            }
        }
        return low
    }
}

private object AllMaterialSymbols : AbstractList<MaterialSymbol>(), RandomAccess {
    override val size: Int
        get() = MATERIAL_SYMBOL_NAMES.size

    override fun get(index: Int): MaterialSymbol {
        checkElementIndex(index, size)
        return MaterialSymbols.symbolAt(index)
    }
}

private class MaterialSymbolAliases(
    private val start: Int,
    private val end: Int,
) : AbstractList<MaterialSymbol>(), RandomAccess {
    override val size: Int
        get() = end - start

    override fun get(index: Int): MaterialSymbol {
        checkElementIndex(index, size)
        val catalogIndex = MATERIAL_SYMBOL_CODE_POINT_ORDER[start + index]
        return MaterialSymbols.symbolAt(catalogIndex)
    }
}

private fun checkElementIndex(index: Int, size: Int) {
    if (index < 0 || index >= size) {
        throw IndexOutOfBoundsException("index: $index, size: $size")
    }
}

/**
 * Encodes [codePoint] as Unicode text for a symbol-font glyph.
 *
 * This is useful for custom/private-use code points that are not part of the
 * generated [MaterialSymbols] catalog. Supplementary scalar values are
 * returned as a UTF-16 surrogate pair. This function does not search the
 * catalog, load a font, or check whether a font contains the requested glyph.
 *
 * @param codePoint Unicode scalar value to encode
 * @return a one-code-point string suitable for a text renderer
 * @throws IllegalArgumentException if [codePoint] is outside the Unicode range
 * or is a surrogate code point.
 */
fun materialSymbolText(codePoint: Int): String {
    require(
        codePoint in 0..0x10FFFF &&
            codePoint !in 0xD800..0xDFFF
    ) {
        "Not a Unicode scalar value: U+${codePoint.toString(16).uppercase()}"
    }

    if (codePoint <= 0xFFFF) {
        return codePoint.toChar().toString()
    }

    val supplementary = codePoint - 0x10000
    val highSurrogate = 0xD800 + (supplementary ushr 10)
    val lowSurrogate = 0xDC00 + (supplementary and 0x3FF)
    return charArrayOf(highSurrogate.toChar(), lowSurrogate.toChar()).concatToString()
}
