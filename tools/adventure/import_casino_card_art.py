"""Compose Ragab's card table from Kenney cards and mehrasaur chips, all CC0.

The cards are cropped, enlarged with nearest-neighbor scaling and arranged
without painting over or redrawing their face art.
"""
from __future__ import annotations

import argparse
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
CASINO = ROOT / "src/main/resources/assets/gems/textures/gui/casino"
CARDS = CASINO / "cards"
OUT = CASINO / "tables"
CHIP_ARCHIVE = ROOT / "tools/assets/mehrasaur-poker-chips-only.zip"
CHIP_ARCHIVE_SHA256 = "0dd409750384e194921ad22c9c7b03b1f72b0547a64bf5d57c94398ee3a73fff"
CHIP_SOURCES = {
    "black": "9b1a2bec8f06d3f2123e5896d607ae70fe72831ef19092cb539885ec0209e40a",
    "yellow": "a818054da570fa9225164b53d998aaaf9d9106adfa04961a9e1b9efa5d7f8dba",
    "blue": "0234b8bcb39eaf63a147c9b89a0fe4b05a9eda8b7e6e3d1ef31fa2c6e5e19423",
}
SOURCES = {
    "card_hearts_a.png": "e719bdc64b5953a2130e1bc8e380c8a38098315bbfcc7d7ebb01f576852a57b5",
    "card_clubs_k.png": "5b594fe1c83efd9ee0e2bc1f022506c495f332d147b0f10afa548dd7f979f44d",
    "card_diamonds_q.png": "4cb914bb0b67ed8913826918af3eecc3f84cebad295546010e3010402bff0773",
}


def png_bytes(image: Image.Image) -> bytes:
    output = BytesIO()
    image.save(output, format="PNG", optimize=True)
    return output.getvalue()


def source_cards() -> dict[str, Image.Image]:
    result = {}
    for name, digest in SOURCES.items():
        data = (CARDS / name).read_bytes()
        assert sha256(data).hexdigest() == digest, f"Kenney source card changed: {name}"
        card = Image.open(BytesIO(data)).convert("RGBA")
        assert card.size == (32, 32)
        result[name] = card.crop(card.getbbox())
    return result


def source_chips() -> dict[str, Image.Image]:
    assert sha256(CHIP_ARCHIVE.read_bytes()).hexdigest() == CHIP_ARCHIVE_SHA256, "chip archive changed"
    result = {}
    with ZipFile(CHIP_ARCHIVE) as archive:
        for color, digest in CHIP_SOURCES.items():
            member = f"poker_chips_only_2d/PNGs/small/chip_{color}_top.png"
            data = archive.read(member)
            assert sha256(data).hexdigest() == digest, f"chip source changed: {member}"
            result[color] = Image.open(BytesIO(data)).convert("RGBA")
    return result


def outputs() -> dict[Path, bytes]:
    cards = source_cards()
    chips = source_chips()
    scene = Image.new("RGBA", (320, 56), "#10252c")
    draw = ImageDraw.Draw(scene)
    draw.rectangle((2, 2, 317, 53), outline="#dfbd79", width=2)
    draw.rectangle((6, 6, 313, 49), outline="#608185")
    for x, y, color in ((80, 28, "black"), (94, 33, "yellow"),
                        (240, 28, "blue"), (226, 33, "black")):
        chip = chips[color].resize((18, 18), Image.Resampling.LANCZOS)
        scene.alpha_composite(chip, (x - 9, y - 9))
    for name, center_x, angle in (("card_clubs_k.png", 140, 13),
                                  ("card_diamonds_q.png", 180, -13),
                                  ("card_hearts_a.png", 160, 0)):
        card = cards[name].resize((30, 44), Image.Resampling.NEAREST)
        card = card.rotate(angle, Image.Resampling.NEAREST, expand=True)
        scene.alpha_composite(card, (center_x - card.width // 2, 28 - card.height // 2))
    mark = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    mark.alpha_composite(cards["card_hearts_a.png"].resize((13, 19), Image.Resampling.NEAREST), (3, 0))
    return {OUT / "cards_scene.png": png_bytes(scene), OUT / "cards_mark.png": png_bytes(mark)}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for path, data in outputs().items():
        if args.check:
            assert path.read_bytes() == data, f"stale card table art: {path}"
        else:
            path.write_bytes(data)
    print(f"{'Verified' if args.check else 'Imported'} 2 source-backed card table textures")


if __name__ == "__main__":
    main()
