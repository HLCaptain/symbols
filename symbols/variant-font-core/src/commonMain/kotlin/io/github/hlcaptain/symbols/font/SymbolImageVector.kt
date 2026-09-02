package io.github.hlcaptain.symbols.font

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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

/**
 * Remembers a painter whose stroked paths follow the current [SymbolsTheme] weight.
 *
 * Only the `wght` setting affects this painter. Weight 100 draws strokes at half their authored
 * width, 400 preserves the authored width, and 700 draws them at one and a half times that width.
 * Intermediate values are scaled evenly, values outside the range are clamped, and settings
 * without `wght` preserve the source. Filled paths and other settings are unchanged.
 *
 * The vector geometry and painter configuration are remembered. A theme change still recomposes
 * the caller, updates the vector subtree, and draws that subtree again; it does not rebuild the
 * vector paths. Use the settings-producer overload when state changes should be observed only by
 * the painter's child composition.
 *
 * @return a painter remembered for this vector and composition
 * @throws IllegalArgumentException when the `wght` value is not finite
 */
@Composable
fun ImageVector.rememberSymbolPainter(): Painter =
    rememberSymbolPainter(SymbolsTheme.fontSettings)

/**
 * Remembers a painter whose stroked paths use the weight in [fontSettings].
 *
 * Weight 100 draws strokes at half their authored width, 400 preserves the authored width, and 700
 * draws them at one and a half times that width. Intermediate values are scaled evenly, values
 * outside the range are clamped, and settings without `wght` preserve the source. Filled paths and
 * other settings are unchanged.
 *
 * This overload does not read [SymbolsTheme]. When the caller supplies a new value during
 * recomposition, the remembered painter updates and draws the vector subtree again without
 * rebuilding its paths. If the argument is derived from snapshot state, that state is observed by
 * the caller; use the settings-producer overload to move that observation into the painter.
 *
 * @param fontSettings settings whose `wght` value controls stroke width
 * @return a painter remembered for this vector and composition
 * @throws IllegalArgumentException when the `wght` value is not finite
 */
@Composable
fun ImageVector.rememberSymbolPainter(fontSettings: SymbolFontSettings): Painter =
    rememberSymbolPainter { fontSettings }

/**
 * Remembers a painter whose stroked paths use settings returned by [fontSettings].
 *
 * The producer may read snapshot state. That state is then observed by the vector painter's child
 * composition instead of the caller's composition, so changing it need not recompose the caller.
 * The vector child still updates and draws again. Its cached paths are not rebuilt.
 *
 * The producer is not composable, may be called more than once, and should be cheap and free of
 * side effects. Use [rememberSymbolPainter] without arguments to read the current [SymbolsTheme].
 * Only `wght` is used: values 100, 400, and 700 produce `0.5x`, `1x`, and `1.5x` authored stroke
 * widths, with intermediate values scaled evenly and values outside that range clamped. Filled
 * paths and other settings are unchanged.
 *
 * @param fontSettings non-composable producer for the latest symbol-font settings
 * @return a painter remembered for this vector and composition
 * @throws IllegalArgumentException when the produced `wght` value is not finite
 */
@Composable
fun ImageVector.rememberSymbolPainter(
    fontSettings: () -> SymbolFontSettings,
): Painter {
    val currentFontSettings = rememberUpdatedState(fontSettings)
    val strokeScale = remember {
        derivedStateOf { currentFontSettings.value().symbolStrokeScale() }
    }
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
