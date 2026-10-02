package io.github.hlcaptain.symbols.sample.imagevectormigration.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.image_vector_migration.generated.resources.Res
import io.github.hlcaptain.image_vector_migration.generated.resources.allDrawableResources
import org.jetbrains.compose.resources.vectorResource

@Composable
internal actual fun galleryReferencePainters(page: MaterialGalleryPage): List<Painter> {
    val sheet = vectorResource(Res.allDrawableResources.getValue(page.resourceName))
    val glyphs = remember(sheet) {
        (0 until sheet.root.size).map { index ->
            val group = sheet.root[index] as VectorGroup
            require(group.size == 1) { "Reference glyph must contain one path" }
            val path = group[0] as VectorPath
            // Match a single Icon's viewport/cache, without altering its reference outline.
            ImageVector.Builder("XML reference $index", 24.dp, 24.dp, 24f, 24f)
                .addPath(path.pathData, pathFillType = path.pathFillType, fill = path.fill)
                .build()
        }
    }
    return glyphs.map { rememberVectorPainter(it) }
}

internal actual val galleryReferenceFormat: String get() = "XML"
