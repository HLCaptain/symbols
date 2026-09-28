#!/usr/bin/env python3
"""Verify exact native-raw retention and published-byte parity in a consumer APK."""
import argparse
from collections import Counter
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import zipfile


PREFIX = "symbols_probe_"
RESOURCE = re.compile(r"^\s*resource\s+0x[0-9a-fA-F]+\s+(?:[\w.]+:)?(\w+)/(\w+)\b")
FILE = re.compile(r"\(file\)\s+(\S+)")
RAW_FILE = re.compile(r"res/raw(?:-[^/]+)?/(symbols_probe_[a-z0-9_]+)\.[^/]+$")
DRAWABLE_ASSETS = "composeResources/io.github.hlcaptain.symbols.material.outlined.compose.drawables.resources/drawable/"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def publication_bytes(stock_aar, native_aar, native_jvm, control_aar, control_jvm):
    """Compare published XML bytes/notices directly with the stock outlined AAR.

    This is callable separately from the APK retention CLI. It intentionally
    handles this study's outlined pack, not arbitrary Compose resource layouts.
    """
    def inventory(path, prefix):
        path = Path(path)
        with zipfile.ZipFile(path) as archive:
            entries = [entry for entry in archive.infolist()
                       if entry.filename.startswith(prefix) and entry.filename.endswith(".xml")]
            files = {}
            for entry in entries:
                name = entry.filename.removeprefix(prefix)
                if name in files:
                    raise ValueError(f"Duplicate published XML: {path}:{name}")
                files[name] = archive.read(entry)
            notices = {}
            containers = [("archive", archive)]
            if "classes.jar" in archive.namelist():
                containers.append(("classes.jar", zipfile.ZipFile(io.BytesIO(archive.read("classes.jar")))))
            try:
                for container, contents in containers:
                    for name in contents.namelist():
                        if name.startswith("META-INF/") and name.endswith(("LICENSE", "THIRD_PARTY_NOTICES.md")):
                            notices[name] = (contents.read(name), container)
            finally:
                for _, contents in containers[1:]:
                    contents.close()
            fingerprint = sha(json.dumps({name: sha(data) for name, data in sorted(files.items())},
                                         separators=(",", ":")).encode())
            record = dict(path=str(path), archive_sha256=sha(path.read_bytes()), archive_bytes=path.stat().st_size,
                          xml_files=len(files), xml_bytes=sum(map(len, files.values())),
                          xml_compressed_bytes=sum(entry.compress_size for entry in entries),
                          xml_inventory_sha256=fingerprint,
                          android_asset_files=sum(not entry.is_dir() and entry.filename.startswith("assets/" + DRAWABLE_ASSETS)
                                                  for entry in archive.infolist()))
            return files, notices, record

    original, original_notices, stock = inventory(stock_aar, "assets/" + DRAWABLE_ASSETS)
    if not original or not original_notices:
        raise ValueError("Stock AAR must contain outlined XML and original notices")
    report = dict(schema_version=1, verified=True,
                  reference="Direct bytes from the original stock AAR, independent of publisher manifests",
                  stock=stock, publications={})
    for label, path, prefix in (
        ("native_android", native_aar, "res/raw/" + PREFIX),
        ("native_jvm", native_jvm, DRAWABLE_ASSETS),
        ("control_android", control_aar, "assets/" + DRAWABLE_ASSETS),
        ("control_jvm", control_jvm, DRAWABLE_ASSETS),
    ):
        files, notices, record = inventory(path, prefix)
        if files.keys() != original.keys():
            raise ValueError(f"Published XML inventory differs from stock: {label}")
        mismatches = [name for name in original if files[name] != original[name]]
        if mismatches:
            raise ValueError(f"Published XML bytes differ from stock: {label}: {mismatches[:5]}")
        if label == "native_android" and record["android_asset_files"]:
            raise ValueError("Native AAR retains duplicate owned Compose assets")
        for name, (data, _) in original_notices.items():
            if name not in notices or notices[name][0] != data:
                raise ValueError(f"Original notice missing or changed: {label}:{name}")
        record["notices"] = {name: dict(sha256=sha(notices[name][0]), container=notices[name][1])
                             for name in sorted(original_notices)}
        record["different_xml_files"] = 0
        report["publications"][label] = record
    return report


