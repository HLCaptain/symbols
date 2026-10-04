plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    application
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation("io.github.hlcaptain:symbols-material-rounded:2.1.0")
    implementation("io.github.hlcaptain:symbols-material-vectors-rounded:2.1.0")
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(compose.desktop.currentOs)
}

application {
    mainClass.set("ReadmeMediaKt")
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir
    environment("DISPLAY", "")
    environment("WAYLAND_DISPLAY", "")
}
