package io.github.hlcaptain.symbols.generator

/**
 * Stable library entry point that keeps extraction separate from pure rendering.
 *
 * Gradle tasks should declare the request fields as inputs, call [extract], then
 * render one or both output formats into task-owned output directories.
 *
 * @param outlineExtractor font reader used by the font-based [extract]
 * overload. Supply a custom implementation for tests or another font engine.
 */
class SymbolGenerator(
    private val outlineExtractor: FontOutlineExtractor = SkikoFontOutlineExtractor(),
) {
    /**
     * Extracts every unique code point required by [catalog] from a font.
     *
     * The code points already present in [request] are replaced with the
     * catalog's code points, while its font, axis, transform, and tolerance
     * settings are kept. The configured [FontOutlineExtractor] may read the font
     * file and use native code. This function does not write generated files or
     * cache the result in this generator.
     *
     * @param catalog names and code points that the generated API must cover.
     * @param request font and extraction settings; its `codePoints` value is
     * ignored in favor of [catalog].
     * @return extracted font metadata and an outline for each catalog code point.
     * @throws SymbolGenerationException if the font or requested glyphs cannot
     * be extracted.
     */
    fun extract(
        catalog: SymbolCatalog,
        request: FontExtractionRequest,
    ): ExtractedFont {
        val requested = request.copy(codePoints = catalog.uniqueCodePoints)
        return outlineExtractor.extract(requested)
    }

    /**
     * Extracts every supported SVG file in [request]'s source directory.
     *
     * The directory and SVG contents are read immediately and converted into
     * an in-memory model. Subdirectories are ignored. No generated files are
     * written and this generator does not cache the result.
     *
     * @param request source directory, output viewport, and curve tolerance.
     * @return validated icons in a stable icon-name order.
     * @throws SymbolGenerationException if an SVG cannot be parsed or converted.
     * @throws IllegalArgumentException if the directory or filenames are invalid.
     * @throws java.io.IOException if the source directory cannot be listed.
     */
    fun extract(request: SvgExtractionRequest): List<SvgIcon> =
        SvgIconExtractor().extract(request)

    /**
     * Renders a font-derived icon set as Kotlin `ImageVector` source text.
     *
     * The returned files exist only in memory. With [includeNamespace] enabled,
     * they include the `Symbols` entry point and style objects; disable it only
     * when another generation task provides that shared namespace. Generated
     * vector getters cache their built vectors, but this generator stores no
     * rendering cache itself.
     *
     * @param iconSet validated font-derived icon styles to render.
     * @param options dimensions, precision, chunking, color, and mirroring used
     * in the generated source.
     * @param includeNamespace whether to include shared root and style objects.
     * @return relative Kotlin source paths and their complete contents.
     */
    fun imageVectors(
        iconSet: GeneratedIconSet,
        options: VectorRenderOptions = VectorRenderOptions(),
        includeNamespace: Boolean = true,
    ): RenderedFiles =
        KotlinImageVectorRenderer(options, includeNamespace).render(iconSet)

    /**
     * Renders an SVG-derived icon set as Kotlin `ImageVector` source text.
     *
     * The returned files exist only in memory. With [includeNamespace] enabled,
     * they include the `Symbols` entry point and style objects; disable it only
     * when another generation task provides that shared namespace. Generated
     * vector getters cache their built vectors, but this generator stores no
     * rendering cache itself.
     *
     * @param iconSet validated SVG-derived icon styles to render.
     * @param options dimensions, precision, chunking, color, and mirroring used
     * in the generated source.
     * @param includeNamespace whether to include shared root and style objects.
     * @return relative Kotlin source paths and their complete contents.
     */
    fun imageVectors(
        iconSet: GeneratedSvgIconSet,
        options: VectorRenderOptions = VectorRenderOptions(),
        includeNamespace: Boolean = true,
    ): RenderedFiles =
        KotlinImageVectorRenderer(options, includeNamespace).render(iconSet)

    /**
     * Renders a font-derived icon set as native Android vector drawable XML.
     *
     * Within each style, one drawable is produced per unique code point, so
     * aliases in that style share the same resource. The result remains in
     * memory until a caller writes its
     * [AndroidVectorOutput.files]. This function does not inspect or modify an
     * Android project.
     *
     * @param iconSet validated font-derived icon styles to render.
     * @param resourcePrefix prefix applied to every generated drawable name.
     * @param options dimensions, precision, color, and mirroring used in XML.
     * @return drawable files and a lookup from style and code point to resource
     * name.
     * @throws IllegalArgumentException if [resourcePrefix] is not a lowercase
     * Android resource prefix.
     * @throws SymbolGenerationException if generated resource names collide.
     */
    fun androidVectors(
        iconSet: GeneratedIconSet,
        resourcePrefix: String = SymbolNames.androidResourcePrefix(iconSet.name),
        options: VectorRenderOptions = VectorRenderOptions(),
    ): AndroidVectorOutput =
        AndroidVectorXmlRenderer(options, resourcePrefix).render(iconSet)

    /**
     * Renders an SVG-derived icon set as native Android vector drawable XML.
     *
     * Within each style, one drawable is produced per SVG icon. The result
     * remains in memory until a caller writes its [SvgAndroidVectorOutput.files].
     * This function does not inspect or modify an Android project.
     *
     * @param iconSet validated SVG-derived icon styles to render.
     * @param resourcePrefix prefix applied to every generated drawable name.
     * @param options dimensions, precision, color, and mirroring used in XML.
     * @return drawable files and a lookup from style and icon name to
     * resource name.
     * @throws IllegalArgumentException if [resourcePrefix] is not a lowercase
     * Android resource prefix.
     * @throws SymbolGenerationException if generated resource names collide.
     */
    fun androidVectors(
        iconSet: GeneratedSvgIconSet,
        resourcePrefix: String = SymbolNames.androidResourcePrefix(iconSet.name),
        options: VectorRenderOptions = VectorRenderOptions(),
    ): SvgAndroidVectorOutput =
        AndroidVectorXmlRenderer(options, resourcePrefix).render(iconSet)
}
