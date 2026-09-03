package io.github.hlcaptain.symbols.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.SourceDirectories
import com.android.build.api.variant.Variant
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
import org.gradle.api.file.RegularFile
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
        val externalComposeResources = project.files(
            extension.composeResourceRoots,
            extension.composeFontResources,
        ).filter { directory ->
            directory.toPath().toAbsolutePath().normalize() !=
                conventionalComposeResources
        }
        val composeResources = project.tasks.registerOrConfigure<MergeSymbolComposeResources>(
            "mergeGeneratedSymbolComposeResources",
        ) {
            group = "symbol fonts"
            description =
                "Merges symbol fonts and generated drawables into one " +
                    "Compose resource root."
            inputDirectories.from(
                project.layout.projectDirectory.dir(
                    "src/commonMain/composeResources",
                ),
            )
            inputDirectories.from(externalComposeResources)
            outputDirectory.convention(
                project.layout.buildDirectory.dir(
                    "generated/symbolFonts/composeResources",
                ),
            )
        }
        project.wireComposeResources(
            mergeTask = composeResources,
            extension = extension,
            externalResources = externalComposeResources,
        )

        extension.iconSets.all { iconSet ->
            derivedNames.claimNamespace(iconSet.name)
            val namespaceTask = project.registerNamespaceTask(iconSet)
            project.wireGeneratedKotlin(
                namespaceTask.flatMap { task -> task.outputDirectory },
            )
            iconSet.styles.all { style ->
                derivedNames.claimStyle(iconSet.name, style.name)
                style.resourcePrefix.convention(
                    iconSet.rootName.map(::androidResourcePrefix),
                )
                val task = project.registerGenerationTask(
                    iconSet = iconSet,
                    style = style,
                    generatorClasspath = generatorClasspath,
                )
                project.afterEvaluate {
                    if (style.generateComposeDrawables.get()) {
                        composeResources.configure {
                            it.inputDirectories.from(
                                task.flatMap { generated ->
                                    generated.composeOutputDirectory
                                },
                            )
                        }
                    }
                }
                project.wireGeneratedKotlin(
                    task.flatMap { generated ->
                        generated.kotlinOutputDirectory
                    },
                )
                project.wireAndroidResources(
                    iconSet = iconSet,
                    style = style,
                    sharedTask = task,
                    generatorClasspath = generatorClasspath,
                )
            }
        }
        project.afterEvaluate {
            val defaultPackageName = project.androidDefaultPackageName()
            extension.iconSets.forEach { iconSet ->
                iconSet.packageName.convention(defaultPackageName)
            }
            project.warnAboutVariantAwareMixedOutputs(extension)
            validateConfiguredIconSets(extension)
        }
    }
}

private fun Project.registerCatalogsTask(
    extension: SymbolFontsExtension,
): TaskProvider<GenerateSymbolCatalogsTask> {
    val task = tasks.registerOrConfigure<GenerateSymbolCatalogsTask>(
        "generateSymbolCatalogs",
    ) {
        group = "symbol fonts"
        description =
            "Generates runtime symbol catalogs from codepoint manifests."
        packageName.set(extension.catalogPackageName)
        catalogs.convention(emptyList())
        outputDirectory.convention(
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
): TaskProvider<GenerateSymbolFontDescriptorsTask> {
    val task = tasks.registerOrConfigure<GenerateSymbolFontDescriptorsTask>(
        "generateSymbolFontDescriptors",
    ) {
        group = "symbol fonts"
        description = "Generates typed descriptors and public accessors for Compose fonts."
        this.generatorClasspath.from(generatorClasspath)
        resourceRoots.from(extension.composeFontResources)
        resourcePackage.convention(provider(::defaultComposeResourcePackage))
        resourceClassName.convention("Res")
        publicAccessors.convention(false)
        fontAccessors.convention(emptyList())
        outputDirectory.convention(
            layout.buildDirectory.dir(
                "generated/symbolFonts/fontDescriptors/kotlin",
            ),
        )
    }
    extension.fontAccessors.all { accessor ->
        task.configure { descriptors ->
            descriptors.fontAccessors.add(accessor)
        }
    }
    return task
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
    val generationTask = tasks.registerOrConfigure<GenerateSymbolFontTask>(
        generationTaskName(iconSet.name, style.name),
    ) {
        group = "symbol fonts"
        description =
            "Generates ${iconSet.name}.${style.name} icons."
        configureGenerationInputs(
            iconSet = iconSet,
            style = style,
            generatorClasspath = generatorClasspath,
        )
        generateImageVectors.set(style.generateImageVectors)
        generateAndroidDrawables.set(
            provider {
                style.generateAndroidDrawables.get() &&
                    (
                        style.androidFontResourceName.get().isEmpty() ||
                            style.svgDirectory.isPresent
                    )
            },
        )
        generateComposeDrawables.set(style.generateComposeDrawables)
        generatesAndroidDrawablesByVariant.set(
            provider {
                style.generateAndroidDrawables.get() &&
                    style.androidFontResourceName.get().isNotEmpty() &&
                    !style.svgDirectory.isPresent
            },
        )

        val outputRoot = generationOutputRoot(iconSet.name, style.name)
        kotlinOutputDirectory.convention(
            layout.buildDirectory.dir("$outputRoot/kotlin"),
        )
        androidOutputDirectory.convention(
            layout.buildDirectory.dir("$outputRoot/androidRes"),
        )
        composeOutputDirectory.convention(
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
                            "androidMain/res/font/*",
                            "commonMain/composeResources/font/*",
                        )
                    },
                )
            }
        }
    }
    return generationTask
}

