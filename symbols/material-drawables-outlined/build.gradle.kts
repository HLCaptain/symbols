import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.androidLibrary)
    `maven-publish`
    id("io.github.hlcaptain.symbol-fonts")
}

android {
    namespace = "io.github.hlcaptain.symbols.material.outlined.drawables"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 21
    }

    publishing {
        singleVariant("release")
    }
}

symbolFonts {
    iconSet("MaterialSymbols") {
        packageName.set(
            "io.github.hlcaptain.symbols.material.outlined.drawables.generated",
        )
        manifest.set(
            rootProject.layout.projectDirectory.file(
                "fonts/material/MaterialSymbols.codepoints",
            ),
        )
        includeAll()

        style("Outlined") {
            font.set(
                rootProject.layout.projectDirectory.file(
                    "fonts/material/outlined-static/composeResources/font/" +
                        "material_symbols_outlined_regular.ttf",
                ),
            )
            resourcePrefix.set("material_symbols")
            androidDrawables()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "symbols-${project.name}"
            }
        }
    }
}
