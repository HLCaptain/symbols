#!/usr/bin/env python3
"""Prototype-only generator adapter; production emitters remain unchanged."""
import argparse
import importlib.util
import json
from pathlib import Path
import sys
import time
from types import SimpleNamespace

p = argparse.ArgumentParser()
p.add_argument('--root', type=Path, required=True)
p.add_argument('--style', choices=['outlined', 'rounded', 'sharp', 'themed'], required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
start = time.perf_counter()
spec = importlib.util.spec_from_file_location('vector_generator', a.root / 'tools/generate_material_vectors.py')
g = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = g
spec.loader.exec_module(g)
entries = g.parse_codepoints(g.CODEPOINTS_PATH)
if a.style == 'themed':
    g.THEMED_SOURCE_DIRECTORY = a.output / g.THEMED_PACKAGE.replace('.', '/')
    rendered = g.render_themed(entries)
else:
    original = next(s for s in g.STYLES if s.name == a.style)
    style = SimpleNamespace(name=original.name, title=original.title,
                            typed_root=original.typed_root, font_path=original.font_path,
                            package_name=original.package_name,
                            source_directory=a.output / original.package_name.replace('.', '/'))
    codepoints = tuple(sorted({cp for _, cp in entries}))
    tt, pen, transform, version = g.import_fonttools()
    paths = g.extract_paths(style, codepoints, tt, pen, transform)
    rendered = g.render_style(style, entries, codepoints, paths)
for path, content in rendered.items():
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8', newline='\n')
print(json.dumps({'style': a.style, 'seconds': time.perf_counter() - start,
                  'files': len(rendered), 'bytes': sum(len(s.encode()) for s in rendered.values())}))
