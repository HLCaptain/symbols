package io.github.hlcaptain.symbols.generator

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Parses the stable `<snake_case_name> <hex_code_point>` manifest format. */
object SymbolManifestParser {
    private val linePattern =
        Regex("^(?<name>[a-z0-9]+(?:_[a-z0-9]+)*)[ \\t]+(?<codePoint>[0-9a-fA-F]{1,6})[ \\t]*$")

    /**
     * Reads a UTF-8 manifest from [path] and creates a validated catalog.
     *
     * Each nonblank line must contain a lowercase snake-case name followed by a
     * hexadecimal Unicode code point. The file is opened only for this call and
     * is closed before the function returns. No files are changed.
     *
     * @param path manifest file to read.
     * @return entries sorted and indexed by [SymbolCatalog].
     * @throws SymbolGenerationException if the file is missing or empty, a line
     * is malformed, or names and generated Kotlin identifiers collide.
     * @throws java.io.IOException if the manifest cannot be read.
     */
    fun parse(path: Path): SymbolCatalog {
        if (!Files.isRegularFile(path)) {
            throw SymbolGenerationException("Manifest does not exist: $path")
        }
        return Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
            parse(reader.readLines(), path.toString())
        }
    }

    /**
     * Parses manifest [content] without reading or writing files.
     *
     * Blank lines are ignored. Every other line must contain a lowercase
     * snake-case name followed by a hexadecimal Unicode code point.
     * [sourceName] is included in error messages and does not need to name a
     * real file.
     *
     * @param content complete manifest text.
     * @param sourceName label shown with line numbers when validation fails.
     * @return entries sorted and indexed by [SymbolCatalog].
     * @throws SymbolGenerationException if the content is empty, a line is
     * malformed, or names and generated Kotlin identifiers collide.
     */
    fun parse(content: String, sourceName: String = "<manifest>"): SymbolCatalog =
        parse(content.lineSequence().toList(), sourceName)

    private fun parse(lines: List<String>, sourceName: String): SymbolCatalog {
        val entries = buildList {
            lines.forEachIndexed { index, line ->
                if (line.isBlank()) {
                    return@forEachIndexed
                }
                val match = linePattern.matchEntire(line)
                    ?: throw SymbolGenerationException(
                        "$sourceName:${index + 1}: expected " +
                            "'<snake_case_name> <hex_code_point>'",
                    )
                val codePoint = match.groups["codePoint"]!!.value.toInt(16)
                try {
                    add(
                        SymbolEntry(
                            name = match.groups["name"]!!.value,
                            codePoint = codePoint,
                        ),
                    )
                } catch (error: IllegalArgumentException) {
                    throw SymbolGenerationException(
                        "$sourceName:${index + 1}: ${error.message}",
                        error,
                    )
                }
            }
        }

        return try {
            SymbolCatalog.of(entries)
        } catch (error: IllegalArgumentException) {
            throw SymbolGenerationException("$sourceName: ${error.message}", error)
        }
    }
}
