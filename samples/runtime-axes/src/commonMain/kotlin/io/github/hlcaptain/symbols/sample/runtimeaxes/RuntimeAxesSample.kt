package io.github.hlcaptain.symbols.sample.runtimeaxes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.font.SymbolFontAxis
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolsRuntime
import io.github.hlcaptain.symbols.font.SymbolsTheme
import io.github.hlcaptain.symbols.font.fontSettings
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.Rounded
import io.github.hlcaptain.symbols.material.Search
import io.github.hlcaptain.symbols.material.font
import io.github.hlcaptain.symbols.sample.api.SampleAvailability
import io.github.hlcaptain.symbols.sample.api.SampleItem
import io.github.hlcaptain.symbols.sample.runtimeaxes.config.SampleBuildConfig
import io.github.hlcaptain.symbols.sample.ui.AxisControls
import io.github.hlcaptain.symbols.sample.ui.AxisUiModel
import io.github.hlcaptain.symbols.sample.ui.ExampleCard
import io.github.hlcaptain.symbols.sample.ui.SamplePage
import kotlin.math.roundToInt
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single

@Module
@Configuration
class RuntimeAxesNavigationModule {
    @Qualifier(RuntimeAxesNavigationModule::class)
    @Single
    fun sampleItem() = SampleItem(
        id = "runtime-axes",
        title = Title,
        description = Description,
        modulePath = SampleBuildConfig.MODULE_PATH,
        availability = if (SymbolsRuntime.variableFontsSupported) {
            SampleAvailability.Available
        } else {
            SampleAvailability.Unavailable(UnsupportedMessage)
        },
        content = { RuntimeAxesContent() },
    )
}

@Composable
private fun RuntimeAxesContent() {
    val fontAxes = Symbols.Material.Rounded.font.variationAxes
    val axisValues = remember {
        fontAxes.associateTo(mutableStateMapOf()) { axis ->
            axis.tag to axis.defaultValue
        }
    }
    val axes = fontAxes.map { axis ->
        val value = axisValues.getValue(axis.tag)
        AxisUiModel(
            tag = axis.tag,
            label = axis.label,
            value = value,
            minValue = axis.minValue,
            maxValue = axis.maxValue,
            valueLabel = axis.format(value),
            steps = 0,
        )
    }

    SamplePage(description = Description) {
        RuntimeMaterialFontPreview(axisValues)
        AxisControls(
            axes = axes,
            onValueChange = { tag, value -> axisValues[tag] = value },
            onReset = {
                fontAxes.forEach { axis -> axisValues[axis.tag] = axis.defaultValue }
            },
            resetEnabled = fontAxes.any { axis ->
                axisValues[axis.tag] != axis.defaultValue
            },
            title = "Runtime Material font axes",
        )
    }
}

@Composable
private fun RuntimeMaterialFontPreview(axisValues: Map<String, Float>) {
    val font = Symbols.Material.Rounded.font
    val overrides = font.variationAxes.filter { axis ->
        axisValues[axis.tag] != axis.defaultValue
    }
    SymbolsTheme(fontSettings = font.fontSettings(axisValues)) {
        ExampleCard(
            title = "Live Material variable font",
            description = "Each control updates its matching Material Symbols axis live.",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SymbolFontIcon(
                    codePoint = Symbols.Material.Search.codePoint,
                    font = font,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    size = 72.dp,
                )
                Text(
                    text = if (overrides.isEmpty()) {
                        "Overrides: defaults"
                    } else {
                        overrides.joinToString(prefix = "Overrides: ") { axis ->
                            "${axis.tag}=${axis.format(axisValues.getValue(axis.tag))}"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun SymbolFontAxis.format(value: Float): String = when (tag) {
    "FILL" -> "${(value * 100).roundToInt()}%"
    else -> value.roundToInt().toString()
}

private const val Title = "Runtime Material variable-font axes"
private const val Description =
    "Animate or drag each Material Symbols axis at runtime and read its live value."
private const val UnsupportedMessage =
    "Android requires API 26 or newer for runtime font variations."
