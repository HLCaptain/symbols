plugins {
    alias(libs.plugins.symbolsComposeMultiplatformLibrary)
    alias(libs.plugins.symbolsKmpPublishing)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.modules.variantFontCore)
        api(projects.modules.materialCore)
    }
}

kotlin {
    android {
        namespace = "io.github.hlcaptain.symbols.material.compose"
    }
}
