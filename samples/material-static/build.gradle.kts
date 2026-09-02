plugins {
    alias(libs.plugins.symbolsSampleFeature)
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.modules.materialComposeDrawablesRounded)
        implementation(projects.modules.materialRoundedStatic)
    }
}
