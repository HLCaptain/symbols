plugins {
    alias(libs.plugins.symbolsComposeMultiplatformLibrary)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.compose.runtime)
    }
}

android {
    namespace = "io.github.hlcaptain.symbols.sample.api"
}
