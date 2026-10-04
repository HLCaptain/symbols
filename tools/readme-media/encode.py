#!/usr/bin/env python3
"""Encode and verify the real Compose captures; requires Pillow 12.3.0."""

from pathlib import Path

from PIL import Image, ImageChops

HERE = Path(__file__).resolve().parent
DESTINATION = HERE.parents[1] / "docs" / "media"


def encode(name: str) -> None:
    paths = sorted((HERE / "build" / "frames" / name).glob("*.png"))
    assert len(paths) == 240, f"Expected 240 captured frames, got {len(paths)}"
    frames = []
    for path in paths:
        with Image.open(path) as image:
            assert image.size == (1120, 256), f"Unexpected capture size: {path}"
            frame = image.convert("RGB")
            red, green, blue = frame.split()
            assert ImageChops.difference(red, green).getbbox() is None
            assert ImageChops.difference(red, blue).getbbox() is None, "Media must be monochrome"
            assert frame.getpixel((0, 0)) == (255, 255, 255), "Background must be white"
            frames.append(frame)

    first = frames[0]
    assert ImageChops.difference(first, frames[-1]).getbbox() is None, "Loop must return to its initial pixels"
    if name == "icons-comparison":
        left = (0, 0, 632, 256)
        assert all(ImageChops.difference(first.crop(left), frame.crop(left)).getbbox() is None for frame in frames), "Legacy side must remain still"
        crops = [(672 + i * 108, 110, 736 + i * 108, 174) for i in range(4)]
    else:
        crops = [(104 + i * 276, 86, 192 + i * 276, 174) for i in range(4)]
    for crop in crops:
        assert any(ImageChops.difference(first.crop(crop), frame.crop(crop)).getbbox() for frame in frames), f"Icon/axis did not animate: {crop}"

    DESTINATION.mkdir(parents=True, exist_ok=True)
    first.save(DESTINATION / f"{name}-still.png", optimize=True)
    target = DESTINATION / f"{name}.png"
    first.save(
        target,
        save_all=True,
        append_images=frames[1:],
        duration=[33, 34, 33] * 80,
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
        assert abs(duration - 8000) < 1, f"Unexpected duration: {duration}"
        assert ImageChops.difference(first, decoded.convert("RGB")).getbbox() is None
        print(f"{target.name}: {decoded.n_frames} encoded frames, {duration / 1000:g}s, {target.stat().st_size:,} bytes")
    for frame in frames:
        frame.close()


if __name__ == "__main__":
    encode("icons-comparison")
    encode("variable-fonts")
