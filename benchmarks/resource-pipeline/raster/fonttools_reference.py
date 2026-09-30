#!/usr/bin/env python3
"""Create an independent filled-outline reference from the pinned static font."""

import argparse
import hashlib
import json
from pathlib import Path
import re
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

import fontTools
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--font", type=Path, required=True)
    parser.add_argument("--codepoints", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    aliases = {}
    for row in args.codepoints.read_text().splitlines():
        if not row.strip() or row.startswith("#"):
            continue
        if not re.fullmatch(r"[a-z0-9]+(?:_[a-z0-9]+)*\s+[0-9a-fA-F]{1,6}", row):
            raise ValueError(f"Invalid catalog entry: {row!r}")
        name, codepoint = row.split()
        aliases.setdefault(int(codepoint, 16), []).append(name)
    if not aliases:
        raise ValueError("Empty glyph catalog")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with TTFont(args.font) as font, ZipFile(args.output, "w") as archive:
        if "fvar" in font:
            raise ValueError("Use the pinned static TTF, not an unspecified variable instance")
        units = font["head"].unitsPerEm
        scale = 24 / units
        glyphs, cmap = font.getGlyphSet(), font.getBestCmap()

        def write(name, text):
            entry = ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.compress_type = ZIP_DEFLATED
            archive.writestr(entry, text)

        for codepoint, names in sorted(aliases.items()):
            pen = SVGPathPen(glyphs)
            glyphs[cmap[codepoint]].draw(TransformPen(pen, (scale, 0, 0, -scale, 0, 24)))
            path = pen.getCommands()
            write(
                f"res/drawable/material_symbols_outlined_{min(names)}_u{codepoint:x}.xml",
                '<vector xmlns:android="http://schemas.android.com/apk/res/android" '
                'android:viewportWidth="24" android:viewportHeight="24">'
                f'<path android:fillColor="#FF000000" android:pathData="{path}"/></vector>',
            )
        metadata = {
            "fonttools": fontTools.__version__,
            "font_sha256": hashlib.sha256(args.font.read_bytes()).hexdigest(),
            "codepoints_sha256": hashlib.sha256(args.codepoints.read_bytes()).hexdigest(),
            "unique_glyphs": len(aliases),
            "units_per_em": units,
            "transform": [scale, 0, 0, -scale, 0, 24],
        }
        write("reference.json", json.dumps(metadata, indent=2) + "\n")
    print(json.dumps(metadata))


if __name__ == "__main__":
    main()
