# Skiko deployment-copy removal

The production JavaScript sample contained two byte-identical Skiko WASM files:
`skiko.wasm` and webpack's content-hashed asset. The default bundled loader requests
the hashed asset. Excluding the raw copy from the final distribution removes
**8,640,316 bytes** (3,324,678 bytes with per-file gzip level 9).

| JavaScript distribution | Before | After |
| --- | ---: | ---: |
| All deployed files | 55,805,879 bytes | 47,165,563 bytes |
| Excluding source maps | 47,431,204 bytes | 38,790,888 bytes |
| Per-file gzip, excluding source maps | 18,005,389 bytes | 14,680,711 bytes |

These are deployment totals, not initial-page transfer measurements. The browser
already requested only the hashed file, so this change does not claim a page-load
speedup. The complete measured record is [web-skiko-dedup.json](web-skiko-dedup.json).

## Implementation and scope

`jsBrowserDistribution` is Kotlin's normal Gradle `Sync` task. A native `CopySpec`
exclusion prevents the unused file from being copied:

```kotlin
tasks.named<Sync>("jsBrowserDistribution") {
    exclude("skiko.wasm")
}
```

Compose 1.12.1's `configureWebApplication.kt` adds its unpacked Skiko runtime to
JavaScript `processResources`, based on legacy global-JavaScript integration.
Webpack now also bundles the sample's Skiko module and emits the hashed WASM.
The WasmJS path already provides the runtime directly to webpack instead of
copying it into the final distribution. These paths can be inspected in the
[published Compose Gradle plugin sources](https://repo.maven.apache.org/maven2/org/jetbrains/compose/compose-gradle-plugin/1.12.1/compose-gradle-plugin-1.12.1-sources.jar).

This change applies only to this sample's production JavaScript distribution.
Published dependencies, Skiko's raw compiler/linker input, development resources,
and the WasmJS distribution remain intact. A future custom `locateFile` override
that deliberately requests `/skiko.wasm` must retain that file or remove the
exclusion; this is not a general rule for every Skiko application.

## Verification

On 2026-09-28, `:composeApp:jsBrowserDistribution` passed with both the production
linker and webpack **UP-TO-DATE**. It removed exactly the raw copy; every remaining
file matched the previous distribution byte for byte. No minifier rebuild was
needed, and the raw linker input remained present.

A fresh Headless Chromium 151 session, driven with agent-browser against a local
HTTP server, verified home navigation, static Material fonts and Compose drawable
XML, live variable-font FILL animation, and typed ImageVector/painter samples.
Screenshots showed the expected glyphs and changing axis values. There were no
browser errors or failed requests. The only WASM request was the retained hashed
asset (HTTP 200); `/skiko.wasm` was never requested. Screenshots and raw logs remain
local rather than being committed.
