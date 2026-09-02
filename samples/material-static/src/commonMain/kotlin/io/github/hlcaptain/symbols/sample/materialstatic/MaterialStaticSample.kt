package io.github.hlcaptain.symbols.sample.materialstatic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.material.Favorite
import io.github.hlcaptain.symbols.material.Home
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.MaterialSymbol
import io.github.hlcaptain.symbols.material.Search
import io.github.hlcaptain.symbols.material.rounded.compose.drawables.resources.Res as RoundedDrawablesRes
import io.github.hlcaptain.symbols.material.rounded.compose.drawables.resources.material_symbols_rounded_home_ue9b2
import io.github.hlcaptain.symbols.material.rounded.staticfont.MaterialSymbolsRoundedStatic
import io.github.hlcaptain.symbols.sample.api.SampleItem
import io.github.hlcaptain.symbols.sample.materialstatic.config.SampleBuildConfig
import io.github.hlcaptain.symbols.sample.ui.ExampleCard
import io.github.hlcaptain.symbols.sample.ui.SamplePage
import org.jetbrains.compose.resources.painterResource
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
                StaticSymbol("Home", Symbols.Material.Home)
                StaticSymbol("Search", Symbols.Material.Search)
                StaticSymbol("Favorite", Symbols.Material.Favorite)
            }
        }
        ExampleCard(
            title = "Compose drawable resource",
            description = "The complete Rounded pack exposes standard public Res.drawable " +
                "accessors on every Compose Multiplatform target.",
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(
                        RoundedDrawablesRes.drawable.material_symbols_rounded_home_ue9b2,
                    ),
                    contentDescription = "Home Compose drawable",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
                Text("Res.drawable", style = MaterialTheme.typography.labelMedium)
            }
            Text(
                text = "painterResource(Res.drawable.material_symbols_rounded_home_ue9b2)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
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
    "Compare a regular Material Symbols font with a standard Compose drawable resource."
