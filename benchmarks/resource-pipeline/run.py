#!/usr/bin/env python3
"""Compare pinned Symbols revisions using existing app profiles and APK analysis."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from contextlib import contextmanager
from datetime import datetime, timezone
from html.parser import HTMLParser
import importlib.util
import json
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import statistics
import subprocess
import time
import tomllib

ROOT = Path(__file__).resolve().parents[2]
SCENARIOS = ("target-clean", "noop", "code-edit", "resource-edit", "configuration-cache", "build-cache")
CODE = Path("composeApp/src/commonMain/kotlin/io/github/hlcaptain/symbols/sample/App.kt")
RESOURCE = Path("samples/image-vector-migration/src/commonMain/svg/tabler/home.svg")
RESOURCE_PROFILES = {"image-vector-migration", "android-views", "all"}


def load(path):
    spec = importlib.util.spec_from_file_location(path.stem, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def output(root, *args, env=None):
    return subprocess.check_output(args, cwd=root, env=env, text=True, stderr=subprocess.STDOUT).strip()


def assignments(values, *, resolve_symlinks=True):
    result = {}
    for item in values:
        label, separator, value = item.partition("=")
        if not separator or not re.fullmatch(r"[A-Za-z0-9_-]+", label) or label in result:
            raise ValueError(f"Expected a unique LABEL=PATH, got {item!r}")
        path = Path(value).expanduser().absolute()
        result[label] = path.resolve() if resolve_symlinks else path
    return result


@contextmanager
def edit(path, before, after):
    original = path.read_bytes()
    if original.count(before) != 1:
        raise ValueError(f"Expected exactly one edit marker in {path}")
    path.write_bytes(original.replace(before, after))
    try:
        yield
    finally:
        path.write_bytes(original)


def clean_outputs(root):
    # Preserve downloaded dependencies, included tooling builds, and configuration caches.
    scripts = output(root, "git", "ls-files", "**/build.gradle.kts", "build.gradle.kts").splitlines()
    for script in scripts:
        relative = Path(script)
        if relative.parts[0] in {"build-logic", "tooling"}:
            continue
        directory = root / relative.parent / "build"
        if directory.is_symlink():
            raise ValueError(f"Refusing to clean symlink {directory}")
        if directory.exists():
            shutil.rmtree(directory)


class ProfileRows(HTMLParser):
    def __init__(self):
        super().__init__()
        self.rows, self.row, self.cell = [], None, None

    def handle_starttag(self, tag, attrs):
        if tag == "tr": self.row = []
        if tag == "td": self.cell = ""

    def handle_data(self, data):
        if self.cell is not None: self.cell += data

    def handle_endtag(self, tag):
        if tag == "td" and self.row is not None and self.cell is not None:
            self.row.append(self.cell.strip())
            self.cell = None
        if tag == "tr" and self.row is not None:
            self.rows.append(self.row)
            self.row = None


def profile_tasks(path):
    parser = ProfileRows()
    parser.feed(path.read_text())
    tasks = []
    for row in parser.rows:
        if len(row) >= 3 and row[0].startswith(":"):
            parts = re.findall(r"([\d.]+)\s*(h|m|s)", row[1])
            if parts:
                tasks.append({"path": row[0], "seconds": sum(float(value) * {"h": 3600, "m": 60, "s": 1}[unit] for value, unit in parts), "result": row[2]})
    return tasks


def summarize(rows):
    groups = defaultdict(list)
    for row in rows:
        if row["measured"] and row["exit_code"] == 0:
            groups[(row["revision"], row["profile"], row["variant"], row["scenario"])].append(row)
    summary = []
    for (revision, profile, variant, scenario), samples in sorted(groups.items()):
        item = dict(revision=revision, profile=profile, variant=variant, scenario=scenario, runs=len(samples))
        for key in ("wall_seconds", "peak_process_tree_rss_bytes_sampled", "apk_bytes"):
            values = [row["apk"][key] if key == "apk_bytes" else row[key] for row in samples if key == "apk_bytes" or row[key] is not None]
            item[key] = {"median": statistics.median(values), "min": min(values), "max": max(values), "samples": values} if values else None
        summary.append(item)
    return summary


def comparisons(summary, labels):
    indexed = {(row["revision"], row["profile"], row["variant"], row["scenario"]): row for row in summary}
    result = []
    for right_index, right in enumerate(labels):
        for left in labels[:right_index]:
            for key, right_row in indexed.items():
                revision, profile, variant, scenario = key
                left_row = indexed.get((left, profile, variant, scenario))
                if revision != right or left_row is None:
                    continue
                deltas = {}
                for metric in ("wall_seconds", "peak_process_tree_rss_bytes_sampled", "apk_bytes"):
                    if left_row[metric] is None or right_row[metric] is None:
                        continue
                    before, after = left_row[metric]["median"], right_row[metric]["median"]
                    deltas[metric] = {"absolute": after-before, "percent": (after-before)*100/before if before else None}
                result.append(dict(before=left, after=right, profile=profile, variant=variant, scenario=scenario, delta=deltas))
    return result


def generated_inventory(root):
    result = {}
    for directory in sorted(root.glob("**/build/generated")):
        if directory.relative_to(root).parts[0] in {"build-logic", "tooling"}:
            continue
        files = [path for path in directory.rglob("*") if path.is_file()]
        result[str(directory.relative_to(root))] = {"files": len(files), "bytes": sum(path.stat().st_size for path in files), "kotlin_lines": sum(len(path.read_bytes().splitlines()) for path in files if path.suffix == ".kt")}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--revision", action="append", required=True, metavar="LABEL=WORKTREE")
    parser.add_argument("--python", action="append", default=[], metavar="LABEL=PYTHON", help="Per-revision Python virtualenv executable; its bin directory is prepended to PATH.")
    parser.add_argument("--output", type=Path, required=True, help="Private output directory outside every measured checkout.")
    parser.add_argument("--profile", action="append", help="Existing sample profile; default image-vector-migration.")
    parser.add_argument("--variant", action="append", choices=("release", "shrunk"))
    parser.add_argument("--scenario", action="append", choices=SCENARIOS)
    parser.add_argument("--repeat", type=int, default=3)
    parser.add_argument("--max-workers", type=int, default=1)
    args = parser.parse_args()
    if args.repeat < 1 or args.max_workers < 1:
        parser.error("--repeat and --max-workers must be positive")
    if not os.environ.get("JAVA_HOME"):
        parser.error("Set JAVA_HOME to the same JDK 21 for every revision")
    revisions = assignments(args.revision)
    # A venv's python is commonly a symlink; resolving it selects the base interpreter.
    pythons = assignments(args.python, resolve_symlinks=False)
    if set(pythons) - revisions.keys():
        parser.error("Each --python label must match --revision")
    destination = args.output.expanduser().resolve()
    if any(destination.is_relative_to(root) or root.is_relative_to(destination) for root in revisions.values()):
        parser.error("--output must not contain or be contained by a measured checkout")
    if (destination / "results.json").exists():
        parser.error("Use a fresh --output directory to preserve previous results")
    profile_module = load(ROOT / "benchmarks/sample-app/build.py")
    analyzer = load(ROOT / "benchmarks/sample-app/analyze.py")
    memory = load(ROOT / "benchmarks/shrinkable-vectors/measure.py")
    profiles = list(dict.fromkeys(args.profile or ["image-vector-migration"]))
    if set(profiles) - set(profile_module.PROFILES):
        parser.error(f"Unknown profile; choose from {profile_module.PROFILES}")
    variants = list(dict.fromkeys(args.variant or ["release", "shrunk"]))
    scenarios = set(args.scenario or SCENARIOS)
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "logs").mkdir()
    rows = []
    report = {"schema_version": 1, "created_at": datetime.now(timezone.utc).isoformat(), "environment": {"platform": platform.platform(), "cpu_count": os.cpu_count(), "java_home": os.environ["JAVA_HOME"], "java_version": output(ROOT, str(Path(os.environ["JAVA_HOME"]) / "bin/java"), "-version")}, "revisions": {}, "builds": rows, "skipped": []}
    environments = {}
    for label, root in revisions.items():
        if output(root, "git", "status", "--porcelain"):
            raise ValueError(f"{label}: use a clean, disposable checkout, not the development tree")
        env = dict(os.environ)
        if label in pythons:
            env["PATH"] = str(pythons[label].parent) + os.pathsep + env["PATH"]
            env["SYMBOLS_PYTHON"] = str(pythons[label])
        environments[label] = env
        python_executable = str(pythons[label]) if label in pythons else "python3"
        generator_environment = json.loads(output(root, python_executable, "-c", """
