#!/usr/bin/env python3
"""Summarize emitted Macrobenchmark 1.4 JSON and per-iteration icon counters."""

import argparse
import json
import re
import statistics
from pathlib import Path


def only_file(directory, pattern):
    matches = list(directory.rglob(pattern))
    if len(matches) != 1:
        raise ValueError(f"Expected one {pattern} below {directory}, found {len(matches)}")
    return matches[0]


def median_per_frame(metric, frames):
    if len(metric["runs"]) != len(frames["runs"]):
        raise ValueError("Metric and frame iteration counts differ")
    return statistics.median(
        work / count for work, count in zip(metric["runs"], frames["runs"]) if count > 0
    )


def timing_summary(path):
    data = json.loads(path.read_text())
    result = []
    for benchmark in data["benchmarks"]:
        match = re.fullmatch(r"animateAxes\[(baseline|shared|value|native)-(wght|FILL|GRAD|opsz|all)-(1|100)(-stress|-draw)?(-core|-diagnostic)?\]", benchmark["name"])
        if not match:
            raise ValueError(f"Unexpected benchmark name: {benchmark['name']}")
        renderer, axis, count, suffix, mode_suffix = match.groups()
        scenario = suffix.removeprefix("-") if suffix else "axes"
        instrumentation = mode_suffix.removeprefix("-") if mode_suffix else "historical"
        counters_path = path.with_name(f"{renderer}-{axis}-{count}{suffix or ''}{mode_suffix or ''}-counts.json")
        counters = json.loads(counters_path.read_text())
        if len(counters) != benchmark["repeatIterations"]:
            raise ValueError(f"Counter iteration mismatch: {counters_path}")
        for run in counters:
            if mode_suffix and run["diagnostics"] != (instrumentation == "diagnostic"):
                raise ValueError(f"Diagnostic mode mismatch: {counters_path}")
            check_effect_budget(run, animation=True)
        metrics = benchmark["metrics"]
        if renderer == "value" and any(metrics["TextStringSimpleNode::measureCount"]["runs"]):
            raise ValueError(f"Value overload unexpectedly used Compose text layout: {counters_path}")
        sampled = benchmark["sampledMetrics"]
        frames = metrics["frameCount"]
        overrun_runs = sampled.get("frameOverrunMs", {}).get("runs")
        overrun_samples = [value for run in overrun_runs for value in run] if overrun_runs else None
        integrity_available = all("distinctDrawnUpdates" in run for run in counters)
        if scenario == "draw" and not integrity_available:
            raise ValueError(f"Draw scenario requires content-tick instrumentation: {counters_path}")
        if integrity_available:
            for run in counters:
                expected_ticks = set(range(1, run["updates"] + 1))
                if scenario in ("axes", "draw") and set(run["drawnTicks"]) != expected_ticks:
                    raise ValueError(f"Animation updates were skipped: {counters_path}")
                if scenario == "draw" and set(run.get("layerTicks", [])) != expected_ticks:
                    raise ValueError(f"Draw scenario layer updates were skipped: {counters_path}")
                if scenario in ("axes", "draw") and renderer == "native" and any(run.get(key, 0) for key in (
                    "hostCompositions", "groupCompositions", "iconCompositions", "measures", "placements",
                )):
                    raise ValueError(f"Native animation restarted composition/layout: {counters_path}")
        result.append({
            "renderer": renderer, "axis": axis, "count": int(count), "scenario": scenario, "instrumentation": instrumentation,
            "size_dp": counters[0]["sizeDp"], "iterations": benchmark["repeatIterations"],
            "frame_cpu_ms": {key: sampled["frameDurationCpuMs"][key] for key in ("P50", "P90", "P95", "P99")},
            "frame_overrun_ms": {key: value for key, value in sampled.get("frameOverrunMs", {}).items() if key != "runs"},
            "positive_overrun_frames": None if overrun_samples is None else sum(value > 0 for value in overrun_samples),
            "overrun_sampled_frames": None if overrun_samples is None else len(overrun_samples),
            "median_frames": frames["median"],
            "median_counters": {
                key: statistics.median(run[key] for run in counters)
                for key in ("hostCompositions", "groupCompositions", "iconCompositions", "measures", "placements", "draws", "updates", "layerUpdates", "effectEvaluations")
                if all(key in run for run in counters)
            },
            "draw_calls_per_update": statistics.median(run["draws"] / int(count) / run["updates"] for run in counters),
            "effect_evaluations_per_group_update": statistics.median(
                run["effectEvaluations"] / ((10 if int(count) == 100 else 1) * run["updates"])
                for run in counters
            ) if scenario != "axes" and all("effectEvaluations" in run for run in counters) else None,
            "rendered_update_coverage": statistics.median(run["distinctDrawnUpdates"] / run["updates"] for run in counters) if integrity_available else None,
            "layer_update_coverage": statistics.median(run["distinctLayerUpdates"] / run["updates"] for run in counters)
                if scenario in ("stress", "draw") and all("distinctLayerUpdates" in run for run in counters) else None,
            "outer_sizes_px": sorted({tuple(bounds[2:]) for run in counters for bounds in run["bounds"]}),
            "effects_at_capture": [run["effects"] for run in counters if "effects" in run],
            "measured_sizes_px": sorted({tuple(size) for run in counters for size in run.get("measuredSizes", [])}),
            "median_trace_ms_per_frame": {
                name: median_per_frame(value, frames)
                for name, value in metrics.items() if name.endswith("SumMs")
            },
            "median_trace_counts": {name: value["median"] for name, value in metrics.items() if name.endswith("Count")},
            "thermal_throttle_sleep_seconds": benchmark["thermalThrottleSleepSeconds"],
            "sources": {"metrics": str(path), "counts": str(counters_path)},
        })
    return {"context": data["context"], "timing": result}


