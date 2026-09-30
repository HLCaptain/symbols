#!/usr/bin/env python3
"""Compare the installed dynamic-all fixtures on the study's Pixel 6a grid."""
import argparse
import json
from pathlib import Path
import subprocess
import time

from PIL import Image, ImageChops, ImageStat

BACKENDS = ("android", "compose", "vectors")
POSITIONS = (1008, 1808, 2308, 2364)


def compare(directory):
    results = []
    for first in POSITIONS:
        with Image.open(directory / f"vectors-{first}.png") as image:
            reference = image.convert("RGB")
        if reference.size != (1080, 2400):
            raise ValueError(f"Expected Pixel 6a 1080x2400 capture, got {reference.size}")
        for backend in ("android", "compose"):
            with Image.open(directory / f"{backend}-{first}.png") as image:
                actual = image.convert("RGB")
            if actual.size != reference.size:
                raise ValueError("Backend capture dimensions differ")
            diff = ImageChops.difference(reference, actual)
            errors = []
            # ponytail: fixed 420dpi study grid; use UI hierarchy bounds for other devices.
            # Skip the system-bar row and bottom gesture area.
            for row in range(1, 13):
                for column in range(4):
                    box = (column * 270, row * 168, (column + 1) * 270, (row + 1) * 168)
                    if min(ImageStat.Stat(reference.crop(box)).mean) > 254:
                        raise ValueError(f"Reference glyph is blank: {first}/{row}/{column}")
                    errors.append(sum(ImageStat.Stat(diff.crop(box)).mean) / (3 * 255))
            results.append(dict(backend=backend, first=first, cells=len(errors),
                                mean_absolute_rgb_error=sum(errors) / len(errors),
                                max_cell_mean_absolute_rgb_error=max(errors)))
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", help="Required when capturing")
    parser.add_argument("--compare-only", action="store_true")
    args = parser.parse_args()
    if not args.compare_only:
        if not args.serial:
            parser.error("--serial is required when capturing")
        if args.output.exists():
            parser.error("Use a fresh output directory, or --compare-only")
        args.output.mkdir(parents=True)
        for first in POSITIONS:
            for backend in BACKENDS:
                prefix = [args.adb, "-s", args.serial]
                package = f"io.github.hlcaptain.symbols.usage.{backend}.dynamic"
                subprocess.run(prefix + ["shell", "am", "force-stop", package], check=True)
                subprocess.run(prefix + ["shell", "am", "start", "-W", "-n",
                               f"{package}/study.MainActivity", "--ei", "first", str(first)], check=True)
                time.sleep(1)
                with (args.output / f"{backend}-{first}.png").open("wb") as stream:
                    subprocess.run(prefix + ["exec-out", "screencap", "-p"], check=True, stdout=stream)
    results = compare(args.output)
    accepted = all(row["max_cell_mean_absolute_rgb_error"] <= 0.005 for row in results)
    report = dict(device="Pixel 6a, 1080x2400, 420dpi", reference="FontTools-generated typed vectors",
                  max_cell_error_bound=0.005, accepted=accepted, results=results)
    (args.output / "parity.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    if not accepted:
        raise SystemExit("Pixel rendering differs from the vector reference; inspect local captures")


if __name__ == "__main__":
    main()
