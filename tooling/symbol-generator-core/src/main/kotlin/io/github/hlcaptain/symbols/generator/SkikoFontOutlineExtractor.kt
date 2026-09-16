package io.github.hlcaptain.symbols.generator

import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.max
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontHinting
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontVariation
import org.jetbrains.skia.PathSegment
import org.jetbrains.skia.PathVerb
import org.jetbrains.skia.Point
import org.jetbrains.skia.Typeface

/**
 * Loads TTF, OTF, or TTC fonts with pinned Skiko and extracts one static
 * instance. Variable coordinates are validated against the font's `fvar` axes.
 */
class SkikoFontOutlineExtractor : FontOutlineExtractor {
    /**
     * Reads a TTF, OTF, or TTC file with Skiko and extracts the requested glyph
     * outlines at one fixed set of variable-axis values.
     *
     * Axes omitted from [FontExtractionRequest.axes] use the defaults stored in
     * the font. Font and native Skiko resources opened by this call are closed
     * before it returns. The source font is never changed and no output files
     * are written.
     *
     * @param request font face, code points, axis values, coordinate mapping,
     * and curve-conversion tolerance to use.
     * @return normalized outlines plus the family name and axis values that were
     * actually applied.
     * @throws SymbolGenerationException if the file cannot be opened, the face
     * or axis settings are invalid, or a requested glyph has no usable outline.
     */
    override fun extract(request: FontExtractionRequest): ExtractedFont {
        return loadSkikoTypeface(request.fontFile, request.fontIndex).use { base ->
            val (typeface, appliedAxes) = configureAxes(base, request)
            if (typeface === base) {
                extractConfiguredTypeface(typeface, request, appliedAxes)
            } else {
                typeface.use {
                    extractConfiguredTypeface(it, request, appliedAxes)
                }
            }
        }
    }

    private fun configureAxes(
        base: Typeface,
        request: FontExtractionRequest,
    ): Pair<Typeface, Map<String, Float>> {
        val availableAxes = base.variationAxes
            .orEmpty()
            .associateBy { axis -> axis.tag }
            .toSortedMap()

        if (request.axes.isEmpty()) {
            val defaults = availableAxes.mapValues { (_, axis) -> axis.defaultValue }
            return base to defaults
        }
        if (availableAxes.isEmpty()) {
            throw SymbolGenerationException(
                "${request.fontFile} is not variable but axes were requested: " +
                    request.axes.keys.sorted().joinToString(),
            )
        }

        val unknown = request.axes.keys - availableAxes.keys
        if (unknown.isNotEmpty()) {
            throw SymbolGenerationException(
                "${request.fontFile} does not define axes: ${unknown.sorted().joinToString()}; " +
                    "available axes: ${availableAxes.keys.joinToString()}",
            )
        }

        val resolved = availableAxes.mapValues { (tag, axis) ->
            val value = request.axes[tag] ?: axis.defaultValue
            if (value !in axis.minValue..axis.maxValue) {
                throw SymbolGenerationException(
                    "$tag=$value is outside ${axis.minValue}..${axis.maxValue} " +
                        "in ${request.fontFile}",
                )
            }
            value
        }
        val variations = resolved
            .map { (tag, value) -> FontVariation(tag, value) }
            .toTypedArray()
        return base.makeClone(variations) to resolved
    }

