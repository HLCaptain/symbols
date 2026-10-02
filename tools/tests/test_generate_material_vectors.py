from __future__ import annotations

import importlib.util
import sys
import unittest
from tempfile import TemporaryDirectory
from unittest.mock import patch
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "generate_material_vectors.py"
SPEC = importlib.util.spec_from_file_location("generate_material_vectors", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
generator = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = generator
SPEC.loader.exec_module(generator)


class VectorGeneratorTest(unittest.TestCase):
    def test_canonical_names_have_stable_typed_identifiers_and_aliases(self) -> None:
        entries = generator.parse_codepoints(generator.CODEPOINTS_PATH)
        identifiers = [generator.kotlin_identifier(name) for name, _ in entries]
        grouped = generator.group_names_by_code_point(entries)

        self.assertEqual(4102, len(identifiers))
        self.assertEqual(4102, len(set(identifiers)))
        self.assertEqual(
            (
                "grade",
                "star",
                "star_border",
                "star_border_purple500",
                "star_outline",
                "star_purple500",
            ),
            grouped[0xF09A],
        )

    def test_themed_output_is_isolated_and_cleans_only_owned_stale_files(self) -> None:
        with TemporaryDirectory() as temporary, patch.object(
            generator, "import_fonttools", side_effect=AssertionError("themed needs no font")
        ):
            output = Path(temporary)
            args = ["--style", "themed", "--output", str(output)]
            self.assertEqual(0, generator.main(args))
            directory = output / generator.THEMED_PACKAGE.replace(".", "/")
            stale = directory / "ThemedIcons999.generated.kt"
            stale.write_text(generator.GENERATED_HEADER.replace("2.874", "older-version") + "// stale\n")
            handwritten = directory / "notes.txt"
            handwritten.write_text("keep")
            self.assertEqual(0, generator.main(args))
            self.assertFalse(stale.exists())
            self.assertEqual("keep", handwritten.read_text())
            self.assertEqual(65, len(list(directory.glob("*.generated.kt"))))
            self.assertEqual(0, generator.main([*args, "--check"]))

    def test_simple_pascal_case_and_digit_prefix(self) -> None:
        self.assertEqual(
            "ArrowBackIosNew",
            generator.kotlin_identifier("arrow_back_ios_new"),
        )
        self.assertEqual(
            "_3dRotation",
            generator.kotlin_identifier("3d_rotation"),
        )
        self.assertEqual("_360", generator.kotlin_identifier("360"))

    def test_direct_path_operations_cover_bundled_font_commands(self) -> None:
        operations = generator.render_path_operations(
            "M1 2 3 4L5 6H7V8Q9 10 11 12Z"
        )

        self.assertEqual(
            (
                "moveTo(1f, 2f)",
                "lineTo(3f, 4f)",
                "lineTo(5f, 6f)",
                "horizontalLineTo(7f)",
                "verticalLineTo(8f)",
                "quadTo(9f, 10f, 11f, 12f)",
                "close()",
            ),
            operations,
        )

    def test_generated_operations_preserve_precision_and_viewport_overshoot(
        self,
    ) -> None:
        entries = generator.parse_codepoints(generator.CODEPOINTS_PATH)
        style = generator.STYLES[0]
        tt_font, svg_pen, transform_pen, _ = generator.import_fonttools()
        paths = generator.extract_paths(style, (0xF8DA,), tt_font, svg_pen, transform_pen)
        source = generator.render_icon_file(
            style=style, chunk_index=0, start=0, code_points=(0xF8DA,),
            paths=paths, names_by_code_point=generator.group_names_by_code_point(entries),
        )
        body = source.split("private object OutlinedVectorF8DA {", 1)[1]
        body = body.split("\nprivate object ", 1)[0]

        self.assertIn("-0.3f", body)
        self.assertIn("24.35f", body)
        self.assertEqual("1.2346", generator.format_coordinate(1.23456))

    def test_alias_getters_share_one_direct_vector_body(self) -> None:
        style = generator.Style("outlined", "Outlined")
        entries = (
            ("check", 0xE5CA),
            ("grade", 0xF09A),
            ("star", 0xF09A),
        )
        rendered = generator.render_icon_file(
            style=style,
            chunk_index=0,
            start=0,
            code_points=(0xE5CA, 0xF09A),
            paths=("M1 2L3 4Z", "M5 6Q7 8 9 10Z"),
            names_by_code_point=generator.group_names_by_code_point(entries),
        )

        self.assertIn("val Icons.Outlined.Check: ImageVector", rendered)
        self.assertIn("val Icons.Outlined.Grade: ImageVector", rendered)
        self.assertIn("val Icons.Outlined.Star: ImageVector", rendered)
        self.assertNotIn("public val Icons.Outlined.", rendered)
        self.assertEqual(
            2,
            rendered.count(
                "get() = OutlinedVectorF09A.value(autoMirror = false)"
            ),
        )
        self.assertEqual(1, rendered.count("private object OutlinedVectorF09A {"))
        self.assertEqual(
            2,
            rendered.count("private val autoMirrored: ImageVector by lazy {"),
        )
        self.assertIn("moveTo(1f, 2f)", rendered)
        self.assertIn("lineTo(3f, 4f)", rendered)
        self.assertIn("quadTo(7f, 8f, 9f, 10f)", rendered)
        self.assertNotIn("PathParser", rendered)
        self.assertNotIn("outlinedVectorAt", rendered)

    def test_typed_root_is_generator_configurable(self) -> None:
        style = generator.Style("outlined", "Outlined", typed_root="Glyphs")
        public_api = generator.render_public_api(style)
        icon_file = generator.render_icon_file(
            style=style,
            chunk_index=0,
            start=0,
            code_points=(0xE5CA,),
            paths=("M1 2Z",),
            names_by_code_point={0xE5CA: ("check",)},
        )

        self.assertNotIn("object Glyphs", public_api)
        self.assertIn(
            "import io.github.hlcaptain.symbols.material.Glyphs",
            icon_file,
        )
        self.assertIn("val Glyphs.Outlined.Check", icon_file)
        self.assertIn("val Glyphs.AutoMirrored.Outlined.Check", icon_file)

    def test_typed_mirrored_getters_share_direct_caches_for_every_style(self) -> None:
        entries = (("arrow_back", 0xE5C4), ("grade", 0xF09A), ("star", 0xF09A))
        for style in generator.STYLES:
            with self.subTest(style=style.name):
                rendered = generator.render_icon_file(
                    style=style,
                    chunk_index=0,
                    start=0,
                    code_points=(0xE5C4, 0xF09A),
                    paths=("M1 2L3 4Z", "M5 6Q7 8 9 10Z"),
                    names_by_code_point=generator.group_names_by_code_point(entries),
                )

                for name, code_point in entries:
                    identifier = generator.kotlin_identifier(name)
                    self.assertIn(
                        f"val Icons.AutoMirrored.{style.title}.{identifier}: ImageVector\n"
                        f"    get() = {style.title}Vector{code_point:X}.value(autoMirror = true)",
                        rendered,
                    )
                    self.assertIn(
                        f"val Icons.{style.title}.{identifier}: ImageVector\n"
                        f"    get() = {style.title}Vector{code_point:X}.value(autoMirror = false)",
                        rendered,
                    )
                self.assertEqual(
                    1, rendered.count(f"private object {style.title}VectorF09A "),
                )
                self.assertNotIn(f"{style.name}VectorAt", rendered)
                self.assertNotIn(f"{style.name}VectorIndex", rendered)
                self.assertNotIn(f"as{style.title}ImageVector", rendered)

    def test_themed_getters_select_direct_style_properties(self) -> None:
        rendered = generator.render_themed_icon_file(
            entries=(("home", 0xE9B2),),
        )

        self.assertIn("val Icons.Themed.Home: ImageVector", rendered)
        self.assertNotIn("public val Icons.Themed.", rendered)
        self.assertIn(
            "import io.github.hlcaptain.symbols.material.MaterialSymbolsTheme "
            "as SymbolsTheme",
            rendered,
        )
        self.assertIn("when (SymbolsTheme.style)", rendered)
        self.assertIn(
            "MaterialSymbolStyle.Outlined -> Icons.Outlined.OutlinedHome",
            rendered,
        )
        self.assertIn(
            "MaterialSymbolStyle.Rounded -> Icons.Rounded.RoundedHome",
            rendered,
        )
        self.assertIn(
            "MaterialSymbolStyle.Sharp -> Icons.Sharp.SharpHome",
            rendered,
        )
        self.assertNotIn("outlinedVectorAt", rendered)

    def test_mirrored_themed_getters_select_direct_mirrored_style_properties(self) -> None:
        rendered = generator.render_themed_icon_file(
            entries=(("arrow_back", 0xE5C4),),
        )

        self.assertIn(
            "val Icons.AutoMirrored.Themed.ArrowBack: ImageVector\n"
            "    @Composable\n"
            "    @ReadOnlyComposable\n"
            "    get() = when (SymbolsTheme.style)",
            rendered,
        )
        for style in generator.STYLES:
            with self.subTest(style=style.name):
                self.assertEqual(
                    1,
                    rendered.count(
                        f"import {style.package_name}.ArrowBack as {style.title}ArrowBack"
                    ),
                )
                self.assertIn(
                    f"MaterialSymbolStyle.{style.title} -> "
                    f"Icons.AutoMirrored.{style.title}.{style.title}ArrowBack",
                    rendered,
                )
                self.assertNotIn(f"{style.name}VectorAt", rendered)
                self.assertNotIn(f"as{style.title}ImageVector", rendered)
        self.assertNotIn("asThemedImageVector", rendered)

    def test_legacy_api_uses_separate_index_dispatch(self) -> None:
        style = generator.Style("rounded", "Rounded")
        public_api = generator.render_public_api(style)
        index = generator.render_index(
            style,
            code_points=(0xE5CA, 0xF09A),
            chunk_count=1,
        )

        self.assertIn("val MaterialSymbol.roundedImageVector", public_api)
        self.assertIn("fun MaterialSymbol.asRoundedImageVector", public_api)
        self.assertIn("Use this function when the symbol is chosen at runtime", public_api)
        self.assertIn("@param autoMirror", public_api)
        self.assertIn("@throws IllegalArgumentException", public_api)
        self.assertNotIn("public ", public_api)
        self.assertIn("return roundedVectorAt(vectorIndex, autoMirror)", public_api)
        self.assertIn("roundedVectorChunk000(index, autoMirror)", index)

    def test_filled_getters_deduplicate_invariants_and_keep_aliases_direct(self) -> None:
        style = generator.Style("rounded", "Rounded")
        source = generator.render_icon_file(
            style=style, chunk_index=0, start=0,
            code_points=(0xE5CA, 0xF09A),
            paths=("M1 2L3 4Z", "M5 6L7 8Z"),
            filled_paths=("M1 2L3 4Z", "M9 10L11 12Z"),
            names_by_code_point={0xE5CA: ("check",), 0xF09A: ("grade", "star")},
        )

        for root, mirror in (("Icons", "false"), ("Icons.AutoMirrored", "true")):
            self.assertIn(
                f"val {root}.Rounded.Filled.Check: ImageVector\n"
                f"    get() = RoundedVectorE5CA.value(autoMirror = {mirror})",
                source,
            )
            for name in ("Grade", "Star"):
                self.assertIn(
                    f"val {root}.Rounded.Filled.{name}: ImageVector\n"
                    f"    get() = RoundedFilledVectorF09A.value(autoMirror = {mirror})",
                    source,
                )
        self.assertNotIn("private object RoundedFilledVectorE5CA", source)
        self.assertEqual(1, source.count("private object RoundedFilledVectorF09A : RoundedVectorCache("))
        self.assertIn('"MaterialSymbolsRounded.Filled.U+F09A",', source)
        self.assertIn('"M9 10L11 12Z",', source)
        self.assertIn('"M5 6L7 8Z",', source)
        self.assertNotIn("PathParser", source)
        self.assertNotIn("roundedFilledVectorAt", source)

    def test_pinned_fill_axis_changes_hearts_but_not_arrows(self) -> None:
        style = generator.Style("rounded", "Rounded")
        tt_font, svg_pen, transform_pen, _ = generator.import_fonttools()
        points = (0xE5C4, 0xE87E, 0xE88E)
        normal = generator.extract_paths(style, points, tt_font, svg_pen, transform_pen)
        filled = generator.extract_paths(style, points, tt_font, svg_pen, transform_pen, fill=1.0)

        self.assertEqual(normal[0], filled[0])
        self.assertNotEqual(normal[1], filled[1])
        self.assertNotEqual(normal[2], filled[2])
        self.assertEqual(0.0, generator.AXIS_LOCATION["FILL"])

    def test_filled_paths_require_complete_rounded_input(self) -> None:
        for style, paths in ((generator.Style("outlined", "Outlined"), ("M1 2Z",)),
                             (generator.Style("rounded", "Rounded"), ())):
            with self.subTest(style=style.name), self.assertRaisesRegex(generator.GenerationError, "Filled vectors"):
                generator.render_style(style, (("check", 0xE5CA),), (0xE5CA,), ("M1 2Z",), paths)

    def test_rounded_backings_keep_per_icon_caches_and_exact_svg(self) -> None:
        normal = ("M1.2346 2H3V4Q5 6 7 8Z", "M0 0L1 1Z")
        filled = ("M2 2H3V4Q5 6 7 8Z", normal[1])
        rendered = generator.render_icon_file(
            generator.Style("rounded", "Rounded", typed_root="Glyphs"),
            0, 0, (0xE87E, 0xE5C4), normal,
            {0xE87E: ("favorite", "favorite_border"), 0xE5C4: ("arrow_back",)}, filled,
        )
        self.assertIn('"' + filled[0] + '",', rendered)
        self.assertIn('"' + normal[0] + '",', rendered)
        self.assertIn("private object RoundedFilledVectorE87E", rendered)
        self.assertNotIn("private object RoundedFilledVectorE5C4", rendered)
        self.assertIn("val Glyphs.AutoMirrored.Rounded.Filled.Favorite", rendered)
        self.assertEqual(2, rendered.count("get() = RoundedFilledVectorE87E.value(autoMirror = false)"))
        self.assertNotIn("roundedVectorAt", rendered)
        self.assertNotIn("moveTo(", rendered)

    def test_shared_cache_keeps_literals_per_icon_and_builder_once(self) -> None:
        style = generator.Style("rounded", "Rounded")
        rendered = generator.render_style(
            style, (("favorite", 0xE87E), ("favorite_border", 0xE87E), ("check", 0xE5CA)),
            (0xE87E, 0xE5CA), ("M1 2Z", "M3 4Z"), ("M2 3Z", "M3 4Z"),
        )
        icons = next(text for path, text in rendered.items() if path.name == "RoundedIcons000.generated.kt")
        helper = next(text for path, text in rendered.items() if path.name == "RoundedVectorCache.generated.kt")
        self.assertIn('private object RoundedVectorE87E : RoundedVectorCache(\n    "MaterialSymbolsRounded.U+E87E",\n    "M1 2Z",', icons)
        self.assertIn('private object RoundedFilledVectorE87E : RoundedVectorCache(', icons)
        self.assertNotIn('RoundedFilledVectorE5CA', icons)
        self.assertNotIn('by lazy', icons)
        self.assertNotIn('ImageVector.Builder', icons)
        self.assertNotIn('addPathNodes', icons)
        self.assertEqual(2, helper.count('by lazy'))
        self.assertEqual(1, helper.count('ImageVector.Builder('))
        self.assertEqual(1, helper.count('addPathNodes(pathData)'))
        self.assertNotIn('E87E', helper)
        self.assertNotIn('IntArray', helper)
        self.assertNotIn('Map<', helper)

    def test_compact_rejects_oversized_jvm_string_constant(self) -> None:
        with self.assertRaisesRegex(generator.GenerationError, "single JVM UTF8"):
            generator.render_cached_vector(
                "TestVector", "Test", "M1 2" * 17000, cache_class="RoundedVectorCache",
            )

    def test_unsupported_path_syntax_is_rejected(self) -> None:
        with self.assertRaisesRegex(
            generator.GenerationError,
            "Unsupported SVG path",
        ):
            generator.render_path_operations("M1 2C3 4 5 6 7 8")


if __name__ == "__main__":
    unittest.main()
