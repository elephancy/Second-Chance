"""Draw original 16 px Angle Desk and Return Board marks with fixed pixel geometry."""

from pathlib import Path

from PIL import Image, ImageDraw


ASSETS = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/textures"
INK = "#203844"
PALE = "#eee8db"
TEAL = "#78b8bb"
GOLD = "#d9ae62"
BRASS = "#967449"
NIGHT = "#243146"


def angle_desk():
    image = Image.new("RGB", (16, 16), INK)
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 14, 14), outline=GOLD)
    draw.rectangle((2, 2, 13, 13), fill=PALE)
    draw.line((4, 11, 11, 4), fill=INK, width=2)
    draw.line((4, 11, 12, 11), fill=INK, width=2)
    draw.line((7, 8, 10, 8, 10, 11), fill=TEAL, width=1)
    draw.rectangle((3, 3, 5, 5), fill=TEAL)
    draw.rectangle((11, 3, 12, 4), fill=GOLD)
    image.save(ASSETS / "block/townhall_angle_desk.png")


def return_board():
    image = Image.new("RGB", (16, 16), NIGHT)
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 14, 14), outline=BRASS)
    draw.rectangle((2, 2, 13, 13), fill="#3d4f5a")
    draw.ellipse((4, 3, 11, 10), outline=GOLD, width=2)
    draw.line((8, 5, 8, 7, 10, 8), fill=PALE, width=1)
    draw.line((4, 12, 11, 12), fill=GOLD, width=1)
    draw.rectangle((3, 12, 4, 13), fill=TEAL)
    image.save(ASSETS / "block/townhall_return_board.png")


def angle_lens():
    image = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(image)
    draw.ellipse((2, 1, 12, 11), fill=GOLD, outline=INK, width=1)
    draw.ellipse((4, 3, 10, 9), fill=TEAL, outline=PALE, width=1)
    draw.line((10, 10, 14, 14), fill=INK, width=3)
    draw.line((6, 8, 9, 5), fill=PALE, width=1)
    image.save(ASSETS / "item/angle_lens.png")


def return_stub():
    image = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(image)
    draw.rectangle((2, 1, 13, 14), fill=PALE, outline=BRASS, width=1)
    draw.rectangle((4, 3, 11, 9), fill=NIGHT)
    draw.ellipse((5, 4, 10, 9), outline=GOLD, width=1)
    draw.line((7, 4, 7, 6, 9, 7), fill=PALE, width=1)
    draw.line((4, 11, 11, 11), fill=BRASS, width=1)
    draw.line((4, 13, 9, 13), fill=TEAL, width=1)
    image.save(ASSETS / "item/return_stub.png")


def practice_stage():
    image = Image.new("RGB", (16, 16), "#34263e")
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 14, 14), outline=GOLD)
    draw.rectangle((2, 2, 13, 13), fill="#5d3553")
    draw.ellipse((3, 3, 12, 12), outline=GOLD, width=2)
    draw.rectangle((6, 6, 9, 9), outline="#df78a8")
    draw.line((7, 2, 7, 4), fill=PALE)
    draw.line((7, 11, 7, 13), fill=PALE)
    image.save(ASSETS / "block/townhall_practice_stage.png")


def route_desk():
    image = Image.new("RGB", (16, 16), INK)
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 14, 14), outline=TEAL)
    draw.rectangle((2, 2, 13, 13), fill=PALE)
    draw.line((7, 13, 7, 8, 4, 5), fill=INK, width=2)
    draw.line((7, 8, 11, 4), fill=BRASS, width=2)
    draw.polygon(((10, 3), (13, 3), (12, 6)), fill=GOLD)
    draw.rectangle((3, 4, 5, 6), fill=TEAL)
    image.save(ASSETS / "block/townhall_route_desk.png")


if __name__ == "__main__":
    (ASSETS / "block").mkdir(parents=True, exist_ok=True)
    (ASSETS / "item").mkdir(parents=True, exist_ok=True)
    angle_desk()
    return_board()
    angle_lens()
    return_stub()
    practice_stage()
    route_desk()
