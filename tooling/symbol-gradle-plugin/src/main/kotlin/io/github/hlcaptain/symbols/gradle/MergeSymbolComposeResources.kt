package io.github.hlcaptain.symbols.gradle

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Merges independently cacheable style outputs into one Compose resource root. */
@CacheableTask
public abstract class MergeSymbolComposeResources : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val inputDirectories: ConfigurableFileCollection

    @get:OutputDirectory
    public abstract val outputDirectory: DirectoryProperty

    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    protected fun merge() {
        rejectDuplicateTargetPaths()
        fileSystemOperations.sync { spec ->
            spec.from(inputDirectories)
            spec.exclude(".symbols-generated-files")
            spec.exclude("**/.symbols-generated-files")
            spec.duplicatesStrategy = DuplicatesStrategy.FAIL
            spec.into(outputDirectory)
        }
    }

    private fun rejectDuplicateTargetPaths() {
        val owners = linkedMapOf<String, String>()
        inputDirectories.files
            .sortedBy { directory -> directory.absolutePath }
            .forEach { directory ->
                if (!directory.exists()) {
                    return@forEach
                }
                directory.walkTopDown()
                    .filter { file ->
                        file.isFile &&
                            file.name != ".symbols-generated-files"
                    }
                    .forEach { file ->
                        val targetPath = file
                            .relativeTo(directory)
                            .invariantSeparatorsPath
                        val owner = file.absolutePath
                        val existingOwner = owners.putIfAbsent(targetPath, owner)
                        if (existingOwner != null && existingOwner != owner) {
                            throw InvalidUserDataException(
                                "Generated Compose resource target collision " +
                                    "'$targetPath': $existingOwner and $owner",
                            )
                        }
                    }
            }
    }
}
