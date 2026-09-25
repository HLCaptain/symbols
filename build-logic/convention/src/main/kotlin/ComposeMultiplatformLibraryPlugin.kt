import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

open class ComposeMultiplatformLibraryPlugin : KotlinMultiplatformLibraryPlugin() {
    override fun apply(target: Project) = with(target) {
        super.apply(target)
        pluginManager.apply(defaultLibs.findPlugin("composeMultiplatform").get().get().pluginId)
        pluginManager.apply(defaultLibs.findPlugin("composeCompiler").get().get().pluginId)
        configureComposeAndroidTarget()
        extensions.configure<KotlinMultiplatformExtension> {
            targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach { android ->
                android.androidResources.enable = true
            }
        }
    }
}

internal fun Project.configureComposeAndroidTarget() {
    extensions.configure<KotlinMultiplatformExtension> {
        targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach { android ->
            android.compileSdk = defaultLibs.findVersion("android-compileSdk").get().requiredVersion.toInt()
            android.minSdk = defaultLibs.findVersion("compose-android-minSdk").get().requiredVersion.toInt()
            android.aarMetadata.minCompileSdk = android.compileSdk
        }
    }
}
