#!/usr/bin/env python3
"""Check an unshrunk control and native resources in a device-specific app bundle."""
import argparse
import json
from pathlib import Path
import subprocess
import zipfile

import publisher
import verify

BUNDLETOOL_SHA256 = "a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("fixture", "aar", "bundletool", "java", "adb", "aapt2", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    for name, value in vars(args).items():
        if isinstance(value, Path):
            setattr(args, name, value.resolve())
    if args.output.exists():
        parser.error("Use a fresh output directory")
    if publisher.digest(args.bundletool.read_bytes()) != BUNDLETOOL_SHA256:
        raise ValueError("Expected the checksum-pinned official bundletool-all 1.18.3 JAR")
    fixture = args.fixture.resolve()
    manifest = json.loads((fixture / "fixture.json").read_text())
    if "native_raw" not in manifest:
        raise ValueError("This check requires a native-resource fixture")
    args.output.mkdir(parents=True)
    def command(argv, name):
        with (args.output / f"{name}.log").open("w") as log:
            subprocess.run(list(map(str, argv)), cwd=fixture, check=True, stdout=log, stderr=subprocess.STDOUT)
    command([fixture / "gradlew", "assembleRelease", "bundleShrunk", "--no-daemon", "--max-workers=1",
             "--configuration-cache", "--no-build-cache", "-Pkotlin.compiler.execution.strategy=in-process"], "gradle")
    control, = (fixture / "build/outputs/apk/release").glob("*.apk")
    control_fixture = args.output / "unshrunk"
    control_fixture.mkdir()
    control_manifest = dict(manifest, native_raw=dict(manifest["native_raw"], expected_resources=manifest["native_raw"]["all_resources"]))
    (control_fixture / "fixture.json").write_text(json.dumps(control_manifest))
    unshrunk = verify.analyze(control_fixture, control, args.aar, args.aapt2)
    bundle, = (fixture / "build/outputs/bundle/shrunk").glob("*.aab")
    tool = [args.java, "-jar", args.bundletool]
    spec, apks = args.output / "device.json", args.output / "device.apks"
    command([*tool, "get-device-spec", f"--output={spec}", f"--adb={args.adb}", f"--device-id={args.serial}"], "device-spec")
    command([*tool, "build-apks", f"--bundle={bundle}", f"--output={apks}", f"--device-spec={spec}"], "build-apks")
    with zipfile.ZipFile(apks) as archive:
        master, = [name for name in archive.namelist() if name.endswith("base-master.apk")]
        base = args.output / "base-master.apk"
        base.write_bytes(archive.read(master))
    split = verify.analyze(fixture, base, args.aar, args.aapt2)
    command([*tool, "install-apks", f"--apks={apks}", f"--adb={args.adb}", f"--device-id={args.serial}"], "install-apks")
    result = dict(schema_version=1, bundletool_sha256=BUNDLETOOL_SHA256,
                  aab_sha256=publisher.digest(bundle.read_bytes()), aab_bytes=bundle.stat().st_size,
                  device_spec=json.loads(spec.read_text()), unshrunk=unshrunk, split=split,
                  installed=True, rendering="Launch and inspect separately; successful installation alone is not a rendering check")
    (args.output / "results.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
