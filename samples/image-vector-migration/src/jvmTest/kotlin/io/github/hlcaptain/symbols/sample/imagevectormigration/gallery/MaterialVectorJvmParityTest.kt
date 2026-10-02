package io.github.hlcaptain.symbols.sample.imagevectormigration.gallery

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test

class MaterialVectorJvmParityTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun allFixedVectorsMatchSvgReferencePages() = runDesktopComposeUiTest {
        val pages = MaterialGalleryPageProvider().values.toList()
        var selected by mutableStateOf(pages.first())
        setContent { MaterialTheme { key(selected) { MaterialVectorGalleryPage(selected) } } }
        for (page in pages) {
            runOnIdle { selected = page }
            waitUntil(timeoutMillis = 10_000) {
                onNodeWithTag("gallery-reference").fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "Ready"
            }
            assertGalleryPixelsEqual(
                page,
                onNodeWithTag("gallery-candidate").captureToImage(),
                onNodeWithTag("gallery-reference").captureToImage(),
            )
        }
    }
}
