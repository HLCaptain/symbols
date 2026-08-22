package io.github.hlcaptain.symbols.sample.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hlcaptain.symbols.sample.api.SampleAvailability
import io.github.hlcaptain.symbols.sample.api.SampleItem

@Composable
fun SampleBrowser(
    items: List<SampleItem>,
    onItemClick: (SampleItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    remember(items) { requireUniqueSampleIds(items) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (items.isEmpty()) {
            item {
                StatusMessage("No samples are registered.")
            }
        } else {
            items(items, key = SampleItem::id) { item ->
                ListItem(
                    headlineContent = {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = item.description,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.modulePath,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    trailingContent = if (item.availability is SampleAvailability.Unavailable) {
                        {
                            Text(
                                text = "Unavailable",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            enabled = item.availability is SampleAvailability.Available,
                            onClick = { onItemClick(item) },
                        ),
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
        }
    }
}

internal fun requireUniqueSampleIds(items: List<SampleItem>) {
    val duplicateIds = items
        .groupingBy(SampleItem::id)
        .eachCount()
        .filterValues { it > 1 }
        .keys
        .sorted()
    require(duplicateIds.isEmpty()) {
        "Sample ids must be unique: ${duplicateIds.joinToString()}"
    }
}
