"""Draw original stage-mask boss skin and movement-mark pixel art."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/textures"
PLUM = "#332540"
PLUM_LIGHT = "#64405F"
MAGENTA = "#C95888"
BRASS = "#D7B160"
IVORY = "#F4E6C7"
INK = "#1F1B29"


def draw() -> None:
    skin = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(skin)

    # Classic biped UVs: head, torso, arms and legs. A two-tone theater mask
    # and curtain coat read differently from the station clock and gallery ink.
    for box in ((8, 0, 15, 7), (16, 0, 23, 7), (0, 8, 7, 15),
                (8, 8, 15, 15), (16, 8, 23, 15), (24, 8, 31, 15)):
        d.rectangle(box, fill=IVORY)
    d.rectangle((8, 8, 11, 15), fill=PLUM)
    d.rectangle((12, 8, 15, 15), fill=IVORY)
    d.point((10, 11), fill=BRASS)
    d.point((13, 11), fill=INK)
    d.line((10, 13, 13, 14), fill=MAGENTA)
    d.rectangle((8, 8, 15, 15), outline=BRASS)

    for box in ((20, 16, 27, 19), (28, 16, 35, 19), (16, 20, 19, 31),
                (20, 20, 27, 31), (28, 20, 31, 31), (32, 20, 39, 31)):
        d.rectangle(box, fill=PLUM)
    d.line((20, 20, 27, 20), fill=BRASS)
    d.line((20, 30, 27, 30), fill=MAGENTA)
    d.line((23, 20, 23, 30), fill=PLUM_LIGHT)
    d.polygon([(25, 20), (27, 20), (26, 27)], fill=BRASS)
    d.rectangle((18, 25, 19, 29), fill=MAGENTA)
    d.rectangle((28, 25, 29, 29), fill=MAGENTA)

    for x, y in ((40, 20), (32, 52), (0, 20), (16, 52)):
        d.rectangle((x, y, x + 15, y + 11), fill=PLUM_LIGHT)
        d.line((x, y + 2, x + 15, y + 2), fill=BRASS)
        d.rectangle((x, y + 9, x + 15, y + 11), fill=PLUM)
    for x, y in ((44, 16), (36, 48), (4, 16), (20, 48)):
        d.rectangle((x, y, x + 7, y + 3), fill=PLUM)
    d.line((43, 25, 51, 25), fill=MAGENTA)
    d.line((3, 25, 11, 25), fill=MAGENTA)

    boss = ROOT / "entity/unbroken_punchline.png"
    boss.parent.mkdir(parents=True, exist_ok=True)
    skin.save(boss)

    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = ImageDraw.Draw(icon)
    p.rectangle((2, 2, 13, 13), fill=PLUM, outline=BRASS)
    p.line((4, 10, 7, 10, 7, 6, 11, 6), fill=IVORY, width=2)
    p.polygon([(10, 4), (13, 6), (10, 8)], fill=MAGENTA)
    p.point((4, 4), fill=BRASS)
    p.point((11, 11), fill=BRASS)
    mark = ROOT / "item/rehearsal_mark.png"
    mark.parent.mkdir(parents=True, exist_ok=True)
    icon.save(mark)
    print(f"Drawn {boss} and {mark}")


if __name__ == "__main__":
    draw()
