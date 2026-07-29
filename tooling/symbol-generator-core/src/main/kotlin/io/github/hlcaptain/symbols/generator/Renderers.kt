package io.github.hlcaptain.symbols.generator

import java.math.BigDecimal
import java.math.RoundingMode

/** Files rendered relative to a caller-owned generated output directory. */
public data class RenderedFiles(
    public val files: Map<String, String>,
) {
    init {
        require(files.isNotEmpty()) { "At least one generated file is required" }
        files.keys.forEach { relativePath ->
            require(
                relativePath.isNotBlank() &&
                    !relativePath.startsWith('/') &&
                    '\\' !in relativePath &&
                    relativePath.split('/').none { it == ".." },
            ) {
                "Generated file paths must be safe forward-slash relative paths: $relativePath"
            }
        }
    }
}

/** Shared deterministic vector rendering settings. */
public data class VectorRenderOptions(
    public val viewportWidth: Float = 24f,
    public val viewportHeight: Float = 24f,
    public val defaultWidthDp: Float = viewportWidth,
    public val defaultHeightDp: Float = viewportHeight,
    public val precision: Int = 4,
    public val symbolsPerFile: Int = 64,
    public val autoMirror: Boolean = false,
    public val fillColor: String = "#FF000000",
) {
    init {
        require(viewportWidth.isFinite() && viewportWidth > 0f) {
            "viewportWidth must be finite and positive"
        }
        require(viewportHeight.isFinite() && viewportHeight > 0f) {
            "viewportHeight must be finite and positive"
        }
        require(defaultWidthDp.isFinite() && defaultWidthDp > 0f) {
            "defaultWidthDp must be finite and positive"
        }
        require(defaultHeightDp.isFinite() && defaultHeightDp > 0f) {
            "defaultHeightDp must be finite and positive"
        }
        require(precision in 0..8) { "precision must be in 0..8" }
        require(symbolsPerFile in 1..512) {
            "symbolsPerFile must be in 1..512"
        }
        require(Regex("^#[0-9A-Fa-f]{8}$").matches(fillColor)) {
            "fillColor must be #AARRGGBB, but was $fillColor"
        }
    }
}

/**
 * Emits a Compose-idiomatic namespace plus one independently shrinkable source
 * methods per unique code point. Alias properties share one nullable cache.
 *
 * No registry, path table, dispatcher, reflection hook, or all-icons collection
 * is emitted.
 */
