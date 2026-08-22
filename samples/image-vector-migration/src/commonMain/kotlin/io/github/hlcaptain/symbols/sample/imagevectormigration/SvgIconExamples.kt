package io.github.hlcaptain.symbols.sample.imagevectormigration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.image_vector_migration.generated.resources.Res
import io.github.hlcaptain.image_vector_migration.generated.resources.tabler_outline_hierarchy_2
import io.github.hlcaptain.symbols.font.SymbolFontIcon
import io.github.hlcaptain.symbols.font.SymbolFontSettings
import io.github.hlcaptain.symbols.font.SymbolsRuntime
import io.github.hlcaptain.symbols.font.SymbolsTheme
import io.github.hlcaptain.symbols.font.rememberSymbolPainter
import io.github.hlcaptain.symbols.material.AccountTree
import io.github.hlcaptain.symbols.material.MaterialSymbols
import io.github.hlcaptain.symbols.material.rounded.MaterialSymbolsRounded
import io.github.hlcaptain.symbols.sample.imagevectormigration.generated.Tabler
import io.github.hlcaptain.symbols.sample.imagevectormigration.generated.outline.Hierarchy2
import io.github.hlcaptain.symbols.sample.imagevectormigration.generated.outline.Home
import io.github.hlcaptain.symbols.sample.imagevectormigration.generated.outline.Settings
import io.github.hlcaptain.symbols.sample.ui.AxisControls
import io.github.hlcaptain.symbols.sample.ui.AxisUiModel
import io.github.hlcaptain.symbols.sample.ui.ExampleCard
import io.github.hlcaptain.symbols.sample.ui.PreviewScreenshotBaseline
import io.github.hlcaptain.symbols.sample.ui.PreviewSymbolsScreen
import io.github.hlcaptain.symbols.sample.ui.SamplePage
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SvgIconExamples() {
    ExampleCard(
        title = "SVG vectors, painters, and drawables",
        description = "One source directory generates typed ImageVectors and XML resources. " +
            "The theme painter keeps the authored Tabler stroke adjustable.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            VectorExample("ImageVector", Tabler.Outline.Home)
            PainterExample("Theme painter", Tabler.Outline.Settings.rememberSymbolPainter())
            PainterExample(
                "XML painter",
                painterResource(Res.drawable.tabler_outline_hierarchy_2),
            )
        }
    }
}

@Composable
internal fun SharedWeightAxisExample() {
    var weight by remember { mutableFloatStateOf(DefaultWeight) }
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(isAnimating) {
        if (!isAnimating) return@LaunchedEffect

        val animatedWeight = Animatable(weight)
        var target = if (weight < MaxWeight) MaxWeight else MinWeight
        while (true) {
            animatedWeight.animateTo(
                targetValue = target,
                animationSpec = tween(AnimationDurationMillis, easing = LinearEasing),
            ) {
                weight = value
            }
            target = if (target == MaxWeight) MinWeight else MaxWeight
        }
    }

    val fontSettings = remember(weight.roundToInt()) {
        SymbolFontSettings(
            FontVariation.Settings(FontVariation.weight(weight.roundToInt())),
        )
    }
    SymbolsTheme(fontSettings = fontSettings) {
        ExampleCard(
            title = "One weight API",
            description = "The same SymbolsTheme wght value updates a variable font and " +
                "the SVG painter's stroke width.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (SymbolsRuntime.variableFontsSupported) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SymbolFontIcon(
                            codePoint = MaterialSymbols.AccountTree.codePoint,
                            font = MaterialSymbolsRounded,
                            contentDescription = "Material font Account tree",
                            tint = MaterialTheme.colorScheme.primary,
                            size = 64.dp,
                        )
                        Text("Font glyph", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Text(
                        text = "Variable font unavailable",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                PainterExample(
                    label = "SVG painter",
                    painter = Tabler.Outline.Hierarchy2.rememberSymbolPainter(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { isAnimating = !isAnimating }) {
                    Text(if (isAnimating) "Stop" else "Animate")
                }
                Text(
                    text = "wght=${weight.roundToInt()} · SVG stroke=${strokeWidth(weight)}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
    AxisControls(
        axes = listOf(
            AxisUiModel(
                tag = "wght",
                label = "Weight",
                value = weight,
                minValue = MinWeight,
                maxValue = MaxWeight,
                valueLabel = weight.roundToInt().toString(),
            ),
        ),
        onValueChange = { _, value ->
            isAnimating = false
            weight = value
        },
        onReset = {
            isAnimating = false
            weight = DefaultWeight
        },
        resetEnabled = isAnimating || weight != DefaultWeight,
        title = "Shared weight / stroke control",
    )
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
            modifier = Modifier.size(48.dp),
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PainterExample(
    label: String,
    painter: Painter,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painter,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun strokeWidth(weight: Float): Float =
    ((1f + (weight.coerceIn(MinWeight, MaxWeight) - MinWeight) / 300f) * 10f)
        .roundToInt() / 10f

@PreviewScreenshotBaseline
@PreviewSymbolsScreen
@Composable
private fun SvgIconExamplesPreview() {
    MaterialTheme {
        SamplePage {
            SvgIconExamples()
            SharedWeightAxisExample()
        }
    }
}

private const val MinWeight = 100f
private const val DefaultWeight = 400f
private const val MaxWeight = 700f
private const val AnimationDurationMillis = 1_500
