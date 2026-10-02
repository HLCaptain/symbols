import groovy.json.JsonOutput
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

val snapshotVersion = Regex("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)-SNAPSHOT-[0-9a-f]{8}")

allprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories.maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/hlcaptain/symbols")
                credentials(PasswordCredentials::class)
            }
        }
        tasks.withType<PublishToMavenRepository>().configureEach {
            doFirst {
                if (repository.name == "GitHubPackages") {
                    require(snapshotVersion.matches(publication.version)) { "GitHub Packages is the testing-snapshot channel" }
                }
            }
        }
    }
}

gradle.projectsEvaluated {
    rootProject.tasks.register("githubPackagePublicationManifest") {
        val manifest = rootProject.layout.buildDirectory.file("github-packages-manifest.json")
        outputs.file(manifest)
        // Publication models can change between recovery runs; inspect the configured files afresh.
        outputs.upToDateWhen { false }
        doLast {
            val version = rootProject.version.toString()
            require(snapshotVersion.matches(version)) { "Expected a commit-qualified testing snapshot" }
            val paths = rootProject.allprojects.flatMap { project ->
                project.extensions.findByType(PublishingExtension::class.java)
                    ?.publications?.withType(MavenPublication::class.java)?.flatMap { publication ->
                        require(publication.version == version)
                        val stem = "${publication.artifactId}-$version"
                        val directory = "${publication.groupId.replace('.', '/')}/${publication.artifactId}/$version/"
                        val files = publication.artifacts.map { artifact ->
                            val classifier = artifact.classifier?.takeIf(String::isNotEmpty)?.let { "-$it" }.orEmpty()
                            "$stem$classifier.${artifact.extension}"
                        } + listOf("$stem.pom", "$stem.module")
                        files.map { directory + it }
                    }.orEmpty()
            }.distinct().sorted()
            require(paths.isNotEmpty())
            manifest.get().asFile.apply {
                parentFile.mkdirs()
                writeText(JsonOutput.toJson(mapOf("version" to version, "paths" to paths)))
            }
        }
    }
}
