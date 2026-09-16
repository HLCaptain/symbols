plugins {
    alias(libs.plugins.symbolsSampleFeature)
    alias(libs.plugins.symbolFonts)
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

symbolFonts {
    iconSet("AndroidViewIcons") {
        style("Regular") {
            codepoints.set(
                layout.projectDirectory.file(
                    "src/androidMain/PowerlineSymbols.codepoints",
                ),
            )
            androidDrawables()
        }
    }
}
