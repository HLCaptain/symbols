package io.github.hlcaptain.symbols.gradle

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/** Generates typed symbol-font descriptors for Compose font resources. */
@CacheableTask
abstract class GenerateSymbolFontDescriptorsTask : DefaultTask() {
    @get:Classpath
    abstract val generatorClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceRoots: ConfigurableFileCollection

    @get:Input
    abstract val resourcePackage: Property<String>

    @get:Input
    abstract val resourceClassName: Property<String>

    @get:Input
    abstract val publicAccessors: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    protected fun generate() {
        val roots = resourceRoots.files.sortedBy(File::getAbsolutePath)
        if (roots.isEmpty()) {
            val output = outputDirectory.get().asFile
            check(output.deleteRecursively() || !output.exists()) {
                "Unable to clear generated font descriptors: $output"
            }
            check(output.mkdirs() || output.isDirectory) {
                "Unable to create generated font descriptor directory: $output"
            }
            return
        }

        val arguments = buildList {
            roots.forEach { root ->
                addAll(listOf("--resource-root", root.absolutePath))
            }
            addAll(listOf("--package", resourcePackage.get()))
            addAll(listOf("--res-class", resourceClassName.get()))
            addAll(listOf("--output", outputDirectory.get().asFile.absolutePath))
            if (publicAccessors.get()) {
                add("--public")
            }
        }
        execOperations.javaexec { spec ->
            spec.classpath(generatorClasspath)
            spec.mainClass.set(GeneratorMainClass)
            spec.args(arguments)
            spec.systemProperty("java.awt.headless", "true")
        }.assertNormalExitValue()
    }

    private companion object {
        private const val GeneratorMainClass: String =
            "io.github.hlcaptain.symbols.generator.SymbolFontDescriptorsCli"
    }
}
