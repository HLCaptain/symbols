plugins { alias(libs.plugins.androidApplication) }

android {
    namespace = "example.consumer"
    enableKotlin = false
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = namespace
        minSdk = 21
        targetSdk = libs.versions.android.targetSdk.get().toInt()
    }
    buildTypes.named("release") {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    listOf("outlined", "rounded", "sharp").forEach { style ->
        listOf("", "-filled", "-automirrored", "-automirrored-filled").forEach { variant ->
            val module = "material-drawables-$style$variant"
            implementation(files("../../../symbols/$module/build/outputs/aar/$module-release.aar"))
        }
    }
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

tasks.withType<Test>().configureEach {
    systemProperty("renderOutput", layout.buildDirectory.dir("rendered").get().asFile.absolutePath)
    systemProperty("java.io.tmpdir", temporaryDir.absolutePath)
}
