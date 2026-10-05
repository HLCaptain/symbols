import configureMaterialDrawables

plugins {
    alias(libs.plugins.symbolsPublishedAndroidLibrary)
    alias(libs.plugins.symbolFonts)
}

android {
    namespace = "io.github.hlcaptain.symbols.material.outlined.drawables.automirrored"
}

configureMaterialDrawables("outlined", mirrored = true)
