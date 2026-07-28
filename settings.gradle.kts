rootProject.name = "symbols"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
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
}

include(":composeApp")
include(
    ":modules:material-core",
    ":modules:material-compose",
    ":modules:material-outlined",
    ":modules:material-rounded",
    ":modules:material-sharp",
    ":modules:material-vectors-outlined",
    ":modules:material-vectors-rounded",
    ":modules:material-vectors-sharp",
)

project(":modules:material-core").projectDir = file("symbols/material-core")
project(":modules:material-compose").projectDir = file("symbols/material-compose")
project(":modules:material-outlined").projectDir = file("symbols/material-outlined")
project(":modules:material-rounded").projectDir = file("symbols/material-rounded")
project(":modules:material-sharp").projectDir = file("symbols/material-sharp")
project(":modules:material-vectors-outlined").projectDir =
    file("symbols/material-vectors-outlined")
project(":modules:material-vectors-rounded").projectDir =
    file("symbols/material-vectors-rounded")
project(":modules:material-vectors-sharp").projectDir =
    file("symbols/material-vectors-sharp")
