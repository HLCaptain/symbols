package io.github.hlcaptain.symbols.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.font.SymbolFont
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.font.SymbolsRuntime
import io.github.hlcaptain.symbols.font.SymbolsTheme
import io.github.hlcaptain.symbols.material.MaterialSymbol
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.MaterialSymbolStyle
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.MaterialSymbolsTheme
import io.github.hlcaptain.symbols.material.Search
import io.github.hlcaptain.symbols.material.outlined.MaterialSymbolsOutlined
import io.github.hlcaptain.symbols.material.rounded.MaterialSymbolsRounded
import io.github.hlcaptain.symbols.material.rounded.staticfont.MaterialSymbolsRoundedStatic
import io.github.hlcaptain.symbols.material.sharp.MaterialSymbolsSharp
import io.github.hlcaptain.symbols.material.vectors.themed.asThemedImageVector
import io.github.hlcaptain.symbols.sample.generated.AppIcons
import io.github.hlcaptain.symbols.sample.generated.fontawesome.FontAwesomeIcons
import io.github.hlcaptain.symbols.sample.generated.fontawesome.solid.CircleCheck
import io.github.hlcaptain.symbols.sample.generated.regular.Check
import io.github.hlcaptain.symbols.sample.generated.rounded.Favorite
import io.github.hlcaptain.symbols.sample.generated.tabler.TablerIcons
import io.github.hlcaptain.symbols.sample.generated.tabler.filled.Alien
import io.github.hlcaptain.symbols.sample.generated.tabler.outline.Sparkles
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(name = "Material themed vectors")
@Composable
fun App() {
    App(onOpenLegacyViews = null)
}

@Preview(name = "Custom variable font")
@Composable
private fun AppPreview() {
    App(
        onOpenLegacyViews = null,
        initialSettings = ExplorerSettings(
            family = ExplorerFamily.Academmunicons,
            academmuniconsAxes = AcademmuniconsAxes(weight = 600f),
        ),
    )
}

@Composable
internal fun App(
    onOpenLegacyViews: (() -> Unit)?,
    initialSettings: ExplorerSettings = ExplorerSettings(),
) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!SymbolsRuntime.variableFontsSupported) {
                Api21SymbolShowcase(onOpenLegacyViews)
            } else {
                SymbolExplorerRoot(onOpenLegacyViews, initialSettings)
            }
        }
    }
}

@Composable
private fun SymbolExplorerRoot(
    onOpenLegacyViews: (() -> Unit)?,
    initialSettings: ExplorerSettings,
) {
    var settings by remember(initialSettings) {
        mutableStateOf(initialSettings)
    }
    val onFamilyChange: (ExplorerFamily) -> Unit = { family ->
        settings = settings.copy(family = family, query = "")
    }
    val onQueryChange: (String) -> Unit = { query ->
        settings = settings.copy(query = query)
    }

    SymbolsTheme(fontSettings = settings.fontSettings) {
        ExplorerContent(
            family = settings.family,
            settings = settings,
            onSettingsChange = { settings = it },
            onFamilyChange = onFamilyChange,
            onQueryChange = onQueryChange,
            onOpenLegacyViews = onOpenLegacyViews,
        )
    }
}

