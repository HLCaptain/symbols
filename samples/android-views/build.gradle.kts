plugins {
    alias(libs.plugins.symbolsSampleFeature)
}

kotlin {
    android {
        namespace = "io.github.hlcaptain.symbols.sample.androidviews.shared"
        localDependencySelection {
            selectBuildTypeFrom.set(listOf("release"))
        }
    }
    sourceSets.androidMain.dependencies {
        implementation(projects.samples.androidViewsPlatform)
    }
}
