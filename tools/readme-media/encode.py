#!/usr/bin/env python3
"""Encode and verify the real Compose captures; requires Pillow 12.3.0."""

import csv
from pathlib import Path

from PIL import Image, ImageChops

HERE = Path(__file__).resolve().parent
DESTINATION = HERE.parents[1] / "docs" / "media"


def encode(name: str) -> None:
    paths = sorted((HERE / "build" / "frames" / name).glob("*.png"))
    count = 456 if name == "icons-comparison" else 240
    assert len(paths) == count, f"Expected {count} captured frames, got {len(paths)}"
    frames = []
    for path in paths:
        with Image.open(path) as image:
            assert image.size == (1000, 200), f"Unexpected capture size: {path}"
            frame = image.convert("RGB")
            red, green, blue = frame.split()
            assert ImageChops.difference(red, green).getbbox() is None
            assert ImageChops.difference(red, blue).getbbox() is None, "Media must be monochrome"
            assert frame.getpixel((0, 0)) == (255, 255, 255), "Background must be white"
            frames.append(frame)

    first = frames[0]
    assert ImageChops.difference(first, frames[-1]).getbbox() is None, "Loop must return to its initial pixels"
    if name == "icons-comparison":
        left = (0, 0, 578, 200)
        assert all(ImageChops.difference(first.crop(left), frame.crop(left)).getbbox() is None for frame in frames), "Legacy side must remain still"
        crops = [(588 + i * 104, 58, 668 + i * 104, 138) for i in range(4)]
        expected = [
            (1, 400, 24), (0, 400, 24), (0, 700, 24), (0, 700, 20),
            (0, 700, 48), (0, 100, 48), (0, 100, 20), (0, 400, 20),
            (0, 400, 48), (0, 400, 24), (1, 400, 24), (1, 700, 24),
            (1, 700, 20), (1, 700, 48), (1, 100, 48), (1, 100, 20),
            (1, 400, 20), (1, 400, 48), (1, 400, 24),
        ]
        with (paths[0].parent / "states.csv").open() as source:
            states = list(csv.DictReader(source))
        assert len(states) == len(expected)
        for index, (state, axes) in enumerate(zip(states, expected)):
            assert int(state["frame"]) == index * 48
            assert float(state["grade"]) == 0
            assert tuple(float(state[key]) for key in ("fill", "weight", "opticalSize")) == axes
        # Every prescribed transition must visibly reach the renderer before the next one.
        settled = [frames[index * 24 + 23].crop((578, 50, 1000, 145)) for index in range(len(expected))]
        assert all(ImageChops.difference(a, b).getbbox() for a, b in zip(settled, settled[1:])), "Missing visible transition"
    else:
        crops = [(73 + i * 250, 44, 177 + i * 250, 148) for i in range(4)]
        expected = [
            (0, 400, 0, 24), (1, 700, 200, 48), (0, 400, 0, 24),
            (1, 100, -50, 20), (0, 400, 0, 24),
        ]
        with (paths[0].parent / "states.csv").open() as source:
            states = list(csv.DictReader(source))
        assert len(states) == len(expected)
        for index, (state, axes) in enumerate(zip(states, expected)):
            assert int(state["frame"]) == index * 96
            assert tuple(float(state[key]) for key in ("fill", "weight", "grade", "opticalSize")) == axes
        for axis, crop in enumerate(crops):
            settled = [frames[index * 48 + 47].crop(crop) for index in range(len(expected))]
            assert all(ImageChops.difference(a, b).getbbox() for a, b in zip(settled, settled[1:])), "Every axis transition must render"
            assert all(ImageChops.difference(settled[0], settled[index]).getbbox() is None for index in (2, 4)), "Medium/unfilled states must match"
            if axis == 0:
                assert ImageChops.difference(settled[1], settled[3]).getbbox() is None, "Both filled states must match"
            else:
                assert ImageChops.difference(settled[1], settled[3]).getbbox(), "Small and large must differ"
    for crop in crops:
        assert any(ImageChops.difference(first.crop(crop), frame.crop(crop)).getbbox() for frame in frames), f"Icon/axis did not animate: {crop}"

    DESTINATION.mkdir(parents=True, exist_ok=True)
    first.save(DESTINATION / f"{name}-still.png", optimize=True)
    target = DESTINATION / f"{name}.png"
    first.save(
        target,
        save_all=True,
        append_images=frames[1:],
        duration=[33, 34, 33] * (count // 3),
        loop=2,
        disposal=0,
        blend=0,
        optimize=True,
    )
    # Check the encoded output, not just the source frames. Pillow coalesces identical holds.
    with Image.open(target) as decoded:
        assert decoded.is_animated and decoded.n_frames > 30
        assert decoded.info["loop"] == 2
        duration = 0
        for index in range(decoded.n_frames):
            decoded.seek(index)
            duration += decoded.info["duration"]
        assert abs(duration - count * 1000 / 30) < 1, f"Unexpected duration: {duration}"
        assert ImageChops.difference(first, decoded.convert("RGB")).getbbox() is None
        print(f"{target.name}: {decoded.n_frames} encoded frames, {duration / 1000:g}s, {target.stat().st_size:,} bytes")
    for frame in frames:
        frame.close()


if __name__ == "__main__":
    encode("icons-comparison")
    encode("variable-fonts")
