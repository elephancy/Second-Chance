"""Draw original Redline guard skin and rescue-line icon."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/textures"
CHARCOAL = "#292E36"
COAT = "#3C4148"
RUST = "#A6484C"
RED = "#D66462"
BRASS = "#D8AC63"
CREAM = "#E7D9BB"
SHADOW = "#735357"


def draw() -> None:
    skin = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(skin)
    # Minecraft biped atlas: face, torso, then four sleeves and legs.
    for box in ((8, 0, 15, 7), (16, 0, 23, 7), (0, 8, 7, 15),
                (8, 8, 15, 15), (16, 8, 23, 15), (24, 8, 31, 15)):
        d.rectangle(box, fill=CHARCOAL)
    d.rectangle((9, 9, 14, 14), fill=CREAM, outline=SHADOW)
    d.point((10, 11), fill=RUST)
    d.point((13, 11), fill=RUST)
    d.line((9, 14, 14, 14), fill=RED)

    for box in ((20, 16, 27, 19), (28, 16, 35, 19), (16, 20, 19, 31),
                (20, 20, 27, 31), (28, 20, 31, 31), (32, 20, 39, 31)):
        d.rectangle(box, fill=COAT)
    d.line((20, 21, 27, 29), fill=RED, width=2)
    d.line((20, 22, 27, 30), fill=RUST)
    d.rectangle((23, 25, 25, 27), fill=BRASS)
    d.line((20, 30, 27, 30), fill=BRASS)
    d.line((28, 21, 35, 29), fill=RED, width=2)
    d.line((16, 22, 19, 22), fill=RUST)
    d.line((32, 22, 39, 22), fill=RUST)

    for x, y in ((40, 20), (32, 52), (0, 20), (16, 52)):
        d.rectangle((x, y, x + 15, y + 11), fill=COAT)
        d.rectangle((x, y + 9, x + 15, y + 11), fill=CHARCOAL)
        d.line((x + 1, y + 5, x + 14, y + 5), fill=RED)
        d.point((x + 3, y + 5), fill=BRASS)
    for x, y in ((44, 16), (36, 48), (4, 16), (20, 48)):
        d.rectangle((x, y, x + 7, y + 3), fill=CHARCOAL)

    boss = ROOT / "entity/red_line_between.png"
    boss.parent.mkdir(parents=True, exist_ok=True)
    skin.save(boss)

    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = ImageDraw.Draw(icon)
    p.ellipse((2, 2, 13, 13), fill=CHARCOAL, outline=BRASS)
    p.ellipse((5, 5, 10, 10), fill=SHADOW, outline=CREAM)
    p.line((1, 13, 6, 8, 10, 8, 14, 2), fill=RED, width=2)
    p.point((1, 13), fill=BRASS)
    p.point((14, 2), fill=BRASS)
    item = ROOT / "item/rescue_line.png"
    item.parent.mkdir(parents=True, exist_ok=True)
    icon.save(item)
    print(f"Drawn {boss} and {item}")


if __name__ == "__main__":
    draw()
