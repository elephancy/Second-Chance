"""Draw original pixel art for the Unwritten boss and Stage Mark."""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/textures"
INK = "#232330"
BURGUNDY = "#673948"
PLUM = "#8E627C"
PORCELAIN = "#DDD1BC"
GOLD = "#DBAD69"
CYAN = "#81C9DC"
RED = "#D86F74"


def entity() -> Image.Image:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    pen = ImageDraw.Draw(image)
    # Minecraft's 64x64 biped UV islands.
    for box in ((8, 0, 15, 7), (16, 0, 23, 7), (0, 8, 7, 15),
                (8, 8, 15, 15), (16, 8, 23, 15), (24, 8, 31, 15)):
        pen.rectangle(box, fill=INK)
    pen.rectangle((9, 9, 14, 14), fill=PORCELAIN)
    pen.polygon([(9, 9), (14, 9), (12, 12), (9, 11)], fill=INK)
    pen.point((10, 12), fill=CYAN)
    pen.point((13, 12), fill=RED)
    pen.line((9, 14, 14, 14), fill=BURGUNDY)

    for box in ((20, 16, 27, 19), (28, 16, 35, 19), (16, 20, 19, 31),
                (20, 20, 27, 31), (28, 20, 31, 31), (32, 20, 39, 31)):
        pen.rectangle(box, fill=BURGUNDY)
    pen.polygon([(20, 20), (24, 20), (23, 31), (20, 31)], fill=INK)
    pen.line((24, 20, 24, 30), fill=GOLD)
    pen.line((27, 20, 20, 29), fill=PLUM, width=2)
    pen.rectangle((21, 23, 26, 26), outline=GOLD)
    pen.point((23, 24), fill=CYAN)
    pen.point((25, 25), fill=RED)

    for x, y in ((40, 20), (32, 52), (0, 20), (16, 52)):
        pen.rectangle((x, y, x + 15, y + 11), fill=INK)
        pen.rectangle((x, y, x + 15, y + 3), fill=BURGUNDY)
        pen.line((x + 2, y + 5, x + 12, y + 9), fill=GOLD)
        pen.point((x + 7, y + 7), fill=CYAN)
    for x, y in ((44, 16), (36, 48), (4, 16), (20, 48)):
        pen.rectangle((x, y, x + 7, y + 3), fill=BURGUNDY)
    return image


def icon() -> Image.Image:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pen = ImageDraw.Draw(image)
    pen.polygon([(2, 2), (13, 2), (13, 13), (2, 13)], fill=INK, outline=GOLD)
    pen.polygon([(4, 4), (11, 4), (11, 11), (4, 11)], fill=BURGUNDY)
    pen.line((5, 5, 10, 10), fill=PORCELAIN, width=2)
    pen.line((10, 5, 5, 10), fill=CYAN, width=2)
    pen.point((8, 8), fill=RED)
    return image


def draw() -> None:
    target = ROOT / "entity/stray_cadenza.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    entity().save(target)
    mark = ROOT / "item/stage_mark.png"
    mark.parent.mkdir(parents=True, exist_ok=True)
    icon().save(mark)
    print(target, mark)


if __name__ == "__main__":
    draw()
