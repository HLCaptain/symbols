import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.Surface
import org.w3c.dom.Element
import kotlin.math.abs

private const val ANDROID = "http://schemas.android.com/apk/res/android"
private val xml = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
}.newDocumentBuilder()
private data class Shape(val path: Path, val color: Int)
private class Vector(val width: Float, val height: Float, val shapes: List<Shape>) : AutoCloseable {
    override fun close() { shapes.forEach { it.path.close() } }
}
private fun read(bytes: ByteArray): Vector {
    val root = xml.parse(ByteArrayInputStream(bytes)).documentElement
    check(root.tagName == "vector")
    val shapes = (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }.map { element ->
        check(element.tagName == "path") { "Unsupported XML element ${element.tagName}" }
        check(!element.hasAttributeNS(ANDROID, "strokeColor"))
        check(!element.hasAttributeNS(ANDROID, "trimPathStart"))
        check(!element.hasAttributeNS(ANDROID, "trimPathEnd"))
        val path = requireNotNull(Path.makeFromSVGString(element.getAttributeNS(ANDROID, "pathData")))
        path.fillMode = when (element.getAttributeNS(ANDROID, "fillType")) {
            "", "nonZero" -> PathFillMode.WINDING
            "evenOdd" -> PathFillMode.EVEN_ODD
            else -> error("Unknown fill rule")
        }
        val value = element.getAttributeNS(ANDROID, "fillColor")
        check(value.matches(Regex("#[0-9a-fA-F]{8}"))) { "Unsupported color $value" }
        val color = value.drop(1).toLong(16).toInt()
        check((color and 0x00ffffff) == 0) { "Expected black foreground" }
        val alpha = element.getAttributeNS(ANDROID, "fillAlpha").ifEmpty { "1" }.toFloat()
        check(alpha == 1f) { "Unexpected fillAlpha $alpha" }
        Shape(path, color)
    }
    return Vector(root.getAttributeNS(ANDROID, "viewportWidth").toFloat(), root.getAttributeNS(ANDROID, "viewportHeight").toFloat(), shapes)
}
private fun raster(vector: Vector, size: Int, png: File? = null): ByteArray = Surface.makeRasterN32Premul(size, size).use { surface ->
    surface.canvas.clear(if (png != null) -1 else 0)
    surface.canvas.scale(size / vector.width, size / vector.height)
    Paint().use { paint ->
        paint.isAntiAlias = true
        vector.shapes.forEach { shape ->
            paint.color = shape.color
            surface.canvas.drawPath(shape.path, paint)
        }
    }
    surface.makeImageSnapshot().use { image ->
        if (png != null) requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { png.writeBytes(it.bytes) }
        Bitmap.makeFromImage(image).use { bitmap ->
            check(bitmap.rowBytes == size * 4)
            val pixels = requireNotNull(bitmap.readPixels())
            ByteArray(size * size) { pixels[it * 4 + 3] }
        }
    }
}
private fun bounds(pixels: ByteArray, size: Int): List<Int> {
    var left = size; var top = size; var right = -1; var bottom = -1
    pixels.indices.forEach { i ->
        if ((pixels[i].toInt() and 255) > 0) {
            left = minOf(left, i % size); top = minOf(top, i / size)
            right = maxOf(right, i % size); bottom = maxOf(bottom, i / size)
        }
    }
    return if (right < 0) emptyList() else listOf(left, top, right, bottom)
}
private data class Diff(val name: String, val size: Int, val changed: Int, val over8: Int, val over32: Int, val over128: Int, val maxAlpha: Int, val totalAlpha: Long, val alphaA: Long, val alphaB: Long, val boundsA: List<Int>, val boundsB: List<Int>) {
    val normalized get() = totalAlpha.toDouble() / (255 * size * size)
    fun json() = """{"name":"$name","size":$size,"changed_pixels":$changed,"pixels_delta_gt8":$over8,"pixels_delta_gt32":$over32,"pixels_delta_gt128":$over128,"max_alpha_delta":$maxAlpha,"sum_absolute_alpha_delta":$totalAlpha,"canvas_difference_fraction":$normalized,"alpha_sum_A":$alphaA,"alpha_sum_B":$alphaB,"bounds_A":$boundsA,"bounds_B":$boundsB}"""
}
private fun sha256(path: String): String = MessageDigest.getInstance("SHA-256")
    .digest(File(path).readBytes()).joinToString("") { "%02x".format(it) }

