plugins {
    alias(libs.plugins.symbolsMaterialFontLibrary)
    id("io.github.hlcaptain.symbol-fonts")
}

val materialFontResources = rootProject.layout.projectDirectory.dir(
    "fonts/material/rounded/composeResources",
)

compose.resources {
    packageOfResClass = "io.github.hlcaptain.symbols.material.rounded.resources"
}

symbolFonts {
    composeFontResources.from(materialFontResources)
}

android {
    namespace = "io.github.hlcaptain.symbols.material.rounded"
}
