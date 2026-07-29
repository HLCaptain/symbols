from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


MODULE_PATH = (
    Path(__file__).resolve().parents[1] / "generate_material_static_fonts.py"
)
SPEC = importlib.util.spec_from_file_location(
    "generate_material_static_fonts",
    MODULE_PATH,
)
assert SPEC is not None and SPEC.loader is not None
module = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


@unittest.skipIf(
    module.FONTTOOLS_IMPORT_ERROR is not None,
    "FontTools is not installed",
)
class GenerateMaterialStaticFontsTest(unittest.TestCase):
    def test_default_axes_match_material_contract(self) -> None:
        self.assertEqual(
            {
                "FILL": 0.0,
                "GRAD": 0.0,
                "opsz": 24.0,
                "wght": 400.0,
            },
            module.DEFAULT_AXES,
        )

    def test_static_font_is_deterministic_and_has_no_variable_tables(self) -> None:
        spec = module.FONT_SPECS[0]
        input_path = module.REPOSITORY_ROOT / spec.input_path

        first = module.instantiate_static_font(input_path)
        second = module.instantiate_static_font(input_path)

        self.assertEqual(first, second)
        font = module.TTFont(
            module.BytesIO(first),
            recalcTimestamp=False,
        )
        self.assertFalse(module.VARIABLE_TABLES.intersection(font.keys()))
        self.assertEqual(6301, len(font.getGlyphOrder()))

    def test_all_specs_have_distinct_inputs_and_outputs(self) -> None:
        self.assertEqual(
            len(module.FONT_SPECS),
            len({spec.input_path for spec in module.FONT_SPECS}),
        )
        self.assertEqual(
            len(module.FONT_SPECS),
            len({spec.output_path for spec in module.FONT_SPECS}),
        )


if __name__ == "__main__":
    unittest.main()
