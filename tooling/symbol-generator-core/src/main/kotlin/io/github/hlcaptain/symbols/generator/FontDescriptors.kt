package io.github.hlcaptain.symbols.generator

import java.nio.file.Files
import java.nio.file.Path

internal data class GeneratedFontAxis(
    val tag: String,
    val minValue: Float,
    val defaultValue: Float,
    val maxValue: Float,
    val hidden: Boolean,
) {
    init {
        require(tag.length == 4 && tag.all { it.code in 0x20..0x7E }) {
            "OpenType axis tags must contain four printable ASCII characters: '$tag'"
        }
        require(
            minValue.isFinite() &&
                defaultValue.isFinite() &&
                maxValue.isFinite() &&
                minValue <= defaultValue &&
                defaultValue <= maxValue,
        ) {
            "Axis $tag must have finite min <= default <= max values"
        }
    }
}

internal data class GeneratedFontDescriptor(
    val accessorName: String,
    val familyName: String,
    val axes: List<GeneratedFontAxis>,
) {
    init {
        SymbolNames.requireTypeIdentifier(accessorName, "Compose font resource accessor")
        require(familyName.isNotBlank()) { "Font family name must not be blank" }
        require(axes.map(GeneratedFontAxis::tag).distinct().size == axes.size) {
            "$familyName contains duplicate variation-axis tags"
        }
    }
}

internal data class GeneratedFontAccessor(
    val resourceAccessorName: String,
    val receiver: String,
    val propertyName: String,
    val packageName: String,
    val componentIndex: Int?,
    val fixedAxisValues: Map<String, Float>,
) {
    init {
        SymbolNames.requireTypeIdentifier(
            resourceAccessorName,
            "Compose font resource accessor",
        )
        require('.' in receiver) {
            "Font accessor receiver must be fully qualified: $receiver"
        }
        receiver.split('.').forEach { segment ->
            SymbolNames.requireTypeIdentifier(segment, "font accessor receiver segment")
        }
        SymbolNames.requireTypeIdentifier(propertyName, "font accessor property")
        SymbolNames.requirePackageName(packageName)
        require(componentIndex == null || componentIndex > 0) {
            "Font accessor component index must be positive"
        }
        fixedAxisValues.forEach { (tag, value) ->
            require(tag.length == 4 && tag.all { it.code in 0x20..0x7E }) {
                "OpenType axis tags must contain four printable ASCII characters: '$tag'"
            }
            require(value.isFinite()) { "Fixed axis $tag must be finite" }
        }
    }
}

internal class SkikoFontDescriptorScanner {
    fun scan(resourceRoots: Collection<Path>): List<GeneratedFontDescriptor> {
        val fontFiles = resourceRoots
            .map { root -> root.toAbsolutePath().normalize() }
            .distinct()
            .flatMap(::directFontFiles)
            .distinct()
        require(fontFiles.isNotEmpty()) {
            "No direct .ttf, .otf, or .ttc files found under the configured font/ directories"
        }

        val filesByAccessor = fontFiles.groupBy(::composeAccessorName)
        val collisions = filesByAccessor.filterValues { files -> files.size > 1 }
        if (collisions.isNotEmpty()) {
            val details = collisions.entries
                .sortedBy(Map.Entry<String, List<Path>>::key)
                .joinToString("; ") { (accessor, files) ->
                    "$accessor <- ${files.sorted().joinToString()}"
                }
            throw SymbolGenerationException(
                "Compose font resource accessor collisions: $details",
            )
        }

        return filesByAccessor.entries
            .sortedBy(Map.Entry<String, List<Path>>::key)
            .map { (accessorName, files) ->
                loadSkikoTypeface(files.single(), 0).use { typeface ->
                    GeneratedFontDescriptor(
                        accessorName = accessorName,
                        familyName = typeface.familyName,
                        axes = typeface.variationAxes.orEmpty().map { axis ->
                            GeneratedFontAxis(
                                tag = axis.tag,
                                minValue = axis.minValue,
                                defaultValue = axis.defaultValue,
                                maxValue = axis.maxValue,
                                hidden = axis.isHidden,
                            )
                        },
                    )
                }
            }
    }

