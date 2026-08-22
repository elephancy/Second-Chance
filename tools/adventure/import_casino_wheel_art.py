"""Import the original CC0 roulette wheel illustration for Ragab's Wheel table.

The 64px transparent export retains the artist's wheel pixels after a fixed
crop, circular transparency mask and resize from the original SVG rendering.
No earlier Second Chance wheel graphic is redrawn.
"""
from __future__ import annotations

import argparse
from hashlib import sha256
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SVG = ROOT / "tools/assets/looneybits-roulette-casino.svg"
WHEEL = ROOT / "tools/assets/looneybits-roulette-wheel-64.png"
OUT = ROOT / "src/main/resources/assets/gems/textures/gui/casino/tables"
SVG_SHA256 = "3c36dfaa7e3c915f5e1caba4b958096ba96f963393aed9b33c7fbe264bf9b970"
WHEEL_SHA256 = "3f2e04d4786d90ea4534f56d11c06b95ac5c51fa3c43c1d0a5a1129f40ffb8b2"


def png_bytes(image: Image.Image) -> bytes:
    output = BytesIO()
    image.save(output, format="PNG", optimize=True)
    return output.getvalue()


def outputs() -> dict[Path, bytes]:
    assert sha256(SVG.read_bytes()).hexdigest() == SVG_SHA256, "roulette SVG source changed"
    source = WHEEL.read_bytes()
    assert sha256(source).hexdigest() == WHEEL_SHA256, "roulette raster export changed"
    wheel = Image.open(BytesIO(source)).convert("RGBA")
    assert wheel.size == (64, 64)

    scene = Image.new("RGBA", (320, 56), "#10252c")
    draw = ImageDraw.Draw(scene)
    draw.rectangle((2, 2, 317, 53), outline="#dfbd79", width=2)
    draw.rectangle((6, 6, 313, 49), outline="#608185")
    draw.line((34, 16, 92, 16), fill="#d2ad69", width=2)
    draw.line((34, 39, 92, 39), fill="#d2ad69", width=2)
    draw.line((228, 16, 286, 16), fill="#d2ad69", width=2)
    draw.line((228, 39, 286, 39), fill="#d2ad69", width=2)
    for x in (50, 74, 246, 270):
        draw.ellipse((x - 3, 24, x + 3, 30), fill="#d2ad69")
    scene.alpha_composite(wheel.resize((52, 52), Image.Resampling.LANCZOS), (134, 2))
    mark = wheel.resize((20, 20), Image.Resampling.LANCZOS)
    return {OUT / "wheel_scene.png": png_bytes(scene), OUT / "wheel_mark.png": png_bytes(mark)}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for path, data in outputs().items():
        if args.check:
            assert path.read_bytes() == data, f"stale roulette art: {path}"
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    print(f"{'Verified' if args.check else 'Imported'} 2 source-backed roulette textures")


if __name__ == "__main__":
    main()
