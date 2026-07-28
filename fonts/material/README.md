# Material Symbols asset snapshot

This directory contains the canonical inputs for the built-in Google Material
Symbols 2.874 catalog:

- `MaterialSymbols.codepoints`;
- one Outlined variable TTF;
- one Rounded variable TTF; and
- one Sharp variable TTF.

The files are unmodified upstream snapshots. Exact SHA-256 values and licensing
are recorded in [`THIRD_PARTY_NOTICES.md`](../../THIRD_PARTY_NOTICES.md). They
were acquired on 2025-09-29 and verified byte-for-byte against immutable upstream
revision
[`bb04090f930e272697f2a1f0d7b352d92dfeee43`](https://github.com/google/material-design-icons/tree/bb04090f930e272697f2a1f0d7b352d92dfeee43/variablefont).

## Intentional update procedure

1. Identify and record a specific upstream revision.
2. Review the upstream license and notices.
3. Replace all three fonts and the matching codepoint manifest together.
4. Install the pinned verifier dependency in an isolated environment.
5. Run `tools/verify_material_fonts.py` and inspect every expected mismatch.
6. Update verifier pins only after independently confirming the new facts.
7. Regenerate the catalog and all vector styles.
8. Run every generator in `--check` mode and the full multiplatform test suite.
9. Update version, hashes, paths, and modification status in the third-party
   notice and changelog.

Do not make the normal Gradle build download fonts. Do not update only one style
or copy a codepoint manifest from a different font revision.

Detailed verifier setup and checked invariants are in
[`tools/FONT_VERIFICATION.md`](../../tools/FONT_VERIFICATION.md).
