package io.github.hlcaptain.symbols.font

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
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
                FontVariation.Setting("wght", 500f),
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
        assertEquals(FontVariation.Setting("wght", 500f), settings.first())
    }

    @Test
    fun variationsReplaceByAxisAndKeepOtherSettings() {
        val original = SymbolFontSettings(
            FontVariation.Settings(
                FontVariation.Setting("FILL", 0f),
                FontVariation.Setting("wght", 400f),
            ),
        )

        assertSame(original, original.withVariations())

        val modified = original.withVariations(
            FontVariation.Setting("wght", 500f),
            FontVariation.Setting("wdth", 80f),
            FontVariation.Setting("wght", 600f),
        ).variationSettings.settings

        assertEquals(listOf("FILL", "wght", "wdth"), modified.map { it.axisName })
        assertEquals(listOf(0f, 600f, 80f), modified.map { it.toVariationValue(null) })
    }

    @Test
    fun regularFontAcceptsOnlyItsBakedSettings() {
        assertNull(
            symbolFontVariationSettings(
                font = RegularFont,
                fontSettings = RegularFont.fontSettings,
                variableFontsSupported = false,
                density = TestDensity,
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            symbolFontVariationSettings(
                font = RegularFont,
                fontSettings = HeavySettings,
                variableFontsSupported = true,
                density = TestDensity,
            )
        }
        assertNull(
            symbolFontVariationSettings(
                font = ReorderedRegularFont,
                fontSettings = ReorderedSettings,
                variableFontsSupported = false,
                density = TestDensity,
            ),
        )
        assertNull(
            symbolFontVariationSettings(
                font = ComposeSettingsRegularFont,
                fontSettings = FloatSettings,
                variableFontsSupported = false,
                density = TestDensity,
            ),
        )
        assertNull(
            symbolFontVariationSettings(
                font = DensityAwareRegularFont,
                fontSettings = ResolvedOpticalSizeSettings,
                variableFontsSupported = false,
                density = ScaledDensity,
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
                density = TestDensity,
            ),
        )
        assertSame(HeavySettings.variationSettings, settings)
        assertFailsWith<UnsupportedOperationException> {
            symbolFontVariationSettings(
                font = VariableFont,
                fontSettings = SymbolFontSettings.Default,
                variableFontsSupported = false,
                density = TestDensity,
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
                density = TestDensity,
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
    fun variableFontSettingsUseGeneratedDefaultsAndValidateOverrides() {
        assertEquals(
            listOf(400f),
            DescribedVariableFont.defaultFontSettings.variationSettings.settings.map {
                it.toVariationValue(null)
            },
        )
        assertEquals(
            listOf(650f),
            DescribedVariableFont.fontSettings(
                mapOf("wght" to 650f),
            ).variationSettings.settings.map { it.toVariationValue(null) },
        )
        assertFailsWith<IllegalArgumentException> {
            DescribedVariableFont.fontSettings(mapOf("NOPE" to 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            DescribedVariableFont.fontSettings(mapOf("wght" to 701f))
        }
        assertFailsWith<IllegalArgumentException> {
            DescribedVariableFont.fontSettings(mapOf("wght" to Float.NaN))
        }
    }

    @Test
    fun genericSettingsDispatchUsesTheSelectedFontCapability() {
        val selectedVariable: SymbolFont = DescribedVariableFont
        val selectedRegular: SymbolFont = RegularFont

        assertEquals(
            DescribedVariableFont.fontSettings(mapOf("wght" to 650f)),
            selectedVariable.fontSettings(mapOf("wght" to 650f)),
        )
        assertEquals(RegularFont.fontSettings, selectedRegular.fontSettings())
        assertFailsWith<IllegalArgumentException> {
            selectedRegular.fontSettings(mapOf("wght" to 400f))
        }
    }

    @Test
    fun contradictoryCapabilityMarkersAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            symbolFontVariationSettings(
                font = ContradictoryFont,
                fontSettings = SymbolFontSettings.Default,
                variableFontsSupported = true,
                density = TestDensity,
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

    private object ComposeSettingsRegularFont : SymbolFont.Regular {
        override val familyName: String = "Compose settings regular"
        override val resource: FontResource
            get() = error("The contract test must not load a resource")
        override val fontSettings: SymbolFontSettings = SymbolFontSettings(
            variationSettings = FontVariation.Settings(
                FontWeight.Normal,
                FontStyle.Normal,
            ),
        )
    }

    private object DensityAwareRegularFont : SymbolFont.Regular {
        override val familyName: String = "Density-aware regular"
        override val resource: FontResource
            get() = error("The contract test must not load a resource")
        override val fontSettings: SymbolFontSettings = SymbolFontSettings(
            variationSettings = FontVariation.Settings(
                FontVariation.opticalSizing(16.sp),
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

        val FloatSettings: SymbolFontSettings = SymbolFontSettings(
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("ital", 0f),
                FontVariation.Setting("wght", 400f),
            ),
        )

        val ResolvedOpticalSizeSettings: SymbolFontSettings = SymbolFontSettings(
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("opsz", 24f),
            ),
        )

        val TestDensity: Density = Density(1f)
        val ScaledDensity: Density = Density(1f, fontScale = 1.5f)
    }
}
