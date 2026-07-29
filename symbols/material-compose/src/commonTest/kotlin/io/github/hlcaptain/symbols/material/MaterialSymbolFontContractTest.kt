package io.github.hlcaptain.symbols.material

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.jetbrains.compose.resources.FontResource

class MaterialSymbolFontContractTest {
    @Test
    fun regularFontNeverProducesVariationSettings() {
        assertNull(
            materialSymbolVariationSettings(
                font = RegularFont,
                axes = MaterialSymbolAxes.Default,
                variableFontsSupported = false,
            ),
        )
    }

    @Test
    fun regularFontRejectsAxesThatAreNotBakedIntoTheResource() {
        assertFailsWith<IllegalArgumentException> {
            materialSymbolVariationSettings(
                font = RegularFont,
                axes = MaterialSymbolAxes(weight = 500),
                variableFontsSupported = true,
            )
        }

        assertNull(
            materialSymbolVariationSettings(
                font = HeavyRegularFont,
                axes = HeavyRegularFont.axes,
                variableFontsSupported = true,
            ),
        )
    }

    @Test
    fun explicitAndLegacyVariableFontsProduceVariationSettings() {
        assertNotNull(
            materialSymbolVariationSettings(
                font = VariableFont,
                axes = MaterialSymbolAxes.Default,
                variableFontsSupported = true,
            ),
        )
        assertNotNull(
            materialSymbolVariationSettings(
                font = LegacyVariableFont,
                axes = MaterialSymbolAxes.Default,
                variableFontsSupported = true,
            ),
        )
    }

    @Test
    fun variableFontsFailClearlyWhenThePlatformCannotApplyAxes() {
        assertFailsWith<UnsupportedOperationException> {
            materialSymbolVariationSettings(
                font = VariableFont,
                axes = MaterialSymbolAxes.Default,
                variableFontsSupported = false,
            )
        }
    }

    @Test
    fun contradictoryCapabilityMarkersAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            materialSymbolVariationSettings(
                font = ContradictoryFont,
                axes = MaterialSymbolAxes.Default,
                variableFontsSupported = true,
            )
        }
    }

    private object RegularFont : MaterialSymbolRegularFont {
        override val familyName: String = "Regular"
        override val resource: FontResource
            get() = error("The contract test must not load a resource")
    }

    private object HeavyRegularFont : MaterialSymbolRegularFont {
        override val familyName: String = "Heavy regular"
        override val resource: FontResource
            get() = error("The contract test must not load a resource")
        override val axes: MaterialSymbolAxes = MaterialSymbolAxes(weight = 700)
    }

    private object VariableFont : MaterialSymbolVariableFont {
        override val familyName: String = "Variable"
        override val resource: FontResource
            get() = error("The contract test must not load a resource")
    }

    private object LegacyVariableFont : MaterialSymbolFont {
        override val familyName: String = "Legacy variable"
        override val resource: FontResource
            get() = error("The contract test must not load a resource")
    }

    private object ContradictoryFont :
        MaterialSymbolRegularFont,
        MaterialSymbolVariableFont {
        override val familyName: String = "Contradictory"
        override val resource: FontResource
            get() = error("The contract test must not load a resource")
    }
}
