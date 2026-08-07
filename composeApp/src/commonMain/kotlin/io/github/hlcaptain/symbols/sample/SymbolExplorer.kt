package io.github.hlcaptain.symbols.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.font.SymbolFont
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.rememberSymbolFontFamily
import io.github.hlcaptain.symbols.material.Icons
import io.github.hlcaptain.symbols.material.MaterialSymbol
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.vectors.themed.Home
import io.github.hlcaptain.symbols.material.vectors.themed.Search
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun MaterialCatalogExplorer(
    families: List<ExplorerFamily>,
    query: String,
    style: MaterialStyleOption,
    axes: MaterialSymbolAxes,
    onFamilyChange: (ExplorerFamily) -> Unit,
    onQueryChange: (String) -> Unit,
    onStyleChange: (MaterialStyleOption) -> Unit,
    onAxesChange: (MaterialSymbolAxes) -> Unit,
    onOpenLegacyViews: (() -> Unit)?,
) {
    val fontFamily = rememberSymbolFontFamily(style.font)

    ExplorerLayout(
        families = families,
        selectedFamily = ExplorerFamily.Material,
        query = query,
        label = ExplorerFamily.Material.label,
        searchHint = "Try account_circle",
        items = MaterialSymbols.all,
        itemName = MaterialSymbol::name,
        icon = { symbol -> MaterialFontIcon(symbol, fontFamily) },
        onFamilyChange = onFamilyChange,
        onQueryChange = onQueryChange,
        onOpenLegacyViews = onOpenLegacyViews,
        options = {
            MaterialOptions(
                style = style,
                axes = axes,
                onStyleChange = onStyleChange,
                onAxesChange = onAxesChange,
            )
        },
    )
}

@Composable
private fun MaterialFontIcon(
    symbol: MaterialSymbol,
    fontFamily: FontFamily,
) {
    SymbolFontIcon(
        codePoint = symbol.codePoint,
        fontFamily = fontFamily,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        size = 26.dp,
    )
}

@Composable
internal fun TablerCatalogExplorer(
    families: List<ExplorerFamily>,
    query: String,
    style: TablerStyle,
    stroke: TablerStroke,
    onFamilyChange: (ExplorerFamily) -> Unit,
    onQueryChange: (String) -> Unit,
    onStyleChange: (TablerStyle) -> Unit,
    onStrokeChange: (TablerStroke) -> Unit,
    onOpenLegacyViews: (() -> Unit)?,
) {
    val font = when (style) {
        TablerStyle.Outline -> stroke
        TablerStyle.Filled -> TablerFilled
    }
    val catalog = when (style) {
        TablerStyle.Outline -> TablerOutlineCatalog
        TablerStyle.Filled -> TablerFilledCatalog
    }

    FontCatalogExplorer(
        families = families,
        selectedFamily = ExplorerFamily.Tabler,
        query = query,
        label = "Tabler ${style.label}",
        searchHint = "Try sparkles",
        catalog = catalog,
        font = font,
        onFamilyChange = onFamilyChange,
        onQueryChange = onQueryChange,
        onOpenLegacyViews = onOpenLegacyViews,
        options = {
            TablerOptions(
                style = style,
                stroke = stroke,
                onStyleChange = onStyleChange,
                onStrokeChange = onStrokeChange,
            )
        },
    )
}

@Composable
internal fun AcademmuniconsCatalogExplorer(
    families: List<ExplorerFamily>,
    query: String,
    axes: AcademmuniconsAxes,
    onFamilyChange: (ExplorerFamily) -> Unit,
    onQueryChange: (String) -> Unit,
    onAxesChange: (AcademmuniconsAxes) -> Unit,
    onOpenLegacyViews: (() -> Unit)?,
) {
    FontCatalogExplorer(
        families = families,
        selectedFamily = ExplorerFamily.Academmunicons,
        query = query,
        label = "Academmunicons",
        searchHint = "Try orcid",
        catalog = AcademmuniconsCatalog,
        font = axes.font,
        onFamilyChange = onFamilyChange,
        onQueryChange = onQueryChange,
        onOpenLegacyViews = onOpenLegacyViews,
        options = { AcademmuniconsAxesCard(axes, onAxesChange) },
    )
}