    private fun extractConfiguredTypeface(
        typeface: Typeface,
        request: FontExtractionRequest,
        appliedAxes: Map<String, Float>,
    ): ExtractedFont {
        val unitsPerEm = typeface.unitsPerEm
        if (unitsPerEm <= 0) {
            throw SymbolGenerationException(
                "${request.fontFile} reports invalid unitsPerEm=$unitsPerEm",
            )
        }

        val outlines = linkedMapOf<Int, GlyphOutline>()
        Font(typeface, unitsPerEm.toFloat()).use { font ->
            font.isLinearMetrics = true
            font.hinting = FontHinting.NONE

            request.codePoints.distinct().sorted().forEach { codePoint ->
                val glyphId = typeface.getUTF32Glyph(codePoint)
                if (glyphId.toInt() == 0) {
                    throw SymbolGenerationException(
                        "${request.fontFile} does not map " +
                            "U+${codePoint.toString(16).uppercase()}",
                    )
                }
                val path = font.getPath(glyphId)
                    ?: throw SymbolGenerationException(
                        "${request.fontFile} has no outline for " +
                            "U+${codePoint.toString(16).uppercase()}",
                    )
                path.use {
                    if (it.isEmpty) {
                        throw SymbolGenerationException(
                            "${request.fontFile} has an empty outline for " +
                                "U+${codePoint.toString(16).uppercase()}",
                        )
                    }
                    it.iterator(false).use { iterator ->
                        outlines[codePoint] = GlyphOutline(
                            codePoint = codePoint,
                            commands = convertSkikoPath(
                                pathSegments = iterator.asSequence()
                                    .filterNotNull(),
                                pointTransform = { point ->
                                    request.transform.apply(
                                        point.x,
                                        point.y,
                                        unitsPerEm,
                                    )
                                },
                                conicTolerance = request.conicTolerance,
                            ),
                        )
                    }
                }
            }
        }

        return ExtractedFont(
            familyName = typeface.familyName,
            unitsPerEm = unitsPerEm,
            appliedAxes = appliedAxes,
            outlines = outlines,
        )
    }
}

internal fun convertSkikoPath(
    pathSegments: Sequence<PathSegment>,
    pointTransform: (Point) -> VectorPoint,
    conicTolerance: Float,
): List<VectorCommand> = buildList {
    pathSegments.forEach { segment ->
        fun Point.normalized(): VectorPoint = pointTransform(this)

        when (segment.verb) {
            PathVerb.MOVE -> add(
                VectorCommand.MoveTo(segment.p0.required("MOVE p0").normalized()),
            )
            PathVerb.LINE -> add(
                VectorCommand.LineTo(segment.p1.required("LINE p1").normalized()),
            )
            PathVerb.QUAD -> add(
                VectorCommand.QuadraticTo(
                    control = segment.p1.required("QUAD p1").normalized(),
                    end = segment.p2.required("QUAD p2").normalized(),
                ),
            )
            PathVerb.CONIC -> appendConic(
                start = segment.p0.required("CONIC p0").normalized(),
                control = segment.p1.required("CONIC p1").normalized(),
                end = segment.p2.required("CONIC p2").normalized(),
                weight = segment.conicWeight,
                tolerance = conicTolerance,
            )
            PathVerb.CUBIC -> add(
                VectorCommand.CubicTo(
                    control1 = segment.p1.required("CUBIC p1").normalized(),
                    control2 = segment.p2.required("CUBIC p2").normalized(),
                    end = segment.p3.required("CUBIC p3").normalized(),
                ),
            )
            PathVerb.CLOSE -> add(VectorCommand.Close)
            PathVerb.DONE -> Unit
        }
    }
}

private fun MutableList<VectorCommand>.appendConic(
    start: VectorPoint,
    control: VectorPoint,
    end: VectorPoint,
    weight: Float,
    tolerance: Float,
) {
    require(weight.isFinite() && weight > 0f) {
        "A conic weight must be finite and positive, but was $weight"
    }
    if (abs(weight - 1f) <= 1e-6f) {
        add(VectorCommand.QuadraticTo(control, end))
        return
    }

    val curve = RationalQuadratic(start, control, end, weight.toDouble())
    appendConicInterval(
        curve = curve,
        startT = 0.0,
        endT = 1.0,
        tolerance = tolerance.toDouble(),
        depth = 0,
    )
}

private fun MutableList<VectorCommand>.appendConicInterval(
    curve: RationalQuadratic,
    startT: Double,
    endT: Double,
    tolerance: Double,
    depth: Int,
) {
    val start = curve.point(startT)
    val end = curve.point(endT)
    val interval = endT - startT
    val control1 = start + curve.derivative(startT) * (interval / 3.0)
    val control2 = end - curve.derivative(endT) * (interval / 3.0)

    val error = listOf(0.25, 0.5, 0.75).maxOf { fraction ->
        val actual = curve.point(startT + interval * fraction)
        val approximate = cubicPoint(
            start,
            control1,
            control2,
            end,
            fraction,
        )
        max(abs(actual.x - approximate.x), abs(actual.y - approximate.y))
    }
    if (error <= tolerance || depth >= MaxConicSubdivisionDepth) {
        add(
            VectorCommand.CubicTo(
                control1 = control1.toVectorPoint(),
                control2 = control2.toVectorPoint(),
                end = end.toVectorPoint(),
            ),
        )
        return
    }

    val middle = (startT + endT) / 2.0
    appendConicInterval(curve, startT, middle, tolerance, depth + 1)
    appendConicInterval(curve, middle, endT, tolerance, depth + 1)
}

