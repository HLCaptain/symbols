package io.github.hlcaptain.symbols.generator

/**
 * Stable library entry point that keeps extraction separate from pure rendering.
 *
 * Gradle tasks should declare the request fields as inputs, call [extract], then
 * render one or both output formats into task-owned output directories.
 */
public class SymbolGenerator(
    private val outlineExtractor: FontOutlineExtractor = SkikoFontOutlineExtractor(),
) {
    public fun extract(
        catalog: SymbolCatalog,
        request: FontExtractionRequest,
    ): ExtractedFont {
        val requested = request.copy(codePoints = catalog.uniqueCodePoints)
        return outlineExtractor.extract(requested)
    }

    public fun imageVectors(
        iconSet: GeneratedIconSet,
        options: VectorRenderOptions = VectorRenderOptions(),
        includeNamespace: Boolean = true,
    ): RenderedFiles =
        KotlinImageVectorRenderer(options, includeNamespace).render(iconSet)

    public fun androidVectors(
        iconSet: GeneratedIconSet,
        resourcePrefix: String = SymbolNames.androidResourcePrefix(iconSet.name),
        options: VectorRenderOptions = VectorRenderOptions(),
    ): AndroidVectorOutput =
        AndroidVectorXmlRenderer(options, resourcePrefix).render(iconSet)
}
