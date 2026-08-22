plugins {
    alias(libs.plugins.symbolsPublishedAndroidLibrary)
    id("io.github.hlcaptain.symbol-fonts")
}

android {
    namespace = "io.github.hlcaptain.symbols.material.outlined.drawables"
}

symbolFonts {
    iconSet("MaterialSymbols") {
        style("Outlined") {
            codepoints.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/material/MaterialSymbols.codepoints",
                ),
            )
            font.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/material/outlined-static/composeResources/font/" +
                        "material_symbols_outlined_regular.ttf",
                ),
            )
            resourcePrefix.set("material_symbols")
            androidDrawables()
        }
    }
}
