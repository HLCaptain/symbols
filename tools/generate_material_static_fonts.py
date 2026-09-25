#!/usr/bin/env python3
"""Generate deterministic static instances from variable symbol fonts.

With no custom input, this updates the API-21-compatible Material Symbols
regular fonts. Consumer builds never require Python or FontTools.
"""

from __future__ import annotations

import argparse
import hashlib
import math
import re
import sys
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from typing import Mapping, Sequence

try:
    import fontTools
    from fontTools.ttLib import TTFont
    from fontTools.varLib.instancer import instantiateVariableFont

    FONTTOOLS_IMPORT_ERROR: ImportError | None = None
except ImportError as error:
    fontTools = None  # type: ignore[assignment]
    TTFont = None  # type: ignore[assignment,misc]
    instantiateVariableFont = None  # type: ignore[assignment]
    FONTTOOLS_IMPORT_ERROR = error


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
EXPECTED_FONTTOOLS_VERSION = "4.66.0"
DEFAULT_AXES = {
    "FILL": 0.0,
    "GRAD": 0.0,
    "opsz": 24.0,
    "wght": 400.0,
}
VARIABLE_TABLES = frozenset({"avar", "fvar", "gvar", "HVAR", "MVAR", "VVAR"})


@dataclass(frozen=True)
class StaticFontSpec:
    style: str
    input_path: Path
    output_path: Path


FONT_SPECS = tuple(
    StaticFontSpec(
        style=style,
        input_path=Path(
            f"fonts/material/{style}/composeResources/font/"
            f"material_symbols_{style}_variable.ttf"
        ),
        output_path=Path(
            f"fonts/material/{style}-static/composeResources/font/"
            f"material_symbols_{style}_regular.ttf"
        ),
    )
    for style in ("outlined", "rounded", "sharp")
)


def _require_fonttools() -> None:
    if FONTTOOLS_IMPORT_ERROR is not None:
        raise RuntimeError(
            "FontTools is required. Install "
            "tools/requirements-font-verification.txt in an isolated environment."
        ) from FONTTOOLS_IMPORT_ERROR
    if fontTools.version != EXPECTED_FONTTOOLS_VERSION:
        raise RuntimeError(
            f"Expected FontTools {EXPECTED_FONTTOOLS_VERSION}, "
            f"found {fontTools.version}."
        )


def _postscript_name(family_name: str) -> str:
    name = re.sub(r"[^A-Za-z0-9]+", "-", family_name).strip("-")
    if not name:
        raise ValueError("family name must contain an ASCII letter or digit")
    return name[:55].rstrip("-")


def _rename_font(font: TTFont, family_name: str) -> None:
    family_name = family_name.strip()
    if not family_name or any(not character.isprintable() for character in family_name):
        raise ValueError("family name must be nonempty and printable")
    postscript_name = _postscript_name(family_name)
    replacements = {
        1: family_name,
        3: f"1.000;symbols;{postscript_name}-Regular",
        4: f"{family_name} Regular",
        6: f"{postscript_name}-Regular",
        16: family_name,
    }
    names = font["name"]
    for record in list(names.names):
        value = replacements.get(record.nameID)
        if value is not None:
            names.setName(
                value,
                record.nameID,
                record.platformID,
                record.platEncID,
                record.langID,
            )


def instantiate_static_font(
    input_path: Path,
    axes: Mapping[str, float] = DEFAULT_AXES,
    *,
    family_name: str | None = None,
) -> bytes:
    """Return a deterministic static instance at one fully resolved axis point."""

    _require_fonttools()
    source = TTFont(input_path, recalcTimestamp=False)
    try:
        if "fvar" not in source:
            raise ValueError(f"Input is not a variable font: {input_path}")
        available_axes = {
            axis.axisTag: (axis.minValue, axis.defaultValue, axis.maxValue)
            for axis in source["fvar"].axes
        }
        unknown_axes = sorted(axes.keys() - available_axes.keys())
        if unknown_axes:
            raise ValueError(
                f"{input_path} does not define axes: {', '.join(unknown_axes)}"
            )
        resolved_axes = {
            tag: axes.get(tag, default)
            for tag, (_, default, _) in available_axes.items()
        }
        for tag, value in resolved_axes.items():
            minimum, _, maximum = available_axes[tag]
            if not math.isfinite(value) or not minimum <= value <= maximum:
                raise ValueError(
                    f"{input_path} axis {tag} cannot represent {value}; "
                    f"range is {minimum}..{maximum}"
                )

        static_font = instantiateVariableFont(
            source,
            resolved_axes,
            inplace=False,
            optimize=True,
        )
        remaining_tables = VARIABLE_TABLES.intersection(static_font.keys())
        if remaining_tables:
            raise AssertionError(
                "Static instancing retained variable tables: "
                + ", ".join(sorted(remaining_tables))
            )
        if family_name is not None:
            _rename_font(static_font, family_name)

        output = BytesIO()
        static_font.recalcTimestamp = False
        static_font.save(output, reorderTables=True)
        return output.getvalue()
    finally:
        source.close()


