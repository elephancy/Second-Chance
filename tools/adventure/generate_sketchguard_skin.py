"""Draw the original paper-and-ink Sketchguard skin on vanilla biped UVs."""

from pathlib import Path

from PIL import Image, ImageDraw


OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/textures/entity/sketchguard.png"
PAPER = "#D9D0B7"
SHADE = "#A79D89"
INK = "#202535"
EDGE = "#465064"
CYAN = "#73D4CF"
COPPER = "#BD8158"
VIOLET = "#76658A"


def draw() -> None:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)

    # Head: pale paper cube with an ink face and one off-center eye.
    for box in ((8, 0, 15, 7), (16, 0, 23, 7), (0, 8, 7, 15),
                (8, 8, 15, 15), (16, 8, 23, 15), (24, 8, 31, 15)):
        d.rectangle(box, fill=PAPER)
    d.rectangle((8, 8, 15, 15), fill=INK)
    d.rectangle((9, 10, 13, 12), fill=CYAN)
    d.point((14, 11), fill=COPPER)
    d.line((8, 14, 15, 14), fill=VIOLET)
    d.line((8, 0, 15, 0), fill=COPPER)
    d.line((24, 9, 30, 12), fill=EDGE, width=1)

    # Torso: a canvas apron with a crooked painted line, copper clasp and
    # shaded side faces. Front face is x=20..27, y=20..31.
    for box in ((20, 16, 27, 19), (28, 16, 35, 19), (16, 20, 19, 31),
                (20, 20, 27, 31), (28, 20, 31, 31), (32, 20, 39, 31)):
        d.rectangle(box, fill=PAPER)
    d.rectangle((16, 20, 19, 31), fill=SHADE)
    d.rectangle((28, 20, 31, 31), fill=SHADE)
    d.line((20, 22, 26, 28), fill=INK, width=2)
    d.line((22, 22, 27, 27), fill=CYAN, width=1)
    d.rectangle((23, 20, 24, 22), fill=COPPER)
    d.line((20, 30, 27, 30), fill=VIOLET)
    d.line((32, 21, 38, 27), fill=EDGE, width=1)

    # Sleeves resemble paper strips held by dark ink cuffs.
    for top_x, side_x, side_y in ((44, 40, 20), (36, 32, 52)):
        d.rectangle((top_x, side_y - 4, top_x + 3, side_y - 1), fill=SHADE)
        d.rectangle((top_x + 4, side_y - 4, top_x + 7, side_y - 1), fill=EDGE)
        d.rectangle((side_x, side_y, side_x + 15, side_y + 11), fill=PAPER)
        d.line((side_x + 1, side_y + 2, side_x + 13, side_y + 8), fill=SHADE)
        d.rectangle((side_x, side_y + 9, side_x + 15, side_y + 11), fill=INK)
        d.line((side_x + 3, side_y + 10, side_x + 6, side_y + 10), fill=CYAN)

    # Two dark legs with contrasting brushed edges.
    for top_x, side_x, side_y in ((4, 0, 20), (20, 16, 52)):
        d.rectangle((top_x, side_y - 4, top_x + 7, side_y - 1), fill=INK)
        d.rectangle((side_x, side_y, side_x + 15, side_y + 11), fill=EDGE)
        d.line((side_x + 5, side_y, side_x + 7, side_y + 8), fill=VIOLET, width=2)
        d.rectangle((side_x, side_y + 9, side_x + 15, side_y + 11), fill=INK)
        d.point((side_x + 5, side_y + 7), fill=CYAN)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)


if __name__ == "__main__":
    draw()
    print(f"Drawn {OUT}")
