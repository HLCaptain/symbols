rootProject.name = "symbols"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("tooling")
    includeBuild("build-logic")

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradleup.nmcp.settings") version "1.6.2"
}

nmcpSettings {
    centralPortal {
        username = providers.gradleProperty("mavenCentralUsername")
            .orElse("")
            .get()
        password = providers.gradleProperty("mavenCentralPassword")
            .orElse("")
            .get()
        publishingType = "AUTOMATIC"
        publicationName = "symbols:" +
            providers.gradleProperty("VERSION_NAME").orElse("unspecified").get()
        validationTimeout = java.time.Duration.ofMinutes(30)
        // Central publication is asynchronous; CI separately waits for public coordinates.
        publishingTimeout = java.time.Duration.ZERO
    }
}

include(":composeApp")
include(":androidApp")
include(":benchmarks:shrinkable-vectors")
include(":benchmarks:animated-font-app", ":benchmarks:animated-font")
include(
    ":samples:api",
    ":samples:ui:components",
    ":samples:material-static",
    ":samples:material-variable",
    ":samples:custom-static",
    ":samples:custom-variable",
    ":samples:image-vector-migration",
    ":samples:android-views",
    ":samples:android-views-platform",
    ":samples:theming",
    ":samples:runtime-axes",
)
include(
    ":modules:symbols-core",
    ":modules:variant-font-core",
    ":modules:material-core",
    ":modules:material-compose",
    ":modules:material-outlined",
    ":modules:material-rounded",
    ":modules:material-sharp",
    ":modules:material-outlined-static",
    ":modules:material-rounded-static",
    ":modules:material-sharp-static",
    ":modules:material-compose-drawables-outlined",
    ":modules:material-compose-drawables-rounded",
    ":modules:material-compose-drawables-sharp",
    ":modules:material-drawables-outlined",
    ":modules:material-drawables-rounded",
    ":modules:material-drawables-sharp",
    ":modules:material-vectors-outlined",
    ":modules:material-vectors-rounded",
    ":modules:material-vectors-sharp",
    ":modules:material-vectors-themed",
)

project(":modules").projectDir = file("symbols")
project(":modules:symbols-core").projectDir = file("symbols/symbols-core")
project(":modules:variant-font-core").projectDir = file("symbols/variant-font-core")
project(":modules:material-core").projectDir = file("symbols/material-core")
project(":modules:material-compose").projectDir = file("symbols/material-compose")
project(":modules:material-outlined").projectDir = file("symbols/material-outlined")
project(":modules:material-rounded").projectDir = file("symbols/material-rounded")
project(":modules:material-sharp").projectDir = file("symbols/material-sharp")
project(":modules:material-outlined-static").projectDir =
    file("symbols/material-outlined-static")
project(":modules:material-rounded-static").projectDir =
    file("symbols/material-rounded-static")
project(":modules:material-sharp-static").projectDir =
    file("symbols/material-sharp-static")
project(":modules:material-compose-drawables-outlined").projectDir =
    file("symbols/material-compose-drawables-outlined")
project(":modules:material-compose-drawables-rounded").projectDir =
    file("symbols/material-compose-drawables-rounded")
project(":modules:material-compose-drawables-sharp").projectDir =
    file("symbols/material-compose-drawables-sharp")
project(":modules:material-drawables-outlined").projectDir =
    file("symbols/material-drawables-outlined")
project(":modules:material-drawables-rounded").projectDir =
    file("symbols/material-drawables-rounded")
project(":modules:material-drawables-sharp").projectDir =
    file("symbols/material-drawables-sharp")
project(":modules:material-vectors-outlined").projectDir =
    file("symbols/material-vectors-outlined")
project(":modules:material-vectors-rounded").projectDir =
    file("symbols/material-vectors-rounded")
project(":modules:material-vectors-sharp").projectDir =
    file("symbols/material-vectors-sharp")
project(":modules:material-vectors-themed").projectDir =
    file("symbols/material-vectors-themed")
