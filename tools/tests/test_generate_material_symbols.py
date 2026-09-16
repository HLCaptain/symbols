from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "generate_material_symbols.py"
SPEC = importlib.util.spec_from_file_location("generate_material_symbols", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
generator = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = generator
SPEC.loader.exec_module(generator)


class GeneratorTest(unittest.TestCase):
    def parse(self, content: str):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "symbols.codepoints"
            path.write_text(content, encoding="utf-8")
            return generator.parse_codepoints(path)

    def test_canonical_catalog_has_expected_lossless_shape(self) -> None:
        entries = generator.parse_codepoints(generator.DEFAULT_INPUT)
        identifiers = [generator.kotlin_identifier(entry.name) for entry in entries]
        code_points = {entry.code_point for entry in entries}

        self.assertEqual(4102, len(entries))
        self.assertEqual(4102, len({entry.name for entry in entries}))
        self.assertEqual(4102, len(set(identifiers)))
        self.assertEqual(3802, len(code_points))
        self.assertEqual(65, sum(identifier.startswith("_") for identifier in identifiers))

        aliases = {
            entry.name
            for entry in entries
            if entry.code_point == 0xF09A
        }
        self.assertEqual(
            {
                "grade",
                "star",
                "star_border",
                "star_border_purple500",
                "star_outline",
                "star_purple500",
            },
            aliases,
        )

    def test_simple_pascal_case_and_digit_prefix(self) -> None:
        self.assertEqual("ArrowBackIosNew", generator.kotlin_identifier("arrow_back_ios_new"))
        self.assertEqual("_3dRotation", generator.kotlin_identifier("3d_rotation"))
        self.assertEqual("_360", generator.kotlin_identifier("360"))

    def test_entries_are_sorted_deterministically_and_supplementary_is_allowed(self) -> None:
        entries = self.parse("z_last 10ffff\na_first 1f600\n")

        self.assertEqual(["a_first", "z_last"], [entry.name for entry in entries])
        rendered = generator.render_kotlin(entries, "example.symbols")
        self.assertIn("0x1F600, 0x10FFFF", rendered)
        self.assertLess(rendered.index("MaterialSymbols.AFirst"), rendered.index("MaterialSymbols.ZLast"))
        self.assertNotIn("public val MaterialSymbols.", rendered)

    def test_duplicate_name_is_rejected(self) -> None:
        with self.assertRaisesRegex(generator.CatalogError, "duplicate name"):
            self.parse("home e88a\nhome e88b\n")

    def test_invalid_unicode_scalars_are_rejected(self) -> None:
        for code_point in ("d800", "dfff", "110000"):
            with self.subTest(code_point=code_point):
                with self.assertRaisesRegex(generator.CatalogError, "not a Unicode scalar"):
                    self.parse(f"invalid {code_point}\n")

    def test_identifier_collision_is_rejected(self) -> None:
        with self.assertRaisesRegex(generator.CatalogError, "identifier collision"):
            self.parse("a1b e000\na_1b e001\n")

    def test_checked_in_output_is_current(self) -> None:
        entries = generator.parse_codepoints(generator.DEFAULT_INPUT)
        expected = generator.render_kotlin(entries, generator.DEFAULT_PACKAGE)
        actual = generator.DEFAULT_OUTPUT.read_text(encoding="utf-8")

        self.assertEqual(expected, actual)


if __name__ == "__main__":
    unittest.main()
