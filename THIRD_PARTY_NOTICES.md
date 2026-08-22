# Third-party notices

This repository includes third-party assets in addition to project-owned
source code. The project as a whole is distributed under the Apache License
2.0 in [LICENSE](LICENSE). Third-party material remains subject to the
applicable upstream notices and license described below.

## Google Material Symbols

- Copyright: Google LLC
- Upstream project: [google/material-design-icons](https://github.com/google/material-design-icons)
- Upstream revision: [`bb04090f930e272697f2a1f0d7b352d92dfeee43`](https://github.com/google/material-design-icons/commit/bb04090f930e272697f2a1f0d7b352d92dfeee43) (`Update Symbols`, 2025-09-19)
- Immutable font sources: [`variablefont` at the pinned revision](https://github.com/google/material-design-icons/tree/bb04090f930e272697f2a1f0d7b352d92dfeee43/variablefont)
- Snapshot acquired and independently re-verified: 2025-09-29; re-verified 2026-07-28
- Icon browser and documentation: [Google Fonts — Material Symbols](https://fonts.google.com/icons)
- License: [Apache License 2.0 at the pinned revision](https://github.com/google/material-design-icons/blob/bb04090f930e272697f2a1f0d7b352d92dfeee43/LICENSE)
- Bundled font version: 2.874

The following files are unmodified snapshots of the upstream Material Symbols
variable fonts. The hashes describe the exact files currently carried by this
repository:

| Style | Repository path | SHA-256 |
| --- | --- | --- |
| Outlined | `fonts/material/outlined/composeResources/font/material_symbols_outlined_variable.ttf` | `fb0d00bfa03507fe6712348604aea33741b41f97643e109ad4e582b1a15865b9` |
| Rounded | `fonts/material/rounded/composeResources/font/material_symbols_rounded_variable.ttf` | `d719f22fdee27e344b07e46e6fa8b50b1fce3cfcb03d4a84f03fafbf0812fc22` |
| Sharp | `fonts/material/sharp/composeResources/font/material_symbols_sharp_variable.ttf` | `e76edbb72b8cbca2380c507cd17ea5cb4023cf3e01a9dcfeddd670da8c495f37` |

The repository also carries three modified, default-axis regular-font
derivatives. They were generated on 2026-07-29 from the corresponding unmodified
files above with FontTools 4.59.0 at
`FILL=0, GRAD=0, opsz=24, wght=400`. Variable tables are removed; timestamps are
not recalculated; and tables are written in stable order. These files remain
Google Material Symbols artwork under the upstream Apache License 2.0 and are
not represented as unmodified upstream downloads:

| Style | Repository path | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Outlined | `fonts/material/outlined-static/composeResources/font/material_symbols_outlined_regular.ttf` | 1,303,612 | `f73c7bcb7dbeab41fe741cbc3dd73f8fd3d0569c386ca82ceee60ccf5c1fbc52` |
| Rounded | `fonts/material/rounded-static/composeResources/font/material_symbols_rounded_regular.ttf` | 1,700,344 | `874908e92b9eb30b225f213980a480e12b8909a283ad3a723b520460d0d5e128` |
| Sharp | `fonts/material/sharp-static/composeResources/font/material_symbols_sharp_regular.ttf` | 1,149,652 | `8f6dacd3736db8f70190f6eed1dfcc24a12baefa9dee4f60e3bd027cc2f85691` |

The exact generation and byte-comparison commands, run from the repository
root, are:

```shell
python3 -m venv /tmp/symbols-fonttools
/tmp/symbols-fonttools/bin/python -m pip install \
  -r tools/requirements-font-verification.txt
/tmp/symbols-fonttools/bin/python tools/generate_material_static_fonts.py
/tmp/symbols-fonttools/bin/python tools/generate_material_static_fonts.py --check
```

`fonts/material/MaterialSymbols.codepoints` is the corresponding upstream
name-to-codepoint map. It is used as input when generating the Kotlin symbol
catalog. Its SHA-256 is
`3e5293b71c38cb0487ab5fc5de293956aab4f96f445ae482849cce3df345aec9`.

All four bundled inputs were downloaded again from the pinned revision on
2026-07-28 and compared byte-for-byte with the repository copies. Immutable
raw source URLs are formed from the revision and upstream filenames:

- [`MaterialSymbolsOutlined[FILL,GRAD,opsz,wght].ttf`](https://raw.githubusercontent.com/google/material-design-icons/bb04090f930e272697f2a1f0d7b352d92dfeee43/variablefont/MaterialSymbolsOutlined%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf)
- [`MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf`](https://raw.githubusercontent.com/google/material-design-icons/bb04090f930e272697f2a1f0d7b352d92dfeee43/variablefont/MaterialSymbolsRounded%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf)
- [`MaterialSymbolsSharp[FILL,GRAD,opsz,wght].ttf`](https://raw.githubusercontent.com/google/material-design-icons/bb04090f930e272697f2a1f0d7b352d92dfeee43/variablefont/MaterialSymbolsSharp%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf)
- [`MaterialSymbolsOutlined[FILL,GRAD,opsz,wght].codepoints`](https://raw.githubusercontent.com/google/material-design-icons/bb04090f930e272697f2a1f0d7b352d92dfeee43/variablefont/MaterialSymbolsOutlined%5BFILL%2CGRAD%2Copsz%2Cwght%5D.codepoints)

Generated Kotlin vector operations under `symbols/material-vectors-outlined`,
`symbols/material-vectors-rounded`, and `symbols/material-vectors-sharp` are a
deterministic default-axis conversion of outlines from those fonts. Those
operations are derivative third-party material and remain subject to the
upstream Apache License 2.0 and Google LLC copyright; they are not represented
as project-original icon artwork.

The Android Views migration sample also checks in Google's downloaded
`home` VectorDrawable at
`samples/android-views/src/androidMain/res/drawable/google_home_f0_w400_g0_opsz24.xml`.
It was selected from [Google Fonts Icons](https://fonts.google.com/icons?selected=Material+Symbols+Outlined%3Ahome%3AFILL%400%3Bwght%40400%3BGRAD%400%3Bopsz%4024)
and downloaded on 2026-08-12 from the corresponding
[24 px XML endpoint](https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/home/default/24px.xml)
with Outlined, `FILL=0`, `wght=400`, `GRAD=0`, and `opsz=24`. The checked-in
file, including its provenance comment, has SHA-256
`9a295556c9956bd80ddae66bf30111300bf99ce5b9e3fa7f13c4c10c67f80cfd`
and remains Google artwork under Apache License 2.0.

The names “Google,” “Material,” and “Material Symbols” may be trademarks of
their respective owners. The Apache License 2.0 does not grant trademark
rights. References in this repository identify the origin and compatibility
of the assets; they do not imply endorsement by Google.

### Updating these assets

An update must be reviewable and reproducible:

1. Pin an identifiable upstream revision and record the upstream font version.
2. Replace the variable fonts and codepoint data together.
3. Regenerate the regular fonts and dependent sources, then run the complete
   test suite.
4. Verify that each bundled font is byte-for-byte identical to its intended
   upstream source, or prominently document every modification and reproducible
   derivation.
5. Replace the paths, version, and SHA-256 values in this notice.

To audit the recorded hashes on macOS or Linux:

```shell
shasum -a 256 \
  fonts/material/outlined/composeResources/font/material_symbols_outlined_variable.ttf \
  fonts/material/rounded/composeResources/font/material_symbols_rounded_variable.ttf \
  fonts/material/sharp/composeResources/font/material_symbols_sharp_variable.ttf \
  fonts/material/outlined-static/composeResources/font/material_symbols_outlined_regular.ttf \
  fonts/material/rounded-static/composeResources/font/material_symbols_rounded_regular.ttf \
  fonts/material/sharp-static/composeResources/font/material_symbols_sharp_regular.ttf
```

## Font Awesome Free Solid fixture

- Copyright: 2024 Fonticons, Inc.
- Upstream project: [FortAwesome/Font-Awesome](https://github.com/FortAwesome/Font-Awesome)
- Version/revision: [`6.7.2`](https://github.com/FortAwesome/Font-Awesome/tree/af620534bfc3c2d4cbefcfeec29603bbe7809e64), commit `af620534bfc3c2d4cbefcfeec29603bbe7809e64`
- Font license: SIL Open Font License 1.1, with Reserved Font Name “Font Awesome”
- Icon/generated-outline license: Creative Commons Attribution 4.0
- Metadata/code license: MIT
- Complete upstream terms retained at `fonts/samples/font-awesome-free-solid/LICENSE.txt`

The binary and manifest are retained as pinned generator and provenance
fixtures; no current launcher module depends on them.

`fonts/samples/font-awesome-free-solid/composeResources/font/font_awesome_free_solid.ttf`
is the unmodified
upstream `webfonts/fa-solid-900.ttf`, SHA-256
`af19d135d3a935b3ebfbd80320716ffe1202052c5f68dc2c5f1abc57005ac605`.
The manifest contains all 1,402 Free Solid glyphs under 1,402 canonical names
and 564 official aliases from the pinned
[`metadata/icons.yml`](https://github.com/FortAwesome/Font-Awesome/blob/af620534bfc3c2d4cbefcfeec29603bbe7809e64/metadata/icons.yml)
by replacing hyphens with underscores. Generated outlines remain Font Awesome
third-party material; no brand icons are included.

## Tabler Icons fixture

- Copyright: 2020–2026 Paweł Kuna
- Upstream project: [tabler/tabler-icons](https://github.com/tabler/tabler-icons)
- Version/revision: [`v3.46.0`](https://github.com/tabler/tabler-icons/tree/v3.46.0), commit `8ac7d81b72ece11072ef25ea9fd92e80c6f3c9fc`
- Distribution: official [`@tabler/icons-webfont` 3.46.0](https://www.npmjs.com/package/@tabler/icons-webfont/v/3.46.0) package
- License: MIT, retained beside both sample font families

The binaries and manifests are retained as pinned generator and provenance
fixtures; no current launcher module depends on them.

`fonts/samples/tabler-icons-filled/composeResources/font/tabler_icons_filled.ttf`
is the unmodified
package font, SHA-256
`e1aa44d701709565e8b33b6ccbf9dc7f78e0b435defb50a47c1c0cb162c1cab6`.
The manifest contains all 1,057 CSS names for the font's 1,054 encoded glyphs,
normalized from the package's `content` declarations. These include
supplementary Unicode scalars such as U+101B2.

The outline sample carries the official package's three static stroke fonts.
The package's build maps `tabler-icons-200.ttf` to 1 px,
`tabler-icons-300.ttf` to 1.5 px, and `tabler-icons.ttf` to the default 2 px.
They are stored under `composeResources/font` as `tabler_icons_outline_1.ttf`,
`tabler_icons_outline_1_5.ttf`, and `tabler_icons_outline_2.ttf` with SHA-256:

| Stroke | SHA-256 |
| ---: | --- |
| 1 px | `9dfddc56080de80c8981115d9d4bfe0d91ebf632fe5fd8507df44e7e32de079c` |
| 1.5 px | `5f9aeaa71d851dff83367660f90f759730dcf4e7937f8a474675e112d056a93c` |
| 2 px | `9920d9866628db84af956877d04ff185ee3472a9716b03a9bb958b529ae1a9da` |

The outline manifest contains all 5,193 CSS names for 5,130 encoded glyphs,
normalized with the same rules as the filled manifest.
These fonts have no OpenType variation axis. Tabler's website changes the
`stroke-width` of inline SVG paths instead, so selectable font strokes require
the separate binaries above.

## Powerline Symbols custom sample

- Copyright: 2013 Kim Silkebækken and other contributors
- Upstream project: [powerline/powerline](https://github.com/powerline/powerline)
- Version/revision: [`2.8.4`](https://github.com/powerline/powerline/tree/51570938d4a558578fa3512a4b546584530e23c1), commit `51570938d4a558578fa3512a4b546584530e23c1`
- License: MIT, retained at `samples/custom-static/LICENSE`; the canonical copy
  remains at `fonts/samples/powerline/LICENSE`

The font at
`samples/custom-static/src/commonMain/composeResources/font/powerline_symbols.otf`
is the unmodified upstream font,
SHA-256
`4a2496a009b1649878ce067a7ec2aed9f79656c90136971e1dba00766515f7a1`.
The canonical manifest at `fonts/samples/powerline/PowerlineSymbols.codepoints`
covers all eight Unicode mappings in the font: U+2588 and the documented
Powerline assignments U+E0A0–U+E0A2 and U+E0B0–U+E0B3. The runnable module uses
a byte-identical copy.

## Academmunicons custom sample and fixture

- Copyright: 2020 Academmunicons Authors; 2014 Creative Commons
- Upstream project: [twardoch/academmunicons-font](https://github.com/twardoch/academmunicons-font)
- Version/revision: `200415`, commit [`6ae78e1c8831765fb5e6c4a276675a4f4e12ab73`](https://github.com/twardoch/academmunicons-font/tree/6ae78e1c8831765fb5e6c4a276675a4f4e12ab73)
- License: SIL Open Font License 1.1, with Reserved Font Name “Academmunicons”
- Complete upstream terms retained at `samples/custom-variable/LICENSE`; the
  canonical copy remains at `fonts/samples/academmunicons/LICENSE.txt`

`samples/custom-variable/src/commonMain/composeResources/font/academmunicons_variable.ttf`
is the unmodified
upstream `fonts/Variable-TT/Academmunicons-VF.ttf`, SHA-256
`b9b5e711a566f16f86dcca0441977c75a88296e0f93e1b847f9e89be55bf622e`.
It exposes `ital` 0–1 and `wght` 100–800. The canonical manifest at
`fonts/samples/academmunicons/Academmunicons.codepoints` contains all 50
semantic icons in the upstream recommended PUA range; its snake-case names come
directly from the font's `cmap` glyph names. The runnable module uses a
byte-identical copy.

`fonts/samples/academmunicons/academmunicons-regular.ttf` is a modified static
instance generated with FontTools 4.60.2 at `ital=0,wght=400`. Its variation
tables were removed and, as required by the Reserved Font Name clause, its
family was renamed “Symbols Academic Icons”. SHA-256:
`8c89d561295874ccf575d4d5d128247cd40e25979ae0ae0307a525388edf77cf`.
