"""Draw the original Lastlight clock boss and return-ticket pixel art."""

from pathlib import Path

from PIL import Image, ImageDraw


OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/textures/entity/last_arrival.png"
TICKET = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/textures/item/return_ticket.png"
COAT = "#1B2932"
COAT_EDGE = "#37505B"
BRASS = "#D6AB59"
PALE = "#F0DFB0"
SHADOW = "#80663D"
RED = "#C85E4A"


def draw() -> None:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)

    # A face-sized clock gives the boss an identifiable silhouette in a crowd.
    for box in ((8, 0, 15, 7), (16, 0, 23, 7), (0, 8, 7, 15),
                (8, 8, 15, 15), (16, 8, 23, 15), (24, 8, 31, 15)):
        d.rectangle(box, fill=BRASS)
    d.rectangle((9, 9, 14, 14), fill=PALE)
    d.point((11, 10), fill=COAT)
    d.point((13, 11), fill=COAT)
    d.point((11, 13), fill=COAT)
    d.point((9, 11), fill=COAT)
    d.line((11, 11, 11, 9), fill=RED)
    d.line((11, 11, 13, 12), fill=COAT)
    d.rectangle((8, 8, 15, 15), outline=SHADOW)

    # Tall uniform coat, central clock chain and a single late red ticket.
    for box in ((20, 16, 27, 19), (28, 16, 35, 19), (16, 20, 19, 31),
                (20, 20, 27, 31), (28, 20, 31, 31), (32, 20, 39, 31)):
        d.rectangle(box, fill=COAT)
    d.rectangle((20, 20, 27, 21), fill=COAT_EDGE)
    d.line((23, 20, 23, 30), fill=BRASS)
    d.point((23, 23), fill=PALE)
    d.point((23, 26), fill=PALE)
    d.rectangle((25, 22, 27, 26), fill=RED)
    d.line((16, 20, 19, 31), fill=COAT_EDGE)
    d.line((28, 20, 31, 31), fill=SHADOW)
    d.line((20, 30, 27, 30), fill=BRASS)

    for top_x, side_x, side_y in ((44, 40, 20), (36, 32, 52)):
        d.rectangle((top_x, side_y - 4, top_x + 7, side_y - 1), fill=COAT_EDGE)
        d.rectangle((side_x, side_y, side_x + 15, side_y + 11), fill=COAT)
        d.rectangle((side_x, side_y + 9, side_x + 15, side_y + 11), fill=BRASS)
        d.line((side_x + 1, side_y + 2, side_x + 14, side_y + 2), fill=COAT_EDGE)

    for top_x, side_x, side_y in ((4, 0, 20), (20, 16, 52)):
        d.rectangle((top_x, side_y - 4, top_x + 7, side_y - 1), fill=COAT)
        d.rectangle((side_x, side_y, side_x + 15, side_y + 11), fill=COAT_EDGE)
        d.rectangle((side_x, side_y + 8, side_x + 15, side_y + 11), fill=COAT)
        d.line((side_x + 5, side_y + 1, side_x + 5, side_y + 8), fill=SHADOW)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)

    ticket = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    t = ImageDraw.Draw(ticket)
    t.rectangle((1, 3, 14, 12), fill=BRASS, outline=PALE)
    t.rectangle((3, 4, 12, 11), fill=COAT)
    t.rectangle((5, 5, 10, 10), fill=PALE)
    t.point((7, 6), fill=COAT)
    t.point((9, 8), fill=COAT)
    t.line((7, 8, 7, 6), fill=RED)
    t.line((7, 8, 9, 8), fill=COAT)
    t.point((2, 7), fill=SHADOW)
    t.point((13, 7), fill=SHADOW)
    TICKET.parent.mkdir(parents=True, exist_ok=True)
    ticket.save(TICKET)


if __name__ == "__main__":
    draw()
    print(f"Drawn {OUT} and {TICKET}")
