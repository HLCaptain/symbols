package io.github.hlcaptain.symbols.benchmark.vectorruntime

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@OptIn(ExperimentalMetricApi::class)
class VectorRuntimeBenchmark(private val filled: Boolean) {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test
    fun constructAndReadCache() {
        val samples = JSONArray()
        benchmark.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf("first", "remaining", "cached").map {
                TraceSectionMetric("Vector.$it", TraceSectionMetric.Mode.Sum)
            },
            compilationMode = CompilationMode.Full(),
            iterations = iterations,
            setupBlock = { killProcess() },
        ) {
            startActivityAndWait(targetIntent())
            samples.put(device.readStats())
        }
        validate(samples, animated = false)
        outputFile("construction-$filled.json").writeText(samples.toString(2))
    }

    @Test
    fun renderCachedVectors() {
        val samples = JSONArray()
        benchmark.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Full(),
            iterations = iterations,
            setupBlock = {
                killProcess()
                startActivityAndWait(targetIntent())
                device.readStats()
            },
        ) {
            device.executeShellCommand("am broadcast -a $PACKAGE.START -p $PACKAGE --ei duration_ms 2000")
            SystemClock.sleep(2_200)
            samples.put(device.readStats())
        }
        validate(samples, animated = true)
        outputFile("render-$filled.json").writeText(samples.toString(2))
        // Macrobenchmark may stop the target at completion. Capture a fresh static grid after timing.
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("am force-stop $PACKAGE")
        device.executeShellCommand("am start -W -n $PACKAGE/.VectorBenchmarkActivity --ez filled $filled")
        val staticStats = device.readStats()
        assertEquals(0, staticStats.getInt("runId"))
        assertEquals(0, staticStats.getInt("frames"))
        assertEquals(false, staticStats.getBoolean("complete"))
        val bounds = requireNotNull(device.findObject(By.desc("vector-grid"))).visibleBounds
        val screen = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val crop = Bitmap.createBitmap(screen, bounds.left, bounds.top, bounds.width(), bounds.height())
        outputFile("render-$filled.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle()
        screen.recycle()
    }

    private fun validate(samples: JSONArray, animated: Boolean) {
        assertEquals(iterations, samples.length())
        val processIds = mutableSetOf<Int>()
        for (index in 0 until samples.length()) {
            val sample = samples.getJSONObject(index)
            assertEquals(filled, sample.getBoolean("filled"))
            assertEquals(101, sample.getInt("vectors"))
            assertEquals(101_000, sample.getInt("cachedCalls"))
            assertTrue("Each iteration requires a fresh process", processIds.add(sample.getInt("processId")))
            if (animated) {
                assertTrue("Animation did not finish", sample.getBoolean("complete"))
                assertTrue("No animated frames", sample.getInt("frames") > 1)
            }
        }
    }

    private fun targetIntent() = Intent().setClassName(PACKAGE, "$PACKAGE.VectorBenchmarkActivity")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("filled", filled)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "filled={0}")
        fun cases() = (InstrumentationRegistry.getArguments().getString("fills") ?: "false")
            .split("+").map { arrayOf(it.toBooleanStrict()) }
    }
}

private val iterations get() = (InstrumentationRegistry.getArguments().getString("iterations") ?: "5")
    .toInt().also { require(it in 1..20) }

private fun UiDevice.readStats(): JSONObject {
    val node = wait(Until.findObject(By.descStartsWith("vector-stats:")), 10_000)
        ?: run {
            dumpWindowHierarchy(outputFile("missing-ready.xml"))
            error("No visible vector statistics; see missing-ready.xml")
        }
    return JSONObject(node.contentDescription.removePrefix("vector-stats:"))
}

private fun outputFile(name: String): File {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        ?.let(::File) ?: instrumentation.context.getExternalFilesDir(null)!!
    directory.mkdirs()
    return File(directory, name)
}

private const val PACKAGE = "io.github.hlcaptain.symbols.benchmark.vectorruntime"
