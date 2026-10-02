from __future__ import annotations

from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET


TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
import generate_material_gallery as gallery


class MaterialGalleryGeneratorTest(unittest.TestCase):
    def test_page_reference_uses_identical_paths_and_transforms_in_svg_and_xml(self):
        paths = tuple(f"M{i} 0L{i + 1} 1Z" for i in range(gallery.PAGE_SIZE))
        svg = ET.fromstring(gallery.render_reference(paths, svg=True))
        xml = ET.fromstring(gallery.render_reference(paths, svg=False))
        android = "{http://schemas.android.com/apk/res/android}"
        for index, (svg_group, xml_group) in enumerate(zip(svg, xml)):
            x = index % gallery.COLUMNS * gallery.CELL + gallery.ICON_X
            y = index // gallery.COLUMNS * gallery.CELL + gallery.ICON_Y
            self.assertEqual(f"translate({x} {y})", svg_group.attrib["transform"])
            self.assertEqual(str(x), xml_group.attrib[android + "translateX"])
            self.assertEqual(str(y), xml_group.attrib[android + "translateY"])
            self.assertEqual(paths[index], svg_group[0].attrib["d"])
            self.assertEqual(paths[index], xml_group[0].attrib[android + "pathData"])
        self.assertEqual(gallery.PAGE_SIZE, len(svg))
        self.assertEqual(gallery.PAGE_SIZE, len(xml))

    def test_last_page_has_no_fabricated_empty_icons(self):
        paths = ("M-0.3 0H24.35Z",)
        svg = ET.fromstring(gallery.render_reference(paths, svg=True))
        self.assertEqual(1, len(svg))
        self.assertEqual(paths[0], svg[0][0].attrib["d"])

    def test_filled_selector_covers_unique_codepoints_without_alias_duplication(self):
        source = gallery.render_filled_selector((0xE87E,), {0xE87E: ("favorite", "favorite_border")})
        self.assertIn("0 -> Symbols.Material.Rounded.Filled.Favorite", source)
        self.assertNotIn("Filled.FavoriteBorder", source)
        self.assertNotIn("addPathNodes", source)


if __name__ == "__main__":
    unittest.main()
