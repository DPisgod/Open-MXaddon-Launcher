"""Build the blue Mx OS icon as SVG, antialiased PNGs and a multi-size ICO.

Requires Pillow only; the launcher itself does not depend on Python.
"""
from pathlib import Path
import struct

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parent
OUTPUT = ASSETS.parent / "src/main/resources/icons"
SIZES = (16, 24, 32, 48, 64, 128, 256)
BACKGROUND = "#9cc4ff"
FOREGROUND = "#14263c"
# Keep the original geometric M, with a smaller lowercase x on its baseline.
LETTERS = (
    ((43, 187), (43, 69), (66, 69), (94, 112), (122, 69),
     (145, 69), (145, 187), (122, 187), (122, 112), (94, 154),
     (66, 112), (66, 187)),
    ((154, 115), (174, 115), (186, 135), (198, 115), (218, 115),
     (196, 150), (220, 187), (199, 187), (186, 165), (173, 187),
     (152, 187), (176, 150)),
)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    polygons = "\n".join(
        '  <polygon points="' + " ".join(f"{x},{y}" for x, y in points)
        + f'" fill="{FOREGROUND}"/>' for points in LETTERS
    )
    (ASSETS / "launcher-icon.svg").write_text(
        '<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256">\n'
        f'  <rect x="8" y="8" width="240" height="240" rx="56" fill="{BACKGROUND}"/>\n'
        + polygons + '\n</svg>\n', encoding="utf8"
    )
    scale = 8
    image = Image.new("RGBA", (256 * scale, 256 * scale))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((8 * scale, 8 * scale, 248 * scale, 248 * scale),
                           radius=56 * scale, fill=BACKGROUND)
    for points in LETTERS:
        draw.polygon([(x * scale, y * scale) for x, y in points], fill=FOREGROUND)
    frames = []
    for size in SIZES:
        path = OUTPUT / f"launcher-{size}.png"
        image.resize((size, size), Image.Resampling.LANCZOS).save(path)
        frames.append((size, path.read_bytes()))
    # Store the very same PNG pixels in each Windows icon frame.
    offset = 6 + 16 * len(frames)
    directory = []
    for size, data in frames:
        directory.append(struct.pack("<BBBBHHII", size % 256, size % 256,
                                     0, 0, 1, 32, len(data), offset))
        offset += len(data)
    (OUTPUT / "launcher.ico").write_bytes(
        struct.pack("<HHH", 0, 1, len(frames)) + b"".join(directory)
        + b"".join(data for _, data in frames)
    )
    print("Blue Mx icon generated:", OUTPUT)


if __name__ == "__main__":
    main()
