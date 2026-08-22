plugins {
    alias(libs.plugins.symbolsSampleFeature)
}

kotlin {
    sourceSets.androidMain.dependencies {
        implementation(libs.androidx.appcompat)
        implementation(libs.compose.ui)
        implementation(projects.modules.materialDrawablesOutlined)
        implementation(projects.samples.imageVectorMigration)
    }
}

android {
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}
