import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class SampleFeaturePlugin : ComposeMultiplatformLibraryPlugin() {
    override fun apply(target: Project) = with(target) {
        super.apply(target)
        listOf(
            "com.github.gmazzo.buildconfig",
            "io.insert-koin.compiler.plugin",
        ).forEach(pluginManager::apply)

        val packageSuffix = name.replace("-", "")
        val namespace = "io.github.hlcaptain.symbols.sample.$packageSuffix"

        extensions.configure<KotlinMultiplatformExtension> {
            targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach { android ->
                android.namespace = namespace
                android.minSdk = defaultLibs.findVersion("sample-android-minSdk")
                    .get()
                    .requiredVersion
                    .toInt()
            }
            compilerOptions {
                optIn.add("org.koin.core.annotation.KoinExperimentalAPI")
            }

            sourceSets.commonMain.dependencies {
                api(project.dependencies.project(mapOf("path" to ":samples:api")))
                implementation(
                    project.dependencies.project(mapOf("path" to ":samples:ui:components")),
                )
                implementation(defaultLibs.findLibrary("compose-material3").get())
                implementation(defaultLibs.findLibrary("koin-annotations").get())
                implementation(defaultLibs.findLibrary("koin-core").get())
            }
        }

        extensions.configure<BuildConfigExtension> {
            packageName.set("$namespace.config")
            className.set("SampleBuildConfig")
            useKotlinOutput()
            buildConfigField(String::class.java, "MODULE_PATH", project.path)
        }
    }
}
