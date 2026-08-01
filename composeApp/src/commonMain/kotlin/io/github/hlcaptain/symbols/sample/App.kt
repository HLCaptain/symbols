package io.github.hlcaptain.symbols.sample

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.material.Icons
import io.github.hlcaptain.symbols.material.MaterialSymbol
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.MaterialSymbolFont
import io.github.hlcaptain.symbols.material.MaterialSymbolIcon
import io.github.hlcaptain.symbols.material.MaterialSymbolStyle
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.MaterialSymbolsRuntime
import io.github.hlcaptain.symbols.material.MaterialSymbolsTheme
import io.github.hlcaptain.symbols.material.Search
import io.github.hlcaptain.symbols.material.Symbols
import io.github.hlcaptain.symbols.material.outlined.MaterialSymbolsOutlined
import io.github.hlcaptain.symbols.material.rememberMaterialSymbolFontFamily
import io.github.hlcaptain.symbols.material.rounded.MaterialSymbolsRounded
import io.github.hlcaptain.symbols.material.rounded.staticfont.MaterialSymbolsRoundedStatic
import io.github.hlcaptain.symbols.material.sharp.MaterialSymbolsSharp
import io.github.hlcaptain.symbols.material.vectors.themed.Home
import io.github.hlcaptain.symbols.sample.generated.AppIcons
import io.github.hlcaptain.symbols.sample.generated.fontawesome.FontAwesomeIcons
import io.github.hlcaptain.symbols.sample.generated.fontawesome.solid.CircleCheck
import io.github.hlcaptain.symbols.sample.generated.powerline.PowerlineIcons
import io.github.hlcaptain.symbols.sample.generated.powerline.regular.Branch
import io.github.hlcaptain.symbols.sample.generated.regular.Check
import io.github.hlcaptain.symbols.sample.generated.rounded.Favorite
import io.github.hlcaptain.symbols.sample.generated.tabler.TablerIcons
import io.github.hlcaptain.symbols.sample.generated.tabler.filled.Alien
import kotlin.math.roundToInt
import org.jetbrains.compose.ui.tooling.preview.Preview
import io.github.hlcaptain.symbols.material.rounded.staticfont.MaterialSymbolIcon as StaticRoundedSymbolIcon

@Composable
@Preview
fun App() {
    App(onOpenLegacyViews = null)
}

@Composable
internal fun App(onOpenLegacyViews: (() -> Unit)?) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (MaterialSymbolsRuntime.variableFontsSupported) {
                SymbolExplorer(onOpenLegacyViews)
            } else {
                Api21SymbolShowcase(onOpenLegacyViews)
            }
        }
    }
}

@Composable
private fun Api21SymbolShowcase(onOpenLegacyViews: (() -> Unit)?) {
    MaterialSymbolsTheme {
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
                    "and variable fonts all work without runtime font variations.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StaticRoundedSymbolIcon(
                    symbol = Symbols.Rounded.Search,
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
                    contentDescription =
                        "Favorite generated from a variable font instance",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                text = "The two vectors were selected by semantic name in the " +
                    "Gradle DSL, so unused generated symbols never enter this app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OtherIconFontsShowcase()
            LegacyViewsButton(onOpenLegacyViews)
        }
    }
}

