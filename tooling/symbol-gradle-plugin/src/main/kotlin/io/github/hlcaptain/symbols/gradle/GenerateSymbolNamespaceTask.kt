package io.github.hlcaptain.symbols.gradle

import io.github.hlcaptain.symbols.generator.GeneratedFileWriter
import io.github.hlcaptain.symbols.generator.KotlinIconNamespaceRenderer
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/** Generates the one root object shared by independently cacheable styles. */
@CacheableTask
public abstract class GenerateSymbolNamespaceTask : DefaultTask() {
    @get:Input
    public abstract val packageName: Property<String>

    @get:Input
    public abstract val rootName: Property<String>

    @get:Input
    public abstract val styleNames: SetProperty<String>

    @get:OutputDirectory
    public abstract val outputDirectory: DirectoryProperty

    @TaskAction
    protected fun generate() {
        GeneratedFileWriter.synchronize(
            outputDirectory = outputDirectory.get().asFile.toPath(),
            renderedFiles = KotlinIconNamespaceRenderer().render(
                packageName = packageName.get(),
                iconSetName = rootName.get(),
                styleNames = styleNames.get(),
            ),
        )
    }
}
