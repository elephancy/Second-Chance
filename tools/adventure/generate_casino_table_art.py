"""Draw three original Second Chance Casino GUI table illustrations.

Run with Pillow: python tools/adventure/generate_casino_table_art.py
The output is deterministic and uses only shapes authored here. Hold and Spin
and Wheel and Cards use licensed source sprites imported by the matching importers.
"""

from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/gems/textures/gui/casino/tables"
INK = "#10252c"
FELT = "#183d43"
GOLD = "#dfbd79"
PALE = "#f1e8d2"
COLORS = {
    "rps": "#e69c84",
    "trivia": "#9cb7e8", "coop": "#b9a5df",
}


def line(draw, points, color=GOLD, width=1):
    draw.line(points, fill=color, width=width, joint="curve")


def frame(draw, accent):
    draw.rectangle((0, 0, 319, 55), fill=INK)
    draw.rectangle((2, 2, 317, 53), outline=GOLD)
    draw.rectangle((5, 5, 314, 50), fill=FELT, outline="#608185")
    for x in (14, 305):
        line(draw, ((x - 6, 28), (x, 18), (x + 6, 28), (x, 38), (x - 6, 28)), accent)
    for x in range(30, 296, 16):
        draw.point((x, 8), fill="#608185")
        draw.point((x, 47), fill="#608185")
    line(draw, ((29, 12), (47, 12), (54, 19)), GOLD)
    line(draw, ((291, 12), (273, 12), (266, 19)), GOLD)
    line(draw, ((29, 43), (47, 43), (54, 36)), GOLD)
    line(draw, ((291, 43), (273, 43), (266, 36)), GOLD)


def diamond(draw, cx, cy, r, color):
    draw.polygon(((cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)), fill=color)


def draw_rps(draw):
    # Three carved medallions: stone fist, parchment hand, crossed shears.
    for cx, color in ((94, "#b8a597"), (160, PALE), (226, "#cf9f99")):
        draw.ellipse((cx - 22, 7, cx + 22, 49), fill="#26444b", outline=GOLD, width=2)
        draw.arc((cx - 17, 12, cx + 17, 44), 20, 160, fill=color, width=2)
    draw.polygon(((81, 32), (83, 23), (88, 21), (90, 17), (97, 17), (102, 23),
                  (105, 32), (101, 37), (85, 37)), fill="#c4a38a", outline="#f2c5a7")
    for x in (87, 93, 99):
        line(draw, ((x, 19), (x, 25)), "#805e56")
    draw.rounded_rectangle((151, 16, 169, 37), radius=3, fill="#eadbc5", outline="#9f8273")
    for x in (153, 158, 163, 168):
        line(draw, ((x, 16), (x, 29)), "#9f8273")
    line(draw, ((214, 18), (235, 39)), "#e9c4b0", 3)
    line(draw, ((235, 18), (214, 39)), "#e9c4b0", 3)
    for x, y in ((214, 18), (235, 18)):
        draw.ellipse((x - 4, y - 4, x + 4, y + 4), outline=PALE, width=2)
    draw.ellipse((221, 25, 229, 33), fill=GOLD)


def draw_trivia(draw):
    # Open book and four answer seals.
    draw.polygon(((101, 15), (132, 10), (160, 17), (188, 10), (219, 15),
                  (219, 42), (188, 38), (160, 44), (132, 38), (101, 42)), fill="#d6c8a9", outline=GOLD)
    line(draw, ((160, 17), (160, 43)), "#705f58", 2)
    for y, end in ((22, 145), (27, 140), (32, 147)):
        line(draw, ((110, y), (end, y-2)), "#8a8173")
    draw.arc((174, 18, 195, 34), 185, 340, fill="#455d86", width=3)
    draw.ellipse((182, 35, 185, 38), fill="#455d86")
    for x, color in ((75, "#c46b72"), (87, "#75ae94"), (233, "#8c9bca"), (245, "#d9b978")):
        diamond(draw, x, 28, 5, color)


def draw_coop(draw):
    for cx, color in ((103, "#b8777d"), (217, "#82bda0")):
        draw.rounded_rectangle((cx-24, 13, cx+24, 43), radius=4, fill="#233d4b", outline=GOLD, width=2)
        diamond(draw, cx, 28, 11, color)
        diamond(draw, cx, 28, 5, PALE)
    line(draw, ((127, 28), (146, 28), (153, 21), (167, 35), (174, 28), (193, 28)), "#b9a5df", 3)
    for x, color in ((153, "#cf6b77"), (160, "#74bf9e"), (167, "#77b4e4")):
        draw.ellipse((x-3, 9, x+3, 15), fill=color)


SCENES = {"rps": draw_rps,
          "trivia": draw_trivia, "coop": draw_coop}


def draw_mark(game):
    mark = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    d = ImageDraw.Draw(mark)
    accent = COLORS[game]
    d.ellipse((1, 1, 18, 18), fill="#18343c", outline=GOLD, width=2)
    if game == "rps":
        d.polygon(((5, 13), (6, 7), (9, 6), (10, 4), (14, 6), (15, 13), (12, 15), (7, 15)), fill=accent)
    elif game == "trivia":
        d.arc((6, 4, 14, 12), 180, 355, fill=accent, width=2)
        d.line((13, 9, 10, 12), fill=accent, width=2)
        d.ellipse((9, 15, 11, 17), fill=accent)
    else:
        diamond(d, 6, 10, 4, "#c77680")
        diamond(d, 14, 10, 4, "#77b9e4")
        d.line((9, 10, 11, 10), fill=PALE, width=2)
    return mark


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for game, scene in SCENES.items():
        image = Image.new("RGBA", (320, 56))
        draw = ImageDraw.Draw(image)
        frame(draw, COLORS[game])
        scene(draw)
        image.save(OUT / f"{game}_scene.png", optimize=True)
        draw_mark(game).save(OUT / f"{game}_mark.png", optimize=True)


if __name__ == "__main__":
    main()
