package io.github.hlcaptain.symbols.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import io.github.hlcaptain.symbols.generator.SymbolGeneratorCli
import io.github.hlcaptain.symbols.generator.SymbolManifestParser
import io.github.hlcaptain.symbols.generator.SymbolNames
import io.github.hlcaptain.symbols.generator.SvgIconExtractor
import java.io.File
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Gradle integration for deterministic typed font and SVG icon generation. */
class SymbolFontsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val generatorClasspath = project.createGeneratorClasspath()
        val extension = project.extensions.create(
            "symbolFonts",
            SymbolFontsExtension::class.java,
        )
        val catalogs = project.registerCatalogsTask(extension)
        var catalogsWired = false
        extension.catalogs.all {
            if (!catalogsWired) {
                project.wireGeneratedKotlin(
                    catalogs.flatMap { task -> task.outputDirectory },
                )
                catalogsWired = true
            }
        }
        val fontDescriptors = project.registerFontDescriptorsTask(
            extension = extension,
            generatorClasspath = generatorClasspath,
        )
        project.wireGeneratedKotlin(
            fontDescriptors.flatMap { task -> task.outputDirectory },
        )
        project.wireFontDescriptorResourceSettings(fontDescriptors, extension)
        val derivedNames = DerivedNameRegistry()
        val conventionalComposeResources = project.layout.projectDirectory
            .dir("src/commonMain/composeResources")
            .asFile
            .toPath()
            .toAbsolutePath()
            .normalize()
        val composeResources = project.tasks.register(
            "mergeGeneratedSymbolComposeResources",
            MergeSymbolComposeResources::class.java,
        ) { task ->
            task.group = "symbol fonts"
            task.description =
                "Merges symbol fonts and generated drawables into one Compose resource root."
            task.inputDirectories.from(
                project.layout.projectDirectory.dir("src/commonMain/composeResources"),
            )
            task.inputDirectories.from(
                extension.composeFontResources.filter { directory ->
                    directory.toPath().toAbsolutePath().normalize() !=
                        conventionalComposeResources
                },
            )
            task.outputDirectory.convention(
                project.layout.buildDirectory.dir(
                    "generated/symbolFonts/composeResources",
                ),
            )
        }
        project.wireComposeResources(composeResources)

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
            val defaultPackageName = project.androidDefaultPackageName()
            extension.iconSets.forEach { iconSet ->
                iconSet.packageName.convention(defaultPackageName)
            }
            validateConfiguredIconSets(extension)
        }
    }
}

private fun Project.registerCatalogsTask(
    extension: SymbolFontsExtension,
): TaskProvider<GenerateSymbolCatalogsTask> {
    val task = tasks.register(
        "generateSymbolCatalogs",
        GenerateSymbolCatalogsTask::class.java,
    ) { catalogs ->
        catalogs.group = "symbol fonts"
        catalogs.description =
            "Generates runtime symbol catalogs from codepoint manifests."
        catalogs.packageName.set(extension.catalogPackageName)
        catalogs.catalogs.convention(emptyList())
        catalogs.outputDirectory.convention(
            layout.buildDirectory.dir("generated/symbolFonts/catalogs/kotlin"),
        )
    }
    extension.catalogs.all { catalog ->
        task.configure { catalogs ->
            catalogs.catalogs.add(catalog)
        }
    }
    return task
}

private fun Project.registerFontDescriptorsTask(
    extension: SymbolFontsExtension,
    generatorClasspath: FileCollection,
): TaskProvider<GenerateSymbolFontDescriptorsTask> = tasks.register(
    "generateSymbolFontDescriptors",
    GenerateSymbolFontDescriptorsTask::class.java,
) { task ->
    task.group = "symbol fonts"
    task.description = "Generates typed descriptors for Compose font resources."
    task.generatorClasspath.from(generatorClasspath)
    task.resourceRoots.from(extension.composeFontResources)
    task.resourcePackage.convention(provider(::defaultComposeResourcePackage))
    task.resourceClassName.convention("Res")
    task.publicAccessors.convention(false)
    task.outputDirectory.convention(
        layout.buildDirectory.dir("generated/symbolFonts/fontDescriptors/kotlin"),
    )
}

private fun Project.wireFontDescriptorResourceSettings(
    task: TaskProvider<GenerateSymbolFontDescriptorsTask>,
    extension: SymbolFontsExtension,
) {
    plugins.withId("org.jetbrains.compose") {
        val resources = composeResourcesExtension()
        task.configure { descriptors ->
            descriptors.resourcePackage.set(
                provider {
                    resources.packageOfResClass.ifEmpty {
                        defaultComposeResourcePackage()
                    }
                },
            )
            descriptors.resourceClassName.set(provider { resources.nameOfResClass })
            descriptors.publicAccessors.set(provider { resources.publicResClass })
        }
        afterEvaluate {
            if (!extension.composeFontResources.isEmpty) {
                resources.generateResClass = resources.always
            }
        }
    }
}

private fun Project.defaultComposeResourcePackage(): String {
    val groupName = group.toString().lowercase().composeResourceIdentifier()
    val moduleName = name.lowercase().composeResourceIdentifier()
    val id = if (groupName.isNotEmpty()) "$groupName.$moduleName" else moduleName
    return "$id.generated.resources"
}

