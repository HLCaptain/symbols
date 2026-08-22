package io.github.hlcaptain.symbols.sample.ui

import io.github.hlcaptain.symbols.sample.api.SampleAvailability
import io.github.hlcaptain.symbols.sample.api.SampleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SampleUiTest {
    @Test
    fun sampleIdsMustBeUnique() {
        val entry = sampleEntry("same")

        val error = assertFailsWith<IllegalArgumentException> {
            requireUniqueSampleIds(listOf(entry, entry.copy(title = "Another sample")))
        }

        assertEquals("Sample ids must be unique: same", error.message)
    }

    @Test
    fun axisValueMustStayInsideItsRange() {
        assertFailsWith<IllegalArgumentException> {
            AxisUiModel(
                tag = "wght",
                label = "Weight",
                value = 800f,
                minValue = 100f,
                maxValue = 700f,
            )
        }
    }

    @Test
    fun unavailableReasonMustExplainTheConstraint() {
        assertFailsWith<IllegalArgumentException> {
            SampleAvailability.Unavailable(" ")
        }
    }
}

private fun sampleEntry(id: String): SampleItem = SampleItem(
    id = id,
    title = "Sample",
    description = "Description",
    modulePath = ":samples:test",
    content = {},
)