@Composable
private fun ExplorerContent(
    family: ExplorerFamily,
    settings: ExplorerSettings,
    onSettingsChange: (ExplorerSettings) -> Unit,
    onFamilyChange: (ExplorerFamily) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenLegacyViews: (() -> Unit)?,
) {
    when (family) {
        ExplorerFamily.Material -> MaterialSymbolsTheme(
            style = settings.materialStyleOption.style,
            axes = settings.axes,
        ) {
            VariableFontCatalogExplorer(
                selectedFamily = family,
                query = settings.query,
                label = family.label,
                searchHint = "Try account_circle",
                catalog = MaterialSymbols.all,
                itemName = MaterialSymbol::name,
                itemCodePoint = MaterialSymbol::codePoint,
                font = settings.materialStyleOption.font,
                fontSettings = settings.axes.fontSettings,
                defaultFontSettings = MaterialSymbolAxes.Default.fontSettings,
                onAxisValueChange = { tag, value ->
                    onSettingsChange(
                        settings.copy(axes = settings.axes.withAxisValue(tag, value)),
                    )
                },
                onAxesReset = {
                    onSettingsChange(settings.copy(axes = MaterialSymbolAxes.Default))
                },
                onFamilyChange = onFamilyChange,
                onQueryChange = onQueryChange,
                onOpenLegacyViews = onOpenLegacyViews,
                staticImageVector = if (usesThemedImageVectors(settings.axes)) {
                    { symbol -> symbol.asThemedImageVector() }
                } else {
                    null
                },
                options = {
                    MaterialOptions(
                        style = settings.materialStyleOption,
                        onStyleChange = {
                            onSettingsChange(settings.copy(materialStyleOption = it))
                        },
                    )
                },
            )
        }

        ExplorerFamily.Tabler -> {
            val font = settings.tablerStyle.font(settings.tablerStroke)
            val catalog = when (settings.tablerStyle) {
                TablerStyle.Outline -> TablerOutlineCatalog
                TablerStyle.Filled -> TablerFilledCatalog
            }
            VariableFontCatalogExplorer(
                selectedFamily = family,
                query = settings.query,
                label = "Tabler ${settings.tablerStyle.label}",
                searchHint = "Try sparkles",
                catalog = catalog,
                itemName = DemoIcon::name,
                itemCodePoint = DemoIcon::codePoint,
                font = font,
                fontSettings = font.fontSettings,
                onFamilyChange = onFamilyChange,
                onQueryChange = onQueryChange,
                onOpenLegacyViews = onOpenLegacyViews,
                options = {
                    TablerOptions(
                        style = settings.tablerStyle,
                        stroke = settings.tablerStroke,
                        onStyleChange = {
                            onSettingsChange(settings.copy(tablerStyle = it))
                        },
                        onStrokeChange = {
                            onSettingsChange(settings.copy(tablerStroke = it))
                        },
                    )
                },
            )
        }

        ExplorerFamily.Academmunicons -> VariableFontCatalogExplorer(
            selectedFamily = family,
            query = settings.query,
            label = family.label,
            searchHint = "Try orcid",
            catalog = AcademmuniconsCatalog,
            itemName = DemoIcon::name,
            itemCodePoint = DemoIcon::codePoint,
            font = AcademmuniconsVariable,
            renderFont = settings.academmuniconsAxes.font,
            fontSettings = settings.academmuniconsAxes.fontSettings,
            defaultFontSettings = AcademmuniconsAxes.Default.fontSettings,
            onAxisValueChange = { tag, value ->
                onSettingsChange(
                    settings.copy(
                        academmuniconsAxes = settings.academmuniconsAxes.withAxisValue(tag, value),
                    ),
                )
            },
            onAxesReset = {
                onSettingsChange(
                    settings.copy(academmuniconsAxes = AcademmuniconsAxes.Default),
                )
            },
            onFamilyChange = onFamilyChange,
            onQueryChange = onQueryChange,
            onOpenLegacyViews = onOpenLegacyViews,
        )

        is ExplorerFamily.RegularCatalog -> VariableFontCatalogExplorer(
            selectedFamily = family,
            query = settings.query,
            label = family.catalogLabel,
            searchHint = family.searchHint,
            catalog = family.catalog(),
            itemName = DemoIcon::name,
            itemCodePoint = DemoIcon::codePoint,
            font = family.font,
            fontSettings = family.font.fontSettings,
            onFamilyChange = onFamilyChange,
            onQueryChange = onQueryChange,
            onOpenLegacyViews = onOpenLegacyViews,
        )
    }
}

