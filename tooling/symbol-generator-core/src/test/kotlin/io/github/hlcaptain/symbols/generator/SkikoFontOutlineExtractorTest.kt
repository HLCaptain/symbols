package io.github.hlcaptain.symbols.generator

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SkikoFontOutlineExtractorTest {
    private val repositoryRoot: Path =
        Path.of(requireNotNull(System.getProperty("symbols.repositoryRoot")))
            .toAbsolutePath()
            .normalize()

    private val variableFont: Path = repositoryRoot.resolve(
        "fonts/material/outlined/composeResources/font/" +
            "material_symbols_outlined_variable.ttf",
    )
    private val regularFont: Path = repositoryRoot.resolve(
        "fonts/material/outlined-static/composeResources/font/" +
            "material_symbols_outlined_regular.ttf",
    )

    @Test
    fun extractsDefaultAndNonDefaultVariableInstances() {
        val extractor = SkikoFontOutlineExtractor()
        val default = extractor.extract(
            FontExtractionRequest(
                fontFile = variableFont,
                codePoints = listOf(0xE88A, 0xE8B6),
            ),
        )
        val filled = extractor.extract(
            FontExtractionRequest(
                fontFile = variableFont,
                codePoints = listOf(0xE88A, 0xE8B6),
                axes = mapOf(
                    "FILL" to 1f,
                    "GRAD" to 0f,
                    "opsz" to 24f,
                    "wght" to 700f,
                ),
            ),
        )

        assertEquals(960, default.unitsPerEm)
        assertEquals(
            setOf("FILL", "GRAD", "opsz", "wght"),
            default.appliedAxes.keys,
        )
        assertEquals(0f, default.appliedAxes.getValue("FILL"))
        assertEquals(1f, filled.appliedAxes.getValue("FILL"))
        assertTrue(default.outlines.values.all { it.commands.isNotEmpty() })
        assertTrue(default.outlines.values.all { it.commands.first() is VectorCommand.MoveTo })
        assertNotEquals(
            default.outlines.getValue(0xE88A).commands,
            filled.outlines.getValue(0xE88A).commands,
        )
    }

    @Test
    fun extractsRegularFontAndRejectsAxes() {
        val extractor = SkikoFontOutlineExtractor()
        val regular = extractor.extract(
            FontExtractionRequest(
                fontFile = regularFont,
                codePoints = listOf(0xE88A),
            ),
        )

        assertTrue(regular.appliedAxes.isEmpty())
        assertTrue(regular.outlines.getValue(0xE88A).commands.isNotEmpty())
        assertFailsWith<SymbolGenerationException> {
            extractor.extract(
                FontExtractionRequest(
                    fontFile = regularFont,
                    codePoints = listOf(0xE88A),
                    axes = mapOf("wght" to 400f),
                ),
            )
        }
    }

    @Test
    fun rejectsUnknownOutOfRangeAxesAndMissingCodePoints() {
        val extractor = SkikoFontOutlineExtractor()
        assertFailsWith<SymbolGenerationException> {
            extractor.extract(
                FontExtractionRequest(
                    fontFile = variableFont,
                    codePoints = listOf(0xE88A),
                    axes = mapOf("NOPE" to 1f),
                ),
            )
        }
        assertFailsWith<SymbolGenerationException> {
            extractor.extract(
                FontExtractionRequest(
                    fontFile = variableFont,
                    codePoints = listOf(0xE88A),
                    axes = mapOf("wght" to 900f),
                ),
            )
        }
        assertFailsWith<SymbolGenerationException> {
            extractor.extract(
                FontExtractionRequest(
                    fontFile = variableFont,
                    codePoints = listOf(0x10FFFF),
                ),
            )
        }
    }
}