def check_effect_budget(record, animation):
    if "effectEvaluations" not in record or record.get("scenario", "axes") == "axes":
        return
    groups = 10 if record["count"] == 100 else 1
    evaluations = record["effectEvaluations"]
    if not 0 <= evaluations <= groups * record["updates"]:
        raise ValueError("Effects evaluated more than once per group/update")
    if animation and evaluations == 0:
        raise ValueError("Animated effects were not evaluated")


def table(headers, rows):
    def cell(value):
        return "n/a" if value is None else f"{value:.3f}" if isinstance(value, float) else str(value)
    return "\n".join([
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
        *("| " + " | ".join(map(cell, row)) + " |" for row in rows),
    ])


def geometry_summary(path):
    records = json.loads(path.read_text())
    bounds_by_case = {}
    baseline = {(r["axis"], r["progress"]): r for r in records if r["renderer"] == "baseline"}
    comparisons = []
    for record in records:
        key = (record["renderer"], record["axis"], record["sizeDp"])
        previous = bounds_by_case.setdefault(key, record["bounds"])
        if previous != record["bounds"]:
            raise ValueError(f"Outer layout changes with settings: {key}")
        if record["ink"]["pixels"] <= 0:
            raise ValueError(f"Empty glyph: {key}")
        if record["renderer"] == "native" and any(record[k] != 0 for k in ("hostCompositions", "iconCompositions", "measures", "placements")):
            raise ValueError(f"Native setting update invalidated composition/layout: {key}")
        expected = baseline.get((record["axis"], record["progress"]))
        if record["renderer"] == "baseline" or expected is None:
            continue
        if record["bounds"] != expected["bounds"]:
            raise ValueError(f"Renderer outer layouts differ: {key}")
        delta = {key: record["ink"][key] - expected["ink"][key] for key in ("left", "top", "width", "height", "pixels")}
        if delta["width"] or delta["height"] or abs(delta["left"]) > 1 or abs(delta["top"]) > 1 or abs(delta["pixels"]) > max(1, expected["ink"]["pixels"] * .01):
            raise ValueError(f"Renderer ink geometry differs beyond tolerance: {key}: {delta}")
        comparisons.append({"renderer": record["renderer"], "axis": record["axis"], "progress": record["progress"],
                            "outer_bounds_px": record["bounds"][0], "baseline_ink": expected["ink"],
                            "actual_ink": record["ink"], "delta": delta})
    return {"source": str(path), "records": records, "comparisons": comparisons,
            "checks": "Stable outer bounds; native composition/layout counts zero; ink dimensions equal, position within 1 px, coverage within 1%."}


