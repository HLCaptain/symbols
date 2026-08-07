package io.github.hlcaptain.symbols.sample

import androidx.compose.ui.text.font.FontVariation
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CustomIconCatalogTest {
    @Test
    fun generatedCatalogsCoverEveryCustomFont() {
        listOf(
            Triple(AcademmuniconsCatalog, 50, DemoIcon("orcid", 0xF04F)),
            Triple(FontAwesomeCatalog, 1966, DemoIcon("face_smile", 0xF118)),
            Triple(PowerlineCatalog, 8, DemoIcon("branch", 0xE0A0)),
            Triple(TablerFilledCatalog, 1057, DemoIcon("sparkles", 0x101B2)),
            Triple(TablerOutlineCatalog, 5193, DemoIcon("sparkles", 0xF6D7)),
        ).forEach { (catalog, expectedSize, expectedIcon) ->
            assertEquals(expectedSize, catalog.size)
            assertEquals(expectedIcon, catalog.single { it.name == expectedIcon.name })
        }
    }

    @Test
    fun tablerExposesTheOfficialStaticStrokeFonts() {
        assertEquals(listOf(1f, 1.5f, 2f), TablerStroke.entries.map { it.value })
        assertEquals(
            listOf(
                "Tabler Icons Outline 1px",
                "Tabler Icons Outline 1.5px",
                "Tabler Icons Outline 2px",
            ),
            TablerStroke.entries.map { it.familyName },
        )
    }

    @Test
    fun customVariableAxesMapToGenericFontSettings() {
        assertEquals(
            SymbolFontSettings(
                FontVariation.Settings(
                    FontVariation.Setting("ital", 0.75f),
                    FontVariation.Setting("wght", 650f),
                ),
            ),
            AcademmuniconsAxes(frame = 0.75f, weight = 650f).fontSettings,
        )
        assertFailsWith<IllegalArgumentException> {
            AcademmuniconsAxes(weight = 801f)
        }
        assertEquals(AcademmuniconsRegular, AcademmuniconsAxes.Default.font)
        assertEquals(
            AcademmuniconsAxes.Default.fontSettings,
            AcademmuniconsRegular.fontSettings,
        )
        assertEquals(
            AcademmuniconsVariable,
            AcademmuniconsAxes(weight = 500f).font,
        )
    }

    @Test
    fun explorerUsesOneFontSettingsValueForEveryFamily() {
        val materialAxes = MaterialSymbolAxes(fill = 1f, weight = 700)
        val academmuniconsAxes = AcademmuniconsAxes(frame = 0.75f, weight = 650f)

        assertEquals(
            materialAxes.fontSettings,
            ExplorerSettings(family = ExplorerFamily.Material, axes = materialAxes).fontSettings,
        )
        assertEquals(
            academmuniconsAxes.fontSettings,
            ExplorerSettings(
                family = ExplorerFamily.Academmunicons,
                academmuniconsAxes = academmuniconsAxes,
            ).fontSettings,
        )
        listOf(
            ExplorerSettings(family = ExplorerFamily.FontAwesome),
            ExplorerSettings(family = ExplorerFamily.Powerline),
            ExplorerSettings(
                family = ExplorerFamily.Tabler,
                tablerStyle = TablerStyle.Filled,
                tablerStroke = TablerStroke.Thin,
            ),
        ).forEach { settings ->
            assertEquals(SymbolFontSettings.Default, settings.fontSettings)
        }
    }
}
