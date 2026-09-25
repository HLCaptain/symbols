import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

class PublishedAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(defaultLibs.findPlugin("androidLibrary").get().get().pluginId)
        pluginManager.apply("maven-publish")

        extensions.configure<LibraryExtension> {
            configureAndroidLibrary(this)
            // These publications contain Android drawable resources only.
            enableKotlin = false
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        val releasePublication = extensions
            .getByType(PublishingExtension::class.java)
            .publications
            .register<MavenPublication>("release") {
                artifactId = "symbols-${project.name}"
            }
        components.matching { it.name == "release" }.all { releaseComponent ->
            releasePublication.configure { publication ->
                publication.from(releaseComponent)
            }
        }
    }
}