    private fun directFontFiles(resourceRoot: Path): List<Path> {
        require(Files.isDirectory(resourceRoot)) {
            "Compose font resource root does not exist or is not a directory: $resourceRoot"
        }
        val fontDirectory = resourceRoot.resolve("font")
        if (!Files.isDirectory(fontDirectory)) {
            return emptyList()
        }
        return Files.newDirectoryStream(fontDirectory).use { entries ->
            entries.asSequence()
                .filter(Files::isRegularFile)
                .filter { file ->
                    file.fileName.toString().substringAfterLast('.', "").lowercase() in
                        SupportedDescriptorFontExtensions
                }
                .map { file -> file.toAbsolutePath().normalize() }
                .sorted()
                .toList()
        }
    }
}

internal class KotlinFontDescriptorsRenderer {
    fun render(
        packageName: String,
        resClassName: String,
        publicAccessors: Boolean,
        descriptors: List<GeneratedFontDescriptor>,
        fontAccessors: List<GeneratedFontAccessor> = emptyList(),
    ): RenderedFiles {
        SymbolNames.requirePackageName(packageName)
        SymbolNames.requireTypeIdentifier(resClassName, "Compose resource class name")
        require(descriptors.isNotEmpty()) { "At least one font descriptor is required" }
        require(
            descriptors.map(GeneratedFontDescriptor::accessorName).distinct().size ==
                descriptors.size,
        ) {
            "Font descriptor accessor names must be unique"
        }
        validateFontAccessors(descriptors, fontAccessors)
        val configuredAccessors = fontAccessors.associateBy(
            GeneratedFontAccessor::resourceAccessorName,
        )
        val resolvedFontAccessors = descriptors.map { descriptor ->
            configuredAccessors[descriptor.accessorName] ?: GeneratedFontAccessor(
                resourceAccessorName = descriptor.accessorName,
                receiver = DefaultFontAccessorReceiver,
                propertyName = upperCamelIdentifier(descriptor.accessorName),
                packageName = packageName,
                componentIndex = null,
                fixedAxisValues = emptyMap(),
            )
        }
        validateFontAccessors(descriptors, resolvedFontAccessors)

        val visibilityPrefix = if (publicAccessors) "" else "internal "
        val fixedAxesByResource = resolvedFontAccessors.associate { accessor ->
            accessor.resourceAccessorName to accessor.fixedAxisValues
        }
        val contents = buildString {
            appendLine(
                """
                // Generated by Symbols. DO NOT EDIT.
                package $packageName

                import $packageName.$resClassName as $ResourceClassAlias
                import io.github.hlcaptain.symbols.font.SymbolFont
                import io.github.hlcaptain.symbols.font.SymbolFontAxis
                """.trimIndent(),
            )
            if (fixedAxesByResource.values.any { axes -> axes.isNotEmpty() }) {
                appendLine("import androidx.compose.ui.text.font.FontVariation")
                appendLine("import io.github.hlcaptain.symbols.font.SymbolFontSettings")
            }
            appendLine()
            appendLine("${visibilityPrefix}object SymbolFonts {")
            descriptors
                .sortedBy(GeneratedFontDescriptor::accessorName)
                .forEachIndexed { index, descriptor ->
                    if (index > 0) {
                        appendLine()
                    }
                    appendDescriptor(
                        visibilityPrefix = visibilityPrefix,
                        descriptor = descriptor,
                        fixedAxisValues = fixedAxesByResource[descriptor.accessorName].orEmpty(),
                    )
                }
            appendLine(
                """
                }

                ${visibilityPrefix}val $ResourceClassAlias.symbolFonts: SymbolFonts
                    get() = SymbolFonts
                """.trimIndent(),
            )
        }
        val path = "${packageName.replace('.', '/')}/SymbolFonts.generated.kt"
        val rendered = linkedMapOf(path to contents)
        resolvedFontAccessors.sortedWith(
            compareBy(GeneratedFontAccessor::packageName)
                .thenBy(GeneratedFontAccessor::receiver)
                .thenBy(GeneratedFontAccessor::propertyName),
        ).forEach { accessor ->
            val descriptor = descriptors.single { descriptor ->
                descriptor.accessorName == accessor.resourceAccessorName
            }
            val accessorPath = accessorFilePath(accessor)
            check(
                rendered.put(
                    accessorPath,
                    renderAccessor(packageName, resClassName, descriptor, accessor),
                ) == null,
            ) {
                "Generated font accessor file collision: $accessorPath"
            }
        }
        return RenderedFiles(rendered)
    }

