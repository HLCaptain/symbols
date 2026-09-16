import org.gradle.api.Project

open class ComposeMultiplatformLibraryPlugin : KotlinMultiplatformLibraryPlugin() {
    override fun apply(target: Project) = with(target) {
        super.apply(target)
        pluginManager.apply(defaultLibs.findPlugin("composeMultiplatform").get().get().pluginId)
        pluginManager.apply(defaultLibs.findPlugin("composeCompiler").get().get().pluginId)
    }
}
