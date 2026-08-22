package io.github.hlcaptain.symbols.sample.customstatic

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            title = "Generated ImageVectors",
            description = "The Gradle plugin generates direct, typed properties.",
        ) {
            Icon(
                imageVector = PowerlineIcons.Regular.Branch,
                contentDescription = "Branch",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

private const val Title = "Static custom symbols"
private const val Description =
    "Powerline is read directly from composeResources/font and converted at build time."
