package io.github.hlcaptain.symbols.generator

/**
 * Stable library entry point that keeps extraction separate from pure rendering.
 *
 * Gradle tasks should declare the request fields as inputs, call [extract], then
 * render one or both output formats into task-owned output directories.
 */
class SymbolGenerator(
    private val outlineExtractor: FontOutlineExtractor = SkikoFontOutlineExtractor(),
) {
    fun extract(
        catalog: SymbolCatalog,
        request: FontExtractionRequest,
    ): ExtractedFont {
        val requested = request.copy(codePoints = catalog.uniqueCodePoints)
        return outlineExtractor.extract(requested)
    }

    /** Extracts every supported SVG file in [request]'s source directory. */
    fun extract(request: SvgExtractionRequest): List<SvgIcon> =
        SvgIconExtractor().extract(request)

    fun imageVectors(
        iconSet: GeneratedIconSet,
        options: VectorRenderOptions = VectorRenderOptions(),
        includeNamespace: Boolean = true,
    ): RenderedFiles =
        KotlinImageVectorRenderer(options, includeNamespace).render(iconSet)

    fun imageVectors(
        iconSet: GeneratedSvgIconSet,
        options: VectorRenderOptions = VectorRenderOptions(),
        includeNamespace: Boolean = true,
    ): RenderedFiles =
        KotlinImageVectorRenderer(options, includeNamespace).render(iconSet)

    fun androidVectors(
        iconSet: GeneratedIconSet,
        resourcePrefix: String = SymbolNames.androidResourcePrefix(iconSet.name),
        options: VectorRenderOptions = VectorRenderOptions(),
    ): AndroidVectorOutput =
        AndroidVectorXmlRenderer(options, resourcePrefix).render(iconSet)

    fun androidVectors(
        iconSet: GeneratedSvgIconSet,
        resourcePrefix: String = SymbolNames.androidResourcePrefix(iconSet.name),
        options: VectorRenderOptions = VectorRenderOptions(),
    ): SvgAndroidVectorOutput =
        AndroidVectorXmlRenderer(options, resourcePrefix).render(iconSet)
}
