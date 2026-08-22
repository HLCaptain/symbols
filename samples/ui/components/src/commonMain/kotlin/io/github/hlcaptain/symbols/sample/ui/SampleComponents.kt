package io.github.hlcaptain.symbols.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.sample.api.SampleAvailability
import kotlin.math.roundToInt

/** A consistently spaced, vertically scrollable sample surface. */
@Composable
fun SamplePage(
    description: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (description != null) {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        content()
    }
}

/** A titled card for one independently understandable example. */
@Composable
fun ExampleCard(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (description != null) {
                    Text(
                        text = description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            content()
        }
    }
}

/** UI-only metadata for one numeric axis; it has no symbol-library dependency. */
data class AxisUiModel(
    val tag: String,
    val label: String,
    val value: Float,
    val minValue: Float,
    val maxValue: Float,
    val valueLabel: String = formatAxisValue(value),
    val steps: Int = 0,
) {
    init {
        require(tag.isNotBlank()) { "Axis tag must not be blank" }
        require(label.isNotBlank()) { "Axis label must not be blank" }
        require(
            value.isFinite() &&
                minValue.isFinite() &&
                maxValue.isFinite() &&
                minValue <= value &&
                value <= maxValue,
        ) {
            "Axis $tag must have finite min <= value <= max values"
        }
        require(steps >= 0) { "Axis $tag steps must not be negative" }
    }
}

/** Renders controls from axis metadata supplied by any font implementation. */
@Composable
fun AxisControls(
    axes: List<AxisUiModel>,
    onValueChange: (tag: String, value: Float) -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    resetEnabled: Boolean = true,
    title: String = "Variable font axes",
) {
    ExampleCard(title = title, modifier = modifier) {
        if (onReset != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onReset, enabled = resetEnabled) {
                    Text("Reset")
                }
            }
        }
        if (axes.isEmpty()) {
            StatusMessage("This font exposes no adjustable axes.")
        } else {
            axes.forEach { axis ->
                AxisControl(axis, onValueChange)
            }
        }
    }
}

@Composable
private fun AxisControl(
    axis: AxisUiModel,
    onValueChange: (tag: String, value: Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = axis.label,
                modifier = Modifier.width(64.dp),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = axis.value,
                onValueChange = { onValueChange(axis.tag, it) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = axis.label },
                valueRange = axis.minValue..axis.maxValue,
                steps = axis.steps,
            )
            Text(
                text = axis.valueLabel,
                modifier = Modifier.width(48.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            text = axis.tag,
            modifier = Modifier.padding(start = 72.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

enum class StatusKind {
    Info,
    Success,
    Warning,
    Error,
}

/** A theme-aware inline status message. */
@Composable
fun StatusMessage(
    text: String,
    modifier: Modifier = Modifier,
    kind: StatusKind = StatusKind.Info,
) {
    val colors = when (kind) {
        StatusKind.Info -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        StatusKind.Success -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
        StatusKind.Warning -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
        StatusKind.Error -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.first),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            color = colors.second,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Shows the current target availability for a sample. */
@Composable
fun AvailabilityStatus(
    availability: SampleAvailability,
    modifier: Modifier = Modifier,
) {
    when (availability) {
        SampleAvailability.Available -> StatusMessage(
            text = "Available on this platform.",
            modifier = modifier,
            kind = StatusKind.Success,
        )
        is SampleAvailability.Unavailable -> StatusMessage(
            text = availability.reason,
            modifier = modifier,
            kind = StatusKind.Warning,
        )
    }
}

private fun formatAxisValue(value: Float): String {
    val rounded = (value * 10f).roundToInt() / 10f
    return if (rounded == rounded.roundToInt().toFloat()) {
        rounded.roundToInt().toString()
    } else {
        rounded.toString()
    }
}
