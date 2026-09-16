import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Generates the built-in Material vectors without changing their published API. */
class MaterialVectorSourcesPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val style = name.removePrefix("material-vectors-")
        require(style in setOf("outlined", "rounded", "sharp", "themed")) {
            "Unsupported Material vector project: $name"
        }
        val sources = tasks.register<GenerateMaterialVectors>("generateMaterialVectors") {
            group = "generation"
            description = "Generates the $style Material vector Kotlin sources."
            this.style.set(style)
            generator.set(rootProject.layout.projectDirectory.file("tools/generate_material_vectors.py"))
            manifest.set(rootProject.layout.projectDirectory.file("fonts/material/MaterialSymbols.codepoints"))
            requirements.set(rootProject.layout.projectDirectory.file("tools/requirements-font-verification.txt"))
            if (style != "themed") {
                font.set(rootProject.layout.projectDirectory.file(
                    "fonts/material/$style/composeResources/font/material_symbols_${style}_variable.ttf",
                ))
            }
            pythonExecutable.convention(providers.gradleProperty("symbolsPython")
                .orElse(providers.environmentVariable("SYMBOLS_PYTHON"))
                .orElse("python3"))
            outputDirectory.set(layout.buildDirectory.dir("generated/materialVectors/commonMain/kotlin"))
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.named("commonMain") { sourceSet ->
                    sourceSet.kotlin.srcDir(sources.flatMap { it.outputDirectory })
                }
            }
            tasks.matching { it.name == "prepareKotlinIdeaImport" }.configureEach { task ->
                task.dependsOn(sources)
            }
        }
    }
}

@CacheableTask
abstract class GenerateMaterialVectors : DefaultTask() {
    @get:Input abstract val style: Property<String>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val generator: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val requirements: RegularFileProperty
    @get:Optional @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val font: RegularFileProperty
    // The generator enforces the pinned FontTools version; interpreter location is machine-local.
    @get:Internal abstract val pythonExecutable: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:Inject protected abstract val execOperations: ExecOperations

    @TaskAction
    protected fun generate() {
        execOperations.exec { spec ->
            spec.commandLine(pythonExecutable.get(), generator.get().asFile,
                "--style", style.get(), "--output", outputDirectory.get().asFile)
        }.assertNormalExitValue()
    }
}
