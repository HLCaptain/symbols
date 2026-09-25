plugins {
    // Compile conventions with the same Kotlin version as their plugin dependencies.
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
}

group = "io.github.hlcaptain.symbols.buildlogic"

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        register("kotlinMultiplatformLibrary") {
            id = "io.github.hlcaptain.symbols.kotlin-multiplatform-library"
            implementationClass = "KotlinMultiplatformLibraryPlugin"
        }
        register("composeMultiplatformLibrary") {
            id = "io.github.hlcaptain.symbols.compose-multiplatform-library"
            implementationClass = "ComposeMultiplatformLibraryPlugin"
        }
        register("kmpPublishing") {
            id = "io.github.hlcaptain.symbols.kmp-publishing"
            implementationClass = "KmpPublishingPlugin"
        }
        register("materialFontLibrary") {
            id = "io.github.hlcaptain.symbols.material-font-library"
            implementationClass = "MaterialFontLibraryPlugin"
        }
        register("materialVectorSources") {
            id = "io.github.hlcaptain.symbols.material-vector-sources"
            implementationClass = "MaterialVectorSourcesPlugin"
        }
        register("materialVectorLibrary") {
            id = "io.github.hlcaptain.symbols.material-vector-library"
            implementationClass = "MaterialVectorLibraryPlugin"
        }
        register("publishedAndroidLibrary") {
            id = "io.github.hlcaptain.symbols.published-android-library"
            implementationClass = "PublishedAndroidLibraryPlugin"
        }
        register("sampleFeature") {
            id = "io.github.hlcaptain.symbols.sample-feature"
            implementationClass = "SampleFeaturePlugin"
        }
    }
}

dependencies {
    implementation(gradleKotlinDsl())
    implementation(libs.android.gradlePlugin)
    implementation(libs.buildconfig.gradlePlugin)
    implementation(libs.composeCompiler.gradlePlugin)
    implementation(libs.composeMultiplatform.gradlePlugin)
    implementation(libs.kotlinMultiplatform.gradlePlugin)
    implementation(libs.koinCompiler.gradlePlugin)
    implementation(libs.symbols.gradlePlugin)

    testImplementation(kotlin("test-junit"))
}
