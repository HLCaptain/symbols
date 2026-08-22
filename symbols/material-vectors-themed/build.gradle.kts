plugins {
    alias(libs.plugins.symbolsComposeMultiplatformLibrary)
    alias(libs.plugins.symbolsKmpPublishing)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.modules.materialCompose)
        api(projects.modules.materialVectorsOutlined)
        api(projects.modules.materialVectorsRounded)
        api(projects.modules.materialVectorsSharp)
    }
}

android {
    namespace = "io.github.hlcaptain.symbols.material.vectors.themed"
}
