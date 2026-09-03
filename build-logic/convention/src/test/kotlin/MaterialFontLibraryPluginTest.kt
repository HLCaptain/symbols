import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MaterialFontLibraryPluginTest {
    @Test
    fun recognizesOnlyTheSixMaterialFontProjects() {
        val expected = mapOf(
            "material-outlined" to MaterialFontConvention("outlined", isStatic = false),
            "material-outlined-static" to MaterialFontConvention("outlined", isStatic = true),
            "material-rounded" to MaterialFontConvention("rounded", isStatic = false),
            "material-rounded-static" to MaterialFontConvention("rounded", isStatic = true),
            "material-sharp" to MaterialFontConvention("sharp", isStatic = false),
            "material-sharp-static" to MaterialFontConvention("sharp", isStatic = true),
        )

        assertEquals(expected, expected.keys.associateWith(::materialFontConvention))
        assertFailsWith<IllegalStateException> {
            materialFontConvention("material-unknown")
        }
    }

    @Test
    fun derivesVariableAndStaticApiConfiguration() {
        val variable = materialFontConvention("material-rounded")
        assertEquals("rounded", variable.resourceDirectory)
        assertEquals("io.github.hlcaptain.symbols.material.rounded", variable.namespace)
        assertEquals("${variable.namespace}.resources", variable.resourcePackage)
        assertEquals("material_symbols_rounded_variable", variable.resourceAccessor)
        assertEquals("io.github.hlcaptain.symbols.material.Icons.Rounded", variable.receiver)
        assertEquals("font", variable.propertyName)
        assertEquals(1, variable.componentIndex)
        assertEquals(emptyMap(), variable.fixedAxisValues)

        val static = materialFontConvention("material-rounded-static")
        assertEquals("rounded-static", static.resourceDirectory)
        assertEquals("io.github.hlcaptain.symbols.material.rounded.staticfont", static.namespace)
        assertEquals("${static.namespace}.resources", static.resourcePackage)
        assertEquals("material_symbols_rounded_regular", static.resourceAccessor)
        assertEquals("io.github.hlcaptain.symbols.material.Icons.Rounded", static.receiver)
        assertEquals("staticFont", static.propertyName)
        assertEquals(2, static.componentIndex)
        assertEquals(
            mapOf("FILL" to 0f, "GRAD" to 0f, "opsz" to 24f, "wght" to 400f),
            static.fixedAxisValues,
        )
    }
}
