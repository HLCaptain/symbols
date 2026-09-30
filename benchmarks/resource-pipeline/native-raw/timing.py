#!/usr/bin/env python3
"""Repeated, alternating clean/no-change consumer builds; never time publishers."""
import argparse
from contextlib import redirect_stderr
import json
import os
from pathlib import Path
import platform
import shutil
import statistics
import subprocess

import run
import verify


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("assets", "native", "repository", "aapt2", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--repeat", type=int, default=3)
    args = parser.parse_args()
    if args.repeat < 3 or args.output.exists():
        parser.error("Use at least three repeats and a fresh output directory")
    usage = run.load(Path(__file__).resolve().parents[1] / "usage.py")
    study = run.load(Path(__file__).resolve().parents[1] / "run.py")
    memory = run.load(Path(__file__).resolve().parents[2] / "shrinkable-vectors/measure.py")
    retention = run.load(Path(__file__).resolve().parents[1] / "retention.py")
    fixtures = {label: getattr(args, label).resolve() for label in ("assets", "native")}
    manifests = {label: json.loads((path / "fixture.json").read_text()) for label, path in fixtures.items()}
    selected = {label: [{key: value for key, value in icon.items() if key != "raw_resource"}
                        for icon in manifest["selected"]]
                for label, manifest in manifests.items()}
    if selected["assets"] != selected["native"]:
        raise ValueError("Compare identical logical resources and XML bytes")
    for field in ("access", "count", "all_resources", "all_unique_glyphs", "versions"):
        if manifests["assets"][field] != manifests["native"][field]:
            raise ValueError(f"Compare identical consumer inputs: {field}")
    for relative in ("gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties", "gradle.properties"):
        if (fixtures["assets"] / relative).read_bytes() != (fixtures["native"] / relative).read_bytes():
            raise ValueError(f"Compare identical toolchain configuration: {relative}")
    args.output.mkdir(parents=True)
    report = dict(schema_version=1, method="One excluded warm-up per backend; alternating order per repeat; clean then no-change; single-use daemon, one worker, in-process Kotlin, no build/configuration cache; downloaded dependencies/transforms remain warm", inputs={}, samples=[])
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else "java"
    report["environment"] = dict(platform=platform.platform(), logical_cpus=os.cpu_count(),
                                 java=subprocess.check_output([java, "-version"], stderr=subprocess.STDOUT, text=True).strip(),
                                 versions={key: manifests["native"]["versions"][key] for key in ("agp", "kotlin", "composeMultiplatform", "android-compileSdk")},
                                 wrapper_properties=(fixtures["native"] / "gradle/wrapper/gradle-wrapper.properties").read_text())
    for label, path in fixtures.items():
        manifest = manifests[label]
        if manifest["prototype_backend"] != label:
            raise ValueError("Fixture/backend mismatch")
        report["inputs"][label] = dict(fixture_sha256=usage.inputs_hash(path),
                                      publication_sha256=run.publication_hash(args.repository, manifest))

    def build(label, phase, iteration):
        fixture, manifest = fixtures[label], manifests[label]
        if usage.inputs_hash(fixture) != report["inputs"][label]["fixture_sha256"]:
            raise ValueError("Fixture changed during measurement")
        if phase in ("clean", "warmup"):
            output = fixture / "build"
            if output.is_symlink():
                raise ValueError("Refusing symlink build directory")
            if output.exists():
                shutil.rmtree(output)
        memory.ROOT, memory.TASKS = fixture, ("assembleShrunk",)
        log = args.output / f"{iteration}-{label}-{phase}.log"
        with log.open("w") as stream, redirect_stderr(stream):
            measured = memory.run_measured(label, ("--max-workers=1", "--no-build-cache", "--profile",
                                                  "-Pkotlin.compiler.execution.strategy=in-process",
                                                  "-Dorg.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8"))
        if not measured["peak_descendant_rss_kib_sampled"]:
            raise ValueError("RSS sampling failed")
        apk, = (fixture / "build/outputs/apk/shrunk").glob("*.apk")
        if label == "native":
            group, module, version = manifest["coordinates"].split(":")
            aar, = (args.repository / group.replace(".", "/") / (module + "-android") / version).glob("*.aar")
            retained = verify.analyze(fixture, apk, aar, args.aapt2)
        else:
            retained = retention.analyze(fixture, apk, args.aapt2)
            if retained["retained_icon_resources"] != manifest["all_unique_glyphs"]:
                raise ValueError("Asset baseline retention changed")
        profile = max((fixture / "build/reports/profile").glob("profile-*.html"), key=lambda p: p.stat().st_mtime)
        shutil.copy2(profile, log.with_suffix(".html"))
        report["samples"].append(dict(backend=label, phase=phase, iteration=iteration, measured=phase != "warmup",
                                      **measured, apk_bytes=apk.stat().st_size, apk_sha256=usage.sha(apk),
                                      retention=retained, task_timings=study.profile_tasks(profile)))
        (args.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")
        print(f"{iteration} {label} {phase}: {measured['wall_seconds']}s", flush=True)

    for label in fixtures:
        build(label, "warmup", 0)
    for iteration in range(1, args.repeat + 1):
        for label in (list(fixtures) if iteration % 2 else list(reversed(fixtures))):
            build(label, "clean", iteration)
            build(label, "noop", iteration)
    report["summary"] = {}
    for label in fixtures:
        for phase in ("clean", "noop"):
            rows = [r for r in report["samples"] if r["backend"] == label and r["phase"] == phase]
            report["summary"][f"{label}-{phase}"] = {
                metric: dict(median=statistics.median(values), minimum=min(values), maximum=max(values))
                for metric in ("wall_seconds", "peak_descendant_rss_bytes_sampled")
                for values in [[r[metric] for r in rows]]}
    for label, manifest in manifests.items():
        if run.publication_hash(args.repository, manifest) != report["inputs"][label]["publication_sha256"]:
            raise ValueError("Publication changed during measurement")
    (args.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
