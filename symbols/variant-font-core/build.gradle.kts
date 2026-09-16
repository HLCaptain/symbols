plugins {
    alias(libs.plugins.symbolsComposeMultiplatformLibrary)
    alias(libs.plugins.symbolsKmpPublishing)
}

kotlin {
    applyDefaultHierarchyTemplate {
        common {
            group("skiko") {
                withJvm()
                withIos()
                withJs()
                withWasmJs()
            }
        }
    }
    sourceSets.commonMain.dependencies {
        api(projects.modules.symbolsCore)
        api(libs.compose.resources)
        api(libs.compose.foundation)
        api(libs.compose.runtime)
        api(libs.compose.ui)
    }
    sourceSets.jvmTest.dependencies {
        runtimeOnly(compose.desktop.currentOs)
    }
    sourceSets.named("skikoMain") {
        dependencies { implementation(libs.skiko) }
    }
    sourceSets.jvmTest {
        resources.srcDir(rootProject.file("fonts/material/outlined/composeResources"))
    }
}

android {
    namespace = "io.github.hlcaptain.symbols.font.core"
}
