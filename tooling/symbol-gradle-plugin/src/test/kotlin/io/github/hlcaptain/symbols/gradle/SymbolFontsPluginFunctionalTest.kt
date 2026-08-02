package io.github.hlcaptain.symbols.gradle

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.api.InvalidUserDataException
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class SymbolFontsPluginFunctionalTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

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
                    include('home')

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
        assertTrue("public object AppIcons" in contents)
        assertTrue(contents.indexOf("public object Regular") < contents.indexOf("public object Rounded"))

        val second = runner(project, "generateAppIconsSymbolFontNamespace").build()
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            second.task(":generateAppIconsSymbolFontNamespace")?.outcome,
        )
    }

    @Test
    fun defaultsPackageSelectionAndConventionalFonts() {
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
                iconSet('SubsetIcons') {
                    include('home')
                    style('Regular') {
                        codepoints.set(file('icons.codepoints'))
                        font.set(file('font.ttf'))
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
                    assert rounded.includedNames.get().isEmpty()
                    assert rounded.manifest.get().asFile == file('rounded.codepoints')
                    assert !rounded.font.isPresent()
                    assert rounded.conventionalFontName.get().isEmpty()
                    assert rounded.conventionalFonts.singleFile ==
                        file('src/commonMain/composeResources/font/material-rounded.ttf')

                    def sharp = tasks.named(
                        'generateAppIconsSharpSymbolFonts',
                        GenerateSymbolFontTask
                    ).get()
                    assert sharp.manifest.get().asFile == file('sharp.codepoints')
                    assert !sharp.font.isPresent()
                    assert sharp.conventionalFontName.get() == 'material-rounded.ttf'
                    assert sharp.conventionalFonts.singleFile ==
                        file('src/commonMain/composeResources/font/material-rounded.ttf')

                    def subset = tasks.named(
                        'generateSubsetIconsRegularSymbolFonts',
                        GenerateSymbolFontTask
                    ).get()
                    assert subset.includedNames.get() == ['home'] as Set
                    assert subset.conventionalFonts.isEmpty()
                }
            }
            """,
            files = mapOf(
                "rounded.codepoints" to "home e001\n",
                "sharp.codepoints" to "home e101\n",
                "src/commonMain/composeResources/font/material-rounded.ttf" to
                    "rounded",
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
                "src/main/res/font/app-icons.otf" to "icons",
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
                        viewportWidth.set(30f)
                        viewportHeight.set(18f)
                    }
                    style('Custom') {
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
                }
                iconSet('Two') {
                    packageName.set('com.example.icons')
                    rootName.set('Shared')
                }
            }
            """,
        )
        val rootFailure = runner(rootCollision, "help").buildAndFail()
        assertTrue(
            "generated Kotlin root collision 'com.example.icons.Shared'" in
                rootFailure.output,
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
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                    style('A1b') {
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
                    include('home')
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
                    include('bar_baz')
                    style('Foo') {
                        codepoints.set(file('one.codepoints'))
                        font.set(file('font.ttf'))
                        resourcePrefix.set('app')
                        composeDrawables()
                    }
                }
                iconSet('Two') {
                    packageName.set('com.example.two')
                    include('baz')
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

    private fun runner(project: File, vararg tasks: String): GradleRunner =
        configuredRunner(project, tasks.toList(), "--no-configuration-cache")

    private fun cachedRunner(project: File, vararg tasks: String): GradleRunner =
        configuredRunner(project, tasks.toList(), "--configuration-cache")

    private fun configuredRunner(
        project: File,
        tasks: List<String>,
        configurationCacheArgument: String,
    ): GradleRunner =
        GradleRunner.create()
            .withProjectDir(project)
            .withTestKitDir(project.resolve(".test-kit"))
            .withPluginClasspath()
            .withArguments(
                tasks + listOf(
                    "--offline",
                    configurationCacheArgument,
                    "--no-build-cache",
                    "--console=plain",
                    "--stacktrace",
                ),
            )
}