@Composable
internal fun FontCatalogExplorer(
    families: List<ExplorerFamily>,
    selectedFamily: ExplorerFamily,
    query: String,
    label: String,
    searchHint: String,
    catalog: List<DemoIcon>,
    font: SymbolFont,
    onFamilyChange: (ExplorerFamily) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenLegacyViews: (() -> Unit)?,
    options: @Composable () -> Unit = { StaticFontDescription(label) },
) {
    val fontFamily = rememberSymbolFontFamily(font)

    ExplorerLayout(
        families = families,
        selectedFamily = selectedFamily,
        query = query,
        label = label,
        searchHint = searchHint,
        items = catalog,
        itemName = DemoIcon::name,
        icon = { icon ->
            SymbolFontIcon(
                codePoint = icon.codePoint,
                fontFamily = fontFamily,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                size = 26.dp,
            )
        },
        onFamilyChange = onFamilyChange,
        onQueryChange = onQueryChange,
        onOpenLegacyViews = onOpenLegacyViews,
        options = options,
    )
}

@Composable
private fun <T> ExplorerLayout(
    families: List<ExplorerFamily>,
    selectedFamily: ExplorerFamily,
    query: String,
    label: String,
    searchHint: String,
    items: List<T>,
    itemName: (T) -> String,
    icon: @Composable (T) -> Unit,
    onFamilyChange: (ExplorerFamily) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenLegacyViews: (() -> Unit)?,
    options: @Composable () -> Unit,
) {
    val normalizedQuery = query.trim().lowercase()
    val visibleItems = remember(items, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            items
        } else {
            items.filter { normalizedQuery in itemName(it) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            modifier = Modifier
                .widthIn(max = 1200.dp)
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ExplorerHeader(onOpenLegacyViews)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                PrimaryTabRow(
                    selectedTabIndex = families.indexOf(selectedFamily),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    families.forEach { family ->
                        Tab(
                            selected = selectedFamily == family,
                            onClick = { onFamilyChange(family) },
                            modifier = Modifier.semantics {
                                contentDescription = family.label
                            },
                            text = {
                                Text(
                                    text = family.tabLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                options()
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search $label") },
                    placeholder = { Text(searchHint) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Themed.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    supportingText = {
                        Text(
                            if (visibleItems.isEmpty()) {
                                "No matching icons"
                            } else {
                                "${visibleItems.size} icons"
                            },
                        )
                    },
                )
            }

            if (visibleItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    StatusMessage("No icon matches “${query.trim()}”.")
                }
            } else {
                items(
                    items = visibleItems,
                    key = itemName,
                ) { item ->
                    IconCard(name = itemName(item)) {
                        icon(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorerHeader(onOpenLegacyViews: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Themed.Search,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.Themed.Home,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Symbols explorer",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Browse Material Symbols and complete custom icon-font catalogs.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        LegacyViewsButton(onOpenLegacyViews)
    }
}

@Composable
private fun MaterialOptions(
    style: MaterialStyleOption,
    axes: MaterialSymbolAxes,
    onStyleChange: (MaterialStyleOption) -> Unit,
    onAxesChange: (MaterialSymbolAxes) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryTabRow(
            selectedTabIndex = style.ordinal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            MaterialStyleOption.entries.forEach { candidate ->
                Tab(
                    selected = style == candidate,
                    onClick = { onStyleChange(candidate) },
                    text = { Text(candidate.label) },
                )
            }
        }
        MaterialAxesCard(axes, onAxesChange)
    }
}

@Composable
private fun MaterialAxesCard(
    axes: MaterialSymbolAxes,
    onAxesChange: (MaterialSymbolAxes) -> Unit,
) {
    VariableAxesCard(
        resetEnabled = axes != MaterialSymbolAxes.Default,
        onReset = { onAxesChange(MaterialSymbolAxes.Default) },
    ) {
        AxisControl(
            label = "Fill",
            valueLabel = "${(axes.fill * 100).roundToInt()}%",
            value = axes.fill,
            range = MaterialSymbolAxes.MinFill..MaterialSymbolAxes.MaxFill,
            onValueChange = { onAxesChange(axes.copy(fill = it)) },
        )
        AxisControl(
            label = "Weight",
            valueLabel = axes.weight.toString(),
            value = axes.weight.toFloat(),
            range = MaterialSymbolAxes.MinWeight.toFloat()..
                MaterialSymbolAxes.MaxWeight.toFloat(),
            steps = 5,
            onValueChange = { onAxesChange(axes.copy(weight = it.roundToInt())) },
        )
        AxisControl(
            label = "Grade",
            valueLabel = axes.grade.roundToInt().toString(),
            value = axes.grade,
            range = MaterialSymbolAxes.MinGrade..MaterialSymbolAxes.MaxGrade,
            onValueChange = { onAxesChange(axes.copy(grade = it)) },
        )
        AxisControl(
            label = "Optical",
            valueLabel = axes.opticalSize.roundToInt().toString(),
            value = axes.opticalSize,
            range = MaterialSymbolAxes.MinOpticalSize..MaterialSymbolAxes.MaxOpticalSize,
            onValueChange = { onAxesChange(axes.copy(opticalSize = it)) },
        )
    }
}

@Composable
private fun AcademmuniconsAxesCard(
    axes: AcademmuniconsAxes,
    onAxesChange: (AcademmuniconsAxes) -> Unit,
) {
    VariableAxesCard(
        resetEnabled = axes != AcademmuniconsAxes.Default,
        onReset = { onAxesChange(AcademmuniconsAxes.Default) },
    ) {
        AxisControl(
            label = "Frame",
            valueLabel = "${(axes.frame * 100).roundToInt()}%",
            value = axes.frame,
            range = AcademmuniconsAxes.MinFrame..AcademmuniconsAxes.MaxFrame,
            onValueChange = { onAxesChange(axes.copy(frame = it)) },
        )
        AxisControl(
            label = "Weight",
            valueLabel = axes.weight.roundToInt().toString(),
            value = axes.weight,
            range = AcademmuniconsAxes.MinWeight..AcademmuniconsAxes.MaxWeight,
            steps = 13,
            onValueChange = { onAxesChange(axes.copy(weight = it)) },
        )
    }
}

@Composable
private fun VariableAxesCard(
    resetEnabled: Boolean,
    onReset: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Variable font axes", style = MaterialTheme.typography.titleMedium)
                TextButton(
                    onClick = onReset,
                    enabled = resetEnabled,
                ) {
                    Text("Reset")
                }
            }
            content()
        }
    }
}

@Composable
private fun TablerOptions(
    style: TablerStyle,
    stroke: TablerStroke,
    onStyleChange: (TablerStyle) -> Unit,
    onStrokeChange: (TablerStroke) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryTabRow(
            selectedTabIndex = style.ordinal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TablerStyle.entries.forEach { candidate ->
                Tab(
                    selected = style == candidate,
                    onClick = { onStyleChange(candidate) },
                    text = { Text(candidate.label) },
                )
            }
        }
        if (style == TablerStyle.Outline) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Outline stroke", style = MaterialTheme.typography.titleMedium)
                    AxisControl(
                        label = "Stroke",
                        valueLabel = stroke.label,
                        value = stroke.value,
                        range = 1f..2f,
                        steps = 1,
                        onValueChange = { value ->
                            onStrokeChange(
                                TablerStroke.entries.minBy { abs(it.value - value) },
                            )
                        },
                    )
                }
            }
        } else {
            InfoCard(
                title = "Filled font",
                body = "The complete filled catalog uses Tabler's dedicated filled glyphs.",
            )
        }
    }
}

@Composable
private fun StaticFontDescription(label: String) {
    InfoCard(
        title = "Static font",
        body = "$label defines no OpenType variation axes. Its complete bundled catalog " +
            "is available below.",
    )
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AxisControl(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(60.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = label },
            valueRange = range,
            steps = steps,
        )
        Text(
            text = valueLabel,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StatusMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IconCard(
    name: String,
    icon: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            icon()
            Text(
                text = name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
