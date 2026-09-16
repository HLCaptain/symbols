plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.symbolFonts)
}

android {
    namespace = "io.github.hlcaptain.symbols.benchmark.shrinkablevectors"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.hlcaptain.symbols.benchmark.shrinkablevectors"
        minSdk = libs.versions.sample.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        val release = getByName("release")

        create("unshrunk") {
            initWith(release)
            isMinifyEnabled = false
            isShrinkResources = false
        }

        create("shrunk") {
            initWith(release)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(projects.modules.materialVectorsOutlined)
}

symbolFonts {
    iconSet("NativeBenchmarkIcons") {
        style("Regular") {
            codepoints.set(
                layout.projectDirectory.file("PowerlineSymbols.codepoints"),
            )
            androidDrawables(fontResource = "powerline_symbols.otf")
        }
    }
}
