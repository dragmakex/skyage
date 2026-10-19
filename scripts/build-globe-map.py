"""Build the bundled globe mask. Requires Pillow; no runtime network or image decoder.
Source: Natural Earth 1:110m countries (public domain), https://www.naturalearthdata.com/about/terms-of-use/
"""
import json
import urllib.request
from pathlib import Path
from PIL import Image, ImageDraw

url = 'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson'
world = json.load(urllib.request.urlopen(url))
width, height = 1024, 512
image = Image.new('L', (width, height))
draw = ImageDraw.Draw(image)
for feature in world['features']:
    geometry = feature['geometry']
    polygons = geometry['coordinates'] if geometry['type'] == 'MultiPolygon' else [geometry['coordinates']]
    for polygon in polygons:
        for i, ring in enumerate(polygon):
            points = [((lon + 180) / 360 * (width - 1), (lat + 90) / 180 * (height - 1)) for lon, lat, *_ in ring]
            draw.polygon(points, fill=1 if i == 0 else 0)
            draw.line(points, fill=2, width=1)
runs = []
for value in image.getdata():
    if runs and runs[-2] == value:
        runs[-1] += 1
    else:
        runs.extend([value, 1])
path = Path(__file__).resolve().parents[1] / 'src/map/earth-mask.json'
path.write_text(json.dumps({'width': width, 'height': height, 'runs': runs}, separators=(',', ':')) + '\n')
