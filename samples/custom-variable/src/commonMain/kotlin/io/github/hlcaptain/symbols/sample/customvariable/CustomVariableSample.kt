package io.github.hlcaptain.symbols.sample.customvariable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.custom_variable.generated.resources.AcademmuniconsVariable
import io.github.hlcaptain.custom_variable.generated.resources.Res
import io.github.hlcaptain.custom_variable.generated.resources.academmunicons_semibold_orcid_uf04f
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.font.SymbolFont
import io.github.hlcaptain.symbols.font.SymbolFontAxis
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolsRuntime
import io.github.hlcaptain.symbols.font.SymbolsTheme
import io.github.hlcaptain.symbols.font.fontSettings
import io.github.hlcaptain.symbols.sample.api.SampleItem
import io.github.hlcaptain.symbols.sample.customvariable.generated.Academmunicons
import io.github.hlcaptain.symbols.sample.customvariable.generated.semibold.Orcid
import io.github.hlcaptain.symbols.sample.customvariable.config.SampleBuildConfig
import io.github.hlcaptain.symbols.sample.ui.AxisControls
import io.github.hlcaptain.symbols.sample.ui.AxisUiModel
import io.github.hlcaptain.symbols.sample.ui.ExampleCard
import io.github.hlcaptain.symbols.sample.ui.SamplePage
import io.github.hlcaptain.symbols.sample.ui.StatusMessage
import kotlin.math.roundToInt
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single
import org.jetbrains.compose.resources.painterResource

@Module
@Configuration
class CustomVariableNavigationModule {
    @Qualifier(CustomVariableNavigationModule::class)
    @Single
    fun sampleItem() = SampleItem(
        id = "custom-variable",
        title = Title,
        description = Description,
        modulePath = SampleBuildConfig.MODULE_PATH,
        content = { CustomVariableContent() },
    )
}

@Composable
private fun CustomVariableContent() {
    val font = Symbols.AcademmuniconsVariable
    val axisValues = remember(font) {
        font.variationAxes.associateTo(mutableStateMapOf()) { axis ->
            axis.tag to axis.defaultValue
        }
    }
    val axes = font.variationAxes.map { axis ->
        val value = axisValues.getValue(axis.tag)
        AxisUiModel(
            tag = axis.tag,
            label = axis.displayLabel,
            value = value,
            minValue = axis.minValue,
            maxValue = axis.maxValue,
            valueLabel = axis.format(value),
            steps = 0,
        )
    }
    SamplePage(
        description = Description,
    ) {
        ExampleCard(
            title = "Custom symbol generated at build time",
            description = "The Gradle axis(...) settings select ital=0 and wght=600 before " +
                "generating the fixed ImageVector and Res.drawable painter.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Icon(
                    imageVector = Symbols.Academmunicons.Semibold.Orcid,
                    contentDescription = "ORCID ImageVector",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Icon(
                    painter = painterResource(
                        Res.drawable.academmunicons_semibold_orcid_uf04f,
                    ),
                    contentDescription = "ORCID Compose drawable",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        if (SymbolsRuntime.variableFontsSupported) {
            RuntimeAcademmuniconsPreview(font, axisValues)
            AxisControls(
                axes = axes,
                onValueChange = { tag, value -> axisValues[tag] = value },
                onReset = {
                    font.variationAxes.forEach { axis ->
                        axisValues[axis.tag] = axis.defaultValue
                    }
                },
                resetEnabled = font.variationAxes.any { axis ->
                    axisValues[axis.tag] != axis.defaultValue
                },
                title = "Custom runtime font axes",
            )
        } else {
            StatusMessage(
                "The generated custom ImageVector works here, but live variable fonts require " +
                    "Android API 26 or newer.",
            )
        }
    }
}

@Composable
private fun RuntimeAcademmuniconsPreview(
    font: SymbolFont.Variable,
    axisValues: Map<String, Float>,
) {
    SymbolsTheme(fontSettings = font.fontSettings(axisValues)) {
        ExampleCard(
            title = "Custom runtime variable font",
            description = "The original font follows the live shape and weight controls without " +
                "regenerating the fixed resources above.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                SymbolFontIcon(
                    codePoint = OrcidCodePoint,
                    font = font,
                    contentDescription = "ORCID",
                    tint = MaterialTheme.colorScheme.primary,
                    size = 64.dp,
                )
            }
        }
    }
}

private val SymbolFontAxis.displayLabel: String
    get() = when (tag) {
        "ital" -> "Shape"
        "wght" -> "Weight"
        else -> label
    }

private fun SymbolFontAxis.format(value: Float): String = when (tag) {
    "ital" -> when {
        value <= minValue -> "Round"
        value >= maxValue -> "Square"
        else -> "${(value * 100).roundToInt()}%"
    }
    else -> value.roundToInt().toString()
}

private const val Title = "Custom symbols: build-time vs runtime"
private const val Description =
    "Compare a custom Academmunicons symbol generated at fixed build-time axes with the same " +
        "variable font adjusted live at runtime."
private const val OrcidCodePoint = 0xF04F
