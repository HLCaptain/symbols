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
    implementation(files(
        "../../../symbols/material-drawables-outlined/build/outputs/aar/material-drawables-outlined-release.aar",
        "../../../symbols/material-drawables-rounded/build/outputs/aar/material-drawables-rounded-release.aar",
        "../../../symbols/material-drawables-sharp/build/outputs/aar/material-drawables-sharp-release.aar",
    ))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

tasks.withType<Test>().configureEach {
    systemProperty("renderOutput", layout.buildDirectory.dir("rendered").get().asFile.absolutePath)
    systemProperty("java.io.tmpdir", temporaryDir.absolutePath)
}
