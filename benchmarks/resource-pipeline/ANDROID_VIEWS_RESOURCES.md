# Android Views resource ownership

The `android-views` sample profile now generates its Tabler native XML directly
from the existing SVG directory. Previously, it depended on the entire
`image-vector-migration` sample to obtain those resources. That dependency also
packaged an unused **14,586,584-byte Rounded variable font** and the other
sample's code and Compose assets.

The fix removes one project dependency and uses the existing
`iconSet("Tabler") { style("Outline") { svgDirectory.set(...); androidDrawables() } }`
DSL. It adds no module, source copies, icon filters, or public API changes.
Resource names and generated XML bytes remain the same.

## Measured APK payloads

Preparatory validation on 2026-09-28, using the same upgraded toolchain and
default font-compression settings in B and C:

| `android-views` APK | B: feature dependency | C: native resource generation | Change |
| --- | ---: | ---: | ---: |
| Release, unminified | 36,770,144 bytes | 17,523,059 bytes | −19,247,085 bytes (−52.3443%) |
| Shrunk release | 16,457,984 bytes | 1,808,407 bytes | −14,649,577 bytes (−89.0120%) |

The variable-font asset is absent from both C APKs. C's unminified APK retains
the 2,264-byte Powerline input font; normal resource shrinking removes that
unused input from the shrunk APK, leaving **no bundled font files** in this
profile. The additional size difference includes removed code and Compose
resources; it must not all be attributed to the variable font.

These are APK payload measurements, not controlled build-time results. The
preparatory builds used existing caches. A separate B/C timing run is required
before claiming faster builds.

## Correctness checks

- Both release and shrunk profile builds passed. APK resource tables retain the
  six requested native drawables, including both used Tabler resources, the
  generated Powerline branch, and the used Material resources.
- All three Tabler XML outputs (`home`, `hierarchy_2`, and `settings`) are
  byte-identical to B's generated native resources.
- The full `all` profile passed native-resource and asset merging with both
  Tabler producers present. Its 3,917 Compose assets, including all four font
  assets, are byte-identical to the baseline full-app APK's Compose assets.

The full sample intentionally includes the font-using features, so the
14.6 MB font saving above applies **only to the `android-views` profile**. This
change does not make Compose assets shrinkable or alter published library packs.

## Reproduce

From the C checkout, with the documented JDK, SDK, and Python environment:

```bash
python3 benchmarks/sample-app/build.py --profile android-views \
  --variant release --variant shrunk --label native-resource-owner
./gradlew :androidApp:mergeReleaseResources :androidApp:mergeReleaseAssets \
  -PsymbolsSampleProfile=all --no-daemon --max-workers=1
```

Compare payloads with the existing sample APK analyzer and inspect the final
resource tables. [Compact evidence](android-views-resource-owner.json) records
APK hashes, font inventories, retained names, and XML hashes. Raw APKs and build
logs stay local.
