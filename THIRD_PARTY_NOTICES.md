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

Generated Kotlin path data under `symbols/material-vectors-outlined`,
`symbols/material-vectors-rounded`, and `symbols/material-vectors-sharp` is a
deterministic default-axis conversion of outlines from those fonts. That path
data is derivative third-party material and remains subject to the upstream
Apache License 2.0 and Google LLC copyright; it is not represented as
project-original icon artwork.

The names “Google,” “Material,” and “Material Symbols” may be trademarks of
their respective owners. The Apache License 2.0 does not grant trademark
rights. References in this repository identify the origin and compatibility
of the assets; they do not imply endorsement by Google.

### Updating these assets

An update must be reviewable and reproducible:

1. Pin an identifiable upstream revision and record the upstream font version.
2. Replace the fonts and codepoint data together.
3. Regenerate dependent sources and run the complete test suite.
4. Verify that each bundled font is byte-for-byte identical to its intended
   upstream source, or prominently document every modification.
5. Replace the paths, version, and SHA-256 values in this notice.

To audit the recorded hashes on macOS or Linux:

```shell
shasum -a 256 \
  fonts/material/outlined/composeResources/font/material_symbols_outlined_variable.ttf \
  fonts/material/rounded/composeResources/font/material_symbols_rounded_variable.ttf \
  fonts/material/sharp/composeResources/font/material_symbols_sharp_variable.ttf
```
