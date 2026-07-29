#!/usr/bin/env python3
"""Measure a cold full build and an output-warm no-change build.

Peak RSS is a sampled sum over the Gradle launcher and all descendant
processes. It is an operating-system measurement, not a JVM heap measurement.
Both measurements use a single-use daemon so that the complete Gradle process
tree remains a descendant of the measured launcher.
"""

from __future__ import annotations

import json
import os
import platform
import subprocess
import sys
import threading
import time
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
TASKS = (
    ":benchmarks:shrinkable-vectors:assembleUnshrunk",
    ":benchmarks:shrinkable-vectors:assembleShrunk",
)


def process_table() -> dict[int, tuple[int, int]]:
    output = subprocess.check_output(
        ("ps", "-axo", "pid=,ppid=,rss="),
        text=True,
    )
    table: dict[int, tuple[int, int]] = {}
    for row in output.splitlines():
        fields = row.split()
        if len(fields) == 3:
            pid, parent, rss = (int(field) for field in fields)
            table[pid] = (parent, rss)
    return table


def descendant_rss_kib(root_pid: int) -> int:
    table = process_table()
    descendants = {root_pid}
    changed = True
    while changed:
        changed = False
        for pid, (parent, _) in table.items():
            if parent in descendants and pid not in descendants:
                descendants.add(pid)
                changed = True
    return sum(table.get(pid, (0, 0))[1] for pid in descendants)


def run_measured(label: str, extra: tuple[str, ...]) -> dict[str, Any]:
    command = (
        str(ROOT / "gradlew"),
        *TASKS,
        "--console=plain",
        "--quiet",
        "--no-daemon",
        "--no-configuration-cache",
        *extra,
    )
    start = time.monotonic()
    process = subprocess.Popen(
        command,
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    peak_rss_kib = 0
    stop = threading.Event()

    def sample() -> None:
        nonlocal peak_rss_kib
        while not stop.wait(0.1):
            try:
                peak_rss_kib = max(
                    peak_rss_kib,
                    descendant_rss_kib(process.pid),
                )
            except (OSError, subprocess.SubprocessError, ValueError):
                pass

    sampler = threading.Thread(target=sample, daemon=True)
    sampler.start()
    assert process.stdout is not None
    for line in process.stdout:
        sys.stderr.write(f"[{label}] {line}")
    return_code = process.wait()
    stop.set()
    sampler.join()
    elapsed = time.monotonic() - start
    if return_code:
        raise subprocess.CalledProcessError(return_code, command)
    return {
        "command": list(command),
        "wall_seconds": round(elapsed, 3),
        "peak_descendant_rss_kib_sampled": peak_rss_kib,
        "peak_descendant_rss_bytes_sampled": peak_rss_kib * 1024,
        "rss_sample_interval_seconds": 0.1,
    }


def command_output(*command: str) -> str:
    return subprocess.check_output(
        command,
        cwd=ROOT,
        text=True,
        stderr=subprocess.STDOUT,
    ).strip()


def main() -> int:
    subprocess.run(
        (str(ROOT / "gradlew"), "--stop"),
        cwd=ROOT,
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    cold = run_measured(
        "cold",
        ("--no-build-cache", "--rerun-tasks"),
    )

    warm = run_measured("warm", ())

    java_home = os.environ.get("JAVA_HOME", "")
    configured_java = Path(java_home) / "bin" / "java"
    java_command = (
        str(configured_java)
        if java_home and configured_java.is_file()
        else "java"
    )
    result = {
        "schema_version": 1,
        "method": {
            "cold": (
                "existing daemons stopped; single-use daemon, build cache "
                "disabled, every task rerun, configuration cache disabled; "
                "downloaded dependency and transform caches retained"
            ),
            "warm": (
                "same outputs and caches; fresh single-use daemon; no-change "
                "tasks; configuration cache disabled"
            ),
            "memory": (
                "maximum 100 ms sample of summed RSS for the Gradle launcher "
                "process and its descendant process tree"
            ),
        },
        "measurements": {"cold": cold, "warm": warm},
        "environment": {
            "platform": platform.platform(),
            "machine": platform.machine(),
            "processor": platform.processor(),
            "python": platform.python_version(),
            "java_home": java_home,
            "java_version": command_output(java_command, "-version"),
            "gradle_version": command_output(
                str(ROOT / "gradlew"),
                "--quiet",
                "--version",
            ),
            "git_revision": command_output("git", "rev-parse", "HEAD"),
            "git_dirty": bool(command_output("git", "status", "--porcelain")),
        },
    }
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
