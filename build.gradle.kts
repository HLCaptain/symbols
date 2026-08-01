import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.provider.MapProperty
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

abstract class VerifyPublishedArchives : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val legalDocuments: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archives: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val singleFontArchives: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val namespacedLegalArchives: ConfigurableFileCollection

    @get:Input
    abstract val legalNamespaceByArchivePath: MapProperty<String, String>

    @TaskAction
    fun verifyArchives() {
        val singleFontArchivePaths = singleFontArchives.files
            .map { it.toPath().toAbsolutePath().normalize() }
            .toSet()
        val namespacedLegalArchivePaths = namespacedLegalArchives.files
            .map { it.toPath().toAbsolutePath().normalize() }
            .toSet()
        val expectedLegalDocuments = legalDocuments.files.associate {
            it.name to it.readBytes()
        }

        archives.files.sortedBy { it.path }.forEach { archive ->
            check(archive.isFile) {
                "Expected publication archive does not exist: $archive"
            }
            val archivePath = archive.toPath().toAbsolutePath().normalize()
            val expectsNamespacedLegal =
                archivePath in namespacedLegalArchivePaths
            val expectedLegalNamespace = if (expectsNamespacedLegal) {
                checkNotNull(
                    legalNamespaceByArchivePath.get()[archivePath.toString()],
                ) {
                    "Missing legal namespace metadata for $archive"
                }
            } else {
                null
            }
            ZipFile(archive).use { zip ->
                val entries = buildList {
                    val archiveEntries = zip.entries()
                    while (archiveEntries.hasMoreElements()) {
                        add(archiveEntries.nextElement().name)
                    }
                }

                expectedLegalDocuments.forEach {
                    (documentName, expectedContent) ->
                    val expectedEntryName = if (expectedLegalNamespace != null) {
                        "META-INF/$expectedLegalNamespace/$documentName"
                    } else {
                        "META-INF/$documentName"
                    }
                    val matchingEntries = entries.filter {
                        it == expectedEntryName
                    }
                    check(matchingEntries.size == 1) {
                        "$archive must contain exactly one module-owned legal " +
                            "entry at $expectedEntryName; found " +
                            "${matchingEntries.size}"
                    }
                    val legalEntryName = matchingEntries.single()
                    val actualContent = zip.getInputStream(
                        zip.getEntry(legalEntryName),
                    ).use { input ->
                        input.readBytes()
                    }
                    check(actualContent.contentEquals(expectedContent)) {
                        "$archive contains stale or modified content at " +
                            legalEntryName
                    }
                }

                val fonts = entries.filter { it.endsWith(".ttf", ignoreCase = true) }
                if (archivePath in singleFontArchivePaths) {
                    check(fonts.size == 1) {
                        "$archive must contain exactly one TTF font; " +
                            "found ${fonts.size}: $fonts"
                    }
                } else {
                    check(fonts.isEmpty()) {
                        "$archive must not contain a TTF font; found ${fonts.size}: $fonts"
                    }
                }
            }
        }

        logger.lifecycle(
            "Verified legal notices in ${archives.files.size} publication archives " +
                "(${namespacedLegalArchives.files.size} module-namespaced) and the " +
                "single-font invariant in ${singleFontArchives.files.size} archives.",
        )
    }
}

