import groovy.json.JsonOutput
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
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
    if (gradle.parent != null) return@projectsEvaluated
    val requestedTasks = rootProject.providers.gradleProperty("githubPackagePublications").orNull
        ?.takeIf(String::isNotEmpty)?.let { selection ->
            require(selection.isNotBlank()) { "Expected GitHub publication task paths, not whitespace" }
            selection.trim().lines().map(String::trim).also { paths ->
                require(paths.all(String::isNotEmpty)) { "Empty GitHub publication task path" }
                require(paths.distinct().size == paths.size) { "Duplicate GitHub publication task path" }
            }
        }
    val publicationTasks = rootProject.allprojects.flatMap { project ->
        project.tasks.withType(PublishToMavenRepository::class.java)
            .filter { it.repository.name == "GitHubPackages" }
    }.associateBy { it.path }
    val selectedTasks = requestedTasks?.map { path ->
        requireNotNull(publicationTasks[path]) { "Unknown GitHub publication task: $path" }
    } ?: publicationTasks.values.sortedBy { it.path }
    require(selectedTasks.isNotEmpty()) { "No GitHub publications are configured" }
    if (requestedTasks != null) {
        publicationTasks.values.forEach { publishTask ->
            publishTask.doFirst {
                require(publishTask.path in requestedTasks) {
                    "GitHub publication task was not selected: ${publishTask.path}"
                }
            }
        }
    }
    rootProject.tasks.register("githubPackagePublicationManifest") {
        val manifest = rootProject.layout.buildDirectory.file("github-packages-manifest.json")
        outputs.file(manifest)
        // Publication models can change between recovery runs; inspect the configured files afresh.
        outputs.upToDateWhen { false }
        doLast {
            val version = rootProject.version.toString()
            require(snapshotVersion.matches(version)) { "Expected a commit-qualified testing snapshot" }
            val paths = selectedTasks.flatMap { task ->
                val publication = task.publication
                require(publication.version == version)
                val stem = "${publication.artifactId}-$version"
                val directory = "${publication.groupId.replace('.', '/')}/${publication.artifactId}/$version/"
                val files = publication.artifacts.map { artifact ->
                    val classifier = artifact.classifier?.takeIf(String::isNotEmpty)?.let { "-$it" }.orEmpty()
                    "$stem$classifier.${artifact.extension}"
                } + listOf("$stem.pom", "$stem.module")
                files.map { directory + it }
            }.distinct().sorted()
            require(paths.isNotEmpty())
            manifest.get().asFile.apply {
                parentFile.mkdirs()
                writeText(JsonOutput.toJson(mapOf(
                    "version" to version,
                    "paths" to paths,
                    "tasks" to selectedTasks.map { it.path },
                )))
            }
        }
    }
}
