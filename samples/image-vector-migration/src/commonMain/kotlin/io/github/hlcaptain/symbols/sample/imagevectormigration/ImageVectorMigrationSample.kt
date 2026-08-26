package io.github.hlcaptain.symbols.sample.imagevectormigration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons as MaterialIcons
import androidx.compose.material.icons.rounded.AccountTree as MaterialAccountTree
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.image_vector_migration.generated.resources.Res
import io.github.hlcaptain.image_vector_migration.generated.resources.academmunicons_default_orcid_uf04f
import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.material.Material
import io.github.hlcaptain.symbols.material.Rounded
import io.github.hlcaptain.symbols.material.rounded.vectors.AccountTree as SymbolsAccountTree
import io.github.hlcaptain.symbols.sample.api.SampleItem
import io.github.hlcaptain.symbols.sample.imagevectormigration.config.SampleBuildConfig
import io.github.hlcaptain.symbols.sample.imagevectormigration.generated.Academmunicons
import io.github.hlcaptain.symbols.sample.imagevectormigration.generated.default.Orcid
import io.github.hlcaptain.symbols.sample.ui.ExampleCard
import io.github.hlcaptain.symbols.sample.ui.PreviewScreenshotBaseline
import io.github.hlcaptain.symbols.sample.ui.PreviewSymbolsScreen
import io.github.hlcaptain.symbols.sample.ui.SamplePage
import org.jetbrains.compose.resources.painterResource
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single

@Module
@Configuration
class ImageVectorMigrationNavigationModule {
    @Qualifier(ImageVectorMigrationNavigationModule::class)
    @Single
    fun sampleItem() = SampleItem(
        id = "image-vector-migration",
        title = Title,
        description = Description,
        modulePath = SampleBuildConfig.MODULE_PATH,
        content = { ImageVectorMigrationContent() },
    )
}

@Composable
private fun ImageVectorMigrationContent() {
    SamplePage(description = Description) {
        ExampleCard(
            title = "ImageVector: old and new",
            description = "The old Material Icons Extended and generated Symbols APIs can use " +
                "the same Material3 Icon call site.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                VectorExample("Old · Extended", MaterialIcons.Rounded.MaterialAccountTree)
                VectorExample("New · Symbols", Symbols.Material.Rounded.SymbolsAccountTree)
            }
            Text(
                text = "Old: MaterialIcons.Rounded.AccountTree\n" +
                    "New: Symbols.Material.Rounded.AccountTree",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        ExampleCard(
            title = "Custom font vectors and painters",
            description = "With no axis(...) calls, the variable font uses its embedded " +
                "defaults: ital=0 and wght=100.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                VectorExample("ImageVector", Symbols.Academmunicons.Default.Orcid)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            Res.drawable.academmunicons_default_orcid_uf04f,
                        ),
                        contentDescription = "ORCID generated drawable",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Text("Painter", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                text = "Both are build-time snapshots, so SymbolsTheme axis changes do not " +
                    "reshape them. MaterialTheme tint still updates normally.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SvgIconExamples()
        SharedWeightAxisExample()
    }
}

@Composable
private fun VectorExample(
    label: String,
    imageVector: ImageVector,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@PreviewScreenshotBaseline
@PreviewSymbolsScreen
@Composable
private fun ImageVectorMigrationPreview() {
    MaterialTheme {
        ImageVectorMigrationContent()
    }
}

private const val Title = "Migrate from Material Icons Extended"
private const val Description =
    "Keep Material3 Icon while replacing ImageVector or painter inputs with generated symbols."
