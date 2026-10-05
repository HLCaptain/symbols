#!/usr/bin/env python3
"""Build focused native consumers and compare ordinary APKs with pinned 2.1.0 packs."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
from zipfile import ZipFile

from verify_aars import BASELINE_SHA256, HERE, ROOT

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("aapt2", type=Path)
args = parser.parse_args()
output = HERE / "build/size-regression"
output.mkdir(parents=True, exist_ok=True)
styles = ("outlined", "rounded", "sharp")


def resource(style, variant):
    mirrored = "automirrored" in variant
    filled = "filled" in variant
    return f"material_symbols_{'automirrored_' if mirrored else ''}{style}_{'filled_' if filled else ''}volume_off_ue04f"


def check(name, packs, used, minimum=21, baseline=False):
    fixture = output / name
    if not fixture.exists():
        shutil.copytree(HERE, fixture, ignore=shutil.ignore_patterns("build", ".gradle", "__pycache__"))
        shutil.rmtree(fixture / "src/test")
    settings = (HERE / "settings.gradle.kts").read_text()
    (fixture / "settings.gradle.kts").write_text(settings.replace("../../../gradle/libs.versions.toml", str(ROOT / "gradle/libs.versions.toml")))
    paths = []
    for style, variant in packs:
        module = f"material-drawables-{style}{'-' + variant if variant else ''}"
        path = HERE / f"build/baseline/symbols-{module}-2.1.0.aar" if baseline else ROOT / f"symbols/{module}/build/outputs/aar/{module}-release.aar"
        assert path.is_file(), path
        if baseline:
            assert hashlib.sha256(path.read_bytes()).hexdigest() == BASELINE_SHA256[style]
        paths.append(str(path))
    build = (HERE / "build.gradle.kts").read_text()
    start, end = build.index('    listOf("outlined"'), build.index("    testImplementation(libs.junit)")
    build = build[:start] + "    implementation(files(\n" + "".join(f'        "{path}",\n' for path in paths) + "    ))\n" + build[end:]
    (fixture / "build.gradle.kts").write_text(build.replace("minSdk = 21", f"minSdk = {minimum}"))
    imports = "".join(f"import static io.github.hlcaptain.symbols.material.{style}.drawables{'.' + variant.replace('-', '.') if variant else ''}.R.drawable.*;\n" for style, variant in used)
    activity = (HERE / "src/main/java/example/ConsumerActivity.java").read_text()
    activity = re.sub(r"(?m)^import static .*;\n", "", activity).replace("package example;\n", "package example;\n\n" + imports)
    for style, variant in used:
        activity = activity.replace(resource(style, "automirrored-filled"), resource(style, variant))
    (fixture / "src/main/java/example/ConsumerActivity.java").write_text(activity)
    layout = (HERE / "src/main/res/layout/native_drawable.xml").read_text()
    (fixture / "src/main/res/layout/native_drawable.xml").write_text(layout.replace(resource("rounded", "automirrored-filled"), resource(*used[1])))
    with (fixture / "gradle.log").open("w") as log:
        subprocess.run([str(ROOT / "gradlew"), "-p", str(fixture), "assembleDebug", "assembleRelease", "--max-workers=2", "--no-configuration-cache", "--console=plain"], check=True, stdout=log, stderr=subprocess.STDOUT)
    results = {}
    expected = {resource(*pack) for pack in used}
    for variant in ("debug", "release"):
        apk, = (fixture / f"build/outputs/apk/{variant}").glob("*.apk")
        dump = subprocess.check_output([str(args.aapt2), "dump", "resources", str(apk)], text=True)
        names = set(re.findall(r"drawable/(material_symbols_[a-z0-9_]+)", dump))
        assert expected <= names, (name, variant, "used resource removed")
        assert len(names) == len(packs) * 3802 if variant == "debug" else names == expected, (name, variant, names - expected)
        (fixture / f"{variant}-resources.txt").write_text(dump)
        with ZipFile(apk) as archive:
            results[variant] = {"apk_bytes": apk.stat().st_size, "resource_table_bytes": archive.getinfo("resources.arsc").file_size, "drawables": len(names)}
    configuration = (fixture / "build/outputs/mapping/release/configuration.txt").read_text()
    assert "-dontshrink" not in configuration and "-dontoptimize" not in configuration
    print(name, json.dumps(results), flush=True)
    return results


ordinary = [(style, "") for style in styles]
results = {}
for minimum in (21, 26):
    for baseline in (True, False):
        name = f"{'baseline' if baseline else 'ordinary'}-min{minimum}"
        results[name] = check(name, ordinary, ordinary, minimum, baseline)
    assert results[f"baseline-min{minimum}"]["release"] == results[f"ordinary-min{minimum}"]["release"], "Ordinary-only APK size or resource table grew"
for variant in ("filled", "automirrored", "automirrored-filled"):
    packs = [(style, variant) for style in styles]
    results[variant] = check(variant, packs, packs)
mixed = [("outlined", ""), ("rounded", "filled"), ("sharp", "automirrored-filled")]
results["mixed-with-unused-pack"] = check("mixed-with-unused-pack", mixed + [("outlined", "automirrored")], mixed)
(output / "results.json").write_text(json.dumps(results, indent=2) + "\n")
