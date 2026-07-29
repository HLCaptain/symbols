package io.github.hlcaptain.symbols.generator

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Parses the stable `<snake_case_name> <hex_code_point>` manifest format. */
public object SymbolManifestParser {
    private val linePattern =
        Regex("^(?<name>[a-z0-9]+(?:_[a-z0-9]+)*)[ \\t]+(?<codePoint>[0-9a-fA-F]{1,6})[ \\t]*$")

    public fun parse(path: Path): SymbolCatalog {
        if (!Files.isRegularFile(path)) {
            throw SymbolGenerationException("Manifest does not exist: $path")
        }
        return Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
            parse(reader.readLines(), path.toString())
        }
    }

    public fun parse(content: String, sourceName: String = "<manifest>"): SymbolCatalog =
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
