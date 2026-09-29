#!/usr/bin/env python3
"""Build a deterministic TrueType icon font and codepoint manifest from SVGs."""

from __future__ import annotations

import argparse
import math
import os
import re
import stat
import sys
import tempfile
from importlib.metadata import PackageNotFoundError, version
from io import BytesIO
from pathlib import Path
from typing import Sequence

try:
    import fontTools
    from fontTools.fontBuilder import FontBuilder
    from fontTools.pens.cu2quPen import Cu2QuPen
    from fontTools.pens.ttGlyphPen import TTGlyphPen
    from fontTools.svgLib.path import SVGPath
    from fontTools.ttLib import TTFont
    from picosvg.svg import SVG

    DEPENDENCY_IMPORT_ERROR: ImportError | None = None
except ImportError as error:
    fontTools = None  # type: ignore[assignment]
    FontBuilder = None  # type: ignore[assignment,misc]
    Cu2QuPen = None  # type: ignore[assignment,misc]
    TTGlyphPen = None  # type: ignore[assignment,misc]
    SVGPath = None  # type: ignore[assignment,misc]
    TTFont = None  # type: ignore[assignment,misc]
    SVG = None  # type: ignore[assignment,misc]
    DEPENDENCY_IMPORT_ERROR = error


EXPECTED_VERSIONS = {
    "fonttools": "4.66.0",
    "picosvg": "0.23.0",
    "skia-pathops": "0.9.2",
}
UNITS_PER_EM = 1000
PRIVATE_USE_START = 0xE000
PRIVATE_USE_END = 0xF8FF
MAC_TIMESTAMP_1970 = 2_082_844_800
XLINK_HREF = "{http://www.w3.org/1999/xlink}href"
MANIFEST_LINE = re.compile(
    r"^(?P<name>[a-z0-9]+(?:_[a-z0-9]+)*)[ \t]+"
    r"(?P<codepoint>[0-9a-fA-F]{1,6})[ \t]*$"
)


def _require_dependencies() -> None:
    if DEPENDENCY_IMPORT_ERROR is not None:
        raise RuntimeError(
            "SVG font dependencies are required. Install "
            "tools/requirements-font-verification.txt in an isolated environment."
        ) from DEPENDENCY_IMPORT_ERROR

    actual_versions = {"fonttools": fontTools.version}
    try:
        actual_versions.update(
            {name: version(name) for name in ("picosvg", "skia-pathops")}
        )
    except PackageNotFoundError as error:
        raise RuntimeError(f"Missing required package: {error.name}") from error

    mismatches = [
        f"{name} {actual_versions[name]} (expected {expected})"
        for name, expected in EXPECTED_VERSIONS.items()
        if actual_versions[name] != expected
    ]
    if mismatches:
        raise RuntimeError("Dependency version mismatch: " + ", ".join(mismatches))


def normalize_icon_name(filename: str) -> str:
    stem = Path(filename).stem
    stem = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1_\2", stem)
    stem = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", stem)
    name = re.sub(r"[^A-Za-z0-9]+", "_", stem).strip("_").lower()
    if not name:
        raise ValueError(f"SVG filename has no usable icon name: {filename}")
    return name


def collect_svg_sources(input_dir: Path) -> dict[str, Path]:
    if not input_dir.is_dir():
        raise ValueError(f"SVG input directory does not exist: {input_dir}")

    sources: dict[str, Path] = {}
    for path in sorted(input_dir.iterdir(), key=lambda item: item.name.casefold()):
        if not path.is_file() or path.suffix.lower() != ".svg":
            continue
        name = normalize_icon_name(path.name)
        previous = sources.get(name)
        if previous is not None:
            raise ValueError(
                f"SVG filenames normalize to the same icon name '{name}': "
                f"{previous.name}, {path.name}"
            )
        sources[name] = path

    if not sources:
        raise ValueError(f"SVG input directory contains no .svg files: {input_dir}")
    return sources


