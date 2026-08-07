import org.gradle.api.tasks.Sync
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    id("io.github.hlcaptain.symbol-fonts")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Symbols"
            isStatic = true
        }
    }
    
    jvm()
    
    js {
        browser()
        binaries.executable()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(projects.modules.materialDrawablesOutlined)
        }
        commonMain.dependencies {
            implementation(projects.modules.materialOutlined)
            implementation(projects.modules.materialRounded)
            implementation(projects.modules.materialSharp)
            implementation(projects.modules.materialRoundedStatic)
            implementation(projects.modules.materialVectorsThemed)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

android {
    namespace = "io.github.hlcaptain.symbols.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.hlcaptain.symbols.sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    androidResources {
        // Typeface.Builder can mmap uncompressed font assets; compressed variable fonts are
        // inflated into a full-size buffer for every variation, exhausting small heaps quickly.
        noCompress += "ttf"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

symbolFonts {
    iconSet("AppIcons") {
        include("check", "favorite", "home")

        style("Rounded") {
            codepoints.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/material/MaterialSymbols.codepoints",
                ),
            )
            font.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/material/rounded/composeResources/font/" +
                        "material_symbols_rounded_variable.ttf",
                ),
            )
            axis("FILL", 1f)
            axis("GRAD", 0f)
            axis("opsz", 24f)
            axis("wght", 400f)
            imageVectors()
            androidDrawables()
            composeDrawables()
        }

        style("Regular") {
            codepoints.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/material/MaterialSymbols.codepoints",
                ),
            )
            font.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/material/rounded-static/composeResources/font/" +
                        "material_symbols_rounded_regular.ttf",
                ),
            )
            imageVectors()
            androidDrawables()
            composeDrawables()
        }
    }

    iconSet("FontAwesomeIcons") {
        packageName.set("io.github.hlcaptain.symbols.sample.generated.fontawesome")
        include("circle_check", "compass", "face_smile")

        style("Solid") {
            codepoints.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/samples/font-awesome-free-solid/" +
                        "FontAwesomeFreeSolid.codepoints",
                ),
            )
            font.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/samples/font-awesome-free-solid/fa-solid-900.ttf",
                ),
            )
            baselineY.set(21f)
            imageVectors()
        }
    }

    iconSet("TablerIcons") {
        packageName.set("io.github.hlcaptain.symbols.sample.generated.tabler")
        include("alien", "dice_5", "sparkles")

        style("Outline") {
            codepoints.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/samples/tabler-icons-outline/" +
                        "TablerIconsOutline.codepoints",
                ),
            )
            font.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/samples/tabler-icons-outline/tabler-icons-outline-2.ttf",
                ),
            )
            baselineY.set(23.5f)
            imageVectors()
        }

        style("Filled") {
            codepoints.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/samples/tabler-icons-filled/" +
                        "TablerIconsFilled.codepoints",
                ),
            )
            font.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/samples/tabler-icons-filled/tabler-icons-filled.ttf",
                ),
            )
            baselineY.set(23.5f)
            imageVectors()
        }
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

// Backport CMP-7170: Preview packaging must include generated Compose assets.
tasks.configureEach {
    if (name == "packageDebugResources") {
        dependsOn("copyDebugComposeResourcesToAndroidAssets")
    }
}

val sampleFontResourcesDirectory =
    layout.buildDirectory.dir("generated/sampleFontResources")
val prepareSampleFontResources = tasks.register<Sync>("prepareSampleFontResources") {
    into(sampleFontResourcesDirectory)
    from(
        rootProject.layout.projectDirectory.file(
            "fonts/samples/academmunicons/academmunicons-variable.ttf",
        ),
    ) {
        into("font")
        rename { "academmunicons_variable.ttf" }
    }
    from(
        rootProject.layout.projectDirectory.file(
            "fonts/samples/academmunicons/academmunicons-regular.ttf",
        ),
    ) {
        into("font")
        rename { "academmunicons_regular.ttf" }
    }
    from(
        rootProject.layout.projectDirectory.file(
            "fonts/samples/font-awesome-free-solid/fa-solid-900.ttf",
        ),
    ) {
        into("font")
        rename { "font_awesome_free_solid.ttf" }
    }
    from(
        rootProject.layout.projectDirectory.file(
            "fonts/samples/powerline/PowerlineSymbols.otf",
        ),
    ) {
        into("font")
        rename { "powerline_symbols.otf" }
    }
    from(
        rootProject.layout.projectDirectory.file(
            "fonts/samples/tabler-icons-filled/tabler-icons-filled.ttf",
        ),
    ) {
        into("font")
        rename { "tabler_icons_filled.ttf" }
    }
    from(
        rootProject.layout.projectDirectory.dir("fonts/samples/tabler-icons-outline"),
    ) {
        into("font")
        include("*.ttf")
        rename { it.replace('-', '_') }
    }
}

compose.resources {
    // The sample owns lightweight FontResource descriptors in commonMain so Fast Preview
    // never depends on a generated Font0_commonMainKt class.
    packageOfResClass = "io.github.hlcaptain.composeapp.generated.resources"
    generateResClass = never
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = prepareSampleFontResources.map {
            sampleFontResourcesDirectory.get()
        },
    )
}

compose.desktop {
    application {
        mainClass = "io.github.hlcaptain.symbols.sample.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "symbols-sample"
            // macOS DMG metadata requires a positive major version.
            packageVersion = "1.0.0"
        }
    }
}
