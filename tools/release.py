#!/usr/bin/env python3
"""Release preflight and retry checks; never uploads packages."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen

TAG = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\Z")
GROUP = "io.github.hlcaptain"
PLUGIN = GROUP + ".symbol-fonts"
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
    if not TAG.fullmatch(tag):
        raise ValueError("Release tags must use vMAJOR.MINOR.PATCH, without leading zeros or SNAPSHOT.")
    return tag[1:]


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def release_commit(tag):
    version_from_tag(tag)
    commit = git("rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}")
    subprocess.run(["git", "merge-base", "--is-ancestor", commit, "refs/remotes/origin/main"], check=True)
    if os.environ.get("GITHUB_EVENT_NAME") == "push":
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        if event.get("deleted") or event.get("forced"):
            raise ValueError("Release tags cannot be deleted or moved.")
        if event.get("after") not in {commit, git("rev-parse", f"refs/tags/{tag}")}:
            raise ValueError("The release tag changed after this event.")
    return commit


def pom_url(base, group, artifact, version):
    return f"{base}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"


def publication_urls(component, version):
    central = "https://repo.maven.apache.org/maven2"
    if component == "libraries":
        return [pom_url(central, GROUP, artifact, version) for artifact in LIBRARIES]
    if component == "tooling":
        return [pom_url(central, GROUP, artifact, version) for artifact in ("symbol-generator-core", "symbol-gradle-plugin")] + [
            pom_url(central, PLUGIN, PLUGIN + ".gradle.plugin", version)]
    # The Portal Maven endpoint can proxy Central; only its version listing proves registration.
    return [f"https://plugins.gradle.org/plugin/{PLUGIN}/{version}"]


def exists(url):
    try:
        method = "GET" if url.startswith("https://plugins.gradle.org/plugin/") else "HEAD"
        with urlopen(Request(url, method=method), timeout=30) as response:
            return response.status == 200
    except HTTPError as error:
        if error.code == 404:
            return False
        if (error.code == 400 and url.startswith("https://plugins.gradle.org/plugin/")
                and b"<title>Gradle - Plugin Not Found</title>" in error.read(65536)):
            return False
        raise  # Authentication, rate limits and server failures are not "unpublished".


def prior_success(component, tag, checks):
    name = f"{JOB_NAMES[component]} ({tag})"
    return any(c["name"] == name and c.get("conclusion") == "success"
               and c.get("app", {}).get("slug") == "github-actions" for c in checks)


def publication_done(component, tag, checks):
    # Successful jobs also cover CDN propagation and a first Portal submission awaiting approval.
    if prior_success(component, tag, checks):
        return True
    present = [exists(url) for url in publication_urls(component, version_from_tag(tag))]
    if any(present) and not all(present):
        raise ValueError(f"Part of the {component} release already exists; inspect the deployment before retrying.")
    return all(present)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--wait-component", choices=("libraries", "tooling"))
    args = parser.parse_args()
    version = version_from_tag(args.tag)
    if args.wait_component:
        deadline = time.monotonic() + 600
        while not all(exists(url) for url in publication_urls(args.wait_component, version)):
            if time.monotonic() >= deadline:
                raise TimeoutError("Central publication is not yet visible; inspect its status before retrying.")
            time.sleep(10)
        return
    commit = release_commit(args.tag)
    repository = os.environ["GITHUB_REPOSITORY"]
    pages = json.loads(subprocess.check_output([
        "gh", "api", "--paginate", "--slurp",
        f"repos/{repository}/commits/{commit}/check-runs?filter=all&per_page=100",
    ], text=True))
    checks = [check for page in pages for check in page["check_runs"]]
    outputs = {"tag": args.tag, "version": version, "commit": commit}
    for component in JOB_NAMES:
        outputs[f"{component}_done"] = str(publication_done(component, args.tag, checks)).lower()
    outputs["complete"] = str(all(outputs[f"{c}_done"] == "true" for c in JOB_NAMES)).lower()
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        for name, value in outputs.items():
            print(f"{name}={value}", file=output)
    print(json.dumps(outputs, indent=2))


if __name__ == "__main__":
    main()