    private fun StringBuilder.appendDescriptor(
        visibilityPrefix: String,
        descriptor: GeneratedFontDescriptor,
        fixedAxisValues: Map<String, Float>,
    ) {
        val fontType = if (descriptor.axes.isEmpty()) "Regular" else "Variable"
        appendLine(
            """
            |    ${visibilityPrefix}val ${descriptor.accessorName}: SymbolFont.$fontType =
            |        SymbolFont.${fontType.lowercase()}(
            |            familyName = ${kotlinString(descriptor.familyName)},
            |            resource = $ResourceClassAlias.font.${descriptor.accessorName},
            """.trimMargin(),
        )
        if (descriptor.axes.isNotEmpty()) {
            val visibleAxes = descriptor.axes.filterNot(GeneratedFontAxis::hidden)
            if (visibleAxes.isEmpty()) {
                appendLine("            variationAxes = emptyList(),")
            } else {
                appendLine("            variationAxes = listOf(")
                visibleAxes.forEach { axis ->
                    appendLine("                SymbolFontAxis(${kotlinString(axis.tag)}, ${floatLiteral(axis.minValue)}, ${floatLiteral(axis.defaultValue)}, ${floatLiteral(axis.maxValue)}),")
                }
                appendLine("            ),")
            }
        } else if (fixedAxisValues.isNotEmpty()) {
            appendLine("            fontSettings = SymbolFontSettings(")
            appendLine("                FontVariation.Settings(")
            fixedAxisValues.toSortedMap().forEach { (tag, value) ->
                appendLine("                    FontVariation.Setting(${kotlinString(tag)}, ${floatLiteral(value)}),")
            }
            appendLine("                ),")
            appendLine("            ),")
        }
        appendLine("        )")
    }

    private fun validateFontAccessors(
        descriptors: List<GeneratedFontDescriptor>,
        fontAccessors: List<GeneratedFontAccessor>,
    ) {
        val descriptorsByName = descriptors.associateBy(GeneratedFontDescriptor::accessorName)
        val missingResources = fontAccessors
            .map(GeneratedFontAccessor::resourceAccessorName)
            .filterNot(descriptorsByName::containsKey)
            .distinct()
            .sorted()
        require(missingResources.isEmpty()) {
            "Font accessors reference missing Compose font resources: " +
                missingResources.joinToString()
        }
        require(
            fontAccessors.map(GeneratedFontAccessor::resourceAccessorName).distinct().size ==
                fontAccessors.size,
        ) {
            "Each Compose font resource may have only one fontAccessor declaration"
        }

        val propertyCollisions = fontAccessors.groupBy { accessor ->
            Triple(accessor.packageName, accessor.receiver, accessor.propertyName)
        }.filterValues { accessors -> accessors.size > 1 }
        require(propertyCollisions.isEmpty()) {
            "Generated font accessor property collisions: " +
                propertyCollisions.keys.sortedBy { signature -> signature.toString() }
                    .joinToString()
        }
        val componentCollisions = fontAccessors
            .filter { accessor -> accessor.componentIndex != null }
            .groupBy { accessor ->
                Triple(accessor.packageName, accessor.receiver, accessor.componentIndex)
            }
            .filterValues { accessors -> accessors.size > 1 }
        require(componentCollisions.isEmpty()) {
            "Generated font accessor component collisions: " +
                componentCollisions.keys.sortedBy { signature -> signature.toString() }
                    .joinToString()
        }
        fontAccessors.forEach { accessor ->
            val descriptor = descriptorsByName.getValue(accessor.resourceAccessorName)
            require(descriptor.axes.isEmpty() || accessor.fixedAxisValues.isEmpty()) {
                "Variable font ${accessor.resourceAccessorName} cannot declare fixedAxisValues"
            }
        }
    }

