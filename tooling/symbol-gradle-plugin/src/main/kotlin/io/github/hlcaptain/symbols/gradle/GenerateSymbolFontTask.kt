package io.github.hlcaptain.symbols.gradle

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/** Cacheable, isolated build-time extraction of one symbol-font style. */
@CacheableTask
public abstract class GenerateSymbolFontTask : DefaultTask() {
    @get:Classpath
    public abstract val generatorClasspath: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val manifest: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val font: RegularFileProperty

    @get:Input
    public abstract val packageName: Property<String>

    @get:Input
    public abstract val rootName: Property<String>

    @get:Input
    public abstract val styleName: Property<String>

    @get:Input
    public abstract val fontIndex: Property<Int>

    @get:Input
    public abstract val axes: MapProperty<String, Float>

    @get:Input
    public abstract val includedNames: SetProperty<String>

    @get:Input
    public abstract val allSymbols: Property<Boolean>

    @get:Input
    public abstract val generateImageVectors: Property<Boolean>

    @get:Input
    public abstract val generateAndroidDrawables: Property<Boolean>

    @get:Input
    public abstract val generateComposeDrawables: Property<Boolean>

    @get:Input
    public abstract val resourcePrefix: Property<String>

    @get:Input
    public abstract val symbolsPerFile: Property<Int>

    @get:Input
    public abstract val precision: Property<Int>

    @get:Input
    public abstract val viewportWidth: Property<Float>

    @get:Input
    public abstract val viewportHeight: Property<Float>

    @get:OutputDirectory
    public abstract val kotlinOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    public abstract val androidOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    public abstract val composeOutputDirectory: DirectoryProperty

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    protected fun generate() {
        val outputKinds = buildList {
            if (generateImageVectors.get()) {
                add("ImageVectors")
            }
            if (generateAndroidDrawables.get()) {
                add("Android drawables")
            }
            if (generateComposeDrawables.get()) {
                add("Compose drawables")
            }
        }
        require(outputKinds.isNotEmpty()) {
            "Style ${styleName.get()} has no output. Call imageVectors(), " +
                "androidDrawables(), and/or composeDrawables()."
        }
        require(allSymbols.get() || includedNames.get().isNotEmpty()) {
            "Icon set ${rootName.get()} has no selected symbols. Call include(...) " +
                "or opt into includeAll()."
        }

        val arguments = buildList {
            addAll(listOf("--manifest", manifest.get().asFile.absolutePath))
            addAll(listOf("--font", font.get().asFile.absolutePath))
            addAll(listOf("--package", packageName.get()))
            addAll(listOf("--set", rootName.get()))
            addAll(listOf("--style", styleName.get()))
            addAll(listOf("--font-index", fontIndex.get().toString()))
            addAll(listOf("--resource-prefix", resourcePrefix.get()))
            addAll(listOf("--symbols-per-file", symbolsPerFile.get().toString()))
            addAll(listOf("--precision", precision.get().toString()))
            addAll(listOf("--viewport-width", viewportWidth.get().toString()))
            addAll(listOf("--viewport-height", viewportHeight.get().toString()))
            if (generateImageVectors.get()) {
                add("--omit-kotlin-namespace")
                addAll(
                    listOf(
                        "--kotlin-output",
                        kotlinOutputDirectory.get().asFile.absolutePath,
                    ),
                )
            }
            if (generateAndroidDrawables.get()) {
                addAll(
                    listOf(
                        "--android-output",
                        androidOutputDirectory.get().asFile.absolutePath,
                    ),
                )
            }
            if (generateComposeDrawables.get()) {
                addAll(
                    listOf(
                        "--compose-output",
                        composeOutputDirectory.get().asFile.absolutePath,
                    ),
                )
            }

            axes.get().toSortedMap().forEach { (tag, value) ->
                addAll(listOf("--axis", "$tag=$value"))
            }
            if (allSymbols.get()) {
                add("--include-all")
            } else {
                includedNames.get().sorted().forEach { name ->
                    addAll(listOf("--include", name))
                }
            }
        }

        clearDisabledOutputs()
        outputDirectories().forEach(File::mkdirs)
        execOperations.javaexec { spec ->
            spec.classpath(generatorClasspath)
            spec.mainClass.set(GeneratorMainClass)
            spec.args(arguments)
            spec.maxHeapSize = "768m"
            spec.systemProperty("java.awt.headless", "true")
        }.assertNormalExitValue()
    }

    private fun outputDirectories(): List<File> = listOf(
        kotlinOutputDirectory.get().asFile,
        androidOutputDirectory.get().asFile,
        composeOutputDirectory.get().asFile,
    )

    private fun clearDisabledOutputs() {
        val disabled = buildList {
            if (!generateImageVectors.get()) {
                add(kotlinOutputDirectory.get().asFile)
            }
            if (!generateAndroidDrawables.get()) {
                add(androidOutputDirectory.get().asFile)
            }
            if (!generateComposeDrawables.get()) {
                add(composeOutputDirectory.get().asFile)
            }
        }
        disabled.forEach { directory ->
            check(directory.deleteRecursively() || !directory.exists()) {
                "Unable to clear disabled generated output: $directory"
            }
        }
    }

    private companion object {
        private const val GeneratorMainClass: String =
            "io.github.hlcaptain.symbols.generator.MainKt"
    }
}
