package io.github.hlcaptain.symbols.gradle

import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.InvalidUserDataException
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class SymbolFontsPluginFunctionalTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun androidDrawablesOverloadIsAnExplicitModeToggle() {
        val project = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.newFolder())
            .build()
        val extension = project.extensions.create(
            "symbolFonts",
            SymbolFontsExtension::class.java,
        )
        val style = extension.iconSets.create("AppIcons").styles.create("Rounded")

        style.androidDrawables("app_icons.ttf")

        assertTrue(style.generateAndroidDrawables.get())
        assertEquals("app_icons.ttf", style.androidFontResourceName.get())

        style.androidDrawables()

        assertTrue(style.generateAndroidDrawables.get())
        assertTrue(style.androidFontResourceName.get().isEmpty())
        assertFailsWith<IllegalArgumentException> {
            style.androidDrawables("App-icons.ttf")
        }
    }

    @Test
    fun fontAccessorUsesResourceBasedDefaults() {
        val project = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.newFolder())
            .build()
        val extension = project.extensions.create(
            "symbolFonts",
            SymbolFontsExtension::class.java,
        )

        extension.fontAccessor("app_icons")

        val accessor = extension.fontAccessors.getByName("app_icons")
        assertEquals("io.github.hlcaptain.symbols.Symbols", accessor.receiver.get())
        assertEquals("AppIcons", accessor.propertyName.get())
        assertFalse(accessor.packageName.isPresent)
        assertFalse(accessor.componentIndex.isPresent)
        assertTrue(accessor.fixedAxisValues.get().isEmpty())
    }

    @Test
    fun androidVariantFontUsesTheHighestPriorityResourceLayer() {
        val projectDirectory = temporaryFolder.newFolder()
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDirectory)
            .build()
        val main = project.layout.projectDirectory.dir("src/main/res")
        val debug = project.layout.projectDirectory.dir("src/debug/res")
        main.file("font/app_icons.ttf").asFile.apply {
            parentFile.mkdirs()
            writeText("main")
        }
        debug.file("font/app_icons.ttf").asFile.apply {
            parentFile.mkdirs()
            writeText("debug")
        }

        val selected = selectAndroidVariantFont(
            iconSetName = "AppIcons",
            styleName = "Rounded",
            variantName = "debug",
            fontResource = "app_icons.ttf",
            layers = listOf(listOf(debug), listOf(main)),
        )

        assertEquals(debug.file("font/app_icons.ttf").asFile, selected.asFile)
    }

    @Test
    fun androidVariantFontRejectsEqualPriorityDuplicates() {
        val projectDirectory = temporaryFolder.newFolder()
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDirectory)
            .build()
        val first = project.layout.projectDirectory.dir("src/first/res")
        val second = project.layout.projectDirectory.dir("src/second/res")
        listOf(first, second).forEach { directory ->
            directory.file("font/app_icons.ttf").asFile.apply {
                parentFile.mkdirs()
                writeText("font")
            }
        }

        val failure = assertFailsWith<InvalidUserDataException> {
            selectAndroidVariantFont(
                iconSetName = "AppIcons",
                styleName = "Rounded",
                variantName = "debug",
                fontResource = "app_icons.ttf",
                layers = listOf(listOf(first, second)),
            )
        }

        assertTrue("equal priority" in failure.message.orEmpty())
        assertTrue(first.asFile.absolutePath in failure.message.orEmpty())
        assertTrue(second.asFile.absolutePath in failure.message.orEmpty())
    }

    @Test
    fun androidVariantFontReportsEverySearchedResourceRoot() {
        val projectDirectory = temporaryFolder.newFolder()
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDirectory)
            .build()
        val debug = project.layout.projectDirectory.dir("src/debug/res")
        val main = project.layout.projectDirectory.dir("src/main/res")

        val failure = assertFailsWith<InvalidUserDataException> {
            selectAndroidVariantFont(
                iconSetName = "AppIcons",
                styleName = "Rounded",
                variantName = "debug",
                fontResource = "missing.ttf",
                layers = listOf(listOf(debug), listOf(main)),
            )
        }

        assertTrue("font/missing.ttf" in failure.message.orEmpty())
        assertTrue(debug.asFile.absolutePath in failure.message.orEmpty())
        assertTrue(main.asFile.absolutePath in failure.message.orEmpty())
    }

    @Test
    fun generatorRuntimeUsesCatalogSkikoVersion() {
        val expectedVersion = SymbolFontsBuildConfig.SKIKO_VERSION
        val project = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            tasks.register('assertGeneratorRuntimeVersion') {
                doLast {
                    def dependencies = configurations
                        .symbolFontGeneratorRuntimeClasspath
                        .dependencies
                    assert dependencies*.version.toSet() == ['$expectedVersion'] as Set
                    assert dependencies*.name.contains('skiko-awt')
                    assert dependencies*.name.any {
                        it.startsWith('skiko-awt-runtime-')
                    }
                }
            }
            """,
        )

        val result = runner(project, "assertGeneratorRuntimeVersion").build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":assertGeneratorRuntimeVersion")?.outcome,
        )
    }

    @Test
    fun variantAwareAndroidFontResourcesWarnAboutSharedOutputs() {
        val project = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('AppIcons') {
                    style('Rounded') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                        composeDrawables()
                        androidDrawables('app_icons.ttf')
                    }
                }
            }
            """,
        )

        val result = runner(project, "help").build()

        assertTrue(
            "AppIcons.Rounded combines androidDrawables" in result.output,
        )
        assertTrue("variant's res/font overlays" in result.output)
        assertTrue("Variant overrides do not change shared outputs" in result.output)
    }

    @Test
    fun androidAndKmpPluginsWireGeneratedSourcesAndResources() {
        listOf(false, true).forEach { multiplatform ->
            val plugins = if (multiplatform) {
                "id 'org.jetbrains.kotlin.multiplatform'\n" +
                    "id 'com.android.kotlin.multiplatform.library'"
            } else {
                "id 'com.android.library'"
            }
            val android = "namespace 'com.example.icons'; compileSdk 36; " +
                if (multiplatform) "minSdk 23" else "defaultConfig { minSdk 23 }"
            val project = fixture(
                """
                import io.github.hlcaptain.symbols.gradle.GenerateSymbolFontTask

                plugins {
                    $plugins
                    id 'io.github.hlcaptain.symbol-fonts'
                }
                ${if (multiplatform) "kotlin { android { $android } }" else "android { $android }"}
                symbolFonts {
                    iconSet('AppIcons') {
                        style('Outlined') {
                            svgDirectory.set(file('icons'))
                            imageVectors()
                            androidDrawables()
                        }
                    }
                }
                configurations.named('symbolFontGeneratorRuntimeClasspath') {
                    dependencies.clear()
                }
                tasks.withType(GenerateSymbolFontTask).configureEach {
                    generatorClasspath.setFrom(files(file('generator-classpath.txt').readLines()))
                }
                androidComponents.onVariants(androidComponents.selector().all()) { variant ->
                    tasks.register('assertGeneratedSources' + variant.name.capitalize()) {
                        def res = files(variant.sources.res.all)
                        def sources = ${if (multiplatform) "kotlin.sourceSets.commonMain.kotlin" else "files(variant.sources.kotlin.all)"}
                        dependsOn(res, sources)
                        doLast {
                            assert res.asFileTree.files.any { it.name == 'app_icons_outlined_check.xml' }
                            assert sources.asFileTree.files.any { it.name.endsWith('.kt') }
                            assert tasks.named('generateAppIconsOutlinedSymbolFonts')
                                .get().packageName.get() == 'com.example.icons.generated'
                        }
                    }
                }
                tasks.register('assertAllGeneratedAndroidSources') {
                    dependsOn(tasks.matching { it.name.startsWith('assertGeneratedSources') })
                }
                """,
                files = mapOf(
                    "icons/check.svg" to tablerSvg("M5 12l4 4L19 6"),
                    "generator-classpath.txt" to generatorTestClasspath(),
                ),
            )
            configureAndroidSdk(project)

            val result = androidRunner(project, "assertAllGeneratedAndroidSources").build()

            assertEquals(
                TaskOutcome.SUCCESS,
                result.task(":generateAppIconsOutlinedSymbolFonts")?.outcome,
            )
            assertEquals(
                if (multiplatform) 1 else 2,
                result.tasks.count { it.path.startsWith(":assertGeneratedSources") },
            )
        }
    }

    @Test
    fun androidVariantsUseMainFontThenObserveResourceOverlays() {
        val project = fixture(
            """
            plugins {
                id 'com.android.library'
                id 'io.github.hlcaptain.symbol-fonts'
            }

            android {
                namespace 'com.example.icons'
                compileSdk 36
            }

            symbolFonts {
                iconSet('AppIcons') {
                    style('Rounded') {
                        codepoints.set(file('icons.codepoints'))
                        androidDrawables('app_icons.ttf')
                    }
                }
            }

            tasks.register('assertAndroidVariantFontTasks') {
                doLast {
                    def shared = tasks.named(
                        'generateAppIconsRoundedSymbolFonts'
                    ).get()
                    def debug = tasks.named(
                        'generateAppIconsRoundedSymbolFontsForDebugAndroidDrawables'
                    ).get()
                    def release = tasks.named(
                        'generateAppIconsRoundedSymbolFontsForReleaseAndroidDrawables'
                    ).get()
                    def mainFont = file('src/main/res/font/app_icons.ttf')
                    def debugFont = file('src/debug/res/font/app_icons.ttf')
                    assert shared.packageName.get() == 'com.example.icons.generated'
                    assert !shared.generateAndroidDrawables.get()
                    assert shared.generatesAndroidDrawablesByVariant.get()
                    assert debug.font.get().asFile ==
                        (debugFont.isFile() ? debugFont : mainFont)
                    assert release.font.get().asFile == mainFont
                    assert debug.generateAndroidDrawables.get()
                    assert !debug.generateImageVectors.get()
                    assert !debug.generateComposeDrawables.get()
                    println('DEBUG_FONT=' + debug.font.get().asFile.name + ':' +
                        debug.font.get().asFile.text)
                }
            }
            """,
            files = mapOf(
                "src/main/res/font/app_icons.ttf" to "main",
            ),
        )
        configureAndroidSdk(project)

        val first = androidRunner(
            project,
            "assertAndroidVariantFontTasks",
        ).build()
        assertTrue("DEBUG_FONT=app_icons.ttf:main" in first.output)

        project.resolve("src/debug/res/font/app_icons.ttf").apply {
            parentFile.mkdirs()
            writeText("debug")
        }
        val second = androidRunner(
            project,
            "assertAndroidVariantFontTasks",
        ).build()

        assertTrue("DEBUG_FONT=app_icons.ttf:debug" in second.output)
    }

    @Test
    fun androidVariantTaskInvalidatesConfigurationCacheForNewOverlay() {
        val project = fixture(
            """
            import io.github.hlcaptain.symbols.gradle.GenerateSymbolFontTask

            plugins {
                id 'com.android.library'
                id 'io.github.hlcaptain.symbol-fonts'
            }

            android {
                namespace 'com.example.icons'
                compileSdk 36
            }

            symbolFonts {
                iconSet('AppIcons') {
                    style('Rounded') {
                        codepoints.set(file('icons.codepoints'))
                        androidDrawables('app_icons.otf')
                    }
                }
            }

            configurations.named('symbolFontGeneratorRuntimeClasspath') {
                dependencies.clear()
            }
            tasks.withType(GenerateSymbolFontTask).configureEach {
                generatorClasspath.setFrom(
                    files(file('generator-classpath.txt').readLines())
                )
            }
            """,
            files = mapOf(
                "generator-classpath.txt" to generatorTestClasspath(),
                "icons.codepoints" to "branch e0a0\nfull_block 2588\n",
            ),
        )
        configureAndroidSdk(project)
        val mainFont = project.resolve("src/main/res/font/app_icons.otf")
        mainFont.parentFile.mkdirs()
        File(requireNotNull(System.getProperty("symbols.powerlineTestFont")))
            .copyTo(mainFont)
        val taskName =
            "generateAppIconsRoundedSymbolFontsForDebugAndroidDrawables"

        val first = androidCachedRunner(project, taskName).build()

        assertEquals(TaskOutcome.SUCCESS, first.task(":$taskName")?.outcome)
        assertTrue(
            project.resolve(
                "build/generated/res/$taskName/drawable/" +
                    "app_icons_rounded_branch_ue0a0.xml",
            ).isFile,
        )

        val debugFont = project.resolve("src/debug/res/font/app_icons.otf")
        debugFont.parentFile.mkdirs()
        File(
            requireNotNull(
                System.getProperty("symbols.academmuniconsTestFont"),
            ),
        ).copyTo(debugFont)

        val second = androidCachedRunner(project, taskName).buildAndFail()
        val invalidationReason =
            "configuration cache cannot be reused because the file system " +
                "entry 'src/debug/res/font/app_icons.otf' has been created"

        assertTrue(
            actual = invalidationReason in second.output,
            message = second.output,
        )
        assertEquals(TaskOutcome.FAILED, second.task(":$taskName")?.outcome)
        assertTrue(debugFont.absolutePath in second.output)
    }

    @Test
    fun configuresACompatiblePreRegisteredTask() {
        val project = fixture(
            """
            import io.github.hlcaptain.symbols.gradle.GenerateSymbolCatalogsTask

            plugins {
                id 'io.github.hlcaptain.symbol-fonts' apply false
            }

            tasks.register(
                'generateSymbolCatalogs',
                GenerateSymbolCatalogsTask
            )
            apply plugin: 'io.github.hlcaptain.symbol-fonts'

            tasks.register('assertPreRegisteredTask') {
                doLast {
                    def catalogs = tasks.named(
                        'generateSymbolCatalogs',
                        GenerateSymbolCatalogsTask
                    ).get()
                    assert catalogs.description ==
                        'Generates runtime symbol catalogs from codepoint manifests.'
                    assert catalogs.outputDirectory.get().asFile == file(
                        'build/generated/symbolFonts/catalogs/kotlin'
                    )
                }
            }
            """,
        )

        val result = runner(project, "assertPreRegisteredTask").build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":assertPreRegisteredTask")?.outcome,
        )
    }

    @Test
    fun rejectsAnIncompatiblePreRegisteredTask() {
        val project = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts' apply false
            }

            tasks.register('generateSymbolCatalogs')
            apply plugin: 'io.github.hlcaptain.symbol-fonts'
            """,
        )

        val failure = runner(project, "help").buildAndFail()

        assertTrue("generateSymbolCatalogs" in failure.output)
        assertTrue("not a subclass of the given type" in failure.output)
    }

    @Test
    fun generatesNamedRuntimeCatalogsIncrementally() {
        val project = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                catalogPackageName.set('com.example.catalogs')
                catalog('SecondCatalog') {
                    codepoints.set(file('second.codepoints'))
                }
                catalog('FirstCatalog') {
                    codepoints.set(file('first.codepoints'))
                }
            }
            """,
            files = mapOf(
                "first.codepoints" to "beta e002\nalpha e001\n",
                "second.codepoints" to "gamma 1f600\n",
            ),
        )

        val first = buildCachedRunner(project, "generateSymbolCatalogs").build()
        assertEquals(
            TaskOutcome.SUCCESS,
            first.task(":generateSymbolCatalogs")?.outcome,
        )
        val generated = project.resolve(
            "build/generated/symbolFonts/catalogs/kotlin/" +
                "com/example/catalogs/SymbolCatalogs.generated.kt",
        ).readText()
        assertTrue("internal data class SymbolCatalogEntry" in generated)
        assertTrue("internal val FirstCatalog" in generated)
        assertTrue("internal val SecondCatalog" in generated)
        assertTrue(generated.indexOf("FirstCatalog") < generated.indexOf("SecondCatalog"))
        assertTrue(generated.indexOf("\"alpha\"") < generated.indexOf("\"beta\""))
        assertTrue("SymbolCatalogEntry(\"gamma\", 0x1F600)" in generated)

        val second = buildCachedRunner(project, "generateSymbolCatalogs").build()
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            second.task(":generateSymbolCatalogs")?.outcome,
        )
        assertTrue("Reusing configuration cache." in second.output)

        check(project.resolve("build").deleteRecursively())
        val restored = buildCachedRunner(project, "generateSymbolCatalogs").build()
        assertEquals(
            TaskOutcome.FROM_CACHE,
            restored.task(":generateSymbolCatalogs")?.outcome,
        )
    }

    @Test
    fun composeMergeIncludesConfiguredFontResources() {
        val project = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                composeFontResources.from(file('sampleResources'))
            }
            """,
            files = mapOf(
                "sampleResources/font/sample.ttf" to "font",
            ),
        )

        val result = runner(project, "mergeGeneratedSymbolComposeResources").build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":mergeGeneratedSymbolComposeResources")?.outcome,
        )
        val output = project.resolve("build/generated/symbolFonts/composeResources")
        assertEquals("font", output.resolve("font/sample.ttf").readText())
    }

    @Test
    fun composeMergeIncludesConventionalAndConfiguredResources() {
        val project = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                composeFontResources.from(file('externalResources'))
            }
            """,
            files = mapOf(
                "src/commonMain/composeResources/font/local.ttf" to "local",
                "externalResources/font/external.ttf" to "external",
            ),
        )

        val result = runner(project, "mergeGeneratedSymbolComposeResources").build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":mergeGeneratedSymbolComposeResources")?.outcome,
        )
        val output = project.resolve("build/generated/symbolFonts/composeResources/font")
        assertEquals("local", output.resolve("local.ttf").readText())
        assertEquals("external", output.resolve("external.ttf").readText())
    }

    @Test
    fun composeMergeIncludesGeneralTaskBackedResources() {
        val project = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            def prepared = tasks.register('prepareComposeResources') {
                outputs.dir(layout.buildDirectory.dir('preparedResources'))
            }

            symbolFonts {
                composeResourceRoots.from(
                    prepared.map { it.outputs.files.singleFile }
                )
            }

            tasks.register('assertComposeResourceMerge') {
                doLast {
                    def merge = tasks.named(
                        'mergeGeneratedSymbolComposeResources'
                    ).get()
                    assert merge.inputDirectories.buildDependencies
                        .getDependencies(merge)*.name ==
                        ['prepareComposeResources']
                    def descriptors = tasks.named(
                        'generateSymbolFontDescriptors'
                    ).get()
                    assert descriptors.resourceRoots.isEmpty()
                }
            }
            """,
        )

        val result = runner(project, "assertComposeResourceMerge").build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":assertComposeResourceMerge")?.outcome,
        )
    }

    @Test
    fun composeMergeDependsOnlyOnComposeDrawableStyles() {
        val project = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('AppIcons') {
                    packageName.set('com.example.icons')
                    style('KotlinOnly') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                    style('Compose') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        composeDrawables()
                    }
                }
            }

            tasks.register('assertComposeStyleDependencies') {
                doLast {
                    def merge = tasks.named(
                        'mergeGeneratedSymbolComposeResources'
                    ).get()
                    assert merge.inputDirectories.buildDependencies
                        .getDependencies(merge)*.name ==
                        ['generateAppIconsComposeSymbolFonts']
                }
            }
            """,
        )

        val result = runner(project, "assertComposeStyleDependencies").build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":assertComposeStyleDependencies")?.outcome,
        )
    }

    @Test
    fun composePluginKeepsExistingCustomDirectoryWhenMergeIsUnused() {
        val project = fixture(
            """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'org.jetbrains.kotlin.plugin.compose'
                id 'org.jetbrains.compose'
                id 'io.github.hlcaptain.symbol-fonts'
            }

            kotlin {
                jvm()
            }

            compose.resources {
                customDirectory(
                    'commonMain',
                    layout.dir(providers.provider {
                        file('existingResources')
                    })
                )
            }
            """,
            files = mapOf(
                "existingResources/drawable/existing.xml" to "<vector />\n",
            ),
        )

        val result = composeRunner(
            project,
            "copyNonXmlValueResourcesForCommonMain",
        ).build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":copyNonXmlValueResourcesForCommonMain")?.outcome,
        )
        assertEquals(null, result.task(":mergeGeneratedSymbolComposeResources"))
        assertTrue(
            project.resolve(
                "build/generated/compose/resourceGenerator/preparedResources/" +
                    "commonMain/composeResources/drawable/existing.xml",
            ).isFile,
        )
    }

    @Test
    fun composeResourceRootsAggregateExistingAndConventionalResources() {
        val project = fixture(
            """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'org.jetbrains.kotlin.plugin.compose'
                id 'org.jetbrains.compose'
                id 'io.github.hlcaptain.symbol-fonts'
            }

            kotlin {
                jvm()
            }

            def existing = tasks.register('prepareExistingResources') {
                def output = layout.buildDirectory.dir('existingResources')
                outputs.dir(output)
                doLast {
                    def drawable = output.get().file(
                        'drawable/existing.xml'
                    ).asFile
                    drawable.parentFile.mkdirs()
                    drawable.text = '<vector />\n'
                }
            }
            def existingDirectory = existing.map {
                layout.buildDirectory.dir('existingResources').get()
            }

            compose.resources {
                customDirectory('commonMain', existingDirectory)
            }
            symbolFonts {
                composeResourceRoots.from(existingDirectory)
            }
            """,
            files = mapOf(
                "src/commonMain/composeResources/drawable/conventional.xml" to
                    "<vector />\n",
            ),
        )

        val result = composeCachedRunner(
            project,
            "copyNonXmlValueResourcesForCommonMain",
        ).build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":prepareExistingResources")?.outcome,
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":mergeGeneratedSymbolComposeResources")?.outcome,
        )
        val prepared = project.resolve(
            "build/generated/compose/resourceGenerator/preparedResources/" +
                "commonMain/composeResources/drawable",
        )
        assertTrue(prepared.resolve("existing.xml").isFile)
        assertTrue(prepared.resolve("conventional.xml").isFile)

        val second = composeCachedRunner(
            project,
            "copyNonXmlValueResourcesForCommonMain",
        ).build()
        assertTrue("Reusing configuration cache." in second.output)
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            second.task(":mergeGeneratedSymbolComposeResources")?.outcome,
        )
    }

    @Test
    fun descriptorTaskUsesOptInTaskBackedRoots() {
        val project = fixture(
            """
            import io.github.hlcaptain.symbols.gradle.GenerateSymbolFontDescriptorsTask

            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            def prepared = tasks.register('prepareFontResources') {
                outputs.dir(layout.buildDirectory.dir('preparedResources'))
            }

            symbolFonts {
                composeFontResources.from(
                    prepared.map { it.outputs.files.singleFile }
                )
            }

            tasks.register('assertFontDescriptorConfiguration') {
                doLast {
                    def descriptors = tasks.named(
                        'generateSymbolFontDescriptors',
                        GenerateSymbolFontDescriptorsTask
                    ).get()
                    assert descriptors.resourceRoots.singleFile ==
                        layout.buildDirectory.dir('preparedResources').get().asFile
                    assert descriptors.resourceRoots.buildDependencies
                        .getDependencies(descriptors)*.name ==
                        ['prepareFontResources']
                    assert descriptors.resourceClassName.get() == 'Res'
                    assert !descriptors.publicAccessors.get()
                    assert descriptors.outputDirectory.get().asFile == file(
                        'build/generated/symbolFonts/fontDescriptors/kotlin'
                    )
                    def merge = tasks.named(
                        'mergeGeneratedSymbolComposeResources'
                    ).get()
                    assert merge.inputDirectories.buildDependencies
                        .getDependencies(merge)*.name ==
                        ['prepareFontResources']
                }
            }
            """,
        )

        val result = runner(project, "assertFontDescriptorConfiguration").build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":assertFontDescriptorConfiguration")?.outcome,
        )
    }

    @Test
    fun descriptorTaskCalculatesDefaultResourcePackageLazily() {
        val project = fixture(
            """
            import io.github.hlcaptain.symbols.gradle.GenerateSymbolFontDescriptorsTask

            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            group = 'Com.Example-Group'

            tasks.register('assertFontDescriptorDefaults') {
                doLast {
                    def descriptors = tasks.named(
                        'generateSymbolFontDescriptors',
                        GenerateSymbolFontDescriptorsTask
                    ).get()
                    assert descriptors.resourceRoots.isEmpty()
                    assert descriptors.resourcePackage.get() ==
                        'com.example_group.symbol_fonts_plugin_test.generated.resources'
                    assert descriptors.resourceClassName.get() == 'Res'
                    assert !descriptors.publicAccessors.get()
                }
            }
            """,
        )

        val result = runner(project, "assertFontDescriptorDefaults").build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":assertFontDescriptorDefaults")?.outcome,
        )
    }

    @Test
    fun descriptorTaskGeneratesConfiguredPublicFontAccessor() {
        val project = fixture(
            """
            import io.github.hlcaptain.symbols.gradle.GenerateSymbolFontDescriptorsTask

            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'org.jetbrains.kotlin.plugin.compose'
                id 'org.jetbrains.compose'
                id 'io.github.hlcaptain.symbol-fonts'
            }

            kotlin {
                jvm()
            }

            compose.resources {
                packageOfResClass = 'com.example.resources'
                nameOfResClass = 'SymbolFont'
            }

            symbolFonts {
                composeFontResources.from(file('resources'))
                fontAccessor('academic_icons') {
                    receiver.set('com.example.Icons.Rounded')
                    propertyName.set('staticFont')
                    packageName.set('com.example.api')
                    componentIndex.set(2)
                    fixedAxisValues.put('wght', 400f)
                }
            }

            configurations.named('symbolFontGeneratorRuntimeClasspath') {
                dependencies.clear()
            }
            tasks.withType(GenerateSymbolFontDescriptorsTask).configureEach {
                generatorClasspath.setFrom(
                    files(file('generator-classpath.txt').readLines())
                )
            }
            """,
            files = mapOf(
                "generator-classpath.txt" to generatorTestClasspath(),
            ),
        )
        val font = project.resolve("resources/font/academic_icons.ttf")
        font.parentFile.mkdirs()
        File(requireNotNull(System.getProperty("symbols.academmuniconsTestFont")))
            .copyTo(font)
        font.copyTo(project.resolve("resources/font/other_icons.ttf"))

        val result = composeRunner(project, "generateSymbolFontDescriptors").build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":generateSymbolFontDescriptors")?.outcome,
        )
        val root = project.resolve("build/generated/symbolFonts/fontDescriptors/kotlin")
        val descriptor = root.resolve(
            "com/example/resources/SymbolFonts.generated.kt",
        ).readText()
        assertTrue("internal object SymbolFonts" in descriptor)
        assertTrue(
            "import com.example.resources.SymbolFont as _SymbolsResourceClass" in descriptor,
        )
        assertTrue("resource = _SymbolsResourceClass.font.academic_icons" in descriptor)
        assertTrue("FontVariation.Setting(\"wght\", 400.0f)" in descriptor)
        val accessor = root.resolve(
            "com/example/api/FontAccessor_academic_icons.generated.kt",
        ).readText()
        assertTrue(
            "import com.example.resources.SymbolFont as _SymbolsResourceClass" in accessor,
        )
        assertTrue("val com.example.Icons.Rounded.staticFont" in accessor)
        assertTrue(
            "get() = _SymbolsResourceClass._symbolsFontDescriptors.academic_icons" in accessor,
        )
        assertTrue("component2()" in accessor)
        assertFalse(Regex("\\bpublic\\b").containsMatchIn(accessor))
        val automaticAccessor = root.resolve(
            "com/example/resources/FontAccessor_other_icons.generated.kt",
        ).readText()
        assertTrue(
            "val io.github.hlcaptain.symbols.Symbols.OtherIcons: SymbolFont.Regular" in
                automaticAccessor,
        )
        assertTrue(
            "get() = _SymbolsResourceClass._symbolsFontDescriptors.other_icons" in
                automaticAccessor,
        )
        assertFalse(
            root.resolve(
                "com/example/resources/FontAccessor_academic_icons.generated.kt",
            ).exists(),
        )
    }

    @Test
    fun happyNamespaceConfigurationIsDeterministicAndIncremental() {
        val project = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('AppIcons') {
                    packageName.set('com.example.icons')

                    style('Rounded') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                    style('Regular') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                }
            }
            """,
        )

        val first = runner(project, "generateAppIconsSymbolFontNamespace").build()
        assertEquals(
            TaskOutcome.SUCCESS,
            first.task(":generateAppIconsSymbolFontNamespace")?.outcome,
        )
        val namespace = project.resolve(
            "build/generated/symbolFonts/app_icons/namespace/kotlin/" +
                "com/example/icons/AppIcons.generated.kt",
        )
        val contents = namespace.readText()
        assertTrue("object AppIcons" in contents)
        assertTrue("val Symbols.AppIcons: AppIcons" in contents)
        assertTrue("get() = _AppIconsEntryPoint" in contents)
        assertTrue(
            contents.indexOf("object AppIconsRegular") <
                contents.indexOf("object AppIconsRounded"),
        )
        assertFalse(Regex("\\bpublic\\b").containsMatchIn(contents))

        val second = runner(project, "generateAppIconsSymbolFontNamespace").build()
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            second.task(":generateAppIconsSymbolFontNamespace")?.outcome,
        )
    }

    @Test
    fun resourceOnlyStyleDoesNotGenerateKotlinNamespace() {
        val project = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('ResourceIcons') {
                    packageName.set('com.example.resources')
                    style('Regular') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        composeDrawables()
                    }
                }
            }
            """,
        )

        val taskName = "generateResourceIconsSymbolFontNamespace"
        val result = runner(project, taskName).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":$taskName")?.outcome)
        assertTrue(
            project.resolve("build/generated/symbolFonts/resource_icons/namespace/kotlin")
                .walkTopDown()
                .none { file -> file.extension == "kt" },
        )
    }

    @Test
    fun svgDirectoryGeneratesEveryOutputUnderBuild() {
        val project = fixture(
            """
            import io.github.hlcaptain.symbols.gradle.GenerateSymbolFontTask

            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            repositories {
                mavenCentral()
            }

            symbolFonts {
                iconSet('TablerIcons') {
                    packageName.set('com.example.icons')
                    style('Outline') {
                        svgDirectory.set(file('svg'))
                        imageVectors()
                        androidDrawables()
                        composeDrawables()
                    }
                }
            }

            tasks.named(
                'generateTablerIconsOutlineSymbolFonts',
                GenerateSymbolFontTask
            ) {
                generatorClasspath.setFrom(
                    files(file('generator-classpath.txt').readLines())
                )
            }
            """,
            files = mapOf(
                "svg/arrow-left.svg" to tablerSvg("M19 12h-14m6 6l-6 -6l6 -6"),
                "svg/badge-check.svg" to tablerSvg("M7 12l3 3l7 -7"),
                "generator-classpath.txt" to generatorTestClasspath(),
            ),
        )

        val taskName = "generateTablerIconsOutlineSymbolFonts"
        val first = runner(project, taskName).build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":$taskName")?.outcome)

        val outputRoot = project.resolve(
            "build/generated/symbolFonts/tabler_icons/outline",
        )
        val kotlinSource = outputRoot.resolve(
            "kotlin/com/example/icons/outline/" +
                "TablerIconsOutlineIcons000.generated.kt",
        )
        assertTrue(kotlinSource.isFile)
        val kotlinContents = kotlinSource.readText()
        assertTrue("TablerIcons.Outline.ArrowLeft" in kotlinContents)
        assertTrue("TablerIcons.Outline.BadgeCheck" in kotlinContents)
        assertTrue("U+" !in kotlinContents)

        val expectedResources = setOf(
            "tabler_icons_outline_arrow_left.xml",
            "tabler_icons_outline_badge_check.xml",
        )
        val androidDrawables = outputRoot.resolve("androidRes/drawable")
        val composeDrawables = outputRoot.resolve("composeResources/drawable")
        assertEquals(
            expectedResources,
            androidDrawables.listFiles().orEmpty().map(File::getName).toSet(),
        )
        assertEquals(
            expectedResources,
            composeDrawables.listFiles().orEmpty().map(File::getName).toSet(),
        )
        expectedResources.forEach { resourceName ->
            assertEquals(
                androidDrawables.resolve(resourceName).readText(),
                composeDrawables.resolve(resourceName).readText(),
            )
        }
        assertTrue(outputRoot.toPath().startsWith(project.resolve("build").toPath()))

        val second = runner(project, taskName).build()
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":$taskName")?.outcome)
    }

    @Test
    fun defaultsPackageSourcesAndConventionalFonts() {
        val project = fixture(
            """
            import io.github.hlcaptain.symbols.gradle.GenerateSymbolFontTask

            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            group = 'com.example'

            symbolFonts {
                iconSet('AppIcons') {
                    style('Rounded') {
                        codepoints.set(file('rounded.codepoints'))
                        imageVectors()
                    }
                    style('Sharp') {
                        codepoints.set(file('sharp.codepoints'))
                        font('material-rounded.ttf')
                        imageVectors()
                    }
                }
                iconSet('SvgIcons') {
                    style('Regular') {
                        svgDirectory.set(file('svg'))
                        imageVectors()
                    }
                }
            }

            tasks.register('assertSymbolDefaults') {
                doLast {
                    def rounded = tasks.named(
                        'generateAppIconsRoundedSymbolFonts',
                        GenerateSymbolFontTask
                    ).get()
                    assert rounded.packageName.get() ==
                        'com.example.symbol_fonts_plugin_test.generated'
                    assert rounded.manifest.get().asFile == file('rounded.codepoints')
                    assert !rounded.svgDirectory.isPresent()
                    assert !rounded.font.isPresent()
                    assert rounded.conventionalFontName.get().isEmpty()
                    assert rounded.conventionalFonts.singleFile ==
                        file('src/commonMain/composeResources/font/material-rounded.ttf')

                    def sharp = tasks.named(
                        'generateAppIconsSharpSymbolFonts',
                        GenerateSymbolFontTask
                    ).get()
                    assert sharp.manifest.get().asFile == file('sharp.codepoints')
                    assert !sharp.svgDirectory.isPresent()
                    assert !sharp.font.isPresent()
                    assert sharp.conventionalFontName.get() == 'material-rounded.ttf'
                    assert sharp.conventionalFonts.singleFile ==
                        file('src/commonMain/composeResources/font/material-rounded.ttf')

                    def svg = tasks.named(
                        'generateSvgIconsRegularSymbolFonts',
                        GenerateSymbolFontTask
                    ).get()
                    assert !svg.manifest.isPresent()
                    assert svg.svgDirectory.get().asFile == file('svg')
                    assert !svg.font.isPresent()
                    assert svg.conventionalFonts.isEmpty()
                }
            }
            """,
            files = mapOf(
                "rounded.codepoints" to "home e001\n",
                "sharp.codepoints" to "home e101\n",
                "src/commonMain/composeResources/font/material-rounded.ttf" to
                    "rounded",
                "svg/home.svg" to tablerSvg("M5 12h14"),
            ),
        )

        val result = runner(project, "assertSymbolDefaults").build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":assertSymbolDefaults")?.outcome,
        )
    }

    @Test
    fun conventionalFontDiscoverySurvivesConfigurationCacheReuse() {
        val project = fixture(
            """
            import io.github.hlcaptain.symbols.gradle.GenerateSymbolFontTask
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.ConfigurableFileCollection
            import org.gradle.api.tasks.InputFiles
            import org.gradle.api.tasks.TaskAction

            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('AppIcons') {
                    style('Rounded') {
                        codepoints.set(file('icons.codepoints'))
                        imageVectors()
                    }
                }
            }

            abstract class AssertOneConventionalFont extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getFonts()

                @TaskAction
                void verify() {
                    def count = fonts.files.size()
                    if (count != 1) {
                        throw new GradleException(
                            'Expected one conventional font; found ' + count + '.'
                        )
                    }
                }
            }

            def generation = tasks.named(
                'generateAppIconsRoundedSymbolFonts',
                GenerateSymbolFontTask
            ).get()
            tasks.register(
                'assertOneConventionalFont',
                AssertOneConventionalFont
            ) {
                fonts.from(generation.conventionalFonts)
            }
            """,
            files = mapOf(
                "src/androidMain/res/font/app-icons.otf" to "icons",
            ),
        )

        val taskName = "assertOneConventionalFont"
        val first = cachedRunner(project, taskName).build()
        assertEquals(
            TaskOutcome.SUCCESS,
            first.task(":$taskName")?.outcome,
        )

        project.resolve(
            "src/commonMain/composeResources/font/other-icons.otf",
        ).also { file ->
            file.parentFile.mkdirs()
            file.writeText("other")
        }

        val second = cachedRunner(project, taskName).buildAndFail()
        assertTrue("Reusing configuration cache." in second.output)
        assertTrue(
            "Expected one conventional font; found 2." in second.output,
        )
    }

    @Test
    fun conventionalFontSelectionRequiresOneMatch() {
        val directory = temporaryFolder.newFolder()
        val first = directory.resolve("first.ttf").apply { writeText("first") }
        val second = directory.resolve("second.otf").apply { writeText("second") }

        assertEquals(
            second,
            selectConventionalFont(
                "Rounded",
                "second.otf",
                listOf(first, second),
            ),
        )
        val failure = assertFailsWith<InvalidUserDataException> {
            selectConventionalFont("Rounded", null, listOf(first, second))
        }
        assertTrue(
            "Cannot choose a font for style 'Rounded'" in
                failure.message.orEmpty(),
        )
    }

    @Test
    fun transformDefaultsFollowViewportAndCustomValuesReachGenerationTasks() {
        val project = fixture(
            """
            import io.github.hlcaptain.symbols.gradle.GenerateSymbolFontTask

            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('AppIcons') {
                    packageName.set('com.example.icons')

                    style('Defaults') {
                        codepoints.set(file('icons.codepoints'))
                        viewportWidth.set(30f)
                        viewportHeight.set(18f)
                    }
                    style('Custom') {
                        codepoints.set(file('icons.codepoints'))
                        emSize.set(15.5f)
                        originX.set(2.25f)
                        baselineY.set(16.75f)
                    }
                }
            }

            tasks.register('assertSymbolTransforms') {
                doLast {
                    def defaults = tasks.named(
                        'generateAppIconsDefaultsSymbolFonts',
                        GenerateSymbolFontTask
                    ).get()
                    assert defaults.emSize.get() == 18f
                    assert defaults.originX.get() == 0f
                    assert defaults.baselineY.get() == 18f

                    def custom = tasks.named(
                        'generateAppIconsCustomSymbolFonts',
                        GenerateSymbolFontTask
                    ).get()
                    assert custom.emSize.get() == 15.5f
                    assert custom.originX.get() == 2.25f
                    assert custom.baselineY.get() == 16.75f
                }
            }
            """,
        )

        val result = runner(project, "assertSymbolTransforms").build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":assertSymbolTransforms")?.outcome,
        )
    }

    @Test
    fun rejectsAmbiguousGenerationTaskNamesBeforeRegistration() {
        val project = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('AB') {
                    style('C') {}
                }
                iconSet('A') {
                    style('BC') {}
                }
            }
            """,
        )

        val failure = runner(project, "help").buildAndFail()

        assertTrue("Gradle task name collision 'generateABCSymbolFonts'" in failure.output)
        assertTrue("icon set 'AB' style 'C'" in failure.output)
        assertTrue("icon set 'A' style 'BC'" in failure.output)
    }

    @Test
    fun rejectsMissingAndMixedStyleSources() {
        val missing = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('AppIcons') {
                    packageName.set('com.example.icons')
                    style('Outline') {
                        imageVectors()
                    }
                }
            }
            """,
        )
        val missingFailure = runner(missing, "help").buildAndFail()
        assertTrue(
            "must configure svgDirectory or codepoints" in missingFailure.output,
        )

        val mixed = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('AppIcons') {
                    packageName.set('com.example.icons')
                    style('Outline') {
                        svgDirectory.set(file('svg'))
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                }
            }
            """,
            files = mapOf("svg/home.svg" to tablerSvg("M5 12h14")),
        )
        val mixedFailure = runner(mixed, "help").buildAndFail()
        assertTrue(
            "svgDirectory together with font-only inputs: codepoints, font" in
                mixedFailure.output,
        )
    }

    @Test
    fun rejectsNormalizedOutputDirectoryCollisionsBeforeRegistration() {
        val project = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('URLIcons') {}
                iconSet('Urlicons') {}
            }
            """,
        )

        val failure = runner(project, "help").buildAndFail()

        assertTrue("generated output directory collision" in failure.output)
        assertTrue("generated/symbolFonts/urlicons/namespace/kotlin" in failure.output)
        assertTrue("icon set 'URLIcons' namespace" in failure.output)
        assertTrue("icon set 'Urlicons' namespace" in failure.output)
    }

    @Test
    fun rejectsLazyKotlinRootAndStylePackageCollisions() {
        val rootCollision = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('One') {
                    packageName.set('com.example.icons')
                    rootName.set('Shared')
                    style('Regular') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                }
                iconSet('Two') {
                    packageName.set('com.example.icons')
                    rootName.set('Shared')
                    style('Regular') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                }
            }
            """,
        )
        val rootFailure = runner(rootCollision, "help").buildAndFail()
        assertTrue(
            "generated Kotlin namespace collision 'com.example.icons.Shared'" in
                rootFailure.output,
        )

        val entryPointCollision = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('One') {
                    packageName.set('com.example.one')
                    rootName.set('Shared')
                    style('Regular') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                }
                iconSet('Two') {
                    packageName.set('com.example.two')
                    rootName.set('Shared')
                    style('Regular') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                }
            }
            """,
        )
        val entryPointFailure = runner(entryPointCollision, "help").buildAndFail()
        assertTrue(
            "generated Symbols entry point collision 'Shared'" in
                entryPointFailure.output,
        )

        val keywordCollision = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('Keyword') {
                    rootName.set('wh')
                    style('en') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                }
            }
            """,
        )
        val keywordFailure = runner(keywordCollision, "help").buildAndFail()
        assertTrue("Invalid generated style namespace: when" in keywordFailure.output)

        val namespaceCollision = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('A') {
                    packageName.set('com.example.icons')
                    style('B') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                }
                iconSet('AB') {
                    packageName.set('com.example.icons')
                    style('Regular') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                }
            }
            """,
        )
        val namespaceFailure = runner(namespaceCollision, "help").buildAndFail()
        assertTrue(
            "generated Kotlin namespace collision 'com.example.icons.AB'" in
                namespaceFailure.output,
        )

        val packageCollision = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('AppIcons') {
                    packageName.set('com.example.icons')
                    style('A1B') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                    style('A1b') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                }
            }
            """,
        )
        val packageFailure = runner(packageCollision, "help").buildAndFail()
        assertTrue(
            "generated style package segment collision 'a1b'" in
                packageFailure.output,
        )
    }

    @Test
    fun rejectsAndroidPrefixAndFinalResourceOwnerCollisions() {
        val prefixCollision = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('AppIcons') {
                    packageName.set('com.example.icons')
                    style('a1a') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        composeDrawables()
                    }
                    style('a1A_') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
                        composeDrawables()
                    }
                }
            }
            """,
        )
        val prefixFailure = runner(prefixCollision, "help").buildAndFail()
        assertTrue(
            "generated Android resource prefix collision 'app_icons_a1a'" in
                prefixFailure.output,
        )

        val ownerCollision = fixture(
            """
            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            symbolFonts {
                iconSet('One') {
                    packageName.set('com.example.one')
                    style('Foo') {
                        codepoints.set(file('one.codepoints'))
                        font.set(file('font.ttf'))
                        resourcePrefix.set('app')
                        composeDrawables()
                    }
                }
                iconSet('Two') {
                    packageName.set('com.example.two')
                    style('FooBar') {
                        codepoints.set(file('two.codepoints'))
                        font.set(file('font.ttf'))
                        resourcePrefix.set('app')
                        composeDrawables()
                    }
                }
            }
            """,
            files = mapOf(
                "one.codepoints" to "bar_baz e001\n",
                "two.codepoints" to "baz e001\n",
            ),
        )
        val ownerFailure = runner(ownerCollision, "help").buildAndFail()
        assertTrue(
            "generated Android resource name collision " +
                "'app_foo_bar_baz_ue001'" in ownerFailure.output,
        )
        assertTrue("icon set 'One' style 'Foo' U+E001" in ownerFailure.output)
        assertTrue("icon set 'Two' style 'FooBar' U+E001" in ownerFailure.output)
    }

    @Test
    fun composeMergeRejectsDuplicateTargetPaths() {
        val project = fixture(
            """
            import io.github.hlcaptain.symbols.gradle.MergeSymbolComposeResources

            plugins {
                id 'io.github.hlcaptain.symbol-fonts'
            }

            tasks.named(
                'mergeGeneratedSymbolComposeResources',
                MergeSymbolComposeResources
            ) {
                inputDirectories.from(file('first'), file('second'))
            }
            """,
            files = mapOf(
                "first/drawable/shared.xml" to "<vector first=\"true\" />\n",
                "second/drawable/shared.xml" to "<vector second=\"true\" />\n",
            ),
        )

        val failure = runner(
            project,
            "mergeGeneratedSymbolComposeResources",
        ).buildAndFail()

        assertTrue(
            "Generated Compose resource target collision 'drawable/shared.xml'" in
                failure.output,
        )
    }

    private fun fixture(
        buildScript: String,
        files: Map<String, String> = emptyMap(),
    ): File {
        val project = temporaryFolder.newFolder()
        project.resolve("settings.gradle").writeText(
            "rootProject.name = 'symbol-fonts-plugin-test'\n",
        )
        project.resolve("build.gradle").writeText(buildScript.trimIndent())
        project.resolve("icons.codepoints").writeText("home e88a\n")
        project.resolve("font.ttf").writeBytes(byteArrayOf())
        files.forEach { (relativePath, contents) ->
            project.resolve(relativePath).also { file ->
                file.parentFile.mkdirs()
                file.writeText(contents)
            }
        }
        return project
    }

    private fun configureAndroidSdk(project: File) {
        val sdk = sequenceOf(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            File(System.getProperty("user.home"), "Android/Sdk").absolutePath,
            File(System.getProperty("user.home"), "Library/Android/sdk").absolutePath,
        )
            .filterNotNull()
            .map(::File)
            .firstOrNull(File::isDirectory)
            ?: error("Android SDK is required for the Android plugin fixture")
        project.resolve("local.properties").writeText(
            "sdk.dir=${sdk.absolutePath.replace("\\", "\\\\")}\n",
        )
    }

    private fun tablerSvg(pathData: String): String =
        """
        <svg xmlns="http://www.w3.org/2000/svg"
            width="24"
            height="24"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            class="icon icon-tabler icons-tabler-outline">
          <path stroke="none" d="M0 0h24v24H0z" fill="none" />
          <path d="$pathData" />
        </svg>
        """.trimIndent()

    private fun generatorTestClasspath(): String =
        requireNotNull(System.getProperty("symbols.generatorTestClasspath"))
            .split(File.pathSeparator)
            .joinToString("\n")

    private fun runner(project: File, vararg tasks: String): GradleRunner =
        configuredRunner(project, tasks.toList(), "--no-configuration-cache")

    private fun cachedRunner(project: File, vararg tasks: String): GradleRunner =
        configuredRunner(project, tasks.toList(), "--configuration-cache")

    private fun buildCachedRunner(project: File, vararg tasks: String): GradleRunner =
        configuredRunner(
            project,
            tasks.toList(),
            "--configuration-cache",
            "--build-cache",
        )

    private fun androidRunner(
        project: File,
        vararg tasks: String,
    ): GradleRunner = configuredAndroidRunner(
        project = project,
        tasks = tasks.toList(),
        configurationCacheArgument = "--no-configuration-cache",
    )

    private fun androidCachedRunner(
        project: File,
        vararg tasks: String,
    ): GradleRunner = configuredAndroidRunner(
        project = project,
        tasks = tasks.toList(),
        configurationCacheArgument = "--configuration-cache",
    )

    private fun composeRunner(
        project: File,
        vararg tasks: String,
    ): GradleRunner = configuredAndroidRunner(
        project = project,
        tasks = tasks.toList(),
        configurationCacheArgument = "--no-configuration-cache",
    )

    private fun composeCachedRunner(
        project: File,
        vararg tasks: String,
    ): GradleRunner = configuredAndroidRunner(
        project = project,
        tasks = tasks.toList(),
        configurationCacheArgument = "--configuration-cache",
    )

    private fun configuredAndroidRunner(
        project: File,
        tasks: List<String>,
        configurationCacheArgument: String,
    ): GradleRunner =
        GradleRunner.create()
            .withGradleVersion(requireNotNull(System.getProperty("symbols.fixtureGradleVersion")))
            .withProjectDir(project)
            .withTestKitDir(project.resolve(".test-kit"))
            .withPluginClasspath(androidPluginClasspath())
            .withArguments(
                tasks.toList() + listOf(
                    "--offline",
                    configurationCacheArgument,
                    "--no-build-cache",
                    "--console=plain",
                    "--stacktrace",
                ),
            )

    private fun androidPluginClasspath(): List<File> {
        val metadata = Properties().apply {
            val resource = requireNotNull(
                this@SymbolFontsPluginFunctionalTest.javaClass.classLoader
                    .getResourceAsStream(
                        "plugin-under-test-metadata.properties",
                    ),
            )
            resource.use(::load)
        }
        val implementation = metadata
            .getProperty("implementation-classpath")
            .split(File.pathSeparator)
        val testRuntime = requireNotNull(
            System.getProperty("symbols.generatorTestClasspath"),
        ).split(File.pathSeparator)
        return (implementation + testRuntime)
            .filter(String::isNotBlank)
            .map(::File)
            .distinctBy(File::getAbsolutePath)
    }

    private fun configuredRunner(
        project: File,
        tasks: List<String>,
        configurationCacheArgument: String,
        buildCacheArgument: String = "--no-build-cache",
    ): GradleRunner =
        GradleRunner.create()
            .withProjectDir(project)
            .withTestKitDir(project.resolve(".test-kit"))
            .withPluginClasspath()
            .withArguments(
                tasks + listOf(
                    "--offline",
                    configurationCacheArgument,
                    buildCacheArgument,
                    "--console=plain",
                    "--stacktrace",
                ),
            )
}
