#!/usr/bin/env python3
"""Check proposed runtime capabilities with real Gradle resolution, without compiling."""
import argparse
import json
from pathlib import Path
import re
import subprocess

import runtime


def prepare(directory, repository, candidate, higher_supported=None):
    if directory.exists():
        raise ValueError(f"Refusing to overwrite {directory}")
    directory.mkdir(parents=True)
    runtime.write(directory / "settings.gradle", "rootProject.name = 'native-xml-runtime-resolution'\n")
    script = '''import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.Usage
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import groovy.json.JsonOutput

repositories {
    maven { url = uri('__REPOSITORY__') }
    mavenCentral()
    google()
}

def capability = '__GROUP__:__CAPABILITY__'
def cases = []
def probe = { String name, String module, String version, String platform,
              String usage, boolean sources, boolean requireNative, boolean expected ->
    def configuration = configurations.create(name) {
        canBeResolved = true
        canBeConsumed = false
        transitive = false
        attributes {
            attribute(Attribute.of('org.jetbrains.kotlin.platform.type', String), platform)
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, usage))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, sources ? Category.DOCUMENTATION : Category.LIBRARY))
            if (sources) attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.SOURCES))
        }
    }
    def dependency = dependencies.create('__GROUP__:' + module + ':' + version)
    if (requireNative) dependency.capabilities { requireCapability(capability) }
    configuration.dependencies.add(dependency)
    cases.add([name: name, configuration: configuration, expected: expected])
    configuration
}
probe('nativeApi', 'components-resources-android', '__CANDIDATE__', 'androidJvm', Usage.JAVA_API, false, true, true)
probe('nativeRuntime', 'components-resources-android', '__CANDIDATE__', 'androidJvm', Usage.JAVA_RUNTIME, false, true, true)
probe('nativeSources', 'components-resources-android', '__CANDIDATE__', 'androidJvm', Usage.JAVA_RUNTIME, true, true, true)
probe('rootDefault', 'components-resources', '__CANDIDATE__', 'androidJvm', Usage.JAVA_RUNTIME, false, false, true)
probe('androidDefault', 'components-resources-android', '__CANDIDATE__', 'androidJvm', Usage.JAVA_RUNTIME, false, false, true)
probe('jvmDefault', 'components-resources', '__CANDIDATE__', 'jvm', Usage.JAVA_RUNTIME, false, false, true)
probe('stockUnsupported', 'components-resources-android', '1.12.1', 'androidJvm', Usage.JAVA_RUNTIME, false, true, false)
def higher = probe('higherUnsupported', 'components-resources-android', '__CANDIDATE__', 'androidJvm', Usage.JAVA_RUNTIME, false, true, false)
higher.dependencies.add(dependencies.create('__GROUP__:components-resources:1.13.0-alpha01'))
def higherAndroid = probe('higherAndroidUnsupported', 'components-resources-android', '__CANDIDATE__', 'androidJvm', Usage.JAVA_RUNTIME, false, true, false)
higherAndroid.dependencies.add(dependencies.create('__GROUP__:components-resources-android:1.13.0-alpha01'))
probe('jvmCapabilityRejected', 'components-resources-android', '__CANDIDATE__', 'jvm', Usage.JAVA_RUNTIME, false, true, false)
def supportedVersion = __HIGHER_SUPPORTED__
if (supportedVersion != null) {
    def supported = probe('higherSupported', 'components-resources-android', '__CANDIDATE__', 'androidJvm', Usage.JAVA_RUNTIME, false, true, true)
    supported.dependencies.add(dependencies.create('__GROUP__:components-resources:' + supportedVersion))
    cases.last().requiredComponent = '__GROUP__:components-resources-android:' + supportedVersion
}

tasks.register('verifyRuntimeCapabilities') {
    doLast {
        def results = cases.collect { test ->
            try {
                def files = test.configuration.files.collect { it.name }.sort()
                def components = test.configuration.incoming.resolutionResult.allComponents
                    .findAll { it.id instanceof ModuleComponentIdentifier }.collect { it.id.displayName }.sort()
                [name: test.name, expected_resolution: test.expected, resolved: true,
                 verified: test.expected && (!test.requiredComponent || components.contains(test.requiredComponent)),
                 files: files, components: components]
            } catch (Exception failure) {
                def messages = []
                for (Throwable cause = failure; cause != null; cause = cause.cause) messages.add(cause.message)
                def message = messages.join('\\n')
                def rejectedCapability = message.contains(capability) &&
                    (message.contains('Unable to find a variant') || message.contains('No matching variant'))
                [name: test.name, expected_resolution: test.expected, resolved: false,
                 verified: !test.expected && rejectedCapability, diagnostic: message]
            }
        }
        def report = [schema_version: 1, candidate: '__CANDIDATE__', capability: capability,
                      scope: 'Dependency metadata resolution only; compilation and runtime require separate checks',
                      verified: results.every { it.verified }, cases: results]
        file('resolution-results.json').text = JsonOutput.prettyPrint(JsonOutput.toJson(report)) + '\\n'
        results.each { println(it.name + ': ' + (it.verified ? 'PASS' : 'FAIL')) }
        if (!report.verified) throw new GradleException('Runtime capability resolution contract failed; see resolution-results.json')
    }
}
'''
    for token, value in {"REPOSITORY": repository.resolve().as_uri(), "GROUP": runtime.GROUP,
                         "CAPABILITY": runtime.CAPABILITY, "CANDIDATE": candidate,
                         "HIGHER_SUPPORTED": json.dumps(higher_supported)}.items():
        script = script.replace("__" + token + "__", value)
    runtime.write(directory / "build.gradle", script)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--candidate-version", default=runtime.VERSION)
    parser.add_argument("--higher-supported-version", help="Also verify normal selection of a newer compatible candidate")
    parser.add_argument("--gradle", type=Path, required=True)
    parser.add_argument("--prepare-only", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.+-]+", args.candidate_version):
        parser.error("Invalid candidate version")
    if args.higher_supported_version and not re.fullmatch(r"[A-Za-z0-9_.+-]+", args.higher_supported_version):
        parser.error("Invalid higher supported version")
    directory = args.output.resolve()
    prepare(directory, args.repository, args.candidate_version, args.higher_supported_version)
    if args.prepare_only:
        return
    command = [str(args.gradle.resolve()), "-p", str(directory), "verifyRuntimeCapabilities",
               "--no-daemon", "--no-configuration-cache", "--no-build-cache", "--max-workers=1",
               "--console=plain", "-Dorg.gradle.jvmargs=-Xmx768M -Dfile.encoding=UTF-8"]
    runtime.write(directory / "command.json", json.dumps(command, indent=2) + "\n")
    with (directory / "resolution.log").open("w") as output:
        result = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        raise SystemExit(f"Resolution failed; see {directory / 'resolution.log'}")
    print((directory / "resolution-results.json").read_text())


if __name__ == "__main__":
    main()
