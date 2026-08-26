package io.github.hlcaptain.symbols.sample.runtimeaxes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.hlcaptain.symbols.material.Search
import io.github.hlcaptain.symbols.material.rounded.MaterialSymbolsRounded
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
    var axisValues by remember { mutableStateOf(emptyMap<String, Float>()) }
    var isAnimating by remember { mutableStateOf(false) }
    val animatedAxis = MaterialSymbolsRounded.variationAxes.first { it.tag == "wght" }
    val animatedValue = axisValues[animatedAxis.tag] ?: animatedAxis.defaultValue
    val axes = MaterialSymbolsRounded.variationAxes.map { axis ->
        val value = axisValues[axis.tag] ?: axis.defaultValue
        AxisUiModel(
            tag = axis.tag,
            label = axis.label,
            value = value,
            minValue = axis.minValue,
            maxValue = axis.maxValue,
            valueLabel = axis.format(value),
        )
    }

    LaunchedEffect(isAnimating) {
        if (!isAnimating) return@LaunchedEffect

        val value = Animatable(animatedValue)
        var target = if (value.value < animatedAxis.maxValue) {
            animatedAxis.maxValue
        } else {
            animatedAxis.minValue
        }
        while (true) {
            value.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = AnimationDurationMillis, easing = LinearEasing),
            ) {
                axisValues = axisValues + (animatedAxis.tag to this.value)
            }
            target = if (target == animatedAxis.maxValue) {
                animatedAxis.minValue
            } else {
                animatedAxis.maxValue
            }
        }
    }

    SamplePage(description = Description) {
        SymbolsTheme(fontSettings = MaterialSymbolsRounded.fontSettings(axisValues)) {
            ExampleCard(
                title = "Live variable font",
                description = "Every slider updates the same generic Map<String, Float>.",
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SymbolFontIcon(
                        codePoint = Symbols.Material.Search.codePoint,
                        font = MaterialSymbolsRounded,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        size = 72.dp,
                    )
                    Text(
                        text = if (axisValues.isEmpty()) {
                            "Overrides: defaults"
                        } else {
                            axisValues.entries.joinToString(prefix = "Overrides: ") {
                                (tag, _) -> "$tag=${axes.first { it.tag == tag }.valueLabel}"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = { isAnimating = !isAnimating }) {
                            Text(if (isAnimating) "Stop animation" else "Animate weight")
                        }
                        Text(
                            text = "${animatedAxis.label}: ${animatedAxis.format(animatedValue)}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        AxisControls(
            axes = axes,
            onValueChange = { tag, value ->
                isAnimating = false
                axisValues = axisValues + (tag to value)
            },
            onReset = {
                isAnimating = false
                axisValues = emptyMap()
            },
            resetEnabled = isAnimating || axisValues.isNotEmpty(),
        )
    }
}

private fun SymbolFontAxis.format(value: Float): String = when (tag) {
    "FILL" -> "${(value * 100).roundToInt()}%"
    else -> value.roundToInt().toString()
}

private const val Title = "Runtime variable-font axes"
private const val Description =
    "Animate a variable font in real time or drag its sliders while reading each current value."
private const val UnsupportedMessage =
    "Android requires API 26 or newer for runtime font variations."
private const val AnimationDurationMillis = 1_500
