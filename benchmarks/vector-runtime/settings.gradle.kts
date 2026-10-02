pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

dependencyResolutionManagement {
    repositories {
        providers.gradleProperty("vectorRepository").orNull?.let { location ->
            maven {
                url = uri(location)
                content { includeGroup("io.github.hlcaptain") }
            }
        }
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
            library("benchmark-vectors", "io.github.hlcaptain", "symbols-material-vectors-rounded")
                .version(providers.gradleProperty("vectorVersion").orElse("2.0.0").get())
        }
    }
}

rootProject.name = "symbols-vector-runtime-benchmark"
include(":app", ":benchmark")