@Composable
private fun Api21SymbolShowcase(onOpenLegacyViews: (() -> Unit)?) {
    MaterialSymbolsTheme(style = MaterialSymbolStyle.Rounded) {
        Column(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Symbols on Android 21–25",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "A live regular font and build-time snapshots of regular " +
                    "and variable fonts work without runtime font variations.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SymbolFontIcon(
                    codePoint = MaterialSymbols.Search.codePoint,
                    font = MaterialSymbolsRoundedStatic,
                    fontSettings = MaterialSymbolsRoundedStatic.fontSettings,
                    contentDescription = "Search from a regular font",
                    tint = MaterialTheme.colorScheme.primary,
                    size = 40.dp,
                )
                Icon(
                    imageVector = AppIcons.Regular.Check,
                    contentDescription = "Check generated from a regular font",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Icon(
                    imageVector = AppIcons.Rounded.Favorite,
                    contentDescription = "Favorite generated from a variable font instance",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                text = "The two vectors were selected by semantic name in the Gradle DSL, " +
                    "so unused generated symbols never enter this app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OtherIconFontsShowcase()
            LegacyViewsButton(onOpenLegacyViews)
        }
    }
}

@Composable
private fun OtherIconFontsShowcase() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Generated from other icon fonts",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                OtherFontExample(
                    imageVector = FontAwesomeIcons.Solid.CircleCheck,
                    label = "Font Awesome",
                    modifier = Modifier.weight(1f),
                )
                OtherFontExample(
                    imageVector = TablerIcons.Outline.Sparkles,
                    label = "Tabler outline",
                    modifier = Modifier.weight(1f),
                )
                OtherFontExample(
                    imageVector = TablerIcons.Filled.Alien,
                    label = "Tabler filled",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OtherFontExample(
    imageVector: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun LegacyViewsButton(onOpenLegacyViews: (() -> Unit)?) {
    if (onOpenLegacyViews != null) {
        OutlinedButton(onClick = onOpenLegacyViews) {
            Text("Open Android Views examples")
        }
    }
}

internal sealed class ExplorerFamily(
    val label: String,
    val tabLabel: String,
) {
    abstract fun fontSettings(settings: ExplorerSettings): SymbolFontSettings

    data object Material : ExplorerFamily("Material Symbols", "Material") {
        override fun fontSettings(settings: ExplorerSettings): SymbolFontSettings =
            settings.axes.fontSettings
    }

    data object FontAwesome : RegularCatalog(
        label = "Font Awesome",
        tabLabel = "Awesome",
        searchHint = "Try face_smile",
        catalog = { FontAwesomeCatalog },
        font = FontAwesomeRegular,
    )

    data object Tabler : ExplorerFamily("Tabler Icons", "Tabler") {
        override fun fontSettings(settings: ExplorerSettings): SymbolFontSettings =
            settings.tablerStyle.font(settings.tablerStroke).fontSettings
    }

    data object Powerline : RegularCatalog(
        label = "Powerline Symbols",
        tabLabel = "Powerline",
        catalogLabel = "Powerline",
        searchHint = "Try branch",
        catalog = { PowerlineCatalog },
        font = PowerlineRegular,
    )

    data object Academmunicons : ExplorerFamily("Academmunicons", "Academic") {
        override fun fontSettings(settings: ExplorerSettings): SymbolFontSettings =
            settings.academmuniconsAxes.fontSettings
    }

    sealed class RegularCatalog(
        label: String,
        tabLabel: String,
        val catalogLabel: String = label,
        val searchHint: String,
        val catalog: () -> List<DemoIcon>,
        val font: SymbolFont.Regular,
    ) : ExplorerFamily(label, tabLabel) {
        final override fun fontSettings(settings: ExplorerSettings): SymbolFontSettings =
            font.fontSettings
    }

    companion object {
        val entries: List<ExplorerFamily> by lazy {
            listOf(Material, FontAwesome, Tabler, Powerline, Academmunicons)
        }
    }
}

internal enum class MaterialStyleOption(
    val label: String,
    val font: SymbolFont.Variable,
    val style: MaterialSymbolStyle,
) {
    Outlined("Outlined", MaterialSymbolsOutlined, MaterialSymbolStyle.Outlined),
    Rounded("Rounded", MaterialSymbolsRounded, MaterialSymbolStyle.Rounded),
    Sharp("Sharp", MaterialSymbolsSharp, MaterialSymbolStyle.Sharp),
}

internal data class ExplorerSettings(
    val family: ExplorerFamily = ExplorerFamily.Material,
    val query: String = "",
    val materialStyleOption: MaterialStyleOption = MaterialStyleOption.Outlined,
    val axes: MaterialSymbolAxes = MaterialSymbolAxes.Default,
    val tablerStyle: TablerStyle = TablerStyle.Outline,
    val tablerStroke: TablerStroke = TablerStroke.Default,
    val academmuniconsAxes: AcademmuniconsAxes = AcademmuniconsAxes.Default,
) {
    val fontSettings: SymbolFontSettings
        get() = family.fontSettings(this)
}

internal fun usesThemedImageVectors(axes: MaterialSymbolAxes): Boolean =
    axes == MaterialSymbolAxes.Default