def read_manifest(path: Path) -> dict[str, int]:
    if not path.exists():
        return {}
    if not path.is_file():
        raise ValueError(f"Manifest is not a file: {path}")

    assignments: dict[str, int] = {}
    codepoint_names: dict[int, str] = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        match = MANIFEST_LINE.fullmatch(line)
        if match is None:
            raise ValueError(
                f"{path}:{line_number}: expected "
                "'<snake_case_name> <hex_code_point>'"
            )
        name = match.group("name")
        codepoint = int(match.group("codepoint"), 16)
        if name in assignments:
            raise ValueError(f"{path}:{line_number}: duplicate icon name '{name}'")
        if not PRIVATE_USE_START <= codepoint <= PRIVATE_USE_END:
            raise ValueError(
                f"{path}:{line_number}: U+{codepoint:04X} is outside the BMP "
                "Private Use Area U+E000..U+F8FF"
            )
        previous = codepoint_names.get(codepoint)
        if previous is not None:
            raise ValueError(
                f"{path}:{line_number}: U+{codepoint:04X} is already assigned "
                f"to '{previous}'"
            )
        assignments[name] = codepoint
        codepoint_names[codepoint] = name
    return assignments


def allocate_codepoints(
    sources: dict[str, Path],
    existing: dict[str, int],
    start_codepoint: int,
) -> dict[str, int]:
    if not PRIVATE_USE_START <= start_codepoint <= PRIVATE_USE_END:
        raise ValueError("start code point must be in U+E000..U+F8FF")
    missing_sources = sorted(existing.keys() - sources.keys())
    if missing_sources:
        raise ValueError(
            "Manifest entries have no matching SVG; refusing to delete released "
            "assignments: " + ", ".join(missing_sources)
        )

    assignments = dict(existing)
    used = set(assignments.values())
    candidate = max(start_codepoint, max(used, default=start_codepoint - 1) + 1)
    for name in sorted(sources.keys() - assignments.keys()):
        while candidate in used and candidate <= PRIVATE_USE_END:
            candidate += 1
        if candidate > PRIVATE_USE_END:
            # ponytail: BMP PUA keeps Android/web consumers simple; add an explicit
            # supplementary-PUA mode only when a real set exhausts these 6,400 slots.
            raise ValueError("BMP Private Use Area is exhausted")
        assignments[name] = candidate
        used.add(candidate)
        candidate += 1
    return assignments


def render_manifest(assignments: dict[str, int]) -> bytes:
    return "".join(
        f"{name} {codepoint:x}\n" for name, codepoint in sorted(assignments.items())
    ).encode("utf-8")


def _override_stroke_width(document: SVG, stroke_width: float | None) -> None:
    if stroke_width is None:
        return
    if not math.isfinite(stroke_width) or stroke_width <= 0:
        raise ValueError("--stroke-width must be finite and positive")
    if document.xpath(".//svg:style"):
        raise ValueError("--stroke-width does not support <style> elements")

    document.apply_style_attributes(inplace=True)
    document.resolve_use(inplace=True)
    rendered_width = str(stroke_width)
    stroked_shapes = 0
    for context in document.depth_first(resolve_clip_paths=False):
        if not context.is_shape() or str(context.shape().stroke).lower() == "none":
            continue
        context.element.attrib["stroke-width"] = rendered_width
        stroked_shapes += 1
    if not stroked_shapes:
        raise ValueError("--stroke-width requested but SVG contains no stroked shapes")


def _svg_glyph(path: Path, stroke_width: float | None = None):
    try:
        document = SVG.fromstring(path.read_bytes())
        for use in document.xpath(".//svg:use"):
            href = use.attrib.pop("href", None)
            if href is not None:
                existing_href = use.attrib.get(XLINK_HREF)
                if existing_href is not None and existing_href != href:
                    raise ValueError("<use> has conflicting href attributes")
                use.attrib[XLINK_HREF] = href
        _override_stroke_width(document, stroke_width)
        normalized = document.topicosvg(ndigits=6)
        view_box = normalized.view_box()
        if view_box is None:
            raise ValueError("missing viewBox or numeric width/height")
        if not all(
            math.isfinite(value)
            for value in (view_box.x, view_box.y, view_box.w, view_box.h)
        ) or view_box.w <= 0 or view_box.h <= 0:
            raise ValueError("viewBox must have finite positive width and height")

        normalized.clip_to_viewbox(inplace=True)
        normalized.round_floats(6, inplace=True)
        shapes = normalized.shapes()
        if not shapes:
            raise ValueError("contains no painted shapes")
        paints = set()
        for shape in shapes:
            if shape.opacity != 1.0:
                raise ValueError("opacity cannot be represented by a monochrome font")
            paint = str(shape.fill).lower()
            if paint.startswith("url("):
                raise ValueError("gradients and patterns cannot be represented by a font")
            if (
                paint == "transparent"
                or paint.startswith(("rgba(", "hsla("))
                or "/" in paint
                or (
                    paint.startswith("#")
                    and len(paint) == 5
                    and paint[-1] != "f"
                )
                or (
                    paint.startswith("#")
                    and len(paint) == 9
                    and paint[-2:] != "ff"
                )
            ):
                raise ValueError("alpha paint cannot be represented by a font")
            if shape.stroke != "none":
                raise ValueError("stroke normalization did not produce filled outlines")
            paints.add(paint)
        if len(paints) > 1:
            raise ValueError("multiple paint colors cannot be represented by a font")
    except Exception as error:
        raise ValueError(f"{path}: {error}") from error

    scale = min(UNITS_PER_EM / view_box.w, UNITS_PER_EM / view_box.h)
    pad_x = (UNITS_PER_EM - view_box.w * scale) / 2
    pad_y = (UNITS_PER_EM - view_box.h * scale) / 2
    transform = (
        scale,
        0,
        0,
        -scale,
        pad_x - view_box.x * scale,
        UNITS_PER_EM - pad_y + view_box.y * scale,
    )

    pen = TTGlyphPen(None)
    try:
        SVGPath.fromstring(normalized.tostring(), transform=transform).draw(
            Cu2QuPen(pen, max_err=1.0)
        )
        glyph = pen.glyph()
    except Exception as error:
        raise ValueError(f"{path}: cannot convert normalized paths: {error}") from error
    if glyph.numberOfContours <= 0:
        raise ValueError(f"{path}: normalized outline is empty")
    return glyph


