#!/usr/bin/env python3
"""Release preflight and retry checks; never uploads packages."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen

TAG = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[a-z][a-z0-9]*)?\Z")
SNAPSHOT = re.compile(r"((?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))-SNAPSHOT-([0-9a-f]{8})\Z")
GROUP = "io.github.hlcaptain"
PLUGIN = GROUP + ".symbol-fonts"
GITHUB_PACKAGES = "https://maven.pkg.github.com/hlcaptain/symbols"
JOB_NAMES = {
    "libraries": "Publish Central libraries",
    "tooling": "Publish Central tooling and Plugin Portal",
    "portal": "Publish Central tooling and Plugin Portal",
}
LIBRARIES = ["symbols-core", "symbols-variant-font-core", "symbols-material-core", "symbols-material-compose"]
LIBRARIES += [f"symbols-material-{s}{suffix}" for s in ("outlined", "rounded", "sharp") for suffix in ("", "-static")]
LIBRARIES += [f"symbols-material-{kind}-{s}" for kind in ("drawables", "compose-drawables", "vectors") for s in ("outlined", "rounded", "sharp")]
LIBRARIES += ["symbols-material-vectors-themed"]


def version_from_tag(tag):
    if not SNAPSHOT.fullmatch(tag) and (not TAG.fullmatch(tag) or "snapshot" in tag.lower()):
        raise ValueError("Release tags must use MAJOR.MINOR.PATCH with an optional lowercase qualifier "
                         "(e.g. -alpha01), or MAJOR.MINOR.PATCH-SNAPSHOT-<8 lowercase hex characters>, "
                         "without a v prefix, leading zeros, or a mutable SNAPSHOT suffix.")
    return tag


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def release_commit(tag):
    version_from_tag(tag)
    commit = git("rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}")
    subprocess.run(["git", "merge-base", "--is-ancestor", commit, "refs/remotes/origin/main"], check=True)
    snapshot = SNAPSHOT.fullmatch(tag)
    if snapshot and snapshot[2] != commit[:8]:
        raise ValueError("The snapshot tag hash must match its commit's first eight characters.")
    if (not snapshot and os.environ.get("GITHUB_EVENT_NAME") == "workflow_dispatch"
            and os.environ.get("GITHUB_SHA") != commit):
        raise ValueError("Manual retries must run from the tag: gh workflow run publish.yml --ref TAG -f tag=TAG")
    if os.environ.get("GITHUB_EVENT_NAME") == "push":
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        if event.get("deleted") or (event.get("forced") and not (snapshot and event.get("ref") == "refs/heads/main")):
            raise ValueError("Release tags cannot be deleted or moved.")
        if event.get("after") not in {commit, git("rev-parse", f"refs/tags/{tag}")}:
            raise ValueError("The release tag changed after this event.")
    return commit


def snapshot_tag(commit):
    """Choose a stable release line, retaining an already assigned snapshot on retries."""
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Snapshots require a full Git commit SHA.")
    subprocess.run(["git", "merge-base", "--is-ancestor", commit, "refs/remotes/origin/main"], check=True)
    existing = [tag for tag in git("tag", "--points-at", commit).splitlines() if SNAPSHOT.fullmatch(tag)]
    if len(existing) > 1:
        raise ValueError("Multiple snapshot tags point to this commit; inspect them before retrying.")
    if existing:
        if SNAPSHOT.fullmatch(existing[0])[2] != commit[:8]:
            raise ValueError("The existing snapshot tag hash does not match this commit.")
        return existing[0]
    stable = [tag for tag in git("tag", "--merged", commit).splitlines() if TAG.fullmatch(tag) and "-" not in tag]
    if not stable:
        raise ValueError("A stable release tag on main is required before creating snapshots.")
    base = max(stable, key=lambda tag: tuple(map(int, tag.split("."))))
    tag = f"{base}-SNAPSHOT-{commit[:8]}"
    if git("tag", "--list", tag) and git("rev-parse", f"refs/tags/{tag}^{{commit}}") != commit:
        raise ValueError("Snapshot tag hash collision; the existing tag must not be moved.")
    return tag


def snapshot_release():
    if os.environ.get("GITHUB_EVENT_NAME") != "push" or os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise ValueError("Automatic snapshots require a push to main.")
    commit = os.environ["GITHUB_SHA"]
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    if (event.get("ref") != "refs/heads/main" or event.get("deleted")
            or event.get("after") != commit or git("rev-parse", "HEAD") != commit):
        raise ValueError("The checked-out commit must match the main push event.")
    tag = snapshot_tag(commit)
    return {"tag": tag, "version": tag, "commit": commit,
            "tag_exists": str(bool(git("tag", "--list", tag))).lower()}


def pom_url(base, group, artifact, version):
    return f"{base}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"


def publication_urls(component, version):
    # Recovery runs these tools against the tagged source, which may predate optional packs.
    libraries = LIBRARIES + [
        f"symbols-material-drawables-{style}-{variant}"
        for style in ("outlined", "rounded", "sharp")
        for variant in ("filled", "automirrored", "automirrored-filled")
        if Path(f"symbols/material-drawables-{style}-{variant}/build.gradle.kts").is_file()
    ]
    if SNAPSHOT.fullmatch(version):
        if component == "portal":
            return []  # Testing snapshots never go to the Plugin Portal.
        if component == "libraries":
            artifacts = list(libraries)
            for artifact in libraries:
                if not artifact.startswith("symbols-material-drawables-"):
                    artifacts.extend(f"{artifact}-{target}" for target in
                                     ("android", "iosarm64", "iossimulatorarm64", "js", "jvm", "wasm-js"))
            return [pom_url(GITHUB_PACKAGES, GROUP, artifact, version) for artifact in artifacts]
        return [pom_url(GITHUB_PACKAGES, GROUP, artifact, version)
                for artifact in ("symbol-generator-core", "symbol-gradle-plugin")] + [
            pom_url(GITHUB_PACKAGES, PLUGIN, PLUGIN + ".gradle.plugin", version)]
    central = "https://repo.maven.apache.org/maven2"
    if component == "libraries":
        return [pom_url(central, GROUP, artifact, version) for artifact in libraries]
    if component == "tooling":
        return [pom_url(central, GROUP, artifact, version) for artifact in ("symbol-generator-core", "symbol-gradle-plugin")] + [
            pom_url(central, PLUGIN, PLUGIN + ".gradle.plugin", version)]
    # The Portal Maven endpoint can proxy Central; only its version listing proves registration.
    return [f"https://plugins.gradle.org/plugin/{PLUGIN}/{version}"]


def exists(url):
    try:
        method = "GET" if url.startswith("https://plugins.gradle.org/plugin/") else "HEAD"
        request = Request(url, method=method)
        if url.startswith(GITHUB_PACKAGES + "/"):
            credentials = f"{os.environ['GITHUB_ACTOR']}:{os.environ['GH_TOKEN']}".encode()
            # Do not forward credentials if the registry redirects to artifact storage.
            request.add_unredirected_header("Authorization", "Basic " + base64.b64encode(credentials).decode())
        with urlopen(request, timeout=30) as response:
            return response.status == 200
    except HTTPError as error:
        if error.code == 404:
            return False
        if (error.code == 400 and url.startswith("https://plugins.gradle.org/plugin/")
                and b"<title>Gradle - Plugin Not Found</title>" in error.read(65536)):
            return False
        raise  # Authentication, rate limits and server failures are not "unpublished".


def prior_success(component, tag, checks, commit=None):
    if SNAPSHOT.fullmatch(tag) and not commit:
        return False
    name = (f"Publish GitHub {component} ({tag}, {commit})" if SNAPSHOT.fullmatch(tag)
            else f"{JOB_NAMES[component]} ({tag})")
    return any(c["name"] in {name, f"Publish snapshot / {name}"} and c.get("conclusion") == "success"
               and c.get("app", {}).get("slug") == "github-actions" for c in checks)


def publication_done(component, tag, checks, commit=None):
    if SNAPSHOT.fullmatch(tag) and component == "portal":
        return True
    # Successful jobs also cover CDN propagation and a first Portal submission awaiting approval.
    if prior_success(component, tag, checks, commit):
        return True
    present = [exists(url) for url in publication_urls(component, version_from_tag(tag))]
    if SNAPSHOT.fullmatch(tag) and any(present):
        raise ValueError(f"Unconfirmed GitHub {component} publication already exists; inspect it before retrying. "
                         "Do not overwrite a testing snapshot.")
    if any(present) and not all(present):
        raise ValueError(f"Part of the {component} release already exists; inspect the deployment before retrying.")
    return all(present)


def require_unpublished_manifest(path, version):
    if not SNAPSHOT.fullmatch(version):
        raise ValueError("The GitHub Packages manifest is only for testing snapshots.")
    manifest = json.loads(Path(path).read_text())
    if manifest["version"] != version or not manifest["paths"]:
        raise ValueError("Publication manifest must contain this snapshot's files.")
    for file in manifest["paths"]:
        if (not re.fullmatch(r"[A-Za-z0-9_./-]+", file) or ".." in file.split("/")
                or not file.startswith("io/github/hlcaptain/") or f"/{version}/" not in file):
            raise ValueError("Unexpected file in publication manifest.")
        if exists(f"{GITHUB_PACKAGES}/{file}"):
            raise ValueError(f"A snapshot file already exists: {file}; inspect the partial publication, do not overwrite it.")
    print(f"Authenticated absence verified for {len(manifest['paths'])} snapshot files.")


def github_pages(path):
    return json.loads(subprocess.check_output(["gh", "api", "--paginate", "--slurp", path], text=True))


def publication_checks(repository, commit, tag):
    checks = [check for page in github_pages(
        f"repos/{repository}/commits/{commit}/check-runs?filter=all&per_page=100"
    ) for check in page["check_runs"]]
    if SNAPSHOT.fullmatch(tag):
        # Recovery can use newer publisher code while keeping the artifact source tag immutable.
        runs = [run for page in github_pages(
            f"repos/{repository}/actions/workflows/publish.yml/runs?event=workflow_dispatch&per_page=100"
        ) for run in page["workflow_runs"]]
        for run in runs:
            if run["display_title"] != f"Release {tag}" or str(run["id"]) == os.environ.get("GITHUB_RUN_ID"):
                continue
            jobs = [job for page in github_pages(
                f"repos/{repository}/actions/runs/{run['id']}/jobs?filter=all&per_page=100"
            ) for job in page["jobs"]]
            if not any(job["name"] == "Publication preflight" and job["conclusion"] == "success" for job in jobs):
                continue
            checks.extend({"name": job["name"], "conclusion": job["conclusion"],
                           "app": {"slug": "github-actions"}} for job in jobs)
    return checks


def wait_for_publication(component, version, timeout_seconds=7200):
    urls = publication_urls(component, version)
    pending = urls
    deadline = time.monotonic() + timeout_seconds
    while pending:
        pending = [url for url in pending if not exists(url)]
        if not pending:
            print(f"{component}: all {len(urls)} Maven coordinates are public.", flush=True)
            return
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError(
                "Publication is still not fully visible. Inspect the existing registry publication; "
                "do not re-upload while it is pending. Once published, start "
                "a fresh tag-scoped workflow run so preflight checks the registries again."
            )
        print(f"{component}: waiting for {len(pending)}/{len(urls)} public Maven coordinates "
              f"({int(remaining)}s left).", flush=True)
        time.sleep(min(30, remaining))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument("--tag")
    selection.add_argument("--snapshot", action="store_true", help="Select an immutable snapshot for this main push")
    parser.add_argument("--wait-component", choices=("libraries", "tooling"))
    parser.add_argument("--check-unpublished-manifest")
    parser.add_argument("--recover-unpublished-libraries", action="store_true")
    args = parser.parse_args()
    if args.recover_unpublished_libraries:
        if args.snapshot or args.wait_component or args.check_unpublished_manifest:
            parser.error("Recovery must be a standalone manual snapshot preflight")
        if not SNAPSHOT.fullmatch(args.tag) or os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch":
            raise ValueError("Library recovery requires a manually dispatched testing snapshot.")
    if args.snapshot:
        if args.wait_component or args.check_unpublished_manifest:
            parser.error("Registry checks require --tag")
        write_outputs(snapshot_release())
        return
    version = version_from_tag(args.tag)
    if args.check_unpublished_manifest:
        if args.wait_component:
            parser.error("Choose one registry check")
        require_unpublished_manifest(args.check_unpublished_manifest, version)
        return
    if args.wait_component:
        wait_for_publication(args.wait_component, version)
        return
    commit = release_commit(args.tag)
    repository = os.environ["GITHUB_REPOSITORY"]
    checks = publication_checks(repository, commit, args.tag)
    outputs = {"tag": args.tag, "version": version, "commit": commit,
               "snapshot": str(bool(SNAPSHOT.fullmatch(args.tag))).lower()}
    for component in JOB_NAMES:
        # Explicit recovery is checked file-by-file against the selected native publication tasks before upload.
        done = False if component == "libraries" and args.recover_unpublished_libraries else publication_done(
            component, args.tag, checks, commit)
        outputs[f"{component}_done"] = str(done).lower()
    outputs["complete"] = str(all(outputs[f"{c}_done"] == "true" for c in JOB_NAMES)).lower()
    outputs["github_components"] = json.dumps([
        component for component in ("libraries", "tooling")
        if outputs["snapshot"] == "true" and outputs[f"{component}_done"] != "true"
    ])
    write_outputs(outputs)


def write_outputs(outputs):
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        for name, value in outputs.items():
            print(f"{name}={value}", file=output)
    print(json.dumps(outputs, indent=2))


if __name__ == "__main__":
    main()
