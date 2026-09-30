package org.jetbrains.compose.resources

import com.android.build.api.variant.Sources
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.asClassName
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.internal.utils.uppercaseFirstChar
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMetadataTarget
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

internal const val NATIVE_XML_CAPABILITY = "org.jetbrains.compose.components:components-resources-native-xml-v1"

internal data class NativeXmlLocation(
    val sourceSet: String,
    val relativePath: String,
    val logicalPath: String,
    val resourceName: String,
    val contentHash: String,
) {
    val factoryName: String get() = "_nativeXmlPath_$resourceName"
    fun line(): String = listOf(sourceSet, relativePath, logicalPath, resourceName, contentHash).joinToString("\t")
    fun matches(file: File): Boolean = file.readBytes().sha256() == contentHash
}

internal fun readNativeXmlLocations(files: Collection<File>): List<NativeXmlLocation> = files
    .flatMap { file -> file.readLines().filter(String::isNotBlank) }
    .map { line ->
        val fields = line.split('\t')
        require(fields.size == 5) { "Invalid native XML location manifest" }
        NativeXmlLocation(fields[0], fields[1], fields[2], fields[3], fields[4])
    }
    .sortedBy { it.logicalPath }

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this).joinToString("") { "%02x".format(it) }

/** Copies only enrolled XML bytes; ownership is checked against prepared Compose resources. */
@CacheableTask
internal abstract class PrepareNativeAndroidXmlTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val ownedDirectories: ConfigurableFileCollection

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val preparedDirectory: DirectoryProperty

    @get:Input abstract val sourceSetName: Property<String>
    @get:Input abstract val moduleDirectory: Property<String>
    @get:Input abstract val resourcePackage: Property<String>
    @get:OutputDirectory abstract val resourcesDirectory: DirectoryProperty
    @get:OutputFile abstract val locationsFile: RegularFileProperty

    @TaskAction
    fun prepare() {
        val owned = sortedMapOf<String, File>()
        ownedDirectories.files.forEach { root ->
            root.listNotHiddenFiles().filter { it.isDirectory && it.name.substringBefore('-') == "drawable" }
                .forEach { dir ->
                    dir.listNotHiddenFiles().filter { it.isFile && it.extension.equals("xml", true) }.forEach { file ->
                        val relative = file.relativeTo(root).invariantSeparatorsPath
                        require(relative.none { it == '\t' || it == '\n' || it == '\r' }) { "Invalid XML path: $relative" }
                        require(owned.put(relative, file) == null) { "Duplicate native XML ownership: $relative" }
                    }
                }
        }
        val locations = owned.map { (relative, file) ->
            val prepared = preparedDirectory.file(relative).get().asFile
            require(prepared.isFile && prepared.readBytes().contentEquals(file.readBytes())) {
                "Enrolled XML must match the effective Compose resource path and bytes: $relative"
            }
            val logical = moduleDirectory.get() + relative
            val key = (resourcePackage.get() + ":" + sourceSetName.get() + ":" + relative).encodeToByteArray().sha256()
            NativeXmlLocation(sourceSetName.get(), relative, logical, "compose_native_xml_$key", file.readBytes().sha256())
        }
        val output = resourcesDirectory.get().asFile
        check(output.deleteRecursively() || !output.exists())
        output.resolve("raw").mkdirs()
        locations.forEach { location ->
            owned.getValue(location.relativePath).copyTo(output.resolve("raw/${location.resourceName}.xml"))
        }
        locationsFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(locations.joinToString("\n", postfix = if (locations.isEmpty()) "" else "\n") { it.line() })
        }
    }
}

