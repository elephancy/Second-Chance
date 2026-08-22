"""Deterministically draw nine first-party marker faces over the bundled CC0 stone.

Run from the repository root. Source grain: tools/assets/cc0_block_set/marble_bricks.png.
"""

from __future__ import annotations

import json
from pathlib import Path
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/gems"
SOURCE = ROOT / "tools/assets/cc0_block_set/marble_bricks.png"
MARKERS = {
    "trail_origin": ("#62b6b4", "origin"),
    "trail_turn": ("#87cfe1", "turn"),
    "trail_finish": ("#d2e9e3", "finish"),
    "cipher_plaque": ("#d5aa69", "plaque"),
    "cipher_sun": ("#f3c55c", "sun"),
    "cipher_star": ("#e9e4af", "star"),
    "cipher_moon": ("#b9c7eb", "moon"),
    "cipher_seal": ("#b791dd", "seal"),
    "vista_dial": ("#a7e4ea", "dial"),
}


def draw_marker(name: str, color: str, motif: str) -> Image.Image:
    source = Image.open(SOURCE).convert("RGBA")
    if source.size != (16, 16):
        raise ValueError("Expected a 16x16 CC0 source tile")
    out = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r, g, b, alpha = source.getpixel((x, y))
            shade = (3 * r + 6 * g + b) // 10
            base = 30 + shade // 4
            out.putpixel((x, y), (base, base + 6, base + 10, alpha))
    p = ImageDraw.Draw(out)
    p.rectangle((0, 0, 15, 15), outline="#c9b892")
    p.rectangle((2, 2, 13, 13), fill="#202a32", outline="#7d7565")
    if motif == "origin":
        p.ellipse((4, 4, 11, 11), outline=color, width=2)
        p.polygon(((8, 2), (5, 7), (10, 7)), fill=color)
    elif motif == "turn":
        p.line((4, 11, 4, 6, 10, 6), fill=color, width=2)
        p.polygon(((10, 3), (13, 6), (10, 9)), fill=color)
    elif motif == "finish":
        p.line((4, 12, 4, 3), fill=color, width=2)
        p.polygon(((5, 3), (12, 4), (10, 8), (5, 8)), fill=color)
    elif motif == "plaque":
        p.line((4, 5, 11, 5), fill=color, width=2)
        p.line((4, 8, 11, 8), fill=color)
        p.line((4, 11, 9, 11), fill=color)
    elif motif == "sun":
        p.ellipse((5, 5, 10, 10), fill=color)
        for q in ((8, 3, 8, 4), (8, 11, 8, 12), (3, 8, 4, 8), (11, 8, 12, 8),
                  (4, 4, 5, 5), (11, 11, 12, 12), (11, 4, 12, 3), (3, 12, 4, 11)):
            p.line(q, fill=color)
    elif motif == "star":
        p.polygon(((8, 2), (9, 6), (13, 6), (10, 9), (11, 13), (8, 11),
                   (5, 13), (6, 9), (3, 6), (7, 6)), fill=color)
    elif motif == "moon":
        p.ellipse((4, 3, 11, 12), fill=color)
        p.ellipse((7, 2, 13, 10), fill="#202a32")
    elif motif == "seal":
        p.ellipse((3, 3, 12, 12), outline=color, width=2)
        p.line((5, 8, 7, 10, 11, 5), fill=color, width=2)
    else:
        p.ellipse((3, 3, 12, 12), outline=color)
        p.line((8, 3, 8, 12), fill=color)
        p.line((3, 8, 12, 8), fill=color)
        p.polygon(((8, 2), (5, 7), (11, 7)), fill=color)
    return out


def main() -> None:
    for name, (color, motif) in MARKERS.items():
        texture = ASSETS / "textures/block" / f"{name}.png"
        texture.parent.mkdir(parents=True, exist_ok=True)
        draw_marker(name, color, motif).save(texture)
        model = ASSETS / "models/block" / f"{name}.json"
        model.parent.mkdir(parents=True, exist_ok=True)
        model.write_text(json.dumps({"parent": "minecraft:block/cube_all",
                                     "textures": {"all": f"gems:block/{name}"}},
                                    separators=(",", ":")) + "\n", encoding="utf-8")
        state = ASSETS / "blockstates" / f"{name}.json"
        state.parent.mkdir(parents=True, exist_ok=True)
        state.write_text(json.dumps({"variants": {"": {"model": f"gems:block/{name}"}}},
                                    separators=(",", ":")) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
