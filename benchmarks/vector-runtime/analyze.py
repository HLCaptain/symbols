#!/usr/bin/env python3
"""Validate an Android Macrobenchmark capture and summarize its actual ART/frame data."""
import argparse
import json
from pathlib import Path
import re


def analyze(directory, fills):
    files = list(directory.rglob("*-benchmarkData.json"))
    if len(files) != 1:
        raise ValueError(f"Expected one benchmark result, found {files}")
    source = files[0]
    data = json.loads(source.read_text())
    expected = {(kind, fill) for kind in ("constructAndReadCache", "renderCachedVectors") for fill in fills}
    observed = set()
    rows = []
    for result in data["benchmarks"]:
        match = re.fullmatch(r"(?:EMULATOR_)?(constructAndReadCache|renderCachedVectors)\[filled=(false|true)\]", result["name"])
        if not match:
            raise ValueError(f"Unexpected benchmark: {result['name']}")
        kind, fill = match.groups()
        key = (kind, fill)
        if key in observed:
            raise ValueError(f"Duplicate result: {key}")
        observed.add(key)
        count_file = source.with_name(f"{'construction' if kind == 'constructAndReadCache' else 'render'}-{fill}.json")
        counts = json.loads(count_file.read_text())
        assert len(counts) == result["repeatIterations"]
        assert len({r["processId"] for r in counts}) == len(counts)
        assert all(r["vectors"] == 101 and r["cachedCalls"] == 101_000 and r["filled"] == (fill == "true") for r in counts)
        row = {
            "name": result["name"], "filled": fill == "true", "iterations": len(counts),
            "thermal_throttle_sleep_seconds": result["thermalThrottleSleepSeconds"],
        }
        if kind == "constructAndReadCache":
            metrics = result["metrics"]
            for name in ("first", "remaining", "cached"):
                assert metrics[f"Vector.{name}Count"]["runs"] == [1.0] * len(counts)
                row[f"{name}_ms"] = metrics[f"Vector.{name}SumMs"]
            row["cached_ns_per_indexed_getter_and_sink"] = row["cached_ms"]["median"] * 1_000_000 / 101_000
        else:
            assert all(r["complete"] and r["frames"] > 1 for r in counts)
            assert source.with_name(f"render-{fill}.png").is_file()
            assert all(value > 0 for value in result["metrics"]["frameCount"]["runs"])
            row["frame_count"] = result["metrics"]["frameCount"]
            row["frame_cpu_ms"] = result["sampledMetrics"]["frameDurationCpuMs"]
            row["frame_overrun_ms"] = result["sampledMetrics"].get("frameOverrunMs")
        rows.append(row)
    if observed != expected:
        raise ValueError(f"Incomplete benchmark matrix: expected {expected}, got {observed}")
    return {"source": str(source), "context": data["context"], "results": rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--fills", default="false", help="Plus-separated false/true values")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    fills = args.fills.split("+")
    if len(fills) != len(set(fills)) or not set(fills) <= {"false", "true"}:
        raise ValueError(args.fills)
    result = analyze(args.capture, fills)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(f"Verified {len(result['results'])} Android benchmark cases: {args.output}")


if __name__ == "__main__":
    main()
