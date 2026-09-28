#!/usr/bin/env python3
"""Compare published AAR JVM declarations and Kotlin module names with javap."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


def inspect(aar, javap):
    with tempfile.TemporaryDirectory(prefix="symbols-abi-") as directory:
        jar = Path(directory) / "classes.jar"
        with zipfile.ZipFile(aar) as archive:
            jar.write_bytes(archive.read("classes.jar"))
        with zipfile.ZipFile(jar) as archive:
            classes = sorted(name[:-6].replace("/", ".") for name in archive.namelist() if name.endswith(".class"))
            modules = sorted(name for name in archive.namelist() if name.endswith(".kotlin_module"))
        if not classes:
            raise ValueError(f"No JVM classes in {aar}")
        output = "".join(subprocess.check_output(
            [str(javap), "-protected", "-s", "-constants", "-classpath", str(jar), *classes[start:start+128]],
            text=True,
        ) for start in range(0, len(classes), 128))
    output = "\n".join(line for line in output.splitlines() if not line.startswith("Compiled from")) + "\n"
    declarations = {}
    for block in output.split("\n}\n"):
        lines = [line.strip() for line in block.splitlines() if line.strip()]
        if not lines:
            continue
        match = re.search(r"(?:class|interface) ([^ <]+)", lines[0])
        if not match:
            raise ValueError(f"Unrecognized javap class declaration: {lines[0]}")
        members = {lines[index-1] + "\n" + line for index, line in enumerate(lines) if line.startswith("descriptor:")}
        declarations[match.group(1)] = (lines[0], members)
    if set(declarations) != set(classes):
        raise ValueError("javap class inventory differs from the AAR")
    summary = dict(aar=str(aar), aar_sha256=hashlib.sha256(aar.read_bytes()).hexdigest(),
                   class_count=len(classes), member_count=sum(len(value[1]) for value in declarations.values()),
                   kotlin_modules=modules, declarations_sha256=hashlib.sha256(output.encode()).hexdigest())
    return summary, declarations


def compare(stock_aar, candidate_aar, javap):
    stock, before = inspect(stock_aar, javap)
    candidate, after = inspect(candidate_aar, javap)
    removed = sorted(before.keys() - after.keys())
    changed_headers = sorted(name for name in before.keys() & after.keys() if before[name][0] != after[name][0])
    missing_members = {name: sorted(before[name][1] - after[name][1])
                       for name in before.keys() & after.keys() if before[name][1] - after[name][1]}
    same_modules = stock["kotlin_modules"] == candidate["kotlin_modules"]
    return dict(schema_version=1, verified=not removed and not changed_headers and not missing_members and same_modules,
                method="javap -protected -s -constants; every original class header/member retained, additive helpers allowed",
                stock=stock, candidate=candidate, kotlin_module_names_preserved=same_modules,
                removed_classes=removed, changed_class_headers=changed_headers,
                removed_or_changed_members=missing_members, added_classes=sorted(after.keys() - before.keys()),
                added_members_in_existing_classes=sum(len(after[name][1] - before[name][1]) for name in before.keys() & after.keys()),
                bounds="JVM declaration and Kotlin module-name check; source/metadata consumption and runtime behavior are tested separately")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stock-aar", type=Path, required=True)
    parser.add_argument("--candidate-aar", type=Path, required=True)
    parser.add_argument("--javap", type=Path, required=True)
    args = parser.parse_args()
    result = compare(args.stock_aar.resolve(), args.candidate_aar.resolve(), args.javap)
    print(json.dumps(result, indent=2))
    if not result["verified"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
