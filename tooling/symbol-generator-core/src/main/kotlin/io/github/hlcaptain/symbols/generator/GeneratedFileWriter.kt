package io.github.hlcaptain.symbols.generator

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Synchronizes a dedicated generated output directory without deleting files
 * owned by another task. A private state file records only paths from the
 * previous invocation of this writer.
 */
object GeneratedFileWriter {
    fun synchronize(
        outputDirectory: Path,
        renderedFiles: RenderedFiles,
        stateFileName: String = ".symbols-generated-files",
    ): WriteResult {
        require('/' !in stateFileName && '\\' !in stateFileName) {
            "stateFileName must be a simple filename"
        }

        val root = outputDirectory.toAbsolutePath().normalize()
        Files.createDirectories(root)
        val stateFile = root.resolve(stateFileName)
        val previous = if (Files.isRegularFile(stateFile)) {
            Files.readAllLines(stateFile, StandardCharsets.UTF_8)
                .filter(String::isNotBlank)
                .toSet()
        } else {
            emptySet()
        }
        val expected = renderedFiles.files.keys.toSortedSet()

        var deleted = 0
        (previous - expected).sortedDescending().forEach { relativePath ->
            val target = safeResolve(root, relativePath)
            if (Files.deleteIfExists(target)) {
                deleted += 1
                pruneEmptyParents(target.parent, root)
            }
        }

        var written = 0
        var unchanged = 0
        renderedFiles.files.toSortedMap().forEach { (relativePath, content) ->
            val target = safeResolve(root, relativePath)
            val bytes = content.toByteArray(StandardCharsets.UTF_8)
            if (Files.isRegularFile(target) && Files.readAllBytes(target).contentEquals(bytes)) {
                unchanged += 1
            } else {
                Files.createDirectories(target.parent)
                writeAtomically(target, bytes)
                written += 1
            }
        }

        val stateContent = expected.joinToString(separator = "\n", postfix = "\n")
        val stateBytes = stateContent.toByteArray(StandardCharsets.UTF_8)
        if (
            !Files.isRegularFile(stateFile) ||
            !Files.readAllBytes(stateFile).contentEquals(stateBytes)
        ) {
            writeAtomically(stateFile, stateBytes)
        }
        return WriteResult(written = written, unchanged = unchanged, deleted = deleted)
    }

    private fun safeResolve(root: Path, relativePath: String): Path {
        val resolved = root.resolve(relativePath).normalize()
        if (!resolved.startsWith(root)) {
            throw SymbolGenerationException(
                "Generated path escapes output directory: $relativePath",
            )
        }
        return resolved
    }

    private fun writeAtomically(target: Path, bytes: ByteArray) {
        val temporary = Files.createTempFile(target.parent, ".symbols-", ".tmp")
        try {
            Files.write(temporary, bytes)
            try {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun pruneEmptyParents(directory: Path?, root: Path) {
        var current = directory
        while (current != null && current != root && current.startsWith(root)) {
            Files.newDirectoryStream(current).use { children ->
                if (children.iterator().hasNext()) {
                    return
                }
            }
            Files.deleteIfExists(current)
            current = current.parent
        }
    }
}

/** File synchronization statistics. */
data class WriteResult(
    val written: Int,
    val unchanged: Int,
    val deleted: Int,
)
