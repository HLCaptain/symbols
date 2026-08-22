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
        pluginManager.apply(defaultLibs.findPlugin("androidLibrary").get().get().pluginId)
        pluginManager.apply(defaultLibs.findPlugin("kotlinMultiplatform").get().get().pluginId)

        configureLibraryTargets()
        extensions.configure<LibraryExtension> {
            configureAndroidLibrary(this)
        }
    }
}

@OptIn(ExperimentalWasmDsl::class)
private fun Project.configureLibraryTargets() {
    extensions.configure<KotlinMultiplatformExtension> {
        jvmToolchain(17)

        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
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
    compileSdk = defaultLibs.findVersion("android-compileSdk").get().requiredVersion.toInt()

    defaultConfig {
        minSdk = defaultLibs.findVersion("android-minSdk").get().requiredVersion.toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
