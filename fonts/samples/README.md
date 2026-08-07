# External icon-font samples

These pinned fonts are runtime resources for the sample app's complete,
searchable catalogs. Font Awesome and Tabler are also build inputs for its
generated-vector examples.

The generator uses one canonical `<snake_case_name> <hex_code_point>` manifest.
That file shape is not an icon-font standard: each complete manifest below was
normalized from the upstream project's own metadata.

| Set | Manifest coverage | Upstream mapping | Normalization |
| --- | ---: | --- | --- |
| Font Awesome Free Solid 6.7.2 | 1,966 names / 1,402 glyphs | `metadata/icons.yml` | canonical and alias-name hyphens become underscores |
| Tabler Icons Outline 3.46.0 | 5,193 names / 5,130 glyphs | CSS `content` declarations | class names lose `ti-` and hyphens become underscores |
| Tabler Icons Filled 3.46.0 | 1,057 names / 1,054 glyphs | CSS `content` declarations | class names lose `ti-` and hyphens become underscores |
| Powerline Symbols 2.8.4 | 8 names / 8 glyphs | documented assignments and the font `cmap` | descriptive snake-case names |
| Academmunicons 200415 | 50 semantic icons | recommended PUA `cmap` entries | upstream glyph names are retained |

The sample app calls `include(...)` for only three Font Awesome/Tabler
generated-vector examples per style. Every complete manifest is rendered lazily
from its packaged font, avoiding thousands of generated vector builders.
Academmunicons starts on a regular instance baked at `ital=0,wght=400`; changing
either coordinate switches to its genuine variable font through the same
generic settings API as Material Symbols.

Other common distributions are equivalent in purpose but not syntax:
[Bootstrap Icons](https://github.com/twbs/icons/blob/v1.13.1/font/bootstrap-icons.json)
uses a JSON name-to-decimal map,
[Pictogrammers Material Design Icons](https://pictogrammers.com/docs/guides/nodejs-scripting/)
publishes JSON records with names and hexadecimal code points, IcoMoon exports
[`selection.json`](https://icomoon.io/old-docs), and Fontello exports
[`config.json`](https://github.com/fontello/fontello/blob/master/server/fontello/font/_lib/config_schema.js).
Provider-specific parsers are intentionally left outside the generator; a small
reviewed conversion keeps build inputs deterministic and the public naming
contract stable.

## Provenance

- Font Awesome Free Solid is the unmodified `webfonts/fa-solid-900.ttf` from
  revision `af620534bfc3c2d4cbefcfeec29603bbe7809e64` (tag `6.7.2`), SHA-256
  `af19d135d3a935b3ebfbd80320716ffe1202052c5f68dc2c5f1abc57005ac605`.
  This release is pinned because the 7.3.1 npm package provides WOFF2 rather
  than the TTF/OTF/TTC inputs currently accepted by the generator.
- Tabler Icons Outline uses the official package's unmodified 1, 1.5, and 2 px
  fonts: `tabler-icons-200.ttf`, `tabler-icons-300.ttf`, and
  `tabler-icons.ttf`. Their SHA-256 hashes are
  `9dfddc56080de80c8981115d9d4bfe0d91ebf632fe5fd8507df44e7e32de079c`,
  `5f9aeaa71d851dff83367660f90f759730dcf4e7937f8a474675e112d056a93c`,
  and `9920d9866628db84af956877d04ff185ee3472a9716b03a9bb958b529ae1a9da`.
  They are static fonts without an OpenType variation axis; the Tabler website
  changes inline SVG stroke width rather than varying its webfont.
- Tabler Icons Filled is the unmodified `dist/fonts/tabler-icons-filled.ttf`
  from the official `@tabler/icons-webfont` `3.46.0` npm package, SHA-256
  `e1aa44d701709565e8b33b6ccbf9dc7f78e0b435defb50a47c1c0cb162c1cab6`.
- Powerline Symbols is the unmodified `font/PowerlineSymbols.otf` from revision
  `51570938d4a558578fa3512a4b546584530e23c1` (tag `2.8.4`), SHA-256
  `4a2496a009b1649878ce067a7ec2aed9f79656c90136971e1dba00766515f7a1`.
- Academmunicons Variable is the unmodified
  `fonts/Variable-TT/Academmunicons-VF.ttf`
  from revision `6ae78e1c8831765fb5e6c4a276675a4f4e12ab73` (version `200415`),
  SHA-256
  `b9b5e711a566f16f86dcca0441977c75a88296e0f93e1b847f9e89be55bf622e`.
  The manifest contains every semantic icon from the upstream recommended PUA
  range; the font's `ital` axis morphs its frame and `wght` changes its weight.
  `academmunicons-regular.ttf` is its static `ital=0,wght=400` derivative,
  renamed “Symbols Academic Icons” under the Reserved Font Name requirement;
  SHA-256
  `8c89d561295874ccf575d4d5d128247cd40e25979ae0ae0307a525388edf77cf`.

The corresponding upstream license is retained beside each font. See the
repository's `THIRD_PARTY_NOTICES.md` for links and redistribution details.