import json, sys, fontTools, picosvg, pathops
from importlib.metadata import version
print(json.dumps({"executable": sys.executable, "python": sys.version.split()[0],
                  "fonttools": fontTools.__version__, "picosvg": version("picosvg"),
                  "skia-pathops": version("skia-pathops")}))
""", env=env))
        report["revisions"][label] = {"root": str(root), "sha": output(root, "git", "rev-parse", "HEAD"), "gradle": output(root, str(root / "gradlew"), "--version", env=env), "python": f"Python {generator_environment['python']}", "generator_environment": generator_environment, "versions": tomllib.loads((root / "gradle/libs.versions.toml").read_text())["versions"], "generator_requirements": (root / "tools/requirements-font-verification.txt").read_text()}
    if len(set(info["root"] for info in report["revisions"].values())) != len(revisions):
        raise ValueError("Use distinct worktrees per revision")

    def save():
        report["summary"] = summarize(rows)
        report["comparisons"] = comparisons(report["summary"], list(revisions))
        (destination / "results.json").write_text(json.dumps(report, indent=2) + "\n")

    def run(label, profile, variant, scenario, iteration, measured=True, cache=False, config=False):
        root = revisions[label]
        module = "androidApp" if (root / "androidApp/build.gradle.kts").is_file() else "composeApp"
        command = [str(root / "gradlew"), f":{module}:assemble{variant.capitalize()}", f"-PsymbolsSampleProfile={profile}", "--no-daemon", "--console=plain", "--stacktrace", "--profile", f"--max-workers={args.max_workers}", "--build-cache" if cache else "--no-build-cache", "--configuration-cache" if config else "--no-configuration-cache", "-Pkotlin.compiler.execution.strategy=in-process", "-Dorg.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8"]
        name = f"{label}-{profile}-{variant}-{iteration}-{scenario}"
        log_path = destination / "logs" / f"{name}.log"
        started_wall = time.time()
        started, peak, sample_count, errors = time.monotonic(), 0, 0, []
        with log_path.open("w") as log:
            process = subprocess.Popen(command, cwd=root, env=environments[label], stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                while process.poll() is None:
                    try:
                        peak = max(peak, memory.descendant_rss_kib(process.pid) * 1024)
                        sample_count += 1
                    except (OSError, ValueError, subprocess.SubprocessError) as error:
                        if not errors: errors.append(str(error))
                    time.sleep(0.1)
                code = process.wait()
            except BaseException:
                os.killpg(process.pid, signal.SIGTERM)
                process.wait()
                raise
        row = dict(revision=label, profile=profile, variant=variant, scenario=scenario, iteration=iteration, measured=measured, command=command, exit_code=code, wall_seconds=round(time.monotonic()-started, 3), peak_process_tree_rss_bytes_sampled=peak if sample_count else None, rss_samples=sample_count, rss_errors=errors, log=str(log_path))
        log_text = log_path.read_text()
        row["task_outcomes"] = dict(Counter(status or "EXECUTED" for status in re.findall(r"^> Task \S+(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?$", log_text, re.M)))
        row["configuration_cache_reused"] = "Reusing configuration cache." in log_text
        candidates = [path for path in (root / "build/reports/profile").glob("profile-*.html") if path.stat().st_mtime >= started_wall]
        if candidates:
            newest = max(candidates, key=lambda path: path.stat().st_mtime)
            saved_profile = log_path.with_suffix(".html")
            shutil.copy2(newest, saved_profile)
            row["task_timings"] = profile_tasks(saved_profile)
        if code == 0:
            apk_paths = list((root / module / "build/outputs/apk" / variant).glob("*.apk"))
            if len(apk_paths) != 1:
                raise ValueError(f"Expected one APK: {apk_paths}")
            # Reuse the established APK analysis, with a canonical local profile filename.
            copied = root / "build/reports/apk-study/apks" / f"{profile}-{variant}.apk"
            copied.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(apk_paths[0], copied)
            previous_root = analyzer.ROOT
            try:
                analyzer.ROOT = root
                row["apk"] = analyzer.analyze(copied)
            finally:
                analyzer.ROOT = previous_root
        if scenario == "target-clean" and iteration == 1 and code == 0:
            row["generated"] = generated_inventory(root)
        rows.append(row)
        save()
        print(f"{name}: {row['wall_seconds']:.3f}s, exit={code}", flush=True)
        if code:
            raise subprocess.CalledProcessError(code, command)
        if scenario == "configuration-cache" and not row["configuration_cache_reused"]:
            raise RuntimeError(f"Configuration cache was not reused; inspect {log_path}")
        if scenario == "build-cache" and not row["task_outcomes"].get("FROM-CACHE", 0):
            raise RuntimeError(f"No task restored from the build cache; inspect {log_path}")

    for profile in profiles:
        for variant in variants:
            for label in revisions:
                run(label, profile, variant, "warmup", 0, measured=False)
            order = list(revisions)
            for iteration in range(1, args.repeat + 1):
                rotated = order[(iteration-1) % len(order):] + order[:(iteration-1) % len(order)]
                for label in rotated:
                    root = revisions[label]
                    if "target-clean" in scenarios:
                        clean_outputs(root)
                        run(label, profile, variant, "target-clean", iteration)
                    if "noop" in scenarios:
                        run(label, profile, variant, "noop", iteration)
                    for scenario, path, before, after in [("code-edit", CODE, b'contentDescription = "Back",', b'contentDescription = "Benchmark back",'), ("resource-edit", RESOURCE, b'stroke-width="2"', b'stroke-width="2.25"')]:
                        if scenario not in scenarios: continue
                        if scenario == "resource-edit" and profile not in RESOURCE_PROFILES:
                            skip = {"profile": profile, "scenario": scenario, "reason": "This profile does not consume the SVG fixture"}
                            if skip not in report["skipped"]: report["skipped"].append(skip)
                            continue
                        with edit(root / path, before, after):
                            run(label, profile, variant, scenario, iteration)
                        run(label, profile, variant, scenario + "-restore", iteration, measured=False)
                    if "configuration-cache" in scenarios:
                        run(label, profile, variant, "configuration-seed", iteration, measured=False, config=True)
                        run(label, profile, variant, "configuration-cache", iteration, config=True)
                    if "build-cache" in scenarios:
                        clean_outputs(root)
                        run(label, profile, variant, "build-cache-seed", iteration, measured=False, cache=True)
                        clean_outputs(root)
                        run(label, profile, variant, "build-cache", iteration, cache=True)
    save()


if __name__ == "__main__":
    main()
