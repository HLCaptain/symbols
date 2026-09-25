plugins {
    alias(libs.plugins.symbolsKotlinMultiplatformLibrary)
    alias(libs.plugins.symbolsKmpPublishing)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.modules.symbolsCore)
    }
}

kotlin {
    android {
        namespace = "io.github.hlcaptain.symbols.material"
    }
}
