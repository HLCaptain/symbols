import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class MaterialVectorLibraryPlugin : KotlinMultiplatformLibraryPlugin() {
    override fun apply(target: Project) = with(target) {
        super.apply(target)
        pluginManager.apply(KmpPublishingPlugin::class.java)

        extensions.configure<KotlinMultiplatformExtension> {
            explicitApi()
            sourceSets.commonMain.dependencies {
                api(dependencies.project(mapOf("path" to ":modules:material-core")))
                api(defaultLibs.findLibrary("compose-ui").get())
            }
        }
    }
}
