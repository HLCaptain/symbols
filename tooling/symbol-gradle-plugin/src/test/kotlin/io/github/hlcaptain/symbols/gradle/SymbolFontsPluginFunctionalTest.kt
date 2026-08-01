package io.github.hlcaptain.symbols.gradle

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
                    manifest.set(file('icons.codepoints'))
                    include('home')

                    style('Rounded') {
                        font.set(file('font.ttf'))
                        imageVectors()
                    }
                    style('Regular') {
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
                    manifest.set(file('icons.codepoints'))
                    include('home')
                    style('a1a') {
                        font.set(file('font.ttf'))
                        composeDrawables()
                    }
                    style('a1A_') {
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
                    manifest.set(file('one.codepoints'))
                    include('bar_baz')
                    style('Foo') {
                        font.set(file('font.ttf'))
                        resourcePrefix.set('app')
                        composeDrawables()
                    }
                }
                iconSet('Two') {
                    packageName.set('com.example.two')
                    manifest.set(file('two.codepoints'))
                    include('baz')
                    style('FooBar') {
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
        GradleRunner.create()
            .withProjectDir(project)
            .withTestKitDir(project.resolve(".test-kit"))
            .withPluginClasspath()
            .withArguments(
                tasks.toList() + listOf(
                    "--offline",
                    "--no-configuration-cache",
                    "--no-build-cache",
                    "--console=plain",
                    "--stacktrace",
                ),
            )
}
