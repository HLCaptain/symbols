plugins {
    alias(libs.plugins.symbolsPublishedAndroidLibrary)
    id("io.github.hlcaptain.symbol-fonts")
}

android {
    namespace = "io.github.hlcaptain.symbols.material.sharp.drawables"
}

symbolFonts {
    iconSet("MaterialSymbols") {
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
            androidDrawables()
        }
    }
}
