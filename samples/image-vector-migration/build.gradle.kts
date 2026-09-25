import com.github.takahirom.roborazzi.AnnotationFilter
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.symbolsSampleFeature)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.symbolFonts)
}

kotlin {
    jvmToolchain(21)

    android {
        androidResources.enable = true
        withHostTest {
            isIncludeAndroidResources = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.modules.materialRounded)
            implementation(projects.modules.materialVectorsRounded)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.resources)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
            implementation(libs.androidx.test.core)
            implementation(libs.composePreviewScanner.android)
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.composePreviewScannerSupport)
        }
    }
}

tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    maxHeapSize = "4096m"
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
}

@OptIn(ExperimentalRoborazziApi::class)
roborazzi {
    outputDir.set(layout.buildDirectory.dir("roborazzi/baselines"))
    generateComposePreviewRobolectricTests {
        enable.set(true)
        packages.set(
            listOf(
                "io.github.hlcaptain.symbols.sample.imagevectormigration",
            ),
        )
        includePrivatePreviews.set(true)
        annotationFilter.set(
            AnnotationFilter.Include(
                "io.github.hlcaptain.symbols.sample.ui.PreviewScreenshotBaseline",
            ),
        )
    }
}

symbolFonts {
    iconSet("Academmunicons") {
        packageName.set(
            "io.github.hlcaptain.symbols.sample.imagevectormigration.generated",
        )
        style("Default") {
            codepoints.set(
                layout.projectDirectory.file("../custom-variable/Academmunicons.codepoints"),
            )
            font.set(
                layout.projectDirectory.file(
                    "../custom-variable/src/commonMain/composeResources/font/" +
                        "academmunicons_variable.ttf",
                ),
            )
            imageVectors()
            composeDrawables()
        }
    }
    iconSet("Tabler") {
        packageName.set(
            "io.github.hlcaptain.symbols.sample.imagevectormigration.generated",
        )
        style("Outline") {
            svgDirectory.set(
                layout.projectDirectory.dir("src/commonMain/svg/tabler"),
            )
            imageVectors()
            androidDrawables()
            composeDrawables()
        }
    }
}
