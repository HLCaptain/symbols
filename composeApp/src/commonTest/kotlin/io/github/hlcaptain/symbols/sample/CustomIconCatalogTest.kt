package io.github.hlcaptain.symbols.sample

import androidx.compose.ui.text.font.FontVariation
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.outlined.MaterialSymbolsOutlined
import io.github.hlcaptain.symbols.material.rounded.MaterialSymbolsRounded
import io.github.hlcaptain.symbols.material.sharp.MaterialSymbolsSharp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        TablerStroke.entries.forEach { stroke ->
            assertEquals(stroke, TablerStyle.Outline.font(stroke))
            assertEquals(TablerFilled, TablerStyle.Filled.font(stroke))
        }
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
    fun fontDescriptorsDriveTheAvailableAxisControls() {
        listOf(
            MaterialSymbolsOutlined,
            MaterialSymbolsRounded,
            MaterialSymbolsSharp,
        ).forEach { font ->
            assertEquals(
                listOf("FILL", "wght", "GRAD", "opsz"),
                font.variationAxes.map { it.tag },
            )
        }
        assertEquals(listOf("ital", "wght"), AcademmuniconsVariable.variationAxes.map { it.tag })
        assertEquals("Frame", AcademmuniconsVariable.variationAxes.first().label)
        assertEquals(100f, AcademmuniconsVariable.variationAxes.last().defaultValue)

        assertEquals(
            MaterialSymbolAxes.Default.copy(weight = 550),
            MaterialSymbolAxes.Default.withAxisValue("wght", 550f),
        )
        assertEquals(
            AcademmuniconsAxes.Default.copy(frame = 0.5f),
            AcademmuniconsAxes.Default.withAxisValue("ital", 0.5f),
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
            ExplorerSettings(family = ExplorerFamily.FontAwesome) to
                FontAwesomeRegular.fontSettings,
            ExplorerSettings(family = ExplorerFamily.Powerline) to
                PowerlineRegular.fontSettings,
            ExplorerSettings(
                family = ExplorerFamily.Tabler,
                tablerStyle = TablerStyle.Filled,
                tablerStroke = TablerStroke.Thin,
            ) to TablerFilled.fontSettings,
        ).forEach { (settings, expected) ->
            assertEquals(expected, settings.fontSettings)
        }
    }

    @Test
    fun sealedFamiliesPreserveTabOrderAndLabels() {
        assertEquals(
            listOf(
                ExplorerFamily.Material,
                ExplorerFamily.FontAwesome,
                ExplorerFamily.Tabler,
                ExplorerFamily.Powerline,
                ExplorerFamily.Academmunicons,
            ),
            ExplorerFamily.entries,
        )
        assertEquals(
            listOf("Material", "Awesome", "Tabler", "Powerline", "Academic"),
            ExplorerFamily.entries.map { it.tabLabel },
        )
    }

    @Test
    fun materialUsesThemedVectorsOnlyForTheDefaultAxisSnapshot() {
        assertTrue(usesThemedImageVectors(MaterialSymbolAxes.Default))
        listOf(
            MaterialSymbolAxes.Default.copy(fill = 1f),
            MaterialSymbolAxes.Default.copy(weight = 500),
            MaterialSymbolAxes.Default.copy(grade = 100f),
            MaterialSymbolAxes.Default.copy(opticalSize = 48f),
        ).forEach { axes ->
            assertFalse(usesThemedImageVectors(axes))
        }
    }
}
