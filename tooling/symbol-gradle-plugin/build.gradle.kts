import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
    `maven-publish`
}

kotlin {
    explicitApi()
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
}

gradlePlugin {
    plugins {
        create("symbolFonts") {
            id = "io.github.hlcaptain.symbol-fonts"
            implementationClass =
                "io.github.hlcaptain.symbols.gradle.SymbolFontsPlugin"
            displayName = "Symbols font generator"
            description =
                "Generates shrinkable Compose ImageVectors and Android vector " +
                    "drawables from regular or variable symbol fonts."
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
}
