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
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.koinCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.roborazzi) apply false

    // Convention plugins
    alias(libs.plugins.symbolsKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.symbolsComposeMultiplatformLibrary) apply false
    alias(libs.plugins.symbolsKmpPublishing) apply false
    alias(libs.plugins.symbolsMaterialFontLibrary) apply false
    alias(libs.plugins.symbolsMaterialVectorLibrary) apply false
    alias(libs.plugins.symbolsPublishedAndroidLibrary) apply false
    alias(libs.plugins.symbolsSampleFeature) apply false
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

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val composeDrawableArchives: ConfigurableFileCollection

    @get:Input
    abstract val legalNamespaceByArchivePath: MapProperty<String, String>

    @get:Input
    abstract val composeDrawableStyleByArchivePath: MapProperty<String, String>

    @TaskAction
    fun verifyArchives() {
        val singleFontArchivePaths = singleFontArchives.files
            .map { it.toPath().toAbsolutePath().normalize() }
            .toSet()
        val namespacedLegalArchivePaths = namespacedLegalArchives.files
            .map { it.toPath().toAbsolutePath().normalize() }
            .toSet()
        val composeDrawableArchivePaths = composeDrawableArchives.files
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
            val expectedComposeDrawableStyle = if (
                archivePath in composeDrawableArchivePaths
            ) {
                checkNotNull(
                    composeDrawableStyleByArchivePath.get()[archivePath.toString()],
                ) {
                    "Missing Compose drawable metadata for $archive"
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
                if (expectedComposeDrawableStyle != null) {
                    val drawablePrefix =
                        "material_symbols_${expectedComposeDrawableStyle}_"
                    val drawables = entries.filter { entry ->
                        "/drawable/$drawablePrefix" in entry && entry.endsWith(".xml")
                    }
                    check(drawables.size == MaterialComposeDrawableCount) {
                        "$archive must contain $MaterialComposeDrawableCount " +
                            "$expectedComposeDrawableStyle Compose drawables; found " +
                            drawables.size
                    }
                    check(
                        drawables.any { entry ->
                            entry.endsWith("${drawablePrefix}home_ue9b2.xml")
                        },
                    ) {
                        "$archive is missing the expected $expectedComposeDrawableStyle " +
                            "Home drawable"
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

    private companion object {
        const val MaterialComposeDrawableCount = 3_802
    }
}

val publicationArchiveVerifiers = listOf("JvmAndAndroid", "Web", "Apple")
    .associateWith { platform ->
        tasks.register<VerifyPublishedArchives>("verify${platform}PublishedArchives") {
            group = "verification"
            description = "Verifies legal notices and font counts in $platform publication archives."
            legalDocuments.from(
                layout.projectDirectory.file("LICENSE"),
                layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"),
            )
        }
    }

tasks.register("verifyPublishedArchives") {
    group = "verification"
    description = "Verifies legal notices and font counts in all published archives."
    dependsOn(publicationArchiveVerifiers.values)
}

val verifyLibraryJvm = tasks.register("verifyLibraryJvm") {
    group = "verification"
    description = "Tests and lints published libraries and verifies their JVM and Android archives."
    dependsOn(publicationArchiveVerifiers.getValue("JvmAndAndroid"))
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
        val libraryJvmChecks = tasks.matching {
            it.name == "jvmTest" || it.name == "lintRelease" ||
                it.name == "lintAndroidMain"
        }
        verifyLibraryJvm.configure {
            dependsOn(libraryJvmChecks)
        }

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
        val fontModuleNames = setOf(
            "material-outlined",
            "material-rounded",
            "material-sharp",
            "material-outlined-static",
            "material-rounded-static",
            "material-sharp-static",
        )
        val composeDrawableStyles = mapOf(
            "material-compose-drawables-outlined" to "outlined",
            "material-compose-drawables-rounded" to "rounded",
            "material-compose-drawables-sharp" to "sharp",
        )
        val publicationDescription = when (project.name) {
            "symbols-core" ->
                "Common Symbols namespace for built-in and generated Kotlin " +
                    "Multiplatform symbol-set entry points."
            "variant-font-core" ->
                "Generic regular/variable symbol-font contracts, Compose theme, " +
                    "runtime capability checks, and accessible glyph rendering."
            "material-core" ->
                "Typed Material Symbols catalog, aliases, and code points for " +
                    "Kotlin Multiplatform; no Compose or bundled font."
            "material-compose" ->
                "Material vector-style and generic font-settings theming " +
                    "in Compose Multiplatform; no bundled font."
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
                    "adapter for Android API 23 and Compose Multiplatform."
            "material-rounded-static" ->
                "Default-axis static Rounded Material Symbols font and Compose " +
                    "adapter for Android API 23 and Compose Multiplatform."
            "material-sharp-static" ->
                "Default-axis static Sharp Material Symbols font and Compose " +
                    "adapter for Android API 23 and Compose Multiplatform."
            "material-compose-drawables-outlined" ->
                "Default-axis Outlined Material Symbols drawable resources for " +
                    "Compose Multiplatform; no bundled font."
            "material-compose-drawables-rounded" ->
                "Default-axis Rounded Material Symbols drawable resources for " +
                    "Compose Multiplatform; no bundled font."
            "material-compose-drawables-sharp" ->
                "Default-axis Sharp Material Symbols drawable resources for " +
                    "Compose Multiplatform; no bundled font."
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
                    it.name == "bundleAndroidMainAar" ||
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
                            name == "bundleAndroidMainAar" ||
                            name.endsWith("ZipMultiplatformResourcesForPublication")
                    )
                val expectedComposeDrawableStyle = composeDrawableStyles[project.name]
                    ?.takeIf {
                        name == "jvmJar" ||
                            name == "bundleReleaseAar" ||
                            name == "bundleAndroidMainAar" ||
                            name.endsWith("ZipMultiplatformResourcesForPublication")
                    }

                val platform = when {
                    name.startsWith("ios") -> "Apple"
                    name.startsWith("js") || name.startsWith("wasmJs") -> "Web"
                    name in setOf(
                        "jvmJar",
                        "jvmSourcesJar",
                        "androidReleaseSourcesJar",
                        "androidSourcesJar",
                        "bundleReleaseAar",
                        "bundleAndroidMainAar",
                        "allMetadataJar",
                        "sourcesJar",
                    ) -> "JvmAndAndroid"
                    else -> error("Unclassified publication archive: $path")
                }
                publicationArchiveVerifiers.getValue(platform).configure {
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
                    if (expectedComposeDrawableStyle != null) {
                        composeDrawableArchives.from(archiveTask.archiveFile)
                        composeDrawableStyleByArchivePath.put(
                            archivePath,
                            expectedComposeDrawableStyle,
                        )
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
                val publicationName = name
                // Gradle signs beside each artifact; sharing a JAR also shares its .asc output.
                val centralJavadocJar = tasks.register<Jar>("${publicationName}CentralJavadocJar") {
                    archiveClassifier.set("javadoc")
                    destinationDirectory.set(layout.buildDirectory.dir("publications/$publicationName/javadoc"))
                    from(rootProject.layout.projectDirectory.file("README.md"))
                }
                artifact(centralJavadocJar)
                if (signingKey.isPresent) {
                    project.extensions.getByType<SigningExtension>().sign(this)
                }
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
        }

    }
}
