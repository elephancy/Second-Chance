"""Import original Karsiori CC0 gem frames onto transparent 32px canvases.

The selected source pixels are copied without recoloring, drawing, or resampling.
Pillow is needed only when refreshing these checked-in PNGs, not to build the mod.
"""
from __future__ import annotations

import argparse
import hashlib
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ARCHIVE = ROOT / "tools/assets/karsiori-pixel-art-gems-animated.zip"
ARCHIVE_SHA256 = "cb72c6994fb45128c400db2b5922f0a0827e6ae6f2737bcbbc132a48ddf05a41"
TARGET = ROOT / "gems/src/main/resources/assets/gems/textures/item/gems"
SELECTIONS = {
    "air": (1, "TURQUOISE"),
    "astra": (3, "LILAC"),
    "beacon": (5, "GOLD"),
    "chaos": (9, "LILAC"),
    "duelist": (4, "DARK BLUE"),
    "fire": (7, "RED"),
    "flux": (2, "PURPLE"),
    "hunter": (1, "LIGHT GREEN"),
    "life": (6, "LIGHT GREEN"),
    "pillager": (8, "RED"),
    "prism": (3, "TURQUOISE"),
    "puff": (5, "LILAC"),
    "reaper": (4, "PURPLE"),
    "sentinel": (8, "BLUE"),
    "space": (9, "DARK BLUE"),
    "speed": (1, "GOLD"),
    "spy": (7, "DARK BLUE"),
    "strength": (4, "RED"),
    "summoner": (6, "TURQUOISE"),
    "terror": (8, "PURPLE"),
    "trickster": (2, "GOLD"),
    "void": (6, "PURPLE"),
    "wealth": (3, "GOLD"),
}


def source_member(number: int, color: str) -> str:
    return (f"Pixel Art Gem Pack - Animated/GEM {number}/{color}/"
            f"GEM {number} - {color} - 0000.png")


def render(source_png: bytes) -> tuple[bytes, Image.Image]:
    with Image.open(BytesIO(source_png)) as source:
        if source.mode != "RGBA" or source.width > 32 or source.height > 32:
            raise ValueError("Expected an original RGBA gem frame no larger than 32px")
        original = source.copy()
    canvas = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    offset = ((32 - original.width) // 2, (32 - original.height) // 2)
    canvas.paste(original, offset)
    if canvas.crop((*offset, offset[0] + original.width,
                    offset[1] + original.height)).tobytes() != original.tobytes():
        raise ValueError("Original pixels changed while centering the gem")
    stream = BytesIO()
    canvas.save(stream, format="PNG")
    return stream.getvalue(), canvas


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if hashlib.sha256(ARCHIVE.read_bytes()).hexdigest() != ARCHIVE_SHA256:
        raise SystemExit("Karsiori source archive hash changed")
    with ZipFile(ARCHIVE) as archive:
        for gem, (number, color) in SELECTIONS.items():
            member = source_member(number, color)
            expected, pixels = render(archive.read(member))
            path = TARGET / f"{gem}_gem.png"
            if args.check:
                if not path.is_file():
                    raise SystemExit(f"Missing imported gem: {path}")
                with Image.open(path) as actual:
                    if actual.mode != "RGBA" or actual.size != (32, 32) or actual.tobytes() != pixels.tobytes():
                        raise SystemExit(f"Imported gem pixels differ: {path}")
            else:
                path.write_bytes(expected)
            print(f"{gem}: {member} -> {path.name}; SHA-256 {hashlib.sha256(path.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
