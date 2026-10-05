import com.android.build.api.dsl.LibraryExtension
import io.github.hlcaptain.symbols.gradle.SymbolFontsExtension
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

/** Configures one independently selected native drawable family. */
fun Project.configureMaterialDrawables(
    materialStyle: String,
    filled: Boolean = false,
    mirrored: Boolean = false,
) {
    val title = materialStyle.replaceFirstChar(Char::uppercaseChar)
    val name = (if (mirrored) "Automirrored" else "") + title +
        (if (filled) "Filled" else "")
    extensions.configure<SymbolFontsExtension> {
        iconSet("MaterialSymbols") { iconSet ->
            iconSet.style(name) { variant ->
                variant.codepoints.set(
                    rootProject.layout.projectDirectory.file("fonts/material/MaterialSymbols.codepoints"),
                )
                variant.font.set(
                    rootProject.layout.projectDirectory.file(
                        if (filled) {
                            "fonts/material/$materialStyle/composeResources/font/" +
                                "material_symbols_${materialStyle}_variable.ttf"
                        } else {
                            "fonts/material/$materialStyle-static/composeResources/font/" +
                                "material_symbols_${materialStyle}_regular.ttf"
                        },
                    ),
                )
                if (filled) {
                    variant.axis("FILL", 1f)
                    variant.axis("wght", 400f)
                    variant.axis("GRAD", 0f)
                    variant.axis("opsz", 24f)
                }
                variant.resourcePrefix.set("material_symbols")
                variant.autoMirror.set(mirrored)
                variant.androidDrawables()
            }
        }
    }
}
