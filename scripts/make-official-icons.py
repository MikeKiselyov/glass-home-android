#!/usr/bin/env python3
"""
Builds official device icon tiles for personal use on your own TV.

The brand logos are trademarks of their owners, so they never go into the
repository or the APK: sources and tiles live in .tv/official-icons/ (ignored
by git) and scripts/push-icons.sh copies the tiles straight onto the TV.

Each tile is the logo in white on the brand's colour, 312x312 PNG, rendered
with macOS Quick Look (qlmanage).

The logos are downloaded from Wikimedia Commons on first run.

Usage: python3 scripts/make-official-icons.py
"""
import re
import subprocess
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / ".tv" / "official-icons" / "src"
TILES = ROOT / ".tv" / "official-icons" / "tiles"
SIZE = 312

# name: (source file in SOURCES, gradient start, gradient end, logo width share)
DEVICES = {
    "playstation": ("PlayStation_logo.svg", "#1f6fd6", "#0a2a7a", 0.62),
    "xbox": ("Xbox_one_logo.svg", "#2fb344", "#0b4f0b", 0.56),
    "nintendo-switch": ("Nintendo_Switch_Logo.svg", "#ff4554", "#b3121f", 0.56),
    "apple-tv": ("Apple_TV_logo.svg", "#4a4d55", "#0e0f12", 0.66),
}


def clean(svg_text):
    """Drops parts Quick Look can't parse: XML header, metadata, editor attributes."""
    svg_text = re.sub(r"<\?xml.*?\?>", "", svg_text, flags=re.S)
    svg_text = re.sub(r"<!DOCTYPE.*?>", "", svg_text, flags=re.S)
    svg_text = re.sub(r"<!--.*?-->", "", svg_text, flags=re.S)
    svg_text = re.sub(r"<metadata.*?</metadata>", "", svg_text, flags=re.S)
    svg_text = re.sub(r"<sodipodi:[^>]*/>", "", svg_text, flags=re.S)
    svg_text = re.sub(r"\s(?:sodipodi|inkscape):[\w-]+=\"[^\"]*\"", "", svg_text)
    return svg_text


def view_box(svg_tag):
    match = re.search(r'viewBox="([^"]+)"', svg_tag)
    if match:
        numbers = match.group(1).replace(",", " ").split()
        return [float(number) for number in numbers]
    width = float(re.search(r'width="([\d.]+)', svg_tag).group(1))
    height = float(re.search(r'height="([\d.]+)', svg_tag).group(1))
    return [0.0, 0.0, width, height]


def tile_svg(source_text, start, end, share):
    text = clean(source_text)
    svg_tag = re.search(r"<svg\b[^>]*>", text, re.S).group(0)
    left, top, width, height = view_box(svg_tag)
    inner = text[text.index(svg_tag) + len(svg_tag):text.rindex("</svg>")]

    logo_width = SIZE * share
    logo_height = logo_width * height / width
    if logo_height > SIZE * 0.6:
        logo_height = SIZE * 0.6
        logo_width = logo_height * width / height
    x = (SIZE - logo_width) / 2
    y = (SIZE - logo_height) / 2

    return f"""<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}">
<defs>
<linearGradient id="ground" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{start}"/><stop offset="1" stop-color="{end}"/></linearGradient>
<radialGradient id="sheen" cx="30%" cy="20%" r="70%"><stop offset="0" stop-color="#fff" stop-opacity=".22"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>
</defs>
<rect width="{SIZE}" height="{SIZE}" fill="url(#ground)"/>
<rect width="{SIZE}" height="{SIZE}" fill="url(#sheen)"/>
<svg x="{x:.1f}" y="{y:.1f}" width="{logo_width:.1f}" height="{logo_height:.1f}" viewBox="{left} {top} {width} {height}" class="logo">
<style>.logo path, .logo polygon, .logo rect, .logo circle, .logo ellipse, .logo g {{ fill: #ffffff !important; stroke: none !important; }}</style>
{inner}
</svg>
</svg>"""


def download(source_name):
    """Fetches a logo from Wikimedia Commons into SOURCES unless it is already there."""
    target = SOURCES / source_name
    if target.exists():
        return target
    SOURCES.mkdir(parents=True, exist_ok=True)
    url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + source_name
    request = urllib.request.Request(url, headers={"User-Agent": "glass-home-android icon builder"})
    with urllib.request.urlopen(request, timeout=30) as response:
        target.write_bytes(response.read())
    print(f"downloaded {source_name}")
    return target


def main():
    TILES.mkdir(parents=True, exist_ok=True)
    for name, (source_name, start, end, share) in DEVICES.items():
        source_text = download(source_name).read_text()
        svg_path = TILES / f"official-{name}.svg"
        svg_path.write_text(tile_svg(source_text, start, end, share))
        subprocess.run(["qlmanage", "-t", "-s", str(SIZE), "-o", str(TILES), str(svg_path)],
                       check=True, capture_output=True)
        rendered = TILES / f"official-{name}.svg.png"
        rendered.rename(TILES / f"official-{name}.png")
        print(f"official-{name}.png")


if __name__ == "__main__":
    main()
