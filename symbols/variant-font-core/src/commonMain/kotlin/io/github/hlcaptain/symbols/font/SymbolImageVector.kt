package io.github.hlcaptain.symbols.font

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.RenderVectorGroup
import androidx.compose.ui.graphics.vector.VectorConfig
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.VectorProperty
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/** Remembers this vector with stroked paths weighted by the current [SymbolsTheme]. */
@Composable
fun ImageVector.rememberSymbolPainter(): Painter =
    rememberSymbolPainter(SymbolsTheme.fontSettings)

/**
 * Remembers this vector with its authored stroke widths scaled by the `wght` axis in
 * [fontSettings]. Weight 100 is half width, 400 preserves the authored width, and 700 is one and a
 * half width. Values outside that range are clamped; settings without `wght` preserve the source.
 */
@Composable
fun ImageVector.rememberSymbolPainter(fontSettings: SymbolFontSettings): Painter {
    val strokeScale = rememberUpdatedState(fontSettings.symbolStrokeScale())
    val config = remember {
        SymbolWeightVectorConfig { strokeScale.value }
    }
    val configs = remember(this, config) {
        root.symbolPathNames().associateWith { config }
    }

    return rememberVectorPainter(
        defaultWidth = defaultWidth,
        defaultHeight = defaultHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        name = name,
        tintColor = tintColor,
        tintBlendMode = tintBlendMode,
        autoMirror = autoMirror,
    ) { _, _ ->
        RenderVectorGroup(root, configs)
    }
}

internal fun SymbolFontSettings.symbolStrokeScale(): Float {
    val weight = variationSettings.settings
        .firstOrNull { it.axisName == WeightAxis }
        ?.toVariationValue(null)
        ?: return 1f
    require(weight.isFinite()) { "$WeightAxis must be finite, but was $weight" }
    return MinStrokeScale +
        (weight.coerceIn(MinWeight, MaxWeight) - MinWeight) /
        (MaxWeight - MinWeight)
}

internal class SymbolWeightVectorConfig(
    private val strokeScale: () -> Float,
) : VectorConfig {
    override fun <T> getOrDefault(property: VectorProperty<T>, defaultValue: T): T {
        if (property !== VectorProperty.StrokeLineWidth) return defaultValue

        @Suppress("UNCHECKED_CAST")
        return ((defaultValue as Float) * strokeScale()) as T
    }
}

internal fun VectorGroup.symbolPathNames(): Set<String> = buildSet {
    fun addPaths(group: VectorGroup) {
        group.forEach { node ->
            when (node) {
                is VectorGroup -> addPaths(node)
                is VectorPath -> add(node.name)
            }
        }
    }
    addPaths(this@symbolPathNames)
}

private const val WeightAxis = "wght"
private const val MinWeight = 100f
private const val MaxWeight = 700f
private const val MinStrokeScale = 0.5f
