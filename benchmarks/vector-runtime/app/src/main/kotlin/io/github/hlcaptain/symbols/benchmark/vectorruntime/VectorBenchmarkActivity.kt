package io.github.hlcaptain.symbols.benchmark.vectorruntime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.Trace
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.json.JSONObject

class VectorBenchmarkActivity : ComponentActivity() {
    private var runId by mutableIntStateOf(0)
    private var durationMs = 2_000
    private val startReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            durationMs = intent?.getIntExtra("duration_ms", 2_000) ?: 2_000
            require(durationMs in 100..60_000)
            runId++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filled = intent.getBooleanExtra("filled", false)
        val getVector = if (filled) ::selectedFilledVector else ::selectedVector
        val vectors = ArrayList<ImageVector>(VECTOR_COUNT)
        trace("Vector.first") { vectors += getVector(0) }
        trace("Vector.remaining") {
            for (index in 1 until VECTOR_COUNT) vectors += getVector(index)
        }
        for (index in vectors.indices) check(vectors[index] === getVector(index))
        // Same indexed dispatch and volatile sink for both implementations. No reflection or cache reset.
        repeat(100) { for (index in vectors.indices) sink = getVector(index) }
        trace("Vector.cached") {
            repeat(CACHED_PASSES) { for (index in vectors.indices) sink = getVector(index) }
        }
        // Keep the volatile sink observable to R8 without adding work inside the timed block.
        check(sink === vectors.last())
        val filter = IntentFilter("$packageName.START")
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(startReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(startReceiver, filter)
        }
        setContent {
            var progress by remember { mutableFloatStateOf(0f) }
            var frames by remember { mutableIntStateOf(0) }
            var complete by remember { mutableStateOf(false) }
            LaunchedEffect(runId) {
                if (runId == 0) return@LaunchedEffect
                frames = 0
                complete = false
                val start = withFrameNanos { it }
                var elapsed = 0L
                while (elapsed < durationMs * 1_000_000L) {
                    elapsed = withFrameNanos { it - start }
                    progress = (elapsed.toFloat() / (durationMs * 1_000_000f)).coerceAtMost(1f)
                    frames++
                }
                complete = true
            }
            val stats = JSONObject().put("filled", filled).put("processId", Process.myPid())
                .put("vectors", vectors.size).put("cachedCalls", CACHED_PASSES * VECTOR_COUNT)
                .put("runId", runId).put("frames", frames).put("complete", complete)
            Column(Modifier.fillMaxSize().background(Color.White).systemBarsPadding().padding(16.dp)) {
                BasicText("Vector benchmark", Modifier.semantics { contentDescription = "vector-stats:$stats" })
                Column(Modifier.semantics { contentDescription = "vector-grid" }) {
                    for (row in vectors.chunked(10)) {
                        Row {
                            for (vector in row) {
                                Image(
                                    imageVector = vector,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).graphicsLayer { translationX = progress * 8f },
                                    colorFilter = ColorFilter.tint(Color(0.15f + progress * 0.5f, 0.25f, 0.65f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(startReceiver)
        super.onDestroy()
    }
}

private inline fun trace(name: String, block: () -> Unit) {
    Trace.beginSection(name)
    try {
        block()
    } finally {
        Trace.endSection()
    }
}

private const val CACHED_PASSES = 1_000
@Volatile private var sink: ImageVector? = null
