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
        packageName.set("io.github.hlcaptain.symbols.sample.generated")
        manifest.set(
            rootProject.layout.projectDirectory.file(
                "fonts/material/MaterialSymbols.codepoints",
            ),
        )
        include("check", "favorite", "home")

        style("Rounded") {
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
        manifest.set(
            rootProject.layout.projectDirectory.file(
                "fonts/samples/font-awesome-free-solid/" +
                    "FontAwesomeFreeSolid.codepoints",
            ),
        )
        include("circle_check", "compass", "face_smile")

        style("Solid") {
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
        manifest.set(
            rootProject.layout.projectDirectory.file(
                "fonts/samples/tabler-icons-filled/TablerIconsFilled.codepoints",
            ),
        )
        include("alien", "dice_5", "sparkles")

        style("Filled") {
            font.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/samples/tabler-icons-filled/tabler-icons-filled.ttf",
                ),
            )
            baselineY.set(23.5f)
            imageVectors()
        }
    }

    iconSet("PowerlineIcons") {
        packageName.set("io.github.hlcaptain.symbols.sample.generated.powerline")
        manifest.set(
            rootProject.layout.projectDirectory.file(
                "fonts/samples/powerline/PowerlineSymbols.codepoints",
            ),
        )
        include("branch", "line_number", "read_only")

        style("Regular") {
            font.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/samples/powerline/PowerlineSymbols.otf",
                ),
            )
            emSize.set(21.145374f)
            originX.set(6.533921f)
            baselineY.set(20.130396f)
            imageVectors()
        }
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
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
