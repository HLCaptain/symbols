# External icon-font samples

These pinned fonts are build inputs for the sample app. The generated vectors,
not the input fonts, are packaged in the app.

The generator uses one canonical `<snake_case_name> <hex_code_point>` manifest.
That file shape is not an icon-font standard: each complete manifest below was
normalized from the upstream project's own metadata.

| Set | Manifest coverage | Upstream mapping | Normalization |
| --- | ---: | --- | --- |
| Font Awesome Free Solid 6.7.2 | 1,966 names / 1,402 glyphs | `metadata/icons.yml` | canonical and alias-name hyphens become underscores |
| Tabler Icons Filled 3.46.0 | 1,057 names / 1,054 glyphs | CSS `content` declarations | class names lose `ti-` and hyphens become underscores |
| Powerline Symbols 2.8.4 | 8 names / 8 glyphs | documented assignments and the font `cmap` | descriptive snake-case names |

The sample app still calls `include(...)` for only three names per set. That
keeps generated output small without truncating the reusable manifests.

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
- Tabler Icons Filled is the unmodified `dist/fonts/tabler-icons-filled.ttf`
  from the official `@tabler/icons-webfont` `3.46.0` npm package, SHA-256
  `e1aa44d701709565e8b33b6ccbf9dc7f78e0b435defb50a47c1c0cb162c1cab6`.
- Powerline Symbols is the unmodified `font/PowerlineSymbols.otf` from revision
  `51570938d4a558578fa3512a4b546584530e23c1` (tag `2.8.4`), SHA-256
  `4a2496a009b1649878ce067a7ec2aed9f79656c90136971e1dba00766515f7a1`.

The corresponding upstream license is retained beside each font. See the
repository's `THIRD_PARTY_NOTICES.md` for links and redistribution details.
