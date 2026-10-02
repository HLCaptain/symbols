import com.github.takahirom.roborazzi.AnnotationFilter
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.symbolsSampleFeature)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.symbolFonts)
}

val galleryOutput = layout.buildDirectory.dir("generated/materialGallery")
val generateMaterialGalleryReferences = tasks.register<Exec>("generateMaterialGalleryReferences") {
    val generator = rootProject.layout.projectDirectory.file("tools/generate_material_gallery.py")
    inputs.file(generator)
    inputs.file(rootProject.layout.projectDirectory.file("tools/generate_material_vectors.py"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/requirements-font-verification.txt"))
    inputs.file(rootProject.layout.projectDirectory.file("fonts/material/MaterialSymbols.codepoints"))
    inputs.files(rootProject.fileTree("fonts/material") { include("**/*_variable.ttf") })
    outputs.dir(galleryOutput)
    commandLine(
        providers.gradleProperty("symbolsPython")
            .orElse(providers.environmentVariable("SYMBOLS_PYTHON")).orElse("python3").get(),
        generator.asFile,
        "--output", galleryOutput.get().asFile,
    )
}

kotlin {
    jvmToolchain(21)
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    applyDefaultHierarchyTemplate {
        common {
            group("nonAndroid") {
                withJvm()
                withIos()
                withJs()
                withWasmJs()
            }
        }
    }

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
            implementation(projects.modules.materialVectorsOutlined)
            implementation(projects.modules.materialVectorsRounded)
            implementation(projects.modules.materialVectorsSharp)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.resources)
        }
        commonMain {
            kotlin.srcDir(files(galleryOutput.map { it.dir("commonMain/kotlin") }).builtBy(generateMaterialGalleryReferences))
        }
        jvmTest.dependencies {
            implementation(libs.compose.ui.test)
            implementation(libs.roborazzi.composeDesktop)
            implementation(libs.roborazzi.composeDesktopPreviewScannerSupport)
            implementation(libs.composePreviewScanner.android)
            implementation(libs.junit)
            runtimeOnly(compose.desktop.currentOs)
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

compose.resources {
    customDirectory("androidMain", generateMaterialGalleryReferences.map { galleryOutput.get().dir("androidMain/composeResources") })
    customDirectory("nonAndroidMain", generateMaterialGalleryReferences.map { galleryOutput.get().dir("nonAndroidMain/composeResources") })
}

tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    maxHeapSize = "4096m"
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
}

tasks.withType<Test>().matching { it.name == "jvmTest" }.configureEach {
    systemProperty("java.awt.headless", "true")
    environment("DISPLAY", "")
    environment("WAYLAND_DISPLAY", "")
}

@OptIn(ExperimentalRoborazziApi::class)
roborazzi {
    outputDir.set(layout.buildDirectory.dir("roborazzi/baselines"))
    separateOutputDirs.set(true)
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
    generateComposePreviewDesktopTests {
        enable.set(true)
        packages.set(listOf("io.github.hlcaptain.symbols.sample.imagevectormigration"))
        includePrivatePreviews.set(true)
        annotationFilter.set(AnnotationFilter.Include("io.github.hlcaptain.symbols.sample.ui.PreviewScreenshotBaseline"))
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