@Composable
private fun SymbolExplorer(onOpenLegacyViews: (() -> Unit)?) {
    var style by remember { mutableStateOf(DemoStyle.Outlined) }
    var fill by remember { mutableFloatStateOf(0f) }
    var weight by remember { mutableFloatStateOf(400f) }
    var grade by remember { mutableFloatStateOf(0f) }
    var opticalSize by remember { mutableFloatStateOf(24f) }
    var query by remember { mutableStateOf("") }

    val axes = MaterialSymbolAxes(
        fill = fill,
        weight = weight.roundToInt(),
        grade = grade,
        opticalSize = opticalSize,
    )
    val normalizedQuery = query.trim().lowercase()
    val symbols = remember(normalizedQuery) {
        MaterialSymbols.all.asSequence()
            .filter { normalizedQuery.isEmpty() || normalizedQuery in it.name }
            .take(180)
            .toList()
    }

    MaterialSymbolsTheme(style = style.vectorStyle, axes = axes) {
        val fontFamily = rememberMaterialSymbolFontFamily(style.font)
        Scaffold { contentPadding ->
            Column(
                modifier = Modifier
                    .safeContentPadding()
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MaterialSymbolIcon(
                    symbol = MaterialSymbols.Search,
                    fontFamily = fontFamily,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 40.dp,
                )
                Icon(
                    imageVector = Icons.Themed.Home,
                    contentDescription = "Theme-selected ImageVector access",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Column {
                    Text(
                        text = "Symbols",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "4,102 names · one variable font per style",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            LegacyViewsButton(onOpenLegacyViews)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DemoStyle.entries.forEach { candidate ->
                    FilterChip(
                        selected = style == candidate,
                        onClick = { style = candidate },
                        label = { Text(candidate.label) },
                    )
                }
            }

            AxisControl(
                label = "Fill",
                valueLabel = "${(fill * 100).roundToInt()}%",
                value = fill,
                range = 0f..1f,
                onValueChange = { fill = it },
            )
            AxisControl(
                label = "Weight",
                valueLabel = weight.roundToInt().toString(),
                value = weight,
                range = 100f..700f,
                steps = 5,
                onValueChange = { weight = it },
            )
            AxisControl(
                label = "Grade",
                valueLabel = grade.roundToInt().toString(),
                value = grade,
                range = -50f..200f,
                onValueChange = { grade = it },
            )
            AxisControl(
                label = "Optical",
                valueLabel = opticalSize.roundToInt().toString(),
                value = opticalSize,
                range = 20f..48f,
                onValueChange = { opticalSize = it },
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Find a canonical name") },
                leadingIcon = {
                    MaterialSymbolIcon(
                        symbol = MaterialSymbols.Search,
                        fontFamily = fontFamily,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                supportingText = {
                    Text("${symbols.size} shown · type a name such as account_circle")
                },
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OtherIconFontsShowcase()
                }
                items(
                    items = symbols,
                    key = MaterialSymbol::name,
                ) { symbol ->
                    SymbolCard(
                        symbol = symbol,
                        fontFamily = fontFamily,
                    )
                }
            }
        }
    }
}
}

@Composable
private fun OtherIconFontsShowcase() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Other icon fonts",
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.CircleCheck,
                contentDescription = "Font Awesome circle check",
                modifier = Modifier.size(32.dp),
            )
            Icon(
                imageVector = TablerIcons.Filled.Alien,
                contentDescription = "Tabler alien",
                modifier = Modifier.size(32.dp),
            )
            Icon(
                imageVector = PowerlineIcons.Regular.Branch,
                contentDescription = "Powerline branch",
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = "Font Awesome · Tabler · Powerline",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun LegacyViewsButton(onOpenLegacyViews: (() -> Unit)?) {
    if (onOpenLegacyViews != null) {
        Button(onClick = onOpenLegacyViews) {
            Text("Open Android Views examples")
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
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            valueRange = range,
            steps = steps,
        )
        Text(
            text = valueLabel,
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SymbolCard(
    symbol: MaterialSymbol,
    fontFamily: FontFamily,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MaterialSymbolIcon(
                symbol = symbol,
                fontFamily = fontFamily,
                contentDescription = symbol.name.replace('_', ' '),
                tint = MaterialTheme.colorScheme.onSurface,
                size = 32.dp,
            )
            Text(
                text = symbol.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private enum class DemoStyle(
    val label: String,
    val font: MaterialSymbolFont,
    val vectorStyle: MaterialSymbolStyle,
) {
    Outlined(
        "Outlined",
        MaterialSymbolsOutlined,
        MaterialSymbolStyle.Outlined,
    ),
    Rounded(
        "Rounded",
        MaterialSymbolsRounded,
        MaterialSymbolStyle.Rounded,
    ),
    Sharp(
        "Sharp",
        MaterialSymbolsSharp,
        MaterialSymbolStyle.Sharp,
    ),
}