private fun GenerateSymbolFontTask.configureGenerationInputs(
    iconSet: SymbolIconSet,
    style: SymbolFontStyle,
    generatorClasspath: FileCollection,
) {
    this.generatorClasspath.from(generatorClasspath)
    manifest.set(style.codepoints)
    font.set(style.font)
    svgDirectory.set(style.svgDirectory)
    conventionalFontName.set(
        style.conventionalFontName.zip(
            style.androidFontResourceName,
        ) { conventionalName, androidResourceName ->
            conventionalName.ifEmpty {
                androidResourceName
            }
        },
    )
    packageName.set(iconSet.packageName)
    rootName.set(iconSet.rootName)
    styleName.set(style.name)
    fontIndex.set(style.fontIndex)
    axes.set(style.axes)
    resourcePrefix.set(style.resourcePrefix)
    symbolsPerFile.set(style.symbolsPerFile)
    precision.set(style.precision)
    viewportWidth.set(style.viewportWidth)
    viewportHeight.set(style.viewportHeight)
    emSize.set(style.emSize)
    originX.set(style.originX)
    baselineY.set(style.baselineY)
}

private fun Project.registerNamespaceTask(
    iconSet: SymbolIconSet,
): TaskProvider<GenerateSymbolNamespaceTask> =
    tasks.registerOrConfigure<GenerateSymbolNamespaceTask>(
        namespaceTaskName(iconSet.name),
    ) {
        group = "symbol fonts"
        description =
            "Generates the shared ${iconSet.name} typed icon namespace."
        packageName.set(iconSet.packageName)
        rootName.set(iconSet.rootName)
        styleNames.convention(emptySet())
        outputDirectory.convention(
            layout.buildDirectory.dir(
                namespaceOutputDirectory(iconSet.name),
            ),
        )
        styleNames.set(
            provider {
                iconSet.styles
                    .filter { style -> style.generateImageVectors.get() }
                    .map(SymbolFontStyle::getName)
                    .toSet()
            },
        )
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
    extension: SymbolFontsExtension,
    externalResources: FileCollection,
) {
    plugins.withId("org.jetbrains.compose") {
        afterEvaluate {
            val generatesComposeDrawables = extension.iconSets.any { iconSet ->
                iconSet.styles.any { style -> style.generateComposeDrawables.get() }
            }
            if (generatesComposeDrawables || !externalResources.isEmpty) {
                composeResourcesExtension().customDirectory(
                    "commonMain",
                    mergeTask.flatMap { task -> task.outputDirectory },
                )
            }
        }
    }
}

private fun Project.wireAndroidResources(
    iconSet: SymbolIconSet,
    style: SymbolFontStyle,
    sharedTask: TaskProvider<GenerateSymbolFontTask>,
    generatorClasspath: FileCollection,
) {
    plugins.withId("com.android.application") {
        extensions
            .getByType(
                ApplicationAndroidComponentsExtension::class.java,
            )
            .wireAndroidResources(
                project = this,
                iconSet = iconSet,
                style = style,
                sharedTask = sharedTask,
                generatorClasspath = generatorClasspath,
            )
    }
    plugins.withId("com.android.library") {
        extensions
            .getByType(
                LibraryAndroidComponentsExtension::class.java,
            )
            .wireAndroidResources(
                project = this,
                iconSet = iconSet,
                style = style,
                sharedTask = sharedTask,
                generatorClasspath = generatorClasspath,
            )
    }
}

