import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.koinCompiler)
}

val featureSampleProfiles = listOf(
    "material-static",
    "material-variable",
    "custom-static",
    "custom-variable",
    "image-vector-migration",
    "android-views",
    "theming",
    "runtime-axes",
)
val symbolsSampleProfile = providers
    .gradleProperty("symbolsSampleProfile")
    .orElse("all")
    .get()
require(
    symbolsSampleProfile == "shell" ||
        symbolsSampleProfile == "all" ||
        symbolsSampleProfile in featureSampleProfiles,
) {
    "symbolsSampleProfile must be shell, all, or one of: " +
        featureSampleProfiles.joinToString()
}
val enabledSampleProfiles = when (symbolsSampleProfile) {
    "shell" -> emptySet()
    "all" -> featureSampleProfiles.toSet()
    else -> setOf(symbolsSampleProfile)
}

kotlin {
    android {
        namespace = "io.github.hlcaptain.symbols.sample.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.sample.android.minSdk.get().toInt()
        androidResources.enable = true
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Symbols"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.samples.api)
            implementation(projects.samples.ui.components)
            if ("material-static" in enabledSampleProfiles) {
                implementation(projects.samples.materialStatic)
            }
            if ("material-variable" in enabledSampleProfiles) {
                implementation(projects.samples.materialVariable)
            }
            if ("custom-static" in enabledSampleProfiles) {
                implementation(projects.samples.customStatic)
            }
            if ("custom-variable" in enabledSampleProfiles) {
                implementation(projects.samples.customVariable)
            }
            if ("image-vector-migration" in enabledSampleProfiles) {
                implementation(projects.samples.imageVectorMigration)
            }
            if ("android-views" in enabledSampleProfiles) {
                implementation(projects.samples.androidViews)
            }
            if ("theming" in enabledSampleProfiles) {
                implementation(projects.samples.theming)
            }
            if ("runtime-axes" in enabledSampleProfiles) {
                implementation(projects.samples.runtimeAxes)
            }
            implementation(projects.modules.materialVectorsRounded)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.koin.compose)
            implementation(libs.koin.annotations)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

// Keep generated resource initializers below Binaryen 125's quadratic local-coalescing cliff.
// This bounds inlining while retaining Kotlin's complete optimization pass sequence.
tasks.withType<BinaryenExec>().configureEach {
    binaryenArgs.add(0, "--inline-max-combined-binary-size=32768")
}

compose.desktop {
    application {
        mainClass = "io.github.hlcaptain.symbols.sample.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "symbols-sample"
            // macOS DMG metadata requires a positive major version.
            packageVersion = "1.0.0"
        }
    }
}
