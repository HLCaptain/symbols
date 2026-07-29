package io.github.hlcaptain.symbols.generator

import java.nio.file.Path

/** Immutable inputs for extracting monochrome outlines from one font instance. */
public data class FontExtractionRequest(
    public val fontFile: Path,
    public val codePoints: Collection<Int>,
    public val fontIndex: Int = 0,
    public val axes: Map<String, Float> = emptyMap(),
    public val transform: OutlineTransform = OutlineTransform(),
    public val conicTolerance: Float = 0.002f,
) {
    init {
        require(fontIndex >= 0) { "fontIndex must not be negative" }
        require(codePoints.isNotEmpty()) { "At least one code point is required" }
        codePoints.forEach { codePoint ->
            require(codePoint.isUnicodeScalar()) {
                "Invalid requested code point: U+${codePoint.toString(16).uppercase()}"
            }
        }
        axes.forEach { (tag, value) ->
            require(tag.length == 4 && tag.all { it.code in 0x20..0x7E }) {
                "OpenType axis tags must contain four printable ASCII characters: '$tag'"
            }
            require(value.isFinite()) { "Axis $tag must be finite, but was $value" }
        }
        require(conicTolerance.isFinite() && conicTolerance > 0f) {
            "conicTolerance must be finite and positive"
        }
    }
}

/** An outline engine used by the deterministic source renderers. */
public fun interface FontOutlineExtractor {
    public fun extract(request: FontExtractionRequest): ExtractedFont
}
