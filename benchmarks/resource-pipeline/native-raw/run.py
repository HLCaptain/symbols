#!/usr/bin/env python3
"""Build dependency-only prototype fixtures and verify shrinking/cache correctness.

First-build times are diagnostic, not a controlled performance comparison.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import time

import verify


def publication_hash(repository, manifest):
    """Freeze local metadata and transitive runtime, not just the pack AAR."""
    group, module, version = manifest["coordinates"].split(":")
    coordinates = [(group, name, version) for name in (module, module + "-android", module + "-jvm")]
    if manifest.get("intermediate_coordinates"):
        group, module, version = manifest["intermediate_coordinates"].split(":")
        coordinates += [(group, name, version) for name in (module, module + "-android", module + "-jvm")]
    if manifest["prototype_backend"] == "native":
        coordinates += [("org.jetbrains.compose.components", name, manifest["runtime_version"])
                        for name in ("components-resources", "components-resources-android")]
    digest = hashlib.sha256()
    for group, module, version in coordinates:
        directory = repository / group.replace(".", "/") / module / version
        if not directory.is_dir():
            raise ValueError(f"Missing local publication: {directory}")
        for path in sorted(directory.iterdir()):
            if path.is_file():
                digest.update(str(path.relative_to(repository)).encode())
                digest.update(path.read_bytes())
    return digest.hexdigest()


def load(path):
    spec = importlib.util.spec_from_file_location(path.stem, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--aapt2", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--case", action="append")
    parser.add_argument("--resume", action="store_true")
    args = parser.parse_args()
    usage = load(Path(__file__).resolve().parents[1] / "usage.py")
    retention = load(Path(__file__).resolve().parents[1] / "retention.py")
    destination = args.output.resolve()
    if destination.exists() and not args.resume:
        parser.error("Use a fresh output or --resume")
    report = json.loads(destination.read_text()) if destination.exists() else dict(
        schema_version=1, purpose="Retention/byte parity and configuration-cache correctness; times are diagnostic", cases={})
    paths = sorted(args.fixtures.resolve().glob("*/fixture.json"))
    if not paths or args.case and set(args.case) - {p.parent.name for p in paths}:
        parser.error("No fixtures or unknown selected case")
    destination.parent.mkdir(parents=True, exist_ok=True)
    for path in paths:
        fixture = path.parent
        if args.case and fixture.name not in args.case:
            continue
        manifest = json.loads(path.read_text())
        group, module, version = manifest["coordinates"].split(":")
        aars = list((args.repository.resolve() / group.replace(".", "/") / (module + "-android") / version).glob("*.aar"))
        if len(aars) != 1:
            raise ValueError(f"Expected one published AAR for {manifest['coordinates']}")
        aar = aars[0]
        fingerprint = usage.inputs_hash(fixture)
        publications = publication_hash(args.repository.resolve(), manifest)
        old = report["cases"].get(fixture.name)
        if args.resume and old and old.get("success"):
            if old["input_sha256"] != fingerprint or old.get("publication_sha256") != publications:
                raise ValueError(f"Frozen inputs changed for {fixture.name}; use a new cohort/version")
            if old["apk_sha256"] == usage.sha(Path(old["apk"])):
                print(f"Reusing {fixture.name}", flush=True)
                continue
        row = dict(coordinates=manifest["coordinates"], input_sha256=fingerprint, aar_sha256=usage.sha(aar), publication_sha256=publications,
                   prototype_backend=manifest["prototype_backend"], access=manifest["access"], count=manifest["count"],
                   toolchain={key: manifest["versions"][key] for key in ("agp", "kotlin", "composeMultiplatform")},
                   success=False, builds=[])
        report["cases"][fixture.name] = row
        command = [str(fixture / "gradlew"), "assembleShrunk", "--configuration-cache", "--no-build-cache",
                   "--no-daemon", "--max-workers=1", "-Pkotlin.compiler.execution.strategy=in-process", "--console=plain"]
        try:
            for phase in ("first", "reuse"):
                log = fixture / f"probe-{phase}.log"
                started = time.monotonic()
                with log.open("w") as stream:
                    completed = subprocess.run(command, cwd=fixture, stdout=stream, stderr=subprocess.STDOUT)
                text = log.read_text()
                reused = "Reusing configuration cache." in text or "Configuration cache entry reused." in text
                row["builds"].append(dict(phase=phase, seconds=round(time.monotonic()-started, 3),
                                          exit_code=completed.returncode, configuration_cache_reused=reused,
                                          command=command, log=str(log)))
                if completed.returncode or phase == "reuse" and not reused:
                    raise RuntimeError(f"Build/cache failure; see {log}")
                print(f"{fixture.name} {phase}: {row['builds'][-1]['seconds']}s", flush=True)
            apks = list((fixture / "build/outputs/apk/shrunk").glob("*.apk"))
            if len(apks) != 1:
                raise ValueError(f"Expected one APK for {fixture}")
            if "native_raw" in manifest:
                row["retention"] = verify.analyze(fixture, apks[0], aar, args.aapt2)
            else:
                row["retention"] = retention.analyze(fixture, apks[0], args.aapt2)
                if row["retention"]["retained_icon_resources"] != manifest["all_unique_glyphs"]:
                    raise ValueError("Asset control changed its expected full-pack retention")
            row.update(apk=str(apks[0]), apk_sha256=usage.sha(apks[0]), success=True)
            print(f"Verified {fixture.name}", flush=True)
        finally:
            destination.write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
