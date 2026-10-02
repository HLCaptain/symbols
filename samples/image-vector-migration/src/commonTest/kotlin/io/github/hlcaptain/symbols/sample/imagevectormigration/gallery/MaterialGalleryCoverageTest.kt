package io.github.hlcaptain.symbols.sample.imagevectormigration.gallery

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MaterialGalleryCoverageTest {
    @Test
    fun pagesVisitEachUniqueCodepointExactlyOncePerKind() {
        assertEquals(3_802, gallerySymbols.size)
        assertEquals(GalleryCodePointCount, gallerySymbols.map { it.codePoint }.distinct().size)
        val pages = MaterialGalleryPageProvider().values.toList()
        assertEquals(120, pages.size)
        for (kind in MaterialGalleryKind.entries) {
            val symbols = pages.filter { it.kind == kind }.flatMap { it.symbols }
            assertEquals(gallerySymbols, symbols)
        }
        assertEquals(15_208, pages.sumOf { it.symbols.size })
    }

    @Test
    fun svgGroupsPreservePathsWhileRemovingPageTranslation() {
        val path = "<path fill=\"#000000\" fill-rule=\"nonzero\" d=\"M-0.3 0H24.35Z\"/>"
        val documents = galleryGlyphSvgDocuments("<svg>\n<g transform=\"translate(8 522)\">$path</g>\n</svg>")
        assertEquals(listOf("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\">$path</svg>"), documents)
        assertFailsWith<IllegalArgumentException> {
            galleryGlyphSvgDocuments("<g transform=\"translate(8 522)\"><rect/></g>")
        }
    }
}

internal fun assertGalleryPixelsEqual(page: MaterialGalleryPage, candidate: ImageBitmap, reference: ImageBitmap) {
    assertEquals(candidate.width, reference.width, "$page width")
    assertEquals(candidate.height, reference.height, "$page height")
    val actual = candidate.toPixelMap()
    val expected = reference.toPixelMap()
    val symbols = page.symbols
    var differences = 0
    val codepoints = linkedSetOf<String>()
    val samples = mutableListOf<String>()
    for (y in 0 until actual.height) {
        for (x in 0 until actual.width) {
            if (actual[x, y].toArgb() != expected[x, y].toArgb()) {
                differences++
                if (samples.size < 16) {
                    samples += "($x,$y): ${actual[x, y].toArgb().toUInt().toString(16)} vs ${expected[x, y].toArgb().toUInt().toString(16)}"
                }
                val column = x * GalleryWidth / actual.width / GalleryCellSize
                val row = y * GalleryHeight / actual.height / GalleryCellSize
                symbols.getOrNull(row * GalleryColumns + column)?.let {
                    codepoints += "U+${it.codePoint.toString(16).uppercase()} (${it.name})"
                }
            }
        }
    }
    assertEquals(0, differences, "$page: differing pixels; affected glyphs: ${codepoints.take(12)}; pixels: $samples")
}
