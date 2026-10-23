"""Build theme-tinted, offline globe previews from the same Natural Earth mask as GL.
Requires Pillow. No network access; regenerate after changing earth-mask.json.
"""
import json
import math
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
mask = json.loads((root / 'src/map/earth-mask.json').read_text())
pixels = bytearray()
for value, count in zip(mask['runs'][::2], mask['runs'][1::2]):
    pixels.extend(bytes([value]) * count)
assert len(pixels) == mask['width'] * mask['height']

size = 1024
land = Image.new('L', (size, size))
borders = Image.new('L', (size, size))
land_pixels, border_pixels = land.load(), borders.load()
phi = math.radians(20)
for row in range(size):
    y = 1 - 2 * (row + .5) / size
    for col in range(size):
        x = 2 * (col + .5) / size - 1
        if x*x + y*y > 1:
            continue
        z = math.sqrt(1 - x*x - y*y)
        lat = math.asin(y * math.cos(phi) + z * math.sin(phi))
        lon = math.atan2(x, z * math.cos(phi) - y * math.sin(phi))
        u = round((lon / (2 * math.pi) + .5) * (mask['width'] - 1))
        v = round((lat / math.pi + .5) * (mask['height'] - 1))
        value = pixels[v * mask['width'] + u]
        land_pixels[col, row] = 255 if value else 0
        border_pixels[col, row] = 255 if value == 2 else 0

for name, alpha in [('land', land), ('borders', borders)]:
    assert alpha.histogram()[255] > 1000, f'Empty {name} preview'
    image = Image.new('RGBA', (size, size), 'white')
    image.putalpha(alpha)
    image.resize((512, 512), Image.Resampling.LANCZOS).save(root / f'assets/globe-{name}.png')
