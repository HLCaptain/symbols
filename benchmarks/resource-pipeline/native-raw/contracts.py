#!/usr/bin/env python3
"""Prepare a tiny published qualifier/reader contract fixture; never runs Gradle.

Copy a bounded native publisher, add a second physical XML to its first public
drawable, and generate a normal consuming app. The app's test-only Application
reads that descriptor before providers start; it does not initialize the library.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil

import consumer
import publisher


ACTIVITY = '''@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
package study

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import org.jetbrains.compose.resources.LocalResourceReader
import org.jetbrains.compose.resources.MissingResourceException
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.rememberResourceEnvironment
import @PACKAGE@.*

private const val TAG = "NativeRawContract"
private data class Variant(val theme: String, val path: String, val sha: String)
private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

// Test-only observer, deliberately before AndroidContextProvider installation.
// No application initializer or adapter is required by the native resource backend.
class ContractApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        check(Res.drawable.@ICON@.hashCode() == "drawable:@ICON@".hashCode())
        Log.i(TAG, "PRE_CONTEXT_DESCRIPTOR_OK")
    }
}

class MainActivity : ComponentActivity() {
    private var passed = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Paths arrive at runtime so R8 must preserve the pack's raw dispatch.
        val variants = assets.open("contract-variants.tsv").bufferedReader().use { input ->
            input.readLines().map { line ->
                val fields = line.split('\\t')
                Variant(fields[0], fields[1], fields[2])
            }
        }
        setContent {
            Column(Modifier.fillMaxSize().background(Color.White)) {
                variants.forEach { variant -> VariantContract(variant) }
            }
        }
    }

    @Composable
    private fun VariantContract(variant: Variant) {
        val original = LocalConfiguration.current
        val configuration = remember(original, variant.theme) {
            Configuration(original).apply {
                val night = if (variant.theme == "DARK") Configuration.UI_MODE_NIGHT_YES
                    else Configuration.UI_MODE_NIGHT_NO
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }
        }
        // This is the public Android input to Compose1.12.1's resource environment.
        // That version has no public LocalResourceEnvironment override.
        CompositionLocalProvider(LocalConfiguration provides configuration) {
            val environment = rememberResourceEnvironment()
            val reader = LocalResourceReader.current
            Image(painterResource(Res.drawable.@ICON@), variant.theme, Modifier.size(96.dp))
            LaunchedEffect(variant.theme) {
                val selected = getDrawableResourceBytes(environment, Res.drawable.@ICON@)
                check(sha(selected) == variant.sha) { "Wrong ${variant.theme} qualifier" }
                val raw = Res.readBytes(variant.path)
                check(raw.contentEquals(selected)) { "Raw path differs from selected qualifier" }
                val uri = Res.getUri(variant.path)
                check(Uri.parse(uri).authority == packageName) { "URI must use application identity" }
                val fromUri = contentResolver.openInputStream(Uri.parse(uri))!!.use { it.readBytes() }
                check(raw.contentEquals(fromUri)) { "URI bytes differ" }
                val nativePath = "@PROTOCOL@${Uri.parse(uri).lastPathSegment}/drawable.xml"
                check(reader.getUri(nativePath) == uri)
                check(reader.readPart(nativePath, 5, 13).contentEquals(raw.copyOfRange(5, 18)))
                check(reader.readPart(nativePath, 0, 0).isEmpty())
                // Preserve the existing reader's zero-filled short-read behavior.
                check(reader.readPart(nativePath, raw.size.toLong(), 4).contentEquals(ByteArray(4)))
                for (bad in listOf(
                    "@PROTOCOL@bad/drawable.xml",
                    "@PROTOCOL@0/drawable.xml",
                    "@PROTOCOL@2147483647/drawable.xml",
                    "@PROTOCOL@1/unsupported.bin"
                )) {
                    check(runCatching { reader.read(bad) }.exceptionOrNull() is MissingResourceException)
                    check(runCatching { reader.getUri(bad) }.exceptionOrNull() is MissingResourceException)
                }
                val fallback = "untouched asset fallback".toByteArray()
                check(reader.read("contract-fallback.bin").contentEquals(fallback))
                check(reader.getUri("contract-fallback.bin") == "file:///android_asset/contract-fallback.bin")
                Log.i(TAG, "CONTRACT_OK theme=${variant.theme} sha=${variant.sha} uri=$uri")
                if (++passed == 2) Log.i(TAG, "CONTRACT_ALL_OK")
            }
        }
    }
}
'''


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if text.count(old) != 1:
        raise ValueError(f"Unsupported publisher shape in {path.name}: {old!r}")
    path.write_text(text.replace(old, new))


def prepare(args) -> dict:
    base = args.publisher.resolve()
    descriptor = json.loads((base / "publisher.json").read_text())
    if descriptor["backend"] != "native" or not 1 <= len(descriptor["items"]) <= 3:
        raise ValueError("Use a bounded native publisher containing one to three icons")
    if not re.fullmatch(r"[A-Za-z0-9_.+-]+", args.version) or args.version == descriptor["version"]:
        raise ValueError("Use a fresh Maven version for the contract pack")
    build = (base / "build.gradle.kts").read_text()
    if any(setting not in build for setting in (
        'id("org.jetbrains.kotlin.plugin.compose")', 'moduleName.set(',
        'implementation("org.jetbrains.compose.runtime:runtime:',
    )):
        raise ValueError("Regenerate the small publisher with current publisher.py to preserve Compose/compiler ABI settings")
    output = args.output.resolve()
    if output.exists():
        raise ValueError(f"Refusing to overwrite {output}")
    pack, app = output / "publisher", output / "consumer"
    pack.mkdir(parents=True)
    for name in ("src", "gradle", "gradlew", "gradlew.bat", "settings.gradle.kts", "build.gradle.kts", "gradle.properties"):
        source, target = base / name, pack / name
        if source.is_dir():
            shutil.copytree(source, target)
        else:
            shutil.copy2(source, target)
    replace_once(pack / "build.gradle.kts", f'version = "{descriptor["version"]}"', f'version = "{args.version}"')
    for name in ("settings.gradle.kts", "build.gradle.kts"):
        path = pack / name
        path.write_text(re.sub(r'uri\("file:[^"]+"\)', f'uri({json.dumps(args.repository.resolve().as_uri())})', path.read_text()))
    icon = descriptor["items"][0]
    name, raw = icon["resource"], icon["raw_resource"]
    dark_raw = raw + "_dark"
    light = (pack / f"src/androidMain/res/raw/{raw}.xml").read_bytes()
    dark, changed = re.subn(rb'(android:fillColor=")[^"]+', rb'\g<1>#FF0000FF', light)
    # Keep every XML byte except path fill colors, including its namespace spelling.
    if not changed or dark == light:
        raise ValueError("Expected the source icon to contain a non-blue android:fillColor")
    publisher.write(pack, f"src/androidMain/res/raw/{dark_raw}.xml", dark.decode())
    publisher.write(pack, f"src/jvmMain/resources/{publisher.PREFIX}drawable-dark/{name}.xml", dark.decode())
    package = publisher.PACKAGE.replace(".", "/")
    common = pack / "src/commonMain/kotlin" / package
    accessor = next(p for p in common.glob("Drawable*.kt") if f"public val Res.drawable.{name}:" in p.read_text())
    old = f"ResourceItem(setOf(), nativePath_{name}(), -1, -1),"
    new = f"ResourceItem(setOf(org.jetbrains.compose.resources.ThemeQualifier.LIGHT), nativePath_{name}(), -1, -1),\n        ResourceItem(setOf(org.jetbrains.compose.resources.ThemeQualifier.DARK), nativePath_{name}_dark(), -1, -1),"
    replace_once(accessor, old, new)
    def content_hash(data):
        value = 1
        for byte in data:
            value = (value * 31 + (byte if byte < 128 else byte - 256)) & 0xffffffff
        return value
    combined = (content_hash(light) * 31 + content_hash(dark)) & 0xffffffff
    combined = combined if combined < 0x80000000 else combined - 0x100000000
    text = accessor.read_text()
    text, count = re.subn(r"@delegate:ResourceContentHash\([^\n]+\)(\npublic val Res\.drawable\." + re.escape(name) + r":)",
                          rf"@delegate:ResourceContentHash({combined})\1", text)
    if count != 1:
        raise ValueError("Missing selected resource content-hash annotation")
    accessor.write_text(text)
    for target, declaration in (
        ("commonMain", f"internal expect fun nativePath_{name}_dark(): String"),
        ("androidMain", f'internal actual fun nativePath_{name}_dark(): String = "{publisher.PROTOCOL}${{R.raw.{dark_raw}}}/drawable.xml"'),
        ("jvmMain", f'internal actual fun nativePath_{name}_dark(): String = "{publisher.PREFIX}drawable-dark/{name}.xml"'),
    ):
        with (pack / f"src/{target}/kotlin/{package}/NativeResourcePaths.kt").open("a") as stream:
            stream.write("\n" + declaration + "\n")
    android_paths = pack / f"src/androidMain/kotlin/{package}/NativeResourcePaths.kt"
    replace_once(android_paths, 'internal actual fun platformResourcePath(path: String): String {',
                 f'internal actual fun platformResourcePath(path: String): String {{\n    if (path == "drawable-dark/{name}.xml") return nativePath_{name}_dark()')
    variants = [dict(theme=theme, logical_path=logical, raw_resource=resource,
                     xml_sha256=hashlib.sha256(data).hexdigest(), xml_bytes=len(data))
                for theme, logical, resource, data in (
                    ("LIGHT", f"drawable/{name}.xml", raw, light),
                    ("DARK", f"drawable-dark/{name}.xml", dark_raw, dark))]
    descriptor.update(version=args.version, coordinates=descriptor["coordinates"].rsplit(":", 1)[0] + ":" + args.version,
                      contract_variants=variants, source_publisher_sha256=hashlib.sha256((base / "publisher.json").read_bytes()).hexdigest())
    publisher.write(pack, "publisher.json", json.dumps(descriptor, indent=2) + "\n")
    consumer.generate(argparse.Namespace(publisher=pack, repository=args.repository, output=app,
                                         access="raw", count="1", agp=None))
    publisher.write(app, "src/main/kotlin/study/MainActivity.kt", ACTIVITY.replace("@PACKAGE@", publisher.PACKAGE)
                    .replace("@ICON@", name).replace("@PROTOCOL@", publisher.PROTOCOL))
    replace_once(app / "src/main/AndroidManifest.xml", "<application ", '<application android:name="study.ContractApplication" ')
    replace_once(app / "build.gradle.kts", 'applicationId = "io.github.hlcaptain.symbols.usage.compose.dynamic"',
                 'applicationId = "io.github.hlcaptain.symbols.usage.contracts"')
    publisher.write(app, "src/main/assets/contract-variants.tsv", "".join(f'{v["theme"]}\t{v["logical_path"]}\t{v["xml_sha256"]}\n' for v in variants))
    publisher.write(app, "src/main/assets/contract-fallback.bin", "untouched asset fallback")
    fixture = json.loads((app / "fixture.json").read_text())
    fixture["native_raw"]["expected_resources"].append(dark_raw)
    fixture["native_raw"]["all_resources"].append(dark_raw)
    fixture["contract_variants"] = variants
    fixture["contract_log_markers"] = ["PRE_CONTEXT_DESCRIPTOR_OK", "CONTRACT_OK theme=LIGHT", "CONTRACT_OK theme=DARK", "CONTRACT_ALL_OK"]
    publisher.write(app, "fixture.json", json.dumps(fixture, indent=2) + "\n")
    report = dict(publisher=str(pack), consumer=str(app), version=args.version,
                  application_id="io.github.hlcaptain.symbols.usage.contracts", variants=variants,
                  build_commands=[f"{pack}/gradlew -p {pack} publishAllPublicationsToProbeRepository",
                                  f"{app}/gradlew -p {app} assembleShrunk"],
                  launch_component="io.github.hlcaptain.symbols.usage.contracts/study.MainActivity")
    publisher.write(output, "contracts.json", json.dumps(report, indent=2) + "\n")
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--publisher", type=Path, required=True, help="Existing one-to-three-icon native publisher project")
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--version", required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args), indent=2))


if __name__ == "__main__":
    main()
