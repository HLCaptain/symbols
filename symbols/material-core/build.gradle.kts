plugins {
    alias(libs.plugins.symbolsKotlinMultiplatformLibrary)
    alias(libs.plugins.symbolsKmpPublishing)
}

kotlin {
    explicitApi()
}

android {
    namespace = "io.github.hlcaptain.symbols.material"
}
