"""Draw the small, crisp Rumor Atlas sprite from a fixed pixel palette."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/assets/gems/textures/item/rumor_atlas.png"

ink = "#202334"
shadow = "#3a3040"
leather = "#664957"
leather_light = "#926c63"
paper = "#ead1a0"
paper_light = "#f8e4b6"
paper_shadow = "#bfa374"
gold = "#e6ba70"
teal = "#4ac6b0"
magenta = "#b95e9d"

image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
d = ImageDraw.Draw(image)

# Dark folded cover, two parchment leaves and a stitched spine.
d.polygon([(3, 5), (13, 2), (17, 5), (26, 3), (29, 6),
           (29, 27), (18, 30), (15, 28), (5, 29), (2, 26)], fill=ink)
d.polygon([(4, 6), (13, 4), (16, 7), (16, 27), (5, 27), (4, 25)], fill=leather)
d.polygon([(17, 7), (26, 5), (27, 7), (27, 26), (18, 28), (17, 26)], fill=leather_light)
d.polygon([(6, 7), (13, 5), (15, 8), (15, 25), (6, 26)], fill=paper)
d.polygon([(18, 8), (25, 6), (26, 8), (26, 24), (18, 26)], fill=paper_light)
d.line([(16, 6), (16, 27)], fill=ink, width=2)
d.line([(7, 24), (14, 23)], fill=paper_shadow)
d.line([(19, 24), (25, 22)], fill=paper_shadow)

# A wandering dotted route crosses the page and ends at a compass rose.
for x, y in [(8, 20), (10, 18), (11, 16), (13, 14), (18, 13), (20, 11)]:
    d.point((x, y), fill=magenta)
d.polygon([(22, 8), (23, 10), (25, 11), (23, 12),
           (22, 15), (21, 12), (19, 11), (21, 10)], fill=ink)
d.polygon([(22, 9), (23, 11), (22, 13), (21, 11)], fill=teal)
d.point((22, 11), fill=paper_light)

# A brass corner and thread tie make it read as a carried object at item scale.
d.rectangle((3, 5, 5, 8), fill=gold)
d.rectangle((26, 24, 28, 27), fill=gold)
d.line([(13, 3), (16, 6), (18, 5)], fill=gold)

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
image.save(OUTPUT)
print(OUTPUT)
