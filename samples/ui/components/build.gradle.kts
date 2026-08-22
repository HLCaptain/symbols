plugins {
    alias(libs.plugins.symbolsComposeMultiplatformLibrary)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.samples.api)
        api(libs.compose.foundation)
        api(libs.compose.runtime)
        api(libs.compose.ui)
        implementation(libs.compose.material3)
    }
}

android {
    namespace = "io.github.hlcaptain.symbols.sample.ui"
}
