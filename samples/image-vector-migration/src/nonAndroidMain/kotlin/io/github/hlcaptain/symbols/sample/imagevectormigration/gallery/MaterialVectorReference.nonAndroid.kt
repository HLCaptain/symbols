package io.github.hlcaptain.symbols.sample.imagevectormigration.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import io.github.hlcaptain.image_vector_migration.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToSvgPainter

@Composable
@OptIn(ExperimentalResourceApi::class)
internal actual fun galleryReferencePainters(page: MaterialGalleryPage): List<Painter> {
    val documents by produceState(emptyList<String>(), page.resourceName) {
        value = galleryGlyphSvgDocuments(Res.readBytes("drawable/${page.resourceName}.svg").decodeToString())
    }
    val density = LocalDensity.current
    return remember(documents, density) { documents.map { it.encodeToByteArray().decodeToSvgPainter(density) } }
}

internal actual val galleryReferenceFormat: String get() = "SVG"
