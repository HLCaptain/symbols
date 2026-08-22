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
    iconSet("Academmunicons") {
        packageName.set("io.github.hlcaptain.symbols.sample.customvariable.generated")
        style("Semibold") {
            codepoints.set(layout.projectDirectory.file("Academmunicons.codepoints"))
            axis("ital", 0f)
            axis("wght", 600f)
            imageVectors()
        }
    }
}
