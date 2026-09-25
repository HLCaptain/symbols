import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.symbolFonts)
}

android {
    namespace = "io.github.hlcaptain.symbols.sample.androidviews"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.sample.android.minSdk.get().toInt()
    }
    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
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
    implementation(libs.androidx.appcompat)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(projects.samples.ui.components)
    implementation(projects.modules.materialDrawablesOutlined)
    implementation(projects.samples.imageVectorMigration)
}

symbolFonts {
    iconSet("AndroidViewIcons") {
        style("Regular") {
            codepoints.set(layout.projectDirectory.file("src/main/PowerlineSymbols.codepoints"))
            androidDrawables()
        }
    }
}
