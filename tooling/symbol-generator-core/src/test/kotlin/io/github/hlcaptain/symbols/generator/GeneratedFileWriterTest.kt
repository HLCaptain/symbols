package io.github.hlcaptain.symbols.generator

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratedFileWriterTest {
    @Test
    fun preservesUnownedFilesAndDoesNotRewriteUnchangedOutputs() {
        val directory = createTempDirectory("symbols-writer-")
        try {
            directory.resolve("user.txt").writeText("owned by consumer")
            val first = GeneratedFileWriter.synchronize(
                directory,
                RenderedFiles(
                    mapOf(
                        "kotlin/One.kt" to "one\n",
                        "kotlin/Old.kt" to "old\n",
                    ),
                ),
            )
            val one = directory.resolve("kotlin/One.kt")
            val modifiedTime = Files.getLastModifiedTime(one)

            val second = GeneratedFileWriter.synchronize(
                directory,
                RenderedFiles(
                    mapOf(
                        "kotlin/One.kt" to "one\n",
                        "kotlin/Two.kt" to "two\n",
                    ),
                ),
            )

            assertEquals(WriteResult(written = 2, unchanged = 0, deleted = 0), first)
            assertEquals(WriteResult(written = 1, unchanged = 1, deleted = 1), second)
            assertEquals(modifiedTime, Files.getLastModifiedTime(one))
            assertEquals("owned by consumer", directory.resolve("user.txt").readText())
            assertFalse(Files.exists(directory.resolve("kotlin/Old.kt")))
            assertTrue(Files.isRegularFile(directory.resolve("kotlin/Two.kt")))

            val cleared = GeneratedFileWriter.clear(directory)

            assertEquals(WriteResult(written = 0, unchanged = 0, deleted = 2), cleared)
            assertEquals("owned by consumer", directory.resolve("user.txt").readText())
            assertFalse(Files.exists(one))
            assertFalse(Files.exists(directory.resolve("kotlin/Two.kt")))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
