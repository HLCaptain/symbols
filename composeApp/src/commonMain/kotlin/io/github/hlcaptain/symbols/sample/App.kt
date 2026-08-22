package io.github.hlcaptain.symbols.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.github.hlcaptain.symbols.material.Icons
import io.github.hlcaptain.symbols.material.rounded.vectors.ArrowBack
import io.github.hlcaptain.symbols.sample.api.SampleEntry
import io.github.hlcaptain.symbols.sample.api.SampleItem
import io.github.hlcaptain.symbols.sample.api.SampleList
import io.github.hlcaptain.symbols.sample.ui.PreviewSymbolsScreen
import io.github.hlcaptain.symbols.sample.ui.SampleBrowser
import org.koin.compose.KoinApplication
import org.koin.compose.getKoin
import org.koin.core.annotation.KoinApplication as KoinApplicationDefinition
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.plugin.module.dsl.koinConfiguration

@KoinApplicationDefinition
internal class SymbolsSampleKoinApplication

@Composable
fun App() {
    val koinConfiguration = remember {
        koinConfiguration<SymbolsSampleKoinApplication> {}
    }

    KoinApplication(configuration = koinConfiguration) {
        SampleLauncher()
    }
}

@OptIn(ExperimentalMaterial3Api::class, KoinExperimentalAPI::class)
@Composable
private fun SampleLauncher() {
    val koin = getKoin()
    val items = remember(koin) { koin.getAll<SampleItem>().sortedBy(SampleItem::title) }
    val sampleList = remember(items) {
        SampleList(items) { onItemClick ->
            SampleBrowser(items = items, onItemClick = onItemClick)
        }
    }
    val backStack = remember(sampleList) { mutableStateListOf<SampleEntry>(sampleList) }
    val canNavigateBack = backStack.size > 1

    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(backStack.last().title) },
                    navigationIcon = {
                        if (canNavigateBack) {
                            IconButton(onClick = { backStack.removeLastOrNull() }) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            NavDisplay(
                backStack = backStack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                onBack = {
                    if (canNavigateBack) backStack.removeLastOrNull()
                },
                entryProvider = { entry ->
                    NavEntry(entry) {
                        entry.Content(onItemClick = { backStack.add(it) })
                    }
                },
            )
        }
    }
}

@PreviewSymbolsScreen
@Composable
private fun LauncherPreview() {
    App()
}
