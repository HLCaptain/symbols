package io.github.hlcaptain.symbols.generator

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.min
import org.jetbrains.skia.Path as SkiaPath
import org.jetbrains.skia.Point
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException

/** Immutable inputs for extracting a directory of monochrome SVG icons. */
data class SvgExtractionRequest(
    val svgDirectory: Path,
    val viewportWidth: Float = 24f,
    val viewportHeight: Float = 24f,
    val conicTolerance: Float = 0.002f,
) {
    init {
        require(viewportWidth.isFinite() && viewportWidth > 0f) {
            "viewportWidth must be finite and positive"
        }
        require(viewportHeight.isFinite() && viewportHeight > 0f) {
            "viewportHeight must be finite and positive"
        }
        require(conicTolerance.isFinite() && conicTolerance > 0f) {
            "conicTolerance must be finite and positive"
        }
    }
}

/** Securely parses a flat directory of path-based, monochrome SVG icons. */
class SvgIconExtractor {
    fun extract(request: SvgExtractionRequest): List<SvgIcon> {
        val sources = discoverSvgFiles(request.svgDirectory)
        val documentBuilder = secureDocumentBuilderFactory().newDocumentBuilder().apply {
            setErrorHandler(ThrowingErrorHandler)
        }
        return sources.map { source ->
            try {
                val document = documentBuilder.parse(source.path.toFile())
                parseIcon(source, document.documentElement, request)
            } catch (error: SymbolGenerationException) {
                throw error
            } catch (error: Exception) {
                throw SymbolGenerationException(
                    "Unable to parse SVG ${source.path}: ${error.message}",
                    error,
                )
            }
        }
    }

    companion object {
        /** Returns every generated semantic name in deterministic order. */
        fun discoverNames(svgDirectory: Path): List<String> =
            discoverSvgFiles(svgDirectory).map(SvgSourceFile::name)
    }
}