/** Per-file functions preserve R8 liveness; only raw lookup builds a complete-pack dispatch. */
@CacheableTask
internal abstract class GenerateNativeXmlLocationsTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifests: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeResourceDirectories: ConfigurableFileCollection
    @get:Input abstract val packageName: Property<String>
    @get:Input abstract val resClassName: Property<String>
    @get:Input abstract val moduleDirectory: Property<String>
    @get:Input abstract val sourceSetName: Property<String>
    @get:Input abstract val androidNamespace: Property<String>
    @get:Input abstract val androidTarget: Property<Boolean>
    @get:OutputDirectory abstract val codeDirectory: DirectoryProperty
    @get:OutputDirectory @get:Optional abstract val resourcesDirectory: DirectoryProperty
    @get:Inject protected abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun generate() {
        val locations = readNativeXmlLocations(manifests.files)
        require(locations.map { it.logicalPath }.distinct().size == locations.size) {
            "Native XML logical paths must have one owner across source sets"
        }
        val android = androidTarget.get()
        if (android) {
            fileSystem.sync {
                it.from(nativeResourceDirectories)
                it.into(resourcesDirectory)
                it.includeEmptyDirs = false
                it.duplicatesStrategy = DuplicatesStrategy.FAIL
            }
        }
        val files = mutableListOf<FileSpec>()
        val optIn = com.squareup.kotlinpoet.AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
            .addMember("%T::class", ClassName("org.jetbrains.compose.resources", "InternalResourceApi")).build()
        locations.chunked(100).forEachIndexed { index, chunk ->
            val file = FileSpec.builder(packageName.get(), "NativeXmlLocations$index").addAnnotation(optIn)
            chunk.forEach { location -> file.addFunction(FunSpec.builder(location.factoryName)
                .addModifiers(KModifier.INTERNAL)
                .apply { if (location.sourceSet != sourceSetName.get()) addModifiers(KModifier.ACTUAL) }
                .returns(String::class)
                .apply {
                    if (android) addStatement("return %M(%T.%N, %S)",
                        MemberName("org.jetbrains.compose.resources", "getAndroidResourcePath"),
                        ClassName(androidNamespace.get(), "R", "raw"), location.resourceName, location.logicalPath)
                    else addStatement("return %S", location.logicalPath)
                }.build()) }
            files += file.build()
        }
        val chunks = locations.chunked(64)
        val file = FileSpec.builder(packageName.get(), "NativeXmlRawPaths")
        val dispatch = FunSpec.builder(nativeXmlRawPathFunction(resClassName.get()))
            .addModifiers(KModifier.INTERNAL, KModifier.ACTUAL)
            .addParameter("path", String::class).returns(String::class)
        if (android) chunks.indices.forEach { index ->
            dispatch.addStatement("_nativeXmlRawPathChunk%L(path)?.let { return it }", index)
        }
        dispatch.addStatement("return %S + path", moduleDirectory.get())
        file.addFunction(dispatch.build())
        files += file.build()
        if (android) chunks.forEachIndexed { index, chunk ->
            val body = CodeBlock.builder().beginControlFlow("return when (path)")
            chunk.forEach { body.addStatement("%S -> %N()", it.relativePath, it.factoryName) }
            body.addStatement("else -> null").endControlFlow()
            files += FileSpec.builder(packageName.get(), "NativeXmlRawPaths$index")
                .addFunction(FunSpec.builder("_nativeXmlRawPathChunk$index").addModifiers(KModifier.INTERNAL)
                .addParameter("path", String::class).returns(String::class.asClassName().copy(nullable = true))
                .addCode(body.build()).build()).build()
        }
        val output = codeDirectory.get().asFile
        check(output.deleteRecursively() || !output.exists())
        files.forEach { it.writeTo(output) }
    }
}

internal fun nativeXmlRawPathFunction(resClassName: String) = "_nativeXmlRawPath_$resClassName"

