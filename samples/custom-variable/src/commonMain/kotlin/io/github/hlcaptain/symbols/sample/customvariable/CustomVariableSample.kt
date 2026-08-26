package io.github.hlcaptain.symbols.sample.customvariable

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.sample.api.SampleItem
import io.github.hlcaptain.symbols.sample.customvariable.generated.Academmunicons
import io.github.hlcaptain.symbols.sample.customvariable.generated.semibold.Orcid
import io.github.hlcaptain.symbols.sample.customvariable.config.SampleBuildConfig
import io.github.hlcaptain.symbols.sample.ui.ExampleCard
import io.github.hlcaptain.symbols.sample.ui.SamplePage
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single

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
    SamplePage(
        description = Description,
    ) {
        ExampleCard(
            title = "Fixed semibold snapshot",
            description =
                "axis(...) selects the instance before the typed ImageVector is generated.",
        ) {
            Icon(
                imageVector = Symbols.Academmunicons.Semibold.Orcid,
                contentDescription = "ORCID",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

private const val Title = "Variable custom symbols"
private const val Description =
    "Academmunicons is converted at ital=0 and wght=600 during the build."
