package io.github.hlcaptain.symbols.generator

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SvgIconExtractorTest {
    @Test
    fun extractsEverySvgWithInheritedFillStrokeAndViewportScaling() =
        withTemporaryRoot { root ->
            root.resolve("home-icon.svg").writeText(
                """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
                    fill="none" stroke="currentColor" stroke-width="2"
                    stroke-linecap="round" stroke-linejoin="round"
                    class="icon icon-tabler icon-tabler-home">
                    <path fill="none" stroke="none" d="M0 0 H24 V24 H0 Z" />
                    <path d="M4 12 A8 8 0 1 0 20 12 A8 8 0 1 0 4 12" />
                </svg>
                """.trimIndent(),
            )
            root.resolve("badge.svg").writeText(
                """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 24"
                    opacity=".5">
                    <path fill="currentColor" stroke="none" opacity=".5"
                        fill-rule="evenodd" d="M0 0 L48 24 Z" />
                </svg>
                """.trimIndent(),
            )

            val icons = SvgIconExtractor().extract(SvgExtractionRequest(root))

            assertEquals(listOf("badge", "home_icon"), icons.map(SvgIcon::name))
            assertEquals(listOf("badge", "home_icon"), SvgIconExtractor.discoverNames(root))
            val badge = icons.first().paths.single()
            assertTrue(badge.fill)
            assertFalse(badge.stroke)
            assertEquals(0.25f, badge.fillAlpha)
            assertEquals(VectorFillRule.EvenOdd, badge.fillRule)
            assertEquals(
                VectorPoint(0f, 6f),
                (badge.commands[0] as VectorCommand.MoveTo).point,
            )
            assertEquals(
                VectorPoint(24f, 18f),
                (badge.commands[1] as VectorCommand.LineTo).point,
            )

            val home = icons.last().paths.single()
            assertFalse(home.fill)
            assertTrue(home.stroke)
            assertEquals(2f, home.strokeWidth)
            assertEquals(VectorStrokeCap.Round, home.strokeCap)
            assertEquals(VectorStrokeJoin.Round, home.strokeJoin)
            assertTrue(home.commands.any { it is VectorCommand.CubicTo })
        }

    @Test
    fun rejectsDoctypesForeignNamespacesAndUnsupportedStructures() {
        assertRejected(
            """
            <!DOCTYPE svg [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="M0 0 L1 1" />
            </svg>
            """.trimIndent(),
            "Unable to parse SVG",
        )
        assertRejected(
            """
            <evil:svg xmlns:evil="urn:not-svg" viewBox="0 0 24 24">
                <evil:path d="M0 0 L1 1" />
            </evil:svg>
            """.trimIndent(),
            "must have an <svg> root",
        )
        assertRejected(
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <g><path d="M0 0 L1 1" /></g>
            </svg>
            """.trimIndent(),
            "only direct <path> children are supported",
        )
    }

    @Test
    fun rejectsUnsupportedPaintInvisibleGeometryAndNormalizedNameCollisions() {
        assertRejected(
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path fill="#ff000000" d="M0 0 L1 1" />
            </svg>
            """.trimIndent(),
            "unsupported monochrome",
        )
        assertRejected(
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
                fill="none" stroke="currentColor" stroke-width="0">
                <path d="M0 0 L1 1" />
            </svg>
            """.trimIndent(),
            "has no painted paths",
        )

        withTemporaryRoot { root ->
            ValidSvgNames.forEach { name ->
                root.resolve(name).writeText(SimpleSvg)
            }
            val error = assertFailsWith<IllegalArgumentException> {
                SvgIconExtractor.discoverNames(root)
            }
            assertTrue("collisions" in error.message.orEmpty())
        }
    }

    private fun assertRejected(svg: String, expectedMessage: String) {
        withTemporaryRoot { root ->
            root.resolve("bad.svg").writeText(svg)
            val error = assertFailsWith<SymbolGenerationException> {
                SvgIconExtractor().extract(SvgExtractionRequest(root))
            }
            assertTrue(expectedMessage in error.message.orEmpty(), error.message)
        }
    }

    private fun withTemporaryRoot(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("symbol-svg-extractor-")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private companion object {
        val ValidSvgNames: List<String> = listOf("home-icon.svg", "home_icon.svg")

        const val SimpleSvg: String =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
                "<path d=\"M0 0 L1 1\" /></svg>"
    }
}
