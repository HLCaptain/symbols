plugins {
    alias(libs.plugins.symbolsComposeMultiplatformLibrary)
    alias(libs.plugins.symbolsKmpPublishing)
    alias(libs.plugins.symbolFonts)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.compose.resources)
        implementation(libs.compose.runtime)
    }
}

compose.resources {
    packageOfResClass =
        "io.github.hlcaptain.symbols.material.rounded.compose.drawables.resources"
    publicResClass = true
    generateResClass = always
}

symbolFonts {
    iconSet("MaterialSymbolsRoundedDrawables") {
        style("Rounded") {
            codepoints.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/material/MaterialSymbols.codepoints",
                ),
            )
            font.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/material/rounded-static/composeResources/font/" +
                        "material_symbols_rounded_regular.ttf",
                ),
            )
            resourcePrefix.set("material_symbols")
            composeDrawables()
        }
    }
}

android {
    namespace = "io.github.hlcaptain.symbols.material.rounded.compose.drawables"
}
