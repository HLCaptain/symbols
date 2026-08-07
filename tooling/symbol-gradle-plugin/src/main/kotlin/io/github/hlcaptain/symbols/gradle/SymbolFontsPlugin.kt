package io.github.hlcaptain.symbols.gradle

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import io.github.hlcaptain.symbols.generator.SymbolGeneratorCli
import io.github.hlcaptain.symbols.generator.SymbolManifestParser
import io.github.hlcaptain.symbols.generator.SymbolNames
import java.io.File
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Gradle integration for deterministic, typed symbol-font generation. */
public class SymbolFontsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val generatorClasspath = project.createGeneratorClasspath()
        val extension = project.extensions.create(
            "symbolFonts",
            SymbolFontsExtension::class.java,
        )
        var androidProject = false
        project.plugins.withId("com.android.application") {
            androidProject = true
        }
        project.plugins.withId("com.android.library") {
            androidProject = true
        }
        val derivedNames = DerivedNameRegistry()
        val composeResources = project.tasks.register(
            "mergeGeneratedSymbolComposeResources",
            MergeSymbolComposeResources::class.java,
        ) { task ->
            task.group = "symbol fonts"
            task.description =
                "Merges generated symbol drawables into one Compose resource root."
            task.outputDirectory.convention(
                project.layout.buildDirectory.dir(
                    "generated/symbolFonts/composeResources",
                ),
            )
        }

        var composeResourcesWired = false
        extension.iconSets.all { iconSet ->
            derivedNames.claimNamespace(iconSet.name)
            val namespaceTask = project.registerNamespaceTask(iconSet)
            project.wireGeneratedKotlin(
                namespaceTask.flatMap { task -> task.outputDirectory },
            )
            iconSet.styles.all { style ->
                namespaceTask.configure { task ->
                    task.styleNames.add(style.name)
                }
                if (!composeResourcesWired) {
                    project.wireComposeResources(composeResources)
                    composeResourcesWired = true
                }
                derivedNames.claimStyle(iconSet.name, style.name)
                style.resourcePrefix.convention(
                    iconSet.rootName.map(::androidResourcePrefix),
                )
                val task = project.registerGenerationTask(
                    iconSet = iconSet,
                    style = style,
                    generatorClasspath = generatorClasspath,
                )
                composeResources.configure {
                    it.inputDirectories.from(
                        task.flatMap { generated ->
                            generated.composeOutputDirectory
                        },
                    )
                }
                project.wireGeneratedKotlin(
                    task.flatMap { generated ->
                        generated.kotlinOutputDirectory
                    },
                )
                project.wireAndroidResources(task)
            }
        }
        project.afterEvaluate {
            val defaultPackageName = if (androidProject) {
                project.androidDefaultPackageName()
            } else {
                project.defaultPackageName()
            }
            extension.iconSets.forEach { iconSet ->
                iconSet.packageName.convention(defaultPackageName)
            }
            validateConfiguredIconSets(extension)
        }
    }
}

private fun Project.createGeneratorClasspath(): FileCollection {
    val runtime = configurations.create(
        "symbolFontGeneratorRuntimeClasspath",
    ) { configuration ->
        configuration.isCanBeConsumed = false
        configuration.isCanBeResolved = true
        configuration.isVisible = false
        configuration.description =
            "Isolated symbol-font generator runtime for this host."
        configuration.attributes { attributes ->
            attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                objects.named(Usage::class.java, Usage.JAVA_RUNTIME),
            )
        }
    }
    dependencies.add(
        runtime.name,
        "org.jetbrains.skiko:skiko-awt:$SkikoVersion",
    )
    dependencies.add(
        runtime.name,
        "org.jetbrains.skiko:skiko-awt-runtime-${skikoHost()}:$SkikoVersion",
    )

    /*
     * Gradle plugin implementations carry their implementation dependencies in
     * the plugin classloader. Reusing that exact core artifact works for both an
     * included build and a published plugin, without asking the consumer's
     * repositories to contain a matching snapshot of symbol-generator-core.
     */
    val generatorCodeSource = SymbolGeneratorCli::class.java
        .protectionDomain
        .codeSource
        ?.location
        ?: error("Cannot locate the symbol generator implementation.")
    val generatorArtifact = File(generatorCodeSource.toURI())

    return files(generatorArtifact, runtime)
}

