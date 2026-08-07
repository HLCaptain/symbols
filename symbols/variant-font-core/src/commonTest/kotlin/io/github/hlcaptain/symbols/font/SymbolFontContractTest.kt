package io.github.hlcaptain.symbols.font

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.jetbrains.compose.resources.FontResource

class SymbolFontContractTest {
    @Test
    fun arbitraryVariationSettingsRemainValueSemantic() {
        val settings = SymbolFontSettings(
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("FILL", 1f),
                FontVariation.Setting("wdth", 110f),
                FontVariation.weight(500),
            ),
        )

        assertEquals(settings, settings.copy())
        assertEquals(SymbolFontSettings(), SymbolFontSettings.Default)
    }

    @Test
    fun composeWeightAndStyleBecomeVariationSettings() {
        val settings = SymbolFontSettings(
            weight = FontWeight.Medium,
            style = FontStyle.Italic,
        ).variationSettings.settings

        assertEquals(listOf("wght", "ital"), settings.map { it.axisName })
        assertEquals(listOf(500f, 1f), settings.map { it.toVariationValue(null) })
    }

    @Test
    fun regularFontAcceptsOnlyItsBakedSettings() {
        assertNull(
            symbolFontVariationSettings(
                font = RegularFont,
                fontSettings = RegularFont.fontSettings,
                variableFontsSupported = false,
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            symbolFontVariationSettings(
                font = RegularFont,
                fontSettings = HeavySettings,
                variableFontsSupported = true,
            )
        }
        assertNull(
            symbolFontVariationSettings(
                font = ReorderedRegularFont,
                fontSettings = ReorderedSettings,
                variableFontsSupported = false,
            ),
        )
    }

    @Test
    fun variableFontsUseVariationSettingsAndRejectUnsupportedPlatforms() {
        val settings = assertNotNull(
            symbolFontVariationSettings(
                font = VariableFont,
                fontSettings = HeavySettings,
                variableFontsSupported = true,
            ),
        )
        assertSame(HeavySettings.variationSettings, settings)
        assertFailsWith<UnsupportedOperationException> {
            symbolFontVariationSettings(
                font = VariableFont,
                fontSettings = SymbolFontSettings.Default,
                variableFontsSupported = false,
            )
        }
    }

    @Test
    fun variableFontsKeepArbitrarySettingsUnchanged() {
        val arbitrarySettings = SymbolFontSettings(
            FontVariation.Settings(
                FontVariation.Setting("wght", 350f),
                FontVariation.Setting("wdth", 80f),
            ),
        )

        val settings = assertNotNull(
            symbolFontVariationSettings(
                font = VariableFont,
                fontSettings = arbitrarySettings,
                variableFontsSupported = true,
            ),
        )

        assertSame(arbitrarySettings.variationSettings, settings)
    }

    @Test
    fun variableFontsCanDescribeTheirAxesWithoutChangingCapabilityMarkers() {
        assertEquals(emptyList(), VariableFont.variationAxes)
        assertEquals("Weight", DescribedVariableFont.variationAxes.single().label)
        assertEquals(100f, DescribedVariableFont.variationAxes.single().minValue)
        assertFailsWith<IllegalArgumentException> {
            SymbolFontAxis("weight", 100f, 400f, 700f)
        }
        assertFailsWith<IllegalArgumentException> {
            SymbolFontAxis("wght", 500f, 400f, 700f)
        }
        assertFailsWith<IllegalArgumentException> {
            SymbolFontAxis("wght", 100f, Float.NaN, 700f)
        }
        assertFailsWith<IllegalArgumentException> {
            SymbolFontAxis("wght", 100f, 400f, 700f, " ")
        }
    }

    @Test
    fun contradictoryCapabilityMarkersAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            symbolFontVariationSettings(
                font = ContradictoryFont,
                fontSettings = SymbolFontSettings.Default,
                variableFontsSupported = true,
            )
        }
    }

    @Test
    fun scalarEncodingSupportsSupplementaryCodePointsAndRejectsSurrogates() {
        assertEquals("\uDB80\uDC01", symbolFontText(0xF0001))
        assertFailsWith<IllegalArgumentException> { symbolFontText(0xD800) }
        assertFailsWith<IllegalArgumentException> { symbolFontText(0x110000) }
    }

    private object RegularFont : SymbolFont.Regular {
        override val familyName: String = "Regular"
        override val resource: FontResource
            get() = error("The contract test must not load a resource")
    }

    private object VariableFont : SymbolFont.Variable {
        override val familyName: String = "Variable"
        override val resource: FontResource
            get() = error("The contract test must not load a resource")
    }

    private object DescribedVariableFont : SymbolFont.Variable {
        override val familyName: String = "Described variable"
        override val resource: FontResource
            get() = error("The contract test must not load a resource")
        override val variationAxes: List<SymbolFontAxis> = listOf(
            SymbolFontAxis("wght", 100f, 400f, 700f, "Weight"),
        )
    }

    private object ReorderedRegularFont : SymbolFont.Regular {
        override val familyName: String = "Reordered regular"
        override val resource: FontResource
            get() = error("The contract test must not load a resource")
        override val fontSettings: SymbolFontSettings = SymbolFontSettings(
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("FILL", 1f),
                FontVariation.Setting("wdth", 80f),
            ),
        )
    }

    private object ContradictoryFont : SymbolFont.Regular, SymbolFont.Variable {
        override val familyName: String = "Contradictory"
        override val resource: FontResource
            get() = error("The contract test must not load a resource")
    }

    private companion object {
        val HeavySettings: SymbolFontSettings = SymbolFontSettings(
            weight = FontWeight.Bold,
        )

        val ReorderedSettings: SymbolFontSettings = SymbolFontSettings(
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("wdth", 80f),
                FontVariation.Setting("FILL", 1f),
            ),
        )
    }
}
