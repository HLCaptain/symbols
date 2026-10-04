plugins {
    alias(libs.plugins.symbolsPublishedAndroidLibrary)
    alias(libs.plugins.symbolFonts)
}

android {
    namespace = "io.github.hlcaptain.symbols.material.rounded.drawables"
}

symbolFonts {
    iconSet("MaterialSymbols") {
        listOf(
            Triple("Rounded", false, false),
            Triple("RoundedFilled", true, false),
            Triple("AutomirroredRounded", false, true),
            Triple("AutomirroredRoundedFilled", true, true),
        ).forEach { (name, filled, mirrored) ->
            style(name) {
                codepoints.set(
                    rootProject.layout.projectDirectory.file("fonts/material/MaterialSymbols.codepoints"),
                )
                font.set(
                    rootProject.layout.projectDirectory.file(
                        if (filled) {
                            "fonts/material/rounded/composeResources/font/material_symbols_rounded_variable.ttf"
                        } else {
                            "fonts/material/rounded-static/composeResources/font/material_symbols_rounded_regular.ttf"
                        },
                    ),
                )
                if (filled) {
                    axis("FILL", 1f)
                    axis("wght", 400f)
                    axis("GRAD", 0f)
                    axis("opsz", 24f)
                }
                resourcePrefix.set("material_symbols")
                autoMirror.set(mirrored)
                androidDrawables()
            }
        }
    }
}