internal fun Project.configureNativeAndroidXmlResources(
    kotlin: KotlinMultiplatformExtension,
    config: Provider<ResourcesExtension>,
    componentName: String,
    componentSourceSets: Collection<KotlinSourceSet>,
    sources: Sources,
    namespace: Provider<String>,
    assets: TaskProvider<CopyResourcesToAndroidAssetsTask>,
    moduleResourceDir: Provider<File>?,
) {
    val enrollment = config.get().androidNativeXmlDirectories
    if (enrollment.isEmpty()) return
    val moduleDirectory = moduleResourceDir?.map { it.invariantSeparatorsPath + "/" } ?: provider { "" }
    val packageName = config.getResourcePackage(this)
    val index = enrollment.mapValues { (name, directories) ->
        val sourceSet = kotlin.sourceSets.getByName(name)
        val taskName = "prepareNativeAndroidXmlFor${name.uppercaseFirstChar()}"
        val task = if (taskName in tasks.names) tasks.named(taskName, PrepareNativeAndroidXmlTask::class.java)
        else tasks.register(taskName, PrepareNativeAndroidXmlTask::class.java) {
            it.ownedDirectories.from(directories)
            it.preparedDirectory.set(layout.dir(getPreparedComposeResourcesDir(sourceSet)))
            it.sourceSetName.set(name)
            it.moduleDirectory.set(moduleDirectory)
            it.resourcePackage.set(packageName)
            it.resourcesDirectory.set(layout.buildDirectory.dir("$RES_GEN_DIR/nativeAndroidXml/$name/res"))
            it.locationsFile.set(layout.buildDirectory.file("$RES_GEN_DIR/nativeAndroidXml/$name/locations.tsv"))
        }
        tasks.named(sourceSet.getResourceAccessorsGenerationTaskName(), GenerateResourceAccessorsTask::class.java).configure {
            it.nativeXmlLocations.set(task.flatMap { task -> task.locationsFile })
            it.nativeXmlExpectDeclarations.set(kotlin.targets.filter { it !is KotlinMetadataTarget }.flatMap { target -> target.compilations }
                .none { compilation -> compilation.defaultSourceSet == sourceSet })
        }
        task
    }
    tasks.named("generateComposeResClass", GenerateResClassTask::class.java).configure { it.nativeXmlResources.set(true) }

    fun locationsTask(name: String, sourceSet: KotlinSourceSet, android: Boolean,
                      inputs: Provider<List<TaskProvider<PrepareNativeAndroidXmlTask>>>): TaskProvider<GenerateNativeXmlLocationsTask> {
        if (name in tasks.names) return tasks.named(name, GenerateNativeXmlLocationsTask::class.java)
        return tasks.register(name, GenerateNativeXmlLocationsTask::class.java) {
            it.manifests.from(inputs.map { tasks -> tasks.map { task -> task.flatMap { task -> task.locationsFile } } })
            it.packageName.set(packageName)
            it.resClassName.set(config.map { it.nameOfResClass })
            it.moduleDirectory.set(moduleDirectory)
            it.sourceSetName.set(sourceSet.name)
            it.androidNamespace.set(if (android) namespace else provider { "" })
            it.androidTarget.set(android)
            it.codeDirectory.set(layout.buildDirectory.dir("$RES_GEN_DIR/nativeAndroidXml/$name/kotlin"))
            if (android) {
                it.nativeResourceDirectories.from(inputs.map { tasks -> tasks.map { task -> task.flatMap { task -> task.resourcesDirectory } } })
                it.resourcesDirectory.set(layout.buildDirectory.dir("$RES_GEN_DIR/nativeAndroidXml/$name/res"))
            }
        }
    }
    val compilation = kotlin.targets.filter { it.platformType == KotlinPlatformType.androidJvm }
        .flatMap { it.compilations }.single { it.defaultSourceSet in componentSourceSets }
    val componentSourceSet = compilation.defaultSourceSet
    // KMP finalizes the default hierarchy after AGP's variant callbacks. Keep this
    // selection lazy, just like getAndroidKmpComponentComposeResources.
    val selected = provider {
        val names = compilation.allKotlinSourceSets.map { it.name }.toSet()
        index.filterKeys { it in names }.values.toList()
    }
    val locations = locationsTask("generateNativeXmlLocationsFor${componentName.uppercaseFirstChar()}",
        componentSourceSet, true, selected)
    componentSourceSet.kotlin.srcDir(locations.flatMap { it.codeDirectory })
    requireNotNull(sources.res) { "Enable Android resources before enrolling native XML resources" }
        .addGeneratedSourceDirectory(locations, GenerateNativeXmlLocationsTask::resourcesDirectory)
    assets.configure { it.nativeXmlLocations.from(selected.map { tasks -> tasks.map { task -> task.flatMap { task -> task.locationsFile } } }) }
    val configuration = configurations.getByName(componentSourceSet.implementationConfigurationName)
    configuration.withDependencies { configured ->
        if (selected.get().isNotEmpty() && configured.none { it is ModuleDependency && it.requestedCapabilities.any { it.name == "components-resources-native-xml-v1" } }) {
            val runtime = dependencies.create(ComposePlugin.CommonComponentsDependencies.resources
                .replace(":components-resources:", ":components-resources-android:")) as ModuleDependency
            runtime.capabilities { it.requireCapability(NATIVE_XML_CAPABILITY) }
            configured.add(runtime)
        }
    }
    kotlin.targets.filter { it !is KotlinMetadataTarget && it.platformType != KotlinPlatformType.androidJvm }.forEach { target ->
        target.compilations.filter { it.name == KotlinCompilation.MAIN_COMPILATION_NAME }.forEach { compilation ->
            val inputs = provider {
                val names = compilation.allKotlinSourceSets.map { it.name }.toSet()
                index.filterKeys { it in names }.values.toList()
            }
            val task = locationsTask("generateNativeXmlLocationsFor${target.name.uppercaseFirstChar()}Main",
                compilation.defaultSourceSet, false, inputs)
            compilation.defaultSourceSet.kotlin.srcDir(task.flatMap { it.codeDirectory })
        }
    }
}
