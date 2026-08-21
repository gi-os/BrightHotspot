#!/usr/bin/env python3
"""
Regenerate the BrightHotspot launcher icon: a signal dot with three rising arcs -- the
universal hotspot mark -- in white line art on black, matching the Light Phone III tool
family. Geometry is defined once on a 108-unit canvas and rasterised per density.

    python3 scripts/generate_icon.py    # needs Pillow
"""
from __future__ import annotations
import math, os
from PIL import Image, ImageDraw

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
CANVAS = 108
CX, CY = 54.0, 66.0
DOT_R = 6.0
STROKE = 5.0
ARCS = [15.0, 27.0, 39.0]          # radii of the three arcs, centered on the dot
SS = 8                              # supersample
DENS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def render(px: int, round_icon: bool) -> Image.Image:
    s = px * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    k = s / CANVAS
    # background
    if round_icon:
        d.ellipse([0, 0, s - 1, s - 1], fill=(0, 0, 0, 255))
    else:
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.16), fill=(0, 0, 0, 255))
    # dot
    cx, cy, r = CX * k, CY * k, DOT_R * k
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, 255))
    # arcs: quarter-ish upward sweep from 200deg to 340deg
    for rad in ARCS:
        rr = rad * k
        bbox = [cx - rr, cy - rr, cx + rr, cy + rr]
        d.arc(bbox, start=200, end=340, fill=(255, 255, 255, 255), width=int(STROKE * k))
    return img.resize((px, px), Image.LANCZOS)


def main() -> None:
    for name, px in DENS.items():
        folder = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(folder, exist_ok=True)
        render(px, False).save(os.path.join(folder, "ic_launcher.png"))
        render(px, True).save(os.path.join(folder, "ic_launcher_round.png"))
    print("icons written")


if __name__ == "__main__":
    main()
