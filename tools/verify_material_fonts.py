#!/usr/bin/env python3
"""Verify the pinned Google Material Symbols manifest and variable fonts.

This is a maintainer/CI check, not part of a consumer build. It intentionally
pins the current upstream snapshot so an asset update requires an explicit
review of provenance, manifest shape, font metadata, and expected hashes.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import defaultdict
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from typing import Any, Mapping, Sequence


try:
    import fontTools
    from fontTools.pens.boundsPen import BoundsPen
    from fontTools.pens.recordingPen import RecordingPen
    from fontTools.ttLib import TTFont
    from fontTools.ttLib.sfnt import calcChecksum

    FONTTOOLS_IMPORT_ERROR: ImportError | None = None
except ImportError as error:
    fontTools = None  # type: ignore[assignment]
    BoundsPen = None  # type: ignore[assignment,misc]
    RecordingPen = None  # type: ignore[assignment,misc]
    TTFont = None  # type: ignore[assignment,misc]
    calcChecksum = None  # type: ignore[assignment]
    FONTTOOLS_IMPORT_ERROR = error


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = Path("fonts/material/MaterialSymbols.codepoints")
EXPECTED_MANIFEST_SHA256 = (
    "3e5293b71c38cb0487ab5fc5de293956aab4f96f445ae482849cce3df345aec9"
)
EXPECTED_UPSTREAM_REVISION = "bb04090f930e272697f2a1f0d7b352d92dfeee43"
EXPECTED_UPSTREAM_COMMIT_DATE = "2025-09-19"
EXPECTED_FONT_VERSION = "2.874"
EXPECTED_FONT_REVISION = 188350 / 65536
EXPECTED_SFNT_CHECKSUM = 0xB1B0AFBA
EXPECTED_SFNT_SIGNATURE = b"\x00\x01\x00\x00"
EXPECTED_GLYPH_COUNT = 6301
EXPECTED_CMAP_ENTRY_COUNT = 4174
EXPECTED_PUA_CMAP_ENTRY_COUNT = 4107
EXPECTED_NAMED_INSTANCE_COUNT = 7

CODEPOINT_LINE = re.compile(
    r"^(?P<name>[a-z0-9]+(?:_[a-z0-9]+)*)[ \t]+"
    r"(?P<code_point>[0-9a-fA-F]{1,6})[ \t]*$"
)
UNICODE_MAX = 0x10FFFF
SURROGATE_START = 0xD800
SURROGATE_END = 0xDFFF

EXPECTED_MANIFEST_FACTS = {
    "entry_count": 4102,
    "unique_name_count": 4102,
    "unique_code_point_count": 3802,
    "alias_code_point_count": 216,
    "alias_extra_name_count": 300,
    "largest_alias_group_size": 6,
    "digit_leading_name_count": 65,
    "minimum_code_point": 0xE003,
    "maximum_code_point": 0xF8FF,
}

EXPECTED_LARGEST_ALIAS_GROUPS = {
    0xF09A: (
        "grade",
        "star",
        "star_border",
        "star_border_purple500",
        "star_outline",
        "star_purple500",
    ),
    0xF2CD: (
        "app_promo",
        "install_mobile",
        "mobile_arrow_down",
        "security_update",
        "system_security_update",
        "system_update",
    ),
}

EXPECTED_AXES = (
    ("FILL", 0.0, 0.0, 1.0),
    ("GRAD", -50.0, 0.0, 200.0),
    ("opsz", 20.0, 24.0, 48.0),
    ("wght", 100.0, 400.0, 700.0),
)

EXPECTED_TABLES = frozenset(
    {
        "OS/2",
        "GSUB",
        "HVAR",
        "STAT",
        "avar",
        "cmap",
        "fvar",
        "gasp",
        "glyf",
        "gvar",
        "head",
        "hhea",
        "hmtx",
        "loca",
        "maxp",
        "name",
        "post",
        "prep",
    }
)

OUTLINE_SAMPLE_NAMES = (
    "home",
    "favorite",
    "star",
    "settings",
    "check_circle",
)


@dataclass(frozen=True)
class ManifestEntry:
    name: str
    code_point: int
    source_line: int


@dataclass(frozen=True)
class FontSpec:
    style: str
    path: Path
    sha256: str
    byte_length: int
    family: str
    postscript_name: str


FONT_SPECS = (
    FontSpec(
        style="outlined",
        path=Path(
            "fonts/material/outlined/composeResources/font/"
            "material_symbols_outlined_variable.ttf"
        ),
        sha256="fb0d00bfa03507fe6712348604aea33741b41f97643e109ad4e582b1a15865b9",
        byte_length=10178540,
        family="Material Symbols Outlined",
        postscript_name="MaterialSymbolsOutlined-Regular",
    ),
    FontSpec(
        style="rounded",
        path=Path(
            "fonts/material/rounded/composeResources/font/"
            "material_symbols_rounded_variable.ttf"
        ),
        sha256="d719f22fdee27e344b07e46e6fa8b50b1fce3cfcb03d4a84f03fafbf0812fc22",
        byte_length=14586584,
        family="Material Symbols Rounded",
        postscript_name="MaterialSymbolsRounded-Regular",
    ),
    FontSpec(
        style="sharp",
        path=Path(
            "fonts/material/sharp/composeResources/font/"
            "material_symbols_sharp_variable.ttf"
        ),
        sha256="e76edbb72b8cbca2380c507cd17ea5cb4023cf3e01a9dcfeddd670da8c495f37",
        byte_length=8434940,
        family="Material Symbols Sharp",
        postscript_name="MaterialSymbolsSharp-Regular",
    ),
)


class ManifestError(ValueError):
    """Raised when the canonical codepoint manifest is malformed."""


def is_unicode_scalar(code_point: int) -> bool:
    return (
        0 <= code_point <= UNICODE_MAX
        and not SURROGATE_START <= code_point <= SURROGATE_END
    )


def kotlin_identifier(name: str) -> str:
    identifier = "".join(
        part[0].upper() + part[1:]
        for part in name.split("_")
    )
    return f"_{identifier}" if identifier[0].isdigit() else identifier


def parse_manifest_text(
    content: str,
    source: str = str(MANIFEST_PATH),
) -> tuple[ManifestEntry, ...]:
    """Parse the manifest using the same contract as the catalog generator."""

    entries: list[ManifestEntry] = []
    first_line_by_name: dict[str, int] = {}
    first_name_by_identifier: dict[str, str] = {}

    for line_number, line in enumerate(content.splitlines(), start=1):
        if not line.strip():
            continue

        match = CODEPOINT_LINE.fullmatch(line)
        if match is None:
            raise ManifestError(
                f"{source}:{line_number}: expected "
                "'<snake_case_name> <hex_code_point>'"
            )

        name = match.group("name")
        previous_line = first_line_by_name.get(name)
        if previous_line is not None:
            raise ManifestError(
                f"{source}:{line_number}: duplicate name {name!r}; "
                f"first declared on line {previous_line}"
            )

        code_point = int(match.group("code_point"), 16)
        if not is_unicode_scalar(code_point):
            raise ManifestError(
                f"{source}:{line_number}: U+{code_point:04X} "
                "is not a Unicode scalar value"
            )

        identifier = kotlin_identifier(name)
        previous_name = first_name_by_identifier.get(identifier)
        if previous_name is not None:
            raise ManifestError(
                "Kotlin identifier collision: "
                f"{previous_name!r} and {name!r} both become {identifier!r}"
            )

        first_line_by_name[name] = line_number
        first_name_by_identifier[identifier] = name
        entries.append(ManifestEntry(name, code_point, line_number))

    if not entries:
        raise ManifestError(f"Codepoints file is empty: {source}")

    entries.sort(key=lambda entry: entry.name)
    return tuple(entries)


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def format_code_point(code_point: int) -> str:
    width = max(4, len(f"{code_point:X}"))
    return f"U+{code_point:0{width}X}"


def manifest_facts(entries: Sequence[ManifestEntry]) -> dict[str, Any]:
    names_by_code_point: dict[int, list[str]] = defaultdict(list)
    for entry in entries:
        names_by_code_point[entry.code_point].append(entry.name)

    alias_groups = {
        code_point: tuple(sorted(names))
        for code_point, names in names_by_code_point.items()
        if len(names) > 1
    }
    largest_size = max(
        (len(names) for names in alias_groups.values()),
        default=1,
    )
    largest_groups = [
        {
            "code_point": format_code_point(code_point),
            "names": list(names),
        }
        for code_point, names in sorted(alias_groups.items())
        if len(names) == largest_size
    ]
    code_points = tuple(names_by_code_point)
    return {
        "entry_count": len(entries),
        "unique_name_count": len({entry.name for entry in entries}),
        "unique_code_point_count": len(code_points),
        "alias_code_point_count": len(alias_groups),
        "alias_extra_name_count": sum(
            len(names) - 1
            for names in alias_groups.values()
        ),
        "largest_alias_group_size": largest_size,
        "largest_alias_groups": largest_groups,
        "digit_leading_name_count": sum(
            entry.name[0].isdigit()
            for entry in entries
        ),
        "minimum_code_point": min(code_points),
        "maximum_code_point": max(code_points),
    }


def _append_mismatch(
    errors: list[str],
    scope: str,
    label: str,
    actual: Any,
    expected: Any,
) -> None:
    if actual != expected:
        errors.append(
            f"{scope}: {label} is {actual!r}; expected {expected!r}"
        )


def verify_manifest(
    root: Path,
    errors: list[str],
) -> tuple[tuple[ManifestEntry, ...], dict[str, Any]]:
    path = root / MANIFEST_PATH
    result: dict[str, Any] = {
        "path": MANIFEST_PATH.as_posix(),
        "expected_sha256": EXPECTED_MANIFEST_SHA256,
    }

    try:
        content = path.read_bytes()
    except OSError as error:
        errors.append(f"manifest: cannot read {MANIFEST_PATH}: {error}")
        result.update({"ok": False, "sha256": None})
        return (), result

    digest = sha256_bytes(content)
    result["sha256"] = digest
    result["byte_length"] = len(content)
    if digest != EXPECTED_MANIFEST_SHA256:
        errors.append(
            "manifest: SHA-256 does not match the pinned Material Symbols "
            f"{EXPECTED_FONT_VERSION} snapshot ({digest} != "
            f"{EXPECTED_MANIFEST_SHA256}); an intentional upstream update "
            "must refresh verifier expectations and THIRD_PARTY_NOTICES.md"
        )

    try:
        text = content.decode("utf-8")
    except UnicodeDecodeError as error:
        errors.append(f"manifest: {MANIFEST_PATH} is not valid UTF-8: {error}")
        result["ok"] = False
        return (), result

    try:
        entries = parse_manifest_text(text, MANIFEST_PATH.as_posix())
    except ManifestError as error:
        errors.append(f"manifest: {error}")
        result["ok"] = False
        return (), result

    facts = manifest_facts(entries)
    json_facts = dict(facts)
    json_facts["minimum_code_point"] = format_code_point(
        facts["minimum_code_point"]
    )
    json_facts["maximum_code_point"] = format_code_point(
        facts["maximum_code_point"]
    )
    result.update(json_facts)

    for label, expected in EXPECTED_MANIFEST_FACTS.items():
        _append_mismatch(
            errors,
            "manifest",
            label.replace("_", " "),
            facts[label],
            expected,
        )

    largest_groups = {
        int(group["code_point"][2:], 16): tuple(group["names"])
        for group in facts["largest_alias_groups"]
    }
    if largest_groups != EXPECTED_LARGEST_ALIAS_GROUPS:
        errors.append(
            "manifest: largest allowed alias groups changed; an intentional "
            "manifest update must refresh the pinned alias expectations"
        )

    result["ok"] = not any(
        error.startswith("manifest:")
        for error in errors
    )
    return entries, result


def _name_values(font: Any, name_id: int) -> list[str]:
    values: set[str] = set()
    for record in font["name"].names:
        if record.nameID != name_id:
            continue
        try:
            values.add(record.toUnicode())
        except UnicodeDecodeError:
            continue
    return sorted(values)


def _json_number(value: float) -> int | float:
    rounded = round(float(value), 6)
    return int(rounded) if rounded.is_integer() else rounded


def _json_bounds(bounds: Any) -> list[int | float] | None:
    if bounds is None:
        return None
    return [_json_number(value) for value in bounds]


def _draw_glyph(glyph_set: Any, glyph_name: str) -> tuple[Any, Any]:
    recording_pen = RecordingPen()
    glyph_set[glyph_name].draw(recording_pen)
    bounds_pen = BoundsPen(glyph_set)
    glyph_set[glyph_name].draw(bounds_pen)
    return tuple(recording_pen.value), bounds_pen.bounds


def _has_practical_outline(recording: Sequence[Any], bounds: Any) -> bool:
    drawing_operations = {
        "moveTo",
        "lineTo",
        "qCurveTo",
        "curveTo",
        "addComponent",
    }
    return bounds is not None and any(
        operation in drawing_operations
        for operation, _ in recording
    )


def _verify_table_checksums(
    font: Any,
    raw_font: bytes,
) -> dict[str, Any]:
    invalid_tables: list[str] = []
    for tag, directory_entry in font.reader.tables.items():
        table_data = font.reader[tag]
        if str(tag) == "head":
            table_data = (
                table_data[:8]
                + b"\0\0\0\0"
                + table_data[12:]
            )
        if calcChecksum(table_data) != directory_entry.checkSum:
            invalid_tables.append(str(tag))

    master_checksum = calcChecksum(raw_font)
    return {
        "master": f"0x{master_checksum:08X}",
        "master_valid": master_checksum == EXPECTED_SFNT_CHECKSUM,
        "table_count": len(font.reader.tables),
        "table_checksums_valid": not invalid_tables,
        "invalid_tables": sorted(invalid_tables),
    }


def _verify_axes(
    font: Any,
    scope: str,
    errors: list[str],
) -> list[dict[str, Any]]:
    if "fvar" not in font:
        errors.append(f"{scope}: missing fvar table")
        return []

    axes = [
        (
            axis.axisTag,
            float(axis.minValue),
            float(axis.defaultValue),
            float(axis.maxValue),
        )
        for axis in font["fvar"].axes
    ]
    if tuple(axes) != EXPECTED_AXES:
        errors.append(
            f"{scope}: variable axes are {axes!r}; expected "
            f"{list(EXPECTED_AXES)!r}"
        )

    for axis in font["fvar"].axes:
        if axis.flags != 0:
            errors.append(
                f"{scope}: axis {axis.axisTag} flags are {axis.flags}; expected 0"
            )

    return [
        {
            "tag": tag,
            "minimum": _json_number(minimum),
            "default": _json_number(default),
            "maximum": _json_number(maximum),
        }
        for tag, minimum, default, maximum in axes
    ]


def _verify_outlines(
    font: Any,
    cmap: Mapping[int, str],
    entries: Sequence[ManifestEntry],
    scope: str,
    errors: list[str],
) -> dict[str, Any]:
    code_point_by_name = {
        entry.name: entry.code_point
        for entry in entries
    }
    default_location = {
        tag: default
        for tag, _, default, _ in EXPECTED_AXES
    }
    full_fill_location = dict(default_location)
    full_fill_location["FILL"] = 1.0

    try:
        implicit_default = font.getGlyphSet()
        explicit_default = font.getGlyphSet(location=default_location)
        full_fill = font.getGlyphSet(location=full_fill_location)
    except Exception as error:
        errors.append(f"{scope}: cannot instantiate variable glyph sets: {error}")
        return {"sample_count": 0, "fill_changed_count": 0, "samples": []}

    samples: list[dict[str, Any]] = []
    fill_changed_count = 0
    for name in OUTLINE_SAMPLE_NAMES:
        code_point = code_point_by_name.get(name)
        if code_point is None:
            errors.append(f"{scope}: outline sample {name!r} is absent from manifest")
            continue

        glyph_name = cmap.get(code_point)
        if glyph_name is None:
            errors.append(
                f"{scope}: outline sample {name!r} "
                f"({format_code_point(code_point)}) is absent from cmap"
            )
            continue

        try:
            implicit_recording, implicit_bounds = _draw_glyph(
                implicit_default,
                glyph_name,
            )
            default_recording, default_bounds = _draw_glyph(
                explicit_default,
                glyph_name,
            )
            fill_recording, fill_bounds = _draw_glyph(
                full_fill,
                glyph_name,
            )
        except Exception as error:
            errors.append(
                f"{scope}: cannot draw outline sample {name!r}: {error}"
            )
            continue

        implicit_matches_default = (
            implicit_recording == default_recording
            and implicit_bounds == default_bounds
        )
        default_nonempty = _has_practical_outline(
            default_recording,
            default_bounds,
        )
        fill_nonempty = _has_practical_outline(fill_recording, fill_bounds)
        fill_changes = default_recording != fill_recording

        if not implicit_matches_default:
            errors.append(
                f"{scope}: implicit and explicit default outlines differ "
                f"for {name!r}"
            )
        if not default_nonempty:
            errors.append(f"{scope}: default outline is empty for {name!r}")
        if not fill_nonempty:
            errors.append(f"{scope}: full-FILL outline is empty for {name!r}")
        if not fill_changes:
            errors.append(
                f"{scope}: FILL=0 and FILL=1 outlines are identical for "
                f"responsive sample {name!r}"
            )
        else:
            fill_changed_count += 1

        samples.append(
            {
                "name": name,
                "code_point": format_code_point(code_point),
                "glyph_name": glyph_name,
                "implicit_default_matches": implicit_matches_default,
                "default_nonempty": default_nonempty,
                "full_fill_nonempty": fill_nonempty,
                "fill_changes_outline": fill_changes,
                "default_command_count": len(default_recording),
                "full_fill_command_count": len(fill_recording),
                "default_bounds": _json_bounds(default_bounds),
                "full_fill_bounds": _json_bounds(fill_bounds),
            }
        )

    return {
        "sample_count": len(samples),
        "fill_changed_count": fill_changed_count,
        "samples": samples,
    }


def _style_ttf_paths(root: Path, style: str) -> list[str]:
    style_root = root / "fonts/material" / style
    if not style_root.is_dir():
        return []
    return sorted(
        path.relative_to(root).as_posix()
        for path in style_root.rglob("*")
        if path.is_file() and path.suffix.lower() == ".ttf"
    )


def verify_font(
    root: Path,
    spec: FontSpec,
    entries: Sequence[ManifestEntry],
    errors: list[str],
) -> dict[str, Any]:
    scope = f"font[{spec.style}]"
    path = root / spec.path
    style_fonts = _style_ttf_paths(root, spec.style)
    result: dict[str, Any] = {
        "style": spec.style,
        "path": spec.path.as_posix(),
        "expected_sha256": spec.sha256,
        "ttf_files_in_style": style_fonts,
        "single_ttf_in_style": style_fonts == [spec.path.as_posix()],
    }

    if not result["single_ttf_in_style"]:
        errors.append(
            f"{scope}: expected exactly one TTF at {spec.path.as_posix()}, "
            f"found {style_fonts!r}"
        )

    try:
        raw_font = path.read_bytes()
    except OSError as error:
        errors.append(f"{scope}: cannot read {spec.path.as_posix()}: {error}")
        result.update({"ok": False, "sha256": None})
        return result

    digest = sha256_bytes(raw_font)
    result.update(
        {
            "sha256": digest,
            "byte_length": len(raw_font),
            "sfnt_signature": raw_font[:4].hex(),
        }
    )
    if digest != spec.sha256:
        errors.append(
            f"{scope}: SHA-256 does not match the pinned Material Symbols "
            f"{EXPECTED_FONT_VERSION} snapshot ({digest} != {spec.sha256}); "
            "an intentional upstream update must refresh verifier "
            "expectations and THIRD_PARTY_NOTICES.md"
        )
    _append_mismatch(
        errors,
        scope,
        "byte length",
        len(raw_font),
        spec.byte_length,
    )
    if raw_font[:4] != EXPECTED_SFNT_SIGNATURE:
        errors.append(
            f"{scope}: expected a single TrueType sfnt, signature is "
            f"{raw_font[:4].hex()!r}"
        )

    if FONTTOOLS_IMPORT_ERROR is not None:
        errors.append(
            f"{scope}: FontTools is unavailable: {FONTTOOLS_IMPORT_ERROR}"
        )
        result["ok"] = False
        return result

    font: Any = None
    try:
        font = TTFont(
            BytesIO(raw_font),
            lazy=False,
            recalcBBoxes=False,
            recalcTimestamp=False,
            checkChecksums=0,
        )

        actual_tables = sorted(str(tag) for tag in font.reader.tables)
        result["tables"] = actual_tables
        if set(actual_tables) != EXPECTED_TABLES:
            errors.append(
                f"{scope}: sfnt tables are {actual_tables!r}; expected "
                f"{sorted(EXPECTED_TABLES)!r}"
            )

        checksums = _verify_table_checksums(font, raw_font)
        result["checksums"] = checksums
        if not checksums["master_valid"]:
            errors.append(
                f"{scope}: whole-font checksum is {checksums['master']}; "
                f"expected 0x{EXPECTED_SFNT_CHECKSUM:08X}"
            )
        if not checksums["table_checksums_valid"]:
            errors.append(
                f"{scope}: invalid table checksums: "
                f"{checksums['invalid_tables']!r}"
            )

        for tag in actual_tables:
            try:
                font[tag]
            except Exception as error:
                errors.append(f"{scope}: cannot decode {tag} table: {error}")

        revision = float(font["head"].fontRevision)
        family_names = _name_values(font, 1)
        subfamily_names = _name_values(font, 2)
        version_names = _name_values(font, 5)
        postscript_names = _name_values(font, 6)
        result.update(
            {
                "font_revision": revision,
                "version_names": version_names,
                "family_names": family_names,
                "subfamily_names": subfamily_names,
                "postscript_names": postscript_names,
                "units_per_em": int(font["head"].unitsPerEm),
                "glyph_count": int(font["maxp"].numGlyphs),
                "named_instance_count": len(font["fvar"].instances),
            }
        )
        _append_mismatch(
            errors,
            scope,
            "font revision",
            revision,
            EXPECTED_FONT_REVISION,
        )
        _append_mismatch(
            errors,
            scope,
            "version names",
            version_names,
            [f"Version {EXPECTED_FONT_VERSION}"],
        )
        _append_mismatch(
            errors,
            scope,
            "family names",
            family_names,
            [spec.family],
        )
        _append_mismatch(
            errors,
            scope,
            "subfamily names",
            subfamily_names,
            ["Regular"],
        )
        _append_mismatch(
            errors,
            scope,
            "PostScript names",
            postscript_names,
            [spec.postscript_name],
        )
        _append_mismatch(
            errors,
            scope,
            "units per em",
            int(font["head"].unitsPerEm),
            960,
        )
        _append_mismatch(
            errors,
            scope,
            "glyph count",
            int(font["maxp"].numGlyphs),
            EXPECTED_GLYPH_COUNT,
        )
        _append_mismatch(
            errors,
            scope,
            "named instance count",
            len(font["fvar"].instances),
            EXPECTED_NAMED_INSTANCE_COUNT,
        )

        result["axes"] = _verify_axes(font, scope, errors)

        cmap = font.getBestCmap()
        if cmap is None:
            errors.append(f"{scope}: no Unicode cmap is available")
            cmap = {}

        manifest_code_points = sorted({entry.code_point for entry in entries})
        missing_code_points = [
            code_point
            for code_point in manifest_code_points
            if code_point not in cmap
        ]
        pua_entries = sum(
            0xE000 <= code_point <= 0xF8FF
            for code_point in cmap
        )
        result["cmap"] = {
            "entry_count": len(cmap),
            "pua_entry_count": pua_entries,
            "manifest_code_point_count": len(manifest_code_points),
            "mapped_manifest_code_point_count": (
                len(manifest_code_points) - len(missing_code_points)
            ),
            "missing_manifest_code_points": [
                format_code_point(code_point)
                for code_point in missing_code_points
            ],
        }
        _append_mismatch(
            errors,
            scope,
            "cmap entry count",
            len(cmap),
            EXPECTED_CMAP_ENTRY_COUNT,
        )
        _append_mismatch(
            errors,
            scope,
            "PUA cmap entry count",
            pua_entries,
            EXPECTED_PUA_CMAP_ENTRY_COUNT,
        )
        if missing_code_points:
            preview = ", ".join(
                format_code_point(code_point)
                for code_point in missing_code_points[:20]
            )
            suffix = (
                ""
                if len(missing_code_points) <= 20
                else f", and {len(missing_code_points) - 20} more"
            )
            errors.append(
                f"{scope}: manifest codepoints missing from cmap: "
                f"{preview}{suffix}"
            )

        if entries:
            result["outlines"] = _verify_outlines(
                font,
                cmap,
                entries,
                scope,
                errors,
            )
        else:
            result["outlines"] = {
                "sample_count": 0,
                "fill_changed_count": 0,
                "samples": [],
            }
            errors.append(
                f"{scope}: cannot verify outline samples without a valid manifest"
            )
    except Exception as error:
        errors.append(
            f"{scope}: FontTools could not verify the font: "
            f"{type(error).__name__}: {error}"
        )
    finally:
        if font is not None:
            font.close()

    result["ok"] = not any(error.startswith(f"{scope}:") for error in errors)
    return result


def verify_repository(root: Path = REPOSITORY_ROOT) -> dict[str, Any]:
    root = root.resolve()
    errors: list[str] = []
    entries, manifest = verify_manifest(root, errors)
    fonts = [
        verify_font(root, spec, entries, errors)
        for spec in FONT_SPECS
    ]
    report = {
        "schema_version": 1,
        "ok": not errors,
        "snapshot": {
            "upstream": "Google Material Symbols",
            "upstream_revision": EXPECTED_UPSTREAM_REVISION,
            "upstream_commit_date": EXPECTED_UPSTREAM_COMMIT_DATE,
            "font_version": EXPECTED_FONT_VERSION,
        },
        "fonttools_version": (
            getattr(fontTools, "__version__", None)
            if fontTools is not None
            else None
        ),
        "manifest": manifest,
        "fonts": fonts,
        "errors": errors,
    }
    return report


def render_human(report: Mapping[str, Any]) -> str:
    status = "PASS" if report["ok"] else "FAIL"
    lines = [f"Material Symbols font verification: {status}"]
    snapshot = report["snapshot"]
    lines.append(
        "Upstream: "
        f"google/material-design-icons@{snapshot['upstream_revision']} "
        f"({snapshot['upstream_commit_date']}), "
        f"font version {snapshot['font_version']}"
    )

    manifest = report["manifest"]
    lines.append(
        "Manifest: "
        f"{manifest.get('entry_count', '?')} names, "
        f"{manifest.get('unique_code_point_count', '?')} codepoints, "
        f"{manifest.get('alias_code_point_count', '?')} alias groups, "
        f"SHA-256 {manifest.get('sha256', 'unavailable')}"
    )
    for font in report["fonts"]:
        checksums = font.get("checksums", {})
        outlines = font.get("outlines", {})
        lines.append(
            f"{font['style'].capitalize()}: "
            f"{font.get('glyph_count', '?')} glyphs, "
            f"{font.get('cmap', {}).get('mapped_manifest_code_point_count', '?')} "
            "manifest codepoints mapped, "
            f"{outlines.get('fill_changed_count', '?')}/"
            f"{outlines.get('sample_count', '?')} FILL samples changed, "
            f"checksums "
            f"{'valid' if checksums.get('master_valid') and checksums.get('table_checksums_valid') else 'invalid'}, "
            f"SHA-256 {font.get('sha256', 'unavailable')}"
        )

    errors = report["errors"]
    if errors:
        lines.append("Errors:")
        lines.extend(f"  - {error}" for error in errors)
    return "\n".join(lines)


def create_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=Path,
        default=REPOSITORY_ROOT,
        help="Repository root containing fonts/material (default: script parent)",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit a deterministic machine-readable JSON report.",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    arguments = create_parser().parse_args(argv)
    report = verify_repository(arguments.root)
    if arguments.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print(render_human(report))
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