private fun String.composeResourceIdentifier(): String =
    replace('-', '_').let { value ->
        if (value.firstOrNull()?.isDigit() == true) "_$value" else value
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
        "org.jetbrains.skiko:skiko-awt:${SymbolFontsBuildConfig.SKIKO_VERSION}",
    )
    dependencies.add(
        runtime.name,
        "org.jetbrains.skiko:skiko-awt-runtime-${skikoHost()}:" +
            SymbolFontsBuildConfig.SKIKO_VERSION,
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
        task.svgDirectory.set(style.svgDirectory)
        task.conventionalFontName.set(style.conventionalFontName)
        task.packageName.set(iconSet.packageName)
        task.rootName.set(iconSet.rootName)
        task.styleName.set(style.name)
        task.fontIndex.set(style.fontIndex)
        task.axes.set(style.axes)
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
        if (!style.font.isPresent && !style.svgDirectory.isPresent) {
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
        afterEvaluate {
            composeResourcesExtension()
                .customDirectory(
                    "commonMain",
                    mergeTask.flatMap { task -> task.outputDirectory },
                )
        }
    }
}

private fun Project.wireAndroidResources(
    task: TaskProvider<GenerateSymbolFontTask>,
) {
    plugins.withId("com.android.application") {
        wireAndroidResources(
            extensions.getByType(
                ApplicationAndroidComponentsExtension::class.java,
            ),
            task,
        )
    }
    plugins.withId("com.android.library") {
        wireAndroidResources(
            extensions.getByType(
                LibraryAndroidComponentsExtension::class.java,
            ),
            task,
        )
    }
}

private fun <DslExtensionT, VariantBuilderT : VariantBuilder, VariantT : Variant>
    Project.wireAndroidResources(
        components: AndroidComponentsExtension<
            DslExtensionT,
            VariantBuilderT,
            VariantT,
        >,
        task: TaskProvider<GenerateSymbolFontTask>,
    ) {
        components.onVariants(
            components.selector().all(),
            Action<VariantT> { variant ->
                variant.sources.res?.addGeneratedSourceDirectory(
                    task,
                    GenerateSymbolFontTask::androidOutputDirectory,
                )
            },
        )
    }

private fun Project.androidDefaultPackageName(): String {
    val namespace = when {
        plugins.hasPlugin("com.android.application") ->
            extensions.getByType(ApplicationExtension::class.java).namespace
        plugins.hasPlugin("com.android.library") ->
            extensions.getByType(LibraryExtension::class.java).namespace
        else -> null
    }?.takeIf(String::isNotBlank)
        ?: return defaultPackageName()
    return "$namespace.generated"
}

private fun Project.composeResourcesExtension(): ResourcesExtension =
    extensions
        .getByType(ComposeExtension::class.java)
        .extensions
        .getByType(ResourcesExtension::class.java)

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
        iconSet.styles.sortedBy(SymbolFontStyle::getName).forEach { style ->
            validateStyleSource(style, "$iconSetOwner style '${style.name}'")
        }

        if (resourceStyles.isEmpty()) {
            return@forEach
        }
        resourceStyles.forEach { style ->
            val styleOwner = "$iconSetOwner style '${style.name}'"
            val selectedEntries = resourceEntries(style)
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

            selectedEntries.forEach { entry ->
                val resourceName = buildString {
                    append(combinedPrefix)
                    append('_')
                    append(entry.semanticName)
                    entry.codePoint?.let { codePoint ->
                        append("_u")
                        append(codePoint.toString(16).lowercase())
                    }
                }
                claim(
                    kind = "generated Android resource name",
                    key = resourceName,
                    owner = if (entry.codePoint != null) {
                        "$styleOwner U+" +
                            entry.codePoint.toString(16).uppercase()
                    } else {
                        "$styleOwner '${entry.semanticName}'"
                    },
                    owners = androidResourceOwners,
                )
            }
        }
    }
}

private fun validateStyleSource(
    style: SymbolFontStyle,
    owner: String,
) {
    if (style.svgDirectory.isPresent) {
        val fontInputs = buildList {
            if (style.codepoints.isPresent) add("codepoints")
            if (style.font.isPresent) add("font")
            if (style.conventionalFontName.get().isNotEmpty()) add("font(name)")
            if (style.fontIndex.get() != 0) add("fontIndex")
            if (style.axes.get().isNotEmpty()) add("axes")
        }
        require(fontInputs.isEmpty()) {
            "$owner configures svgDirectory together with font-only inputs: " +
                fontInputs.joinToString()
        }
    } else {
        require(style.codepoints.isPresent) {
            "$owner must configure svgDirectory or codepoints for a font source"
        }
    }
}

private data class ResourceEntry(
    val semanticName: String,
    val codePoint: Int?,
)

private fun resourceEntries(style: SymbolFontStyle): List<ResourceEntry> {
    if (style.svgDirectory.isPresent) {
        return SvgIconExtractor.discoverNames(
            style.svgDirectory.get().asFile.toPath(),
        ).map { name -> ResourceEntry(name, null) }
    }
    val catalog = SymbolManifestParser.parse(style.codepoints.get().asFile.toPath())
    return catalog.entries
        .groupBy { entry -> entry.codePoint }
        .map { (codePoint, aliases) ->
            ResourceEntry(
                semanticName = aliases.minOf { alias -> alias.name },
                codePoint = codePoint,
            )
        }
        .sortedBy { entry -> entry.codePoint }
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