private fun <VariantT : Variant> AndroidComponentsExtension<*, *, VariantT>
    .wireAndroidResources(
        project: Project,
        iconSet: SymbolIconSet,
        style: SymbolFontStyle,
        sharedTask: TaskProvider<GenerateSymbolFontTask>,
        generatorClasspath: FileCollection,
    ) {
    onVariants(
        selector().all(),
        Action<VariantT> { variant ->
            if (!style.generateAndroidDrawables.get()) {
                return@Action
            }
            val resources = variant.sources.res ?: return@Action
            val fontResource = style.androidFontResourceName.get()
            val task = if (
                fontResource.isNotEmpty() &&
                !style.svgDirectory.isPresent
            ) {
                project.registerVariantAndroidDrawablesTask(
                    iconSet = iconSet,
                    style = style,
                    variantName = variant.name,
                    fontResource = fontResource,
                    resources = resources,
                    generatorClasspath = generatorClasspath,
                )
            } else {
                sharedTask
            }
            resources.addGeneratedSourceDirectory(
                task,
                GenerateSymbolFontTask::androidOutputDirectory,
            )
        },
    )
}

private fun Project.registerVariantAndroidDrawablesTask(
    iconSet: SymbolIconSet,
    style: SymbolFontStyle,
    variantName: String,
    fontResource: String,
    resources: SourceDirectories.Layered,
    generatorClasspath: FileCollection,
): TaskProvider<GenerateSymbolFontTask> =
    tasks.registerOrConfigure<GenerateSymbolFontTask>(
        variantAndroidDrawablesTaskName(
            iconSetName = iconSet.name,
            styleName = style.name,
            variantName = variantName,
        ),
    ) {
        group = "symbol fonts"
        description =
            "Generates ${iconSet.name}.${style.name} Android drawables for " +
                "the $variantName variant."
        configureGenerationInputs(
            iconSet = iconSet,
            style = style,
            generatorClasspath = generatorClasspath,
        )
        font.set(
            resources.static.map { layers ->
                selectAndroidVariantFont(
                    iconSetName = iconSet.name,
                    styleName = style.name,
                    variantName = variantName,
                    fontResource = fontResource,
                    layers = layers,
                )
            },
        )
        conventionalFontName.set("")
        generateImageVectors.set(false)
        generateAndroidDrawables.set(true)
        generateComposeDrawables.set(false)

        val outputRoot = generationOutputRoot(iconSet.name, style.name) +
            "/androidVariants/${fileSegment(variantName)}"
        kotlinOutputDirectory.convention(
            layout.buildDirectory.dir("$outputRoot/kotlin"),
        )
        androidOutputDirectory.convention(
            layout.buildDirectory.dir("$outputRoot/androidRes"),
        )
        composeOutputDirectory.convention(
            layout.buildDirectory.dir("$outputRoot/composeResources"),
        )
    }