def stress_geometry_summary(path, scenario="stress"):
    records = json.loads(path.read_text())
    baseline = {r["progress"]: r for r in records if r["renderer"] == "baseline"}
    comparisons = []
    for record in records:
        if record.get("scenario", "stress") != scenario:
            raise ValueError(f"Expected {scenario} geometry: {path}")
        check_effect_budget(record, animation=False)
        if scenario == "draw" and record["renderer"] == "native" and any(record[k] for k in (
            "hostCompositions", "groupCompositions", "iconCompositions", "measures", "placements",
        )):
            raise ValueError("Native draw snapshot updated composition/layout")
        if record["ink"]["coloredPixels"] <= 0:
            raise ValueError("Stress capture has no colored ink")
        expected = baseline.get(record["progress"])
        if record["renderer"] == "baseline" or expected is None:
            continue
        if record["bounds"] != expected["bounds"] or record["captureBounds"] != expected["captureBounds"] or record["measuredSizes"] != expected["measuredSizes"]:
            raise ValueError("Stress renderer layout/capture cells differ")
        if record["effects"] != expected["effects"]:
            raise ValueError("Stress renderer inputs differ")
        delta = {k: record["ink"][k] - expected["ink"][k] for k in ("left", "top", "width", "height")}
        color_delta = [a - b for a, b in zip(record["ink"]["meanRgb"], expected["ink"]["meanRgb"])]
        if any(abs(value) > 2 for value in delta.values()) or any(abs(value) > 3 for value in color_delta):
            raise ValueError("Stress renderer ink position/color differs beyond tolerance")
        comparisons.append({"renderer": record["renderer"], "progress": record["progress"], "ink_delta_px": delta, "mean_rgb_delta": color_delta})
    for renderer in {r["renderer"] for r in records}:
        cases = [r for r in records if r["renderer"] == renderer]
        sizes = {tuple(r["measuredSizes"][0]) for r in cases}
        if (scenario == "draw" and len(sizes) != 1) or (scenario == "stress" and len(sizes) < 2):
            raise ValueError(f"Incorrect {scenario} measured-size behavior: {renderer}")
        if len({tuple(r["ink"]["meanRgb"]) for r in cases}) < 2:
            raise ValueError(f"{scenario} visible color did not change: {renderer}")
        for key in ("fontSizeDp", "colorArgb", "alpha", "scale", "rotationDegrees", "translationXDp", "translationYDp"):
            values = {r["effects"][key] for r in cases}
            if scenario == "draw" and key == "fontSizeDp":
                if len(values) != 1:
                    raise ValueError(f"Draw font size changed: {renderer}")
            elif len(values) < 2:
                raise ValueError(f"{scenario} {key} did not change: {renderer}")
        for axis in ("wght", "FILL", "GRAD", "opsz"):
            if len({r["effects"]["axes"][axis] for r in cases}) < 2:
                raise ValueError(f"Stress font axis {axis} did not change: {renderer}")
        if len({tuple(r["captureBounds"][0]) for r in cases}) != 1:
            raise ValueError(f"Stress capture cell changed: {renderer}")
    checks = "Measured size and visible color change in fixed cells; composition/layout work is expected."
    if scenario == "draw":
        checks = "Measured size stays fixed while visible colors, all font axes and transforms change; native composition/layout counts stay zero."
    return {"source": str(path), "scenario": scenario, "records": records, "comparisons": comparisons,
            "checks": checks + " Matched renderer geometry within 2 px and mean RGB within 3 levels."}


