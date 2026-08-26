package io.github.hlcaptain.symbols.sample.theming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolsTheme
import io.github.hlcaptain.symbols.material.Favorite
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.rounded.staticfont.MaterialSymbolsRoundedStatic
import io.github.hlcaptain.symbols.sample.api.SampleItem
import io.github.hlcaptain.symbols.sample.theming.config.SampleBuildConfig
import io.github.hlcaptain.symbols.sample.ui.ExampleCard
import io.github.hlcaptain.symbols.sample.ui.SamplePage
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single

@Module
@Configuration
class ThemingNavigationModule {
    @Qualifier(ThemingNavigationModule::class)
    @Single
    fun sampleItem() = SampleItem(
        id = "theming",
        title = Title,
        description = Description,
        modulePath = SampleBuildConfig.MODULE_PATH,
        content = { ThemingContent() },
    )
}

@Composable
private fun ThemingContent() {
    val colors = MaterialTheme.colorScheme.copy(
        primary = Color(0xFF6D28D9),
        primaryContainer = Color(0xFFEDE9FE),
        onPrimaryContainer = Color(0xFF2E1065),
    )

    MaterialTheme(colorScheme = colors) {
        SymbolsTheme(fontSettings = MaterialSymbolsRoundedStatic.fontSettings) {
            SamplePage(description = Description) {
                ExampleCard(
                    title = "Two themes, one component tree",
                    description = "The icon inherits its font settings from SymbolsTheme and " +
                        "its color from MaterialTheme.",
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SymbolFontIcon(
                                codePoint = Symbols.Material.Favorite.codePoint,
                                font = MaterialSymbolsRoundedStatic,
                                contentDescription = "Favorite",
                                tint = MaterialTheme.colorScheme.primary,
                                size = 52.dp,
                            )
                            Text(
                                text = "Inherited color and font settings",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val Title = "MaterialTheme and SymbolsTheme"
private const val Description =
    "Material colors and symbol-font settings flow through independent themes."
