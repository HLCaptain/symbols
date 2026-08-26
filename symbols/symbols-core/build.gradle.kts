plugins {
    alias(libs.plugins.symbolsKotlinMultiplatformLibrary)
    alias(libs.plugins.symbolsKmpPublishing)
}

android {
    namespace = "io.github.hlcaptain.symbols.core"
}