private fun Project.registerGenerationTask(
    iconSet: SymbolIconSet,
    style: SymbolFontStyle,
    generatorClasspath: FileCollection,
): TaskProvider<GenerateSymbolFontTask> {
    val generationTask = tasks.register(
        generationTaskName(iconSet.name, style.name),
        GenerateSymbolFontTask::class.java,
    ) { task ->
        task.group = "symbol fonts"
        task.description =
            "Generates ${iconSet.name}.${style.name} icons."

        task.generatorClasspath.from(generatorClasspath)
        task.manifest.set(style.codepoints)
        task.font.set(style.font)
        task.conventionalFontName.set(style.conventionalFontName)
        task.packageName.set(iconSet.packageName)
        task.rootName.set(iconSet.rootName)
        task.styleName.set(style.name)
        task.fontIndex.set(style.fontIndex)
        task.axes.set(style.axes)
        task.includedNames.set(iconSet.includedNames)
        task.generateImageVectors.set(style.generateImageVectors)
        task.generateAndroidDrawables.set(style.generateAndroidDrawables)
        task.generateComposeDrawables.set(style.generateComposeDrawables)
        task.resourcePrefix.set(style.resourcePrefix)
        task.symbolsPerFile.set(style.symbolsPerFile)
        task.precision.set(style.precision)
        task.viewportWidth.set(style.viewportWidth)
        task.viewportHeight.set(style.viewportHeight)
        task.emSize.set(style.emSize)
        task.originX.set(style.originX)
        task.baselineY.set(style.baselineY)

        val outputRoot = generationOutputRoot(iconSet.name, style.name)
        task.kotlinOutputDirectory.convention(
            layout.buildDirectory.dir("$outputRoot/kotlin"),
        )
        task.androidOutputDirectory.convention(
            layout.buildDirectory.dir("$outputRoot/androidRes"),
        )
        task.composeOutputDirectory.convention(
            layout.buildDirectory.dir("$outputRoot/composeResources"),
        )
    }
    afterEvaluate {
        if (!style.font.isPresent) {
            generationTask.configure { task ->
                task.conventionalFonts.from(
                    fileTree(layout.projectDirectory.dir("src")) {
                        it.include(
                            "main/res/font/*",
                            "commonMain/composeResources/font/*",
                        )
                    },
                )
            }
        }
    }
    return generationTask
}

private fun Project.registerNamespaceTask(
    iconSet: SymbolIconSet,
): TaskProvider<GenerateSymbolNamespaceTask> {
    return tasks.register(
        namespaceTaskName(iconSet.name),
        GenerateSymbolNamespaceTask::class.java,
    ) { task ->
        task.group = "symbol fonts"
        task.description =
            "Generates the shared ${iconSet.name} typed icon namespace."
        task.packageName.set(iconSet.packageName)
        task.rootName.set(iconSet.rootName)
        task.styleNames.convention(emptySet())
        task.outputDirectory.convention(
            layout.buildDirectory.dir(
                namespaceOutputDirectory(iconSet.name),
            ),
        )
    }
}

private fun Project.wireGeneratedKotlin(
    sourceDirectory: Provider<Directory>,
) {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions
            .getByType(KotlinMultiplatformExtension::class.java)
            .sourceSets
            .named("commonMain")
            .configure { sourceSet ->
                sourceSet.kotlin.srcDir(
                    sourceDirectory,
                )
            }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions
            .getByType(KotlinJvmProjectExtension::class.java)
            .sourceSets
            .named("main")
            .configure { sourceSet ->
                sourceSet.kotlin.srcDir(
                    sourceDirectory,
                )
            }
    }
}

private fun Project.wireComposeResources(
    mergeTask: TaskProvider<MergeSymbolComposeResources>,
) {
    plugins.withId("org.jetbrains.compose") {
        val compose = extensions
            .getByType(ComposeExtension::class.java)
        (compose as ExtensionAware)
            .extensions
            .getByType(ResourcesExtension::class.java)
            .customDirectory(
                "commonMain",
                mergeTask.flatMap { task -> task.outputDirectory },
            )
    }
}

private fun Project.wireAndroidResources(
    task: TaskProvider<GenerateSymbolFontTask>,
) {
    fun wire() {
        @Suppress("UNCHECKED_CAST")
        val components = extensions.getByType(
            AndroidComponentsExtension::class.java,
        ) as AndroidComponentsExtension<Any, VariantBuilder, Variant>
        components.onVariants(
            components.selector().all(),
            Action { variant ->
                variant.sources.res?.addGeneratedSourceDirectory(
                    task,
                    GenerateSymbolFontTask::androidOutputDirectory,
                )
            },
        )
    }

    plugins.withId("com.android.application") { wire() }
    plugins.withId("com.android.library") { wire() }
}

