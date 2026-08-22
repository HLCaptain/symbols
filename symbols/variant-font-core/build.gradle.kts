plugins {
    alias(libs.plugins.symbolsComposeMultiplatformLibrary)
    alias(libs.plugins.symbolsKmpPublishing)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.compose.resources)
        api(libs.compose.foundation)
        api(libs.compose.runtime)
        api(libs.compose.ui)
    }
}

android {
    namespace = "io.github.hlcaptain.symbols.font.core"
}
