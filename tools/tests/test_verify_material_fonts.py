from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import sys
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "verify_material_fonts.py"
SPEC = importlib.util.spec_from_file_location("verify_material_fonts", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
verifier = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = verifier
SPEC.loader.exec_module(verifier)


class ManifestParserTest(unittest.TestCase):
    def test_aliases_are_allowed_and_reported(self) -> None:
        entries = verifier.parse_manifest_text(
            "star f09a\nstar_outline f09a\nhome e88a\n",
            "test.codepoints",
        )
        facts = verifier.manifest_facts(entries)

        self.assertEqual(3, facts["entry_count"])
        self.assertEqual(2, facts["unique_code_point_count"])
        self.assertEqual(1, facts["alias_code_point_count"])
        self.assertEqual(1, facts["alias_extra_name_count"])

    def test_blank_lines_tabs_and_supplementary_scalars_are_allowed(self) -> None:
        entries = verifier.parse_manifest_text(
            "\nfuture_symbol\t10ffff\t\nhome e88a\n",
            "test.codepoints",
        )

        self.assertEqual(
            [0x10FFFF, 0xE88A],
            [entry.code_point for entry in entries],
        )

    def test_duplicate_names_are_rejected(self) -> None:
        with self.assertRaisesRegex(verifier.ManifestError, "duplicate name"):
            verifier.parse_manifest_text(
                "home e88a\nhome e88b\n",
                "test.codepoints",
            )

    def test_invalid_names_are_rejected(self) -> None:
        for name in ("Home", "_home", "home-", "home__filled"):
            with self.subTest(name=name):
                with self.assertRaisesRegex(
                    verifier.ManifestError,
                    "expected '<snake_case_name>",
                ):
                    verifier.parse_manifest_text(
                        f"{name} e88a\n",
                        "test.codepoints",
                    )

    def test_non_scalar_codepoints_are_rejected(self) -> None:
        for code_point in ("d800", "dfff", "110000"):
            with self.subTest(code_point=code_point):
                with self.assertRaisesRegex(
                    verifier.ManifestError,
                    "not a Unicode scalar",
                ):
                    verifier.parse_manifest_text(
                        f"invalid {code_point}\n",
                        "test.codepoints",
                    )

    def test_kotlin_identifier_collisions_are_rejected(self) -> None:
        with self.assertRaisesRegex(
            verifier.ManifestError,
            "Kotlin identifier collision",
        ):
            verifier.parse_manifest_text(
                "a1b e000\na_1b e001\n",
                "test.codepoints",
            )


@unittest.skipIf(
    verifier.FONTTOOLS_IMPORT_ERROR is not None,
    "FontTools is required for font conformance tests",
)
class RepositoryConformanceTest(unittest.TestCase):
    def test_pinned_repository_snapshot_passes(self) -> None:
        report = verifier.verify_repository(verifier.REPOSITORY_ROOT)

        self.assertTrue(report["ok"], "\n".join(report["errors"]))
        self.assertEqual(4102, report["manifest"]["entry_count"])
        self.assertEqual(3802, report["manifest"]["unique_code_point_count"])
        self.assertEqual(3, len(report["fonts"]))
        for font in report["fonts"]:
            with self.subTest(style=font["style"]):
                self.assertTrue(font["single_ttf_in_style"])
                self.assertTrue(font["checksums"]["master_valid"])
                self.assertTrue(font["checksums"]["table_checksums_valid"])
                self.assertEqual(
                    3802,
                    font["cmap"]["mapped_manifest_code_point_count"],
                )
                self.assertEqual(5, font["outlines"]["sample_count"])
                self.assertEqual(5, font["outlines"]["fill_changed_count"])

    def test_json_mode_is_machine_readable_and_successful(self) -> None:
        stdout = io.StringIO()
        with contextlib.redirect_stdout(stdout):
            exit_code = verifier.main(
                ["--root", str(verifier.REPOSITORY_ROOT), "--json"]
            )

        report = json.loads(stdout.getvalue())
        self.assertEqual(0, exit_code)
        self.assertTrue(report["ok"])
        self.assertEqual(1, report["schema_version"])

    def test_human_mode_has_a_concise_success_summary(self) -> None:
        report = verifier.verify_repository(verifier.REPOSITORY_ROOT)
        output = verifier.render_human(report)

        self.assertIn("Material Symbols font verification: PASS", output)
        self.assertIn("Outlined:", output)
        self.assertIn("Rounded:", output)
        self.assertIn("Sharp:", output)


if __name__ == "__main__":
    unittest.main()
