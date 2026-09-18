#!/usr/bin/env python3
"""Run a CI command with inherited output and sampled Java/wasm-opt memory metrics."""
import argparse
import os
from pathlib import Path
import signal
import subprocess
import time


def sample_rss():
    output = subprocess.check_output(
        ["ps", "-A", "-o", "comm=,rss="], text=True, stderr=subprocess.DEVNULL, timeout=5,
    )
    combined = wasm = 0
    for line in output.splitlines():
        command, rss = line.rsplit(None, 1)
        name = Path(command).name
        if name in {"java", "wasm-opt"}:
            combined += int(rss)
            if name == "wasm-opt":
                wasm += int(rss)
    return combined, wasm


def run(command, label):
    start = time.monotonic()
    peaks = None
    unavailable = False
    process = subprocess.Popen(command)
    handlers = {}
    try:
        for signum in (signal.SIGINT, signal.SIGTERM):
            handlers[signum] = signal.signal(signum, lambda signum, frame: process.send_signal(signum))
        while True:
            try:
                sample = sample_rss()
                peaks = tuple(max(old, current) for old, current in zip(peaks or (0, 0), sample))
            except (OSError, subprocess.SubprocessError, ValueError):
                unavailable = True
            try:
                code = process.wait(timeout=2)
                break
            except subprocess.TimeoutExpired:
                pass
    finally:
        for signum, handler in handlers.items():
            signal.signal(signum, handler)
        elapsed = time.monotonic() - start
        memory = (
            f"peak combined Java + wasm-opt RSS {peaks[0] / 1024:.1f} MiB; "
            f"peak wasm-opt RSS {peaks[1] / 1024:.1f} MiB"
            if peaks is not None else "RSS sampling unavailable"
        )
        if unavailable and peaks is not None:
            memory += " (some samples unavailable)"
        report = f"{label}: elapsed {elapsed:.1f} s; {memory}."
        print(report, flush=True)
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            try:
                with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as stream:
                    print(f"- {report}", file=stream)
            except OSError:
                print("Could not append CI metrics to the job summary.", flush=True)
    # Match the shell convention for a child terminated by a signal.
    return code if code >= 0 else 128 - code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", required=True)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("a command is required after --")
    return run(command, args.label)


if __name__ == "__main__":
    raise SystemExit(main())
