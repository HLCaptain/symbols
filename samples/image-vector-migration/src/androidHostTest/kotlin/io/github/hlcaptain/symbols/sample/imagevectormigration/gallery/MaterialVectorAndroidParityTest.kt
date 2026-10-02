package io.github.hlcaptain.symbols.sample.imagevectormigration.gallery

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w720dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaterialVectorAndroidParityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allFixedVectorsMatchXmlReferencePages() {
        val pages = MaterialGalleryPageProvider().values.toList()
        var selected by mutableStateOf(pages.first())
        compose.setContent { MaterialTheme { key(selected) { MaterialVectorGalleryPage(selected) } } }
        for (page in pages) {
            compose.runOnIdle { selected = page }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onNodeWithTag("gallery-reference").fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "Ready"
            }
            assertGalleryPixelsEqual(
                page,
                compose.onNodeWithTag("gallery-candidate").captureToImage(),
                compose.onNodeWithTag("gallery-reference").captureToImage(),
            )
        }
    }
}
