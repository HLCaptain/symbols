plugins {
    alias(libs.plugins.symbolsComposeMultiplatformLibrary)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.modules.symbolsCore)
        api(libs.compose.runtime)
    }
}

android {
    namespace = "io.github.hlcaptain.symbols.sample.api"
}