    private fun accessorFilePath(accessor: GeneratedFontAccessor): String =
        "${accessor.packageName.replace('.', '/')}/" +
            "FontAccessor_${accessor.resourceAccessorName}.generated.kt"

    private fun renderAccessor(
        resourcePackage: String,
        resClassName: String,
        descriptor: GeneratedFontDescriptor,
        accessor: GeneratedFontAccessor,
    ): String {
        val fontType = if (descriptor.axes.isEmpty()) "Regular" else "Variable"
        return buildString {
            appendLine(
                """
                // Generated by Symbols. DO NOT EDIT.
                package ${accessor.packageName}

                import $resourcePackage.$resClassName as $ResourceClassAlias
                import $resourcePackage.symbolFonts as $SymbolFontsExtensionAlias
                import io.github.hlcaptain.symbols.font.SymbolFont
                """.trimIndent(),
            )
            appendLine(
                """

                /**
                 * A shared descriptor for the `${accessor.resourceAccessorName}` Compose font resource.
                 *
                 * Accessing this property does not load the font. Rendering with the returned descriptor
                 * asks Compose Resources to load it and may update the UI when loading completes.
                 */
                val ${accessor.receiver}.${accessor.propertyName}: SymbolFont.$fontType
                    get() = $ResourceClassAlias.$SymbolFontsExtensionAlias.${accessor.resourceAccessorName}
                """.trimIndent(),
            )
            accessor.componentIndex?.let { index ->
                appendLine(
                    """

                    /** Returns [${accessor.propertyName}] as item $index when this receiver is destructured. */
                    operator fun ${accessor.receiver}.component$index(): SymbolFont.$fontType =
                        ${accessor.propertyName}
                    """.trimIndent(),
                )
            }
        }
    }
}

private fun upperCamelIdentifier(value: String): String {
    val identifier = value
        .split('_')
        .filter(String::isNotEmpty)
        .joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }
    return if (identifier.firstOrNull()?.isDigit() == true) "_$identifier" else identifier
}

private const val DefaultFontAccessorReceiver: String =
    "io.github.hlcaptain.symbols.Symbols"

private const val ResourceClassAlias: String = "_SymbolsResourceClass"
private const val SymbolFontsExtensionAlias: String = "_symbolsFontDescriptors"

internal fun composeAccessorName(fontFile: Path): String {
    val fileName = fontFile.fileName.toString()
    val baseName = fileName.substringBeforeLast('.', fileName).replace('-', '_')
    val accessorName = if (baseName.firstOrNull()?.isDigit() == true) {
        "_$baseName"
    } else {
        baseName
    }
    SymbolNames.requireTypeIdentifier(accessorName, "Compose font resource accessor")
    return accessorName
}

private fun floatLiteral(value: Float): String = "${value}f"

private fun kotlinString(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '$' -> append("\\$")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code in 0x20..0x7E) {
                append(character)
            } else {
                append("\\u")
                append(character.code.toString(16).padStart(4, '0'))
            }
        }
    }
    append('"')
}

private val SupportedDescriptorFontExtensions: Set<String> = setOf("ttf", "otf", "ttc")
