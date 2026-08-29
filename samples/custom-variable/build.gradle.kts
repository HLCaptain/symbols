plugins {
    alias(libs.plugins.symbolsSampleFeature)
    id("io.github.hlcaptain.symbol-fonts")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.modules.variantFontCore)
        implementation(libs.compose.resources)
    }
}

symbolFonts {
    composeFontResources.from(
        layout.projectDirectory.dir("src/commonMain/composeResources"),
    )
    iconSet("Academmunicons") {
        packageName.set("io.github.hlcaptain.symbols.sample.customvariable.generated")
        style("Semibold") {
            codepoints.set(layout.projectDirectory.file("Academmunicons.codepoints"))
            axis("ital", 0f)
            axis("wght", 600f)
            imageVectors()
            composeDrawables()
        }
    }
}
