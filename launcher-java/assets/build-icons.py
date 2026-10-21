"""Build the OML monogram as SVG, antialiased PNGs and a multi-size ICO.

Requires Pillow only; the launcher itself does not depend on Python.
"""
from pathlib import Path
import struct

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parent
OUTPUT = ASSETS.parent / "src/main/resources/icons"
SIZES = (16, 24, 32, 48, 64, 128, 256)
BACKGROUND_TOP = (24, 129, 255)
BACKGROUND_BOTTOM = (53, 57, 203)
FOREGROUND = "#ffffff"
# Three geometric capital letters remain readable without relying on a font.
LETTERS = (
    ((96, 166), (96, 82), (113, 82), (135, 119), (157, 82),
     (174, 82), (174, 166), (157, 166), (157, 115), (135, 150),
     (113, 115), (113, 166)),
    ((185, 82), (202, 82), (202, 149), (229, 149), (229, 166), (185, 166)),
)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    polygons = "\n".join(
        '  <polygon points="' + " ".join(f"{x},{y}" for x, y in points)
        + f'" fill="{FOREGROUND}"/>' for points in LETTERS
    )
    (ASSETS / "launcher-icon.svg").write_text(
        '<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256">\n'
        '  <title>OML monogram for Open-MXaddon-Launcher</title>\n'
        '  <defs><linearGradient id="blue" x1="0" y1="0" x2="0" y2="1">'
        '<stop stop-color="#1881ff"/><stop offset="1" stop-color="#3539cb"/></linearGradient></defs>\n'
        '  <rect x="8" y="8" width="240" height="240" rx="56" fill="url(#blue)"/>\n'
        '  <rect x="36.5" y="89.5" width="40" height="69" rx="12" fill="none" stroke="white" stroke-width="15"/>\n'
        '  <rect x="52" y="190" width="152" height="10" rx="5" fill="#74e4ff"/>\n'
        + polygons + '\n</svg>\n', encoding="utf8"
    )
    scale = 8
    image = Image.new("RGBA", (256 * scale, 256 * scale))
    mask = Image.new("L", image.size)
    ImageDraw.Draw(mask).rounded_rectangle((8 * scale, 8 * scale, 248 * scale, 248 * scale), radius=56 * scale, fill=255)
    draw = ImageDraw.Draw(image)
    for y in range(256 * scale):
        ratio = max(0, min(1, (y / scale - 8) / 240))
        color = tuple(round(a + (b - a) * ratio) for a, b in zip(BACKGROUND_TOP, BACKGROUND_BOTTOM))
        draw.line((0, y, 256 * scale, y), fill=color)
    image.putalpha(mask)
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((29 * scale, 82 * scale, 84 * scale, 166 * scale), radius=19 * scale, outline=FOREGROUND, width=15 * scale)
    for points in LETTERS:
        draw.polygon([(x * scale, y * scale) for x, y in points], fill=FOREGROUND)
    draw.rounded_rectangle((52 * scale, 190 * scale, 204 * scale, 200 * scale), radius=5 * scale, fill="#74e4ff")
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
    print("OML monogram icon generated:", OUTPUT)


if __name__ == "__main__":
    main()
