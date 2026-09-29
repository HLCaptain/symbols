import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.kotlinJvm) apply false
}

allprojects {
    group = providers.gradleProperty("GROUP")
        .orElse("io.github.hlcaptain")
        .get()
    version = providers.gradleProperty("VERSION_NAME")
        .orElse("0.1.0-SNAPSHOT")
        .get()
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
            withJavadocJar()
        }

        val legalDocuments = rootProject.files(
            rootProject.layout.projectDirectory.file("../LICENSE"),
            rootProject.layout.projectDirectory.file("../THIRD_PARTY_NOTICES.md"),
        )
        tasks.withType<Jar>().configureEach {
            from(legalDocuments) {
                into("META-INF/${project.name}")
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        }
    }

    plugins.withId("maven-publish") {
        val signingKey = providers.gradleProperty("signingInMemoryKey")
        if (signingKey.isPresent) {
            pluginManager.apply("signing")
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(
                    signingKey.get(),
                    providers.gradleProperty("signingInMemoryKeyPassword").orNull,
                )
            }
        }
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                if (signingKey.isPresent) {
                    project.extensions.getByType<SigningExtension>().sign(this)
                }
                pom {
                    name.set("Symbols ${project.name}")
                    description.set(
                        when (project.name) {
                            "symbol-generator-core" ->
                                "Deterministic SVG and symbol-font outline " +
                                    "generator for typed Compose vectors and " +
                                    "Android/Compose drawable XML."
                            "symbol-gradle-plugin" ->
                                "Cacheable Gradle integration for generating " +
                                    "shrinkable typed symbol vectors and drawables."
                            else -> error(
                                "Missing tooling publication description for " +
                                    project.path,
                            )
                        },
                    )
                    url.set("https://github.com/HLCaptain/symbols")
                    inceptionYear.set("2025")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set(
                                "https://www.apache.org/licenses/LICENSE-2.0.txt",
                            )
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("HLCaptain")
                            name.set("HLCaptain")
                            url.set("https://github.com/HLCaptain")
                        }
                    }
                    scm {
                        url.set("https://github.com/HLCaptain/symbols")
                        connection.set(
                            "scm:git:https://github.com/HLCaptain/symbols.git",
                        )
                        developerConnection.set(
                            "scm:git:ssh://git@github.com/HLCaptain/symbols.git",
                        )
                    }
                }
            }
        }
    }
}
