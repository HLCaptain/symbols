#!/usr/bin/env python3
"""Exercise the candidate helper without Android classes, a Context, or a Compose host."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import tomllib
import zipfile


SOURCE = '''import org.jetbrains.compose.resources.ResourceReader_androidKt;

public final class RuntimePathCheck {
    public static void main(String[] args) {
        String logical = "composeResources/example/drawable-dark/icon.XML";
        String path = ResourceReader_androidKt.getAndroidResourcePath(0x7f010001, logical);
        if (!path.endsWith(".XML")) throw new AssertionError("XML format suffix lost");
        String signed = ResourceReader_androidKt.getAndroidResourcePath(Integer.MIN_VALUE, "drawable/icon.xml");
        if (!signed.endsWith(".xml")) throw new AssertionError("Signed resource ID rejected");
        try {
            ResourceReader_androidKt.getAndroidResourcePath(0, "drawable/icon.xml");
            throw new AssertionError("Zero ID accepted");
        } catch (IllegalArgumentException expected) {}
        try {
            ResourceReader_androidKt.getAndroidResourcePath(1, "drawable/icon.png");
            throw new AssertionError("Unsupported format accepted");
        } catch (IllegalArgumentException expected) {}
        System.out.println("CONTEXT_FREE_HELPER_OK");
    }
}
'''


def discover_stdlib():
    catalog = Path(__file__).resolve().parents[3] / "gradle/libs.versions.toml"
    version = tomllib.loads(catalog.read_text())["versions"]["kotlin"]
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME") or Path.home() / ".gradle").expanduser()
    cache = gradle_home / "caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib" / version
    matches = sorted(path for path in cache.glob(f"*/kotlin-stdlib-{version}.jar") if path.is_file())
    if not matches:
        raise FileNotFoundError(
            f"No cached kotlin-stdlib-{version}.jar found under {cache}. "
            f"Build the runtime candidate first, or pass --stdlib /path/to/kotlin-stdlib-{version}.jar."
        )
    return matches[0]


def verify(aar, stdlib, jdk):
    with tempfile.TemporaryDirectory(prefix="symbols-runtime-helper-") as directory:
        directory = Path(directory)
        with zipfile.ZipFile(aar) as archive:
            (directory / "runtime.jar").write_bytes(archive.read("classes.jar"))
        source = directory / "RuntimePathCheck.java"
        source.write_text(SOURCE)
        classpath = os.pathsep.join((str(directory / "runtime.jar"), str(stdlib.resolve())))
        subprocess.run([str(jdk / "bin/javac"), "--release", "11", "-cp", classpath,
                        "-d", str(directory), str(source)], check=True)
        output = subprocess.check_output([str(jdk / "bin/java"), "-cp", str(directory) + os.pathsep + classpath,
                                          "RuntimePathCheck"], text=True).strip()
        if output != "CONTEXT_FREE_HELPER_OK":
            raise ValueError(f"Unexpected helper result: {output}")
    return dict(verified=True, aar_sha256=hashlib.sha256(aar.read_bytes()).hexdigest(),
                stdlib_sha256=hashlib.sha256(stdlib.read_bytes()).hexdigest(),
                android_context_or_sdk_classpath_present=False,
                checks=["context-free XML path construction", "uppercase XML extension preserved",
                        "signed Int resource ID accepted", "zero ID rejected", "non-XML path rejected"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("aar", "jdk"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--stdlib", type=Path, help="Override the catalog Kotlin stdlib discovered in the existing Gradle cache")
    args = parser.parse_args()
    try:
        stdlib = (args.stdlib or discover_stdlib()).resolve()
        if not stdlib.is_file():
            raise FileNotFoundError(f"Kotlin stdlib JAR does not exist: {stdlib}")
    except FileNotFoundError as error:
        parser.error(str(error))
    print(json.dumps(verify(args.aar.resolve(), stdlib, args.jdk.resolve()), indent=2))


if __name__ == "__main__":
    main()
