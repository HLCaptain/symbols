import configureMaterialDrawables

plugins {
    alias(libs.plugins.symbolsPublishedAndroidLibrary)
    alias(libs.plugins.symbolFonts)
}

android {
    namespace = "io.github.hlcaptain.symbols.material.outlined.drawables.automirrored.filled"
}

configureMaterialDrawables("outlined", filled = true, mirrored = true)