fun main(args: Array<String>) {
    require(args.size in 3..4) { "Usage: reference.zip candidate.aar output-directory [maximum-error-fraction]" }
    val tolerance = args.getOrNull(3)?.toDouble()?.also { require(it in 0.0..1.0) }

    val destination = File(args[2]).apply { mkdirs() }
    val diffs = mutableListOf<Diff>()
    ZipFile(args[0]).use { a -> ZipFile(args[1]).use { b ->
        fun names(z: ZipFile) = z.entries().asSequence().map { it.name }.filter { it.startsWith("res/drawable/") && it.endsWith(".xml") }.toSortedSet()
        val names = names(a)
        check(names.isNotEmpty() && names == names(b)) { "Drawable resource sets differ" }
        check(names.all { it.matches(Regex("res/drawable/[a-z0-9_]+\\.xml")) }) { "Unexpected resource filename" }
        names.forEachIndexed { index, name ->
            read(a.getInputStream(a.getEntry(name)).readBytes()).use { va ->
                read(b.getInputStream(b.getEntry(name)).readBytes()).use { vb ->
                    check(va.width == vb.width && va.height == vb.height)
                    for (size in listOf(48, 96)) {
                        val pa = raster(va, size); val pb = raster(vb, size)
                        var changed = 0; var over8 = 0; var over32 = 0; var over128 = 0; var maxAlpha = 0
                        var total = 0L; var alphaA = 0L; var alphaB = 0L
                        pa.indices.forEach { i ->
                            val aa = pa[i].toInt() and 255; val ab = pb[i].toInt() and 255
                            val delta = abs(aa - ab)
                            if (delta > 0) changed++
                            if (delta > 8) over8++
                            if (delta > 32) over32++
                            if (delta > 128) over128++
                            maxAlpha = maxOf(maxAlpha, delta); total += delta; alphaA += aa; alphaB += ab
                        }
                        diffs += Diff(name.removePrefix("res/drawable/").removeSuffix(".xml"), size, changed, over8, over32, over128, maxAlpha, total, alphaA, alphaB, bounds(pa,size), bounds(pb,size))
                    }
                }
            }
            if ((index + 1) % 500 == 0) println("Compared ${index+1}/${names.size} glyphs")
        }
        val worst = diffs.sortedByDescending { it.normalized }.distinctBy { it.name }.take(12)
        worst.forEach { diff ->
            val name = "res/drawable/${diff.name}.xml"
            read(a.getInputStream(a.getEntry(name)).readBytes()).use { raster(it, 192, File(destination,"${diff.name}-A.png")) }
            read(b.getInputStream(b.getEntry(name)).readBytes()).use { raster(it, 192, File(destination,"${diff.name}-B.png")) }
        }
        val summaries = listOf(48,96).joinToString(",") { size ->
            val rows = diffs.filter { it.size == size }
            val changed = rows.filter { it.changed > 0 }
            """{"size":$size,"glyphs":${rows.size},"glyphs_exceeding_limit":${tolerance?.let { limit -> rows.count { it.normalized > limit } } ?: "null"},"exact_matches":${rows.count{it.changed==0}},"different":${changed.size},"glyphs_delta_gt8":${rows.count{it.over8>0}},"glyphs_delta_gt32":${rows.count{it.over32>0}},"glyphs_delta_gt128":${rows.count{it.over128>0}},"glyphs_bounds_changed":${rows.count{it.boundsA!=it.boundsB}},"total_changed_pixels":${rows.sumOf{it.changed.toLong()}},"max_changed_pixels":${rows.maxOf{it.changed}},"max_alpha_delta":${rows.maxOf{it.maxAlpha}},"max_canvas_difference_fraction":${rows.maxOf{it.normalized}}}"""
        }
        File(destination,"raster-comparison.json").writeText("""{"renderer":"Skiko 0.150.1 CPU raster, same engine for A and B; transparent background, antialiased black fill, XML winding/evenOdd rules","reference_sha256":"${sha256(args[0])}","candidate_sha256":"${sha256(args[1])}","maximum_error_fraction":${tolerance ?: "null"},"accepted":${if (tolerance == null) "null" else diffs.all { it.normalized <= tolerance }},"summaries":[$summaries],"worst":[${worst.joinToString(","){it.json()}}]}""")
        println(File(destination,"raster-comparison.json").readText())
        if (tolerance != null) check(diffs.all { it.normalized <= tolerance }) {
            "Outline error exceeds $tolerance: " + diffs.filter { it.normalized > tolerance }
                .joinToString { "${it.name}@${it.size}: ${it.normalized}" }
        }
    }}
}
