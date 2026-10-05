import configureMaterialDrawables

plugins {
    alias(libs.plugins.symbolsPublishedAndroidLibrary)
    alias(libs.plugins.symbolFonts)
}

android {
    namespace = "io.github.hlcaptain.symbols.material.sharp.drawables.automirrored"
}

configureMaterialDrawables("sharp", mirrored = true)
