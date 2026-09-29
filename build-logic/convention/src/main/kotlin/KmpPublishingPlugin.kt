import org.gradle.api.Plugin
import org.gradle.api.Project

class KmpPublishingPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("maven-publish")
    }
}
