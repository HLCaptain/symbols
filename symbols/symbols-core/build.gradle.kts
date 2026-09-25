plugins {
    alias(libs.plugins.symbolsKotlinMultiplatformLibrary)
    alias(libs.plugins.symbolsKmpPublishing)
}

kotlin {
    android {
        namespace = "io.github.hlcaptain.symbols.core"
    }
}
