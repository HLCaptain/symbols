package io.github.hlcaptain.symbols.sample.imagevectormigration.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.MaterialSymbol
import io.github.hlcaptain.symbols.material.outlined.vectors.asOutlinedImageVector
import io.github.hlcaptain.symbols.material.rounded.vectors.asRoundedImageVector
import io.github.hlcaptain.symbols.material.sharp.vectors.asSharpImageVector
import io.github.hlcaptain.symbols.sample.ui.PreviewScreenshotBaseline

internal enum class MaterialGalleryKind(val resourceStyle: String, val fill: Int) {
    Outlined("outlined", 0),
    Rounded("rounded", 0),
    Sharp("sharp", 0),
    RoundedFilled("rounded", 1),
}

internal val gallerySymbols: List<MaterialSymbol> =
    Symbols.Material.all.distinctBy { it.codePoint }.sortedBy { it.codePoint }

internal data class MaterialGalleryPage(val kind: MaterialGalleryKind, val number: Int) {
    init { require(number in 0 until GalleryPagesPerKind) }
    val start: Int get() = number * GalleryPageSize
    val symbols: List<MaterialSymbol> get() = gallerySymbols.drop(start).take(GalleryPageSize)
    val resourceName: String get() = "material_gallery_${kind.resourceStyle}_fill${kind.fill}_${number.toString().padStart(3, '0')}"
    override fun toString(): String = "${kind.name}_page_${number.toString().padStart(3, '0')}"

    fun vector(index: Int): ImageVector = when (kind) {
        MaterialGalleryKind.Outlined -> gallerySymbols[index].asOutlinedImageVector()
        MaterialGalleryKind.Rounded -> gallerySymbols[index].asRoundedImageVector()
        MaterialGalleryKind.Sharp -> gallerySymbols[index].asSharpImageVector()
        MaterialGalleryKind.RoundedFilled -> filledGalleryVector(index)
    }
}

internal class MaterialGalleryPageProvider : PreviewParameterProvider<MaterialGalleryPage> {
    override val values: Sequence<MaterialGalleryPage>
        get() = MaterialGalleryKind.entries.asSequence().flatMap { kind ->
            (0 until GalleryPagesPerKind).asSequence().map { MaterialGalleryPage(kind, it) }
        }
}

@Composable
internal expect fun galleryReferencePainters(page: MaterialGalleryPage): List<Painter>

internal expect val galleryReferenceFormat: String

@Composable
internal fun MaterialVectorGalleryPage(page: MaterialGalleryPage) = key(page) {
    val painters = galleryReferencePainters(page)
    Column(Modifier.background(Color.White).padding(12.dp)) {
        Text("${page.kind.name} · FILL=${page.kind.fill} · page ${page.number + 1}/$GalleryPagesPerKind", color = Color.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("ImageVector", color = Color.Black)
                Box(Modifier.requiredSize(GalleryWidth.dp, GalleryHeight.dp).testTag("gallery-candidate")) {
                    page.symbols.forEachIndexed { position, _ ->
                        Icon(
                            imageVector = page.vector(page.start + position),
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.offset(
                                (position % GalleryColumns * GalleryCellSize + GalleryIconX).dp,
                                (position / GalleryColumns * GalleryCellSize + GalleryIconY).dp,
                            ).size(24.dp),
                        )
                    }
                    CodePointLabels(page)
                }
            }
            Column {
                Text("$galleryReferenceFormat reference", color = Color.Black)
                Box(
                    Modifier.requiredSize(GalleryWidth.dp, GalleryHeight.dp)
                        .testTag("gallery-reference")
                        .semantics { stateDescription = if (painters.size == page.symbols.size) "Ready" else "Loading" },
                ) {
                    painters.forEachIndexed { position, painter ->
                        Image(
                            painter,
                            contentDescription = null,
                            modifier = Modifier.offset(
                                (position % GalleryColumns * GalleryCellSize + GalleryIconX).dp,
                                (position / GalleryColumns * GalleryCellSize + GalleryIconY).dp,
                            ).size(24.dp),
                        )
                    }
                    CodePointLabels(page)
                }
            }
        }
    }
}

@Composable
private fun CodePointLabels(page: MaterialGalleryPage) {
    page.symbols.forEachIndexed { position, symbol ->
        Text(
            symbol.codePoint.toString(16).uppercase(),
            fontSize = 8.sp,
            lineHeight = 10.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(
                (position % GalleryColumns * GalleryCellSize).dp,
                (position / GalleryColumns * GalleryCellSize + 28).dp,
            ).size(GalleryCellSize.dp, 12.dp),
        )
    }
}

@PreviewScreenshotBaseline
@Preview(
    name = "All fixed vectors",
    widthDp = 688,
    heightDp = 736,
    device = "spec:width=688dp,height=736dp,dpi=160",
    showBackground = true,
)
@Composable
private fun MaterialVectorGalleryPreview(@PreviewParameter(MaterialGalleryPageProvider::class) page: MaterialGalleryPage) {
    MaterialTheme { MaterialVectorGalleryPage(page) }
}
