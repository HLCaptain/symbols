package io.github.hlcaptain.symbols.gradle

import io.github.hlcaptain.symbols.generator.GeneratedFileWriter
import io.github.hlcaptain.symbols.generator.KotlinSymbolCatalogsRenderer
import io.github.hlcaptain.symbols.generator.SymbolManifestParser
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/** Generates common Kotlin runtime catalogs from codepoint manifests. */
@CacheableTask
public abstract class GenerateSymbolCatalogsTask : DefaultTask() {
    @get:Input
    @get:Optional
    public abstract val packageName: Property<String>

    @get:Nested
    public abstract val catalogs: ListProperty<SymbolCatalogSpec>

    @get:OutputDirectory
    public abstract val outputDirectory: DirectoryProperty

    @TaskAction
    protected fun generate() {
        val configuredCatalogs = catalogs.get().sortedBy(SymbolCatalogSpec::getName)
        if (configuredCatalogs.isEmpty()) {
            clearOutput()
            return
        }
        require(packageName.isPresent) {
            "symbolFonts.catalogPackageName must be set when catalogs are configured"
        }

        GeneratedFileWriter.synchronize(
            outputDirectory = outputDirectory.get().asFile.toPath(),
            renderedFiles = KotlinSymbolCatalogsRenderer().render(
                packageName = packageName.get(),
                catalogs = configuredCatalogs.associate { catalog ->
                    catalog.name to SymbolManifestParser.parse(
                        catalog.codepoints.get().asFile.toPath(),
                    )
                },
            ),
        )
    }

    private fun clearOutput() {
        val output = outputDirectory.get().asFile
        check(output.deleteRecursively() || !output.exists()) {
            "Unable to clear generated symbol catalogs: $output"
        }
        check(output.mkdirs() || output.isDirectory) {
            "Unable to create generated symbol catalog directory: $output"
        }
    }
}
