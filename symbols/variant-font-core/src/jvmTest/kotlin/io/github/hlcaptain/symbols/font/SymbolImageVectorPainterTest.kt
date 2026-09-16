package io.github.hlcaptain.symbols.font

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SymbolImageVectorPainterTest {
    @Test
    fun settingsProducerUpdatesPainterWithoutRecomposingCaller() = runBlocking(ImmediateFrameClock) {
        val weight = mutableFloatStateOf(100f)
        var callerCompositions = 0
        var producerCalls = 0
        var painter: Painter? = null
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(PainterTestApplier(), recomposer)
        val recomposerJob = launch(start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            composition.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    callerCompositions++
                    painter = TestVector.rememberSymbolPainter {
                        producerCalls++
                        settings(weight.floatValue)
                    }
                }
            }
            recomposer.awaitIdle()
            val initialCallerCompositions = callerCompositions
            val initialProducerCalls = producerCalls
            val initialPainter = painter

            Snapshot.withMutableSnapshot { weight.floatValue = 700f }
            recomposer.awaitIdle()

            assertEquals(initialCallerCompositions, callerCompositions)
            assertTrue(producerCalls > initialProducerCalls)
            assertSame(initialPainter, painter)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposerJob.join()
        }
    }

    private fun settings(weight: Float) = SymbolFontSettings(
        FontVariation.Settings(FontVariation.Setting("wght", weight)),
    )
}

private object ImmediateFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
        onFrame(System.nanoTime())
}

private val TestVector = ImageVector.Builder(
    name = "Test vector",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        name = "line",
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
    ) {
        moveTo(2f, 2f)
        lineTo(22f, 22f)
    }
}.build()

private class PainterTestApplier : AbstractApplier<Unit>(Unit) {
    override fun insertBottomUp(index: Int, instance: Unit) = Unit

    override fun insertTopDown(index: Int, instance: Unit) = Unit

    override fun move(from: Int, to: Int, count: Int) = Unit

    override fun onClear() = Unit

    override fun remove(index: Int, count: Int) = Unit
}