internal fun loadSkikoTypeface(fontFile: Path, fontIndex: Int): Typeface {
    if (!Files.isRegularFile(fontFile)) {
        throw SymbolGenerationException("Font does not exist: $fontFile")
    }
    return try {
        requireNotNull(
            FontMgr.default.makeFromFile(
                fontFile.toAbsolutePath().normalize().toString(),
                fontIndex,
            ),
        ) {
            "Skia did not recognize the font"
        }
    } catch (error: RuntimeException) {
        throw SymbolGenerationException(
            "Unable to load font $fontFile at index $fontIndex",
            error,
        )
    }
}

private const val MaxConicSubdivisionDepth: Int = 12

private data class DoublePoint(
    val x: Double,
    val y: Double,
) {
    operator fun plus(other: DoublePoint): DoublePoint =
        DoublePoint(x + other.x, y + other.y)

    operator fun minus(other: DoublePoint): DoublePoint =
        DoublePoint(x - other.x, y - other.y)

    operator fun times(value: Double): DoublePoint =
        DoublePoint(x * value, y * value)

    fun toVectorPoint(): VectorPoint =
        VectorPoint(x.toFloat(), y.toFloat())
}

private class RationalQuadratic(
    start: VectorPoint,
    control: VectorPoint,
    end: VectorPoint,
    private val weight: Double,
) {
    private val p0 = DoublePoint(start.x.toDouble(), start.y.toDouble())
    private val p1 = DoublePoint(control.x.toDouble(), control.y.toDouble())
    private val p2 = DoublePoint(end.x.toDouble(), end.y.toDouble())

    fun point(t: Double): DoublePoint {
        val oneMinusT = 1.0 - t
        val b0 = oneMinusT * oneMinusT
        val b1 = 2.0 * weight * t * oneMinusT
        val b2 = t * t
        val denominator = b0 + b1 + b2
        return DoublePoint(
            x = (b0 * p0.x + b1 * p1.x + b2 * p2.x) / denominator,
            y = (b0 * p0.y + b1 * p1.y + b2 * p2.y) / denominator,
        )
    }

    fun derivative(t: Double): DoublePoint {
        val oneMinusT = 1.0 - t
        val numerator = p0 * (oneMinusT * oneMinusT) +
            p1 * (2.0 * weight * t * oneMinusT) +
            p2 * (t * t)
        val denominator =
            oneMinusT * oneMinusT + 2.0 * weight * t * oneMinusT + t * t
        val numeratorDerivative =
            p0 * (-2.0 * oneMinusT) +
                p1 * (2.0 * weight * (1.0 - 2.0 * t)) +
                p2 * (2.0 * t)
        val denominatorDerivative =
            -2.0 * oneMinusT + 2.0 * weight * (1.0 - 2.0 * t) + 2.0 * t
        return (
            numeratorDerivative * denominator -
                numerator * denominatorDerivative
            ) * (1.0 / (denominator * denominator))
    }
}

private fun cubicPoint(
    start: DoublePoint,
    control1: DoublePoint,
    control2: DoublePoint,
    end: DoublePoint,
    t: Double,
): DoublePoint {
    val oneMinusT = 1.0 - t
    return start * (oneMinusT * oneMinusT * oneMinusT) +
        control1 * (3.0 * oneMinusT * oneMinusT * t) +
        control2 * (3.0 * oneMinusT * t * t) +
        end * (t * t * t)
}

private fun Point?.required(label: String): Point =
    this ?: throw SymbolGenerationException("Skia returned no point for $label")

private fun <T> Iterator<T>.asSequence(): Sequence<T> = sequence {
    while (hasNext()) {
        yield(next())
    }
}
