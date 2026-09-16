package io.github.hlcaptain.symbols.sample.api

import androidx.compose.runtime.Composable

sealed class SampleEntry {
    abstract val title: String

    @Composable
    abstract fun Content(onItemClick: (SampleItem) -> Unit)
}

data class SampleList(
    val items: List<SampleItem>,
    private val content: @Composable ((SampleItem) -> Unit) -> Unit,
) : SampleEntry() {
    override val title: String = "Symbols samples"

    @Composable
    override fun Content(onItemClick: (SampleItem) -> Unit) {
        content(onItemClick)
    }
}

data class SampleItem(
    val id: String,
    override val title: String,
    val description: String,
    val modulePath: String,
    val availability: SampleAvailability = SampleAvailability.Available,
    private val content: @Composable () -> Unit,
) : SampleEntry() {
    init {
        require(id.isNotBlank()) { "Sample id must not be blank" }
        require(title.isNotBlank()) { "Sample title must not be blank" }
        require(description.isNotBlank()) { "Sample description must not be blank" }
        require(modulePath.startsWith(":")) {
            "Sample module path must be an absolute Gradle path: $modulePath"
        }
    }

    @Composable
    override fun Content(onItemClick: (SampleItem) -> Unit) {
        content()
    }
}

sealed interface SampleAvailability {
    data object Available : SampleAvailability

    data class Unavailable(val reason: String) : SampleAvailability {
        init {
            require(reason.isNotBlank()) { "Unavailable sample reason must not be blank" }
        }
    }
}
