package io.github.hlcaptain.symbols.generator

import kotlin.math.min

/** An invalid manifest, font, outline, or generation request. */
public class SymbolGenerationException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** A semantic name and Unicode scalar in an icon-font manifest. */
public data class SymbolEntry(
    public val name: String,
    public val codePoint: Int,
) {
    init {
        require(SymbolNames.isCanonicalName(name)) {
            "Invalid symbol name '$name'; expected lowercase snake_case"
        }
        require(codePoint.isUnicodeScalar()) {
            "U+${codePoint.toString(16).uppercase()} is not a Unicode scalar value"
        }
    }

    /** The public simple PascalCase Kotlin property name. */
    public val kotlinName: String
        get() = SymbolNames.kotlinIdentifier(name)
}

/**
 * A validated semantic catalog.
 *
 * Entries are stored in canonical-name order. Multiple names may intentionally
 * address the same code point, but duplicate names and Kotlin-name collisions
 * are rejected.
 */
public class SymbolCatalog private constructor(
    entries: List<SymbolEntry>,
) {
    public val entries: List<SymbolEntry> = entries.sortedBy(SymbolEntry::name)

    public val entriesByCodePoint: Map<Int, List<SymbolEntry>> =
        this.entries
            .groupBy(SymbolEntry::codePoint)
            .toSortedMap()
            .mapValues { (_, aliases) -> aliases.sortedBy(SymbolEntry::name) }

    public val uniqueCodePoints: List<Int>
        get() = entriesByCodePoint.keys.toList()

    public companion object {
        /** Validates and creates a catalog from [entries]. */
        public fun of(entries: Iterable<SymbolEntry>): SymbolCatalog {
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
public data class VectorPoint(
    public val x: Float,
    public val y: Float,
) {
    init {
        require(x.isFinite() && y.isFinite()) {
            "Vector coordinates must be finite: ($x, $y)"
        }
    }
}

/** An engine-neutral vector path command. */
public sealed interface VectorCommand {
    public data class MoveTo(public val point: VectorPoint) : VectorCommand

    public data class LineTo(public val point: VectorPoint) : VectorCommand

    public data class QuadraticTo(
        public val control: VectorPoint,
        public val end: VectorPoint,
    ) : VectorCommand

    public data class CubicTo(
        public val control1: VectorPoint,
        public val control2: VectorPoint,
        public val end: VectorPoint,
    ) : VectorCommand

    public data object Close : VectorCommand
}

/** One nonempty, normalized monochrome glyph outline. */
public data class GlyphOutline(
    public val codePoint: Int,
    public val commands: List<VectorCommand>,
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
public data class ExtractedFont(
    public val familyName: String,
    public val unitsPerEm: Int,
    public val appliedAxes: Map<String, Float>,
    public val outlines: Map<Int, GlyphOutline>,
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
public data class OutlineTransform(
    public val viewportWidth: Float = 24f,
    public val viewportHeight: Float = 24f,
    public val emSize: Float = min(viewportWidth, viewportHeight),
    public val originX: Float = 0f,
    public val baselineY: Float = viewportHeight,
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
public data class GeneratedStyle(
    public val name: String,
    public val catalog: SymbolCatalog,
    public val font: ExtractedFont,
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
public data class GeneratedIconSet(
    public val packageName: String,
    public val name: String,
    public val styles: List<GeneratedStyle>,
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
