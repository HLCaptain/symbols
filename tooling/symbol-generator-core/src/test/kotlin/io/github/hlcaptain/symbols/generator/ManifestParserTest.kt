package io.github.hlcaptain.symbols.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ManifestParserTest {
    @Test
    fun parsesSortsAndGroupsAliasesByCodePoint() {
        val catalog = SymbolManifestParser.parse(
            """
            |star f09a
            |3d_rotation e84d
            |grade f09a
            |home e88a
            |""".trimMargin(),
        )

        assertEquals(
            listOf("3d_rotation", "grade", "home", "star"),
            catalog.entries.map(SymbolEntry::name),
        )
        assertEquals("_3dRotation", catalog.entries.first().kotlinName)
        assertEquals(listOf(0xE84D, 0xE88A, 0xF09A), catalog.uniqueCodePoints)
        assertEquals(
            listOf("grade", "star"),
            catalog.entriesByCodePoint.getValue(0xF09A).map(SymbolEntry::name),
        )
    }

    @Test
    fun reportsSourceAndLineForMalformedInput() {
        val error = assertFailsWith<SymbolGenerationException> {
            SymbolManifestParser.parse(
                "home e88a\nNot-Snake e001\n",
                sourceName = "icons.codepoints",
            )
        }

        assertTrue("icons.codepoints:2" in error.message.orEmpty())
    }

    @Test
    fun rejectsDuplicatesInvalidScalarsAndIdentifierCollisions() {
        assertFailsWith<SymbolGenerationException> {
            SymbolManifestParser.parse("home e88a\nhome e88b\n")
        }
        assertFailsWith<SymbolGenerationException> {
            SymbolManifestParser.parse("invalid d800\n")
        }
        val collision = assertFailsWith<SymbolGenerationException> {
            SymbolManifestParser.parse("a1b e000\na_1b e001\n")
        }
        assertTrue("A1b" in collision.message.orEmpty())
    }

    @Test
    fun rejectsNamesThatCannotBeUnescapedKotlinIdentifiers() {
        val hardKeywords = listOf(
            "as",
            "break",
            "class",
            "continue",
            "do",
            "else",
            "false",
            "for",
            "fun",
            "if",
            "in",
            "interface",
            "is",
            "null",
            "object",
            "package",
            "return",
            "super",
            "this",
            "throw",
            "true",
            "try",
            "typealias",
            "typeof",
            "val",
            "var",
            "when",
            "while",
        )

        hardKeywords.forEach { keyword ->
            assertFailsWith<IllegalArgumentException>(keyword) {
                SymbolNames.requireTypeIdentifier(keyword, "icon-set name")
            }
            assertFailsWith<IllegalArgumentException>(keyword) {
                SymbolNames.requirePackageName("com.example.$keyword")
            }
        }
        listOf("_", "__", "___").forEach { underscores ->
            assertFailsWith<IllegalArgumentException>(underscores) {
                SymbolNames.requireTypeIdentifier(underscores, "style name")
            }
            assertFailsWith<IllegalArgumentException>(underscores) {
                SymbolNames.requirePackageName("com.$underscores.icons")
            }
        }

        SymbolNames.requireTypeIdentifier("Class", "icon-set name")
        SymbolNames.requireTypeIdentifier("_Class", "style name")
        SymbolNames.requirePackageName("com.example.data")
        assertEquals("_class", SymbolNames.packageSegment("Class"))
        assertEquals("_when", SymbolNames.packageSegment("When"))
    }
}
