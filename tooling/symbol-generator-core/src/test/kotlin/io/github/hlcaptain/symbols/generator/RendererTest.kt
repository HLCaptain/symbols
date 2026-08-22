package io.github.hlcaptain.symbols.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RendererTest {
    @Test
    fun kotlinOutputIsChunkedDirectAndAliasDeduplicated() {
        val iconSet = testIconSet()
        val renderer = KotlinImageVectorRenderer(
            VectorRenderOptions(symbolsPerFile = 2),
        )

        val first = renderer.render(iconSet)
        val second = renderer.render(iconSet)

        assertEquals(first, second)
        assertEquals(3, first.files.size)
        val sources = first.files.values.joinToString("\n")
        assertTrue("object AppIcons" in sources)
        assertTrue("object Rounded" in sources)
        assertTrue("AppIcons.Rounded.Grade" in sources)
        assertTrue("AppIcons.Rounded.Star" in sources)
        assertTrue("get() = appIconsRoundedUF09A()" in sources)
        assertEquals(1, Regex("private fun appIconsRoundedUF09A\\(").findAll(sources).count())
        assertTrue("private var _appIconsRoundedUF09A: ImageVector? = null" in sources)
        assertTrue("moveTo(0f, 0f)" in sources)
        assertTrue("quadTo(4f, 2f, 8f, 0f)" in sources)
        assertTrue("curveTo(9f, 1f, 11f, 3f, 12f, 4f)" in sources)
        assertFalse("PathParser" in sources)
        assertFalse("IntArray" in sources)
        assertFalse("allIcons" in sources)
        assertFalse("when (index" in sources)
        assertFalse(Regex("\\bpublic\\b").containsMatchIn(sources))
    }

    @Test
    fun kotlinNamespaceCanBeOwnedByABuildIntegration() {
        val rendered = KotlinImageVectorRenderer(
            includeNamespace = false,
        ).render(testIconSet())

        assertEquals(1, rendered.files.size)
        assertFalse(
            rendered.files.keys.any { path ->
                path.endsWith("/AppIcons.generated.kt")
            },
        )
        assertTrue(
            rendered.files.values.single().contains(
                "AppIcons.Rounded.Home",
            ),
        )
    }

    @Test
    fun androidOutputHasOneXmlPerStyleAndCodePoint() {
        val iconSet = testIconSet()
        val output = AndroidVectorXmlRenderer(
            options = VectorRenderOptions(precision = 3, autoMirror = true),
            resourcePrefix = "app_icons",
        ).render(iconSet)

        assertEquals(3, output.files.files.size)
        assertEquals(
            "app_icons_rounded_home_ue88a",
            output.resourceNames.getValue(AndroidResourceKey("Rounded", 0xE88A)),
        )
        val star = output.files.files.getValue(
            "drawable/app_icons_rounded_grade_uf09a.xml",
        )
        assertTrue("android:autoMirrored=\"true\"" in star)
        assertTrue("android:pathData=\"M 0,0 L 4,0 Q 4,2 8,0 C 9,1 11,3 12,4 Z\"" in star)
        assertFalse("-0" in star)
    }

    @Test
    fun svgOutputRetainsStyledPathsAndUsesSemanticResourceNames() {
        val iconSet = testSvgIconSet()
        val kotlin = KotlinImageVectorRenderer(
            VectorRenderOptions(fillColor = "#FF123456"),
        ).render(iconSet)
        val sources = kotlin.files.values.joinToString("\n")

        assertTrue("Tabler.Outline.Home" in sources)
        assertTrue("name = \"Tabler.Outline.Home\"" in sources)
        assertTrue("name = \"path_0\"" in sources)
        assertTrue("fill = null" in sources)
        assertTrue("stroke = SolidColor(Color(0xFF123456.toInt()))" in sources)
        assertTrue("strokeLineWidth = 2f" in sources)
        assertTrue("strokeLineCap = StrokeCap.Round" in sources)
        assertTrue("pathFillType = PathFillType.EvenOdd" in sources)

        val android = AndroidVectorXmlRenderer(
            options = VectorRenderOptions(fillColor = "#FF123456"),
            resourcePrefix = "tabler",
        ).render(iconSet)
        assertEquals(
            "tabler_outline_home",
            android.resourceNames.getValue(
                SvgAndroidResourceKey("Outline", "home"),
            ),
        )
        val xml = android.files.files.getValue(
            "drawable/tabler_outline_home.xml",
        )
        assertTrue("android:name=\"path_0\"" in xml)
        assertTrue("android:fillColor=\"#00000000\"" in xml)
        assertTrue("android:strokeColor=\"#FF123456\"" in xml)
        assertTrue("android:strokeLineJoin=\"round\"" in xml)
        assertTrue("android:fillType=\"evenOdd\"" in xml)
        assertFalse("_u" in android.files.files.keys.single())
    }

    @Test
    fun rejectsStylePackageSegmentsThatNormalizeToTheSameName() {
        val sourceStyle = testIconSet().styles.single()
        val collision = assertFailsWith<SymbolGenerationException> {
            GeneratedIconSet(
                packageName = "com.example.icons",
                name = "AppIcons",
                styles = listOf(
                    sourceStyle.copy(name = "FooBar"),
                    sourceStyle.copy(name = "Foo_Bar"),
                ),
            )
        }
        assertTrue("foo_bar <- FooBar, Foo_Bar" in collision.message.orEmpty())

        assertFailsWith<SymbolGenerationException> {
            KotlinIconNamespaceRenderer().render(
                packageName = "com.example.icons",
                iconSetName = "AppIcons",
                styleNames = listOf("FooBar", "Foo_Bar"),
            )
        }
    }

    @Test
    fun rejectsStyleAndroidPrefixesThatNormalizeToTheSameName() {
        val source = testIconSet()
        val iconSet = GeneratedIconSet(
            packageName = source.packageName,
            name = source.name,
            styles = listOf(
                source.styles.single().copy(name = "Foo"),
                source.styles.single().copy(name = "_Foo"),
            ),
        )

        val collision = assertFailsWith<SymbolGenerationException> {
            AndroidVectorXmlRenderer(resourcePrefix = "app_icons")
                .render(iconSet)
        }
        assertTrue("foo <- Foo, _Foo" in collision.message.orEmpty())
    }

    @Test
    fun rejectsExactAndroidResourceNameCollisionsAcrossStyles() {
        val source = testIconSet()
        val sourceStyle = source.styles.single()
        val iconSet = GeneratedIconSet(
            packageName = source.packageName,
            name = source.name,
            styles = listOf(
                sourceStyle.copy(
                    name = "Foo",
                    catalog = SymbolCatalog.of(
                        listOf(SymbolEntry("bar_baz", 0xE88A)),
                    ),
                ),
                sourceStyle.copy(
                    name = "FooBar",
                    catalog = SymbolCatalog.of(
                        listOf(SymbolEntry("baz", 0xE88A)),
                    ),
                ),
            ),
        )

        val collision = assertFailsWith<SymbolGenerationException> {
            AndroidVectorXmlRenderer(resourcePrefix = "app_icons")
                .render(iconSet)
        }
        assertTrue(
            "app_icons_foo_bar_baz_ue88a" in collision.message.orEmpty(),
        )
    }

    private fun testIconSet(): GeneratedIconSet {
        val catalog = SymbolCatalog.of(
            listOf(
                SymbolEntry("home", 0xE88A),
                SymbolEntry("search", 0xE8B6),
                SymbolEntry("grade", 0xF09A),
                SymbolEntry("star", 0xF09A),
            ),
        )
        val commands = listOf(
            VectorCommand.MoveTo(VectorPoint(-0.00001f, 0f)),
            VectorCommand.LineTo(VectorPoint(4f, 0f)),
            VectorCommand.QuadraticTo(
                control = VectorPoint(4f, 2f),
                end = VectorPoint(8f, 0f),
            ),
            VectorCommand.CubicTo(
                control1 = VectorPoint(9f, 1f),
                control2 = VectorPoint(11f, 3f),
                end = VectorPoint(12f, 4f),
            ),
            VectorCommand.Close,
        )
        val outlines = catalog.uniqueCodePoints.associateWith { codePoint ->
            GlyphOutline(codePoint, commands)
        }
        return GeneratedIconSet(
            packageName = "com.example.icons",
            name = "AppIcons",
            styles = listOf(
                GeneratedStyle(
                    name = "Rounded",
                    catalog = catalog,
                    font = ExtractedFont(
                        familyName = "Test Icons",
                        unitsPerEm = 1_000,
                        appliedAxes = emptyMap(),
                        outlines = outlines,
                    ),
                ),
            ),
        )
    }

    private fun testSvgIconSet(): GeneratedSvgIconSet = GeneratedSvgIconSet(
        packageName = "com.example.icons",
        name = "Tabler",
        styles = listOf(
            GeneratedSvgStyle(
                name = "Outline",
                icons = listOf(
                    SvgIcon(
                        name = "home",
                        paths = listOf(
                            StyledVectorPath(
                                commands = listOf(
                                    VectorCommand.MoveTo(VectorPoint(2f, 12f)),
                                    VectorCommand.LineTo(VectorPoint(12f, 2f)),
                                    VectorCommand.LineTo(VectorPoint(22f, 12f)),
                                ),
                                fill = false,
                                stroke = true,
                                strokeWidth = 2f,
                                strokeCap = VectorStrokeCap.Round,
                                strokeJoin = VectorStrokeJoin.Round,
                                fillRule = VectorFillRule.EvenOdd,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
