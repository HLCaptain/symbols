package io.github.hlcaptain.symbols.sample

import io.github.hlcaptain.symbols.sample.api.SampleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.plugin.module.dsl.koinApplication

class SampleRegistryTest {
    @Test
    @OptIn(KoinExperimentalAPI::class)
    fun compilerCollectsFeatureNavigationEntries() {
        val application = koinApplication<SymbolsSampleKoinApplication>()

        try {
            val items = application.koin.getAll<SampleItem>()

            assertEquals(8, items.size)
            assertEquals(
                setOf(
                    ":samples:material-static",
                    ":samples:material-variable",
                    ":samples:custom-static",
                    ":samples:custom-variable",
                    ":samples:image-vector-migration",
                    ":samples:android-views",
                    ":samples:theming",
                    ":samples:runtime-axes",
                ),
                items.mapTo(mutableSetOf(), SampleItem::modulePath),
            )
            assertEquals(items.size, items.mapTo(mutableSetOf(), SampleItem::id).size)
        } finally {
            application.close()
        }
    }
}
