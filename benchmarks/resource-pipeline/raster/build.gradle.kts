plugins {
    kotlin("jvm") version "2.4.20"
    application
}
repositories { mavenCentral() }
val hostOs = when {
    System.getProperty("os.name").startsWith("Linux") -> "linux"
    System.getProperty("os.name").startsWith("Mac") -> "macos"
    System.getProperty("os.name").startsWith("Windows") -> "windows"
    else -> error("Unsupported Skiko host")
}
val hostArch = when (System.getProperty("os.arch")) {
    "amd64", "x86_64" -> "x64"
    "aarch64", "arm64" -> "arm64"
    else -> error("Unsupported Skiko architecture")
}
dependencies {
    // Pin one renderer for both inputs; changing renderers would confound this comparison.
    implementation("org.jetbrains.skiko:skiko-awt:0.150.1")
    runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-$hostOs-$hostArch:0.150.1")
}
kotlin { jvmToolchain(21) }
application {
    mainClass.set("RasterDiffKt")
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}