public class KotlinImageVectorRenderer(
    private val options: VectorRenderOptions = VectorRenderOptions(),
    private val includeNamespace: Boolean = true,
) {
    public fun render(iconSet: GeneratedIconSet): RenderedFiles {
        val rendered = linkedMapOf<String, String>()
        val baseDirectory = iconSet.packageName.replace('.', '/')
        if (includeNamespace) {
            rendered.putAll(
                KotlinIconNamespaceRenderer().render(
                    packageName = iconSet.packageName,
                    iconSetName = iconSet.name,
                    styleNames = iconSet.styles.map(GeneratedStyle::name),
                ).files,
            )
        }

        iconSet.styles.sortedBy(GeneratedStyle::name).forEach { style ->
            val styleSegment = SymbolNames.packageSegment(style.name)
            style.catalog.entriesByCodePoint.entries
                .chunked(options.symbolsPerFile)
                .forEachIndexed { chunkIndex, entries ->
                    val fileName = buildString {
                        append(iconSet.name)
                        append(style.name)
                        append("Icons")
                        append(chunkIndex.toString().padStart(3, '0'))
                        append(".generated.kt")
                    }
                    rendered["$baseDirectory/$styleSegment/$fileName"] =
                        renderChunk(iconSet, style, entries)
                }
        }
        return RenderedFiles(rendered)
    }

    private fun renderChunk(
        iconSet: GeneratedIconSet,
        style: GeneratedStyle,
        entries: List<Map.Entry<Int, List<SymbolEntry>>>,
    ): String {
        val styleSegment = SymbolNames.packageSegment(style.name)

        return buildString {
            appendLine(GeneratedKotlinHeader)
            appendLine("package ${iconSet.packageName}.$styleSegment")
            appendLine()
            appendLine("import androidx.compose.ui.graphics.Color")
            appendLine("import androidx.compose.ui.graphics.SolidColor")
            appendLine("import androidx.compose.ui.graphics.vector.ImageVector")
            appendLine("import androidx.compose.ui.graphics.vector.path")
            appendLine("import androidx.compose.ui.unit.dp")
            appendLine("import ${iconSet.packageName}.${iconSet.name}")
            appendLine()

            entries.forEach { (codePoint, aliases) ->
                appendCodePoint(iconSet, style, codePoint, aliases)
            }
        }
    }

    private fun StringBuilder.appendCodePoint(
        iconSet: GeneratedIconSet,
        style: GeneratedStyle,
        codePoint: Int,
        aliases: List<SymbolEntry>,
    ) {
        val functionName =
            "${iconSet.name.replaceFirstChar(Char::lowercaseChar)}" +
                "${style.name}U${codePoint.toString(16).uppercase()}"
        val cacheName = "_$functionName"
        val outline = requireNotNull(style.font.outlines[codePoint])

        aliases.forEach { alias ->
            appendLine(
                "/** `${alias.name}` (U+${codePoint.toString(16).uppercase()}). */",
            )
            appendLine(
                "public val ${iconSet.name}.${style.name}.${alias.kotlinName}: ImageVector",
            )
            appendLine("    get() = $functionName()")
            appendLine()
        }

        appendLine("private var $cacheName: ImageVector? = null")
        appendLine()
        appendLine("private fun $functionName(): ImageVector {")
        appendLine("    $cacheName?.let { return it }")
        appendLine("    return ImageVector.Builder(")
        appendLine(
            "        name = \"${iconSet.name}.${style.name}." +
                "U+${codePoint.toString(16).uppercase()}\",",
        )
        appendLine("        defaultWidth = ${decimal(options.defaultWidthDp)}.dp,")
        appendLine("        defaultHeight = ${decimal(options.defaultHeightDp)}.dp,")
        appendLine("        viewportWidth = ${floatLiteral(options.viewportWidth)},")
        appendLine("        viewportHeight = ${floatLiteral(options.viewportHeight)},")
        appendLine("        autoMirror = ${options.autoMirror},")
        appendLine("    ).apply {")
        appendLine(
            "        path(" +
                "fill = SolidColor(Color(0x${argbHex(options.fillColor)}.toInt()))) {",
        )
        outline.commands.forEach { command ->
            appendKotlinCommand(command)
        }
        appendLine("        }")
        appendLine("    }.build().also { $cacheName = it }")
        appendLine("}")
        appendLine()
    }

    private fun StringBuilder.appendKotlinCommand(command: VectorCommand) {
        when (command) {
            is VectorCommand.MoveTo ->
                appendLine(
                    "            moveTo(${floatLiteral(command.point.x)}, " +
                        "${floatLiteral(command.point.y)})",
                )
            is VectorCommand.LineTo ->
                appendLine(
                    "            lineTo(${floatLiteral(command.point.x)}, " +
                        "${floatLiteral(command.point.y)})",
                )
            is VectorCommand.QuadraticTo ->
                appendLine(
                    "            quadTo(" +
                        "${floatLiteral(command.control.x)}, " +
                        "${floatLiteral(command.control.y)}, " +
                        "${floatLiteral(command.end.x)}, " +
                        "${floatLiteral(command.end.y)})",
                )
            is VectorCommand.CubicTo ->
                appendLine(
                    "            curveTo(" +
                        "${floatLiteral(command.control1.x)}, " +
                        "${floatLiteral(command.control1.y)}, " +
                        "${floatLiteral(command.control2.x)}, " +
                        "${floatLiteral(command.control2.y)}, " +
                        "${floatLiteral(command.end.x)}, " +
                        "${floatLiteral(command.end.y)})",
                )
            VectorCommand.Close -> appendLine("            close()")
        }
    }

    private fun decimal(value: Float): String =
        NumberFormatter(options.precision).format(value)

    private fun floatLiteral(value: Float): String = "${decimal(value)}f"
}

/** Output from [AndroidVectorXmlRenderer], including code-point resource names. */
public data class AndroidVectorOutput(
    public val files: RenderedFiles,
    public val resourceNames: Map<AndroidResourceKey, String>,
)

/** Identifies a generated native resource without collapsing styles. */
public data class AndroidResourceKey(
    public val styleName: String,
    public val codePoint: Int,
)

/**
 * Emits one native Android vector drawable per unique code point. Alias accessors
 * can safely reference the same resource ID, allowing `shrinkResources` to retain
 * only code points that are actually reached.
 */