def _postscript_name(family_name: str) -> str:
    ascii_family = re.sub(r"[^A-Za-z0-9]+", "-", family_name).strip("-")
    if not ascii_family:
        raise ValueError("--family-name must contain an ASCII letter or digit")
    return f"{ascii_family}-Regular"[:63].rstrip("-")


def build_font(
    sources: dict[str, Path],
    assignments: dict[str, int],
    family_name: str,
    stroke_width: float | None = None,
) -> bytes:
    family_name = family_name.strip()
    if not family_name or any(not character.isprintable() for character in family_name):
        raise ValueError("--family-name must be a nonempty printable name")
    ps_name = _postscript_name(family_name)

    by_codepoint = sorted((codepoint, name) for name, codepoint in assignments.items())
    glyph_names = {codepoint: f"uni{codepoint:04X}" for codepoint, _ in by_codepoint}
    glyphs = {".notdef": TTGlyphPen(None).glyph()}
    for codepoint, name in by_codepoint:
        glyphs[glyph_names[codepoint]] = _svg_glyph(
            sources[name],
            stroke_width,
        )

    builder = FontBuilder(UNITS_PER_EM, isTTF=True)
    builder.updateHead(
        created=MAC_TIMESTAMP_1970,
        modified=MAC_TIMESTAMP_1970,
        fontRevision=1.0,
    )
    builder.setupGlyphOrder([".notdef", *[glyph_names[cp] for cp, _ in by_codepoint]])
    builder.setupCharacterMap({cp: glyph_names[cp] for cp, _ in by_codepoint})
    builder.setupGlyf(glyphs)
    glyf = builder.font["glyf"]
    metrics = {".notdef": (UNITS_PER_EM, 0)}
    metrics.update(
        {
            glyph_names[codepoint]: (
                UNITS_PER_EM,
                glyf[glyph_names[codepoint]].xMin,
            )
            for codepoint, _ in by_codepoint
        }
    )
    builder.setupHorizontalMetrics(metrics)
    builder.setupHorizontalHeader(ascent=UNITS_PER_EM, descent=0, lineGap=0)
    builder.setupNameTable(
        {
            "familyName": family_name,
            "styleName": "Regular",
            "uniqueFontIdentifier": f"1.000;symbols;{ps_name}",
            "fullName": f"{family_name} Regular",
            "psName": ps_name,
            "version": "Version 1.000",
        }
    )
    builder.setupOS2(
        sTypoAscender=UNITS_PER_EM,
        sTypoDescender=0,
        sTypoLineGap=0,
        usWinAscent=UNITS_PER_EM,
        usWinDescent=0,
        fsSelection=0x40,
    )
    builder.setupPost(keepGlyphNames=True, isFixedPitch=1)

    output = BytesIO()
    builder.font.recalcTimestamp = False
    builder.font.save(output, reorderTables=True)
    return output.getvalue()