private fun Project.androidDefaultPackageName(): String {
    @Suppress("UNCHECKED_CAST")
    val namespace =
        (extensions.findByName("android") as?
            CommonExtension<*, *, *, *, *, *>)
            ?.namespace
            ?.takeIf(String::isNotBlank)
        ?: return defaultPackageName()
    return "$namespace.generated"
}

private fun Project.defaultPackageName(): String {
    val projectSegment = androidResourcePrefix(name).let { segment ->
        runCatching { SymbolNames.requirePackageName(segment) }
            .fold(onSuccess = { segment }, onFailure = { "symbols_$segment" })
    }
    val groupPackage = group.toString().takeUnless { value ->
        value == "unspecified" ||
            runCatching { SymbolNames.requirePackageName(value) }.isFailure
    }
    return if (groupPackage != null) {
        "$groupPackage.$projectSegment.generated"
    } else {
        "generated.symbols.$projectSegment"
    }
}

private fun skikoHost(): String {
    val architecture = when (
        System.getProperty("os.arch").lowercase()
    ) {
        "aarch64", "arm64" -> "arm64"
        "amd64", "x86_64", "x64" -> "x64"
        else -> error(
            "Unsupported symbol-font generator architecture: " +
                System.getProperty("os.arch"),
        )
    }
    val operatingSystem = System.getProperty("os.name").lowercase()
    val os = when {
        operatingSystem.startsWith("mac") ||
            operatingSystem.startsWith("darwin") -> "macos"
        operatingSystem.startsWith("linux") -> "linux"
        operatingSystem.startsWith("windows") -> "windows"
        else -> error(
            "Unsupported symbol-font generator operating system: " +
                System.getProperty("os.name"),
        )
    }
    return "$os-$architecture"
}

private fun androidResourcePrefix(value: String): String =
    value
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .replace(Regex("[^A-Za-z0-9_]+"), "_")
        .trim('_')
        .lowercase()
        .let { normalized ->
            require(normalized.isNotEmpty()) {
                "Cannot derive an Android resource prefix from '$value'"
            }
            if (normalized.first().isDigit()) {
                "symbols_$normalized"
            } else {
                normalized
            }
        }

private fun taskSegment(value: String): String =
    value
        .split(Regex("[^A-Za-z0-9]+"))
        .filter(String::isNotEmpty)
        .joinToString("") { segment ->
            segment.replaceFirstChar(Char::uppercaseChar)
        }
        .also { result ->
            require(result.isNotEmpty() && !result.first().isDigit()) {
                "Cannot derive a Gradle task segment from '$value'"
            }
        }

private fun fileSegment(value: String): String =
    androidResourcePrefix(value)

private fun namespaceTaskName(iconSetName: String): String =
    "generate${taskSegment(iconSetName)}SymbolFontNamespace"

private fun generationTaskName(
    iconSetName: String,
    styleName: String,
): String =
    "generate${taskSegment(iconSetName)}${taskSegment(styleName)}SymbolFonts"

private fun namespaceOutputDirectory(iconSetName: String): String =
    "generated/symbolFonts/${fileSegment(iconSetName)}/namespace/kotlin"

private fun generationOutputRoot(
    iconSetName: String,
    styleName: String,
): String =
    "generated/symbolFonts/${fileSegment(iconSetName)}/${fileSegment(styleName)}"

private class DerivedNameRegistry {
    private val taskOwners = linkedMapOf<String, String>()
    private val outputOwners = linkedMapOf<String, String>()

    fun claimNamespace(iconSetName: String) {
        val owner = "icon set '$iconSetName' namespace"
        claim(
            kind = "Gradle task name",
            key = namespaceTaskName(iconSetName),
            owner = owner,
            owners = taskOwners,
        )
        claim(
            kind = "generated output directory",
            key = namespaceOutputDirectory(iconSetName),
            owner = owner,
            owners = outputOwners,
        )
    }

    fun claimStyle(iconSetName: String, styleName: String) {
        val owner = "icon set '$iconSetName' style '$styleName'"
        claim(
            kind = "Gradle task name",
            key = generationTaskName(iconSetName, styleName),
            owner = owner,
            owners = taskOwners,
        )
        val outputRoot = generationOutputRoot(iconSetName, styleName)
        listOf("kotlin", "androidRes", "composeResources").forEach { outputKind ->
            claim(
                kind = "generated output directory",
                key = "$outputRoot/$outputKind",
                owner = owner,
                owners = outputOwners,
            )
        }
    }
}

