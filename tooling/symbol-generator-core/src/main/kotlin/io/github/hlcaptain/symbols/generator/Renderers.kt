package io.github.hlcaptain.symbols.generator

import java.math.BigDecimal
import java.math.RoundingMode

/** Files rendered relative to a caller-owned generated output directory. */
data class RenderedFiles(
    val files: Map<String, String>,
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
data class VectorRenderOptions(
    val viewportWidth: Float = 24f,
    val viewportHeight: Float = 24f,
    val defaultWidthDp: Float = viewportWidth,
    val defaultHeightDp: Float = viewportHeight,
    val precision: Int = 4,
    val symbolsPerFile: Int = 64,
    val autoMirror: Boolean = false,
    val fillColor: String = "#FF000000",
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
 * Emits a Compose-style namespace plus independently shrinkable source methods.
 * Within each style, alias properties for one code point share one nullable
 * cache.
 *
 * No registry, path table, dispatcher, reflection hook, or all-icons collection
 * is emitted.
 *
 * @param options sizes, precision, file grouping, color, and mirroring written
 * into generated vectors
 * @param includeNamespace whether [render] also emits the shared icon-set and
 * style entry points
 */
class KotlinImageVectorRenderer(
    private val options: VectorRenderOptions = VectorRenderOptions(),
    private val includeNamespace: Boolean = true,
) {
    /**
     * Renders a font-derived icon set as Kotlin `ImageVector` source text.
     *
     * Within each style, one builder is emitted for each unique code point, so
     * aliases in that style reuse the same lazily cached vector at runtime. When
     * this renderer was created with
     * `includeNamespace = true`, the root and style entry points are included
     * as well. This function only builds text in memory; use
     * [GeneratedFileWriter.synchronize] to write it to disk.
     *
     * @param iconSet validated styles, names, and extracted font outlines.
     * @return relative Kotlin source paths and their complete contents.
     */
    fun render(iconSet: GeneratedIconSet): RenderedFiles {
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
                    val fileName = "${iconSet.name}${style.name}Icons${chunkIndex.toString().padStart(3, '0')}.generated.kt"
                    rendered["$baseDirectory/$styleSegment/$fileName"] =
                        renderChunk(iconSet, style, entries)
                }
        }
        return RenderedFiles(rendered)
    }

    /**
     * Renders an SVG-derived icon set as Kotlin `ImageVector` source text.
     *
     * Within each style, each icon receives a separate lazy, cached vector
     * getter. When this renderer was created with `includeNamespace = true`, the
     * root and style entry points are included as well. This function reads and
     * writes no files; [iconSet] must already contain extracted SVG paths.
     *
     * @param iconSet validated styles, icon names, and SVG path commands.
     * @return relative Kotlin source paths and their complete contents.
     */
    fun render(iconSet: GeneratedSvgIconSet): RenderedFiles {
        val rendered = linkedMapOf<String, String>()
        val baseDirectory = iconSet.packageName.replace('.', '/')
        if (includeNamespace) {
            rendered.putAll(
                KotlinIconNamespaceRenderer().render(
                    packageName = iconSet.packageName,
                    iconSetName = iconSet.name,
                    styleNames = iconSet.styles.map(GeneratedSvgStyle::name),
                ).files,
            )
        }

        iconSet.styles.sortedBy(GeneratedSvgStyle::name).forEach { style ->
            val styleSegment = SymbolNames.packageSegment(style.name)
            style.icons.sortedBy(SvgIcon::name)
                .chunked(options.symbolsPerFile)
                .forEachIndexed { chunkIndex, icons ->
                    val fileName = "${iconSet.name}${style.name}Icons${chunkIndex.toString().padStart(3, '0')}.generated.kt"
                    rendered["$baseDirectory/$styleSegment/$fileName"] =
                        renderSvgChunk(iconSet, style, icons)
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
            appendLine(
                """
                $GeneratedKotlinHeader
                package ${iconSet.packageName}.$styleSegment

                import androidx.compose.ui.graphics.Color
                import androidx.compose.ui.graphics.SolidColor
                import androidx.compose.ui.graphics.vector.ImageVector
                import androidx.compose.ui.graphics.vector.path
                import androidx.compose.ui.unit.dp
                import ${iconSet.packageName}.${iconSet.name}${style.name}
                """.trimIndent(),
            )
            appendLine()

            entries.forEach { (codePoint, aliases) ->
                appendCodePoint(iconSet, style, codePoint, aliases)
            }
        }
    }

    private fun renderSvgChunk(
        iconSet: GeneratedSvgIconSet,
        style: GeneratedSvgStyle,
        icons: List<SvgIcon>,
    ): String {
        val styleSegment = SymbolNames.packageSegment(style.name)

        return buildString {
            appendLine(
                """
                $GeneratedKotlinHeader
                package ${iconSet.packageName}.$styleSegment

                import androidx.compose.ui.graphics.Color
                import androidx.compose.ui.graphics.PathFillType
                import androidx.compose.ui.graphics.SolidColor
                import androidx.compose.ui.graphics.StrokeCap
                import androidx.compose.ui.graphics.StrokeJoin
                import androidx.compose.ui.graphics.vector.ImageVector
                import androidx.compose.ui.graphics.vector.path
                import androidx.compose.ui.unit.dp
                import ${iconSet.packageName}.${iconSet.name}${style.name}
                """.trimIndent(),
            )
            appendLine()

            icons.forEach { icon ->
                appendSvgIcon(iconSet, style, icon)
            }
        }
    }

    private fun StringBuilder.appendCodePoint(
        iconSet: GeneratedIconSet,
        style: GeneratedStyle,
        codePoint: Int,
        aliases: List<SymbolEntry>,
    ) {
        val functionName = "${iconSet.name.replaceFirstChar(Char::lowercaseChar)}${style.name}U${codePoint.toString(16).uppercase()}"
        val cacheName = "_$functionName"
        val outline = requireNotNull(style.font.outlines[codePoint])

        aliases.forEach { alias ->
            appendLine(
                """
                /** `${alias.name}` (U+${codePoint.toString(16).uppercase()}). */
                val ${iconSet.name}${style.name}.${alias.kotlinName}: ImageVector
                    get() = $functionName()
                """.trimIndent(),
            )
            appendLine()
        }

        appendLine(
            """
            private var $cacheName: ImageVector? = null

            private fun $functionName(): ImageVector {
                $cacheName?.let { return it }
                return ImageVector.Builder(
                    name = "${iconSet.name}.${style.name}.U+${codePoint.toString(16).uppercase()}",
                    defaultWidth = ${decimal(options.defaultWidthDp)}.dp,
                    defaultHeight = ${decimal(options.defaultHeightDp)}.dp,
                    viewportWidth = ${floatLiteral(options.viewportWidth)},
                    viewportHeight = ${floatLiteral(options.viewportHeight)},
                    autoMirror = ${options.autoMirror},
                ).apply {
                    path(fill = SolidColor(Color(0x${argbHex(options.fillColor)}.toInt()))) {
            """.trimIndent(),
        )
        outline.commands.forEach { command ->
            appendKotlinCommand(command)
        }
        appendLine(
            """
                    }
                }.build().also { $cacheName = it }
            }
            """.trimIndent(),
        )
        appendLine()
    }

    private fun StringBuilder.appendSvgIcon(
        iconSet: GeneratedSvgIconSet,
        style: GeneratedSvgStyle,
        icon: SvgIcon,
    ) {
        val functionName = "${iconSet.name.replaceFirstChar(Char::lowercaseChar)}${style.name}${icon.kotlinName}"
        val cacheName = "_$functionName"

        appendLine(
            """
            /** `${icon.name}`. */
            val ${iconSet.name}${style.name}.${icon.kotlinName}: ImageVector
                get() = $functionName()

            private var $cacheName: ImageVector? = null

            private fun $functionName(): ImageVector {
                $cacheName?.let { return it }
                return ImageVector.Builder(
                    name = "${iconSet.name}.${style.name}.${icon.kotlinName}",
                    defaultWidth = ${decimal(options.defaultWidthDp)}.dp,
                    defaultHeight = ${decimal(options.defaultHeightDp)}.dp,
                    viewportWidth = ${floatLiteral(options.viewportWidth)},
                    viewportHeight = ${floatLiteral(options.viewportHeight)},
                    autoMirror = ${options.autoMirror},
                ).apply {
            """.trimIndent(),
        )
        icon.paths.forEachIndexed { index, path ->
            appendLine(
                """
                |        path(
                |            name = "path_$index",
                |            fill = ${paintExpression(path.fill)},
                |            fillAlpha = ${floatLiteral(path.fillAlpha)},
                |            stroke = ${paintExpression(path.stroke)},
                |            strokeAlpha = ${floatLiteral(path.strokeAlpha)},
                |            strokeLineWidth = ${floatLiteral(path.strokeWidth)},
                |            strokeLineCap = StrokeCap.${path.strokeCap},
                |            strokeLineJoin = StrokeJoin.${path.strokeJoin},
                |            strokeLineMiter = ${floatLiteral(path.strokeMiterLimit)},
                |            pathFillType = PathFillType.${path.fillRule},
                |        ) {
                """.trimMargin(),
            )
            path.commands.forEach { command ->
                appendKotlinCommand(command)
            }
            appendLine("        }")
        }
        appendLine(
            """
                }.build().also { $cacheName = it }
            }
            """.trimIndent(),
        )
        appendLine()
    }

    private fun paintExpression(painted: Boolean): String =
        if (painted) {
            "SolidColor(Color(0x${argbHex(options.fillColor)}.toInt()))"
        } else {
            "null"
        }

    private fun StringBuilder.appendKotlinCommand(command: VectorCommand) {
        when (command) {
            is VectorCommand.MoveTo ->
                appendLine("            moveTo(${floatLiteral(command.point.x)}, ${floatLiteral(command.point.y)})")
            is VectorCommand.LineTo ->
                appendLine("            lineTo(${floatLiteral(command.point.x)}, ${floatLiteral(command.point.y)})")
            is VectorCommand.QuadraticTo ->
                appendLine("            quadTo(${floatLiteral(command.control.x)}, ${floatLiteral(command.control.y)}, ${floatLiteral(command.end.x)}, ${floatLiteral(command.end.y)})")
            is VectorCommand.CubicTo ->
                appendLine("            curveTo(${floatLiteral(command.control1.x)}, ${floatLiteral(command.control1.y)}, ${floatLiteral(command.control2.x)}, ${floatLiteral(command.control2.y)}, ${floatLiteral(command.end.x)}, ${floatLiteral(command.end.y)})")
            VectorCommand.Close -> appendLine("            close()")
        }
    }

    private fun decimal(value: Float): String =
        NumberFormatter(options.precision).format(value)

    private fun floatLiteral(value: Float): String = "${decimal(value)}f"
}

/** Output from [AndroidVectorXmlRenderer], including code-point resource names. */
data class AndroidVectorOutput(
    val files: RenderedFiles,
    val resourceNames: Map<AndroidResourceKey, String>,
)

/** Identifies a generated native resource without collapsing styles. */
data class AndroidResourceKey(
    val styleName: String,
    val codePoint: Int,
)

/** Output from SVG Android-vector rendering, keyed by style and semantic name. */
data class SvgAndroidVectorOutput(
    val files: RenderedFiles,
    val resourceNames: Map<SvgAndroidResourceKey, String>,
)

/** Identifies one SVG-derived native resource. */
data class SvgAndroidResourceKey(
    val styleName: String,
    val iconName: String,
)

/**
 * Emits native Android vector drawables from font or SVG styles. Within each
 * style, font alias accessors can safely reference the same resource ID,
 * allowing `shrinkResources` to retain only code points that are reached.
 *
 * @param options sizes, precision, color, and mirroring written into generated
 * vector XML
 * @param resourcePrefix lowercase ASCII prefix added to every resource name
 * @throws IllegalArgumentException if [resourcePrefix] is not a valid Android
 * resource prefix
 */
class AndroidVectorXmlRenderer(
    private val options: VectorRenderOptions = VectorRenderOptions(),
    private val resourcePrefix: String,
) {
    init {
        require(Regex("^[a-z][a-z0-9_]*$").matches(resourcePrefix)) {
            "resourcePrefix must be a lowercase Android resource prefix"
        }
    }

    /**
     * Renders a font-derived icon set as native Android vector drawable XML.
     *
     * Within each style, a single drawable is produced for each unique code
     * point. Manifest aliases in that style therefore map to the same resource
     * name. The returned lookup map can be used when generating typed resource
     * accessors. This function creates XML text in memory and does not modify an
     * Android `res` directory.
     *
     * @param iconSet validated styles, catalogs, and extracted font outlines.
     * @return drawable files and the Android resource name for every style and
     * code point.
     * @throws SymbolGenerationException if two inputs would produce the same
     * Android resource name.
     */
    fun render(iconSet: GeneratedIconSet): AndroidVectorOutput {
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
                val resourceName = "${resourcePrefix}_${stylePrefix}_${semanticName}_u${codePoint.toString(16).lowercase()}"
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

    /**
     * Renders an SVG-derived icon set as native Android vector drawable XML.
     *
     * Within each style, a drawable is produced for every SVG source. The
     * returned lookup map uses each style and icon name as its key. This function
     * only builds XML text in memory; it does not write into an Android `res`
     * directory.
     *
     * @param iconSet validated styles, icon names, and SVG path commands.
     * @return drawable files and the Android resource name for every SVG icon.
     * @throws SymbolGenerationException if two inputs would produce the same
     * Android resource name.
     */
    fun render(iconSet: GeneratedSvgIconSet): SvgAndroidVectorOutput {
        SymbolNames.requireDistinctStyleAndroidResourcePrefixes(
            iconSet.styles.map(GeneratedSvgStyle::name),
        )
        val formatter = NumberFormatter(options.precision)
        val rendered = linkedMapOf<String, String>()
        val resourceNames = linkedMapOf<SvgAndroidResourceKey, String>()
        val resourceOwners = linkedMapOf<String, SvgAndroidResourceKey>()

        iconSet.styles.sortedBy(GeneratedSvgStyle::name).forEach { style ->
            val stylePrefix = SymbolNames.androidResourcePrefix(style.name)
            style.icons.sortedBy(SvgIcon::name).forEach { icon ->
                val resourceName =
                    "${resourcePrefix}_${stylePrefix}_${icon.name}"
                val resourceKey = SvgAndroidResourceKey(style.name, icon.name)
                val existingOwner = resourceOwners.put(resourceName, resourceKey)
                if (existingOwner != null && existingOwner != resourceKey) {
                    throw SymbolGenerationException(
                        "Android resource name collision '$resourceName': " +
                            "${existingOwner.styleName}.${existingOwner.iconName} " +
                            "and ${resourceKey.styleName}.${resourceKey.iconName}",
                    )
                }
                resourceNames[resourceKey] = resourceName
                rendered["drawable/$resourceName.xml"] =
                    renderSvgVectorXml(icon, formatter)
            }
        }
        return SvgAndroidVectorOutput(
            files = RenderedFiles(rendered),
            resourceNames = resourceNames,
        )
    }

    private fun renderVectorXml(
        outline: GlyphOutline,
        formatter: NumberFormatter,
    ): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <!-- Generated by Symbols. DO NOT EDIT. -->
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="${formatter.format(options.defaultWidthDp)}dp"
            android:height="${formatter.format(options.defaultHeightDp)}dp"
            android:viewportWidth="${formatter.format(options.viewportWidth)}"
            android:viewportHeight="${formatter.format(options.viewportHeight)}"
            android:autoMirrored="${options.autoMirror}">
            <path
                android:fillColor="${options.fillColor}"
                android:pathData="${pathData(outline, formatter)}" />
        </vector>
        """.trimIndent() + '\n'

    private fun renderSvgVectorXml(
        icon: SvgIcon,
        formatter: NumberFormatter,
    ): String = buildString {
        appendLine(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Generated by Symbols. DO NOT EDIT. -->
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="${formatter.format(options.defaultWidthDp)}dp"
                android:height="${formatter.format(options.defaultHeightDp)}dp"
                android:viewportWidth="${formatter.format(options.viewportWidth)}"
                android:viewportHeight="${formatter.format(options.viewportHeight)}"
                android:autoMirrored="${options.autoMirror}">
            """.trimIndent(),
        )
        icon.paths.forEachIndexed { index, path ->
            appendLine(
                """
                |    <path
                |        android:name="path_$index"
                |        android:fillColor="${xmlPaint(path.fill)}"
                |        android:fillAlpha="${formatter.format(path.fillAlpha)}"
                |        android:strokeColor="${xmlPaint(path.stroke)}"
                |        android:strokeAlpha="${formatter.format(path.strokeAlpha)}"
                |        android:strokeWidth="${formatter.format(path.strokeWidth)}"
                |        android:strokeLineCap="${path.strokeCap.xmlValue()}"
                |        android:strokeLineJoin="${path.strokeJoin.xmlValue()}"
                |        android:strokeMiterLimit="${formatter.format(path.strokeMiterLimit)}"
                |        android:fillType="${path.fillRule.xmlValue()}"
                |        android:pathData="${pathData(path.commands, formatter)}" />
                """.trimMargin(),
            )
        }
        appendLine("</vector>")
    }

    private fun xmlPaint(painted: Boolean): String =
        if (painted) options.fillColor else "#00000000"
}

