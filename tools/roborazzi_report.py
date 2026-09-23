#!/usr/bin/env python3
"""Report visual changes on isolated, temporary Roborazzi report branches."""
import argparse
import base64
import hashlib
import html
import json
import os
from pathlib import Path
import re
import subprocess

MARKER = "<!-- symbols-roborazzi-report -->"
APPROVAL = "ui-review-approved"
PNG = b"\x89PNG\r\n\x1a\n"
MAX_BYTES = 50 * 1024 * 1024
MAX_INLINE_ROWS = 25


def output(name, value):
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
            print(f"{name}={str(value).lower()}", file=stream)


def images(root):
    if not root.is_dir() or root.is_symlink():
        raise ValueError(f"Missing or unsafe screenshot directory: {root}")
    result = {}
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"Screenshot symlinks are not allowed: {path}")
        if path.is_file() and path.suffix.lower() == ".png":
            if path.stat().st_size > 10 * 1024 * 1024:
                raise ValueError(f"Screenshot exceeds 10 MiB: {path}")
            data = path.read_bytes()
            if not data.startswith(PNG):
                raise ValueError(f"Not a PNG: {path}")
            result[path.relative_to(root).as_posix()] = data
    return result


def build_report(base, head, diffs, destination, outcome, base_supported, head_sha, base_sha):
    baseline, current, comparisons = images(base), images(head), images(diffs)
    if not baseline and not current:
        raise ValueError("Neither revision produced screenshot coverage")
    destination.mkdir(parents=True, exist_ok=False)
    (destination / "images").mkdir()
    stored = {}

    def save(data):
        name = "images/" + hashlib.sha256(data).hexdigest() + ".png"
        stored[name] = data
        if sum(map(len, stored.values())) > MAX_BYTES:
            raise ValueError("Report exceeds 50 MiB; reduce screenshot scope before publishing")
        (destination / name).write_bytes(data)
        return name

    diff_images = {name[:-12] + ".png": data for name, data in comparisons.items() if name.endswith("_compare.png")}
    unmatched = diff_images.keys() - baseline.keys() - current.keys()
    changes = []
    for name in sorted(baseline.keys() | current.keys() | diff_images.keys()):
        diff_name = name[:-4] + "_compare.png"
        if name in unmatched:
            status = "Changed"
        elif name not in baseline:
            status = "Added"
        elif name not in current:
            status = "Removed"
        elif diff_name in comparisons:
            status = "Changed"
        elif baseline[name] != current[name] and outcome == "failure":
            status = "Changed"
        else:
            # Roborazzi's successful comparison takes precedence over PNG metadata differences.
            continue
        row = {"name": name, "status": status}
        if diff_name in comparisons:
            row["image"] = save(comparisons[diff_name])
            row["image_label"] = "Diff"
        elif name in current:
            row["image"] = save(current[name])
            row["image_label"] = "PR image"
        else:
            row["image"] = save(baseline[name])
            row["image_label"] = "Base image"
        changes.append(row)
    report = {
        "head_sha": head_sha, "base_sha": base_sha,
        "changes": changes,
        "technical_failure": bool(unmatched) or (outcome != "success" and not (outcome == "skipped" and not base_supported)),
        "outcome": outcome,
    }
    (destination / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    (destination / "README.md").write_text(markdown(report) + "\n")
    output("has_changes", bool(changes))
    output("change_count", len(changes))
    output("technical_failure", report["technical_failure"])
    return report


def escape(text):
    value = html.escape(" ".join(text.splitlines()))
    for character in "|`[]()!*_\\@":
        value = value.replace(character, f"&#{ord(character)};")
    return value


def preview_details(name):
    parts = Path(name).stem.split(".")
    class_index = next((index for index, part in enumerate(parts) if part.endswith("Kt")), None)
    if class_index is None:
        return name, "Default", "", ""
    file_name = parts[class_index][:-2] + ".kt"
    preview = parts[class_index + 1] if len(parts) > class_index + 1 else Path(name).stem
    variant = ".".join(parts[class_index + 2:]) or "Default"
    variant = re.sub(r"_W(\d+)dp_H(\d+)dp", r" (\1x\2 dp)", variant)
    variant = re.sub(r"_WIDTH_(\d+)DP_HEIGHT_(\d+)DP", r" (\1x\2 dp)", variant)
    variant = re.sub(r"_FONT_(\d+)_(\d+)f", r" font \1.\2x", variant)
    variant = " ".join(variant.replace("_", " ").replace(",", ", ").split())
    source = ""
    if all(part.isidentifier() for part in parts[:class_index + 1]):
        package = Path(*parts[:class_index]) / file_name
        for source_set in ("commonMain", "androidMain", "iosMain", "commonTest", "androidUnitTest"):
            candidate = Path("samples/image-vector-migration/src") / source_set / "kotlin" / package
            if candidate.is_file():
                source = candidate.as_posix()
                break
    return file_name, variant, preview, source


def markdown(report, image_root="", source_root=""):
    status = "Failed" if report["technical_failure"] else {
        "success": "Passed", "failure": "Failed", "skipped": "Skipped",
    }[report["outcome"]]
    lines = [MARKER, "## Roborazzi Visual Comparison", "",
             f'<!-- source:{report["head_sha"]}:{report["base_sha"]} -->',
             f'Status: **{status}**',
             f'Compared against generated screenshots from: `{report["base_sha"]}`',
             f'PR commit: `{report["head_sha"]}`', "",
             f'Visual changes: **{len(report["changes"])}**', ""]
    if report["technical_failure"]:
        lines += ["**The screenshot task failed. Visual approval cannot override this failure.**", ""]
    elif not report["changes"]:
        lines += ["No Roborazzi visual changes were detected."]
    else:
        lines += [f"Review every screenshot below for the current PR commit. If the visual changes are intentional, "
                  f"apply the `{APPROVAL}` PR label to let this check pass.",
                  "A later head or base commit makes that approval stale; updated screenshots require review again.", ""]
    for change, title in (("Added", "Added"), ("Changed", "Modified"), ("Removed", "Removed")):
        rows = [row for row in report["changes"] if row["status"] == change]
        if not rows:
            continue
        lines += ["<details>", f"<summary>{title} baseline image profiles: {len(rows)}</summary>", "",
                  "| Baseline profile | Variant | Image |", "|---|---|---|"]
        for index, row in enumerate(rows):
            file_name, variant, preview, source = preview_details(row["name"])
            profile = f"[{escape(file_name)}]({source_root}{source})" if source and source_root else f"<code>{escape(file_name)}</code>"
            if preview:
                profile += f"<br><sub>Preview: <code>{escape(preview)}</code></sub>"
            url = image_root + row["image"]
            image = f'[Open {row["image_label"].lower()}]({url})'
            if index < MAX_INLINE_ROWS:
                image += f'<br><img src="{url}" width="360" alt="Roborazzi {row["image_label"].lower()}">'
            lines.append(f"| {profile} | {escape(variant)} | {image} |")
        if len(rows) > MAX_INLINE_ROWS:
            lines += ["", f"Only the first {MAX_INLINE_ROWS} {title.lower()} rows are shown inline; remaining rows link to report images."]
        lines += ["", "</details>", ""]
    return "\n".join(lines)


def api(endpoint, method="GET", data=None, missing=False, paginate=False):
    command = ["gh", "api", "--hostname", "github.com", "--method", method, endpoint]
    if paginate:
        command += ["--paginate", "--slurp"]
    if data is not None:
        command += ["--input", "-"]
    result = subprocess.run(command, input=json.dumps(data) if data is not None else None,
                            text=True, capture_output=True)
    if result.returncode:
        if missing and "(HTTP 404)" in result.stderr:
            return None
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout) if result.stdout.strip() else None


