"""Draw six original 64x64 biped skins for regional approach encounters."""

from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/textures/entity"
PALETTES = {
    "signal_warden": ("#363C42", "#917558", "#E7C784", "#D9CBB0"),
    "understudy": ("#38283E", "#864F76", "#E194BF", "#E6D4C7"),
    "margin_shade": ("#25363C", "#477A87", "#8AD4DE", "#DDE7D6"),
    "watchkeeper": ("#30322D", "#785445", "#E0AE65", "#E8DBC6"),
    "relay_phantom": ("#1E3038", "#345B60", "#83D9E1", "#B8D7CE"),
    "uncast": ("#2D293A", "#65566E", "#AA95D5", "#F0E5D8"),
}


def skin(name: str, ink: str, coat: str, light: str, face: str) -> Image.Image:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    # Base Minecraft biped UV islands, with a distinct mask/chest emblem.
    for box in ((8, 0, 15, 7), (16, 0, 23, 7), (0, 8, 7, 15),
                (8, 8, 15, 15), (16, 8, 23, 15), (24, 8, 31, 15)):
        draw.rectangle(box, fill=ink)
    draw.rectangle((9, 9, 14, 14), fill=face)
    draw.line((9, 9, 14, 9), fill=coat, width=2)
    draw.point((10, 12), fill=light)
    draw.point((13, 12), fill=light)
    if name == "uncast":
        draw.rectangle((9, 9, 11, 14), fill=ink)
        draw.point((13, 12), fill=light)
    elif name == "understudy":
        draw.line((9, 9, 14, 14), fill=coat, width=2)
    elif name == "margin_shade":
        draw.line((9, 14, 14, 10), fill=coat)
    elif name == "watchkeeper":
        draw.line((10, 10, 13, 10), fill=light)
    elif name == "relay_phantom":
        draw.point((11, 10), fill=light)
        draw.point((12, 10), fill=light)
    else:
        draw.rectangle((10, 10, 13, 13), outline=coat)

    for box in ((20, 16, 27, 19), (28, 16, 35, 19), (16, 20, 19, 31),
                (20, 20, 27, 31), (28, 20, 31, 31), (32, 20, 39, 31)):
        draw.rectangle(box, fill=coat)
    draw.line((23, 20, 23, 30), fill=ink)
    draw.line((25, 20, 25, 30), fill=light)
    draw.rectangle((21, 23, 26, 27), outline=ink)
    if name == "signal_warden":
        draw.ellipse((22, 23, 25, 26), outline=light)
        draw.line((23, 24, 24, 25), fill=light)
    elif name == "understudy":
        draw.polygon([(21, 26), (24, 22), (27, 26)], fill=light)
    elif name == "margin_shade":
        draw.line((21, 23, 26, 27), fill=light)
        draw.line((26, 23, 21, 27), fill=face)
    elif name == "watchkeeper":
        draw.polygon([(21, 23), (26, 23), (25, 27), (22, 27)], fill=light)
    elif name == "relay_phantom":
        for x in (22, 24, 26):
            draw.point((x, 25), fill=light)
    else:
        draw.rectangle((22, 23, 25, 26), fill=face)
        draw.line((22, 26, 25, 23), fill=light)

    for x, y in ((40, 20), (32, 52), (0, 20), (16, 52)):
        draw.rectangle((x, y, x + 15, y + 11), fill=ink)
        draw.rectangle((x, y, x + 15, y + 3), fill=coat)
        draw.line((x + 2, y + 7, x + 13, y + 7), fill=light)
    for x, y in ((44, 16), (36, 48), (4, 16), (20, 48)):
        draw.rectangle((x, y, x + 7, y + 3), fill=coat)
    return image


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, colors in PALETTES.items():
        target = OUT / f"{name}.png"
        skin(name, *colors).save(target)
        print(target)


if __name__ == "__main__":
    main()
