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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradleup.nmcp.settings") version "1.6.1"
}

nmcpSettings {
    centralPortal {
        username = providers.gradleProperty("mavenCentralUsername")
            .orElse("")
            .get()
        password = providers.gradleProperty("mavenCentralPassword")
            .orElse("")
            .get()
        publishingType = "USER_MANAGED"
        publicationName = "symbols-tooling:" +
            providers.gradleProperty("VERSION_NAME").orElse("unspecified").get()
        publishingTimeout = java.time.Duration.ZERO
    }
}

rootProject.name = "symbols-tooling"

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
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

include(":symbol-generator-core")
include(":symbol-gradle-plugin")
