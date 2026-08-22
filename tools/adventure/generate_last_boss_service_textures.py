"""Draw original 16 px Redline and Unwritten townhall service marks."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/textures"


def rescue_dispatch():
    image = Image.new("RGB", (16, 16), "#552d32")
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 14, 14), outline="#d9a76b")
    draw.rectangle((2, 2, 13, 13), fill="#a65a47")
    draw.line((3, 11, 6, 8, 9, 9, 12, 5), fill="#f4d69e", width=2)
    draw.rectangle((3, 4, 5, 6), fill="#f4d69e")
    draw.rectangle((10, 10, 12, 12), fill="#e9bc6f")
    image.save(ROOT / "block/townhall_rescue_dispatch.png")


def stage_desk():
    image = Image.new("RGB", (16, 16), "#293447")
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 14, 14), outline="#d6bb83")
    draw.rectangle((2, 2, 13, 13), fill="#c7c0ad")
    draw.arc((3, 3, 12, 12), 0, 180, fill="#694c79", width=2)
    draw.line((3, 8, 12, 8), fill="#694c79", width=1)
    draw.polygon(((8, 3), (10, 6), (6, 6)), fill="#c775a7")
    draw.rectangle((6, 10, 9, 12), fill="#d6bb83")
    image.save(ROOT / "block/townhall_stage_desk.png")


def dispatch_bell():
    image = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(image)
    draw.ellipse((5, 1, 10, 5), outline="#4a3335", width=1)
    draw.polygon(((5, 5), (10, 5), (12, 12), (3, 12)), fill="#d7a464", outline="#4a3335")
    draw.line((3, 11, 12, 11), fill="#f5d69f", width=1)
    draw.rectangle((6, 13, 9, 14), fill="#4a3335")
    draw.rectangle((7, 7, 8, 9), fill="#a85747")
    image.save(ROOT / "item/dispatch_bell.png")


if __name__ == "__main__":
    (ROOT / "block").mkdir(parents=True, exist_ok=True)
    (ROOT / "item").mkdir(parents=True, exist_ok=True)
    rescue_dispatch()
    stage_desk()
    dispatch_bell()