/**
 * Renders the one shared root object for independently generated font styles.
 *
 * Build integrations can put this tiny output in its own cacheable task while
 * each expensive font extraction task owns only its style-specific builders.
 */
class KotlinIconNamespaceRenderer {
    /**
     * Renders the small Kotlin namespace shared by independently generated icon
     * styles.
     *
     * The result contains the icon-set object, one object for each style, and a
     * `Symbols` entry point. Style names are sorted so the generated text is
     * stable regardless of collection order. No files are read or written.
     *
     * @param packageName Kotlin package for the generated source.
     * @param iconSetName name of the generated root object.
     * @param styleNames unique generated style-object names.
     * @return one relative Kotlin source path and its complete contents.
     * @throws IllegalArgumentException if a name is invalid, a style is
     * duplicated, or style names collapse to the same package segment.
     */
    fun render(
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
            SymbolNames.requireTypeIdentifier(
                "$iconSetName$styleName",
                "generated style namespace",
            )
        }
        SymbolNames.requireDistinctStylePackageSegments(sortedStyles)

        val baseDirectory = packageName.replace('.', '/')
        val entryPointName = "_${iconSetName}EntryPoint"
        val contents = buildString {
            appendLine(
                """
                $GeneratedKotlinHeader
                package $packageName

                import io.github.hlcaptain.symbols.Symbols

                /** Generated icon namespace. */
                object $iconSetName {
                """.trimIndent(),
            )
            sortedStyles.forEach { styleName ->
                appendLine(
                    """
                    |    /** Generated $styleName icon style. */
                    |    val $styleName: $iconSetName$styleName
                    |        get() = $iconSetName$styleName
                    """.trimMargin(),
                )
                appendLine()
            }
            if (endsWith("\n\n")) {
                deleteAt(lastIndex)
            }
            appendLine("}")
            appendLine()
            sortedStyles.forEach { styleName ->
                appendLine("/** Generated $iconSetName $styleName icon namespace. */")
                appendLine("object $iconSetName$styleName")
                appendLine()
            }
            appendLine(
                """
                private val $entryPointName: $iconSetName = $iconSetName

                /** Generated $iconSetName icon-set entry point. */
                val Symbols.$iconSetName: $iconSetName
                    get() = $entryPointName
                """.trimIndent(),
            )
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
): String = pathData(outline.commands, formatter)

internal fun pathData(
    commands: List<VectorCommand>,
    formatter: NumberFormatter,
): String {
    val format = formatter::format
    return commands.joinToString(" ") { command ->
        when (command) {
            is VectorCommand.MoveTo ->
                "M ${format(command.point.x)},${format(command.point.y)}"
            is VectorCommand.LineTo ->
                "L ${format(command.point.x)},${format(command.point.y)}"
            is VectorCommand.QuadraticTo ->
                "Q ${format(command.control.x)},${format(command.control.y)} " +
                    "${format(command.end.x)},${format(command.end.y)}"
            is VectorCommand.CubicTo ->
                "C ${format(command.control1.x)},${format(command.control1.y)} " +
                    "${format(command.control2.x)},${format(command.control2.y)} " +
                    "${format(command.end.x)},${format(command.end.y)}"
            VectorCommand.Close -> "Z"
        }
    }
}

private fun VectorStrokeCap.xmlValue(): String = name.lowercase()

private fun VectorStrokeJoin.xmlValue(): String = name.lowercase()

private fun VectorFillRule.xmlValue(): String = when (this) {
    VectorFillRule.NonZero -> "nonZero"
    VectorFillRule.EvenOdd -> "evenOdd"
}

private fun argbHex(color: String): String =
    color.removePrefix("#").uppercase()

private const val GeneratedKotlinHeader: String =
    "// Generated by Symbols. DO NOT EDIT."
