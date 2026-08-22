package io.github.hlcaptain.symbols.generator

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SymbolGeneratorCliTest {
    @Test
    fun helpAndInvalidArgumentsHaveStableExitCodes() {
        val help = ByteArrayOutputStream()
        assertEquals(
            0,
            SymbolGeneratorCli.run(
                arrayOf("--help"),
                standardOut = PrintStream(help),
            ),
        )
        assertTrue("Usage: symbol-generator-core" in help.toString())

        val errors = ByteArrayOutputStream()
        assertEquals(
            2,
            SymbolGeneratorCli.run(
                arrayOf("--unknown", "value"),
                standardError = PrintStream(errors),
            ),
        )
        assertTrue("Unknown options" in errors.toString())
    }

    @Test
    fun reportsIoErrorsWithoutLeakingAStackTrace() {
        val directory = Files.createTempDirectory("symbol-generator-io-")
        try {
            val manifest = directory.resolve("invalid.codepoints")
            Files.write(manifest, byteArrayOf(0xC3.toByte()))
            val errors = ByteArrayOutputStream()

            assertEquals(
                1,
                SymbolGeneratorCli.run(
                    arrayOf(
                        "--font", directory.resolve("font.ttf").toString(),
                        "--manifest", manifest.toString(),
                        "--package", "com.example.icons",
                        "--set", "AppIcons",
                        "--style", "Regular",
                        "--kotlin-output", directory.resolve("output").toString(),
                    ),
                    standardError = PrintStream(errors),
                ),
            )
            assertTrue(errors.toString().startsWith("error: "))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun generatesAllOutputsAndRemovesOnlyStaleOwnedFiles() {
        val repositoryRoot = Path.of(
            requireNotNull(System.getProperty("symbols.repositoryRoot")),
        )
        val font = repositoryRoot.resolve(
            "fonts/material/outlined-static/composeResources/font/" +
                "material_symbols_outlined_regular.ttf",
        )
        val temporaryRoot = Files.createTempDirectory("symbol-generator-cli-")
        try {
            val manifest = temporaryRoot.resolve("symbols.codepoints")
            manifest.writeText("check e5ca\nclose e5cd\n")
            val kotlinOutput = temporaryRoot.resolve("kotlin")
            val androidOutput = temporaryRoot.resolve("android")
            val composeOutput = temporaryRoot.resolve("compose")

            val commonArguments = arrayOf(
                "--font",
                font.toString(),
                "--manifest",
                manifest.toString(),
                "--package",
                "com.example.icons",
                "--set",
                "AppIcons",
                "--style",
                "Rounded",
                "--kotlin-output",
                kotlinOutput.toString(),
                "--android-output",
                androidOutput.toString(),
                "--compose-output",
                composeOutput.toString(),
            )

            assertEquals(
                0,
                SymbolGeneratorCli.run(commonArguments),
            )
            val checkRelativePath =
                "drawable/app_icons_rounded_check_ue5ca.xml"
            val closeRelativePath =
                "drawable/app_icons_rounded_close_ue5cd.xml"
            assertTrue(androidOutput.resolve(checkRelativePath).exists())
            assertTrue(androidOutput.resolve(closeRelativePath).exists())
            assertEquals(
                androidOutput.resolve(checkRelativePath).readText(),
                composeOutput.resolve(checkRelativePath).readText(),
            )

            val secondOutput = ByteArrayOutputStream()
            manifest.writeText("check e5ca\n")
            assertEquals(
                0,
                SymbolGeneratorCli.run(
                    commonArguments,
                    standardOut = PrintStream(secondOutput),
                ),
            )
            assertTrue("Generated 1 names / 1 code points" in secondOutput.toString())
            assertFalse(androidOutput.resolve(closeRelativePath).exists())
            assertFalse(composeOutput.resolve(closeRelativePath).exists())

            val generatedKotlin = kotlinOutput
                .resolve(
                    "com/example/icons/rounded/" +
                        "AppIconsRoundedIcons000.generated.kt",
                )
                .readText()
            assertTrue(".Check: ImageVector" in generatedKotlin)
            assertFalse(".Close: ImageVector" in generatedKotlin)
        } finally {
            Files.walk(temporaryRoot).use { paths ->
                paths.sorted(Comparator.reverseOrder())
                    .forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun generatesStyledVectorsFromEverySvgFile() {
        val temporaryRoot = Files.createTempDirectory("symbol-generator-svg-cli-")
        try {
            val svgDirectory = Files.createDirectory(temporaryRoot.resolve("svg"))
            svgDirectory.resolve("home.svg").writeText(
                """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
                    fill="none" stroke="currentColor" stroke-width="2"
                    stroke-linecap="round" stroke-linejoin="round">
                    <path d="M3 12 L12 3 L21 12" />
                </svg>
                """.trimIndent(),
            )
            svgDirectory.resolve("settings.svg").writeText(
                """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                    <path fill="currentColor" d="M4 4 H20 V20 H4 Z" />
                </svg>
                """.trimIndent(),
            )
            val kotlinOutput = temporaryRoot.resolve("kotlin")
            val androidOutput = temporaryRoot.resolve("android")
            val composeOutput = temporaryRoot.resolve("compose")

            val output = ByteArrayOutputStream()
            assertEquals(
                0,
                SymbolGeneratorCli.run(
                    arrayOf(
                        "--svg-directory", svgDirectory.toString(),
                        "--package", "com.example.icons",
                        "--set", "Tabler",
                        "--style", "Outline",
                        "--kotlin-output", kotlinOutput.toString(),
                        "--android-output", androidOutput.toString(),
                        "--compose-output", composeOutput.toString(),
                    ),
                    standardOut = PrintStream(output),
                ),
            )

            assertTrue("Generated 2 SVG icons" in output.toString())
            val homePath = "drawable/tabler_outline_home.xml"
            val homeXml = androidOutput.resolve(homePath).readText()
            assertTrue("android:strokeWidth=\"2\"" in homeXml)
            assertTrue("android:strokeLineCap=\"round\"" in homeXml)
            assertEquals(homeXml, composeOutput.resolve(homePath).readText())
            assertTrue(
                "Tabler.Outline.Settings" in kotlinOutput.resolve(
                    "com/example/icons/outline/" +
                        "TablerOutlineIcons000.generated.kt",
                ).readText(),
            )
        } finally {
            temporaryRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsMalformedNumbersAndOverlappingOutputs() {
        val commonArguments = arrayOf(
            "--font",
            "font.ttf",
            "--manifest",
            "symbols.codepoints",
            "--package",
            "com.example.icons",
            "--set",
            "AppIcons",
            "--style",
            "Rounded",
        )

        val malformedError = ByteArrayOutputStream()
        assertEquals(
            2,
            SymbolGeneratorCli.run(
                commonArguments + arrayOf(
                    "--kotlin-output",
                    "generated/kotlin",
                    "--precision",
                    "four",
                ),
                standardError = PrintStream(malformedError),
            ),
        )
        assertTrue("--precision must be an integer" in malformedError.toString())

        val overlapError = ByteArrayOutputStream()
        assertEquals(
            2,
            SymbolGeneratorCli.run(
                commonArguments + arrayOf(
                    "--kotlin-output",
                    "generated",
                    "--android-output",
                    "./generated",
                ),
                standardError = PrintStream(overlapError),
            ),
        )
        assertTrue("Output directories must be distinct" in overlapError.toString())

        val nestedOutputError = ByteArrayOutputStream()
        assertEquals(
            2,
            SymbolGeneratorCli.run(
                commonArguments + arrayOf(
                    "--kotlin-output",
                    "generated",
                    "--android-output",
                    "generated/android",
                ),
                standardError = PrintStream(nestedOutputError),
            ),
        )
        assertTrue("non-overlapping" in nestedOutputError.toString())

        val mixedSourceError = ByteArrayOutputStream()
        assertEquals(
            2,
            SymbolGeneratorCli.run(
                arrayOf(
                    "--svg-directory", "svg",
                    "--font", "font.ttf",
                    "--manifest", "symbols.codepoints",
                    "--package", "com.example.icons",
                    "--set", "AppIcons",
                    "--style", "Rounded",
                    "--kotlin-output", "generated/kotlin",
                ),
                standardError = PrintStream(mixedSourceError),
            ),
        )
        assertTrue("Use either --svg-directory" in mixedSourceError.toString())
    }
}
