import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("jvm") version "2.2.20"
    application
    `maven-publish`
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

val skikoVersion = "0.9.22.2"

fun skikoRuntimeTarget(): String {
    val os = System.getProperty("os.name").lowercase()
    val architecture = System.getProperty("os.arch").lowercase()
    val normalizedArchitecture = when (architecture) {
        "aarch64", "arm64" -> "arm64"
        "amd64", "x86_64", "x64" -> "x64"
        else -> error("Unsupported Skiko host architecture: $architecture")
    }
    val normalizedOs = when {
        os.startsWith("mac") || os.startsWith("darwin") -> "macos"
        os.startsWith("linux") -> "linux"
        os.startsWith("windows") -> "windows"
        else -> error("Unsupported Skiko host operating system: $os")
    }
    return "$normalizedOs-$normalizedArchitecture"
}

dependencies {
    implementation("org.jetbrains.skiko:skiko-awt:$skikoVersion")
    testRuntimeOnly(
        "org.jetbrains.skiko:skiko-awt-runtime-${skikoRuntimeTarget()}:$skikoVersion",
    )

    testImplementation(kotlin("test-junit"))
}

application {
    mainClass.set("io.github.hlcaptain.symbols.generator.MainKt")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "symbol-generator-core"
        }
    }
}

tasks.test {
    systemProperty(
        "symbols.repositoryRoot",
        layout.projectDirectory.dir("../..").asFile.absolutePath,
    )
}
