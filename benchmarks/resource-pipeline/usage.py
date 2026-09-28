#!/usr/bin/env python3
"""Build generated usage consumers and verify APK retention; timings are diagnostic."""
import argparse
from contextlib import redirect_stderr
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import shutil

ROOT = Path(__file__).resolve().parents[2]


def load(path):
    spec = importlib.util.spec_from_file_location(path.stem, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inputs_hash(fixture):
    digest = hashlib.sha256()
    for path in sorted([fixture / "fixture.json", fixture / "build.gradle.kts", fixture / "settings.gradle.kts", fixture / "gradle.properties", fixture / "gradle/wrapper/gradle-wrapper.properties", fixture / "gradle/wrapper/gradle-wrapper.jar", *[p for p in (fixture / "src").rglob("*") if p.is_file()]]):
        digest.update(str(path.relative_to(fixture)).encode())
        digest.update(path.read_bytes())
    return digest.hexdigest()


def discard_intermediates(fixture):
    # Each fixture is disposable; retain installable APKs, R8 outputs and logs.
    for name in ("intermediates", "kotlin", "tmp", "generated"):
        directory = fixture / "build" / name
        if directory.is_symlink():
            raise ValueError(f"Refusing to remove symlink {directory}")
        if directory.is_dir():
            shutil.rmtree(directory)


def reusable(row, fixture):
    return (row.get("input_sha256") == inputs_hash(fixture)
            and row.get("success", False)
            and all(Path(apk["apk"]).is_file() and sha(Path(apk["apk"])) == apk["sha256"] for apk in row["apks"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--aapt2", type=Path, required=True)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--case", action="append", help="Fixture directory name; repeat to select a bounded subset")
    args = parser.parse_args()
    fixtures = args.fixtures.resolve()
    destination = args.output.resolve()
    if destination.exists() and not args.resume:
        parser.error("Output exists; use --resume or a fresh path")
    destination.parent.mkdir(parents=True, exist_ok=True)
    measurement = load(ROOT / "benchmarks/shrinkable-vectors/measure.py")
    retention = load(Path(__file__).with_name("retention.py"))
    report = json.loads(destination.read_text()) if destination.exists() else {"schema_version": 1, "measurement_purpose": "APK size and retention correctness; wall/RSS measurements are diagnostic under concurrent load, not controlled performance benchmarks", "fixtures": str(fixtures), "results": []}
    prior = {row["case"]: row for row in report["results"]}
    paths = sorted(fixtures.glob("*/fixture.json"))
    if not paths:
        parser.error("No generated fixture.json files found")
    if args.case and set(args.case)-{p.parent.name for p in paths}:
        parser.error("Unknown --case")
    for manifest_path in paths:
        fixture = manifest_path.parent
        if args.case and fixture.name not in args.case:
            continue
        if args.resume and fixture.name in prior and reusable(prior[fixture.name], fixture):
            print(f"Reusing verified {fixture.name}", flush=True)
            continue
        print(f"Building {fixture.name}", flush=True)
        manifest = json.loads(manifest_path.read_text())
        row = {"case": fixture.name, "backend": manifest["backend"], "access": manifest["access"], "count": manifest["count"], "coordinates": manifest["coordinates"], "input_sha256": inputs_hash(fixture), "success": False, "apks": []}
        measurement.ROOT = fixture
        measurement.TASKS = ("assembleRelease", "assembleShrunk")
        log_path = fixture / "usage-build.log"
        try:
            with log_path.open("w") as log, redirect_stderr(log):
                row["build"] = measurement.run_measured(fixture.name, ("--max-workers=1", "--no-build-cache", "-Pkotlin.compiler.execution.strategy=in-process", "-Dorg.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8"))
            for variant in ("release", "shrunk"):
                apks = list((fixture / "build/outputs/apk" / variant).glob("*.apk"))
                if len(apks) != 1:
                    raise ValueError(f"Expected exactly one {variant} APK: {apks}")
                data = retention.analyze(fixture, apks[0], args.aapt2)
                row["apks"].append(dict(data, variant=variant, sha256=sha(apks[0])))
            row["success"] = True
        except (OSError, ValueError, subprocess.SubprocessError) as error:
            row["error"] = str(error)
            raise
        finally:
            row["log"] = str(log_path)
            prior[fixture.name] = row
            report["results"] = list(prior.values())
            destination.write_text(json.dumps(report, indent=2) + "\n")
        discard_intermediates(fixture)
        print(f"Verified {fixture.name}: " + ", ".join(f'{apk["variant"]}={apk["apk_bytes"]}' for apk in row["apks"]), flush=True)


if __name__ == "__main__":
    main()
