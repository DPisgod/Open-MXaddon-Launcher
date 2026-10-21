"""Derive every application icon from the supplied neon badge. Requires Pillow."""
from pathlib import Path
import base64
import struct
from PIL import Image

ASSETS = Path(__file__).resolve().parent
OUTPUT = ASSETS.parent / 'src/main/resources/icons'
SIZES = (16, 24, 32, 48, 64, 128, 256)

def main():
    source = ASSETS / 'launcher-icon-source.png'
    image = Image.open(source).convert('RGBA')
    edge = max(image.size)
    square = Image.new('RGBA', (edge, edge))
    square.alpha_composite(image, ((edge-image.width)//2, (edge-image.height)//2))
    OUTPUT.mkdir(parents=True, exist_ok=True)
    frames = []
    for size in SIZES:
        path = OUTPUT / f'launcher-{size}.png'
        square.resize((size, size), Image.Resampling.LANCZOS).save(path)
        frames.append((size, path.read_bytes()))
    offset = 6 + 16 * len(frames)
    directory = []
    for size, data in frames:
        directory.append(struct.pack('<BBBBHHII', size % 256, size % 256, 0, 0, 1, 32, len(data), offset))
        offset += len(data)
    (OUTPUT / 'launcher.ico').write_bytes(struct.pack('<HHH', 0, 1, len(frames)) + b''.join(directory) + b''.join(data for _, data in frames))
    # Preserve the existing installer input contract without substituting vector artwork.
    encoded = base64.b64encode(source.read_bytes()).decode('ascii')
    (ASSETS / 'launcher-icon.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{edge}" height="{edge}" viewBox="0 0 {edge} {edge}"><title>Open-MXaddon-Launcher neon badge</title><image width="{edge}" height="{edge}" preserveAspectRatio="xMidYMid meet" href="data:image/png;base64,{encoded}"/></svg>\n', encoding='utf8')
    print('Application badge icons generated:', OUTPUT)

if __name__ == '__main__':
    main()
