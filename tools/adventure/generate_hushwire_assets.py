"""Draw original Empty Encore, Signal Echo and Signal Dial pixel art."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/textures"
BLACK = "#202932"
COAT = "#33434A"
COPPER = "#AD6F59"
AMBER = "#E1AD69"
TEAL = "#79D5D8"
PALE = "#B5E1DD"
WHITE = "#E6E9DB"


def skin(echo: bool) -> Image.Image:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    base = PALE if echo else BLACK
    coat = TEAL if echo else COAT
    trim = COPPER if echo else AMBER
    for box in ((8, 0, 15, 7), (16, 0, 23, 7), (0, 8, 7, 15),
                (8, 8, 15, 15), (16, 8, 23, 15), (24, 8, 31, 15)):
        draw.rectangle(box, fill=base)
    draw.rectangle((9, 9, 14, 14), fill=coat, outline=trim)
    for point in ((10, 11), (12, 11), (11, 13)):
        draw.point(point, fill=WHITE if echo else AMBER)
    draw.line((9, 14, 14, 14), fill=BLACK if echo else COPPER)

    for box in ((20, 16, 27, 19), (28, 16, 35, 19), (16, 20, 19, 31),
                (20, 20, 27, 31), (28, 20, 31, 31), (32, 20, 39, 31)):
        draw.rectangle(box, fill=coat)
    draw.line((23, 20, 23, 30), fill=trim)
    draw.rectangle((21, 23, 26, 26), fill=BLACK if echo else COPPER)
    for x in (22, 24, 26):
        draw.point((x, 24), fill=TEAL if echo or x == 24 else AMBER)
    draw.line((20, 30, 27, 30), fill=trim)
    for x in (17, 29, 33, 37):
        draw.point((x, 24), fill=trim)

    for x, y in ((40, 20), (32, 52), (0, 20), (16, 52)):
        draw.rectangle((x, y, x + 15, y + 11), fill=coat)
        draw.rectangle((x, y + 9, x + 15, y + 11), fill=base)
        draw.line((x + 2, y + 4, x + 13, y + 4), fill=trim)
        draw.point((x + 7, y + 4), fill=WHITE if echo else TEAL)
    for x, y in ((44, 16), (36, 48), (4, 16), (20, 48)):
        draw.rectangle((x, y, x + 7, y + 3), fill=base)
    return image


def draw() -> None:
    entity = ROOT / "entity"
    entity.mkdir(parents=True, exist_ok=True)
    skin(False).save(entity / "empty_encore.png")
    skin(True).save(entity / "signal_echo.png")

    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pen = ImageDraw.Draw(icon)
    pen.ellipse((2, 2, 13, 13), fill=BLACK, outline=COPPER)
    pen.ellipse((4, 4, 11, 11), fill=COAT, outline=TEAL)
    pen.line((8, 8, 12, 4), fill=WHITE, width=2)
    for point in ((4, 8), (8, 4), (12, 8)):
        pen.point(point, fill=AMBER)
    pen.point((8, 8), fill=TEAL)
    item = ROOT / "item/signal_dial.png"
    item.parent.mkdir(parents=True, exist_ok=True)
    icon.save(item)
    print(f"Drawn {entity / 'empty_encore.png'}, {entity / 'signal_echo.png'} and {item}")


if __name__ == "__main__":
    draw()
