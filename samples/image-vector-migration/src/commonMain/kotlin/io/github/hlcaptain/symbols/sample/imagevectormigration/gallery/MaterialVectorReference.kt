package io.github.hlcaptain.symbols.sample.imagevectormigration.gallery

/** Reads only our generator's one-path-per-line page format, not arbitrary SVG documents. */
internal fun galleryGlyphSvgDocuments(sheet: String): List<String> = sheet.lineSequence()
    .map(String::trim)
    .filter { it.startsWith("<g ") }
    .map { group ->
        require(group.endsWith("</g>")) { "Malformed generated reference group" }
        val path = group.substringAfter('>').removeSuffix("</g>")
        require(path.startsWith("<path ") && path.endsWith("/>") && path.count { it == '<' } == 1) {
            "Reference glyph must contain one path"
        }
        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\">$path</svg>"
    }.toList()
