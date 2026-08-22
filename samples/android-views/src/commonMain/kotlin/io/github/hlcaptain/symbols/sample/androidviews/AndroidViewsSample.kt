package io.github.hlcaptain.symbols.sample.androidviews

import androidx.compose.runtime.Composable
import io.github.hlcaptain.symbols.sample.androidviews.config.SampleBuildConfig
import io.github.hlcaptain.symbols.sample.api.SampleAvailability
import io.github.hlcaptain.symbols.sample.api.SampleItem
import io.github.hlcaptain.symbols.sample.ui.AvailabilityStatus
import io.github.hlcaptain.symbols.sample.ui.SamplePage
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single

@Module
@Configuration
class AndroidViewsNavigationModule {
    @Qualifier(AndroidViewsNavigationModule::class)
    @Single
    fun sampleItem() = SampleItem(
        id = "android-views",
        title = Title,
        description = Description,
        modulePath = SampleBuildConfig.MODULE_PATH,
        content = { AndroidViewsSample() },
    )
}

@Composable
private fun AndroidViewsSample() {
    SamplePage(description = Description) {
        AndroidViewsPlatformContent()
    }
}

@Composable
internal expect fun AndroidViewsPlatformContent()

@Composable
internal fun AndroidViewsFallback() {
    AvailabilityStatus(
        SampleAvailability.Unavailable(
            "This target is not Android, so Compose-View interop is unavailable.",
        ),
    )
}

private const val Title = "Android XML Views"
private const val Description =
    "Compare downloaded and generated XML drawables through Compose-View interop."
