plugins {
    alias(libs.plugins.symbolsComposeMultiplatformLibrary)
    alias(libs.plugins.symbolsKmpPublishing)
    id("io.github.hlcaptain.symbol-fonts")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.compose.resources)
        implementation(libs.compose.runtime)
    }
}

compose.resources {
    packageOfResClass =
        "io.github.hlcaptain.symbols.material.sharp.compose.drawables.resources"
    publicResClass = true
    generateResClass = always
}

symbolFonts {
    iconSet("MaterialSymbolsSharpDrawables") {
        style("Sharp") {
            codepoints.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/material/MaterialSymbols.codepoints",
                ),
            )
            font.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/material/sharp-static/composeResources/font/" +
                        "material_symbols_sharp_regular.ttf",
                ),
            )
            resourcePrefix.set("material_symbols")
            composeDrawables()
        }
    }
}

android {
    namespace = "io.github.hlcaptain.symbols.material.sharp.compose.drawables"
}
