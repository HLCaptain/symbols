from __future__ import annotations

import importlib.util
import sys
import unittest
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
        code_points = tuple(sorted({code_point for _, code_point in entries}))
        vector_index = code_points.index(0xF8DA)
        chunk_index = vector_index // generator.ICONS_PER_FILE
        source = (
            generator.STYLES[0].source_directory
            / f"OutlinedIcons{chunk_index:03d}.generated.kt"
        ).read_text(encoding="utf-8")
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

        self.assertIn("public val Icons.Outlined.Check: ImageVector", rendered)
        self.assertIn("public val Icons.Outlined.Grade: ImageVector", rendered)
        self.assertIn("public val Icons.Outlined.Star: ImageVector", rendered)
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

        self.assertNotIn("public object Glyphs", public_api)
        self.assertIn(
            "import io.github.hlcaptain.symbols.material.Glyphs",
            icon_file,
        )
        self.assertIn("public val Glyphs.Outlined.Check", icon_file)

    def test_themed_getters_select_direct_style_properties(self) -> None:
        rendered = generator.render_themed_icon_file(
            entries=(("home", 0xE9B2),),
        )

        self.assertIn("public val Icons.Themed.Home: ImageVector", rendered)
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

    def test_legacy_api_uses_separate_index_dispatch(self) -> None:
        style = generator.Style("rounded", "Rounded")
        public_api = generator.render_public_api(style)
        index = generator.render_index(
            style,
            code_points=(0xE5CA, 0xF09A),
            chunk_count=1,
        )

        self.assertIn("public val MaterialSymbol.roundedImageVector", public_api)
        self.assertIn("return roundedVectorAt(vectorIndex, autoMirror)", public_api)
        self.assertIn("roundedVectorChunk000(index, autoMirror)", index)

    def test_unsupported_path_syntax_is_rejected(self) -> None:
        with self.assertRaisesRegex(
            generator.GenerationError,
            "Unsupported SVG path",
        ):
            generator.render_path_operations("M1 2C3 4 5 6 7 8")


if __name__ == "__main__":
    unittest.main()
