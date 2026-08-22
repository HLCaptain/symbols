package io.github.hlcaptain.symbols.generator

import kotlin.math.min

/** An invalid manifest, font, outline, or generation request. */
class SymbolGenerationException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** A semantic name and Unicode scalar in an icon-font manifest. */
data class SymbolEntry(
    val name: String,
    val codePoint: Int,
) {
    init {
        require(SymbolNames.isCanonicalName(name)) {
            "Invalid symbol name '$name'; expected lowercase snake_case"
        }
        require(codePoint.isUnicodeScalar()) {
            "U+${codePoint.toString(16).uppercase()} is not a Unicode scalar value"
        }
    }

    val kotlinName: String
        get() = SymbolNames.kotlinIdentifier(name)
}

/**
 * A validated semantic catalog.
 *
 * Entries are stored in canonical-name order. Multiple names may intentionally
 * address the same code point, but duplicate names and Kotlin-name collisions
 * are rejected.
 */
class SymbolCatalog private constructor(
    entries: List<SymbolEntry>,
) {
    val entries: List<SymbolEntry> = entries.sortedBy(SymbolEntry::name)

    val entriesByCodePoint: Map<Int, List<SymbolEntry>> =
        this.entries
            .groupBy(SymbolEntry::codePoint)
            .toSortedMap()
            .mapValues { (_, aliases) -> aliases.sortedBy(SymbolEntry::name) }

    val uniqueCodePoints: List<Int>
        get() = entriesByCodePoint.keys.toList()

    companion object {
        /** Validates and creates a catalog from [entries]. */
        fun of(entries: Iterable<SymbolEntry>): SymbolCatalog {
            val materialized = entries.toList()
            if (materialized.isEmpty()) {
                throw SymbolGenerationException("A symbol catalog must not be empty")
            }

            val duplicateNames = materialized
                .groupingBy(SymbolEntry::name)
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
                .sorted()
            if (duplicateNames.isNotEmpty()) {
                throw SymbolGenerationException(
                    "Duplicate symbol names: ${duplicateNames.joinToString()}",
                )
            }

            val collisions = materialized
                .groupBy(SymbolEntry::kotlinName)
                .filterValues { values ->
                    values.map(SymbolEntry::name).distinct().size > 1
                }
            if (collisions.isNotEmpty()) {
                val details = collisions.entries
                    .sortedBy { (identifier, _) -> identifier }
                    .joinToString("; ") { (identifier, values) ->
                        "$identifier <- ${values.map(SymbolEntry::name).sorted().joinToString()}"
                    }
                throw SymbolGenerationException(
                    "Kotlin identifier collisions: $details",
                )
            }

            return SymbolCatalog(materialized)
        }
    }
}

/** A two-dimensional point in the normalized output viewport. */
data class VectorPoint(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite() && y.isFinite()) {
            "Vector coordinates must be finite: ($x, $y)"
        }
    }
}

/** An engine-neutral vector path command. */
sealed interface VectorCommand {
    data class MoveTo(val point: VectorPoint) : VectorCommand

    data class LineTo(val point: VectorPoint) : VectorCommand

    data class QuadraticTo(
        val control: VectorPoint,
        val end: VectorPoint,
    ) : VectorCommand

    data class CubicTo(
        val control1: VectorPoint,
        val control2: VectorPoint,
        val end: VectorPoint,
    ) : VectorCommand

    data object Close : VectorCommand
}

/** Fill rule for one generated vector path. */
enum class VectorFillRule {
    NonZero,
    EvenOdd,
}

/** End-cap treatment for one generated stroked path. */
enum class VectorStrokeCap {
    Butt,
    Round,
    Square,
}

/** Join treatment for one generated stroked path. */
enum class VectorStrokeJoin {
    Miter,
    Round,
    Bevel,
}

/** One monochrome path, retaining fill and stroke information from its source. */
data class StyledVectorPath(
    val commands: List<VectorCommand>,
    val fill: Boolean,
    val fillAlpha: Float = 1f,
    val stroke: Boolean,
    val strokeAlpha: Float = 1f,
    val strokeWidth: Float = 1f,
    val strokeCap: VectorStrokeCap = VectorStrokeCap.Butt,
    val strokeJoin: VectorStrokeJoin = VectorStrokeJoin.Miter,
    val strokeMiterLimit: Float = 4f,
    val fillRule: VectorFillRule = VectorFillRule.NonZero,
) {
    init {
        require(commands.isNotEmpty()) { "A styled vector path must not be empty" }
        require(commands.first() is VectorCommand.MoveTo) {
            "A styled vector path must begin with MoveTo"
        }
        require(fill || stroke) { "A styled vector path must paint a fill or stroke" }
        require(fillAlpha.isFinite() && fillAlpha in 0f..1f) {
            "fillAlpha must be finite and in 0..1"
        }
        require(strokeAlpha.isFinite() && strokeAlpha in 0f..1f) {
            "strokeAlpha must be finite and in 0..1"
        }
        require(strokeWidth.isFinite() && strokeWidth >= 0f) {
            "strokeWidth must be finite and non-negative"
        }
        require(strokeMiterLimit.isFinite() && strokeMiterLimit >= 0f) {
            "strokeMiterLimit must be finite and non-negative"
        }
    }
}

/** One SVG-derived icon identified by its semantic file name. */
data class SvgIcon(
    val name: String,
    val paths: List<StyledVectorPath>,
) {
    init {
        require(SymbolNames.isCanonicalName(name)) {
            "Invalid SVG icon name '$name'; expected lowercase snake_case"
        }
        require(paths.isNotEmpty()) { "SVG icon '$name' has no painted paths" }
    }

    val kotlinName: String
        get() = SymbolNames.kotlinIdentifier(name)
}

