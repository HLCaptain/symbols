import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
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
val compressSymbolFonts = providers
    .gradleProperty("compressSymbolFonts")
    .map(String::toBoolean)
    .orElse(false)

kotlin {
    androidTarget {
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
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
        }
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

android {
    namespace = "io.github.hlcaptain.symbols.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.hlcaptain.symbols.sample"
        minSdk = libs.versions.sample.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures {
        dataBinding = true
    }
    androidResources {
        // Typeface.Builder can mmap uncompressed font assets; compressed variable fonts are
        // inflated into a full-size buffer for every variation, exhausting small heaps quickly.
        if (!compressSymbolFonts.get()) {
            noCompress += "ttf"
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        val release = getByName("release") {
            isMinifyEnabled = false
        }
        create("shrunk") {
            initWith(release)
            matchingFallbacks += listOf("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.ui.tooling)
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