internal fun selectAndroidVariantFont(
    iconSetName: String,
    styleName: String,
    variantName: String,
    fontResource: String,
    layers: List<Collection<Directory>>,
): RegularFile {
    val searched = mutableListOf<File>()
    layers.forEach { layer ->
        val candidates = layer.map { directory ->
            directory.file("font/$fontResource")
        }
        searched += candidates.map { candidate -> candidate.asFile }
        val matches = candidates.filter { candidate -> candidate.asFile.isFile }
        if (matches.size == 1) {
            return matches.single()
        }
        if (matches.size > 1) {
            throw InvalidUserDataException(
                "Multiple '$fontResource' font resources have equal priority " +
                    "for $variantName and icon set '$iconSetName' style " +
                    "'$styleName': " +
                    matches.joinToString { match -> match.asFile.absolutePath },
            )
        }
    }
    throw InvalidUserDataException(
        "Cannot find 'font/$fontResource' for $variantName and icon set " +
            "'$iconSetName' style '$styleName'. Searched: " +
            searched.joinToString { file -> file.absolutePath }.ifEmpty { "none" },
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

private fun variantAndroidDrawablesTaskName(
    iconSetName: String,
    styleName: String,
    variantName: String,
): String =
    generationTaskName(iconSetName, styleName) +
        "For${taskSegment(variantName)}AndroidDrawables"

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

private fun Project.warnAboutVariantAwareMixedOutputs(
    extension: SymbolFontsExtension,
) {
    extension.iconSets.forEach { iconSet ->
        iconSet.styles.forEach styleLoop@{ style ->
            val fontResource = style.androidFontResourceName.get()
            if (
                fontResource.isEmpty() ||
                !style.generateAndroidDrawables.get()
            ) {
                return@styleLoop
            }
            val owner = "${iconSet.name}.${style.name}"
            if (
                !plugins.hasPlugin("com.android.application") &&
                !plugins.hasPlugin("com.android.library")
            ) {
                logger.warn(
                    "[symbol-fonts] $owner configures variant-aware Android " +
                        "font resources, but this project has no Android " +
                        "application or library plugin. No variant native " +
                        "drawables will be generated.",
                )
            }
            if (style.svgDirectory.isPresent) {
                logger.warn(
                    "[symbol-fonts] $owner configures " +
                        "androidDrawables(fontResource = \"$fontResource\") " +
                        "with svgDirectory. The font resource is ignored and " +
                        "native Android drawables use the shared SVG source.",
                )
                return@styleLoop
            }

            val sharedOutputs = buildList {
                if (style.generateImageVectors.get()) add("ImageVector")
                if (style.generateComposeDrawables.get()) add("Compose drawable")
            }
            val sharedSource = when {
                style.font.isPresent -> "font.set(...)"
                style.conventionalFontName.get().isNotEmpty() -> "font(...)"
                else -> null
            }
            if (sharedOutputs.isNotEmpty()) {
                logger.warn(
                    "[symbol-fonts] $owner combines " +
                        "androidDrawables(fontResource = \"$fontResource\") " +
                        "with ${sharedOutputs.joinToString()} output. Native " +
                        "Android drawables follow each variant's res/font " +
                        "overlays; shared outputs use " +
                        (sharedSource ?: "the main source-set font") +
                        ". Variant overrides do not change shared outputs. " +
                        "Keep the sources glyph-compatible or split the style.",
                )
            } else if (sharedSource != null) {
                logger.warn(
                    "[symbol-fonts] $owner configures $sharedSource together " +
                        "with androidDrawables(fontResource = " +
                        "\"$fontResource\"). Native Android drawables use the " +
                        "variant resource, so the shared font source is unused.",
                )
            }
        }
    }
}

private fun validateConfiguredIconSets(extension: SymbolFontsExtension) {
    val symbolsEntryPointOwners = linkedMapOf<String, String>()
    val kotlinNamespaceOwners = linkedMapOf<String, String>()
    val kotlinSourceOwners = linkedMapOf<String, String>()
    val androidPrefixOwners = linkedMapOf<String, String>()
    val androidResourceOwners = linkedMapOf<String, String>()

    extension.iconSets.sortedBy(SymbolIconSet::getName).forEach { iconSet ->
        val packageName = iconSet.packageName.get()
        val rootName = iconSet.rootName.get()
        val iconSetOwner = "icon set '${iconSet.name}'"
        SymbolNames.requirePackageName(packageName)
        SymbolNames.requireTypeIdentifier(rootName, "icon-set name")
        val vectorStyles = iconSet.styles
            .sortedBy(SymbolFontStyle::getName)
            .filter { style -> style.generateImageVectors.get() }
        if (vectorStyles.isNotEmpty()) {
            claim(
                kind = "generated Kotlin namespace",
                key = "$packageName.$rootName",
                owner = iconSetOwner,
                owners = kotlinNamespaceOwners,
            )
            claim(
                kind = "generated Symbols entry point",
                key = rootName,
                owner = iconSetOwner,
                owners = symbolsEntryPointOwners,
            )
        }

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
            SymbolNames.requireTypeIdentifier(
                "$rootName${style.name}",
                "generated style namespace",
            )
            if (style.generateImageVectors.get()) {
                val packageSegment = SymbolNames.packageSegment(style.name)
                claim(
                    kind = "generated style package segment",
                    key = packageSegment,
                    owner = styleOwner,
                    owners = stylePackageOwners,
                )
                claim(
                    kind = "generated Kotlin namespace",
                    key = "$packageName.$rootName${style.name}",
                    owner = styleOwner,
                    owners = kotlinNamespaceOwners,
                )
                claim(
                    kind = "generated Kotlin source facade",
                    key = "$packageName.$packageSegment." +
                        "$rootName${style.name}Icons",
                    owner = styleOwner,
                    owners = kotlinSourceOwners,
                )
            }
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
                val codePointSuffix = entry.codePoint
                    ?.let { "_u${it.toString(16).lowercase()}" }
                    .orEmpty()
                val resourceName = "${combinedPrefix}_${entry.semanticName}$codePointSuffix"
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
