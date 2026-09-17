#!/usr/bin/env python3
"""Build public PNG galleries and publish isolated, temporary Roborazzi report branches."""
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
        if name in baseline:
            row["base"] = save(baseline[name])
        if name in current:
            row["head"] = save(current[name])
        if diff_name in comparisons:
            row["diff"] = save(comparisons[diff_name])
        changes.append(row)
    report = {
        "head_sha": head_sha, "base_sha": base_sha,
        "changes": changes,
        "previews": [{"name": name, "image": save(data)} for name, data in current.items()],
        "technical_failure": bool(unmatched) or (outcome != "success" and not (outcome == "skipped" and not base_supported)),
        "outcome": outcome,
    }
    (destination / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    (destination / "README.md").write_text(markdown(report) + "\n")
    output("has_changes", bool(changes))
    output("technical_failure", report["technical_failure"])
    return report


def escape(text):
    value = html.escape(" ".join(text.splitlines()))
    for character in "|`[]()!*_\\@":
        value = value.replace(character, f"&#{ord(character)};")
    return value


def markdown(report, image_root=""):
    def image(path):
        url = image_root + path
        return f'<a href="{url}"><img src="{url}" width="280" alt="Screenshot"></a>'
    lines = [MARKER, "## Roborazzi previews", "",
             f'<!-- source:{report["head_sha"]}:{report["base_sha"]} -->',
             f'PR commit: `{report["head_sha"]}` · Base: `{report["base_sha"]}`', "",
             f'Visual changes: **{len(report["changes"])}**. Comparison: **{report["outcome"]}**.', ""]
    if report["technical_failure"]:
        lines += ["**The screenshot task failed. Visual approval cannot override this failure.**", ""]
    if report["changes"]:
        lines += [f"Review every change, then apply `{APPROVAL}` and rerun the check.", "",
                  "| Change | Preview | Base | PR | Diff |", "| --- | --- | --- | --- | --- |"]
        for row in report["changes"]:
            cells = [row["status"], escape(row["name"])]
            cells += [image(row[key]) if key in row else "—" for key in ("base", "head", "diff")]
            lines.append("| " + " | ".join(cells) + " |")
    lines += ["", "<details>", f'<summary>Current PR previews ({len(report["previews"])})</summary>', ""]
    for preview in report["previews"]:
        lines += [escape(preview["name"]), "", image(preview["image"]), ""]
    lines += ["</details>"]
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


def current_pr(prefix, repository, number, head_sha):
    pr = api(f"{prefix}/pulls/{number}")
    if (pr["state"] != "open" or pr["head"]["sha"] != head_sha
            or (pr["head"].get("repo") or {}).get("full_name") != repository):
        return None
    return pr


def publish(directory, repository, number):
    prefix, branch = context(repository, number)
    report = json.loads((directory / "report.json").read_text())
    for name in ("head_sha", "base_sha"):
        if not re.fullmatch(r"[0-9a-f]{40}", report[name]):
            raise ValueError("Invalid source commit")
    pr = current_pr(prefix, repository, number, report["head_sha"])
    if not pr:
        print("PR closed, changed, or from a fork; no report published.")
        output("published", False)
        return
    previous = bot_comment(prefix, number)
    receipt = f'<!-- source:{report["head_sha"]}:{report["base_sha"]} -->'
    if (report["changes"] and any(label["name"] == APPROVAL for label in pr["labels"])
            and (not previous or receipt not in previous["body"])):
        api(f"{prefix}/issues/{number}/labels/{APPROVAL}", "DELETE", missing=True)
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
    if not current_pr(prefix, repository, number, report["head_sha"]):
        print("PR changed during report preparation; no branch or comment updated.")
        output("published", False)
        return
    if api(f"{prefix}/git/ref/heads/{branch}", missing=True):
        api(f"{prefix}/git/refs/heads/{branch}", "PATCH", {"sha": commit, "force": True})
    else:
        api(f"{prefix}/git/refs", "POST", {"ref": f"refs/heads/{branch}", "sha": commit})
    url = f"https://github.com/{repository}"
    body = markdown(report, f"https://raw.githubusercontent.com/{repository}/{commit}/")
    links = (f"\n\n[Full report]({url}/tree/{branch}) · "
             f"[Download images]({url}/archive/refs/heads/{branch}.zip) · "
             f"[Workflow run]({url}/actions/runs/{os.environ['GITHUB_RUN_ID']})\n\n"
             "Images are public. The temporary report branch is removed when this PR closes.")
    # Keep GitHub's comment limit predictable; the full gallery always remains on the branch.
    if len(body.encode()) > 55000:
        body = f'{MARKER}\n{receipt}\n## Roborazzi previews\nVisual changes: **{len(report["changes"])}**. Open the full report to review every image.'
    body += links
    if previous:
        api(f'{prefix}/issues/comments/{previous["id"]}', "PATCH", {"body": body})
    else:
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
