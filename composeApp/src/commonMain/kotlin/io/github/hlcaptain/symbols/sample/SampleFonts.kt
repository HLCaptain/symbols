@file:OptIn(InternalResourceApi::class)

package io.github.hlcaptain.symbols.sample

import androidx.compose.ui.text.font.FontVariation
import io.github.hlcaptain.symbols.font.SymbolFont
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.font.SymbolRegularFont
import io.github.hlcaptain.symbols.font.SymbolVariableFont
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ResourceItem

private const val SampleResourceRoot =
    "composeResources/io.github.hlcaptain.composeapp.generated.resources/font/"

// Source-owned descriptors keep commonMain Fast Preview independent of generated accessors.
private fun sampleFontResource(fileName: String): FontResource = FontResource(
    "font:${fileName.substringBeforeLast('.')}",
    setOf(ResourceItem(emptySet(), SampleResourceRoot + fileName, -1, -1)),
)

internal object AcademmuniconsVariable : SymbolVariableFont {
    override val familyName: String = "Academmunicons"
    override val resource: FontResource = sampleFontResource("academmunicons_variable.ttf")
}

internal data class AcademmuniconsAxes(
    val frame: Float = DefaultFrame,
    val weight: Float = DefaultWeight,
) {
    init {
        require(frame.isFinite() && frame in MinFrame..MaxFrame)
        require(weight.isFinite() && weight in MinWeight..MaxWeight)
    }

    val fontSettings: SymbolFontSettings =
        SymbolFontSettings(
            FontVariation.Settings(
                FontVariation.Setting("ital", frame),
                FontVariation.Setting("wght", weight),
            ),
        )

    val font: SymbolFont
        get() = if (this == Default) AcademmuniconsRegular else AcademmuniconsVariable

    companion object {
        val Default: AcademmuniconsAxes = AcademmuniconsAxes()

        const val MinFrame: Float = 0f
        const val DefaultFrame: Float = 0f
        const val MaxFrame: Float = 1f

        const val MinWeight: Float = 100f
        const val DefaultWeight: Float = 400f
        const val MaxWeight: Float = 800f
    }
}

internal object AcademmuniconsRegular : SymbolRegularFont {
    override val familyName: String = "Symbols Academic Icons"
    override val resource: FontResource = sampleFontResource("academmunicons_regular.ttf")
    override val fontSettings: SymbolFontSettings = AcademmuniconsAxes.Default.fontSettings
}

internal object FontAwesomeRegular : SymbolRegularFont {
    override val familyName: String = "Font Awesome Free Solid"
    override val resource: FontResource = sampleFontResource("font_awesome_free_solid.ttf")
}

internal object PowerlineRegular : SymbolRegularFont {
    override val familyName: String = "Powerline Symbols"
    override val resource: FontResource = sampleFontResource("powerline_symbols.otf")
}

internal object TablerFilled : SymbolRegularFont {
    override val familyName: String = "Tabler Icons Filled"
    override val resource: FontResource = sampleFontResource("tabler_icons_filled.ttf")
}

internal enum class TablerStyle(val label: String) {
    Outline("Outline"),
    Filled("Filled"),
}

internal enum class TablerStroke(
    val value: Float,
    val label: String,
    override val resource: FontResource,
) : SymbolRegularFont {
    Thin(1f, "1 px", sampleFontResource("tabler_icons_outline_1.ttf")),
    Light(1.5f, "1.5 px", sampleFontResource("tabler_icons_outline_1_5.ttf")),
    Default(2f, "2 px", sampleFontResource("tabler_icons_outline_2.ttf")),
    ;

    override val familyName: String
        get() = "Tabler Icons Outline ${label.replace(" ", "")}"
}

internal data class DemoIcon(
    val name: String,
    val codePoint: Int,
)
