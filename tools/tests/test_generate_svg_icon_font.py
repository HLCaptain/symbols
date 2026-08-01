from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from io import BytesIO
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[1] / "generate_svg_icon_font.py"
SPEC = importlib.util.spec_from_file_location("generate_svg_icon_font", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
module = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


@unittest.skipIf(
    module.DEPENDENCY_IMPORT_ERROR is not None,
    "SVG font dependencies are not installed",
)
class GenerateSvgIconFontTest(unittest.TestCase):
    def test_build_is_deterministic_and_preserves_assignments(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            icons = root / "icons"
            icons.mkdir()
            (icons / "ArrowLeft.svg").write_text(
                """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
                    fill="none" stroke="currentColor" stroke-width="2"
                    stroke-linecap="round" stroke-linejoin="round">
                    <path d="M5 12h14M12 5l-7 7 7 7"/>
                </svg>""",
                encoding="utf-8",
            )
            (icons / "square.svg").write_text(
                """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                    <path d="M4 4h16v16H4z"/>
                </svg>""",
                encoding="utf-8",
            )
            font_path = root / "AppIcons.ttf"
            manifest_path = root / "AppIcons.codepoints"

            count = module.process(
                input_dir=icons,
                font_path=font_path,
                manifest_path=manifest_path,
                family_name="App Icons",
            )
            self.assertEqual(2, count)
            self.assertEqual(
                "arrow_left e000\nsquare e001\n",
                manifest_path.read_text(encoding="utf-8"),
            )

            font = module.TTFont(BytesIO(font_path.read_bytes()))
            self.assertEqual(
                {0xE000: "uniE000", 0xE001: "uniE001"},
                font.getBestCmap(),
            )
            arrow = font["glyf"]["uniE000"]
            self.assertGreater(arrow.numberOfContours, 0)
            self.assertLess(arrow.xMin, 200)
            self.assertGreater(arrow.xMax, 800)

            original_square = module.read_manifest(manifest_path)["square"]
            (icons / "Home.svg").write_text(
                """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                    <defs><path id="home" d="M2 10 10 2l8 8v10H2z"/></defs>
                    <use href="#home" transform="translate(2 2)"/>
                </svg>""",
                encoding="utf-8",
            )
            module.process(
                input_dir=icons,
                font_path=font_path,
                manifest_path=manifest_path,
                family_name="App Icons",
            )
            self.assertEqual(
                original_square,
                module.read_manifest(manifest_path)["square"],
            )
            self.assertEqual(0xE002, module.read_manifest(manifest_path)["home"])
            expected_font = font_path.read_bytes()

            module.process(
                input_dir=icons,
                font_path=font_path,
                manifest_path=manifest_path,
                family_name="App Icons",
                check=True,
            )
            font_path.write_bytes(expected_font + b"stale")
            with self.assertRaisesRegex(RuntimeError, "outputs are stale"):
                module.process(
                    input_dir=icons,
                    font_path=font_path,
                    manifest_path=manifest_path,
                    family_name="App Icons",
                    check=True,
                )
            module.process(
                input_dir=icons,
                font_path=font_path,
                manifest_path=manifest_path,
                family_name="App Icons",
            )
            self.assertEqual(expected_font, font_path.read_bytes())

    def test_refuses_collisions_removals_and_unrepresentable_paint(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            icons = root / "icons"
            icons.mkdir()
            self.assertEqual(
                0xE006,
                module.allocate_codepoints(
                    {"new": icons / "new.svg", "old": icons / "old.svg"},
                    {"old": 0xE005},
                    module.PRIVATE_USE_START,
                )["new"],
            )
            (icons / "foo-bar.svg").write_text("<svg/>", encoding="utf-8")
            (icons / "foo_bar.svg").write_text("<svg/>", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "same icon name"):
                module.collect_svg_sources(icons)

            (icons / "foo_bar.svg").unlink()
            (icons / "foo-bar.svg").write_text(
                """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                    <defs><linearGradient id="g"><stop/><stop offset="1"/></linearGradient></defs>
                    <path fill="url(#g)" d="M0 0h10v10H0z"/>
                </svg>""",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "must not overwrite"):
                module.process(
                    input_dir=icons,
                    font_path=icons / "foo-bar.svg",
                    manifest_path=root / "icons.codepoints",
                    family_name="Test",
                )
            with self.assertRaisesRegex(ValueError, "gradients and patterns"):
                module.build_font(
                    {"foo_bar": icons / "foo-bar.svg"},
                    {"foo_bar": 0xE000},
                    "Test",
                )

            (icons / "foo-bar.svg").write_text(
                """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                    <path fill="transparent" d="M0 0h10v10H0z"/>
                </svg>""",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "alpha paint"):
                module.build_font(
                    {"foo_bar": icons / "foo-bar.svg"},
                    {"foo_bar": 0xE000},
                    "Test",
                )

            manifest = root / "icons.codepoints"
            manifest.write_text("foo_bar e000\nremoved e001\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "refusing to delete"):
                module.allocate_codepoints(
                    module.collect_svg_sources(icons),
                    module.read_manifest(manifest),
                    module.PRIVATE_USE_START,
                )


if __name__ == "__main__":
    unittest.main()
