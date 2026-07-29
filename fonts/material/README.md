# Material Symbols asset snapshot

This directory contains the canonical inputs for the built-in Google Material
Symbols 2.874 catalog and deterministic derived regular-font outputs:

- `MaterialSymbols.codepoints`;
- one Outlined variable TTF;
- one Rounded variable TTF;
- one Sharp variable TTF;
- one default-axis Outlined regular TTF;
- one default-axis Rounded regular TTF; and
- one default-axis Sharp regular TTF.

The manifest and variable fonts are unmodified upstream snapshots. They were
acquired on 2025-09-29 and verified byte-for-byte against immutable upstream
revision
[`bb04090f930e272697f2a1f0d7b352d92dfeee43`](https://github.com/google/material-design-icons/tree/bb04090f930e272697f2a1f0d7b352d92dfeee43/variablefont).
The `*-static` files are modified derivatives generated from those variable
fonts at `FILL=0, GRAD=0, opsz=24, wght=400`; they are not represented as
unmodified Google downloads.

Exact SHA-256 values, byte sizes, modification status, commands, and licensing
are recorded in [`THIRD_PARTY_NOTICES.md`](../../THIRD_PARTY_NOTICES.md).

## Reproduce the regular fonts

Install the pinned FontTools version in an isolated environment:

```shell
python3 -m venv /tmp/symbols-fonttools
/tmp/symbols-fonttools/bin/python -m pip install \
  -r tools/requirements-font-verification.txt
```

Regenerate all three derivatives or byte-compare the checked-in output:

```shell
/tmp/symbols-fonttools/bin/python tools/generate_material_static_fonts.py
/tmp/symbols-fonttools/bin/python tools/generate_material_static_fonts.py --check
```

The generator removes variable tables, disables timestamp recalculation, and
uses a stable table order. It rejects the wrong FontTools version, missing axes,
out-of-range coordinates, and non-variable inputs.

## Intentional update procedure

1. Identify and record a specific upstream revision.
2. Review the upstream license and notices.
3. Replace all three variable fonts and the matching codepoint manifest
   together.
4. Install the pinned verifier dependency in an isolated environment.
5. Run `tools/verify_material_fonts.py` and inspect every expected mismatch.
6. Update verifier pins only after independently confirming the new facts.
7. Regenerate the regular fonts, catalog, typed font namespaces, and all vector
   styles.
8. Run every generator in `--check` mode and the full multiplatform test suite.
9. Update version, hashes, paths, and modification status in the third-party
   notice and changelog.

Do not make the normal Gradle build download or mutate fonts. Do not update only
one variable style, carry forward stale regular derivatives, or copy a
codepoint manifest from a different font revision.

Detailed verifier setup and checked invariants are in
[`tools/FONT_VERIFICATION.md`](../../tools/FONT_VERIFICATION.md).
