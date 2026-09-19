#!/usr/bin/env python3
"""Publish one real KMP library to disposable Maven local with a disposable signing key."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    root = Path(__file__).resolve().parents[1]
    version = "0.0.0-signingcheck"
    with tempfile.TemporaryDirectory(prefix="symbols-signing-") as directory:
        temporary = Path(directory)
        source = temporary / "source"
        repository = temporary / "m2"
        keyring = temporary / "gnupg"
        keyring.mkdir(mode=0o700)
        env = dict(os.environ, GNUPGHOME=str(keyring))

        # Copy working-tree contents, including tracked edits, without stale build outputs.
        tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=root)
        for relative in tracked.decode().split("\0"):
            if relative and (root / relative).is_file():
                destination = source / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(root / relative, destination)
        if (root / "local.properties").is_file():
            shutil.copy2(root / "local.properties", source / "local.properties")

        try:
            subprocess.run(
                ["gpg", "--batch", "--pinentry-mode", "loopback", "--passphrase", "",
                 "--quick-generate-key", "Symbols signing check <signing-check@example.invalid>",
                 "rsa2048", "sign", "1d"],
                env=env, check=True, capture_output=True,
            )
            signing_key = subprocess.check_output(
                ["gpg", "--batch", "--armor", "--export-secret-keys"], env=env, text=True,
            )
            # Reuse downloaded tooling, but never load the developer's signing properties.
            gradle_home = temporary / "gradle-home"
            gradle_home.mkdir()
            existing_home = Path(env.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
            for name in ("caches", "wrapper", "jdks"):
                if (existing_home / name).exists():
                    (gradle_home / name).symlink_to((existing_home / name).resolve(), target_is_directory=True)
            properties = "signingInMemoryKey=" + signing_key.replace("\n", "\\n") + "\n"
            properties += ("signingInMemoryKeyPassword=\n"
                           "org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8\n"
                           "kotlin.compiler.execution.strategy=in-process\n")
            (gradle_home / "gradle.properties").write_text(properties)
            env["GRADLE_USER_HOME"] = str(gradle_home)
            init = temporary / "signing-check.init.gradle"
            init.write_text("""
gradle.projectsEvaluated {
    def owners = [:]
    rootProject.allprojects.each { project ->
        project.tasks.withType(org.gradle.plugins.signing.Sign).each { signing ->
            signing.signatureFiles.files.each { file ->
                def previous = owners.put(file.canonicalPath, signing.path)
                if (previous != null && previous != signing.path) {
                    throw new GradleException("Signing output collision: $previous and ${signing.path}: $file")
                }
            }
        }
    }
    if (rootProject.name == 'symbols') {
        if (owners.isEmpty()) throw new GradleException('Signing was not enabled for the check')
        println("Verified unique producers for ${owners.size()} signature files.")
    }
}
""")
            subprocess.run(
                [str(source / "gradlew"), ":modules:material-compose:publishToMavenLocal",
                 f"-PVERSION_NAME={version}", f"-Dmaven.repo.local={repository}",
                 "--init-script", str(init),
                 "--max-workers=1", "--no-daemon", "--no-configuration-cache"],
                cwd=source, env=env, check=True,
            )

            group = repository / "io/github/hlcaptain"
            expected = {
                f"symbols-material-compose{suffix}"
                for suffix in ("", "-android", "-iosarm64", "-iossimulatorarm64",
                               "-js", "-jvm", "-wasm-js")
            }
            actual = {path.parent.parent.name for path in group.rglob("*-javadoc.jar")}
            if actual != expected:
                raise RuntimeError(f"Expected Javadoc publications {expected}, found {actual}")

            artifacts = sorted(
                path for path in group.rglob("*")
                if path.is_file() and path.suffix in {".jar", ".aar", ".klib", ".pom", ".module"}
            )
            for artifact in artifacts:
                signature = Path(f"{artifact}.asc")
                if not signature.is_file():
                    raise RuntimeError(f"Missing signature for {artifact.relative_to(repository)}")
                subprocess.run(
                    ["gpg", "--batch", "--verify", str(signature), str(artifact)],
                    env=env, check=True, capture_output=True,
                )
            print(f"Verified {len(artifacts)} signed artifacts across seven KMP publications.")
        finally:
            subprocess.run(
                ["gpgconf", "--kill", "gpg-agent"], env=env,
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False,
            )


if __name__ == "__main__":
    main()
