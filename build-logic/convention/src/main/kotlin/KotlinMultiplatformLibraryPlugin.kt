import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

open class KotlinMultiplatformLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(defaultLibs.findPlugin("kotlinMultiplatform").get().get().pluginId)
        pluginManager.apply(defaultLibs.findPlugin("androidMultiplatformLibrary").get().get().pluginId)
        pluginManager.apply(defaultLibs.findPlugin("androidLint").get().get().pluginId)

        configureLibraryTargets()
    }
}

@OptIn(ExperimentalWasmDsl::class)
private fun Project.configureLibraryTargets() {
    extensions.configure<KotlinMultiplatformExtension> {
        jvmToolchain(17)

        targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach { android ->
            // Non-Compose libraries retain the SDK supported by AGP 8.13 consumers.
            android.compileSdk = 36
            android.minSdk = defaultLibs.findVersion("android-minSdk").get().requiredVersion.toInt()
            // Preserve the existing consumer SDK floor when AGP 9 defaults change.
            android.aarMetadata.minCompileSdk = android.compileSdk
            android.compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        }
        jvm {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        }
        js {
            browser()
            nodejs()
        }
        wasmJs {
            browser()
            nodejs()
        }
        iosArm64()
        iosSimulatorArm64()

        applyDefaultHierarchyTemplate()

        sourceSets.commonTest.dependencies {
            implementation(defaultLibs.findLibrary("kotlin-test").get())
        }
    }
}

internal fun Project.configureAndroidLibrary(extension: LibraryExtension) = with(extension) {
    // Native drawable AARs remain consumable with AGP 8.13 and compileSdk 36.
    compileSdk = 36

    defaultConfig {
        minSdk = defaultLibs.findVersion("android-minSdk").get().requiredVersion.toInt()
        aarMetadata.minCompileSdk = compileSdk
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