/** One generated SVG style. */
data class GeneratedSvgStyle(
    val name: String,
    val icons: List<SvgIcon>,
) {
    init {
        SymbolNames.requireTypeIdentifier(name, "style name")
        require(icons.isNotEmpty()) { "SVG style $name must contain at least one icon" }
        val duplicateNames = icons
            .groupingBy(SvgIcon::name)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateNames.isEmpty()) {
            "Duplicate SVG icon names: ${duplicateNames.sorted().joinToString()}"
        }
        val kotlinCollisions = icons
            .groupBy(SvgIcon::kotlinName)
            .filterValues { values -> values.map(SvgIcon::name).distinct().size > 1 }
        require(kotlinCollisions.isEmpty()) {
            "SVG Kotlin identifier collisions: " +
                kotlinCollisions.entries
                    .sortedBy(Map.Entry<String, List<SvgIcon>>::key)
                    .joinToString("; ") { (identifier, values) ->
                        "$identifier <- ${values.map(SvgIcon::name).sorted().joinToString()}"
                    }
        }
    }
}

/** A generated SVG root namespace containing one or more styles. */
data class GeneratedSvgIconSet(
    val packageName: String,
    val name: String,
    val styles: List<GeneratedSvgStyle>,
) {
    init {
        SymbolNames.requirePackageName(packageName)
        SymbolNames.requireTypeIdentifier(name, "icon-set name")
        require(styles.isNotEmpty()) { "At least one SVG style is required" }
        val duplicateStyles = styles
            .groupingBy(GeneratedSvgStyle::name)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateStyles.isEmpty()) {
            "Duplicate styles: ${duplicateStyles.sorted().joinToString()}"
        }
        SymbolNames.requireDistinctStylePackageSegments(
            styles.map(GeneratedSvgStyle::name),
        )
    }
}

/** One nonempty, normalized monochrome glyph outline. */
data class GlyphOutline(
    val codePoint: Int,
    val commands: List<VectorCommand>,
) {
    init {
        require(codePoint.isUnicodeScalar()) {
            "Invalid outline code point: U+${codePoint.toString(16).uppercase()}"
        }
        require(commands.isNotEmpty()) {
            "U+${codePoint.toString(16).uppercase()} has an empty outline"
        }
        require(commands.first() is VectorCommand.MoveTo) {
            "U+${codePoint.toString(16).uppercase()} must begin with MoveTo"
        }
    }
}

/** The result of extracting one font at one static axis location. */
data class ExtractedFont(
    val familyName: String,
    val unitsPerEm: Int,
    val appliedAxes: Map<String, Float>,
    val outlines: Map<Int, GlyphOutline>,
) {
    init {
        require(unitsPerEm > 0) { "unitsPerEm must be positive" }
        require(outlines.isNotEmpty()) { "At least one outline is required" }
        outlines.forEach { (codePoint, outline) ->
            require(codePoint == outline.codePoint) {
                "Outline map key U+${codePoint.toString(16).uppercase()} does not " +
                    "match U+${outline.codePoint.toString(16).uppercase()}"
            }
        }
    }
}

/**
 * Maps Skia's baseline-relative coordinates into an output viewport.
 *
 * [emSize] is the viewport size occupied by one font em. Skia already exposes
 * glyph paths in a y-down coordinate system, so the default baseline at the
 * viewport bottom maps a 0..UPEM icon-font square to 0..24.
 */
data class OutlineTransform(
    val viewportWidth: Float = 24f,
    val viewportHeight: Float = 24f,
    val emSize: Float = min(viewportWidth, viewportHeight),
    val originX: Float = 0f,
    val baselineY: Float = viewportHeight,
) {
    init {
        require(viewportWidth.isFinite() && viewportWidth > 0f) {
            "viewportWidth must be finite and positive"
        }
        require(viewportHeight.isFinite() && viewportHeight > 0f) {
            "viewportHeight must be finite and positive"
        }
        require(emSize.isFinite() && emSize > 0f) {
            "emSize must be finite and positive"
        }
        require(originX.isFinite() && baselineY.isFinite()) {
            "Viewport origins must be finite"
        }
    }

    internal fun apply(x: Float, y: Float, unitsPerEm: Int): VectorPoint {
        val scale = emSize / unitsPerEm
        return VectorPoint(
            x = originX + x * scale,
            y = baselineY + y * scale,
        )
    }
}

/** One generated icon style and its validated semantic catalog. */
data class GeneratedStyle(
    val name: String,
    val catalog: SymbolCatalog,
    val font: ExtractedFont,
) {
    init {
        SymbolNames.requireTypeIdentifier(name, "style name")
        val missing = catalog.uniqueCodePoints.filterNot(font.outlines::containsKey)
        require(missing.isEmpty()) {
            "Style $name is missing outlines: " +
                missing.take(10).joinToString { "U+${it.toString(16).uppercase()}" }
        }
    }
}

/** A generated root namespace containing one or more styles. */
data class GeneratedIconSet(
    val packageName: String,
    val name: String,
    val styles: List<GeneratedStyle>,
) {
    init {
        SymbolNames.requirePackageName(packageName)
        SymbolNames.requireTypeIdentifier(name, "icon-set name")
        require(styles.isNotEmpty()) { "At least one style is required" }
        val duplicateStyles = styles
            .groupingBy(GeneratedStyle::name)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateStyles.isEmpty()) {
            "Duplicate styles: ${duplicateStyles.sorted().joinToString()}"
        }
        SymbolNames.requireDistinctStylePackageSegments(
            styles.map(GeneratedStyle::name),
        )
    }
}

internal fun Int.isUnicodeScalar(): Boolean =
    this in 0..0x10FFFF && this !in 0xD800..0xDFFF
