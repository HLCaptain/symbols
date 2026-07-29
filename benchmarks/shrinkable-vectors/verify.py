#!/usr/bin/env python3
"""Analyze the two benchmark APKs and verify typed-vector shrinking.

The script uses only the Python standard library. It prints stable JSON so a
run can be archived or compared without requiring Android Studio tooling.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
import zipfile
from pathlib import Path
from typing import Any


CHECK_CLASS = (
    b"io/github/hlcaptain/symbols/material/outlined/vectors/"
    b"OutlinedVectorE5CA"
)
CHECK_CLASS_NAME = CHECK_CLASS.decode().replace("/", ".")
UNUSED_HOME_CLASS = (
    b"io/github/hlcaptain/symbols/material/outlined/vectors/"
    b"OutlinedVectorE9B2"
)
UNUSED_HOME_CLASS_NAME = UNUSED_HOME_CLASS.decode().replace("/", ".")
UNUSED_RESOURCE = b"unused_resource_marker"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def dex_metrics(data: bytes) -> dict[str, int]:
    if len(data) < 112 or data[:4] not in (b"dex\n", b"cdex"):
        raise ValueError("entry is not a supported DEX file")
    return {
        "bytes": len(data),
        "string_ids": struct.unpack_from("<I", data, 56)[0],
        "type_ids": struct.unpack_from("<I", data, 64)[0],
        "field_ids": struct.unpack_from("<I", data, 80)[0],
        "method_ids": struct.unpack_from("<I", data, 88)[0],
        "class_defs": struct.unpack_from("<I", data, 96)[0],
        "class_defs_table_bytes": struct.unpack_from("<I", data, 96)[0] * 32,
    }


def sum_entries(entries: list[zipfile.ZipInfo]) -> dict[str, int]:
    return {
        "compressed_bytes": sum(entry.compress_size for entry in entries),
        "uncompressed_bytes": sum(entry.file_size for entry in entries),
        "entries": len(entries),
    }


def analyze_apk(path: Path) -> tuple[dict[str, Any], bytes, bytes]:
    with zipfile.ZipFile(path) as apk:
        entries = apk.infolist()
        dex_entries = sorted(
            (
                entry
                for entry in entries
                if entry.filename.startswith("classes")
                and entry.filename.endswith(".dex")
            ),
            key=lambda entry: entry.filename,
        )
        dex_contents = [apk.read(entry) for entry in dex_entries]
        dexes = [dex_metrics(content) for content in dex_contents]

        res_entries = [
            entry
            for entry in entries
            if entry.filename.startswith("res/")
            or entry.filename == "resources.arsc"
        ]
        class_totals = {
            key: sum(dex[key] for dex in dexes)
            for key in (
                "bytes",
                "string_ids",
                "type_ids",
                "field_ids",
                "method_ids",
                "class_defs",
                "class_defs_table_bytes",
            )
        }
        return (
            {
                "path": path.name,
                "sha256": sha256(path),
                "apk_bytes": path.stat().st_size,
                "zip_entries": len(entries),
                "dex_zip": sum_entries(dex_entries),
                "dex": class_totals,
                "dex_files": dexes,
                "android_resources_zip": sum_entries(res_entries),
                "resources_arsc_zip": sum_entries(
                    [
                        entry
                        for entry in entries
                        if entry.filename == "resources.arsc"
                    ]
                ),
                "native_libraries_zip": sum_entries(
                    [
                        entry
                        for entry in entries
                        if entry.filename.startswith("lib/")
                    ]
                ),
            },
            b"".join(dex_contents),
            b"".join(apk.read(entry) for entry in res_entries),
        )


def find_apk(build_dir: Path, variant: str) -> Path:
    candidates = sorted((build_dir / "outputs" / "apk" / variant).glob("*.apk"))
    if len(candidates) != 1:
        raise FileNotFoundError(
            f"expected exactly one {variant} APK, found: {candidates}"
        )
    return candidates[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--build-dir",
        type=Path,
        default=Path(__file__).resolve().parent / "build",
    )
    args = parser.parse_args()

    unshrunk_path = find_apk(args.build_dir, "unshrunk")
    shrunk_path = find_apk(args.build_dir, "shrunk")
    unshrunk, unshrunk_dex, unshrunk_resources = analyze_apk(unshrunk_path)
    shrunk, shrunk_dex, shrunk_resources = analyze_apk(shrunk_path)
    mapping_dir = args.build_dir / "outputs" / "mapping" / "shrunk"
    mapping = (mapping_dir / "mapping.txt").read_text()
    usage = (mapping_dir / "usage.txt").read_text()
    resource_report = (mapping_dir / "resources.txt").read_text()

    evidence = {
        "unshrunk_contains_check_backing_class": CHECK_CLASS in unshrunk_dex,
        "unshrunk_contains_unused_home_backing_class": (
            UNUSED_HOME_CLASS in unshrunk_dex
        ),
        "shrunk_contains_check_backing_class": CHECK_CLASS in shrunk_dex,
        "shrunk_contains_unused_home_backing_class": (
            UNUSED_HOME_CLASS in shrunk_dex
        ),
        "unshrunk_contains_unused_resource_name": (
            UNUSED_RESOURCE in unshrunk_resources
        ),
        "shrunk_contains_unused_resource_name": (
            UNUSED_RESOURCE in shrunk_resources
        ),
        "r8_mapping_retains_check_backing_class": (
            f"{CHECK_CLASS_NAME} -> " in mapping
        ),
        "r8_mapping_retains_unused_home_backing_class": (
            f"{UNUSED_HOME_CLASS_NAME} -> " in mapping
        ),
        "r8_usage_reports_unused_home_backing_class": (
            UNUSED_HOME_CLASS_NAME in usage
        ),
        "resource_shrinker_marks_unused_marker_unreachable": (
            "string/unused_resource_marker : reachable=false"
            in resource_report
        ),
    }
    expected = {
        "unshrunk_contains_check_backing_class": True,
        "unshrunk_contains_unused_home_backing_class": True,
        "shrunk_contains_check_backing_class": True,
        "shrunk_contains_unused_home_backing_class": False,
        "unshrunk_contains_unused_resource_name": True,
        "shrunk_contains_unused_resource_name": False,
        "r8_mapping_retains_check_backing_class": True,
        "r8_mapping_retains_unused_home_backing_class": False,
        "r8_usage_reports_unused_home_backing_class": True,
        "resource_shrinker_marks_unused_marker_unreachable": True,
    }

    result = {
        "schema_version": 1,
        "variants": {"unshrunk": unshrunk, "shrunk": shrunk},
        "delta": {
            "apk_bytes": shrunk["apk_bytes"] - unshrunk["apk_bytes"],
            "apk_percent": round(
                100 * (shrunk["apk_bytes"] - unshrunk["apk_bytes"])
                / unshrunk["apk_bytes"],
                4,
            ),
            "dex_uncompressed_bytes": (
                shrunk["dex"]["bytes"] - unshrunk["dex"]["bytes"]
            ),
            "dex_class_defs": (
                shrunk["dex"]["class_defs"] - unshrunk["dex"]["class_defs"]
            ),
            "resource_uncompressed_bytes": (
                shrunk["android_resources_zip"]["uncompressed_bytes"]
                - unshrunk["android_resources_zip"]["uncompressed_bytes"]
            ),
        },
        "evidence": evidence,
        "verified": evidence == expected,
    }
    print(json.dumps(result, indent=2, sort_keys=True))
    if evidence != expected:
        print(
            "shrink verification failed; expected " + json.dumps(expected),
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
