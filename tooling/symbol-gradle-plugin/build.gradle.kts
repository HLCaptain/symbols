import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

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
    alias(libs.plugins.gradlePluginPublish)
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
    implementation(projects.symbolGeneratorCore)

    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.android.gradleApi)

    testImplementation(kotlin("test-junit"))
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.gradlePlugin)
    testImplementation(libs.compose.gradlePlugin)
    testImplementation(libs.composeCompiler.gradlePlugin)
    testImplementation(libs.android.gradlePlugin)
    testRuntimeOnly(
        "org.jetbrains.skiko:skiko-awt-runtime-${skikoRuntimeTarget()}:" +
            libs.versions.skiko.get(),
    )
}

gradlePlugin {
    website.set("https://github.com/HLCaptain/symbols")
    vcsUrl.set("https://github.com/HLCaptain/symbols.git")
    plugins {
        create("symbolFonts") {
            id = "io.github.hlcaptain.symbol-fonts"
            implementationClass =
                "io.github.hlcaptain.symbols.gradle.SymbolFontsPlugin"
            compatibility { features { configurationCache.set(true) } }
            tags.set(listOf("compose", "kotlin-multiplatform", "icons", "fonts", "svg"))
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
    // Functional AGP tests use the application toolchain, while this artifact
    // is built with tooling/gradlew to retain its Gradle 8 API baseline.
    val applicationWrapper = Properties().apply {
        rootProject.file("../gradle/wrapper/gradle-wrapper.properties")
            .inputStream().use(::load)
    }
    systemProperty(
        "symbols.fixtureGradleVersion",
        Regex("gradle-([0-9.]+)-").find(
            applicationWrapper.getProperty("distributionUrl"),
        )!!.groupValues[1],
    )
    systemProperty(
        "symbols.powerlineTestFont",
        rootProject.layout.projectDirectory.file(
            "../samples/custom-static/src/commonMain/composeResources/font/" +
                "powerline_symbols.otf",
        ).asFile.absolutePath,
    )
    systemProperty(
        "symbols.academmuniconsTestFont",
        rootProject.layout.projectDirectory.file(
            "../fonts/samples/academmunicons/academmunicons-regular.ttf",
        ).asFile.absolutePath,
    )
}
