#!/usr/bin/env python3
"""Report retained icon resources in one generated usage fixture's release APK."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import zipfile


def analyze(fixture, apk_path, aapt2=None):
    manifest = json.loads((fixture / "fixture.json").read_text())
    selected = {item["resource"] for item in manifest["selected"]}
    all_resources = set(manifest["all_resources"])
    result = {"backend": manifest["backend"], "access": manifest["access"], "count": manifest["count"], "apk": str(apk_path), "apk_bytes": apk_path.stat().st_size}
    with zipfile.ZipFile(apk_path) as apk:
        result["zip_entries"] = len(apk.infolist())
        result["payload_bytes"] = sum(entry.compress_size for entry in apk.infolist())
        result["dex_bytes"] = sum(entry.file_size for entry in apk.infolist() if re.fullmatch(r"classes(?:\d+)?\.dex", entry.filename))
        if manifest["backend"] == "vectors":
            dex = b"".join(apk.read(entry) for entry in apk.infolist() if re.fullmatch(r"classes(?:\d+)?\.dex", entry.filename))
            points = {int(value, 16) for value in re.findall(rb"MaterialSymbolsOutlined\.U\+([0-9A-F]+)", dex)}
            expected = {item["codepoint"] for item in manifest["selected"]}
            result.update(vector_name_markers=len(points), selected_vector_name_markers=len(points & expected), unused_vector_name_markers=len(points - expected), missing_vector_name_markers=sorted(expected-points), interpretation="Name markers are diagnostic only; R8 may inline or remove names. Verify rendering on a device.")
            return result
        if manifest["backend"] == "compose":
            retained = {Path(entry.filename).stem for entry in apk.infolist() if entry.filename.startswith("assets/composeResources/") and "/drawable/material_symbols_outlined_" in entry.filename}
        else:
            if not aapt2:
                raise ValueError("Native resource names require --aapt2; R8 may shorten XML file paths")
            dump = subprocess.check_output([str(aapt2), "dump", "resources", str(apk_path)], text=True)
            retained = set(re.findall(r"\bdrawable/(material_symbols_outlined_[a-z0-9_]+)\b", dump))
    missing = sorted(selected - retained)
    result.update(retained_icon_resources=len(retained & all_resources), retained_selected=len(retained & selected), retained_unused=len((retained & all_resources)-selected), missing_selected=missing)
    if missing:
        raise ValueError(f"Used icon resources missing from {apk_path}: {missing}")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--aapt2", type=Path)
    args = parser.parse_args()
    print(json.dumps(analyze(args.fixture.resolve(), args.apk.resolve(), args.aapt2), indent=2))


if __name__ == "__main__":
    main()
