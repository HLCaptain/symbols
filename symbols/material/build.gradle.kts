@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvm()
    wasmJs {
        nodejs()
    }
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}

android {
    compileSdk = 36
}
