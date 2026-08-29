plugins {
    alias(libs.plugins.symbolsSampleFeature)
    id("io.github.hlcaptain.symbol-fonts")
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
