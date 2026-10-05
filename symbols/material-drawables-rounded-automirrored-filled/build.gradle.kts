import configureMaterialDrawables

plugins {
    alias(libs.plugins.symbolsPublishedAndroidLibrary)
    alias(libs.plugins.symbolFonts)
}

android {
    namespace = "io.github.hlcaptain.symbols.material.rounded.drawables.automirrored.filled"
}

configureMaterialDrawables("rounded", filled = true, mirrored = true)