def sha256(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def parse_axis(value: str) -> tuple[str, float]:
    tag, separator, raw_value = value.partition("=")
    if (
        separator != "="
        or len(tag) != 4
        or not tag.isascii()
        or not tag.isprintable()
    ):
        raise argparse.ArgumentTypeError("axis must be TAG=VALUE with a four-character tag")
    try:
        coordinate = float(raw_value)
    except ValueError as error:
        raise argparse.ArgumentTypeError(f"invalid axis value: {raw_value}") from error
    if not math.isfinite(coordinate):
        raise argparse.ArgumentTypeError("axis value must be finite")
    return tag, coordinate


def process_spec(spec: StaticFontSpec, *, check: bool) -> tuple[int, str]:
    input_path = REPOSITORY_ROOT / spec.input_path
    output_path = REPOSITORY_ROOT / spec.output_path
    generated = instantiate_static_font(input_path)

    if check:
        if not output_path.is_file():
            raise RuntimeError(f"Missing generated static font: {spec.output_path}")
        actual = output_path.read_bytes()
        if actual != generated:
            raise RuntimeError(
                f"Generated static font is stale: {spec.output_path}\n"
                f"expected SHA-256 {sha256(generated)}\n"
                f"actual SHA-256   {sha256(actual)}"
            )
    else:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_bytes(generated)

    return len(generated), sha256(generated)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail when a checked-in static font differs from regenerated bytes.",
    )
    parser.add_argument(
        "--style",
        choices=tuple(spec.style for spec in FONT_SPECS),
        action="append",
        help="Generate only this style. Repeat for multiple styles.",
    )
    parser.add_argument("--input", type=Path, help="Custom variable font input.")
    parser.add_argument("--output", type=Path, help="Custom regular font output.")
    parser.add_argument(
        "--axis",
        action="append",
        default=[],
        type=parse_axis,
        metavar="TAG=VALUE",
        help="Custom axis override; unspecified axes use their font defaults.",
    )
    parser.add_argument(
        "--family-name",
        help="Rename a custom derivative, including its PostScript names.",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    custom_mode = any(
        value is not None
        for value in (args.input, args.output, args.family_name)
    ) or bool(args.axis)

    if custom_mode:
        try:
            if args.input is None or args.output is None:
                raise ValueError("--input and --output are both required for a custom font")
            if args.style:
                raise ValueError("--style cannot be combined with a custom font")
            if args.input.resolve() == args.output.resolve():
                raise ValueError("--output must not overwrite --input")
            axes = dict(args.axis)
            if len(axes) != len(args.axis):
                raise ValueError("each --axis tag may be supplied only once")
            generated = instantiate_static_font(
                args.input,
                axes,
                family_name=args.family_name,
            )
            if args.check:
                if not args.output.is_file() or args.output.read_bytes() != generated:
                    raise RuntimeError(f"Generated static font is stale: {args.output}")
            else:
                args.output.parent.mkdir(parents=True, exist_ok=True)
                args.output.write_bytes(generated)
            verb = "Verified" if args.check else "Generated"
            print(
                f"{verb} custom static font: {len(generated):,} bytes, "
                f"SHA-256 {sha256(generated)}"
            )
        except (AssertionError, OSError, RuntimeError, ValueError) as error:
            print(f"error: {error}", file=sys.stderr)
            return 1
        return 0

    selected_styles = set(args.style or ())
    specs = tuple(
        spec
        for spec in FONT_SPECS
        if not selected_styles or spec.style in selected_styles
    )

    try:
        for spec in specs:
            byte_length, digest = process_spec(spec, check=args.check)
            verb = "Verified" if args.check else "Generated"
            print(
                f"{verb} {spec.style} static font: "
                f"{byte_length:,} bytes, SHA-256 {digest}"
            )
    except (AssertionError, OSError, RuntimeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
