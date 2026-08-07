package io.github.hlcaptain.symbols.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.font.SymbolFont
import io.github.hlcaptain.symbols.font.SymbolFontAxis
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.font.rememberSymbolFontFamily
import io.github.hlcaptain.symbols.material.Icons
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.vectors.themed.Home
import io.github.hlcaptain.symbols.material.vectors.themed.Search
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun <T> VariableFontCatalogExplorer(
    selectedFamily: ExplorerFamily,
    query: String,
    label: String,
    searchHint: String,
    catalog: List<T>,
    itemName: (T) -> String,
    itemCodePoint: (T) -> Int,
    font: SymbolFont,
    renderFont: SymbolFont = font,
    fontSettings: SymbolFontSettings = SymbolFontSettings.Default,
    defaultFontSettings: SymbolFontSettings = fontSettings,
    onAxisValueChange: ((String, Float) -> Unit)? = null,
    onAxesReset: (() -> Unit)? = null,
    onFamilyChange: (ExplorerFamily) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenLegacyViews: (() -> Unit)?,
    staticImageVector: (@Composable (T) -> ImageVector)? = null,
    options: (@Composable () -> Unit)? = null,
) {
    // A matching static snapshot avoids loading the font until an axis changes.
    val fontFamily = if (staticImageVector == null) {
        rememberSymbolFontFamily(renderFont, fontSettings)
    } else {
        null
    }

    ExplorerLayout(
        families = ExplorerFamily.entries,
        selectedFamily = selectedFamily,
        query = query,
        label = label,
        searchHint = searchHint,
        items = catalog,
        itemName = itemName,
        icon = { item ->
            if (staticImageVector == null) {
                SymbolFontIcon(
                    codePoint = itemCodePoint(item),
                    fontFamily = checkNotNull(fontFamily),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    size = 26.dp,
                )
            } else {
                Icon(
                    imageVector = staticImageVector(item),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        onFamilyChange = onFamilyChange,
        onQueryChange = onQueryChange,
        onOpenLegacyViews = onOpenLegacyViews,
        options = {
            FontOptions(
                font = font,
                label = label,
                fontSettings = fontSettings,
                defaultFontSettings = defaultFontSettings,
                onAxisValueChange = onAxisValueChange,
                onAxesReset = onAxesReset,
                additionalOptions = options,
            )
        },
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
internal fun MaterialOptions(
    style: MaterialStyleOption,
    onStyleChange: (MaterialStyleOption) -> Unit,
) {
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
}

@Composable
private fun FontOptions(
    font: SymbolFont,
    label: String,
    fontSettings: SymbolFontSettings,
    defaultFontSettings: SymbolFontSettings,
    onAxisValueChange: ((String, Float) -> Unit)?,
    onAxesReset: (() -> Unit)?,
    additionalOptions: (@Composable () -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        additionalOptions?.invoke()
        when (font) {
            is SymbolFont.Variable -> {
                if (font.variationAxes.isNotEmpty()) {
                    VariableAxesCard(
                        axes = font.variationAxes,
                        fontSettings = fontSettings,
                        resetEnabled = fontSettings != defaultFontSettings,
                        onReset = requireNotNull(onAxesReset) {
                            "${font.familyName} requires an axis reset handler"
                        },
                        onAxisValueChange = requireNotNull(onAxisValueChange) {
                            "${font.familyName} requires an axis change handler"
                        },
                    )
                } else if (additionalOptions == null) {
                    InfoCard(
                        title = "Variable font",
                        body = "$label does not embed axis metadata, so controls cannot be derived.",
                    )
                }
            }

            is SymbolFont.Regular -> {
                if (additionalOptions == null) {
                    InfoCard(
                        title = "Static font",
                        body = "$label defines no OpenType variation axes. Its complete bundled " +
                            "catalog is available below.",
                    )
                }
            }
        }
    }
}

@Composable
private fun VariableAxesCard(
    axes: List<SymbolFontAxis>,
    fontSettings: SymbolFontSettings,
    resetEnabled: Boolean,
    onReset: () -> Unit,
    onAxisValueChange: (String, Float) -> Unit,
) {
    val density = LocalDensity.current
    val values = fontSettings.variationSettings.settings.associate {
        it.axisName to it.toVariationValue(density)
    }
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
            axes.forEach { axis ->
                val value = values[axis.tag] ?: axis.defaultValue
                AxisControl(
                    label = axis.label,
                    valueLabel = axis.format(value),
                    value = value,
                    range = axis.minValue..axis.maxValue,
                    onValueChange = { onAxisValueChange(axis.tag, it) },
                )
            }
        }
    }
}

internal fun MaterialSymbolAxes.withAxisValue(tag: String, value: Float): MaterialSymbolAxes =
    when (tag) {
        "FILL" -> copy(fill = value)
        "GRAD" -> copy(grade = value)
        "opsz" -> copy(opticalSize = value)
        "wght" -> copy(weight = value.roundToInt())
        else -> error("Unsupported Material Symbols axis: $tag")
    }

internal fun AcademmuniconsAxes.withAxisValue(tag: String, value: Float): AcademmuniconsAxes =
    when (tag) {
        "ital" -> copy(frame = value)
        "wght" -> copy(weight = value)
        else -> error("Unsupported Academmunicons axis: $tag")
    }

internal fun SymbolFontAxis.format(value: Float): String =
    if (maxValue - minValue <= 1f) {
        "${(value * 100).roundToInt()}%"
    } else {
        val rounded = (value * 10).roundToInt() / 10f
        if (rounded == rounded.roundToInt().toFloat()) {
            rounded.roundToInt().toString()
        } else {
            rounded.toString()
        }
    }

@Composable
internal fun TablerOptions(
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