def markdown(summary):
    context = summary["context"]
    build = context["build"]
    lines = [
        "# Animated font measurements", "",
        f"Device: `{build['model']}` / `{build['device']}`, Android API {build['version']['sdk']}, "
        f"{context['cpuCoreCount']} CPU cores. Fingerprint: `{build['fingerprint']}`.", "",
        "Results describe the recorded device and configuration. "
        "Per-frame CPU duration, deadline overruns, and animation update coverage are separate measurements.", "",
        table(["Scenario", "Instrumentation", "Renderer", "Axis", "Icons", "dp", "Runs", "CPU P50 ms", "CPU P99 ms", "Overrun P99 ms", "Positive overruns / samples"], [
            [r['scenario'], r['instrumentation'], r['renderer'], r['axis'], r['count'], r['size_dp'], r['iterations'], r['frame_cpu_ms']['P50'], r['frame_cpu_ms']['P99'],
             r['frame_overrun_ms'].get('P99'), f"{r['positive_overrun_frames']} / {r['overrun_sampled_frames']}"] for r in summary['timing']
        ]), "",
        "Counts below are medians per iteration. Content coverage observes font settings in the first "
        "icon's draw inside its graphics layer; layer coverage observes the first icon's layer properties "
        "separately. Cached parent draw calls cannot establish whether a layer updated. `n/a` means "
        "not applicable or instrumentation absent from that capture.", "",
        table(["Scenario", "Renderer", "Icons", "Frames", "Updates", "Effect evaluations", "Draws/icon/update", "Content update coverage", "Layer update coverage", "Host compositions", "Group compositions", "Icon compositions", "Outer measures", "Inner text measures"], [
            [r['scenario'], r['renderer'], r['count'], r['median_frames'], r['median_counters']['updates'], r['median_counters'].get('effectEvaluations'), r['draw_calls_per_update'],
             r['rendered_update_coverage'], r['layer_update_coverage'], r['median_counters']['hostCompositions'], r['median_counters'].get('groupCompositions'), r['median_counters']['iconCompositions'],
             r['median_counters']['measures'], r['median_trace_counts'].get('TextStringSimpleNode::measureCount')]
            for r in summary['timing']
        ]), "",
        "Trace durations are sums divided by that iteration's measured frame count, then median across "
        "iterations. Draw recording does not include all RenderThread/GPU work. Outer zero measures "
        "do not imply zero text shaping or native metric work.", "",
        table(["Scenario", "Renderer", "Icons", "Draw ms/frame", "Layer ms/frame", "Effects ms/frame", "Inner text measure ms/frame", "Android layout init ms/frame", "Outer sizes px"], [
            [r['scenario'], r['renderer'], r['count'], r['median_trace_ms_per_frame'].get('SymbolBenchmark.drawSumMs'),
             r['median_trace_ms_per_frame'].get('SymbolBenchmark.layerSumMs'),
             r['median_trace_ms_per_frame'].get('SymbolBenchmark.effectsSumMs'),
             r['median_trace_ms_per_frame'].get('TextStringSimpleNode::measureSumMs'),
             r['median_trace_ms_per_frame'].get('TextLayout:initLayoutSumMs'), r['outer_sizes_px']]
            for r in summary['timing']
        ]), "",
    ]
    if "geometry" in summary:
        lines += ["## Geometry", "", summary["geometry"]["checks"], "",
                  "Ink rectangles are `(left, top, width, height)` within the icon; outer rectangles use screen pixels.", "",
                  table(["Renderer", "Axis", "Progress", "Outer px", "Baseline ink px", "Renderer ink px", "Baseline / renderer dark pixels", "Delta x,y,pixels"], [
                      [r["renderer"], r["axis"], r["progress"], r["outer_bounds_px"],
                       [r["baseline_ink"][k] for k in ("left", "top", "width", "height")],
                       [r["actual_ink"][k] for k in ("left", "top", "width", "height")],
                       f"{r['baseline_ink']['pixels']} / {r['actual_ink']['pixels']}",
                       [r["delta"][k] for k in ("left", "top", "pixels")]]
                      for r in summary["geometry"]["comparisons"]
                  ]), ""]
    for geometry_key, title in (("stress_geometry", "Combined effects"), ("draw_geometry", "Fixed-size draw effects")):
        if geometry_key not in summary:
            continue
        lines += [f"## {title}", "", summary[geometry_key]["checks"], "",
                  table(["Renderer", "Progress", "Measured size px", "Icon bounds px", "Capture cell px", "Colored pixels", "Mean RGB", "Effects", "Compositions / measures / placements"], [
                      [r["renderer"], r["progress"], r["measuredSizes"][0], r["bounds"][0], r["captureBounds"][0], r["ink"]["coloredPixels"],
                       [round(v, 2) for v in r["ink"]["meanRgb"]], r.get("effects"),
                       [r[k] for k in ("iconCompositions", "measures", "placements")]]
                      for r in summary[geometry_key]["records"]
                  ]), ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("timing", type=Path, help="One preserved timing run directory")
    parser.add_argument("--output", type=Path, help="Report directory; defaults to TIMING/summary")
    parser.add_argument("--geometry", type=Path, help="Preserved geometry directory or geometry.json")
    parser.add_argument("--stress-geometry", type=Path, help="Preserved stress geometry directory or stress-geometry.json")
    parser.add_argument("--draw-geometry", type=Path, help="Preserved draw geometry directory or draw-geometry.json")
    args = parser.parse_args()
    summary = timing_summary(only_file(args.timing, "*-benchmarkData.json"))
    if args.geometry:
        path = args.geometry if args.geometry.is_file() else only_file(args.geometry, "geometry.json")
        summary["geometry"] = geometry_summary(path)
    if args.stress_geometry:
        path = args.stress_geometry if args.stress_geometry.is_file() else only_file(args.stress_geometry, "stress-geometry.json")
        summary["stress_geometry"] = stress_geometry_summary(path)
    if args.draw_geometry:
        path = args.draw_geometry if args.draw_geometry.is_file() else only_file(args.draw_geometry, "draw-geometry.json")
        summary["draw_geometry"] = stress_geometry_summary(path, scenario="draw")
    directory = args.output or args.timing / "summary"
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    report = markdown(summary)
    (directory / "summary.md").write_text(report)
    print(report)


if __name__ == "__main__":
    # Verify per-frame normalization uses paired runs rather than a ratio of independent medians.
    assert median_per_frame({"runs": [10, 60, 90]}, {"runs": [10, 20, 100]}) == 1
    main()
