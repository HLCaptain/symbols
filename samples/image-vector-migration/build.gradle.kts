plugins {
    alias(libs.plugins.symbolsSampleFeature)
    id("io.github.hlcaptain.symbol-fonts")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.modules.materialVectorsRounded)
        implementation(libs.compose.material.icons.extended)
        implementation(libs.compose.resources)
    }
}

symbolFonts {
    iconSet("Academmunicons") {
        packageName.set(
            "io.github.hlcaptain.symbols.sample.imagevectormigration.generated",
        )
        include("orcid")
        style("Default") {
            codepoints.set(
                layout.projectDirectory.file("../custom-variable/Academmunicons.codepoints"),
            )
            font.set(
                layout.projectDirectory.file(
                    "../custom-variable/src/commonMain/composeResources/font/" +
                        "academmunicons_variable.ttf",
                ),
            )
            imageVectors()
            composeDrawables()
        }
    }
}
