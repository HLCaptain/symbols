plugins {
    alias(libs.plugins.symbolsSampleFeature)
    alias(libs.plugins.symbolFonts)
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.compose.resources)
    }
}

symbolFonts {
    iconSet("PowerlineIcons") {
        packageName.set("io.github.hlcaptain.symbols.sample.customstatic.generated")
        style("Regular") {
            codepoints.set(layout.projectDirectory.file("PowerlineSymbols.codepoints"))
            imageVectors()
            composeDrawables()
        }
    }
}
