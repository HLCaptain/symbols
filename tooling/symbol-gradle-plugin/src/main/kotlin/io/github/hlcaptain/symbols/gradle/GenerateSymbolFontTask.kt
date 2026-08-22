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
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/** Cacheable, isolated build-time extraction of one font or SVG style. */
@CacheableTask
abstract class GenerateSymbolFontTask : DefaultTask() {
    @get:Classpath
    abstract val generatorClasspath: ConfigurableFileCollection

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val svgDirectory: DirectoryProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val font: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val conventionalFonts: ConfigurableFileCollection

    @get:Input
    abstract val conventionalFontName: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val rootName: Property<String>

    @get:Input
    abstract val styleName: Property<String>

    @get:Input
    abstract val fontIndex: Property<Int>

    @get:Input
    abstract val axes: MapProperty<String, Float>

    @get:Input
    abstract val generateImageVectors: Property<Boolean>

    @get:Input
    abstract val generateAndroidDrawables: Property<Boolean>

    @get:Input
    abstract val generateComposeDrawables: Property<Boolean>

    @get:Input
    abstract val resourcePrefix: Property<String>

    @get:Input
    abstract val symbolsPerFile: Property<Int>

    @get:Input
    abstract val precision: Property<Int>

    @get:Input
    abstract val viewportWidth: Property<Float>

    @get:Input
    abstract val viewportHeight: Property<Float>

    @get:Input
    abstract val emSize: Property<Float>

    @get:Input
    abstract val originX: Property<Float>

    @get:Input
    abstract val baselineY: Property<Float>

    @get:OutputDirectory
    abstract val kotlinOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val androidOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val composeOutputDirectory: DirectoryProperty

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
        val svgSource = svgDirectory.orNull
        val resolvedFont = if (svgSource == null) resolveFont() else null

        val arguments = buildList {
            if (svgSource != null) {
                addAll(
                    listOf(
                        "--svg-directory",
                        svgSource.asFile.absolutePath,
                    ),
                )
            } else {
                addAll(listOf("--manifest", manifest.get().asFile.absolutePath))
                addAll(listOf("--font", requireNotNull(resolvedFont).absolutePath))
            }
            addAll(listOf("--package", packageName.get()))
            addAll(listOf("--set", rootName.get()))
            addAll(listOf("--style", styleName.get()))
            addAll(listOf("--resource-prefix", resourcePrefix.get()))
            addAll(listOf("--symbols-per-file", symbolsPerFile.get().toString()))
            addAll(listOf("--precision", precision.get().toString()))
            addAll(listOf("--viewport-width", viewportWidth.get().toString()))
            addAll(listOf("--viewport-height", viewportHeight.get().toString()))
            if (svgSource == null) {
                addAll(listOf("--font-index", fontIndex.get().toString()))
                addAll(listOf("--em-size", emSize.get().toString()))
                addAll(listOf("--origin-x", originX.get().toString()))
                addAll(listOf("--baseline-y", baselineY.get().toString()))
            }
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

            if (svgSource == null) {
                axes.get().toSortedMap().forEach { (tag, value) ->
                    addAll(listOf("--axis", "$tag=$value"))
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
