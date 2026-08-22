from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = (
    Path(__file__).resolve().parents[1]
    / "generate_material_font_namespaces.py"
)
SPEC = importlib.util.spec_from_file_location(
    "generate_material_font_namespaces",
    SCRIPT,
)
assert SPEC is not None and SPEC.loader is not None
generator = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = generator
SPEC.loader.exec_module(generator)


class FontNamespaceGeneratorTest(unittest.TestCase):
    def test_chunk_uses_style_typed_inline_wrapper(self) -> None:
        entries = (
            generator.Entry("check", 0xE5CA, 1),
            generator.Entry("3d_rotation", 0xE84D, 2),
        )

        rendered = generator.render_chunk(
            generator.Style("Rounded"),
            entries,
            chunk_index=0,
        )

        self.assertIn(
            "val Symbols.Rounded.Check: RoundedMaterialSymbol",
            rendered,
        )
        self.assertIn(
            "get() = RoundedMaterialSymbol(MaterialSymbols.Check)",
            rendered,
        )
        self.assertIn(
            "val Symbols.Rounded._3dRotation: RoundedMaterialSymbol",
            rendered,
        )

    def test_canonical_catalog_generates_every_name_for_every_style(self) -> None:
        entries = generator.parse_codepoints(generator.DEFAULT_INPUT)
        outputs = generator.generate_outputs(entries)
        expected_files_per_style = (
            len(entries) + generator.SYMBOLS_PER_FILE - 1
        ) // generator.SYMBOLS_PER_FILE

        self.assertEqual(
            len(generator.STYLES) * expected_files_per_style,
            len(outputs),
        )
        self.assertEqual(
            len(generator.STYLES) * len(entries),
            sum(
                content.count("val Symbols.")
                for content in outputs.values()
            ),
        )
        self.assertTrue(
            all("public val Symbols." not in content for content in outputs.values())
        )

    def test_checked_in_output_is_current(self) -> None:
        entries = generator.parse_codepoints(generator.DEFAULT_INPUT)
        outputs = generator.generate_outputs(entries)

        for path, expected in outputs.items():
            self.assertTrue(path.is_file(), path)
            self.assertEqual(expected, path.read_text(encoding="utf-8"), path)

        actual_paths = set(
            generator.DEFAULT_OUTPUT_ROOT.glob(generator.GENERATED_FILE_GLOB)
        )
        self.assertEqual(set(outputs), actual_paths)

    def test_check_rejects_missing_output(self) -> None:
        entries = (generator.Entry("check", 0xE5CA, 1),)
        with tempfile.TemporaryDirectory() as directory:
            output_root = Path(directory)
            outputs = generator.generate_outputs(entries, output_root)

            with self.assertRaisesRegex(
                generator.GenerationError,
                "stale",
            ):
                generator.write_outputs(
                    outputs,
                    output_root,
                    check=True,
                )


if __name__ == "__main__":
    unittest.main()
