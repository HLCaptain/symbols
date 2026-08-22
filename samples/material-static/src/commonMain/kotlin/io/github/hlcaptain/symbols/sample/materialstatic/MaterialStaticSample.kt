package io.github.hlcaptain.symbols.sample.materialstatic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.material.Favorite
import io.github.hlcaptain.symbols.material.Home
import io.github.hlcaptain.symbols.material.MaterialSymbol
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.Search
import io.github.hlcaptain.symbols.material.rounded.staticfont.MaterialSymbolsRoundedStatic
import io.github.hlcaptain.symbols.sample.api.SampleItem
import io.github.hlcaptain.symbols.sample.materialstatic.config.SampleBuildConfig
import io.github.hlcaptain.symbols.sample.ui.ExampleCard
import io.github.hlcaptain.symbols.sample.ui.SamplePage
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single

@Module
@Configuration
class MaterialStaticNavigationModule {
    @Qualifier(MaterialStaticNavigationModule::class)
    @Single
    fun sampleItem() = SampleItem(
        id = "material-static",
        title = Title,
        description = Description,
        modulePath = SampleBuildConfig.MODULE_PATH,
        content = { MaterialStaticContent() },
    )
}

@Composable
private fun MaterialStaticContent() {
    SamplePage(description = Description) {
        ExampleCard(
            title = "One regular font",
            description = "The bundled Rounded font is fixed at its default axes and needs no " +
                "runtime variable-font support.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StaticSymbol("Home", MaterialSymbols.Home)
                StaticSymbol("Search", MaterialSymbols.Search)
                StaticSymbol("Favorite", MaterialSymbols.Favorite)
            }
        }
    }
}

@Composable
private fun StaticSymbol(label: String, symbol: MaterialSymbol) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SymbolFontIcon(
            codePoint = symbol.codePoint,
            font = MaterialSymbolsRoundedStatic,
            fontSettings = MaterialSymbolsRoundedStatic.fontSettings,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            size = 44.dp,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private const val Title = "Static Material Symbols"
private const val Description =
    "Render the regular Material Symbols font on Android API 21 and newer."
