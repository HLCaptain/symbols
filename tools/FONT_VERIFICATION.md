# Material font verification

`verify_material_fonts.py` is the maintainer and CI conformance check for the
pinned, unmodified Google Material Symbols variable-font snapshot. It is not
needed by applications that consume the library and is intentionally separate
from the standard-library-only catalog generator. The derived regular fonts use
the same isolated environment but are verified separately by deterministic byte
regeneration.

Install the pinned verifier dependency in an isolated environment:

```shell
python3 -m venv /tmp/symbols-fonttools
/tmp/symbols-fonttools/bin/python -m pip install \
  -r tools/requirements-font-verification.txt
```

Run the readable report:

```shell
/tmp/symbols-fonttools/bin/python tools/verify_material_fonts.py
/tmp/symbols-fonttools/bin/python tools/generate_material_static_fonts.py --check
```

Run the deterministic JSON report:

```shell
/tmp/symbols-fonttools/bin/python tools/verify_material_fonts.py --json
```

Both verifier report modes exit with status 0 only when every check passes. The
verifier checks:

- the pinned manifest and font SHA-256 values;
- strict manifest syntax, unique names, Unicode scalar values, and the pinned
  alias shape;
- one TrueType variable font in each style artifact;
- sfnt and per-table checksums and the expected table set;
- version, family, glyph, cmap, and variable-axis metadata;
- coverage of every manifest codepoint in every style; and
- representative nonempty outlines at default axes and `FILL=1`, including
  proof that the FILL axis changes responsive symbols.

`generate_material_static_fonts.py --check` independently reconstructs each
API-21 regular font at `FILL=0, GRAD=0, opsz=24, wght=400` and byte-compares it
with the checked-in derivative. It also verifies the exact FontTools version and
that no variable tables survive instancing. The derivative hashes and
modification status live in `THIRD_PARTY_NOTICES.md`.

Run its tests with the same environment:

```shell
/tmp/symbols-fonttools/bin/python -m unittest \
  tools.tests.test_verify_material_fonts \
  tools.tests.test_generate_material_static_fonts
```

## Updating Material Symbols

Digest and shape mismatches are expected during an intentional upstream update.
Do not bypass them. Review the new upstream revision and license, update all
three variable styles and the manifest together, regenerate the regular fonts,
catalogs, namespaces, and vectors, run conformance tests, and then update:

- the snapshot constants in `tools/verify_material_fonts.py`; and
- the version, paths, modification status, and SHA-256 values in
  `THIRD_PARTY_NOTICES.md`.

The normal build must not download fonts or run FontTools. Asset acquisition and
font mutation belong in explicit maintainer workflows.
