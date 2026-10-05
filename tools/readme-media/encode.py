#!/usr/bin/env python3
"""Verify Compose captures with Pillow and encode their unchanged frames with FFmpeg."""

import colorsys
import csv
from pathlib import Path
import shutil
import subprocess
import sys

from PIL import Image, ImageChops

HERE = Path(__file__).resolve().parent
DESTINATION = HERE.parents[1] / "docs" / "media"


def encode(name: str) -> None:
    assert name in ("icons-comparison", "variable-fonts"), f"Unknown capture: {name}"
    paths = sorted((HERE / "build" / "frames" / name).glob("*.png"))
    with (HERE / "build" / "frames" / name / "states.csv").open() as source:
        states = list(csv.DictReader(source))
    count = int(states[-1]["endFrame"]) // 2 if name == "icons-comparison" else 240
    assert len(paths) == count, f"Expected {count} captured frames, got {len(paths)}"
    assert [p.name for p in paths] == [f"{i:04d}.png" for i in range(count)], "Missing or stale frames"
    frames = []
    for path in paths:
        with Image.open(path) as image:
            assert image.size == (1000, 200), f"Unexpected capture size: {path}"
            frame = image.convert("RGB")
            if name == "variable-fonts":
                red, green, blue = frame.split()
                assert ImageChops.difference(red, green).getbbox() is None
                assert ImageChops.difference(red, blue).getbbox() is None, "Variable demo stays monochrome"
            assert frame.getpixel((0, 0)) == (255, 255, 255), "Background must be white"
            frames.append(frame)

    first = frames[0]
    shapes = frames
    assert ImageChops.difference(first, frames[-1]).getbbox() is None, "Loop must return to its initial pixels"
    if name == "icons-comparison":
        shape_paths = sorted((paths[0].parent / "shapes").glob("*.png"))
        assert [p.name for p in shape_paths] == [p.name for p in paths], "Missing native shape references"
        shapes = []
        for path in shape_paths:
            with Image.open(path) as image:
                assert image.size == (1000, 200)
                shapes.append(image.convert("L"))
        left = (0, 0, 450, 200)
        first_left = first.crop(left)
        red, green, blue = first_left.split()
        assert ImageChops.difference(red, green).getbbox() is None
        assert ImageChops.difference(red, blue).getbbox() is None, "Legacy icons must stay black"
        assert all(ImageChops.difference(first_left, frame.crop(left)).getbbox() is None for frame in frames), "Legacy side must remain still"
        for crop in ((0, 0, 1000, 45), (0, 150, 1000, 200)):
            assert all(ImageChops.difference(first.crop(crop), frame.crop(crop)).getbbox() is None for frame in frames), "Text and background must remain still"
        arrow_crop = (450, 50, 578, 145)
        arrow_boxes = []
        for frame in frames:
            red, green, blue = frame.crop(arrow_crop).split()
            assert ImageChops.difference(red, green).getbbox() is None
            assert ImageChops.difference(red, blue).getbbox() is None, "Arrow must stay black"
            arrow_boxes.append(ImageChops.invert(red).getbbox())
        resting_arrow = arrow_boxes[0]
        assert resting_arrow is not None
        assert all(box is not None and box[1::2] == resting_arrow[1::2]
                   and box[2] - box[0] == resting_arrow[2] - resting_arrow[0]
                   and 0 <= box[0] - resting_arrow[0] <= 8 for box in arrow_boxes), "Arrow must move horizontally within 8 pixels"
        crops = [(588 + i * 104, 58, 668 + i * 104, 138) for i in range(4)]
        expected = [
            (1, 400, 0, 24), (1, 100, 0, 24), (0, 100, 0, 24), (0, 400, 0, 24),
            (1, 400, 0, 24), (1, 700, 0, 24), (0, 700, 0, 24), (0, 400, 0, 24),
            (1, 400, 0, 24), (1, 400, 0, 20), (0, 400, 0, 20), (0, 400, 0, 24),
            (1, 400, 0, 24), (1, 400, 0, 48), (0, 400, 0, 48), (0, 400, 0, 24),
            (1, 400, 0, 24), (1, 400, -50, 24), (0, 400, -50, 24), (0, 400, 0, 24),
            (1, 400, 0, 24), (1, 400, 200, 24), (0, 400, 200, 24), (0, 400, 0, 24),
            (1, 400, 0, 24),
        ]
        assert len(states) == len(expected)
        previous_end = 0
        settled = []
        for index, (state, axes) in enumerate(zip(states, expected)):
            start, finish, end = (int(state[key]) for key in ("frame", "settledFrame", "endFrame"))
            assert start == previous_end and start <= finish < end
            assert all(frame % 2 == 0 for frame in (start, finish, end)), "Timing must align with exported frames"
            assert end - finish == 90, "Every completed spring needs a 1.5-second hold"
            assert (finish == start) if index == 0 else (finish > start), "Wait for the animation completion callback"
            assert tuple(float(state[key]) for key in ("fill", "weight", "grade", "opticalSize")) == axes
            hold = shapes[finish // 2:end // 2]
            assert all(ImageChops.difference(hold[0], frame).getbbox() is None for frame in hold), "Settled hold must keep its shape"
            colored_hold = frames[finish // 2:end // 2]
            assert all(ImageChops.difference(colored_hold[0], frame).getbbox() is None for frame in colored_hold), "Tint must stay fixed throughout the hold"
            expected_color = tuple(round(channel * 255) for channel in colorsys.hsv_to_rgb(index / (len(expected) - 1), 1, 0.6))
            for crop in crops:
                ink = min(colored_hold[0].crop(crop).get_flattened_data(), key=sum)
                assert all(abs(actual - color) <= 1 for actual, color in zip(ink, expected_color)), f"Unexpected settled Symbols tint in state {index}: {ink} != {expected_color}"
            if index:
                transition = frames[start // 2:finish // 2]
                tints = {min(frame.crop(crops[0]).get_flattened_data(), key=sum) for frame in transition}
                assert len(tints) > 1, "Tint must animate during the transition"
                arrow_track = [box[0] - resting_arrow[0] for box in arrow_boxes[start // 2:finish // 2]]
                assert max(arrow_track) == 8 and arrow_track[0] == 0 and arrow_track[-1] == 0, "Each transition needs a complete arrow nudge"
                peak = arrow_track.index(8)
                assert all(a <= b for a, b in zip(arrow_track[:peak], arrow_track[1:peak + 1]))
                assert all(a >= b for a, b in zip(arrow_track[peak:], arrow_track[peak + 1:])), "Arrow must return gently without reversing again"
                assert ImageChops.difference(colored_hold[0].crop(arrow_crop), first.crop(arrow_crop)).getbbox() is None, "Arrow must return to rest before the hold"
            settled.append(hold[0].crop((578, 50, 1000, 145)))
            previous_end = end
        changes = [[a != b for a, b in zip(before, after)] for before, after in zip(expected, expected[1:])]
        assert all(sum(change) == 1 for change in changes), "Only one axis may change at a time"
        assert [sum(change[axis] for change in changes) for axis in range(4)] == [12, 4, 4, 4]
        # Every prescribed transition must visibly reach the renderer before the next one.
        assert all(ImageChops.difference(a, b).getbbox() for a, b in zip(settled, settled[1:])), "Missing visible transition"
    else:
        crops = [(73 + i * 250, 44, 177 + i * 250, 148) for i in range(4)]
        expected = [
            (0, 400, 0, 24), (1, 100, -50, 20), (0, 400, 0, 24),
            (1, 700, 200, 48), (0, 400, 0, 24),
        ]
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
        assert any(ImageChops.difference(shapes[0].crop(crop), frame.crop(crop)).getbbox() for frame in shapes), f"Icon/axis did not animate: {crop}"

    DESTINATION.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(paths[0], DESTINATION / f"{name}-still.png")
    target = DESTINATION / f"{name}.png"
    subprocess.run([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-framerate", "30",
        "-i", str(paths[0].parent / "%04d.png"), "-pix_fmt", "rgb24", "-pred", "mixed", "-compression_level", "9",
        "-plays", "2", "-final_delay", "1/30", "-f", "apng", str(target),
    ], check=True)
    # Inspect the encoded output as well as the source frames; Pillow never writes media.
    with Image.open(target) as decoded:
        assert decoded.is_animated and decoded.n_frames == count
        assert decoded.info["loop"] == 2
        duration = 0
        for index in range(decoded.n_frames):
            decoded.seek(index)
            assert ImageChops.difference(frames[index], decoded.convert("RGB")).getbbox() is None, f"Encoded frame {index} differs from native capture"
            duration += decoded.info["duration"]
        assert abs(duration - count * 1000 / 30) < 1, f"Unexpected duration: {duration}"
        assert ImageChops.difference(first, decoded.convert("RGB")).getbbox() is None
        print(f"{target.name}: {decoded.n_frames} encoded frames, {duration / 1000:g}s, {target.stat().st_size:,} bytes")
    for frame in frames:
        frame.close()
    if shapes is not frames:
        for frame in shapes:
            frame.close()


if __name__ == "__main__":
    for name in sys.argv[1:] or ("icons-comparison", "variable-fonts"):
        encode(name)
