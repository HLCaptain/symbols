# Material font verification

`verify_material_fonts.py` is the maintainer and CI conformance check for the
pinned Google Material Symbols snapshot. It is not needed by applications that
consume the library and is intentionally separate from the standard-library-only
catalog generator.

Install the pinned verifier dependency in an isolated environment:

```shell
python3 -m venv /tmp/symbols-fonttools
/tmp/symbols-fonttools/bin/python -m pip install \
  -r tools/requirements-font-verification.txt
```

Run the readable report:

```shell
/tmp/symbols-fonttools/bin/python tools/verify_material_fonts.py
```

Run the deterministic JSON report:

```shell
/tmp/symbols-fonttools/bin/python tools/verify_material_fonts.py --json
```

Both modes exit with status 0 only when every check passes. The verifier checks:

- the pinned manifest and font SHA-256 values;
- strict manifest syntax, unique names, Unicode scalar values, and the pinned
  alias shape;
- one TrueType variable font in each style artifact;
- sfnt and per-table checksums and the expected table set;
- version, family, glyph, cmap, and variable-axis metadata;
- coverage of every manifest codepoint in every style; and
- representative nonempty outlines at default axes and `FILL=1`, including
  proof that the FILL axis changes responsive symbols.

Run its tests with the same environment:

```shell
/tmp/symbols-fonttools/bin/python -m unittest \
  tools.tests.test_verify_material_fonts
```

## Updating Material Symbols

Digest and shape mismatches are expected during an intentional upstream update.
Do not bypass them. Review the new upstream revision and license, update all
three styles and the manifest together, regenerate catalogs, run conformance
tests, and then update:

- the snapshot constants in `tools/verify_material_fonts.py`; and
- the version, paths, and SHA-256 values in `THIRD_PARTY_NOTICES.md`.

The normal build must not download fonts or run FontTools. Asset acquisition and
font mutation belong in explicit maintainer workflows.