val verifyPublishedArchives = tasks.register<VerifyPublishedArchives>(
    "verifyPublishedArchives",
) {
    group = "verification"
    description = "Verifies legal notices and font counts in published archives."
    legalDocuments.from(
        layout.projectDirectory.file("LICENSE"),
        layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"),
    )
}

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        plugins.withId("maven-publish") {
            extensions.configure<KotlinMultiplatformExtension> {
                targets.configureEach {
                    mavenPublication {
                        if (!artifactId.startsWith("symbols-")) {
                            artifactId = "symbols-$artifactId"
                        }
                    }
                }
            }
        }
    }

    plugins.withId("maven-publish") {
        val fontModuleNames = setOf(
            "material-outlined",
            "material-rounded",
            "material-sharp",
            "material-outlined-static",
            "material-rounded-static",
            "material-sharp-static",
        )
        val publicationDescription = when (project.name) {
            "material-core" ->
                "Typed Material Symbols catalog, aliases, and code points for " +
                    "Kotlin Multiplatform; no Compose or bundled font."
            "material-compose" ->
                "Regular/variable font contracts, themed axes, runtime capability " +
                    "checks, and typed font namespaces for Material Symbols in " +
                    "Compose Multiplatform; no bundled font."
            "material-outlined" ->
                "Outlined Material Symbols variable font and Compose adapter for " +
                    "Compose Multiplatform."
            "material-rounded" ->
                "Rounded Material Symbols variable font and Compose adapter for " +
                    "Compose Multiplatform."
            "material-sharp" ->
                "Sharp Material Symbols variable font and Compose adapter for " +
                    "Compose Multiplatform."
            "material-outlined-static" ->
                "Default-axis static Outlined Material Symbols font and Compose " +
                    "adapter for Android API 21 and Compose Multiplatform."
            "material-rounded-static" ->
                "Default-axis static Rounded Material Symbols font and Compose " +
                    "adapter for Android API 21 and Compose Multiplatform."
            "material-sharp-static" ->
                "Default-axis static Sharp Material Symbols font and Compose " +
                    "adapter for Android API 21 and Compose Multiplatform."
            "material-drawables-outlined" ->
                "Default-axis Outlined Material Symbols Android vector drawable " +
                    "pack; no bundled font."
            "material-drawables-rounded" ->
                "Default-axis Rounded Material Symbols Android vector drawable " +
                    "pack; no bundled font."
            "material-drawables-sharp" ->
                "Default-axis Sharp Material Symbols Android vector drawable " +
                    "pack; no bundled font."
            "material-vectors-outlined" ->
                "Default-axis Outlined Material Symbols ImageVector pack for " +
                    "Compose Multiplatform; no bundled font."
            "material-vectors-rounded" ->
                "Default-axis Rounded Material Symbols ImageVector pack for " +
                    "Compose Multiplatform; no bundled font."
            "material-vectors-sharp" ->
                "Default-axis Sharp Material Symbols ImageVector pack for " +
                    "Compose Multiplatform; no bundled font."
            "material-vectors-themed" ->
                "Theme-selected default-axis ImageVector access over all three " +
                    "style packs; no bundled font."
            else -> error("Missing publication description for ${project.path}")
        }
        val legalDocuments = rootProject.files(
            rootProject.layout.projectDirectory.file("LICENSE"),
            rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"),
        )

        tasks.withType<AbstractArchiveTask>()
            .matching {
                it.name == "jvmJar" ||
                    it.name == "jsJar" ||
                    it.name == "wasmJsJar" ||
                    it.name.endsWith("Klib") ||
                    it.name == "allMetadataJar" ||
                    it.name == "sourcesJar" ||
                    (
                        it.name.endsWith("SourcesJar") &&
                            it.name != "metadataSourcesJar" &&
                            !it.name.contains("Debug")
                    ) ||
                    it.name.endsWith("MetadataElements") ||
                    (
                        it.name.startsWith("bundle") &&
                            it.name.endsWith("Aar") &&
                            !it.name.contains("LocalLint")
                    )
            }
            .configureEach {
                from(legalDocuments) {
                    into("META-INF/${project.name}")
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                }
            }

        tasks.withType<AbstractArchiveTask>()
            .matching {
                it.name.endsWith("ZipMultiplatformResourcesForPublication")
            }
            .configureEach {
                from(legalDocuments) {
                    into("META-INF/${project.name}")
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                }
            }

        tasks.withType<AbstractArchiveTask>()
            .matching {
                it.name == "jvmJar" ||
                    it.name == "jsJar" ||
                    it.name == "wasmJsJar" ||
                    it.name.endsWith("Klib") ||
                    it.name == "allMetadataJar" ||
                    it.name == "sourcesJar" ||
                    (
                        it.name.endsWith("SourcesJar") &&
                            it.name != "metadataSourcesJar" &&
                            !it.name.contains("Debug")
                        ) ||
                    it.name.endsWith("MetadataElements") ||
                    it.name == "bundleReleaseAar" ||
                    it.name.endsWith("ZipMultiplatformResourcesForPublication")
            }
            .all {
                val archiveTask = this
                val legalNamespace = project.name
                val expectsSingleFont = project.name in fontModuleNames &&
                    (
                        name == "jvmJar" ||
                            name == "jsJar" ||
                            name == "wasmJsJar" ||
                            name == "bundleReleaseAar" ||
                            name.endsWith("ZipMultiplatformResourcesForPublication")
                    )

                verifyPublishedArchives.configure {
                    dependsOn(archiveTask)
                    archives.from(archiveTask.archiveFile)
                    namespacedLegalArchives.from(archiveTask.archiveFile)
                    val archivePath = archiveTask.archiveFile.get().asFile
                        .toPath()
                        .toAbsolutePath()
                        .normalize()
                        .toString()
                    legalNamespaceByArchivePath.put(archivePath, legalNamespace)
                    if (expectsSingleFont) {
                        singleFontArchives.from(archiveTask.archiveFile)
                    }
                }
            }

        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>()
                .matching { it.name == "kotlinMultiplatform" }
                .configureEach {
                    if (!artifactId.startsWith("symbols-")) {
                        artifactId = "symbols-$artifactId"
                    }
                }

            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set("Symbols ${project.name}")
                    description.set(publicationDescription)
                    url.set("https://github.com/HLCaptain/symbols")
                    inceptionYear.set("2025")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
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
                        connection.set("scm:git:https://github.com/HLCaptain/symbols.git")
                        developerConnection.set(
                            "scm:git:ssh://git@github.com/HLCaptain/symbols.git",
                        )
                    }
                }
            }

            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/hlcaptain/symbols")
                    credentials {
                        username = providers.environmentVariable("GITHUB_ACTOR").orNull
                        password = providers.environmentVariable("GITHUB_TOKEN").orNull
                    }
                }
            }
        }

    }
}
