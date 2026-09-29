import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val compressSymbolFonts = providers
    .gradleProperty("compressSymbolFonts")
    .map(String::toBoolean)
    .orElse(false)

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
        compose = true
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
            matchingFallbacks += "release"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    debugImplementation(libs.compose.ui.tooling)
}
