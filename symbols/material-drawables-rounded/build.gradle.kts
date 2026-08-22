plugins {
    alias(libs.plugins.symbolsPublishedAndroidLibrary)
    id("io.github.hlcaptain.symbol-fonts")
}

android {
    namespace = "io.github.hlcaptain.symbols.material.rounded.drawables"
}

symbolFonts {
    iconSet("MaterialSymbols") {
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
            androidDrawables()
        }
    }
}
