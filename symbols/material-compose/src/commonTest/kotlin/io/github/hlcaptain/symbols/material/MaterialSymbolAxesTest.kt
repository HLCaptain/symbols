package io.github.hlcaptain.symbols.material

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MaterialSymbolAxesTest {
    @Test
    fun defaultsMatchFontDefaults() {
        assertEquals(
            MaterialSymbolAxes(fill = 0f, weight = 400, grade = 0f, opticalSize = 24f),
            MaterialSymbolAxes(),
        )
        assertEquals(MaterialSymbolAxes(), MaterialSymbolAxes.Default)
        assertSame(MaterialSymbolAxes.Default, MaterialSymbolAxes.Default)
    }

    @Test
    fun acceptsEveryInclusiveBoundary() {
        MaterialSymbolAxes(fill = 0f, weight = 100, grade = -50f, opticalSize = 20f)
        MaterialSymbolAxes(fill = 1f, weight = 700, grade = 200f, opticalSize = 48f)
    }

    @Test
    fun mapsToGenericFontSettings() {
        val axes = MaterialSymbolAxes(fill = 1f, weight = 500, grade = 100f)
        val settings = axes.fontSettings.variationSettings.settings

        assertEquals(listOf("FILL", "GRAD", "opsz", "wght"), settings.map { it.axisName })
        assertEquals(
            500f,
            settings.single { it.axisName == "wght" }.toVariationValue(null),
        )
    }

    @Test
    fun rejectsValuesOutsideEveryAxis() {
        assertFailsWith<IllegalArgumentException> { MaterialSymbolAxes(fill = -0.01f) }
        assertFailsWith<IllegalArgumentException> { MaterialSymbolAxes(fill = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { MaterialSymbolAxes(weight = 99) }
        assertFailsWith<IllegalArgumentException> { MaterialSymbolAxes(weight = 701) }
        assertFailsWith<IllegalArgumentException> { MaterialSymbolAxes(grade = -50.01f) }
        assertFailsWith<IllegalArgumentException> { MaterialSymbolAxes(grade = Float.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { MaterialSymbolAxes(opticalSize = 19.99f) }
        assertFailsWith<IllegalArgumentException> { MaterialSymbolAxes(opticalSize = 48.01f) }
    }
}
