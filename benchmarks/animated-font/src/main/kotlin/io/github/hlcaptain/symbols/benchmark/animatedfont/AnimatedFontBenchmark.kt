package io.github.hlcaptain.symbols.benchmark.animatedfont

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
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
class AnimatedFontBenchmark(
    private val renderer: String,
    private val axis: String,
    private val count: Int,
    private val scenarioSuffix: String,
    private val diagnosticsSuffix: String,
) {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test
    fun animateAxes() {
        val iterations = (arguments.getString("iterations") ?: "5").toInt()
        val durationMs = (arguments.getString("durationMs") ?: "2000").toInt()
        require(iterations in 1..20 && durationMs in 100..60_000)
        val diagnostics = diagnosticsSuffix == "-diagnostic"
        val counts = JSONArray()
        benchmark.measureRepeated(
            packageName = PACKAGE,
            metrics = buildList {
                add(FrameTimingMetric())
                if (diagnostics) {
                    for (section in listOf("update", "measure", "place", "draw", "layer", "effects")) {
                        add(TraceSectionMetric("SymbolBenchmark.$section", TraceSectionMetric.Mode.Sum))
                    }
                }
                // Actual upstream trace sections in Compose text, not inferred from outer layout.
                add(TraceSectionMetric("TextStringSimpleNode::measure", TraceSectionMetric.Mode.Sum))
                add(TraceSectionMetric("TextLayout:initLayout", TraceSectionMetric.Mode.Sum))
            },
            iterations = iterations,
            compilationMode = CompilationMode.Full(),
            setupBlock = {
                killProcess()
                startActivityAndWait(targetIntent(renderer, axis, count, scenarioSuffix.removePrefix("-").ifEmpty { "axes" }, diagnostics))
                device.readStats()
            },
        ) {
            device.executeShellCommand("am broadcast -a $PACKAGE.START -p $PACKAGE --ei duration_ms $durationMs")
            SystemClock.sleep(durationMs.toLong() + 150)
            counts.put(device.readStats())
        }
        writeJson("$renderer-$axis-$count$scenarioSuffix$diagnosticsSuffix-counts.json", counts)
        for (index in 0 until counts.length()) {
            val stats = counts.getJSONObject(index)
            assertEquals("Diagnostic mode mismatch", diagnostics, stats.getBoolean("diagnostics"))
            assertEffectBudget(stats, animation = true)
            val scenario = stats.getString("scenario")
            if (scenario == "axes" || scenario == "draw") {
                val expectedTicks = (1..stats.getInt("updates")).toSet()
                for (field in if (scenario == "draw") listOf("drawnTicks", "layerTicks") else listOf("drawnTicks")) {
                    val ticks = stats.getJSONArray(field)
                    assertEquals("Incomplete animation coverage in $field", expectedTicks,
                        (0 until ticks.length()).map { ticks.getInt(it) }.toSet())
                }
                if (renderer == "native") {
                    for (counter in listOf("hostCompositions", "groupCompositions", "iconCompositions", "measures", "placements")) {
                        assertEquals("Native animation updated $counter", 0, stats.getInt(counter))
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}-{2}{3}{4}")
        fun cases(): List<Array<Any>> {
            val scenario = (arguments.getString("scenario") ?: "axes").also { require(it in listOf("axes", "stress", "draw")) }
            val axes = selected("axes", if (scenario != "axes") "all" else "wght,FILL,GRAD,opsz,all")
            require(scenario == "axes" || axes == listOf("all"))
            return selected("renderers", if (scenario != "axes") "baseline,shared,value,native" else "baseline,native").flatMap { renderer ->
                axes.flatMap { axis ->
                    selected("counts", "1,100").map { count ->
                        arrayOf<Any>(renderer, axis, count.toInt(), if (scenario == "axes") "" else "-$scenario",
                            if (diagnosticsEnabled) "-diagnostic" else "-core")
                    }
                }
            }
        }
    }
}

/** Geometry is measured separately so screenshot readback never contaminates frame timings. */
class AnimatedFontGeometryTest {
    @Test
    fun supportedVariationRestoresAfterUnsupportedAxis() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        device.executeShellCommand("am force-stop $PACKAGE")
        instrumentation.context.startActivity(targetIntent("native", "unsupported", 1))
        device.readStats()
        fun snapshot(progress: Float, name: String): Bitmap {
            device.executeShellCommand("am broadcast -a $PACKAGE.SNAPSHOT -p $PACKAGE --ef progress $progress")
            val stats = device.readStats()
            val bounds = stats.getJSONArray("bounds").getJSONArray(0)
            val screen = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            assertTrue("Missing glyph at $progress", inkBounds(screen, bounds).getInt("pixels") > 0)
            val crop = Bitmap.createBitmap(screen, bounds.getInt(0), bounds.getInt(1), bounds.getInt(2), bounds.getInt(3))
            screen.recycle()
            outputFile("unsupported-axis-$name.png").outputStream().use {
                crop.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            return crop
        }
        val before = snapshot(0f, "before")
        val unsupported = snapshot(0.5f, "middle")
        val restored = snapshot(1f, "after")
        try {
            assertTrue("Unsupported axis should restore base-font appearance",
                glyphDifference(before, unsupported).first > 0)
            val (changedInkPixels, maxChannelError) = glyphDifference(before, restored)
            outputFile("unsupported-axis-comparison.json").writeText(
                JSONObject().put("changedInkPixels", changedInkPixels)
                    .put("maxChannelError", maxChannelError).toString(2),
            )
            assertEquals("Reapplying wght=700 must restore the same ink mask", 0, changedInkPixels)
            // Pixel 6a valid control captures vary by up to 3/255 on antialiased edges.
            assertTrue("Restored glyph differs beyond rasterizer rounding: $maxChannelError",
                maxChannelError <= 3)
        } finally {
            before.recycle()
            unsupported.recycle()
            restored.recycle()
        }
    }

    @Test
    fun settingsPreserveOuterLayoutAndProduceInk() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val measurements = JSONArray()
        selected("renderers", "baseline,native").forEach { renderer ->
            selected("axes", "wght,FILL,GRAD,opsz,all").forEach { axis ->
                device.executeShellCommand("am force-stop $PACKAGE")
                instrumentation.context.startActivity(targetIntent(renderer, axis, 1))
                val initial = device.readStats()
                val originalBounds = initial.getJSONArray("bounds").getJSONArray(0)
                listOf(0f, 0.5f, 1f).forEach { progress ->
                    device.executeShellCommand(
                        "am broadcast -a $PACKAGE.SNAPSHOT -p $PACKAGE --ef progress $progress",
                    )
                    val stats = device.readStats()
                    val bounds = stats.getJSONArray("bounds").getJSONArray(0)
                    assertEquals("Outer bounds changed for $renderer/$axis", originalBounds.toString(), bounds.toString())
                    if (renderer == "native") {
                        assertEquals("Producer must not recompose its caller", 0, stats.getInt("iconCompositions"))
                        assertEquals("Fixed outer bounds should not remeasure", 0, stats.getInt("measures"))
                        assertEquals("Fixed outer bounds should not replace", 0, stats.getInt("placements"))
                    }
                    val screen = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                    val ink = inkBounds(screen, bounds)
                    assertTrue("Missing glyph for $renderer/$axis/$progress", ink.getInt("pixels") > 0)
                    stats.put("ink", ink)
                    measurements.put(stats)
                    val file = outputFile("$renderer-$axis-$progress.png")
                    file.outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    screen.recycle()
                }
            }
        }
        writeJson("geometry.json", measurements)
        val records = (0 until measurements.length()).map(measurements::getJSONObject)
        val baseline = records.filter { it.getString("renderer") == "baseline" }
            .associateBy { it.getString("axis") to it.getDouble("progress") }
        records.filter { it.getString("renderer") != "baseline" }.forEach { actual ->
            val expected = baseline[actual.getString("axis") to actual.getDouble("progress")]
            if (expected != null) {
                val label = "${actual.getString("renderer")}/${actual.getString("axis")}/${actual.getDouble("progress")}"
                assertEquals("Outer bounds differ: $label", expected.getJSONArray("bounds").toString(), actual.getJSONArray("bounds").toString())
                val expectedInk = expected.getJSONObject("ink")
                val actualInk = actual.getJSONObject("ink")
                for (dimension in listOf("width", "height")) {
                    assertEquals("Ink $dimension differs: $label", expectedInk.getInt(dimension), actualInk.getInt(dimension))
                }
                for (coordinate in listOf("left", "top")) {
                    assertTrue("Ink $coordinate differs by more than a pixel: $label",
                        kotlin.math.abs(expectedInk.getInt(coordinate) - actualInk.getInt(coordinate)) <= 1)
                }
                assertTrue("Ink coverage differs by more than 1%: $label",
                    kotlin.math.abs(expectedInk.getInt("pixels") - actualInk.getInt("pixels")) <=
                        maxOf(1.0, expectedInk.getInt("pixels") * 0.01))
            }
        }
    }
}

/** Combined effects with dynamic size (`stress`) or fixed size and producer tint (`draw`). */
class AnimatedFontStressTest {
    @Test
    fun combinedEffectsChangeSizeColorAndRendering() {
        val scenario = (arguments.getString("scenario") ?: "stress").also { require(it in listOf("stress", "draw")) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val measurements = JSONArray()
        selected("renderers", "baseline,native").forEach { renderer ->
            device.executeShellCommand("am force-stop $PACKAGE")
            instrumentation.context.startActivity(targetIntent(renderer, "all", 1, scenario))
            device.readStats()
            listOf(0f, 0.25f, 0.5f, 0.75f).forEach { progress ->
                device.executeShellCommand("am broadcast -a $PACKAGE.SNAPSHOT -p $PACKAGE --ef progress $progress")
                val stats = device.readStats()
                val screen = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                stats.put("ink", inkBounds(screen, stats.getJSONArray("captureBounds").getJSONArray(0), colored = true))
                measurements.put(stats)
                outputFile("$renderer-$scenario-$progress.png").outputStream().use {
                    screen.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screen.recycle()
            }
        }
        writeJson("$scenario-geometry.json", measurements)
        val records = (0 until measurements.length()).map(measurements::getJSONObject)
        records.groupBy { it.getString("renderer") }.forEach { (renderer, cases) ->
            val measuredSizes = cases.map { it.getJSONArray("measuredSizes").getJSONArray(0).toString() }.distinct()
            if (scenario == "draw") assertEquals("Draw scenario must keep measured size fixed: $renderer", 1, measuredSizes.size)
            else assertTrue("Stress must change measured icon dimensions: $renderer", measuredSizes.size > 1)
            assertEquals("Capture cell must stay fixed: $renderer", 1,
                cases.map { it.getJSONArray("captureBounds").toString() }.distinct().size)
            assertTrue("Stress must change visible colors: $renderer",
                cases.map { it.getJSONObject("ink").getJSONArray("meanRgb").toString() }.distinct().size > 1)
            for (property in listOf("fontSizeDp", "colorArgb", "alpha", "scale", "rotationDegrees", "translationXDp", "translationYDp")) {
                val values = cases.map { it.getJSONObject("effects").get(property).toString() }.distinct()
                if (scenario == "draw" && property == "fontSizeDp") assertEquals("Draw font size must stay fixed: $renderer", 1, values.size)
                else assertTrue("$scenario must change $property: $renderer", values.size > 1)
            }
            for (axis in listOf("wght", "FILL", "GRAD", "opsz")) {
                assertTrue("Stress must change font axis $axis: $renderer",
                    cases.map { it.getJSONObject("effects").getJSONObject("axes").getDouble(axis) }.distinct().size > 1)
            }
            cases.forEach { record ->
                assertTrue("Missing colored glyph: $renderer/${record.getDouble("progress")}",
                    record.getJSONObject("ink").getInt("coloredPixels") > 0)
                if (scenario == "draw" && renderer == "native") {
                    for (counter in listOf("hostCompositions", "groupCompositions", "iconCompositions", "measures", "placements")) {
                        assertEquals("Native draw scenario must not update $counter", 0, record.getInt(counter))
                    }
                }
                assertEffectBudget(record, animation = false)
            }
        }
        val baseline = records.filter { it.getString("renderer") == "baseline" }.associateBy { it.getDouble("progress") }
        records.filter { it.getString("renderer") != "baseline" }.forEach { actual ->
            val expected = baseline[actual.getDouble("progress")]
            if (expected != null) {
                val label = "${actual.getString("renderer")}/${actual.getDouble("progress")}"
                assertEquals("Stress icon layout mismatch: $label", expected.getJSONArray("bounds").toString(), actual.getJSONArray("bounds").toString())
                assertEquals("Stress capture cell mismatch: $label", expected.getJSONArray("captureBounds").toString(), actual.getJSONArray("captureBounds").toString())
                assertEquals("Stress measured sizes mismatch: $label", expected.getJSONArray("measuredSizes").toString(), actual.getJSONArray("measuredSizes").toString())
                for (property in listOf("fontSizeDp", "colorArgb", "alpha", "scale", "rotationDegrees", "translationXDp", "translationYDp")) {
                    assertEquals("Stress $property mismatch: $label",
                        expected.getJSONObject("effects").get(property).toString(), actual.getJSONObject("effects").get(property).toString())
                }
                for (axis in listOf("wght", "FILL", "GRAD", "opsz")) {
                    assertEquals("Stress font axis $axis mismatch: $label",
                        expected.getJSONObject("effects").getJSONObject("axes").getDouble(axis),
                        actual.getJSONObject("effects").getJSONObject("axes").getDouble(axis), 0.0)
                }
                val expectedInk = expected.getJSONObject("ink")
                val actualInk = actual.getJSONObject("ink")
                for (coordinate in listOf("left", "top", "width", "height")) {
                    assertTrue("Stress ink $coordinate differs: $label",
                        kotlin.math.abs(expectedInk.getInt(coordinate) - actualInk.getInt(coordinate)) <= 2)
                }
                for (channel in 0..2) {
                    assertTrue("Stress color channel differs: $label",
                        kotlin.math.abs(expectedInk.getJSONArray("meanRgb").getDouble(channel) - actualInk.getJSONArray("meanRgb").getDouble(channel)) <= 3)
                }
            }
        }
    }
}

private fun targetIntent(renderer: String, axis: String, count: Int, scenario: String = "axes", diagnostics: Boolean = diagnosticsEnabled) = Intent().apply {
    setClassName(PACKAGE, "$PACKAGE.BenchmarkActivity")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    putExtra("renderer", renderer)
    putExtra("axis", axis)
    putExtra("count", count)
    putExtra("scenario", scenario)
    putExtra("diagnostics", diagnostics)
}

private fun assertEffectBudget(stats: JSONObject, animation: Boolean) {
    if (!stats.has("effectEvaluations") || stats.optString("scenario", "axes") == "axes") return
    val groups = if (stats.getInt("count") == 100) 10 else 1
    val evaluations = stats.getInt("effectEvaluations")
    val budget = groups * stats.getInt("updates")
    assertTrue("Effects must be evaluated at most once per row/update: $evaluations > $budget",
        evaluations in 0..budget)
    if (animation) assertTrue("Animated styles must actually evaluate their effects", evaluations > 0)
}

private fun UiDevice.readStats(): JSONObject {
    val node = wait(Until.findObject(By.descStartsWith("stats:")), 10_000)
        ?: error("Benchmark app did not publish statistics")
    return JSONObject(node.contentDescription.toString().removePrefix("stats:"))
}

private fun isInk(pixel: Int) =
    Color.red(pixel) < 200 && Color.green(pixel) < 200 && Color.blue(pixel) < 200

/** Changed ink-mask pixels and maximum RGB-channel error. */
private fun glyphDifference(expected: Bitmap, actual: Bitmap): Pair<Int, Int> {
    assertEquals(expected.width, actual.width)
    assertEquals(expected.height, actual.height)
    val before = IntArray(expected.width * expected.height)
    val after = IntArray(before.size)
    expected.getPixels(before, 0, expected.width, 0, 0, expected.width, expected.height)
    actual.getPixels(after, 0, actual.width, 0, 0, actual.width, actual.height)
    var changedInkPixels = 0
    var maxChannelError = 0
    for (index in before.indices) {
        if (isInk(before[index]) != isInk(after[index])) changedInkPixels++
        for (shift in 0..16 step 8) {
            maxChannelError = maxOf(maxChannelError, kotlin.math.abs(
                ((before[index] ushr shift) and 255) - ((after[index] ushr shift) and 255),
            ))
        }
    }
    return changedInkPixels to maxChannelError
}

private fun inkBounds(bitmap: Bitmap, bounds: JSONArray, colored: Boolean = false): JSONObject {
    val left = bounds.getInt(0)
    val top = bounds.getInt(1)
    val width = bounds.getInt(2)
    val height = bounds.getInt(3)
    require(left >= 0 && top >= 0 && left + width <= bitmap.width && top + height <= bitmap.height)
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    var pixels = 0
    var coloredPixels = 0
    val channels = LongArray(3)
    for (y in 0 until height) for (x in 0 until width) {
        val pixel = bitmap.getPixel(left + x, top + y)
        if (if (colored) minOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) < 230 else isInk(pixel)) {
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
            pixels++
            if (colored) {
                channels[0] += Color.red(pixel)
                channels[1] += Color.green(pixel)
                channels[2] += Color.blue(pixel)
                if (maxOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) -
                    minOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) > 10) coloredPixels++
            }
        }
    }
    return JSONObject().put("left", minX).put("top", minY)
        .put("width", if (pixels == 0) 0 else maxX - minX + 1)
        .put("height", if (pixels == 0) 0 else maxY - minY + 1)
        .put("pixels", pixels).put("rgbThreshold", if (colored) 230 else 200)
        .apply {
            if (colored) {
                put("coloredPixels", coloredPixels)
                put("meanRgb", JSONArray(channels.map { if (pixels == 0) 0.0 else it.toDouble() / pixels }))
            }
        }
}

private fun selected(argument: String, default: String) = (arguments.getString(argument) ?: default).split(',')
private val arguments get() = InstrumentationRegistry.getArguments()
private val diagnosticsEnabled get() = (arguments.getString("diagnostics") ?: "false").toBooleanStrict()
private fun outputFile(name: String): File {
    val directory = arguments.getString("additionalTestOutputDir")?.let(::File)
        ?: requireNotNull(InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir(null))
    directory.mkdirs()
    return File(directory, name)
}
private fun writeJson(name: String, value: JSONArray) = outputFile(name).writeText(value.toString(2))
private const val PACKAGE = "io.github.hlcaptain.symbols.benchmark.animatedfont"
