"""Draw original archive-solver skin and route-lens icon."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/textures"
INK = "#202935"
COAT = "#314555"
TEAL = "#62C9D5"
BLUE = "#3284A3"
PAPER = "#E7DDC2"
GOLD = "#D7B45E"
RED = "#C86D72"


def draw() -> None:
    skin = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(skin)
    for box in ((8, 0, 15, 7), (16, 0, 23, 7), (0, 8, 7, 15),
                (8, 8, 15, 15), (16, 8, 23, 15), (24, 8, 31, 15)):
        d.rectangle(box, fill=INK)
    d.rectangle((9, 9, 14, 14), fill=PAPER, outline=GOLD)
    d.line((11, 10, 11, 13), fill=BLUE)
    d.line((10, 12, 13, 12), fill=BLUE)
    d.polygon([(11, 9), (10, 11), (12, 11)], fill=RED)
    d.point((13, 13), fill=TEAL)

    for box in ((20, 16, 27, 19), (28, 16, 35, 19), (16, 20, 19, 31),
                (20, 20, 27, 31), (28, 20, 31, 31), (32, 20, 39, 31)):
        d.rectangle(box, fill=COAT)
    d.line((20, 20, 27, 20), fill=TEAL)
    d.line((23, 20, 23, 30), fill=PAPER)
    d.rectangle((24, 23, 26, 28), fill=GOLD)
    d.line((20, 30, 27, 30), fill=BLUE)
    d.line((17, 22, 19, 28), fill=INK)
    d.line((29, 22, 31, 28), fill=INK)

    for x, y in ((40, 20), (32, 52), (0, 20), (16, 52)):
        d.rectangle((x, y, x + 15, y + 11), fill=COAT)
        d.rectangle((x, y + 9, x + 15, y + 11), fill=INK)
        d.line((x + 1, y + 3, x + 14, y + 3), fill=TEAL)
    for x, y in ((44, 16), (36, 48), (4, 16), (20, 48)):
        d.rectangle((x, y, x + 7, y + 3), fill=INK)
    d.point((4, 26), fill=GOLD)
    d.point((45, 26), fill=GOLD)

    boss = ROOT / "entity/second_move_solver.png"
    boss.parent.mkdir(parents=True, exist_ok=True)
    skin.save(boss)

    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = ImageDraw.Draw(icon)
    p.ellipse((1, 1, 14, 14), fill=INK, outline=GOLD)
    p.ellipse((3, 3, 12, 12), fill=PAPER, outline=TEAL)
    p.line((7, 3, 7, 12), fill=BLUE)
    p.line((3, 7, 12, 7), fill=BLUE)
    p.polygon([(7, 3), (5, 8), (8, 7)], fill=RED)
    p.polygon([(8, 8), (10, 5), (8, 12)], fill=TEAL)
    lens = ROOT / "item/route_lens.png"
    lens.parent.mkdir(parents=True, exist_ok=True)
    icon.save(lens)
    print(f"Drawn {boss} and {lens}")


if __name__ == "__main__":
    draw()