def _stage_output(path: Path, content: bytes) -> Path:
    staged: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            dir=path.parent,
            prefix=f".{path.name}.",
            delete=False,
        ) as output:
            staged = Path(output.name)
            output.write(content)
        mode = stat.S_IMODE(path.stat().st_mode) if path.exists() else 0o644
        os.chmod(staged, mode)
        return staged
    except Exception:
        if staged is not None:
            staged.unlink(missing_ok=True)
        raise


def process(
    *,
    input_dir: Path,
    font_path: Path,
    manifest_path: Path,
    family_name: str,
    start_codepoint: int = PRIVATE_USE_START,
    stroke_width: float | None = None,
    check: bool = False,
) -> int:
    _require_dependencies()
    if font_path.resolve() == manifest_path.resolve():
        raise ValueError("--font and --manifest must be different files")
    sources = collect_svg_sources(input_dir)
    source_paths = {path.resolve() for path in sources.values()}
    for label, output_path in (("--font", font_path), ("--manifest", manifest_path)):
        if output_path.resolve() in source_paths:
            raise ValueError(f"{label} must not overwrite an input SVG: {output_path}")
    assignments = allocate_codepoints(
        sources,
        read_manifest(manifest_path),
        start_codepoint,
    )
    manifest = render_manifest(assignments)
    font = build_font(sources, assignments, family_name, stroke_width)

    if check:
        stale = []
        if not manifest_path.is_file() or manifest_path.read_bytes() != manifest:
            stale.append(str(manifest_path))
        if not font_path.is_file() or font_path.read_bytes() != font:
            stale.append(str(font_path))
        if stale:
            raise RuntimeError(
                "Generated SVG icon font outputs are stale: " + ", ".join(stale)
            )
    else:
        manifest_path.parent.mkdir(parents=True, exist_ok=True)
        font_path.parent.mkdir(parents=True, exist_ok=True)
        staged_font: Path | None = None
        staged_manifest: Path | None = None
        try:
            staged_font = _stage_output(font_path, font)
            staged_manifest = _stage_output(manifest_path, manifest)
            # Replacing the font first leaves the old manifest usable if the
            # second atomic replace is interrupted.
            os.replace(staged_font, font_path)
            os.replace(staged_manifest, manifest_path)
        finally:
            if staged_font is not None:
                staged_font.unlink(missing_ok=True)
            if staged_manifest is not None:
                staged_manifest.unlink(missing_ok=True)
    return len(assignments)


def parse_codepoint(value: str) -> int:
    normalized = value.lower().removeprefix("u+").removeprefix("0x")
    try:
        codepoint = int(normalized, 16)
    except ValueError as error:
        raise argparse.ArgumentTypeError(
            f"invalid hexadecimal code point: {value}"
        ) from error
    if not PRIVATE_USE_START <= codepoint <= PRIVATE_USE_END:
        raise argparse.ArgumentTypeError("code point must be in U+E000..U+F8FF")
    return codepoint


def parse_stroke_width(value: str) -> float:
    try:
        stroke_width = float(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError(
            f"stroke width must be a number: {value}"
        ) from error
    if not math.isfinite(stroke_width) or stroke_width <= 0:
        raise argparse.ArgumentTypeError("stroke width must be finite and positive")
    return stroke_width


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input-dir",
        required=True,
        type=Path,
        help="Flat directory of SVG files.",
    )
    parser.add_argument(
        "--font",
        required=True,
        type=Path,
        help="Generated .ttf output.",
    )
    parser.add_argument(
        "--manifest",
        required=True,
        type=Path,
        help="Generated and subsequently preserved .codepoints file.",
    )
    parser.add_argument("--family-name", required=True, help="OpenType family name.")
    parser.add_argument(
        "--stroke-width",
        type=parse_stroke_width,
        help="Override every painted SVG stroke before building a regular font.",
    )
    parser.add_argument(
        "--start-codepoint",
        default=PRIVATE_USE_START,
        type=parse_codepoint,
        help="First BMP Private Use code point for new names (default E000).",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail instead of writing when the font or manifest is stale.",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        count = process(
            input_dir=args.input_dir,
            font_path=args.font,
            manifest_path=args.manifest,
            family_name=args.family_name,
            start_codepoint=args.start_codepoint,
            stroke_width=args.stroke_width,
            check=args.check,
        )
        verb = "Verified" if args.check else "Generated"
        print(f"{verb} {count} SVG glyphs: {args.font}, {args.manifest}")
    except (AssertionError, OSError, RuntimeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
