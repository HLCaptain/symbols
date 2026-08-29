package io.github.hlcaptain.symbols.sample.customstatic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.custom_static.generated.resources.Res
import io.github.hlcaptain.custom_static.generated.resources.powerline_icons_regular_branch_ue0a0
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.sample.api.SampleItem
import io.github.hlcaptain.symbols.sample.customstatic.generated.PowerlineIcons
import io.github.hlcaptain.symbols.sample.customstatic.generated.regular.Branch
import io.github.hlcaptain.symbols.sample.customstatic.config.SampleBuildConfig
import io.github.hlcaptain.symbols.sample.ui.ExampleCard
import io.github.hlcaptain.symbols.sample.ui.SamplePage
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single
import org.jetbrains.compose.resources.painterResource

@Module
@Configuration
class CustomStaticNavigationModule {
    @Qualifier(CustomStaticNavigationModule::class)
    @Single
    fun sampleItem() = SampleItem(
        id = "custom-static",
        title = Title,
        description = Description,
        modulePath = SampleBuildConfig.MODULE_PATH,
        content = { CustomStaticContent() },
    )
}

@Composable
private fun CustomStaticContent() {
    SamplePage(
        description = Description,
    ) {
        ExampleCard(
            title = "Typed vector and Compose resource",
            description = "The ImageVector is on the left; its standard Res.drawable painter " +
                "is on the right.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Icon(
                    imageVector = Symbols.PowerlineIcons.Regular.Branch,
                    contentDescription = "Branch ImageVector",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Icon(
                    painter = painterResource(
                        Res.drawable.powerline_icons_regular_branch_ue0a0,
                    ),
                    contentDescription = "Branch Compose drawable",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}

private const val Title = "Static custom symbols"
private const val Description =
    "Powerline is read directly from composeResources/font and converted at build time."
