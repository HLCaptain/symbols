package io.github.hlcaptain.symbols.gradle

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
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
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
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
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val font: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val conventionalFonts: ConfigurableFileCollection

    @get:Input
    public abstract val conventionalFontName: Property<String>

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

    @get:Input
    public abstract val emSize: Property<Float>

    @get:Input
    public abstract val originX: Property<Float>

    @get:Input
    public abstract val baselineY: Property<Float>

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
        val resolvedFont = resolveFont()

        val arguments = buildList {
            addAll(listOf("--manifest", manifest.get().asFile.absolutePath))
            addAll(listOf("--font", resolvedFont.absolutePath))
            addAll(listOf("--package", packageName.get()))
            addAll(listOf("--set", rootName.get()))
            addAll(listOf("--style", styleName.get()))
            addAll(listOf("--font-index", fontIndex.get().toString()))
            addAll(listOf("--resource-prefix", resourcePrefix.get()))
            addAll(listOf("--symbols-per-file", symbolsPerFile.get().toString()))
            addAll(listOf("--precision", precision.get().toString()))
            addAll(listOf("--viewport-width", viewportWidth.get().toString()))
            addAll(listOf("--viewport-height", viewportHeight.get().toString()))
            addAll(listOf("--em-size", emSize.get().toString()))
            addAll(listOf("--origin-x", originX.get().toString()))
            addAll(listOf("--baseline-y", baselineY.get().toString()))
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
            if (includedNames.get().isEmpty()) {
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

    private fun resolveFont(): File =
        font.orNull?.asFile ?: selectConventionalFont(
            styleName = styleName.get(),
            requestedName = conventionalFontName.get().ifEmpty { null },
            candidates = conventionalFonts.files,
        )

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

internal fun selectConventionalFont(
    styleName: String,
    requestedName: String?,
    candidates: Collection<File>,
): File {
    val supported = candidates
        .filter { file ->
            file.isFile &&
                file.extension.lowercase() in SupportedFontExtensions
        }
        .sortedBy(File::getAbsolutePath)
    val matches = if (requestedName != null) {
        supported.filter { file -> file.name == requestedName }
    } else {
        supported
    }
    if (matches.size == 1) {
        return matches.single()
    }

    val searched =
        "src/main/res/font and src/commonMain/composeResources/font"
    val available = supported.joinToString(transform = File::getAbsolutePath)
        .ifEmpty { "none" }
    throw InvalidUserDataException(
        if (requestedName != null) {
            "Expected exactly one '$requestedName' for style '$styleName' in " +
                "$searched; found ${matches.size}. Available fonts: $available."
        } else {
            "Cannot choose a font for style '$styleName' from $searched. " +
                "Available fonts: $available. Add one font, call " +
                "font(\"file.ttf\"), or set font explicitly."
        },
    )
}