def context(repository, number):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) or number <= 0:
        raise ValueError("Invalid repository or PR number")
    return f"repos/{repository}", f"roborazzi-pr-{number}"


def bot_comment(prefix, number):
    pages = api(f"{prefix}/issues/{number}/comments?per_page=100", paginate=True)
    return next((c for page in pages for c in page
                 if c["user"]["login"] == "github-actions[bot]" and c["body"].startswith(MARKER)), None)


def current_pr(prefix, repository, number, head_sha, base_sha):
    pr = api(f"{prefix}/pulls/{number}")
    if (pr["state"] != "open" or pr["head"]["sha"] != head_sha or pr["base"]["sha"] != base_sha
            or (pr["head"].get("repo") or {}).get("full_name") != repository):
        return None
    return pr


def publish(directory, repository, number):
    prefix, branch = context(repository, number)
    report = json.loads((directory / "report.json").read_text())
    for name in ("head_sha", "base_sha"):
        if not re.fullmatch(r"[0-9a-f]{40}", report[name]):
            raise ValueError("Invalid source commit")
    pr = current_pr(prefix, repository, number, report["head_sha"], report["base_sha"])
    if not pr:
        print("PR closed, changed, or from a fork; no report published.")
        output("published", False)
        return
    previous = bot_comment(prefix, number)
    receipt = f'<!-- source:{report["head_sha"]}:{report["base_sha"]} -->'
    if (report["changes"] and any(label["name"] == APPROVAL for label in pr["labels"])
            and (not previous or receipt not in previous["body"])):
        api(f"{prefix}/issues/{number}/labels/{APPROVAL}", "DELETE", missing=True)
    image_root = ""
    if report["changes"]:
        entries = []
        total = 0
        for path in sorted(directory.rglob("*")):
            if path.is_symlink():
                raise ValueError("Report symlinks are not allowed")
            if not path.is_file():
                continue
            name = path.relative_to(directory).as_posix()
            if name not in ("README.md", "report.json") and not re.fullmatch(r"images/[0-9a-f]{64}\.png", name):
                raise ValueError(f"Unexpected report file: {name}")
            data = path.read_bytes()
            if name.startswith("images/") and (not data.startswith(PNG) or Path(name).stem != hashlib.sha256(data).hexdigest()):
                raise ValueError("Invalid or modified report PNG")
            total += len(data)
            if total > MAX_BYTES:
                raise ValueError("Report exceeds 50 MiB")
            blob = api(f"{prefix}/git/blobs", "POST", {"content": base64.b64encode(data).decode(), "encoding": "base64"})
            entries.append({"path": name, "mode": "100644", "type": "blob", "sha": blob["sha"]})
        tree = api(f"{prefix}/git/trees", "POST", {"tree": entries})
        commit = api(f"{prefix}/git/commits", "POST", {
            "message": f'Roborazzi PR #{number}: {report["head_sha"]}', "tree": tree["sha"], "parents": [],
        })["sha"]
        if not current_pr(prefix, repository, number, report["head_sha"], report["base_sha"]):
            print("PR changed during report preparation; no branch or comment updated.")
            output("published", False)
            return
        if api(f"{prefix}/git/ref/heads/{branch}", missing=True):
            api(f"{prefix}/git/refs/heads/{branch}", "PATCH", {"sha": commit, "force": True})
        else:
            api(f"{prefix}/git/refs", "POST", {"ref": f"refs/heads/{branch}", "sha": commit})
        image_root = f"https://raw.githubusercontent.com/{repository}/{commit}/"
    else:
        if not current_pr(prefix, repository, number, report["head_sha"], report["base_sha"]):
            print("PR changed during report preparation; no branch or comment updated.")
            output("published", False)
            return
        api(f"{prefix}/git/refs/heads/{branch}", "DELETE", missing=True)
    url = f"https://github.com/{repository}"
    body = markdown(report, image_root, f"{url}/blob/{report['head_sha']}/")
    links = f"\n\n[Workflow run]({url}/actions/runs/{os.environ['GITHUB_RUN_ID']})"
    if report["changes"]:
        links += (f" · [Full report]({url}/tree/{branch})\n\n"
                  f"[Download all report images (.zip)]({url}/archive/refs/heads/{branch}.zip)\n\n"
                  "<sub>Report images are hosted on the temporary companion branch for this PR "
                  "and are deleted when the PR closes.</sub>")
    # Keep GitHub's comment limit predictable; the full change report remains on the branch.
    if len(body.encode()) > 55000:
        failure = " Screenshot comparison failed; visual approval cannot override it." if report["technical_failure"] else ""
        body = (f'{MARKER}\n{receipt}\n## Roborazzi Visual Comparison\n'
                f'Visual changes: **{len(report["changes"])}**.{failure} Open the full report to review every change.')
    body += links
    if previous:
        api(f'{prefix}/issues/comments/{previous["id"]}', "PATCH", {"body": body})
    elif report["changes"] or report["technical_failure"]:
        api(f"{prefix}/issues/{number}/comments", "POST", {"body": body})
    output("published", True)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as stream:
            stream.write(body + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("build", "publish"))
    parser.add_argument("--report", type=Path)
    parser.add_argument("--base", type=Path)
    parser.add_argument("--head", type=Path)
    parser.add_argument("--diffs", type=Path)
    parser.add_argument("--outcome", choices=("success", "failure", "skipped"))
    parser.add_argument("--base-supported", choices=("true", "false"), default="true")
    args = parser.parse_args()
    if args.action == "build":
        build_report(args.base, args.head, args.diffs, args.report, args.outcome,
                     args.base_supported == "true", os.environ["PR_HEAD_SHA"], os.environ["PR_BASE_SHA"])
    elif args.action == "publish":
        publish(args.report, os.environ["GITHUB_REPOSITORY"], int(os.environ["PR_NUMBER"]))


if __name__ == "__main__":
    main()