def resource_files(dump):
    """Use the table, because APK resource filenames may be shortened or shared."""
    result = {}
    current = None
    for line in dump.splitlines():
        match = RESOURCE.match(line)
        if match:
            kind, name = match.groups()
            current = name if kind == "raw" and name.startswith(PREFIX) else None
            if current is not None:
                result.setdefault(current, [])
        elif current is not None and (match := FILE.search(line)):
            result[current].append(match.group(1))
    return result


def names(values):
    if not isinstance(values, list) or not all(
        isinstance(value, str) and re.fullmatch(r"symbols_probe_[a-z0-9_]+", value)
        for value in values
    ) or len(set(values)) != len(values):
        raise ValueError("Expected a duplicate-free list of symbols_probe_ resource names")
    return set(values)


def analyze(fixture, apk_path, aar_path, aapt2):
    manifest_path = fixture / "fixture.json"
    manifest_bytes = manifest_path.read_bytes()
    manifest = json.loads(manifest_bytes)
    contract = manifest["native_raw"]
    expected = names(contract["expected_resources"])
    catalog = names(contract["all_resources"])
    if not expected <= catalog:
        raise ValueError("Expected resources must belong to the full published catalog")
    forbidden = contract["forbidden_asset_prefix"]
    if not isinstance(forbidden, str) or not forbidden.startswith("assets/") or not forbidden.endswith("/"):
        raise ValueError("forbidden_asset_prefix must identify an assets/ directory")

    dump = subprocess.check_output([str(aapt2), "dump", "resources", str(apk_path)], text=True)
    retained = resource_files(dump)
    missing, extra = expected - retained.keys(), retained.keys() - expected
    if missing or extra:
        raise ValueError(f"Raw retention mismatch: missing={sorted(missing)}, unexpected={sorted(extra)}")

    with zipfile.ZipFile(aar_path) as aar, zipfile.ZipFile(apk_path) as apk:
        published = {}
        for entry in aar.infolist():
            if match := RAW_FILE.fullmatch(entry.filename):
                published.setdefault(match.group(1), []).append(entry.filename)
        if published.keys() != catalog:
            raise ValueError("Published AAR raw resource names differ from the full fixture catalog")
        for label, archive in (("AAR", aar), ("APK", apk)):
            duplicates = [entry.filename for entry in archive.infolist() if entry.filename.startswith(forbidden)]
            if duplicates:
                raise ValueError(f"{label} still packages owned Compose assets: {duplicates[:5]}")

        fingerprints = {}
        for name, paths in retained.items():
            if not paths:
                raise ValueError(f"Raw resource {name} has no packaged file")
            actual = Counter(sha(apk.read(path)) for path in paths)
            reference = Counter(sha(aar.read(path)) for path in published[name])
            if actual != reference:
                raise ValueError(f"Raw bytes differ from the published AAR for {name}")
            fingerprints[name] = sorted(actual.elements())
        paths = {path for files in retained.values() for path in files}
        entries = [apk.getinfo(path) for path in paths]
        raw_bytes = sum(entry.file_size for entry in entries)
        compressed_bytes = sum(entry.compress_size for entry in entries)

    return dict(
        schema_version=1,
        verified=True,
        fixture=str(fixture),
        fixture_sha256=sha(manifest_bytes),
        apk=str(apk_path),
        apk_sha256=sha(apk_path.read_bytes()),
        apk_bytes=apk_path.stat().st_size,
        published_aar=str(aar_path),
        published_aar_sha256=sha(aar_path.read_bytes()),
        published_resource_count=len(catalog),
        expected_resource_count=len(expected),
        retained_resource_count=len(retained),
        retained_file_count=len(paths),
        retained_raw_bytes=raw_bytes,
        retained_raw_compressed_bytes=compressed_bytes,
        retained_inventory_sha256=sha(json.dumps(fingerprints, sort_keys=True, separators=(",", ":")).encode()),
        forbidden_asset_count=0,
        byte_reference="Published AAR raw entries; producer validation separately compares them with stock assets",
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--aar", type=Path, required=True)
    parser.add_argument("--aapt2", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = analyze(args.fixture.resolve(), args.apk.resolve(), args.aar.resolve(), args.aapt2)
    except (ValueError, KeyError, OSError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Verification failed: {error}\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
