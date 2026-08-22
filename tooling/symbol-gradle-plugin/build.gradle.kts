import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.buildConfig)
    `java-gradle-plugin`
    `maven-publish`
}

buildConfig {
    packageName("io.github.hlcaptain.symbols.gradle")
    className("SymbolFontsBuildConfig")
    useKotlinOutput()
    buildConfigField("SKIKO_VERSION", libs.versions.skiko.get())
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":symbol-generator-core"))

    compileOnly(
        "org.jetbrains.kotlin:kotlin-gradle-plugin:" +
            libs.versions.kotlin.get(),
    )
    compileOnly(
        "org.jetbrains.compose:compose-gradle-plugin:" +
            libs.versions.composeMultiplatform.get(),
    )
    compileOnly(
        "com.android.tools.build:gradle-api:" +
            libs.versions.agp.get(),
    )

    testImplementation(kotlin("test-junit"))
    testImplementation(gradleTestKit())
    testImplementation(
        "org.jetbrains.kotlin:kotlin-gradle-plugin:" +
            libs.versions.kotlin.get(),
    )
    testImplementation(
        "org.jetbrains.compose:compose-gradle-plugin:" +
            libs.versions.composeMultiplatform.get(),
    )
    testImplementation(
        "com.android.tools.build:gradle:" +
            libs.versions.agp.get(),
    )
    testRuntimeOnly(
        "org.jetbrains.skiko:skiko-awt-runtime-${skikoRuntimeTarget()}:" +
            libs.versions.skiko.get(),
    )
}

gradlePlugin {
    plugins {
        create("symbolFonts") {
            id = "io.github.hlcaptain.symbol-fonts"
            implementationClass =
                "io.github.hlcaptain.symbols.gradle.SymbolFontsPlugin"
            displayName = "Symbols vector generator"
            description =
                "Generates shrinkable Compose ImageVectors and Android vector " +
                    "drawables from SVGs or regular and variable symbol fonts."
        }
    }
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}

tasks.test {
    useJUnit()
    systemProperty(
        "symbols.generatorTestClasspath",
        configurations.testRuntimeClasspath.get().asPath,
    )
}