private fun parseIcon(
    source: SvgSourceFile,
    root: Element,
    request: SvgExtractionRequest,
): SvgIcon {
    require(root.localName == "svg" && root.hasSupportedSvgNamespace()) {
        "${source.path} must have an <svg> root"
    }
    val rootAttributes = root.plainAttributes(source.path)
    requireSupportedAttributes(
        source.path,
        "svg",
        rootAttributes,
        RootAttributes,
    )
    rootAttributes["preserveAspectRatio"]?.let { value ->
        require(value == "xMidYMid meet") {
            "${source.path} uses unsupported preserveAspectRatio='$value'"
        }
    }

    val viewBox = parseViewBox(
        source.path,
        requireNotNull(rootAttributes["viewBox"]) {
            "${source.path} must declare viewBox"
        },
    )
    val scale = min(
        request.viewportWidth / viewBox.width,
        request.viewportHeight / viewBox.height,
    )
    val offsetX =
        (request.viewportWidth - viewBox.width * scale) / 2f - viewBox.minX * scale
    val offsetY =
        (request.viewportHeight - viewBox.height * scale) / 2f - viewBox.minY * scale
    val rootStyle = SvgPathStyle.Default.override(rootAttributes, source.path, "svg")
    val rootOpacity = rootAttributes["opacity"]
        ?.let { parseUnitFloat(source.path, "svg opacity", it) }
        ?: 1f
    val paths = buildList {
        var pathIndex = 0
        for (index in 0 until root.childNodes.length) {
            val node = root.childNodes.item(index)
            if (node.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            val element = node as Element
            require(
                element.localName == "path" &&
                    element.hasSupportedSvgNamespace(),
            ) {
                "${source.path} contains unsupported <${element.localName}>; " +
                    "only direct <path> children are supported"
            }
            val attributes = element.plainAttributes(source.path)
            requireSupportedAttributes(
                source.path,
                "path[$pathIndex]",
                attributes,
                PathAttributes,
            )
            val style = rootStyle.override(
                attributes,
                source.path,
                "path[$pathIndex]",
            )
            val opacity = rootOpacity * (
                attributes["opacity"]
                    ?.let { parseUnitFloat(source.path, "path[$pathIndex] opacity", it) }
                    ?: 1f
                )
            val fillAlpha = style.fillAlpha * opacity
            val strokeAlpha = style.strokeAlpha * opacity
            val paintsFill = style.fill && fillAlpha > 0f
            val paintsStroke =
                style.stroke && strokeAlpha > 0f && style.strokeWidth > 0f
            if (!paintsFill && !paintsStroke) {
                pathIndex += 1
                continue
            }

            val pathData = requireNotNull(attributes["d"]) {
                "${source.path} path[$pathIndex] must declare d"
            }.also { data ->
                require(data.isNotBlank()) {
                    "${source.path} path[$pathIndex] has blank path data"
                }
            }
            val commands = try {
                SkiaPath.makeFromSVGString(pathData).use { path ->
                    require(!path.isEmpty) {
                        "${source.path} path[$pathIndex] has empty geometry"
                    }
                    path.iterator(false).use { iterator ->
                        convertSkikoPath(
                            pathSegments = iterator.asSequence()
                                .filterNotNull(),
                            pointTransform = { point: Point ->
                                VectorPoint(
                                    x = point.x * scale + offsetX,
                                    y = point.y * scale + offsetY,
                                )
                            },
                            conicTolerance = request.conicTolerance,
                        )
                    }
                }
            } catch (error: IllegalArgumentException) {
                throw SymbolGenerationException(
                    "${source.path} path[$pathIndex] has invalid path data: " +
                        error.message,
                    error,
                )
            }
            add(
                StyledVectorPath(
                    commands = commands,
                    fill = paintsFill,
                    fillAlpha = fillAlpha,
                    stroke = paintsStroke,
                    strokeAlpha = strokeAlpha,
                    strokeWidth = style.strokeWidth * scale,
                    strokeCap = style.strokeCap,
                    strokeJoin = style.strokeJoin,
                    strokeMiterLimit = style.strokeMiterLimit,
                    fillRule = style.fillRule,
                ),
            )
            pathIndex += 1
        }
    }
    return SvgIcon(source.name, paths)
}

private data class SvgSourceFile(
    val name: String,
    val path: Path,
)

private fun discoverSvgFiles(svgDirectory: Path): List<SvgSourceFile> {
    val directory = svgDirectory.toAbsolutePath().normalize()
    require(Files.isDirectory(directory)) {
        "SVG directory does not exist: $svgDirectory"
    }
    val files = Files.list(directory).use { paths ->
        paths
            .filter(Files::isRegularFile)
            .filter { path ->
                path.fileName.toString().substringAfterLast('.', "")
                    .equals("svg", ignoreCase = true)
            }
            .map { path ->
                val fileName = path.fileName.toString()
                require(fileName.endsWith(".svg")) {
                    "SVG file extensions must be lowercase: $path"
                }
                val sourceName = fileName.removeSuffix(".svg")
                val canonicalName = sourceName.replace('-', '_')
                require(SymbolNames.isCanonicalName(canonicalName)) {
                    "Invalid SVG file name '$fileName'; expected lowercase " +
                        "words separated by '-' or '_'"
                }
                SvgSourceFile(canonicalName, path)
            }
            .toList()
    }
    require(files.isNotEmpty()) { "SVG directory contains no .svg files: $svgDirectory" }
    val collisions = files
        .groupBy(SvgSourceFile::name)
        .filterValues { values -> values.size > 1 }
    require(collisions.isEmpty()) {
        "SVG file-name collisions after '-' normalization: " +
            collisions.entries
                .sortedBy(Map.Entry<String, List<SvgSourceFile>>::key)
                .joinToString("; ") { (name, values) ->
                    "$name <- ${values.map { it.path.fileName }.sorted().joinToString()}"
                }
    }
    return files.sortedBy(SvgSourceFile::name)
}

private data class SvgViewBox(
    val minX: Float,
    val minY: Float,
    val width: Float,
    val height: Float,
)

private fun parseViewBox(path: Path, value: String): SvgViewBox {
    val values = value
        .trim()
        .split(Regex("[\\s,]+"))
        .map { component ->
            component.toFloatOrNull()
                ?: throw SymbolGenerationException(
                    "$path has invalid viewBox='$value'",
                )
        }
    require(values.size == 4 && values.all(Float::isFinite)) {
        "$path must declare four finite viewBox values, but was '$value'"
    }
    require(values[2] > 0f && values[3] > 0f) {
        "$path viewBox width and height must be positive, but was '$value'"
    }
    return SvgViewBox(values[0], values[1], values[2], values[3])
}

private data class SvgPathStyle(
    val fill: Boolean,
    val stroke: Boolean,
    val fillAlpha: Float,
    val strokeAlpha: Float,
    val strokeWidth: Float,
    val strokeCap: VectorStrokeCap,
    val strokeJoin: VectorStrokeJoin,
    val strokeMiterLimit: Float,
    val fillRule: VectorFillRule,
) {
    fun override(
        attributes: Map<String, String>,
        path: Path,
        location: String,
    ): SvgPathStyle = copy(
        fill = attributes["fill"]?.let { parsePaint(path, "$location fill", it) } ?: fill,
        stroke = attributes["stroke"]
            ?.let { parsePaint(path, "$location stroke", it) }
            ?: stroke,
        fillAlpha = attributes["fill-opacity"]
            ?.let { parseUnitFloat(path, "$location fill-opacity", it) }
            ?: fillAlpha,
        strokeAlpha = attributes["stroke-opacity"]
            ?.let { parseUnitFloat(path, "$location stroke-opacity", it) }
            ?: strokeAlpha,
        strokeWidth = attributes["stroke-width"]
            ?.let { parseNonNegativeFloat(path, "$location stroke-width", it) }
            ?: strokeWidth,
        strokeCap = attributes["stroke-linecap"]
            ?.let { parseStrokeCap(path, location, it) }
            ?: strokeCap,
        strokeJoin = attributes["stroke-linejoin"]
            ?.let { parseStrokeJoin(path, location, it) }
            ?: strokeJoin,
        strokeMiterLimit = attributes["stroke-miterlimit"]
            ?.let { parseNonNegativeFloat(path, "$location stroke-miterlimit", it) }
            ?: strokeMiterLimit,
        fillRule = attributes["fill-rule"]
            ?.let { parseFillRule(path, location, it) }
            ?: fillRule,
    )

    companion object {
        val Default: SvgPathStyle = SvgPathStyle(
            fill = true,
            stroke = false,
            fillAlpha = 1f,
            strokeAlpha = 1f,
            strokeWidth = 1f,
            strokeCap = VectorStrokeCap.Butt,
            strokeJoin = VectorStrokeJoin.Miter,
            strokeMiterLimit = 4f,
            fillRule = VectorFillRule.NonZero,
        )
    }
}

private fun parsePaint(path: Path, label: String, value: String): Boolean =
    when (value.lowercase()) {
        "none", "transparent" -> false
        "currentcolor", "black", "#000", "#000000" -> true
        else -> throw SymbolGenerationException(
            "$path uses unsupported monochrome $label='$value'",
        )
    }

private fun parseUnitFloat(path: Path, label: String, value: String): Float =
    parseFloat(path, label, value).also { parsed ->
        require(parsed in 0f..1f) { "$path $label must be in 0..1, but was '$value'" }
    }

private fun parseNonNegativeFloat(path: Path, label: String, value: String): Float =
    parseFloat(path, label, value).also { parsed ->
        require(parsed >= 0f) { "$path $label must be non-negative, but was '$value'" }
    }

private fun parseFloat(path: Path, label: String, value: String): Float =
    value.toFloatOrNull()?.takeIf(Float::isFinite)
        ?: throw SymbolGenerationException("$path $label must be finite, but was '$value'")

private fun parseStrokeCap(path: Path, location: String, value: String): VectorStrokeCap =
    when (value) {
        "butt" -> VectorStrokeCap.Butt
        "round" -> VectorStrokeCap.Round
        "square" -> VectorStrokeCap.Square
        else -> throw SymbolGenerationException(
            "$path $location uses unsupported stroke-linecap='$value'",
        )
    }

private fun parseStrokeJoin(path: Path, location: String, value: String): VectorStrokeJoin =
    when (value) {
        "miter" -> VectorStrokeJoin.Miter
        "round" -> VectorStrokeJoin.Round
        "bevel" -> VectorStrokeJoin.Bevel
        else -> throw SymbolGenerationException(
            "$path $location uses unsupported stroke-linejoin='$value'",
        )
    }

private fun parseFillRule(path: Path, location: String, value: String): VectorFillRule =
    when (value) {
        "nonzero" -> VectorFillRule.NonZero
        "evenodd" -> VectorFillRule.EvenOdd
        else -> throw SymbolGenerationException(
            "$path $location uses unsupported fill-rule='$value'",
        )
    }

private fun Element.plainAttributes(path: Path): Map<String, String> = buildMap {
    for (index in 0 until attributes.length) {
        val attribute = attributes.item(index)
        if (attribute.namespaceURI == XMLConstants.XMLNS_ATTRIBUTE_NS_URI) {
            continue
        }
        require(attribute.namespaceURI.isNullOrEmpty()) {
            "$path uses unsupported namespaced attribute '${attribute.nodeName}'"
        }
        put(attribute.localName ?: attribute.nodeName, attribute.nodeValue)
    }
}

private fun Element.hasSupportedSvgNamespace(): Boolean =
    namespaceURI == SvgNamespace

private fun requireSupportedAttributes(
    path: Path,
    location: String,
    attributes: Map<String, String>,
    supported: Set<String>,
) {
    val unsupported = attributes.keys - supported
    require(unsupported.isEmpty()) {
        "$path $location uses unsupported attributes: ${unsupported.sorted().joinToString()}"
    }
}

private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        isExpandEntityReferences = false
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    }

private object ThrowingErrorHandler : ErrorHandler {
    override fun warning(exception: SAXParseException) {
        throw exception
    }

    override fun error(exception: SAXParseException) {
        throw exception
    }

    override fun fatalError(exception: SAXParseException) {
        throw exception
    }
}

private fun <T> Iterator<T>.asSequence(): Sequence<T> = sequence {
    while (hasNext()) {
        yield(next())
    }
}

private val RootAttributes: Set<String> = setOf(
    // Tabler ships inert CSS class names alongside authoritative inline paint.
    "class",
    "width",
    "height",
    "viewBox",
    "preserveAspectRatio",
    "fill",
    "fill-opacity",
    "fill-rule",
    "stroke",
    "stroke-opacity",
    "stroke-width",
    "stroke-linecap",
    "stroke-linejoin",
    "stroke-miterlimit",
    "opacity",
)

private val PathAttributes: Set<String> = setOf(
    "d",
    "fill",
    "fill-opacity",
    "fill-rule",
    "stroke",
    "stroke-opacity",
    "stroke-width",
    "stroke-linecap",
    "stroke-linejoin",
    "stroke-miterlimit",
    "opacity",
)

private const val SvgNamespace: String = "http://www.w3.org/2000/svg"
