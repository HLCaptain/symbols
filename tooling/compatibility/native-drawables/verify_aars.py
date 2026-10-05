#!/usr/bin/env python3
"""Check each opt-in native Material family against public 2.1.0 baseline AARs."""

from contextlib import ExitStack
import hashlib
import json
from collections import defaultdict
from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"
BASELINE_SHA256 = {
    "outlined": "e410e6268f158b8f3e7dcaf186165c92ed2ace159ef96c2d3270e17280962e66",
    "rounded": "e346eb40a005a0ed9059f2538599dc8e7daee946480135e20a82f255aad50500",
    "sharp": "67f184bf38a1781fbd488c61a24c47e4d17ce956e0a1e7d5f8ff0ba2bd5f56a3",
}


def verify(style: str, suffixes: set[str]) -> dict:
    baseline = HERE / "build/baseline" / f"symbols-material-drawables-{style}-2.1.0.aar"
    assert hashlib.sha256(baseline.read_bytes()).hexdigest() == BASELINE_SHA256[style], baseline
    prefixes = {
        "": f"material_symbols_{style}_",
        "-filled": f"material_symbols_{style}_filled_",
        "-automirrored": f"material_symbols_automirrored_{style}_",
        "-automirrored-filled": f"material_symbols_automirrored_{style}_filled_",
    }
    results = {}
    with ExitStack() as stack:
        archives = {}
        for variant, prefix in prefixes.items():
            module = f"material-drawables-{style}{variant}"
            directory = ROOT / "symbols" / module
            candidate = directory / f"build/outputs/aar/{module}-release.aar"
            actual = stack.enter_context(ZipFile(candidate))
            archives[variant] = actual
            expected = {f"res/drawable/{prefix}{suffix}" for suffix in suffixes}
            drawables = {name for name in actual.namelist() if name.startswith("res/drawable/") and name.endswith(".xml")}
            assert drawables == expected, f"{module}: missing/extra resources: {sorted(drawables ^ expected)[:10]}"
            names = {
                line.split()[2] for line in actual.read("R.txt").decode().splitlines()
                if line.startswith("int drawable ")
            }
            assert names == {Path(name).stem for name in expected}, f"{module}: R.txt inventory differs"
            assert not any(name.endswith((".ttf", ".otf", ".ttc")) for name in actual.namelist())
            pom = ET.parse(directory / "build/publications/release/pom-default.xml").getroot()
            assert not pom.findall("{*}dependencies/{*}dependency"), f"{module}: native packs must have no automatic dependencies"
            for name in expected:
                vector = ET.fromstring(actual.read(name))
                assert vector.tag == "vector"
                assert vector.attrib[ANDROID + "autoMirrored"] == str("automirrored" in variant).lower()
            results[variant or "ordinary"] = {
                "drawables": len(expected),
                "aar_bytes": candidate.stat().st_size,
                "aar_sha256": hashlib.sha256(candidate.read_bytes()).hexdigest(),
            }
        old = stack.enter_context(ZipFile(baseline))
        expected_old = {f"res/drawable/{prefixes['']}{suffix}" for suffix in suffixes}
        assert {name for name in old.namelist() if name.startswith("res/drawable/") and name.endswith(".xml")} == expected_old
        for suffix in suffixes:
            ordinary = f"res/drawable/{prefixes['']}{suffix}"
            assert archives[""].read(ordinary) == old.read(ordinary), f"Changed existing resource: {ordinary}"
            for plain, mirrored in (("", "-automirrored"), ("-filled", "-automirrored-filled")):
                xml = archives[plain].read(f"res/drawable/{prefixes[plain]}{suffix}")
                assert archives[mirrored].read(f"res/drawable/{prefixes[mirrored]}{suffix}") == xml.replace(
                    b'android:autoMirrored="false"', b'android:autoMirrored="true"'
                ), f"Mirroring changed geometry: {style}{mirrored}/{suffix}"
        for suffix in ("home_ue9b2.xml", "favorite_ue87e.xml", "volume_off_ue04f.xml"):
            paths = [
                [path.attrib[ANDROID + "pathData"] for path in ET.fromstring(
                    archives[variant].read(f"res/drawable/{prefixes[variant]}{suffix}")
                ).findall("path")]
                for variant in ("", "-filled")
            ]
            assert paths[0] != paths[1], f"Filled geometry did not change: {style}/{suffix}"
    results["ordinary"]["legacy_xml_byte_identical"] = len(suffixes)
    return results


if __name__ == "__main__":
    aliases = defaultdict(list)
    for line in (ROOT / "fonts/material/MaterialSymbols.codepoints").read_text().splitlines():
        if line.strip():
            name, codepoint = line.split()
            aliases[int(codepoint, 16)].append(name)
    suffixes = {f"{min(names)}_u{codepoint:x}.xml" for codepoint, names in aliases.items()}
    assert len(suffixes) == 3802
    print(json.dumps({style: verify(style, suffixes) for style in BASELINE_SHA256}, indent=2))
