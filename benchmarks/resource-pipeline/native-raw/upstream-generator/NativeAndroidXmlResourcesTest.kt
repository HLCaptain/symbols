package org.jetbrains.compose.resources

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class NativeAndroidXmlResourcesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun disabledGeneratorMatchesTheOriginalPluginByteForByte() {
        val plugin = File(requireNotNull(System.getProperty("originalComposePluginJar")))
        val parent = javaClass.classLoader
        val baseline = object : URLClassLoader(arrayOf(plugin.toURI().toURL()), parent) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (!name.startsWith("org.jetbrains.compose.resources.")) return super.loadClass(name, resolve)
                synchronized(getClassLoadingLock(name)) {
                    val result = findLoadedClass(name) ?: findClass(name)
                    if (resolve) resolveClass(result)
                    return result
                }
            }
        }
        baseline.use {
            val specs = it.loadClass("org.jetbrains.compose.resources.GeneratedResClassSpecKt")
            val originalRes = specs.getMethod("getResFileSpec", String::class.java, String::class.java,
                String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, "test.generated", "AppRes", "composeResources/test.generated/", true).toString()
            assertEquals(originalRes, getResFileSpec("test.generated", "AppRes", "composeResources/test.generated/", true).toString())

            val type = it.loadClass("org.jetbrains.compose.resources.ResourceType").getField("DRAWABLE").get(null)
            val item = it.loadClass("org.jetbrains.compose.resources.ResourceItem").constructors.single { it.parameterCount == 7 }
            val paths = listOf("drawable/sample.xml" to emptyList<String>(), "drawable-dark/sample.xml" to listOf("dark"))
            val originalItems = paths.map { (path, qualifiers) -> item.newInstance(type, qualifiers, "sample", Paths.get(path), 42, -1L, -1L) }
            val original = specs.methods.single { method -> method.name == "getAccessorsSpecs" && method.parameterCount == 7 }
                .invoke(null, mapOf(type to mapOf("sample" to originalItems)), "test.generated", "commonMain",
                    "composeResources/test.generated/", "AppRes", true, true) as List<*>
            val items = paths.map { (path, qualifiers) -> ResourceItem(ResourceType.DRAWABLE, qualifiers, "sample", Paths.get(path), 42) }
            val current = getAccessorsSpecs(mapOf(ResourceType.DRAWABLE to mapOf("sample" to items)), "test.generated",
                "commonMain", "composeResources/test.generated/", "AppRes", true, true)
            assertEquals(original.map(Any?::toString), current.map(Any::toString))
        }
    }

    @Test
    fun enrollmentPreservesOwnershipQualifiersAndNonAndroidPaths() {
        val root = temporary.newFolder()
        val project = ProjectBuilder.builder().withProjectDir(root).build()
        val prepared = root.resolve("prepared")
        val first = root.resolve("first")
        val second = root.resolve("second")
        fun put(root: File, relative: String, value: String) = root.resolve(relative).apply { parentFile.mkdirs(); writeText(value) }
        put(first, "drawable/sample.xml", "<vector light='true'/>")
        put(first, "font/untouched.ttf", "font stays an asset")
        put(second, "drawable-dark/sample.xml", "<vector dark='true'/>")
        first.copyRecursively(prepared)
        second.copyRecursively(prepared)
        put(prepared, "drawable/authored.xml", "<vector authored='true'/>")
        val index = project.tasks.create("index", PrepareNativeAndroidXmlTask::class.java).apply {
            ownedDirectories.from(first, second)
            preparedDirectory.set(prepared)
            sourceSetName.set("commonMain")
            moduleDirectory.set("composeResources/test.generated/")
            resourcePackage.set("test.generated")
            resourcesDirectory.set(root.resolve("native/res"))
            locationsFile.set(root.resolve("native/locations.tsv"))
        }
        index.prepare()
        val locations = readNativeXmlLocations(listOf(index.locationsFile.get().asFile))
        assertEquals(2, locations.size)
        assertEquals(2, locations.map { it.resourceName }.distinct().size)
        locations.forEach { location ->
            assertEquals(prepared.resolve(location.relativePath).readText(),
                index.resourcesDirectory.get().asFile.resolve("raw/${location.resourceName}.xml").readText())
        }
        assertEquals(2, index.resourcesDirectory.get().asFile.walkTopDown().filter(File::isFile).count())
        val items = locations.map { ResourceItem(ResourceType.DRAWABLE,
            if (it.relativePath.startsWith("drawable-dark")) listOf("dark") else emptyList(),
            "sample", Paths.get(it.relativePath), 42) }
        val nativeAccessors = getAccessorsSpecs(mapOf(ResourceType.DRAWABLE to mapOf("sample" to items)),
            "test.generated", "commonMain", "composeResources/test.generated/", "AppRes", true, true,
            locations.associateBy { it.relativePath }).joinToString("\n")
        assertTrue("private object _NativeXml_CommonMainDrawable0_sample" in nativeAccessors)
        assertTrue("_NativeXml_CommonMainDrawable0_sample.value" in nativeAccessors, nativeAccessors)
        assertTrue("@delegate:ResourceContentHash(1_344)" in nativeAccessors)
        assertTrue("by lazy {" in nativeAccessors, nativeAccessors)
        assertFalse("AppRes.drawable.sample: DrawableResource by lazy" in nativeAccessors)
        val task = project.tasks.create("locations", GenerateNativeXmlLocationsTask::class.java).apply {
            manifests.from(index.locationsFile)
            nativeResourceDirectories.from(index.resourcesDirectory)
            packageName.set("test.generated")
            resClassName.set("AppRes")
            moduleDirectory.set("composeResources/test.generated/")
            sourceSetName.set("androidMain")
            androidNamespace.set("test.android")
            androidTarget.set(true)
            codeDirectory.set(root.resolve("locations"))
            resourcesDirectory.set(root.resolve("locations-res"))
        }
        task.generate()
        val android = root.resolve("locations").walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue("getAndroidResourcePath" in android)
        locations.forEach { assertTrue("R.raw.${it.resourceName}" in android) }
        assertFalse("authored.xml" in android)
        assertFalse("untouched.ttf" in android)
        task.androidTarget.set(false)
        task.sourceSetName.set("jvmMain")
        task.generate()
        val jvm = root.resolve("locations").walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertFalse("R.raw." in jvm)
        assertFalse("getAndroidResourcePath" in jvm)
        locations.forEach { assertTrue(it.logicalPath in jvm) }
        assertFalse(root.resolve("locations/test/generated/NativeXmlRawPaths0.kt").exists())
        put(first, "drawable/sample.xml", "changed owner bytes")
        assertFailsWith<IllegalArgumentException> { index.prepare() }
    }
}
