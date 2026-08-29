#!/usr/bin/env python3
"""Analyze copied sample APK payloads with only the Python standard library."""

from __future__ import annotations

import hashlib
import json
import re
import statistics
import struct
import zipfile
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[2]
REPORT_ROOT = ROOT / "build/reports/apk-study"
APK_DIRECTORY = REPORT_ROOT / "apks"
VARIANTS = ("release", "shrunk")
DEX_NAME = re.compile(r"classes(?:\d+)?\.dex")
FONT_EXTENSIONS = {".otf", ".ttc", ".ttf"}
GENERATED_OUTPUTS = {
    "custom-static": (ROOT / "samples/custom-static/build/generated/symbolFonts",),
    "custom-variable": (ROOT / "samples/custom-variable/build/generated/symbolFonts",),
    "image-vector-migration": (
        ROOT / "samples/image-vector-migration/build/generated/symbolFonts",
        ROOT / "samples/image-vector-migration/build/generated/res",
    ),
    "material-drawables-outlined": (
        ROOT / "symbols/material-drawables-outlined/build/generated",
    ),
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def payload(entries: Iterable[zipfile.ZipInfo]) -> dict[str, int]:
    selected = list(entries)
    return {
        "files": len(selected),
        "compressed_bytes": sum(entry.compress_size for entry in selected),
        "uncompressed_bytes": sum(entry.file_size for entry in selected),
    }


def dex_metrics(entry: zipfile.ZipInfo, data: bytes) -> dict[str, int | str]:
    if len(data) < 112 or data[:4] not in (b"dex\n", b"cdex"):
        raise ValueError(f"{entry.filename} is not a supported DEX file")
    return {
        "path": entry.filename,
        "compressed_bytes": entry.compress_size,
        "bytes": len(data),
        "string_ids": struct.unpack_from("<I", data, 56)[0],
        "type_ids": struct.unpack_from("<I", data, 64)[0],
        "proto_ids": struct.unpack_from("<I", data, 72)[0],
        "field_ids": struct.unpack_from("<I", data, 80)[0],
        "method_ids": struct.unpack_from("<I", data, 88)[0],
        "class_defs": struct.unpack_from("<I", data, 96)[0],
        "data_bytes": struct.unpack_from("<I", data, 104)[0],
    }


def entry_metrics(entry: zipfile.ZipInfo) -> dict[str, int | str]:
    return {
        "path": entry.filename,
        "compressed_bytes": entry.compress_size,
        "uncompressed_bytes": entry.file_size,
        "compression": "stored" if entry.compress_type == zipfile.ZIP_STORED else "deflated",
    }


def profile_and_variant(path: Path) -> tuple[str, str]:
    for variant in VARIANTS:
        suffix = f"-{variant}"
        if path.stem.endswith(suffix):
            return path.stem[: -len(suffix)], variant
    raise ValueError(f"APK name must end in -release or -shrunk: {path.name}")


def category(entry: zipfile.ZipInfo) -> str:
    name = entry.filename
    suffix = Path(name).suffix.lower()
    if DEX_NAME.fullmatch(name):
        return "dex"
    if suffix in FONT_EXTENSIONS:
        return "font_assets"
    if name.startswith("assets/composeResources/"):
        return "compose_assets"
    if name == "resources.arsc" or name.startswith("res/"):
        return "android_resources"
    if name.startswith("lib/"):
        return "native_libraries"
    if name.startswith("assets/"):
        return "other_assets"
    return "other"


def analyze(path: Path) -> dict[str, Any]:
    profile, variant = profile_and_variant(path)
    with zipfile.ZipFile(path) as apk:
        entries = apk.infolist()
        entries_by_category: dict[str, list[zipfile.ZipInfo]] = defaultdict(list)
        for entry in entries:
            entries_by_category[category(entry)].append(entry)

        dex_files = [
            dex_metrics(entry, apk.read(entry))
            for entry in entries_by_category["dex"]
        ]
        dex_totals = {
            key: sum(int(dex[key]) for dex in dex_files)
            for key in (
                "bytes",
                "compressed_bytes",
                "string_ids",
                "type_ids",
                "proto_ids",
                "field_ids",
                "method_ids",
                "class_defs",
                "data_bytes",
            )
        }
        compose_namespaces: dict[str, list[zipfile.ZipInfo]] = defaultdict(list)
        for entry in (
            entries_by_category["font_assets"] + entries_by_category["compose_assets"]
        ):
            parts = entry.filename.split("/")
            namespace = parts[2] if len(parts) > 2 else "unknown"
            compose_namespaces[namespace].append(entry)
        native_abis: dict[str, list[zipfile.ZipInfo]] = defaultdict(list)
        for entry in entries_by_category["native_libraries"]:
            parts = entry.filename.split("/")
            native_abis[parts[1] if len(parts) > 2 else "unknown"].append(entry)

        return {
            "profile": profile,
            "variant": variant,
            "path": path.relative_to(ROOT).as_posix(),
            "sha256": sha256(path),
            "apk_bytes": path.stat().st_size,
            "zip_payload": payload(entries),
            "categories": {
                name: payload(entries_by_category[name])
                for name in sorted(entries_by_category)
            },
            "dex": {"totals": dex_totals, "files": dex_files},
            "fonts": [entry_metrics(entry) for entry in entries_by_category["font_assets"]],
            "compose_namespaces": {
                name: payload(namespace_entries)
                for name, namespace_entries in sorted(compose_namespaces.items())
            },
            "native_abis": {
                name: payload(abi_entries)
                for name, abi_entries in sorted(native_abis.items())
            },
            "resources_arsc": payload(
                entry for entry in entries if entry.filename == "resources.arsc"
            ),
            "android_res": payload(
                entry for entry in entries if entry.filename.startswith("res/")
            ),
        }


def delta(right: dict[str, int], left: dict[str, int], key: str) -> int:
    return int(right.get(key, 0)) - int(left.get(key, 0))


def comparisons(apks: list[dict[str, Any]]) -> dict[str, Any]:
    indexed = {(apk["profile"], apk["variant"]): apk for apk in apks}
    shrinking: dict[str, Any] = {}
    profile_vs_shell: dict[str, Any] = {}
    for profile in sorted({str(apk["profile"]) for apk in apks}):
        release = indexed.get((profile, "release"))
        shrunk = indexed.get((profile, "shrunk"))
        if release and shrunk:
            shrinking[profile] = {
                "apk_bytes": int(shrunk["apk_bytes"]) - int(release["apk_bytes"]),
                "dex_compressed_bytes": delta(
                    shrunk["dex"]["totals"],
                    release["dex"]["totals"],
                    "compressed_bytes",
                ),
                "android_resources_compressed_bytes": delta(
                    shrunk["categories"].get("android_resources", {}),
                    release["categories"].get("android_resources", {}),
                    "compressed_bytes",
                ),
            }
    for variant in VARIANTS:
        shell = indexed.get(("shell", variant))
        if not shell:
            continue
        profile_vs_shell[variant] = {
            profile: int(apk["apk_bytes"]) - int(shell["apk_bytes"])
            for (profile, candidate_variant), apk in sorted(indexed.items())
            if candidate_variant == variant
            and profile != "shell"
            and not profile.endswith("-compressed-fonts")
        }
    return {"shrinking": shrinking, "profile_vs_shell": profile_vs_shell}


def build_timings() -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    builds: list[dict[str, Any]] = []
    for path in sorted(REPORT_ROOT.glob("builds-*.json")):
        study = json.loads(path.read_text(encoding="utf-8"))
        for build in study["builds"]:
            builds.append({**build, "label": study["label"]})

    grouped: dict[tuple[str, str, str], list[float]] = defaultdict(list)
    for build in builds:
        if not build.get("warmup", False):
            grouped[(build["label"], build["profile"], build["variant"])].append(
                float(build["wall_seconds"])
            )
    medians = [
        {
            "label": label,
            "profile": profile,
            "variant": variant,
            "runs": len(samples),
            "samples_seconds": samples,
            "median_seconds": round(statistics.median(samples), 3),
        }
        for (label, profile, variant), samples in sorted(grouped.items())
    ]
    return builds, medians


def generated_outputs() -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for name, roots in GENERATED_OUTPUTS.items():
        files = sorted(
            path
            for root in roots
            if root.exists()
            for path in root.rglob("*")
            if path.is_file()
        )
        extensions: dict[str, int] = defaultdict(int)
        for path in files:
            extensions[path.suffix.lower() or "(none)"] += 1
        result[name] = {
            "files": len(files),
            "bytes": sum(path.stat().st_size for path in files),
            "extensions": dict(sorted(extensions.items())),
            "roots": [root.relative_to(ROOT).as_posix() for root in roots],
        }
    return result


def markdown(
    apks: list[dict[str, Any]],
    comparison: dict[str, Any],
    timing_medians: list[dict[str, Any]],
    generated: dict[str, dict[str, Any]],
) -> str:
    def category_bytes(apk: dict[str, Any], name: str, kind: str) -> int:
        return int(apk["categories"].get(name, {}).get(kind, 0))

    lines = [
        "# Sample app APK study",
        "",
        "| Profile | Variant | APK bytes | DEX compressed | DEX raw | Fonts | Compose assets | Android resources | Native |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for apk in sorted(apks, key=lambda item: (item["profile"], item["variant"])):
        lines.append(
            "| {profile} | {variant} | {apk_bytes:,} | {dex_compressed:,} | "
            "{dex_raw:,} | {fonts:,} | {compose:,} | {resources:,} | {native:,} |".format(
                profile=apk["profile"],
                variant=apk["variant"],
                apk_bytes=int(apk["apk_bytes"]),
                dex_compressed=int(apk["dex"]["totals"]["compressed_bytes"]),
                dex_raw=int(apk["dex"]["totals"]["bytes"]),
                fonts=category_bytes(apk, "font_assets", "compressed_bytes"),
                compose=category_bytes(apk, "compose_assets", "compressed_bytes"),
                resources=category_bytes(apk, "android_resources", "compressed_bytes"),
                native=category_bytes(apk, "native_libraries", "compressed_bytes"),
            )
        )

    shrinking = comparison["shrinking"]
    if shrinking:
        lines.extend(
            (
                "",
                "## Shrunk minus release",
                "",
                "| Profile | APK bytes | DEX compressed | Android resources compressed |",
                "| --- | ---: | ---: | ---: |",
            )
        )
        for profile, values in sorted(shrinking.items()):
            lines.append(
                f"| {profile} | {values['apk_bytes']:+,} | "
                f"{values['dex_compressed_bytes']:+,} | "
                f"{values['android_resources_compressed_bytes']:+,} |"
            )
    profile_vs_shell = comparison["profile_vs_shell"]
    if profile_vs_shell:
        lines.extend(
            (
                "",
                "## Profile minus shell APK bytes",
                "",
                "| Profile | Release | Shrunk |",
                "| --- | ---: | ---: |",
            )
        )
        profiles = sorted(
            set(profile_vs_shell.get("release", {}))
            | set(profile_vs_shell.get("shrunk", {}))
        )
        for profile in profiles:
            release = profile_vs_shell.get("release", {}).get(profile)
            shrunk = profile_vs_shell.get("shrunk", {}).get(profile)
            lines.append(
                f"| {profile} | "
                f"{release if release is not None else 0:+,} | "
                f"{shrunk if shrunk is not None else 0:+,} |"
            )
    if timing_medians:
        lines.extend(
            (
                "",
                "## Build wall-time medians",
                "",
                "| Study | Profile | Variant | Runs | Median seconds | Samples |",
                "| --- | --- | --- | ---: | ---: | --- |",
            )
        )
        for timing in timing_medians:
            samples = ", ".join(str(value) for value in timing["samples_seconds"])
            lines.append(
                f"| {timing['label']} | {timing['profile']} | {timing['variant']} | "
                f"{timing['runs']} | {timing['median_seconds']:.3f} | {samples} |"
            )
    if generated:
        lines.extend(
            (
                "",
                "## Generated build outputs",
                "",
                "| Producer | Files | Bytes | Extensions |",
                "| --- | ---: | ---: | --- |",
            )
        )
        for name, values in generated.items():
            extensions = ", ".join(
                f"{extension}: {count}"
                for extension, count in values["extensions"].items()
            )
            lines.append(
                f"| {name} | {values['files']} | {values['bytes']:,} | {extensions} |"
            )
    lines.extend(
        (
            "",
            "Exact entry, DEX-header, font, namespace, ABI, and comparison data is in `metrics.json`.",
            "APK bytes include ZIP metadata; category totals are compressed entry payload bytes.",
            "",
        )
    )
    return "\n".join(lines)


def main() -> int:
    paths = sorted(APK_DIRECTORY.glob("*.apk"))
    if not paths:
        raise FileNotFoundError(f"no copied APKs found under {APK_DIRECTORY}")
    apks = [analyze(path) for path in paths]
    comparison = comparisons(apks)
    builds, timing_medians = build_timings()
    generated = generated_outputs()
    result = {
        "schema_version": 1,
        "apks": apks,
        "comparisons": comparison,
        "builds": builds,
        "build_timing_medians": timing_medians,
        "generated_outputs": generated,
    }
    REPORT_ROOT.mkdir(parents=True, exist_ok=True)
    (REPORT_ROOT / "metrics.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    (REPORT_ROOT / "report.md").write_text(
        markdown(apks, comparison, timing_medians, generated),
        encoding="utf-8",
    )
    print(f"Analyzed {len(apks)} APKs under {REPORT_ROOT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
