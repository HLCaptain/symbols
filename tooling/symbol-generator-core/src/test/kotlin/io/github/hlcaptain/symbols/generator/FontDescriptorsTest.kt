package io.github.hlcaptain.symbols.generator

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FontDescriptorsTest {
    private val repositoryRoot: Path =
        Path.of(requireNotNull(System.getProperty("symbols.repositoryRoot")))
            .toAbsolutePath()
            .normalize()

    private val variableFont: Path = repositoryRoot.resolve(
        "samples/custom-variable/src/commonMain/composeResources/font/" +
            "academmunicons_variable.ttf",
    )
    private val regularFont: Path = repositoryRoot.resolve(
        "fonts/samples/academmunicons/academmunicons-regular.ttf",
    )

    @Test
    fun scansDirectFontsWithComposeNamesAndCompleteAxisMetadata() = withTemporaryRoot { root ->
        val fonts = Files.createDirectories(root.resolve("font"))
        Files.copy(regularFont, fonts.resolve("1-regular.ttf"))
        Files.copy(variableFont, fonts.resolve("academmunicons-variable.ttf"))
        Files.writeString(fonts.resolve("ignored.woff2"), "ignored")
        val nested = Files.createDirectories(fonts.resolve("nested"))
        Files.copy(variableFont, nested.resolve("not_scanned.ttf"))

        val descriptors = SkikoFontDescriptorScanner().scan(listOf(root))

        assertEquals(
            listOf("_1_regular", "academmunicons_variable"),
            descriptors.map(GeneratedFontDescriptor::accessorName),
        )
        val regular = descriptors[0]
        assertEquals("Symbols Academic Icons", regular.familyName)
        assertTrue(regular.axes.isEmpty())

        val variable = descriptors[1]
        assertEquals("Academmunicons VF", variable.familyName)
        assertEquals(
            listOf(
                GeneratedFontAxis("ital", 0f, 0f, 1f, hidden = false),
                GeneratedFontAxis("wght", 100f, 100f, 800f, hidden = false),
            ),
            variable.axes,
        )
    }

    @Test
    fun rendersTypedSingletonAccessorsAndOmitsOnlyHiddenAxes() {
        val descriptors = listOf(
            GeneratedFontDescriptor(
                accessorName = "regular_font",
                familyName = "Regular Font",
                axes = emptyList(),
            ),
            GeneratedFontDescriptor(
                accessorName = "variable_font",
                familyName = "Variable Font",
                axes = listOf(
                    GeneratedFontAxis("wght", 100f, 400f, 900f, hidden = false),
                    GeneratedFontAxis("HIDE", 0f, 1f, 1f, hidden = true),
                    GeneratedFontAxis("opsz", 8f, 14f, 72f, hidden = false),
                ),
            ),
            GeneratedFontDescriptor(
                accessorName = "hidden_only",
                familyName = "Hidden Only",
                axes = listOf(
                    GeneratedFontAxis("HIDE", 0f, 0f, 1f, hidden = true),
                ),
            ),
        )

        val publicSource = KotlinFontDescriptorsRenderer().render(
            packageName = "com.example.generated.resources",
            resClassName = "Res",
            publicAccessors = true,
            descriptors = descriptors,
        ).files.values.single()

        assertTrue("object SymbolFonts" in publicSource)
        assertTrue("val Res.symbolFonts: SymbolFonts" in publicSource)
        assertTrue("val regular_font: SymbolFont.Regular" in publicSource)
        assertTrue("SymbolFont.regular(" in publicSource)
        assertTrue("resource = Res.font.regular_font" in publicSource)
        assertTrue("val variable_font: SymbolFont.Variable" in publicSource)
        assertTrue("SymbolFont.variable(" in publicSource)
        assertTrue("resource = Res.font.variable_font" in publicSource)
        assertTrue("SymbolFontAxis(\"wght\", 100.0f, 400.0f, 900.0f)" in publicSource)
        assertTrue("SymbolFontAxis(\"opsz\", 8.0f, 14.0f, 72.0f)" in publicSource)
        assertTrue(publicSource.indexOf("\"wght\"") < publicSource.indexOf("\"opsz\""))
        assertFalse("\"HIDE\"" in publicSource)
        assertTrue("val hidden_only: SymbolFont.Variable" in publicSource)
        assertTrue("variationAxes = emptyList()" in publicSource)
        assertFalse(Regex("\\bval all\\b").containsMatchIn(publicSource))
        assertFalse(Regex("\\bpublic\\b").containsMatchIn(publicSource))

        val internalSource = KotlinFontDescriptorsRenderer().render(
            packageName = "com.example.generated.resources",
            resClassName = "Assets",
            publicAccessors = false,
            descriptors = listOf(descriptors.first()),
        ).files.values.single()
        assertTrue("internal object SymbolFonts" in internalSource)
        assertTrue("internal val Assets.symbolFonts: SymbolFonts" in internalSource)
        assertTrue("resource = Assets.font.regular_font" in internalSource)
        assertFalse("public object SymbolFonts" in internalSource)
    }

    @Test
    fun rejectsNormalizedAccessorCollisionsAndInvalidIdentifiers() = withTemporaryRoot { root ->
        val fonts = Files.createDirectories(root.resolve("font"))
        Files.writeString(fonts.resolve("same-name.ttf"), "first")
        Files.writeString(fonts.resolve("same_name.otf"), "second")

        val collision = assertFailsWith<SymbolGenerationException> {
            SkikoFontDescriptorScanner().scan(listOf(root))
        }
        assertTrue("same_name" in collision.message.orEmpty())

        Files.delete(fonts.resolve("same-name.ttf"))
        Files.delete(fonts.resolve("same_name.otf"))
        Files.writeString(fonts.resolve("bad.name.ttf"), "invalid")
        val invalid = assertFailsWith<IllegalArgumentException> {
            SkikoFontDescriptorScanner().scan(listOf(root))
        }
        assertTrue("Invalid Compose font resource accessor" in invalid.message.orEmpty())
    }

    @Test
    fun cliWritesGeneratedKotlinAndReportsArgumentErrors() = withTemporaryRoot { root ->
        val resources = Files.createDirectories(root.resolve("resources"))
        val fonts = Files.createDirectories(resources.resolve("font"))
        Files.copy(regularFont, fonts.resolve("academic-icons.ttf"))
        val output = root.resolve("generated")
        val standardOut = ByteArrayOutputStream()

        assertEquals(
            0,
            SymbolFontDescriptorsCli.run(
                arrayOf(
                    "--resource-root",
                    resources.toString(),
                    "--package",
                    "com.example.resources",
                    "--res-class",
                    "Res",
                    "--output",
                    output.toString(),
                    "--public",
                ),
                standardOut = PrintStream(standardOut),
            ),
        )
        assertTrue("Generated 1 font descriptors" in standardOut.toString())
        val generated = output.resolve(
            "com/example/resources/SymbolFonts.generated.kt",
        ).readText()
        assertTrue("val academic_icons: SymbolFont.Regular" in generated)
        assertFalse(Regex("\\bpublic\\b").containsMatchIn(generated))
        assertTrue("resource = Res.font.academic_icons" in generated)

        val errors = ByteArrayOutputStream()
        assertEquals(
            2,
            SymbolFontDescriptorsCli.run(
                arrayOf("--unknown", "value"),
                standardError = PrintStream(errors),
            ),
        )
        assertTrue("Unknown options" in errors.toString())
    }

    private fun withTemporaryRoot(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("symbol-font-descriptors-")
        try {
            block(root)
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder())
                    .forEach(Files::deleteIfExists)
            }
        }
    }
}
