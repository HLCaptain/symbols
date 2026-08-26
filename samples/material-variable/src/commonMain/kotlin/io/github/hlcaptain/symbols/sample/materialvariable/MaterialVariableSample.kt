package io.github.hlcaptain.symbols.sample.materialvariable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolsRuntime
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.Search
import io.github.hlcaptain.symbols.material.rounded.MaterialSymbolsRounded
import io.github.hlcaptain.symbols.sample.api.SampleAvailability
import io.github.hlcaptain.symbols.sample.api.SampleItem
import io.github.hlcaptain.symbols.sample.materialvariable.config.SampleBuildConfig
import io.github.hlcaptain.symbols.sample.ui.ExampleCard
import io.github.hlcaptain.symbols.sample.ui.SamplePage
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single

@Module
@Configuration
class MaterialVariableNavigationModule {
    @Qualifier(MaterialVariableNavigationModule::class)
    @Single
    fun sampleItem() = SampleItem(
        id = "material-variable",
        title = Title,
        description = Description,
        modulePath = SampleBuildConfig.MODULE_PATH,
        availability = if (SymbolsRuntime.variableFontsSupported) {
            SampleAvailability.Available
        } else {
            SampleAvailability.Unavailable(UnsupportedMessage)
        },
        content = { MaterialVariableContent() },
    )
}

@Composable
private fun MaterialVariableContent() {
    SamplePage(description = Description) {
        ExampleCard(
            title = "Default variable instance",
            description = MaterialSymbolsRounded.variationAxes.joinToString { axis ->
                "${axis.tag}=${axis.defaultValue}"
            },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SymbolFontIcon(
                    codePoint = Symbols.Material.Search.codePoint,
                    font = MaterialSymbolsRounded,
                    fontSettings = MaterialSymbolsRounded.defaultFontSettings,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    size = 56.dp,
                )
                Text(
                    text = MaterialSymbolsRounded.familyName,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private const val Title = "Variable Material Symbols"
private const val Description =
    "Use the bundled variable font at its embedded default axis values."
private const val UnsupportedMessage =
    "Android requires API 26 or newer for runtime font variations."
