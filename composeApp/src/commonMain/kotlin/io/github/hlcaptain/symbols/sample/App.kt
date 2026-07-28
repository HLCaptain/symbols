package io.github.hlcaptain.symbols.sample

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.material.Home
import io.github.hlcaptain.symbols.material.MaterialSymbol
import io.github.hlcaptain.symbols.material.MaterialSymbolAxes
import io.github.hlcaptain.symbols.material.MaterialSymbolFont
import io.github.hlcaptain.symbols.material.MaterialSymbolIcon
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.Search
import io.github.hlcaptain.symbols.material.outlined.MaterialSymbolsOutlined
import io.github.hlcaptain.symbols.material.outlined.vectors.outlinedImageVector
import io.github.hlcaptain.symbols.material.rememberMaterialSymbolFontFamily
import io.github.hlcaptain.symbols.material.rounded.MaterialSymbolsRounded
import io.github.hlcaptain.symbols.material.sharp.MaterialSymbolsSharp
import kotlin.math.roundToInt
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SymbolExplorer()
        }
    }
}

@Composable
private fun SymbolExplorer() {
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
    val fontFamily = rememberMaterialSymbolFontFamily(style.font, axes)
    val normalizedQuery = query.trim().lowercase()
    val symbols = remember(normalizedQuery) {
        MaterialSymbols.all.asSequence()
            .filter { normalizedQuery.isEmpty() || normalizedQuery in it.name }
            .take(180)
            .toList()
    }

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
                    symbol = MaterialSymbols.Home,
                    fontFamily = fontFamily,
                    contentDescription = null,
                    axes = axes,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 40.dp,
                )
                Icon(
                    imageVector = MaterialSymbols.Home.outlinedImageVector,
                    contentDescription = "Static ImageVector access",
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
                        axes = axes,
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
                items(
                    items = symbols,
                    key = MaterialSymbol::name,
                ) { symbol ->
                    SymbolCard(
                        symbol = symbol,
                        axes = axes,
                        fontFamily = fontFamily,
                    )
                }
            }
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
    axes: MaterialSymbolAxes,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
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
                axes = axes,
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
) {
    Outlined("Outlined", MaterialSymbolsOutlined),
    Rounded("Rounded", MaterialSymbolsRounded),
    Sharp("Sharp", MaterialSymbolsSharp),
}
