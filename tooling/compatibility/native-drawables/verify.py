#!/usr/bin/env python3
"""Verify that twelve explicitly selected native drawable AARs shrink to three used resources."""

import argparse
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("aapt2", type=Path, help="Android SDK build-tools aapt2 executable")
args = parser.parse_args()
build = Path(__file__).resolve().parent / "build"
used = {
    f"material_symbols_automirrored_{style}_filled_volume_off_ue04f"
    for style in ("outlined", "rounded", "sharp")
}
results = {}
for variant in ("debug", "release"):
    apk, = (build / "outputs/apk" / variant).glob("*.apk")
    dump = subprocess.check_output([args.aapt2, "dump", "resources", apk], text=True)
    names = set(re.findall(r"drawable/(material_symbols_[a-z0-9_]+)", dump))
    assert used <= names, (variant, "used resources removed", used - names)
    if variant == "debug":
        assert len(names) == 45624, ("expected twelve single-family drawable packs", len(names))
    else:
        assert names == used, ("unused resources survived", sorted(names - used))
        configuration = (build / "outputs/mapping/release/configuration.txt").read_text()
        assert "-dontshrink" not in configuration
        assert "-dontoptimize" not in configuration
    (build / (variant + "-resources.txt")).write_text(dump)
    results[variant] = {"drawables": len(names), "apk_bytes": apk.stat().st_size}

report = json.dumps(results, indent=2) + "\n"
(build / "shrink-results.json").write_text(report)
print(report, end="")