public class AndroidVectorXmlRenderer(
    private val options: VectorRenderOptions = VectorRenderOptions(),
    private val resourcePrefix: String,
) {
    init {
        require(Regex("^[a-z][a-z0-9_]*$").matches(resourcePrefix)) {
            "resourcePrefix must be a lowercase Android resource prefix"
        }
    }

    public fun render(iconSet: GeneratedIconSet): AndroidVectorOutput {
        SymbolNames.requireDistinctStyleAndroidResourcePrefixes(
            iconSet.styles.map(GeneratedStyle::name),
        )
        val formatter = NumberFormatter(options.precision)
        val rendered = linkedMapOf<String, String>()
        val resourceNames = linkedMapOf<AndroidResourceKey, String>()
        val resourceOwners = linkedMapOf<String, AndroidResourceKey>()

        iconSet.styles.sortedBy(GeneratedStyle::name).forEach { style ->
            val stylePrefix = SymbolNames.androidResourcePrefix(style.name)
            style.catalog.uniqueCodePoints.forEach { codePoint ->
                val semanticName = requireNotNull(
                    style.catalog.entriesByCodePoint[codePoint],
                ).minBy(SymbolEntry::name).name
                val resourceName =
                    "${resourcePrefix}_${stylePrefix}_${semanticName}_" +
                        "u${codePoint.toString(16).lowercase()}"
                val resourceKey = AndroidResourceKey(style.name, codePoint)
                val existingOwner = resourceOwners.put(resourceName, resourceKey)
                if (existingOwner != null && existingOwner != resourceKey) {
                    throw SymbolGenerationException(
                        "Android resource name collision '$resourceName': " +
                            "${existingOwner.styleName} " +
                            "U+${existingOwner.codePoint.toString(16).uppercase()} " +
                            "and ${resourceKey.styleName} " +
                            "U+${resourceKey.codePoint.toString(16).uppercase()}",
                    )
                }
                resourceNames[resourceKey] = resourceName
                val outline = requireNotNull(style.font.outlines[codePoint])
                rendered["drawable/$resourceName.xml"] =
                    renderVectorXml(outline, formatter)
            }
        }
        return AndroidVectorOutput(
            files = RenderedFiles(rendered),
            resourceNames = resourceNames,
        )
    }

    private fun renderVectorXml(
        outline: GlyphOutline,
        formatter: NumberFormatter,
    ): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        appendLine("<!-- Generated by Symbols. DO NOT EDIT. -->")
        appendLine("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"")
        appendLine("    android:width=\"${formatter.format(options.defaultWidthDp)}dp\"")
        appendLine("    android:height=\"${formatter.format(options.defaultHeightDp)}dp\"")
        appendLine("    android:viewportWidth=\"${formatter.format(options.viewportWidth)}\"")
        appendLine("    android:viewportHeight=\"${formatter.format(options.viewportHeight)}\"")
        appendLine("    android:autoMirrored=\"${options.autoMirror}\">")
        appendLine("    <path")
        appendLine("        android:fillColor=\"${options.fillColor}\"")
        appendLine("        android:pathData=\"${pathData(outline, formatter)}\" />")
        appendLine("</vector>")
    }
}

/**
 * Renders the one shared root object for independently generated font styles.
 *
 * Build integrations can put this tiny output in its own cacheable task while
 * each expensive font extraction task owns only its style-specific builders.
 */
public class KotlinIconNamespaceRenderer {
    public fun render(
        packageName: String,
        iconSetName: String,
        styleNames: Collection<String>,
    ): RenderedFiles {
        SymbolNames.requirePackageName(packageName)
        SymbolNames.requireTypeIdentifier(iconSetName, "icon-set name")
        val sortedStyles = styleNames.toSortedSet()
        require(sortedStyles.size == styleNames.size) {
            "Duplicate generated style names are not allowed"
        }
        sortedStyles.forEach { styleName ->
            SymbolNames.requireTypeIdentifier(styleName, "style name")
        }
        SymbolNames.requireDistinctStylePackageSegments(sortedStyles)

        val baseDirectory = packageName.replace('.', '/')
        val contents = buildString {
            appendLine(GeneratedKotlinHeader)
            appendLine("package $packageName")
            appendLine()
            appendLine("/** Generated icon namespace. */")
            appendLine("public object $iconSetName {")
            sortedStyles.forEach { styleName ->
                appendLine("    /** $styleName icons. */")
                appendLine("    public object $styleName")
                appendLine()
            }
            if (endsWith("\n\n")) {
                deleteAt(lastIndex)
            }
            appendLine("}")
        }
        return RenderedFiles(
            mapOf(
                "$baseDirectory/$iconSetName.generated.kt" to contents,
            ),
        )
    }
}

internal class NumberFormatter(
    private val precision: Int,
) {
    fun format(value: Float): String {
        require(value.isFinite()) { "Cannot render non-finite value: $value" }
        val rounded = BigDecimal
            .valueOf(value.toDouble())
            .setScale(precision, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        return if (rounded == "-0") "0" else rounded
    }
}

internal fun pathData(
    outline: GlyphOutline,
    formatter: NumberFormatter,
): String = buildString {
    outline.commands.forEachIndexed { index, command ->
        if (index > 0) {
            append(' ')
        }
        when (command) {
            is VectorCommand.MoveTo -> {
                append("M ")
                append(formatter.format(command.point.x))
                append(',')
                append(formatter.format(command.point.y))
            }
            is VectorCommand.LineTo -> {
                append("L ")
                append(formatter.format(command.point.x))
                append(',')
                append(formatter.format(command.point.y))
            }
            is VectorCommand.QuadraticTo -> {
                append("Q ")
                append(formatter.format(command.control.x))
                append(',')
                append(formatter.format(command.control.y))
                append(' ')
                append(formatter.format(command.end.x))
                append(',')
                append(formatter.format(command.end.y))
            }
            is VectorCommand.CubicTo -> {
                append("C ")
                append(formatter.format(command.control1.x))
                append(',')
                append(formatter.format(command.control1.y))
                append(' ')
                append(formatter.format(command.control2.x))
                append(',')
                append(formatter.format(command.control2.y))
                append(' ')
                append(formatter.format(command.end.x))
                append(',')
                append(formatter.format(command.end.y))
            }
            VectorCommand.Close -> append('Z')
        }
    }
}

private fun argbHex(color: String): String =
    color.removePrefix("#").uppercase()

private const val GeneratedKotlinHeader: String =
    "// Generated by Symbols. DO NOT EDIT."
