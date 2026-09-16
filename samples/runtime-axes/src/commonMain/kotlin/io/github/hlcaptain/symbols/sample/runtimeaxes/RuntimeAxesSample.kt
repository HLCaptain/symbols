package io.github.hlcaptain.symbols.sample.runtimeaxes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.font.SymbolFontAxis
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolsRuntime
import io.github.hlcaptain.symbols.font.fontSettings
import io.github.hlcaptain.symbols.material.Favorite
import io.github.hlcaptain.symbols.material.Home
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.Rounded
import io.github.hlcaptain.symbols.material.Search
import io.github.hlcaptain.symbols.material.Settings
import io.github.hlcaptain.symbols.material.font
import io.github.hlcaptain.symbols.sample.api.SampleAvailability
import io.github.hlcaptain.symbols.sample.api.SampleItem
import io.github.hlcaptain.symbols.sample.runtimeaxes.config.SampleBuildConfig
import io.github.hlcaptain.symbols.sample.ui.AxisControls
import io.github.hlcaptain.symbols.sample.ui.AxisUiModel
import io.github.hlcaptain.symbols.sample.ui.ExampleCard
import io.github.hlcaptain.symbols.sample.ui.SamplePage
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
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
    SamplePage(description = Description) {
        RuntimeMaterialFontPreview(axisValues)
        RuntimeAxisControls(fontAxes, axisValues)
        CombinedEffectsCard()
    }
}

@Composable
private fun RuntimeAxisControls(
    fontAxes: List<SymbolFontAxis>,
    axisValues: SnapshotStateMap<String, Float>,
) {
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

    val onValueChange = remember(axisValues) {
        { tag: String, value: Float -> axisValues[tag] = value }
    }
    val onReset = remember(fontAxes, axisValues) {
        { fontAxes.forEach { axis -> axisValues[axis.tag] = axis.defaultValue } }
    }
    AxisControls(
        axes = axes,
        onValueChange = onValueChange,
        onReset = onReset,
        resetEnabled = fontAxes.any { axis ->
            axisValues[axis.tag] != axis.defaultValue
        },
        title = "Runtime Material font axes",
    )
}

@Composable
private fun RuntimeMaterialFontPreview(axisValues: SnapshotStateMap<String, Float>) {
    val font = Symbols.Material.Rounded.font
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
                fontSettings = { font.fontSettings(axisValues) },
                tint = MaterialTheme.colorScheme.primary,
                size = 72.dp,
            )
            RuntimeAxisOverrides(axisValues)
        }
    }
}

@Composable
private fun RuntimeAxisOverrides(axisValues: SnapshotStateMap<String, Float>) {
    val overrides = Symbols.Material.Rounded.font.variationAxes.filter { axis ->
        axisValues[axis.tag] != axis.defaultValue
    }
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

@Composable
private fun CombinedEffectsCard() {
    var running by remember { mutableStateOf(false) }
    val phase = if (running) {
        rememberInfiniteTransition(label = "Combined symbol effects").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3_600, easing = LinearEasing)),
            label = "Row phase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    ExampleCard(
        title = "Combined effects",
        description = "Weight, fill, grade, optical size, tint, and size change as each row " +
            "fades, scales, rotates, and moves.",
    ) {
        Column(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            repeat(4) { row -> CombinedEffectsRow(row, phase) }
        }
        TextButton(
            onClick = { running = !running },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(if (running) "Stop" else "Run")
        }
    }
}

@Composable
private fun CombinedEffectsRow(row: Int, phase: State<Float>) {
    val font = Symbols.Material.Rounded.font
    val offset = row / 4f
    val fraction = combinedEffectsFraction(phase.value + offset)
    val tint = lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, fraction)
    val size = lerp(16.dp, 24.dp, fraction)
    val codePoints = remember {
        listOf(Symbols.Material.Search, Symbols.Material.Home, Symbols.Material.Favorite, Symbols.Material.Settings)
            .map { it.codePoint }
    }
    Row {
        codePoints.forEach { codePoint ->
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                SymbolFontIcon(
                    codePoint = codePoint,
                    font = font,
                    contentDescription = null,
                    fontSettings = {
                        val value = combinedEffectsFraction(phase.value + offset)
                        font.fontSettings(font.variationAxes.associate { axis ->
                            axis.tag to lerp(axis.minValue, axis.maxValue, value)
                        })
                    },
                    tint = tint,
                    size = size,
                    graphicsLayer = {
                        val value = combinedEffectsFraction(phase.value + offset)
                        alpha = lerp(0.4f, 1f, value)
                        scaleX = lerp(0.8f, 1f, value)
                        scaleY = scaleX
                        rotationZ = lerp(-12f, 12f, value)
                        translationX = lerp((-2).dp, 2.dp, value).toPx()
                        translationY = lerp(2.dp, (-2).dp, value).toPx()
                    },
                )
            }
        }
    }
}

private fun combinedEffectsFraction(phase: Float): Float =
    ((sin(phase * 2.0 * PI) + 1.0) / 2.0).toFloat()

private fun SymbolFontAxis.format(value: Float): String = when (tag) {
    "FILL" -> "${(value * 100).roundToInt()}%"
    else -> value.roundToInt().toString()
}

private const val Title = "Runtime Material variable-font axes"
private const val Description =
    "Animate or drag each Material Symbols axis at runtime and read its live value."
private const val UnsupportedMessage =
    "Android requires API 26 or newer for runtime font variations."
