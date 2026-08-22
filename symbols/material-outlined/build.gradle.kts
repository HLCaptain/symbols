plugins {
    alias(libs.plugins.symbolsMaterialFontLibrary)
    id("io.github.hlcaptain.symbol-fonts")
}

val materialFontResources = rootProject.layout.projectDirectory.dir(
    "fonts/material/outlined/composeResources",
)

compose.resources {
    packageOfResClass = "io.github.hlcaptain.symbols.material.outlined.resources"
}

symbolFonts {
    composeFontResources.from(materialFontResources)
}

android {
    namespace = "io.github.hlcaptain.symbols.material.outlined"
}