private fun validateConfiguredIconSets(extension: SymbolFontsExtension) {
    val kotlinRootOwners = linkedMapOf<String, String>()
    val kotlinSourceOwners = linkedMapOf<String, String>()
    val androidPrefixOwners = linkedMapOf<String, String>()
    val androidResourceOwners = linkedMapOf<String, String>()

    extension.iconSets.sortedBy(SymbolIconSet::getName).forEach { iconSet ->
        val packageName = iconSet.packageName.get()
        val rootName = iconSet.rootName.get()
        val iconSetOwner = "icon set '${iconSet.name}'"
        SymbolNames.requirePackageName(packageName)
        SymbolNames.requireTypeIdentifier(rootName, "icon-set name")
        claim(
            kind = "generated Kotlin root",
            key = "$packageName.$rootName",
            owner = iconSetOwner,
            owners = kotlinRootOwners,
        )

        val stylePackageOwners = linkedMapOf<String, String>()
        val resourceStyles = iconSet.styles
            .sortedBy(SymbolFontStyle::getName)
            .filter { style ->
                style.generateAndroidDrawables.get() ||
                    style.generateComposeDrawables.get()
            }

        iconSet.styles.sortedBy(SymbolFontStyle::getName).forEach { style ->
            val styleOwner = "$iconSetOwner style '${style.name}'"
            SymbolNames.requireTypeIdentifier(style.name, "style name")
            val packageSegment = SymbolNames.packageSegment(style.name)
            claim(
                kind = "generated style package segment",
                key = packageSegment,
                owner = styleOwner,
                owners = stylePackageOwners,
            )
            claim(
                kind = "generated Kotlin source facade",
                key = "$packageName.$packageSegment." +
                    "$rootName${style.name}Icons",
                owner = styleOwner,
                owners = kotlinSourceOwners,
            )
        }

        if (resourceStyles.isEmpty()) {
            return@forEach
        }
        resourceStyles.forEach { style ->
            val styleOwner = "$iconSetOwner style '${style.name}'"
            val selectedEntries = selectedResourceEntries(iconSet, style)
            val resourcePrefix = style.resourcePrefix.get()
            require(AndroidResourcePrefix.matches(resourcePrefix)) {
                "Invalid Android resource prefix '$resourcePrefix' for $styleOwner"
            }
            val combinedPrefix =
                "${resourcePrefix}_${SymbolNames.androidResourcePrefix(style.name)}"
            claim(
                kind = "generated Android resource prefix",
                key = combinedPrefix,
                owner = styleOwner,
                owners = androidPrefixOwners,
            )

            selectedEntries.forEach { (codePoint, semanticName) ->
                val resourceName =
                    "${combinedPrefix}_${semanticName}_" +
                        "u${codePoint.toString(16).lowercase()}"
                claim(
                    kind = "generated Android resource name",
                    key = resourceName,
                    owner = "$styleOwner U+${codePoint.toString(16).uppercase()}",
                    owners = androidResourceOwners,
                )
            }
        }
    }
}

private fun selectedResourceEntries(
    iconSet: SymbolIconSet,
    style: SymbolFontStyle,
): List<Pair<Int, String>> {
    val catalog = SymbolManifestParser.parse(style.codepoints.get().asFile.toPath())
    val includedNames = iconSet.includedNames.get()
    val selected = if (includedNames.isEmpty()) {
        catalog.entries
    } else {
        val entriesByName = catalog.entries.associateBy { entry -> entry.name }
        val unknownNames = includedNames - entriesByName.keys
        require(unknownNames.isEmpty()) {
            "Unknown included symbol names for ${iconSet.name}.${style.name}: " +
                unknownNames.sorted().joinToString()
        }
        includedNames.map(entriesByName::getValue)
    }
    return selected
        .groupBy { entry -> entry.codePoint }
        .map { (codePoint, aliases) ->
            codePoint to aliases.minOf { alias -> alias.name }
        }
        .sortedBy(Pair<Int, String>::first)
}

private fun claim(
    kind: String,
    key: String,
    owner: String,
    owners: MutableMap<String, String>,
) {
    val existingOwner = owners.putIfAbsent(key, owner)
    if (existingOwner != null && existingOwner != owner) {
        throw InvalidUserDataException(
            "$kind collision '$key': $existingOwner and $owner",
        )
    }
}

private val AndroidResourcePrefix = Regex("^[a-z][a-z0-9_]*$")

private const val SkikoVersion: String = "0.9.22.2"
