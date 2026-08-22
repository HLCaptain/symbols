plugins {
    alias(libs.plugins.symbolsSampleFeature)
}

kotlin {
    sourceSets.androidMain.dependencies {
        implementation(libs.androidx.appcompat)
        implementation(libs.compose.ui)
        implementation(projects.modules.materialDrawablesOutlined)
    }
}

android {
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}
