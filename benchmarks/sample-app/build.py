#!/usr/bin/env python3
"""Build reproducible full-app APK profiles and copy them below root build/."""

from __future__ import annotations

import argparse
import json
import os
import platform
import shutil
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence


ROOT = Path(__file__).resolve().parents[2]
REPORT_ROOT = ROOT / "build/reports/apk-study"
APK_DIRECTORY = REPORT_ROOT / "apks"
PROFILES = (
    "shell",
    "material-static",
    "material-variable",
    "custom-static",
    "custom-variable",
    "image-vector-migration",
    "android-views",
    "theming",
    "runtime-axes",
    "all",
)
VARIANTS = ("release", "shrunk")


def command_output(*command: str) -> str:
    return subprocess.check_output(
        command,
        cwd=ROOT,
        text=True,
        stderr=subprocess.STDOUT,
    ).strip()


def find_apk(variant: str) -> Path:
    output_directory = ROOT / "composeApp/build/outputs/apk" / variant
    candidates = sorted(output_directory.glob("*.apk"))
    if len(candidates) != 1:
        raise FileNotFoundError(
            f"expected exactly one {variant} APK, found: {candidates}"
        )
    return candidates[0]


def build(
    profile: str,
    variant: str,
    execution_cold: bool,
    compress_fonts: bool,
    repetition: int,
    repetitions: int,
) -> dict[str, Any]:
    task = f":composeApp:assemble{variant.capitalize()}"
    command = [
        str(ROOT / "gradlew"),
        task,
        f"-PsymbolsSampleProfile={profile}",
        "--console=plain",
        "--max-workers=1",
        "--no-daemon",
        "--stacktrace",
    ]
    if execution_cold:
        command.extend(
            (
                "--no-build-cache",
                "--no-configuration-cache",
                "--rerun-tasks",
            )
        )
    else:
        command.extend(
            (
                "-Pkotlin.incremental=false",
                "-Pkotlin.compiler.execution.strategy=in-process",
            )
        )
    if compress_fonts:
        command.append("-PcompressSymbolFonts=true")

    started = time.monotonic()
    subprocess.run(command, cwd=ROOT, check=True)
    wall_seconds = round(time.monotonic() - started, 3)

    source = find_apk(variant)
    report_profile = f"{profile}-compressed-fonts" if compress_fonts else profile
    destination = APK_DIRECTORY / f"{report_profile}-{variant}.apk"
    shutil.copy2(source, destination)
    return {
        "profile": report_profile,
        "gradle_profile": profile,
        "compress_fonts": compress_fonts,
        "variant": variant,
        "repetition": repetition,
        "warmup": not execution_cold and repetitions > 1 and repetition == 1,
        "task": task,
        "command": command,
        "wall_seconds": wall_seconds,
        "apk": destination.relative_to(ROOT).as_posix(),
        "apk_bytes": destination.stat().st_size,
    }


def create_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--profile",
        action="append",
        choices=PROFILES,
        help="Profile to build; repeat as needed (default: every profile).",
    )
    parser.add_argument(
        "--variant",
        action="append",
        choices=VARIANTS,
        help="Variant to build; repeat as needed (default: release and shrunk).",
    )
    parser.add_argument(
        "--execution-cold",
        action="store_true",
        help="Disable caches and rerun tasks for execution-cost measurements.",
    )
    parser.add_argument(
        "--repeat",
        type=int,
        default=1,
        help="Number of builds for each profile/variant pair (default: 1).",
    )
    parser.add_argument(
        "--label",
        default="matrix",
        help="Identifier used in the build timing JSON file (default: matrix).",
    )
    parser.add_argument(
        "--compress-fonts",
        action="store_true",
        help="Allow Android to deflate TTF assets and suffix copied profile names.",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    options = create_parser().parse_args(argv)
    profiles = tuple(dict.fromkeys(options.profile or PROFILES))
    variants = tuple(dict.fromkeys(options.variant or VARIANTS))
    if options.repeat < 1:
        raise ValueError("--repeat must be positive")
    if not options.label.replace("-", "").replace("_", "").isalnum():
        raise ValueError("--label must contain only letters, digits, '-' or '_'")
    APK_DIRECTORY.mkdir(parents=True, exist_ok=True)

    builds = [
        build(
            profile,
            variant,
            options.execution_cold,
            options.compress_fonts,
            repetition,
            options.repeat,
        )
        for profile in profiles
        for variant in variants
        for repetition in range(1, options.repeat + 1)
    ]
    java_home = Path(os.environ.get("JAVA_HOME", ""))
    java = java_home / "bin/java"
    result = {
        "schema_version": 1,
        "created_at_utc": datetime.now(timezone.utc).isoformat(),
        "execution_cold": options.execution_cold,
        "label": options.label,
        "repetitions": options.repeat,
        "environment": {
            "platform": platform.platform(),
            "machine": platform.machine(),
            "processor": platform.processor(),
            "cpu_count": os.cpu_count(),
            "python": platform.python_version(),
            "java_version": command_output(
                str(java) if java.is_file() else "java",
                "-version",
            ),
            "gradle_version": command_output(str(ROOT / "gradlew"), "--version"),
            "git_revision": command_output("git", "rev-parse", "HEAD"),
            "git_status": command_output("git", "status", "--porcelain"),
        },
        "builds": builds,
    }
    output = REPORT_ROOT / f"builds-{options.label}.json"
    output.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"Copied {len(builds)} APKs to {APK_DIRECTORY}; timings: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
