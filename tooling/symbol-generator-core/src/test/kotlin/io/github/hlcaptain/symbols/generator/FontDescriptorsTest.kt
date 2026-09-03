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

        val publicFiles = KotlinFontDescriptorsRenderer().render(
            packageName = "com.example.generated.resources",
            resClassName = "Res",
            publicAccessors = true,
            descriptors = descriptors,
        ).files
        val publicSource = publicFiles.getValue(
            "com/example/generated/resources/SymbolFonts.generated.kt",
        )

        assertTrue("object SymbolFonts" in publicSource)
        assertTrue(
            "import com.example.generated.resources.Res as _SymbolsResourceClass" in publicSource,
        )
        assertTrue("val _SymbolsResourceClass.symbolFonts: SymbolFonts" in publicSource)
        assertTrue("val regular_font: SymbolFont.Regular" in publicSource)
        assertTrue("SymbolFont.regular(" in publicSource)
        assertTrue("resource = _SymbolsResourceClass.font.regular_font" in publicSource)
        assertTrue("val variable_font: SymbolFont.Variable" in publicSource)
        assertTrue("SymbolFont.variable(" in publicSource)
        assertTrue("resource = _SymbolsResourceClass.font.variable_font" in publicSource)
        assertTrue("SymbolFontAxis(\"wght\", 100.0f, 400.0f, 900.0f)" in publicSource)
        assertTrue("SymbolFontAxis(\"opsz\", 8.0f, 14.0f, 72.0f)" in publicSource)
        assertTrue(publicSource.indexOf("\"wght\"") < publicSource.indexOf("\"opsz\""))
        assertFalse("\"HIDE\"" in publicSource)
        assertTrue("val hidden_only: SymbolFont.Variable" in publicSource)
        assertTrue("variationAxes = emptyList()" in publicSource)
        assertFalse(Regex("\\bval all\\b").containsMatchIn(publicSource))
        assertFalse(Regex("\\bpublic\\b").containsMatchIn(publicSource))
        val defaultAccessor = publicFiles.getValue(
            "com/example/generated/resources/FontAccessor_regular_font.generated.kt",
        )
        assertTrue(
            "val io.github.hlcaptain.symbols.Symbols.RegularFont: SymbolFont.Regular" in
                defaultAccessor,
        )
        assertTrue(
            "get() = _SymbolsResourceClass._symbolsFontDescriptors.regular_font" in
                defaultAccessor,
        )

        val internalSource = KotlinFontDescriptorsRenderer().render(
            packageName = "com.example.generated.resources",
            resClassName = "symbolFonts",
            publicAccessors = false,
            descriptors = listOf(descriptors.first()),
        ).files.getValue(
            "com/example/generated/resources/SymbolFonts.generated.kt",
        )
        assertTrue("internal object SymbolFonts" in internalSource)
        assertTrue(
            "import com.example.generated.resources.symbolFonts as _SymbolsResourceClass" in
                internalSource,
        )
        assertTrue("internal val _SymbolsResourceClass.symbolFonts: SymbolFonts" in internalSource)
        assertTrue("resource = _SymbolsResourceClass.font.regular_font" in internalSource)
        assertFalse("public object SymbolFonts" in internalSource)
    }

    @Test
    fun rendersPublicReceiverAccessorsWithFixedRegularSettings() {
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
                ),
            ),
        )
        val rendered = KotlinFontDescriptorsRenderer().render(
            packageName = "com.example.resources",
            resClassName = "SymbolFont",
            publicAccessors = false,
            descriptors = descriptors,
            fontAccessors = listOf(
                GeneratedFontAccessor(
                    resourceAccessorName = "regular_font",
                    receiver = "com.example.Icons.Rounded",
                    propertyName = "staticFont",
                    packageName = "com.example.api",
                    componentIndex = 2,
                    fixedAxisValues = mapOf("wght" to 400f, "FILL" to 0f),
                ),
            ),
        ).files

        assertEquals(3, rendered.size)
        val descriptorsSource = rendered.getValue(
            "com/example/resources/SymbolFonts.generated.kt",
        )
        assertTrue("internal object SymbolFonts" in descriptorsSource)
        assertTrue(
            "import com.example.resources.SymbolFont as _SymbolsResourceClass" in
                descriptorsSource,
        )
        assertTrue("resource = _SymbolsResourceClass.font.regular_font" in descriptorsSource)
        assertTrue("fontSettings = SymbolFontSettings(" in descriptorsSource)
        assertTrue("FontVariation.Setting(\"FILL\", 0.0f)" in descriptorsSource)
        assertTrue("FontVariation.Setting(\"wght\", 400.0f)" in descriptorsSource)
        assertTrue(descriptorsSource.indexOf("\"FILL\"") < descriptorsSource.indexOf("\"wght\""))

        val regularAccessor = rendered.getValue(
            "com/example/api/FontAccessor_regular_font.generated.kt",
        )
        assertTrue(
            "import com.example.resources.SymbolFont as _SymbolsResourceClass" in regularAccessor,
        )
        assertTrue(
            "import com.example.resources.symbolFonts as _symbolsFontDescriptors" in
                regularAccessor,
        )
        assertTrue(
            "val com.example.Icons.Rounded.staticFont: SymbolFont.Regular" in regularAccessor,
        )
        assertTrue(
            "get() = _SymbolsResourceClass._symbolsFontDescriptors.regular_font" in
                regularAccessor,
        )
        assertTrue("operator fun com.example.Icons.Rounded.component2()" in regularAccessor)
        assertFalse(Regex("\\bpublic\\b").containsMatchIn(regularAccessor))

        val variableAccessor = rendered.getValue(
            "com/example/resources/FontAccessor_variable_font.generated.kt",
        )
        assertTrue(
            "val io.github.hlcaptain.symbols.Symbols.VariableFont: SymbolFont.Variable" in
                variableAccessor,
        )
        assertFalse("component" in variableAccessor)
    }

    @Test
    fun rejectsInvalidOrCollidingFontAccessors() {
        val regular = GeneratedFontDescriptor("regular", "Regular", emptyList())
        val variable = GeneratedFontDescriptor(
            "variable",
            "Variable",
            listOf(GeneratedFontAxis("wght", 100f, 400f, 900f, hidden = false)),
        )
        fun accessor(
            resource: String,
            property: String = resource,
            component: Int? = null,
            axes: Map<String, Float> = emptyMap(),
        ) = GeneratedFontAccessor(
            resourceAccessorName = resource,
            receiver = "com.example.Icons",
            propertyName = property,
            packageName = "com.example",
            componentIndex = component,
            fixedAxisValues = axes,
        )
        fun render(vararg accessors: GeneratedFontAccessor) =
            KotlinFontDescriptorsRenderer().render(
                packageName = "com.example.resources",
                resClassName = "Res",
                publicAccessors = false,
                descriptors = listOf(regular, variable),
                fontAccessors = accessors.toList(),
            )

        assertTrue(
            "missing Compose font resources" in assertFailsWith<IllegalArgumentException> {
                render(accessor("missing"))
            }.message.orEmpty(),
        )
        assertTrue(
            "property collisions" in assertFailsWith<IllegalArgumentException> {
                render(accessor("regular", "font"), accessor("variable", "font"))
            }.message.orEmpty(),
        )
        assertTrue(
            "component collisions" in assertFailsWith<IllegalArgumentException> {
                render(accessor("regular", component = 1), accessor("variable", component = 1))
            }.message.orEmpty(),
        )
        assertTrue(
            "cannot declare fixedAxisValues" in assertFailsWith<IllegalArgumentException> {
                render(accessor("variable", axes = mapOf("wght" to 400f)))
            }.message.orEmpty(),
        )
        assertTrue(
            "property collisions" in assertFailsWith<IllegalArgumentException> {
                KotlinFontDescriptorsRenderer().render(
                    packageName = "com.example.resources",
                    resClassName = "Res",
                    publicAccessors = false,
                    descriptors = listOf(
                        GeneratedFontDescriptor("foo_bar", "First", emptyList()),
                        GeneratedFontDescriptor("foo__bar", "Second", emptyList()),
                    ),
                )
            }.message.orEmpty(),
        )
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
                    "--font-accessor",
                    "academic_icons",
                    "com.example.Icons",
                    "AcademicIcons",
                    "com.example.api",
                    "",
                    "1",
                    "wght",
                    "400",
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
        assertTrue("resource = _SymbolsResourceClass.font.academic_icons" in generated)
        assertTrue("fontSettings = SymbolFontSettings(" in generated)
        assertTrue(
            Files.isRegularFile(
                output.resolve(
                    "com/example/api/FontAccessor_academic_icons.generated.kt",
                ),
            ),
        )
        assertFalse(
            "component" in output.resolve(
                "com/example/api/FontAccessor_academic_icons.generated.kt",
            ).readText(),
        )

        val automaticOutput = root.resolve("automatic")
        assertEquals(
            0,
            SymbolFontDescriptorsCli.run(
                arrayOf(
                    "--resource-root",
                    resources.toString(),
                    "--package",
                    "com.example.resources",
                    "--res-class",
                    "Assets",
                    "--output",
                    automaticOutput.toString(),
                ),
            ),
        )
        val automaticAccessor = automaticOutput.resolve(
            "com/example/resources/FontAccessor_academic_icons.generated.kt",
        ).readText()
        assertTrue(
            "val io.github.hlcaptain.symbols.Symbols.AcademicIcons: SymbolFont.Regular" in
                automaticAccessor,
        )
        assertTrue(
            "get() = _SymbolsResourceClass._symbolsFontDescriptors.academic_icons" in
                automaticAccessor,
        )

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
