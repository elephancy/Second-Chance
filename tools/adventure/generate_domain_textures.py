"""Rebuild district block faces from checked CC0 source pixels.

Requires Pillow. Original 16x16 tiles are stored in tools/assets/cc0_block_set;
no generated image model or handpicked random seed is involved.
"""

import json
from pathlib import Path
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/assets/cc0_block_set"
TARGET = ROOT / "src/main/resources/assets/gems/textures/block"
ITEM_TARGET = ROOT / "src/main/resources/assets/gems/textures/item"


def recolor(name: str, palette: tuple[str, ...]) -> Image.Image:
    image = Image.open(SOURCE / name).convert("RGBA")
    if image.size != (16, 16):
        raise ValueError(f"{name}: expected 16x16 source tile")
    output = Image.new("RGBA", image.size)
    for y in range(16):
        for x in range(16):
            red, green, blue, alpha = image.getpixel((x, y))
            value = (red * 3 + green * 6 + blue) // 10
            tone = min(len(palette) - 1, value * len(palette) // 256)
            output.putpixel((x, y), tuple(bytes.fromhex(palette[tone][1:])) + (alpha,))
    return output


def main() -> None:
    TARGET.mkdir(parents=True, exist_ok=True)

    # The source slate's green hue becomes a nocturnal blue stone. Cross-grain
    # chisel marks break the tile's original horizontal rhythm.
    slate = recolor("slate_tiles.png", ("#111621", "#1d2633", "#303c4d", "#4b5d70"))
    draw = ImageDraw.Draw(slate)
    for x, y in ((3, 4), (11, 1), (7, 10), (13, 13)):
        draw.line((x, y, x + 1, y + 2), fill="#65768a")
    slate.save(TARGET / "hushwire_slate.png")

    # The marble brick source remains visible as mottled masonry around a
    # newly drawn two-lancet window and rose. This is a distinct block face,
    # not a simple hue-shift of the original tile.
    tracery = recolor("marble_bricks.png", ("#22232d", "#393a48", "#626371", "#9697a2"))
    draw = ImageDraw.Draw(tracery)
    draw.polygon(((1, 14), (1, 6), (3, 3), (6, 2), (8, 0), (10, 2), (13, 3),
                  (15, 6), (15, 14)), fill="#171b29", outline="#b5a7bb")
    draw.polygon(((3, 13), (3, 7), (5, 4), (7, 7), (7, 13)),
                 fill="#37546b", outline="#827b99")
    draw.polygon(((9, 13), (9, 7), (11, 4), (13, 7), (13, 13)),
                 fill="#37546b", outline="#827b99")
    draw.ellipse((6, 3, 9, 6), fill="#78b5c0", outline="#b5a7bb")
    draw.line((8, 7, 8, 14), fill="#b5a7bb")
    draw.line((1, 14, 15, 14), fill="#b5a7bb")
    tracery.save(TARGET / "hushwire_tracery.png")

    # The civic ledger borrows the same masonry source, then gives it a warm
    # carved border and seven distinct route marks around a cyan center.
    ledger = recolor("marble_bricks.png", ("#303039", "#555360", "#8e8079", "#c1ab8b"))
    draw = ImageDraw.Draw(ledger)
    draw.rectangle((1, 1, 14, 14), outline="#ead8aa", width=1)
    draw.rectangle((3, 3, 12, 12), fill="#1b2733", outline="#c1ab8b")
    for x, y in ((5, 5), (8, 4), (11, 5), (4, 8), (12, 8), (5, 11), (11, 11)):
        draw.point((x, y), fill="#9bd0d0")
    draw.ellipse((7, 7, 9, 9), fill="#e3cc91")
    ledger.save(TARGET / "townhall_ledger.png")

    # Hamed's workshop uses the marble source as mottled paper plaster. A
    # timber lattice, folded panel edges and blue ink are new pixel geometry.
    paper = recolor("marble_bricks.png", ("#7b6956", "#a18b70", "#cfbd99", "#eee0ba"))
    draw = ImageDraw.Draw(paper)
    draw.rectangle((0, 0, 15, 15), outline="#382b2b")
    draw.line((5, 0, 5, 15), fill="#483432")
    draw.line((10, 0, 10, 15), fill="#483432")
    draw.line((0, 7, 15, 7), fill="#483432")
    draw.line((1, 1, 4, 6), fill="#e8d7ad")
    draw.line((6, 1, 9, 6), fill="#e8d7ad")
    paper.save(TARGET / "inkbound_screen.png")

    ink = recolor("slate_tiles.png", ("#171b2b", "#272d45", "#394461", "#596985"))
    draw = ImageDraw.Draw(ink)
    draw.rectangle((1, 1, 14, 14), outline="#b58c63")
    draw.line((3, 4, 12, 4), fill="#6c91a3", width=2)
    draw.line((3, 8, 10, 8), fill="#8d74a2", width=2)
    draw.line((3, 12, 12, 12), fill="#c49b70", width=2)
    draw.ellipse((10, 6, 13, 9), fill="#d6c9a0")
    ink.save(TARGET / "inkbound_inkstone.png")

    # Three readable printing plates. Each shares the same source grain but
    # carries a different, hand-authored pixel motif rather than a recolor.
    for name, color in (("spark", "#efb85f"), ("shelter", "#9cc9bf"),
                        ("route", "#b6a5db")):
        plate = recolor("slate_tiles.png", ("#171b2b", "#272d45", "#394461", "#596985"))
        pen = ImageDraw.Draw(plate)
        pen.rectangle((1, 1, 14, 14), outline="#c79f6a")
        pen.rectangle((3, 3, 12, 12), fill="#1c2439")
        if name == "spark":
            pen.polygon(((8, 2), (4, 8), (7, 8), (6, 14), (12, 6), (9, 6)), fill=color)
        elif name == "shelter":
            pen.polygon(((2, 8), (8, 3), (14, 8)), fill=color)
            pen.rectangle((4, 8, 12, 13), outline=color)
            pen.rectangle((7, 10, 9, 13), fill=color)
        else:
            pen.line((3, 12, 6, 9, 5, 6, 9, 5, 12, 2), fill=color, width=2)
            pen.polygon(((9, 2), (14, 1), (13, 6)), fill=color)
        plate.save(TARGET / f"inkbound_{name}.png")

    press = recolor("marble_bricks.png", ("#493a36", "#6f5448", "#a67e5d", "#d2af7a"))
    pen = ImageDraw.Draw(press)
    pen.rectangle((1, 1, 14, 14), outline="#30273b", width=2)
    pen.rectangle((3, 3, 12, 12), fill="#d8c6a0", outline="#30273b")
    for y, length in ((5, 8), (8, 6), (11, 8)):
        pen.line((4, y, length + 3, y), fill="#30465a")
    press.save(TARGET / "inkbound_press.png")

    binding = recolor("marble_bricks.png", ("#493a36", "#6f5448", "#a67e5d", "#d2af7a"))
    pen = ImageDraw.Draw(binding)
    pen.rectangle((1, 1, 14, 14), outline="#30273b", width=2)
    pen.polygon(((3, 3), (8, 4), (8, 13), (3, 12)), fill="#ded0a9", outline="#30465a")
    pen.polygon(((8, 4), (13, 3), (13, 12), (8, 13)), fill="#f0dfbc", outline="#30465a")
    pen.line((5, 6, 7, 7), fill="#8d74a2")
    pen.line((5, 9, 7, 10), fill="#8d74a2")
    pen.line((9, 7, 11, 6), fill="#6c91a3")
    pen.line((9, 10, 11, 9), fill="#6c91a3")
    binding.save(TARGET / "inkbound_binding.png")

    branch_witness = recolor("slate_tiles.png", ("#17232e", "#2b3b4b", "#506170", "#8293a0"))
    pen = ImageDraw.Draw(branch_witness)
    pen.rectangle((1, 1, 14, 14), outline="#b8a77c")
    pen.line((3, 12, 7, 7, 11, 8, 13, 4), fill="#e0c98c", width=2)
    pen.line((3, 5, 6, 7, 10, 5), fill="#729dba")
    pen.rectangle((10, 9, 12, 11), fill="#cc876e")
    branch_witness.save(TARGET / "branch_witness.png")

    lastlight_witness = recolor("marble_bricks.png", ("#30272a", "#684d43", "#a17558", "#cfaa75"))
    pen = ImageDraw.Draw(lastlight_witness)
    pen.rectangle((1, 1, 14, 14), outline="#e4cb97")
    for x in (4, 7, 10, 13):
        pen.line((x, 4, x, 11), fill="#3e5365")
    pen.line((3, 6, 12, 6), fill="#d7e0d5")
    pen.line((3, 9, 6, 9), fill="#d7e0d5")
    pen.rectangle((8, 8, 11, 11), outline="#eaac78")
    lastlight_witness.save(TARGET / "lastlight_witness.png")

    comparison = recolor("marble_bricks.png", ("#303644", "#586174", "#8b8990", "#c5bba4"))
    pen = ImageDraw.Draw(comparison)
    pen.rectangle((1, 1, 14, 14), outline="#d8c296")
    pen.rectangle((2, 3, 6, 12), fill="#e5d3aa", outline="#3d5068")
    pen.rectangle((9, 3, 13, 12), fill="#d8c3a9", outline="#705b70")
    pen.line((4, 6, 6, 7), fill="#506c88")
    pen.line((9, 8, 11, 6), fill="#8d6380")
    pen.line((7, 7, 8, 8), fill="#c89a59")
    comparison.save(TARGET / "othman_comparison.png")

    desk = recolor("marble_bricks.png", ("#493a36", "#6f5448", "#a67e5d", "#d2af7a"))
    pen = ImageDraw.Draw(desk)
    pen.rectangle((1, 1, 14, 14), outline="#30273b", width=2)
    pen.polygon(((3, 4), (10, 3), (13, 5), (12, 12), (4, 13)),
                fill="#ded0a9", outline="#30465a")
    pen.line((5, 7, 11, 6), fill="#30465a")
    pen.line((5, 9, 10, 8), fill="#30465a")
    pen.line((5, 11, 9, 10), fill="#30465a")
    desk.save(TARGET / "townhall_inkbound_desk.png")

    # Ragab's ceramic facing keeps the source marble grain but replaces its
    # brick rhythm with a vertical Art Deco fan and copper chevrons.
    faience = recolor("marble_bricks.png", ("#184d59", "#246c73", "#41898b", "#70aba4"))
    draw = ImageDraw.Draw(faience)
    draw.rectangle((0, 0, 15, 15), outline="#c59155")
    for x in (3, 6, 9, 12):
        draw.line((x, 14, x, 6 + abs(8 - x) // 2), fill="#d5ae73")
    draw.line((1, 11, 8, 3), fill="#e1c592")
    draw.line((8, 3, 14, 11), fill="#e1c592")
    draw.rectangle((6, 2, 9, 4), fill="#f1d997")
    faience.save(TARGET / "deco_faience.png")

    # These two companion tiles are separately drawn compositions over the
    # checked CC0 marble grain, not hue variants of the faience fan.
    sunburst = recolor("marble_bricks.png", ("#193b4a", "#215565", "#3d7880", "#70a4a1"))
    draw = ImageDraw.Draw(sunburst)
    draw.rectangle((0, 0, 15, 15), outline="#d5ae73")
    draw.rectangle((2, 2, 13, 13), fill="#183d4d", outline="#e3bd7d")
    draw.polygon(((3, 12), (8, 5), (13, 12)), fill="#d69b54", outline="#f4d998")
    draw.ellipse((5, 6, 10, 11), fill="#f2cb79")
    for x0, y0, x1, y1 in ((8, 2, 8, 4), (3, 4, 5, 6), (11, 6, 13, 4),
                           (1, 9, 4, 9), (12, 9, 15, 9)):
        draw.line((x0, y0, x1, y1), fill="#f6dfad")
    sunburst.save(TARGET / "deco_sunburst.png")

    marquee = recolor("marble_bricks.png", ("#101f32", "#1b3347", "#285269", "#497a82"))
    draw = ImageDraw.Draw(marquee)
    draw.rectangle((0, 0, 15, 15), outline="#d7aa67")
    for left, top, right, bottom, color in (
            (2, 2, 5, 13, "#e5b770"), (6, 4, 9, 13, "#65c6c4"),
            (10, 2, 13, 13, "#e5b770")):
        draw.rectangle((left, top, right, bottom), fill=color)
        draw.rectangle((left + 1, top + 1, right - 1, bottom - 1), fill="#173a4a")
    for x, y in ((3, 4), (7, 6), (11, 4), (3, 10), (7, 10), (11, 10)):
        draw.point((x, y), fill="#fff0bf")
    marquee.save(TARGET / "deco_marquee.png")

    # Framefall's bright wall is divided into stepped planes; its companion
    # prism depicts a shifted view rather than reusing vanilla stained glass.
    plane = recolor("marble_bricks.png", ("#8c9aa2", "#b9c8c9", "#d9e1dc", "#f1f3e9"))
    draw = ImageDraw.Draw(plane)
    draw.polygon(((0, 2), (13, 0), (15, 5), (2, 7)), fill="#eff3ed")
    draw.polygon(((2, 7), (15, 5), (15, 13), (4, 15)), fill="#b8c9cf")
    draw.line((0, 2, 2, 7, 4, 15), fill="#6f93a1")
    draw.line((2, 7, 15, 5), fill="#f8fbf2")
    draw.line((4, 15, 15, 13), fill="#688697")
    plane.save(TARGET / "gallery_plane.png")

    prism = recolor("marble_bricks.png", ("#536f83", "#739aab", "#a6c5c8", "#dce6dc"))
    draw = ImageDraw.Draw(prism)
    draw.rectangle((0, 0, 15, 15), outline="#dbe3d9")
    draw.polygon(((3, 3), (11, 2), (13, 10), (5, 12)), fill="#2f677e", outline="#e9d495")
    draw.polygon(((5, 5), (9, 4), (11, 9), (7, 10)), fill="#9ed1d3")
    draw.line((2, 13, 13, 2), fill="#f4e3aa", width=2)
    draw.line((4, 14, 15, 3), fill="#d1b9cb")
    prism.save(TARGET / "gallery_prism.png")

    # Lastlight repeats a pale stone datum around aged red brick; the dial
    # freezes the instant of a missed departure as an in-world motif.
    ashlar = recolor("marble_bricks.png", ("#564641", "#8b6658", "#bc9272", "#ddbb8c"))
    draw = ImageDraw.Draw(ashlar)
    draw.rectangle((0, 0, 15, 15), outline="#51443f")
    draw.line((0, 5, 15, 5), fill="#e3c99e", width=2)
    draw.line((0, 11, 15, 11), fill="#e3c99e", width=2)
    for x, y in ((5, 0), (11, 6), (4, 12)):
        draw.line((x, y, x, min(15, y + 4)), fill="#5d514c")
    draw.polygon(((1, 3), (6, 1), (11, 3), (14, 1), (14, 4), (1, 4)),
                 fill="#bb9274")
    ashlar.save(TARGET / "station_ashlar.png")

    dial = recolor("slate_tiles.png", ("#2b3035", "#464847", "#736b5c", "#b9a17b"))
    draw = ImageDraw.Draw(dial)
    draw.rectangle((0, 0, 15, 15), outline="#c4a877")
    draw.ellipse((2, 2, 13, 13), fill="#e2c795", outline="#5b473f", width=2)
    for x, y in ((8, 3), (12, 8), (8, 12), (3, 8)):
        draw.rectangle((x, y, x + 1, y + 1), fill="#3d4e59")
    draw.line((8, 8, 8, 4), fill="#425867", width=2)
    draw.line((8, 8, 11, 10), fill="#425867", width=2)
    draw.ellipse((7, 7, 9, 9), fill="#c26f55")
    dial.save(TARGET / "station_dial.png")

    # Rehearsal's timber has visible joinery and brass fasteners. The fabric
    # face uses newly drawn folds and a scalloped hem over the CC0 grain.
    timber = recolor("slate_tiles.png", ("#3b2831", "#68403b", "#9d6250", "#c58b62"))
    draw = ImageDraw.Draw(timber)
    for y in (0, 7, 15):
        draw.line((0, y, 15, y), fill="#e2ad70")
    for x in (2, 8, 14):
        draw.line((x, 1, x, 6), fill="#402b32")
        draw.rectangle((x, 9, x + 1, 10), fill="#e7bc7b")
    draw.line((0, 8, 15, 8), fill="#54333b", width=2)
    timber.save(TARGET / "stage_timber.png")

    curtain = recolor("marble_bricks.png", ("#491e39", "#7a3155", "#a64b70", "#cc7792"))
    draw = ImageDraw.Draw(curtain)
    draw.rectangle((0, 0, 15, 15), outline="#ddb475")
    for x in (2, 6, 10, 14):
        draw.polygon(((x - 1, 1), (x + 1, 1), (x + 2, 12), (x, 14)),
                     fill="#5d2549")
        draw.line((x - 1, 2, x, 12), fill="#d27b98")
    draw.line((1, 13, 14, 13), fill="#e7be83")
    curtain.save(TARGET / "stage_curtain.png")

    # Safwat's taiga records appear as heavy slate folios; a second tile
    # carries three diverging route marks that remain readable on the ground.
    folio = recolor("slate_tiles.png", ("#232d35", "#3b4c58", "#60717b", "#98a6a5"))
    draw = ImageDraw.Draw(folio)
    draw.rectangle((1, 1, 14, 14), outline="#c2b896")
    for left in (3, 8):
        draw.rectangle((left, 3, left + 3, 12), fill="#d4ccad", outline="#31495b")
        draw.line((left + 1, 5, left + 2, 5), fill="#63899a")
        draw.line((left + 1, 8, left + 2, 8), fill="#63899a")
    draw.line((7, 2, 7, 13), fill="#c1996b")
    folio.save(TARGET / "folio_stone.png")

    index = recolor("marble_bricks.png", ("#2a3340", "#405367", "#718691", "#b5c0b9"))
    draw = ImageDraw.Draw(index)
    draw.rectangle((0, 0, 15, 15), outline="#bbad83")
    draw.line((8, 13, 8, 8, 3, 4), fill="#79c9d4", width=2)
    draw.line((8, 8, 13, 4), fill="#79c9d4", width=2)
    draw.polygon(((1, 2), (6, 3), (3, 7)), fill="#d2b774")
    draw.polygon(((15, 2), (10, 3), (13, 7)), fill="#d2b774")
    draw.ellipse((6, 11, 9, 14), fill="#e8d6a1")
    index.save(TARGET / "branch_index.png")

    # Redline's shelter masonry keeps the CC0 stone grain but draws long,
    # staggered warm courses. The second block is a distinct rescue crest.
    refuge = recolor("marble_bricks.png", ("#543a38", "#875046", "#b66c56", "#dd9874"))
    draw = ImageDraw.Draw(refuge)
    for y in (0, 5, 10, 15):
        draw.line((0, y, 15, y), fill="#f3cf9b")
    for x, top in ((4, 1), (11, 1), (1, 6), (8, 6), (14, 6),
                   (4, 11), (11, 11)):
        draw.line((x, top, x, min(top + 3, 14)), fill="#563c3b")
    draw.rectangle((1, 2, 2, 3), fill="#e9aa7b")
    draw.rectangle((9, 12, 10, 13), fill="#e9aa7b")
    refuge.save(TARGET / "refuge_brick.png")

    emblem = recolor("slate_tiles.png", ("#4b3b3b", "#7c5550", "#b18367", "#e0bd8c"))
    draw = ImageDraw.Draw(emblem)
    draw.rectangle((0, 0, 15, 15), outline="#f0d4a1")
    draw.polygon(((8, 1), (14, 5), (13, 12), (8, 15), (2, 12), (1, 5)),
                 fill="#f3ddaa", outline="#542e35")
    draw.polygon(((8, 3), (12, 6), (11, 11), (8, 13), (5, 11), (4, 6)),
                 fill="#9a4446", outline="#d9a973")
    draw.rectangle((7, 5, 8, 11), fill="#f4e2b2")
    draw.rectangle((5, 7, 10, 8), fill="#f4e2b2")
    emblem.save(TARGET / "shelter_emblem.png")

    # Unwritten uses a painted proscenium: broad pale ribs and two sharply
    # different masks, so the finale reads as a stage from a distance.
    plaster = recolor("marble_bricks.png", ("#56515f", "#8b8795", "#bbb8bd", "#e6ddd2"))
    draw = ImageDraw.Draw(plaster)
    draw.rectangle((0, 0, 15, 15), outline="#292936")
    for x in (3, 8, 13):
        draw.line((x, 0, x, 15), fill="#f6eee1", width=2)
    for y in (4, 11):
        draw.line((0, y, 15, y), fill="#a87768")
    plaster.save(TARGET / "court_plaster.png")

    mask = recolor("slate_tiles.png", ("#222333", "#353749", "#595367", "#8b7682"))
    draw = ImageDraw.Draw(mask)
    draw.rectangle((0, 0, 15, 15), outline="#d5ad83")
    draw.polygon(((1, 3), (6, 2), (7, 6), (6, 12), (3, 14), (1, 10)),
                 fill="#eadac5", outline="#332f42")
    draw.polygon(((9, 2), (14, 3), (14, 10), (12, 14), (9, 12), (8, 6)),
                 fill="#71506f", outline="#dfba90")
    draw.point((4, 6), fill="#303346")
    draw.point((11, 6), fill="#f8dfa5")
    draw.arc((2, 8, 6, 12), 0, 180, fill="#a96a62")
    draw.arc((9, 8, 13, 12), 180, 360, fill="#e8ceac")
    mask.save(TARGET / "mask_relief.png")

    assets = ROOT / "src/main/resources/assets/gems"
    data = ROOT / "src/main/resources/data/gems"
    for name in ("deco_sunburst", "deco_marquee", "gallery_plane",
                 "gallery_prism", "station_ashlar", "station_dial",
                 "stage_timber", "stage_curtain", "folio_stone",
                 "branch_index", "refuge_brick", "shelter_emblem",
                 "court_plaster", "mask_relief"):
        (assets / "blockstates" / f"{name}.json").write_text(json.dumps({
            "variants": {"": {"model": f"gems:block/{name}"}}
        }) + "\n")
        (assets / "models/block" / f"{name}.json").write_text(json.dumps({
            "parent": "minecraft:block/cube_all", "textures": {"all": f"gems:block/{name}"}
        }) + "\n")
        (assets / "items" / f"{name}.json").write_text(json.dumps({
            "model": {"type": "minecraft:model", "model": f"gems:block/{name}"}
        }) + "\n")
        (data / "loot_table/blocks" / f"{name}.json").write_text(json.dumps({
            "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{
                "type": "minecraft:item", "name": f"gems:{name}"}]}]
        }) + "\n")

    # Khaled's bridge stone retains CC0 masonry grain, but gets water-cut
    # ashlar joints and alternating cutwater marks distinct from the source.
    limestone = recolor("marble_bricks.png", ("#52646c", "#829197", "#b6bcb6", "#e2dec9"))
    pen = ImageDraw.Draw(limestone)
    pen.line((0, 0, 15, 0), fill="#ecdec1")
    pen.line((0, 8, 15, 8), fill="#526775")
    pen.line((7, 0, 7, 7), fill="#526775")
    pen.line((3, 9, 3, 15), fill="#526775")
    pen.line((12, 9, 12, 15), fill="#526775")
    for x in (2, 10):
        pen.polygon(((x, 5), (x + 2, 2), (x + 4, 5)),
                    fill="#d3c6a5", outline="#607c85")
    limestone.save(TARGET / "riverward_limestone.png")

    gauge = recolor("slate_tiles.png", ("#30444e", "#4b6870", "#789397", "#b8c6ba"))
    pen = ImageDraw.Draw(gauge)
    pen.rectangle((1, 1, 14, 14), outline="#d0c29d", width=1)
    pen.rectangle((6, 2, 9, 13), fill="#203942", outline="#dfd4b3")
    for y, length in ((4, 3), (7, 5), (10, 3), (13, 5)):
        pen.line((10, y, 10 + length, y), fill="#d6c89f")
    pen.polygon(((7, 10), (8, 4), (9, 10)), fill="#7dc7d1")
    gauge.save(TARGET / "riverward_gauge.png")

    route = limestone.copy()
    pen = ImageDraw.Draw(route)
    pen.rectangle((2, 2, 13, 13), fill="#244653", outline="#e6d2a2")
    pen.line((3, 10, 8, 10, 8, 5, 12, 5), fill="#83c6d4", width=2)
    pen.polygon(((11, 3), (14, 5), (11, 7)), fill="#83c6d4")
    route.save(TARGET / "khaled_route_start.png")

    refuge = limestone.copy()
    pen = ImageDraw.Draw(refuge)
    pen.rectangle((2, 2, 13, 13), fill="#244653", outline="#e6d2a2")
    pen.polygon(((2, 8), (8, 3), (14, 8)), fill="#9ed0cf")
    pen.rectangle((4, 8, 12, 12), outline="#9ed0cf")
    pen.line((5, 10, 11, 10), fill="#e4d5ae", width=2)
    refuge.save(TARGET / "khaled_refuge.png")

    for side in ("west", "east"):
        brace = limestone.copy()
        pen = ImageDraw.Draw(brace)
        pen.rectangle((1, 1, 14, 14), fill="#1f3b48", outline="#dbcba3")
        if side == "west":
            pen.line((2, 12, 4, 5, 8, 3), fill="#bf8661", width=3)
            pen.line((8, 3, 10, 6), fill="#bf8661", width=2)
            pen.line((7, 5, 10, 9, 8, 12), fill="#92c9ce", width=2)
        else:
            pen.line((13, 12, 11, 5, 7, 3), fill="#bf8661", width=3)
            pen.line((7, 3, 5, 6), fill="#bf8661", width=2)
            pen.line((9, 5, 6, 9, 8, 12), fill="#92c9ce", width=2)
        brace.save(TARGET / f"khaled_{side}_brace.png")

        restored = limestone.copy()
        pen = ImageDraw.Draw(restored)
        pen.rectangle((1, 1, 14, 14), fill="#2b5260", outline="#e8d6ac")
        pen.arc((2, 2, 13, 13), 180, 360, fill="#d49a70", width=3)
        pen.line((2, 8, 2, 13, 13, 13, 13, 8), fill="#d49a70", width=2)
        pen.ellipse((6, 6, 9, 9), fill="#f3d8a6", outline="#719fb0")
        if side == "west":
            pen.line((3, 11, 5, 9), fill="#a5d2cf", width=2)
        else:
            pen.line((10, 9, 12, 11), fill="#a5d2cf", width=2)
        restored.save(TARGET / f"khaled_{side}_brace_restored.png")

    finish = limestone.copy()
    pen = ImageDraw.Draw(finish)
    pen.rectangle((2, 2, 13, 13), fill="#244653", outline="#e6d2a2")
    pen.polygon(((8, 3), (12, 5), (11, 10), (8, 13), (5, 10), (4, 5)),
                fill="#93c9c6", outline="#f0dbab")
    pen.line((8, 5, 8, 11), fill="#274b57")
    finish.save(TARGET / "khaled_route_finish.png")

    shelter_desk = gauge.copy()
    pen = ImageDraw.Draw(shelter_desk)
    pen.rectangle((2, 2, 13, 13), fill="#223b47", outline="#e2cda4")
    pen.polygon(((8, 3), (12, 5), (11, 10), (8, 13), (5, 10), (4, 5)),
                fill="#9acbc7", outline="#eacb94")
    pen.line((5, 8, 11, 8), fill="#335967", width=2)
    shelter_desk.save(TARGET / "townhall_shelter_desk.png")

    # Nour's white shore stone carries blue inlaid wave joints; the second
    # tile uses a compass-wave motif to tie the pier, mast and signal rooms.
    chalk = recolor("marble_bricks.png", ("#627e89", "#9db7bd", "#d5dbd2", "#f0ebd9"))
    pen = ImageDraw.Draw(chalk)
    pen.line((0, 7, 5, 7, 8, 10, 11, 7, 15, 7), fill="#317b9a", width=2)
    pen.line((0, 14, 4, 14, 7, 11, 10, 14, 15, 14), fill="#739caa")
    chalk.save(TARGET / "harbor_chalk.png")

    wave = recolor("slate_tiles.png", ("#153b55", "#276381", "#468aa2", "#8bc5cf"))
    pen = ImageDraw.Draw(wave)
    pen.rectangle((1, 1, 14, 14), outline="#e8dcaa")
    pen.line((2, 10, 6, 6, 10, 10, 14, 6), fill="#d2e9e4", width=2)
    pen.line((2, 13, 6, 9, 10, 13, 14, 9), fill="#e8dcaa")
    wave.save(TARGET / "harbor_wave_tile.png")

    clue = wave.copy()
    pen = ImageDraw.Draw(clue)
    pen.rectangle((2, 2, 13, 13), fill="#19374d", outline="#f0e1ab")
    pen.ellipse((4, 4, 11, 11), outline="#e4d7a8", width=2)
    pen.line((8, 4, 8, 11), fill="#9cd4da")
    for name in ("nour_boat_clue", "nour_fog_clue"):
        face = clue.copy()
        marker = ImageDraw.Draw(face)
        if name == "nour_boat_clue":
            marker.polygon(((8, 3), (11, 10), (5, 10)), fill="#9cd4da")
        else:
            marker.arc((3, 4, 12, 13), 30, 300, fill="#e9b968", width=2)
        face.save(TARGET / f"{name}.png")

    for name, light in (("nour_blue_lamp", "#75cce4"),
                        ("nour_amber_lamp", "#edbb70"),
                        ("nour_white_lamp", "#f0edda")):
        lamp = wave.copy()
        pen = ImageDraw.Draw(lamp)
        pen.rectangle((2, 2, 13, 13), fill="#17354a", outline="#e4d3a4")
        pen.polygon(((8, 2), (12, 8), (8, 13), (4, 8)), fill=light)
        pen.line((3, 8, 13, 8), fill="#315e74")
        lamp.save(TARGET / f"{name}.png")

    table = wave.copy()
    pen = ImageDraw.Draw(table)
    pen.rectangle((2, 2, 13, 13), fill="#17354a", outline="#e4d3a4")
    pen.line((4, 11, 8, 5, 12, 11), fill="#9bd5da", width=2)
    for x, y in ((4, 5), (8, 4), (12, 5)):
        pen.ellipse((x - 1, y - 1, x + 1, y + 1), fill="#eecb8e")
    table.save(TARGET / "townhall_signal_table.png")

    # Three relay plates share one shoreline motif but show their station role.
    for name, color, symbol in (("nour_relay_tower", "#f0dfaa", "tower"),
                                ("nour_relay_boat", "#92d5e0", "boat"),
                                ("nour_relay_fog", "#e9bc77", "fog")):
        relay = wave.copy()
        pen = ImageDraw.Draw(relay)
        pen.rectangle((2, 2, 13, 13), fill="#173b50", outline=color, width=2)
        if symbol == "tower":
            pen.line((8, 3, 8, 12), fill=color, width=2)
            pen.arc((3, 4, 12, 12), 200, 340, fill=color, width=2)
            pen.ellipse((7, 2, 9, 4), fill="#f5edcf")
        elif symbol == "boat":
            pen.polygon(((3, 9), (13, 9), (10, 12), (6, 12)), fill=color)
            pen.polygon(((8, 3), (8, 8), (12, 8)), fill="#f0e5c4")
        else:
            for y in (5, 8, 11):
                pen.arc((3, y - 2, 12, y + 2), 15, 170, fill=color, width=2)
        relay.save(TARGET / f"{name}.png")

    # Fam's plaster has broad sun-warm stucco fields and hand-cut arch
    # impressions. The mosaic changes the slate source into a radial ceramic
    # floor with alternating cobalt, amber and ivory tesserae.
    plaster = recolor("marble_bricks.png", ("#745b4b", "#a47d5f", "#d1ac78", "#f0d5a3"))
    pen = ImageDraw.Draw(plaster)
    pen.rectangle((0, 0, 15, 15), outline="#8b6249")
    pen.arc((2, 2, 13, 14), 185, 355, fill="#fff0c5", width=2)
    pen.line((2, 10, 2, 14, 13, 14, 13, 10), fill="#8e684e", width=2)
    pen.line((4, 7, 11, 7), fill="#e8c995")
    plaster.save(TARGET / "forum_plaster.png")

    mosaic = recolor("slate_tiles.png", ("#223f61", "#325e80", "#4c87a0", "#75a7ab"))
    pen = ImageDraw.Draw(mosaic)
    pen.rectangle((0, 0, 15, 15), outline="#ead49b")
    for x in (4, 8, 12):
        pen.line((x, 1, x, 14), fill="#e3ba74")
    for y in (4, 8, 12):
        pen.line((1, y, 14, y), fill="#e3ba74")
    pen.polygon(((8, 3), (12, 8), (8, 12), (3, 8)), fill="#f0dfaf", outline="#274f73")
    pen.ellipse((6, 6, 9, 9), fill="#d78956")
    mosaic.save(TARGET / "forum_mosaic.png")

    story = plaster.copy()
    pen = ImageDraw.Draw(story)
    pen.rectangle((2, 2, 13, 13), fill="#f7e9c4", outline="#765c53")
    pen.polygon(((3, 5), (7, 4), (8, 6), (8, 12), (3, 11)), fill="#f9dfa8", outline="#865a48")
    pen.polygon(((8, 6), (12, 4), (13, 5), (13, 11), (8, 12)), fill="#f9dfa8", outline="#865a48")
    pen.line((4, 7, 6, 6), fill="#326989")
    pen.line((9, 8, 11, 6), fill="#326989")
    story.save(TARGET / "fam_story_clue.png")

    school = mosaic.copy()
    pen = ImageDraw.Draw(school)
    pen.rectangle((2, 2, 13, 13), fill="#f7e9c4", outline="#765c53")
    pen.line((4, 11, 7, 8, 9, 9, 12, 5), fill="#315d7e", width=2)
    pen.ellipse((3, 10, 5, 12), fill="#d68553")
    pen.ellipse((11, 4, 13, 6), fill="#d68553")
    school.save(TARGET / "fam_school_clue.png")

    forum_board = plaster.copy()
    pen = ImageDraw.Draw(forum_board)
    pen.rectangle((1, 1, 14, 14), fill="#294b69", outline="#e8c888")
    for x, y in ((4, 5), (12, 5), (8, 11)):
        pen.ellipse((x - 2, y - 2, x + 2, y + 2), fill="#f1dfb6", outline="#d79258")
    pen.line((5, 6, 8, 9, 11, 6), fill="#80b4bd", width=2)
    forum_board.save(TARGET / "fam_forum_board.png")

    for name, accent, glyph in (("fam_dispatch_story", "#e6bc73", "book"),
                                ("fam_dispatch_school", "#8ccbd1", "path")):
        stop = mosaic.copy()
        pen = ImageDraw.Draw(stop)
        pen.rectangle((2, 2, 13, 13), fill="#294b69", outline=accent, width=2)
        if glyph == "book":
            pen.polygon(((4, 5), (7, 4), (8, 6), (8, 11), (4, 10)),
                        fill="#f2e3bd", outline="#d58b62")
            pen.polygon(((8, 6), (11, 4), (12, 5), (12, 10), (8, 11)),
                        fill="#f2e3bd", outline="#d58b62")
        else:
            pen.line((4, 11, 7, 8, 9, 9, 12, 5), fill="#f2e3bd", width=2)
            pen.ellipse((3, 10, 5, 12), fill=accent)
            pen.ellipse((11, 4, 13, 6), fill=accent)
        stop.save(TARGET / f"{name}.png")

    welcome = mosaic.copy()
    pen = ImageDraw.Draw(welcome)
    pen.rectangle((1, 1, 14, 14), fill="#294b69", outline="#e8c888")
    pen.polygon(((8, 2), (12, 7), (8, 13), (4, 7)), fill="#f1dfb6", outline="#d79258")
    pen.line((6, 9, 8, 5, 10, 9), fill="#315d7e", width=2)
    welcome.save(TARGET / "townhall_welcome_board.png")

    # Adl's measured stone uses a new keyed edge and alignment marks rather
    # than the source brick rhythm. The second face is a true floor compass.
    survey = recolor("marble_bricks.png", ("#434c50", "#68777b", "#9aaba8", "#d2d4bf"))
    pen = ImageDraw.Draw(survey)
    pen.rectangle((0, 0, 15, 15), outline="#314752")
    pen.line((2, 4, 13, 4), fill="#e5d5a8", width=2)
    pen.line((2, 11, 13, 11), fill="#4b6b73", width=2)
    for x in (4, 8, 12):
        pen.line((x, 2, x, 5), fill="#2f4755")
        pen.line((x, 10, x, 13), fill="#d7c799")
    pen.ellipse((7, 7, 9, 9), fill="#d39662")
    survey.save(TARGET / "survey_stone.png")

    compass = recolor("slate_tiles.png", ("#1d3945", "#365b66", "#60848a", "#9cad9f"))
    pen = ImageDraw.Draw(compass)
    pen.rectangle((0, 0, 15, 15), outline="#dbc08b")
    pen.polygon(((8, 1), (10, 6), (14, 8), (10, 10), (8, 14),
                 (6, 10), (1, 8), (6, 6)), fill="#e2d6ae", outline="#294c5a")
    pen.polygon(((8, 2), (9, 7), (8, 8), (7, 7)), fill="#c37b54")
    pen.polygon(((14, 8), (9, 7), (8, 8), (9, 9)), fill="#8bb9b5")
    pen.ellipse((6, 6, 9, 9), fill="#283f4e", outline="#e6d6a9")
    compass.save(TARGET / "compass_mosaic.png")

    east = survey.copy()
    pen = ImageDraw.Draw(east)
    pen.rectangle((2, 2, 13, 13), fill="#183c4a", outline="#e6d09b")
    pen.line((11, 11, 4, 4), fill="#8fd2d3", width=2)
    pen.polygon(((3, 3), (8, 3), (3, 8)), fill="#e8b66e")
    east.save(TARGET / "adl_east_sight.png")

    west = survey.copy()
    pen = ImageDraw.Draw(west)
    pen.rectangle((2, 2, 13, 13), fill="#183c4a", outline="#e6d09b")
    pen.line((4, 11, 11, 4), fill="#8fd2d3", width=2)
    pen.polygon(((12, 3), (12, 8), (7, 3)), fill="#e8b66e")
    west.save(TARGET / "adl_west_chart.png")

    table = compass.copy()
    pen = ImageDraw.Draw(table)
    pen.rectangle((2, 2, 13, 13), fill="#183c4a", outline="#e6d09b")
    pen.line((3, 11, 10, 4), fill="#8fd2d3", width=2)
    pen.line((12, 11, 5, 4), fill="#d8a36e", width=2)
    pen.ellipse((6, 6, 9, 9), fill="#f1e1b4")
    table.save(TARGET / "adl_map_table.png")

    cairn = survey.copy()
    pen = ImageDraw.Draw(cairn)
    pen.polygon(((8, 1), (14, 8), (8, 14), (1, 8)), fill="#203f4c", outline="#ead3a0")
    pen.line((8, 3, 8, 12), fill="#e8c184", width=2)
    pen.line((3, 8, 12, 8), fill="#8fc5c4", width=2)
    pen.ellipse((6, 6, 9, 9), fill="#f0dfab")
    cairn.save(TARGET / "adl_cairn.png")

    pin_board = compass.copy()
    pen = ImageDraw.Draw(pin_board)
    pen.rectangle((2, 2, 13, 13), fill="#183c4a", outline="#e6d09b")
    pen.ellipse((5, 3, 10, 8), fill="#d89061", outline="#f4dfb0")
    pen.polygon(((6, 8), (9, 8), (8, 13)), fill="#e9bb80")
    pin_board.save(TARGET / "townhall_atlas_pin.png")

    # Othman's public library uses pale cut ashlar and dark blue floor inlay.
    # New joints, carved catalog marks and brass lines replace the source
    # masonry rhythm while retaining its checked CC0 pixel grain.
    archive = recolor("marble_bricks.png", ("#5a626a", "#89929a", "#bdc5c6", "#e2ded0"))
    pen = ImageDraw.Draw(archive)
    pen.rectangle((0, 0, 15, 15), outline="#344e61")
    pen.line((0, 5, 15, 5), fill="#d8c69b", width=2)
    pen.line((0, 11, 15, 11), fill="#526b7a", width=2)
    for x in (4, 11):
        pen.line((x, 1, x, 4), fill="#344e61")
    for x in (7, 13):
        pen.line((x, 12, x, 14), fill="#344e61")
    pen.polygon(((6, 7), (8, 6), (10, 7), (10, 9), (8, 10), (6, 9)),
                fill="#2b526b", outline="#d4b879")
    archive.save(TARGET / "archive_limestone.png")

    inlay = recolor("slate_tiles.png", ("#172b3d", "#24435c", "#366078", "#587b89"))
    pen = ImageDraw.Draw(inlay)
    pen.rectangle((0, 0, 15, 15), outline="#d3b67f")
    pen.line((8, 1, 8, 14), fill="#ad996e")
    pen.line((1, 8, 14, 8), fill="#ad996e")
    pen.polygon(((3, 4), (7, 3), (8, 5), (9, 3), (13, 4),
                 (13, 12), (8, 11), (3, 12)), fill="#e7d8aa", outline="#2a4258")
    pen.line((8, 5, 8, 11), fill="#41647b")
    inlay.save(TARGET / "archive_inlay.png")

    catalog = archive.copy()
    pen = ImageDraw.Draw(catalog)
    pen.rectangle((2, 2, 13, 13), fill="#183a51", outline="#e2c990")
    for x, color in ((4, "#ddae74"), (7, "#93bfc1"), (10, "#e8d8ad")):
        pen.rectangle((x, 4, x + 2, 11), fill=color, outline="#263f52")
    catalog.save(TARGET / "othman_public_catalog.png")

    private = inlay.copy()
    pen = ImageDraw.Draw(private)
    pen.rectangle((2, 2, 13, 13), fill="#e8d9ac", outline="#d3b67f")
    pen.polygon(((5, 4), (11, 3), (12, 11), (5, 12)), fill="#294b61", outline="#8d6c54")
    pen.line((7, 6, 10, 5), fill="#e8d9ac")
    pen.rectangle((4, 6, 5, 10), fill="#bd8b61")
    private.save(TARGET / "othman_private_note.png")

    publish = inlay.copy()
    pen = ImageDraw.Draw(publish)
    pen.rectangle((2, 2, 13, 13), fill="#17364c", outline="#e2c990")
    pen.polygon(((3, 5), (7, 4), (8, 6), (8, 12), (3, 11)), fill="#f1dfb6")
    pen.polygon(((8, 6), (12, 4), (13, 5), (13, 11), (8, 12)), fill="#f1dfb6")
    pen.line((8, 2, 8, 5), fill="#8bc1c9", width=2)
    publish.save(TARGET / "othman_publish_desk.png")

    keep = archive.copy()
    pen = ImageDraw.Draw(keep)
    pen.rectangle((2, 2, 13, 13), fill="#17364c", outline="#e2c990")
    pen.polygon(((4, 4), (11, 3), (12, 12), (4, 13)), fill="#d8bd89", outline="#ecddb2")
    pen.rectangle((7, 7, 9, 10), fill="#20475d", outline="#f1dfb6")
    keep.save(TARGET / "othman_keep_desk.png")

    shelf = inlay.copy()
    pen = ImageDraw.Draw(shelf)
    pen.rectangle((2, 2, 13, 13), fill="#17364c", outline="#e2c990")
    for x, color in ((4, "#d9a67b"), (7, "#91c6c7"), (10, "#e9d4a0")):
        pen.rectangle((x, 4, x + 2, 10), fill=color)
    pen.line((3, 11, 12, 11), fill="#e2c990", width=2)
    shelf.save(TARGET / "townhall_knowledge_shelf.png")

    # Yehia's turf construction uses the preserved CC0 grain but replaces the
    # original brick courses with layered sod and a carved story emblem.
    turf = recolor("marble_bricks.png", ("#314335", "#4b6241", "#71905c", "#a8b97a"))
    pen = ImageDraw.Draw(turf)
    for y in (4, 9, 14):
        pen.line((0, y, 15, y), fill="#283e32", width=2)
    for x, y in ((4, 0), (12, 5), (7, 10)):
        pen.line((x, y, x, y + 3), fill="#d1c58b")
    pen.arc((5, 5, 11, 12), 180, 360, fill="#d9c988", width=2)
    pen.rectangle((7, 9, 9, 12), fill="#745e47")
    turf.save(TARGET / "storybook_turf.png")

    path = recolor("slate_tiles.png", ("#4a4b39", "#747151", "#a99b69", "#dbc68d"))
    pen = ImageDraw.Draw(path)
    pen.polygon(((1, 8), (5, 2), (11, 2), (15, 8), (11, 14), (5, 14)),
                fill="#d0b679", outline="#625c43")
    pen.line((3, 8, 13, 8), fill="#f0dca4")
    pen.line((8, 3, 8, 13), fill="#836e51")
    path.save(TARGET / "storybook_path.png")

    truth = path.copy()
    pen = ImageDraw.Draw(truth)
    pen.rectangle((2, 2, 13, 13), fill="#45654a", outline="#e6d19c")
    pen.line((3, 11, 7, 7, 10, 9, 13, 4), fill="#f0dfaa", width=2)
    pen.ellipse((10, 2, 13, 5), fill="#e3b957")
    truth.save(TARGET / "yehia_truth_clue.png")

    prop = turf.copy()
    pen = ImageDraw.Draw(prop)
    pen.rectangle((3, 2, 12, 13), fill="#743e3b", outline="#f0d8a2")
    pen.rectangle((5, 4, 10, 12), fill="#a4514b", outline="#2c3d32")
    pen.ellipse((8, 8, 9, 9), fill="#efd39b")
    pen.line((3, 2, 12, 13), fill="#f5dfaa")
    prop.save(TARGET / "yehia_prop_clue.png")

    story = path.copy()
    pen = ImageDraw.Draw(story)
    pen.rectangle((2, 2, 13, 13), fill="#365748", outline="#e6d19c")
    pen.polygon(((3, 5), (7, 4), (8, 6), (8, 12), (3, 11)), fill="#f3e2b4")
    pen.polygon(((8, 6), (12, 4), (13, 5), (13, 11), (8, 12)), fill="#f3e2b4")
    pen.line((8, 5, 8, 12), fill="#6f9d6c")
    story.save(TARGET / "yehia_story_table.png")

    for name, motif in (("mushroom", "#dc9673"), ("lantern", "#eaca75"),
                        ("tree", "#8fc18b")):
        marker = path.copy()
        pen = ImageDraw.Draw(marker)
        pen.rectangle((2, 2, 13, 13), fill="#365748", outline="#e6d19c")
        if name == "mushroom":
            pen.ellipse((4, 4, 11, 8), fill=motif, outline="#f5dcba")
            pen.rectangle((7, 8, 8, 12), fill="#e9d8b0")
        elif name == "lantern":
            pen.rectangle((5, 5, 10, 11), fill=motif, outline="#f5dcba")
            pen.line((7, 3, 8, 3), fill="#f5dcba", width=2)
        else:
            pen.polygon(((8, 3), (3, 9), (12, 9)), fill=motif)
            pen.rectangle((7, 9, 9, 12), fill="#b58b5e")
        marker.save(TARGET / f"yehia_{name}_mark.png")

    trail = story.copy()
    pen = ImageDraw.Draw(trail)
    pen.ellipse((3, 3, 12, 12), outline="#efcd8e", width=2)
    pen.line((4, 10, 7, 7, 9, 8, 12, 4), fill="#94c795", width=2)
    trail.save(TARGET / "townhall_story_trail.png")

    ITEM_TARGET.mkdir(parents=True, exist_ok=True)
    thread = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    mask = Image.new("L", (16, 16), 0)
    mask_pen = ImageDraw.Draw(mask)
    mask_pen.ellipse((3, 3, 12, 12), outline=255, width=3)
    thread.paste(path, (0, 0), mask)
    pen = ImageDraw.Draw(thread)
    pen.arc((3, 3, 12, 12), 20, 300, fill="#f0d78d", width=2)
    pen.line((9, 9, 14, 3), fill="#82bd93", width=2)
    pen.ellipse((12, 2, 15, 5), fill="#d8eee1", outline="#5f9a78")
    thread.save(ITEM_TARGET / "story_thread.png")

    # Omar's warm clay towers use new staggered mud courses and white inset
    # corners; blue-green court tiles mark what is deliberately out of place.
    clay = recolor("marble_bricks.png", ("#68453b", "#98624c", "#c58a5d", "#e3bb83"))
    pen = ImageDraw.Draw(clay)
    for y in (0, 5, 10, 15):
        pen.line((0, y, 15, y), fill="#684535")
    for x, y in ((4, 1), (11, 6), (5, 11)):
        pen.line((x, y, x, y + 3), fill="#f0d9aa")
    pen.rectangle((1, 1, 3, 4), fill="#efe1bc")
    pen.rectangle((13, 11, 15, 14), fill="#efe1bc")
    clay.save(TARGET / "lost_clay.png")

    lost_tile = recolor("slate_tiles.png", ("#173b48", "#2d6872", "#4f9290", "#8ab4a1"))
    pen = ImageDraw.Draw(lost_tile)
    pen.rectangle((0, 0, 15, 15), outline="#e8cf91")
    pen.line((0, 8, 15, 8), fill="#c5b981")
    pen.line((8, 0, 8, 15), fill="#c5b981")
    pen.polygon(((4, 4), (8, 2), (12, 4), (10, 7), (6, 7)),
                fill="#dfc48e", outline="#1d5862")
    pen.rectangle((7, 7, 9, 9), fill="#173b48")
    lost_tile.save(TARGET / "lost_tile.png")

    receipt = clay.copy()
    pen = ImageDraw.Draw(receipt)
    pen.rectangle((3, 2, 12, 13), fill="#ecd8a8", outline="#315d65")
    for y in (5, 8, 11):
        pen.line((5, y, 10, y), fill="#9a684e")
    pen.ellipse((9, 9, 12, 12), fill="#d9a464", outline="#65473b")
    receipt.save(TARGET / "omar_clock_receipt.png")

    record = clay.copy()
    pen = ImageDraw.Draw(record)
    pen.rectangle((2, 2, 13, 13), fill="#274e5a", outline="#ecd8a8")
    pen.polygon(((4, 4), (7, 4), (8, 6), (8, 11), (4, 10)), fill="#ecd8a8")
    pen.polygon(((8, 6), (12, 4), (12, 10), (8, 11)), fill="#ecd8a8")
    pen.line((8, 6, 8, 11), fill="#a77955")
    record.save(TARGET / "omar_book_record.png")

    for name in ("compass", "clock", "book"):
        choice = lost_tile.copy()
        pen = ImageDraw.Draw(choice)
        pen.rectangle((2, 2, 13, 13), fill="#174654", outline="#f0d59a")
        if name == "compass":
            pen.ellipse((4, 4, 11, 11), outline="#e8c57f", width=2)
            pen.polygon(((8, 3), (10, 8), (8, 12), (6, 8)), fill="#d7755d")
        elif name == "clock":
            pen.ellipse((4, 4, 11, 11), fill="#d9bc7f", outline="#f0d59a")
            pen.line((8, 5, 8, 8, 10, 9), fill="#305664", width=1)
        else:
            pen.polygon(((4, 5), (7, 4), (8, 6), (8, 12), (4, 11)), fill="#e8d5a3")
            pen.polygon(((8, 6), (12, 4), (12, 11), (8, 12)), fill="#e8d5a3")
        choice.save(TARGET / f"omar_{name}_choice.png")

    lost_found = lost_tile.copy()
    pen = ImageDraw.Draw(lost_found)
    pen.rectangle((2, 2, 13, 13), fill="#174654", outline="#f0d59a")
    pen.polygon(((4, 6), (7, 3), (10, 6), (7, 9)), fill="#d9bc7f")
    pen.line((7, 9, 7, 12, 11, 12), fill="#f0d59a", width=2)
    pen.ellipse((10, 10, 12, 12), fill="#d7755d")
    lost_found.save(TARGET / "townhall_lost_found.png")

    # Soryal's old masonry keeps a broken joint rhythm; new copper and glass
    # triangles visibly bridge the gaps rather than recoloring the same block.
    patch_brick = recolor("marble_bricks.png", ("#4e4545", "#76625b", "#a37765", "#c19a78"))
    pen = ImageDraw.Draw(patch_brick)
    for y in (0, 5, 10, 15):
        pen.line((0, y, 15, y), fill="#514c4a")
    pen.line((3, 5, 6, 8, 6, 10), fill="#d2a875", width=2)
    pen.line((10, 1, 10, 4, 13, 5), fill="#648e92", width=2)
    patch_brick.save(TARGET / "patchwork_brick.png")

    patch_mosaic = recolor("slate_tiles.png", ("#263d44", "#456873", "#759691", "#acc5a7"))
    pen = ImageDraw.Draw(patch_mosaic)
    pen.rectangle((1, 1, 14, 14), outline="#d8b87f")
    pen.polygon(((2, 12), (8, 2), (8, 12)), fill="#a36550")
    pen.polygon(((8, 2), (14, 12), (8, 12)), fill="#5d9fa6")
    pen.line((2, 12, 14, 12), fill="#d8b87f", width=2)
    patch_mosaic.save(TARGET / "patchwork_mosaic.png")

    scrap_clue = patch_brick.copy()
    pen = ImageDraw.Draw(scrap_clue)
    pen.rectangle((2, 2, 13, 13), fill="#263d44", outline="#e6c890")
    pen.polygon(((4, 4), (10, 3), (12, 6), (10, 11), (5, 12)),
                fill="#b87358", outline="#e2b782")
    pen.line((5, 9, 10, 5), fill="#f1d29b", width=2)
    pen.polygon(((9, 8), (12, 7), (12, 11)), fill="#70afaf")
    scrap_clue.save(TARGET / "soryal_scrap_clue.png")

    loft_clue = patch_brick.copy()
    pen = ImageDraw.Draw(loft_clue)
    pen.rectangle((2, 2, 13, 13), fill="#263d44", outline="#e6c890")
    pen.polygon(((3, 5), (7, 4), (8, 6), (8, 12), (3, 11)), fill="#e6d5ad")
    pen.polygon(((8, 6), (12, 4), (13, 5), (13, 11), (8, 12)), fill="#e6d5ad")
    pen.line((8, 6, 8, 12), fill="#8e6a59")
    pen.line((9, 8, 11, 8), fill="#70afaf")
    pen.line((9, 10, 11, 10), fill="#a36755")
    loft_clue.save(TARGET / "soryal_loft_clue.png")

    for name, fill in (("copper", "#c17b59"), ("glass", "#7ac3c8"),
                       ("brick", "#9d6657")):
        mark = patch_mosaic.copy()
        pen = ImageDraw.Draw(mark)
        pen.rectangle((2, 2, 13, 13), fill="#243f48", outline="#e6c890")
        if name == "glass":
            pen.polygon(((8, 3), (12, 8), (8, 13), (4, 8)), fill=fill,
                        outline="#d8eeee")
        elif name == "copper":
            pen.polygon(((4, 11), (8, 3), (12, 11)), fill=fill,
                        outline="#edca8c")
        else:
            pen.rectangle((4, 5, 11, 11), fill=fill, outline="#d2a989")
            pen.line((4, 8, 11, 8), fill="#584747")
        mark.save(TARGET / f"soryal_{name}_mark.png")

    repair_bench = patch_mosaic.copy()
    pen = ImageDraw.Draw(repair_bench)
    pen.rectangle((3, 8, 12, 11), fill="#b47b59", outline="#eacb8d")
    pen.rectangle((4, 12, 5, 14), fill="#745a4d")
    pen.rectangle((10, 12, 11, 14), fill="#745a4d")
    pen.line((5, 7, 10, 2), fill="#86c1bd", width=2)
    pen.ellipse((9, 1, 12, 4), fill="#d9b47d")
    repair_bench.save(TARGET / "townhall_repair_bench.png")

    # Redline aid markers use the same warm masonry as the shelter network.
    for name, arrow in (("redline_aid_start", "#f3d68d"),
                        ("redline_aid_finish", "#8ed0ad")):
        aid = patch_mosaic.copy()
        pen = ImageDraw.Draw(aid)
        pen.rectangle((2, 2, 13, 13), fill="#5f3538", outline="#efc589", width=2)
        pen.rectangle((4, 5, 11, 11), fill="#ead7b2", outline="#7b4d45")
        pen.line((4, 7, 11, 7), fill="#b65d55")
        pen.polygon(((5, 3), (11, 3), (8, 6)), fill=arrow)
        aid.save(TARGET / f"{name}.png")

    # Route repairs have visibly different broken and mended silhouettes.
    yard_fitting = patch_brick.copy()
    pen = ImageDraw.Draw(yard_fitting)
    pen.polygon(((2, 3), (11, 2), (13, 12), (4, 13)), fill="#252e35", outline="#e3bb89")
    pen.line((2, 4, 7, 7, 5, 11), fill="#d49a79", width=2)
    yard_fitting.save(TARGET / "soryal_yard_fitting.png")
    yard_restored = patch_brick.copy()
    pen = ImageDraw.Draw(yard_restored)
    pen.rectangle((2, 3, 13, 12), fill="#aa745d", outline="#f1c995", width=2)
    pen.line((3, 8, 12, 8), fill="#5b5049", width=2)
    pen.line((8, 4, 8, 7), fill="#5b5049")
    pen.line((5, 9, 5, 11), fill="#5b5049")
    yard_restored.save(TARGET / "soryal_yard_restored.png")

    bridge_fitting = patch_mosaic.copy()
    pen = ImageDraw.Draw(bridge_fitting)
    pen.rectangle((2, 2, 13, 13), fill="#273840", outline="#e3bb89")
    pen.line((3, 4, 7, 8), fill="#bd7857", width=3)
    pen.line((10, 9, 13, 12), fill="#bd7857", width=3)
    bridge_fitting.save(TARGET / "soryal_bridge_fitting.png")
    bridge_restored = patch_mosaic.copy()
    pen = ImageDraw.Draw(bridge_restored)
    pen.rectangle((2, 2, 13, 13), fill="#273840", outline="#e3bb89")
    pen.line((3, 4, 12, 12), fill="#d59469", width=3)
    pen.ellipse((6, 6, 9, 9), fill="#f2d2a3", outline="#714f48")
    bridge_restored.save(TARGET / "soryal_bridge_restored.png")

    loft_fitting = patch_brick.copy()
    pen = ImageDraw.Draw(loft_fitting)
    pen.rectangle((2, 2, 13, 13), fill="#1d343b", outline="#c29b72", width=2)
    pen.polygon(((4, 4), (11, 4), (8, 8), (5, 12)), fill="#4c7f89")
    pen.line((9, 4, 6, 8, 10, 12), fill="#e0d3b3", width=2)
    loft_fitting.save(TARGET / "soryal_loft_fitting.png")
    loft_restored = patch_brick.copy()
    pen = ImageDraw.Draw(loft_restored)
    pen.rectangle((2, 2, 13, 13), fill="#8cc4c3", outline="#e5d7ae", width=2)
    pen.line((8, 3, 8, 12), fill="#466b75", width=2)
    pen.line((3, 8, 12, 8), fill="#466b75", width=2)
    loft_restored.save(TARGET / "soryal_loft_restored.png")

    # Ali's fractured limestone changes the CC0 brick courses into offset
    # stone faces with a blue seam. The route inlay is a different drawn grid.
    splitstone = recolor("marble_bricks.png", ("#425565", "#728b96", "#a7b9b8", "#d5d9c8"))
    pen = ImageDraw.Draw(splitstone)
    pen.line((0, 3, 5, 3, 7, 6, 13, 6, 15, 9), fill="#3c5969", width=2)
    pen.line((3, 15, 5, 11, 10, 10, 12, 7), fill="#dce5d8")
    pen.line((6, 5, 9, 8, 12, 8), fill="#6cb1bb")
    splitstone.save(TARGET / "splitstone.png")

    waymark = recolor("slate_tiles.png", ("#253e52", "#466779", "#729aa5", "#b6d4cd"))
    pen = ImageDraw.Draw(waymark)
    pen.rectangle((1, 1, 14, 14), outline="#dce1c4")
    pen.line((2, 12, 6, 8, 10, 8, 14, 4), fill="#dfc690", width=2)
    pen.polygon(((11, 3), (14, 4), (13, 7)), fill="#dfc690")
    pen.line((3, 4, 6, 4, 8, 7), fill="#9bd0cf", width=2)
    waymark.save(TARGET / "waymark_tile.png")

    high_map = waymark.copy()
    pen = ImageDraw.Draw(high_map)
    pen.rectangle((2, 2, 13, 13), fill="#e2dbba", outline="#334f62")
    pen.line((5, 11, 5, 5, 8, 5, 11, 8), fill="#3b6475", width=2)
    pen.rectangle((4, 2, 6, 4), fill="#6ab5c2")
    pen.rectangle((11, 7, 13, 9), fill="#6ab5c2")
    high_map.save(TARGET / "ali_high_map.png")

    rubbing = splitstone.copy()
    pen = ImageDraw.Draw(rubbing)
    pen.rectangle((2, 2, 13, 13), fill="#dccba8", outline="#355567")
    pen.arc((4, 4, 12, 13), 180, 355, fill="#345c70", width=2)
    pen.line((4, 8, 4, 12, 12, 12, 12, 8), fill="#345c70", width=2)
    pen.rectangle((2, 8, 5, 10), fill="#7aafba")
    rubbing.save(TARGET / "ali_gate_rubbing.png")

    table = waymark.copy()
    pen = ImageDraw.Draw(table)
    pen.rectangle((2, 3, 13, 12), fill="#d8d6b9", outline="#e9c887")
    pen.line((4, 5, 11, 5, 11, 10, 4, 10, 4, 5), fill="#4c7887")
    for x, y in ((8, 3), (12, 7), (2, 7)):
        pen.rectangle((x, y, x + 2, y + 2), fill="#69abb7")
    table.save(TARGET / "ali_map_table.png")

    for name in ("north", "east", "west"):
        door = waymark.copy()
        pen = ImageDraw.Draw(door)
        pen.arc((4, 4, 12, 13), 180, 360, fill="#e7d6a8", width=2)
        pen.line((4, 8, 4, 12, 12, 12, 12, 8), fill="#e7d6a8", width=2)
        ink = "#75d1d9" if name == "west" else "#e7be86"
        if name == "north":
            pen.polygon(((8, 1), (4, 5), (12, 5)), fill=ink)
            pen.line((8, 5, 8, 8), fill=ink, width=2)
        elif name == "east":
            pen.polygon(((15, 8), (11, 4), (11, 12)), fill=ink)
            pen.line((8, 8, 12, 8), fill=ink, width=2)
        else:
            pen.polygon(((1, 8), (5, 4), (5, 12)), fill=ink)
            pen.line((4, 8, 8, 8), fill=ink, width=2)
        door.save(TARGET / f"ali_{name}_door.png")

    board = splitstone.copy()
    pen = ImageDraw.Draw(board)
    pen.rectangle((2, 2, 13, 13), fill="#294858", outline="#e7d6a8")
    pen.ellipse((3, 5, 7, 9), outline="#91c9ce", width=2)
    pen.ellipse((9, 5, 13, 9), outline="#91c9ce", width=2)
    pen.line((6, 7, 10, 7), fill="#e9ca88", width=2)
    board.save(TARGET / "townhall_shortcut_board.png")

    for name, ink, upper in (("ali_release_high", "#e8d99e", True),
                             ("ali_release_low", "#78c5d0", False)):
        anchor = waymark.copy()
        pen = ImageDraw.Draw(anchor)
        pen.rectangle((2, 2, 13, 13), fill="#294858", outline=ink, width=2)
        pen.arc((4, 4, 12, 13), 180, 355, fill="#e8d99e", width=2)
        pen.line((4, 8, 4, 12, 12, 12, 12, 8), fill="#e8d99e", width=2)
        if upper:
            pen.polygon(((8, 2), (5, 6), (11, 6)), fill=ink)
        else:
            pen.polygon(((8, 14), (5, 10), (11, 10)), fill=ink)
        anchor.save(TARGET / f"{name}.png")

    council = recolor("marble_bricks.png", ("#263b4b", "#4d6b77", "#8a9e9b", "#d5d7bf"))
    pen = ImageDraw.Draw(council)
    pen.rectangle((1, 1, 14, 14), fill="#263e50", outline="#d7bd83")
    pen.ellipse((5, 5, 10, 10), fill="#dfd6b8", outline="#88b1b5")
    pen.line((8, 5, 8, 2), fill="#d8b57c", width=2)
    pen.line((10, 9, 13, 12), fill="#b98066", width=2)
    pen.line((6, 9, 3, 12), fill="#7ac1bb", width=2)
    for x, y, color in ((7, 1, "#d8b57c"), (12, 11, "#b98066"),
                        (2, 11, "#7ac1bb")):
        pen.rectangle((x, y, x + 2, y + 2), fill=color)
    council.save(TARGET / "townhall_story_council.png")

    chalk = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pen = ImageDraw.Draw(chalk)
    pen.polygon(((4, 11), (8, 2), (12, 4), (8, 13)),
                fill="#cbe7e1", outline="#4f8591")
    pen.line((5, 10, 8, 3, 10, 4), fill="#f0e5c7", width=2)
    pen.rectangle((5, 10, 8, 13), fill="#7fc5cb")
    chalk.save(ITEM_TARGET / "shortcut_chalk.png")

    dispatch = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pen = ImageDraw.Draw(dispatch)
    pen.polygon(((2, 5), (11, 2), (14, 10), (5, 13)),
                fill="#f2e3bd", outline="#765c53")
    pen.line((3, 5, 8, 9, 13, 10), fill="#bc956b")
    pen.line((11, 3, 8, 9, 5, 12), fill="#bc956b")
    pen.ellipse((7, 7, 10, 10), fill="#d57d56", outline="#784d54")
    dispatch.save(ITEM_TARGET / "forum_dispatch.png")

    # Omar's physical claim ticket pairs a clay-red receipt with a teal trail.
    # Both shapes are drawn here; no generated-image model or external art is used.
    recovery = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pen = ImageDraw.Draw(recovery)
    pen.polygon(((3, 2), (10, 2), (13, 5), (13, 14), (3, 14)),
                fill="#edd1a2", outline="#7d4d40")
    pen.line((10, 2, 10, 5, 13, 5), fill="#be8061")
    pen.line((5, 6, 8, 6), fill="#bb916c")
    pen.line((5, 8, 7, 8), fill="#bb916c")
    pen.line((5, 11, 7, 11, 9, 9, 11, 11), fill="#2d817c", width=2)
    pen.ellipse((4, 10, 6, 12), fill="#d88352")
    recovery.save(ITEM_TARGET / "recovery_slip.png")

    # The buoy keeps the CC0-derived wave grain inside a new floating hull.
    # Copper braces and an amber signal lamp make its shape readable at 16px.
    buoy = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    hull_mask = Image.new("L", (16, 16), 0)
    mask_pen = ImageDraw.Draw(hull_mask)
    mask_pen.polygon(((3, 8), (5, 5), (10, 5), (12, 8), (10, 13), (5, 13)), fill=255)
    buoy.paste(wave, (0, 0), hull_mask)
    pen = ImageDraw.Draw(buoy)
    pen.line((3, 8, 5, 5, 10, 5, 12, 8, 10, 13, 5, 13, 3, 8),
             fill="#e4c38e", width=1)
    pen.line((5, 9, 10, 9), fill="#f0e4bf", width=2)
    pen.rectangle((7, 3, 8, 6), fill="#875b44")
    pen.rectangle((6, 1, 9, 3), fill="#efbc70", outline="#f8e7b8")
    pen.line((6, 14, 9, 14), fill="#6b5548")
    buoy.save(ITEM_TARGET / "signal_buoy.png")

    pin = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    grain = compass.copy()
    mask = Image.new("L", (16, 16), 0)
    mask_pen = ImageDraw.Draw(mask)
    mask_pen.polygon(((4, 3), (11, 3), (12, 7), (8, 15), (4, 7)), fill=255)
    pin.paste(grain, (0, 0), mask)
    pen = ImageDraw.Draw(pin)
    pen.ellipse((4, 1, 11, 8), fill="#bd7655", outline="#f1d59d")
    pen.ellipse((6, 3, 9, 6), fill="#244c59")
    pen.polygon(((5, 8), (10, 8), (8, 15)), fill="#e4b77c", outline="#764f45")
    pin.save(ITEM_TARGET / "atlas_pin.png")

    assets = ROOT / "src/main/resources/assets/gems"
    for name in ("archive_limestone", "archive_inlay", "othman_public_catalog",
                 "othman_private_note", "othman_publish_desk", "othman_keep_desk",
                 "townhall_knowledge_shelf"):
        blockstate = assets / "blockstates" / f"{name}.json"
        model = assets / "models/block" / f"{name}.json"
        blockstate.parent.mkdir(parents=True, exist_ok=True)
        model.parent.mkdir(parents=True, exist_ok=True)
        blockstate.write_text(json.dumps({"variants": {"": {"model": f"gems:block/{name}"}}}) + "\n")
        model.write_text(json.dumps({"parent": "minecraft:block/cube_all",
                                     "textures": {"all": f"gems:block/{name}"}}) + "\n")
    for name in ("archive_limestone", "archive_inlay"):
        item = assets / "items" / f"{name}.json"
        item.write_text(json.dumps({"model": {"type": "minecraft:model",
                                               "model": f"gems:block/{name}"}}) + "\n")

    for name in ("storybook_turf", "storybook_path", "yehia_truth_clue",
                 "yehia_prop_clue", "yehia_story_table", "yehia_mushroom_mark",
                 "yehia_lantern_mark", "yehia_tree_mark", "townhall_story_trail"):
        blockstate = assets / "blockstates" / f"{name}.json"
        model = assets / "models/block" / f"{name}.json"
        blockstate.write_text(json.dumps({"variants": {"": {"model": f"gems:block/{name}"}}}) + "\n")
        model.write_text(json.dumps({"parent": "minecraft:block/cube_all",
                                     "textures": {"all": f"gems:block/{name}"}}) + "\n")
    for name in ("storybook_turf", "storybook_path"):
        item = assets / "items" / f"{name}.json"
        item.write_text(json.dumps({"model": {"type": "minecraft:model",
                                               "model": f"gems:block/{name}"}}) + "\n")
    (assets / "items/story_thread.json").write_text(json.dumps({
        "model": {"type": "minecraft:model", "model": "gems:item/story_thread"}
    }) + "\n")
    (assets / "models/item/story_thread.json").write_text(json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "gems:item/story_thread"}
    }) + "\n")
    for name in ("lost_clay", "lost_tile", "omar_clock_receipt",
                 "omar_book_record", "omar_compass_choice", "omar_clock_choice",
                 "omar_book_choice", "townhall_lost_found"):
        blockstate = assets / "blockstates" / f"{name}.json"
        model = assets / "models/block" / f"{name}.json"
        blockstate.write_text(json.dumps({"variants": {"": {"model": f"gems:block/{name}"}}}) + "\n")
        model.write_text(json.dumps({"parent": "minecraft:block/cube_all",
                                     "textures": {"all": f"gems:block/{name}"}}) + "\n")
    for name in ("lost_clay", "lost_tile"):
        (assets / "items" / f"{name}.json").write_text(json.dumps({
            "model": {"type": "minecraft:model", "model": f"gems:block/{name}"}
        }) + "\n")
    for name in ("patchwork_brick", "patchwork_mosaic", "soryal_scrap_clue",
                 "soryal_loft_clue", "soryal_copper_mark", "soryal_glass_mark",
                 "soryal_brick_mark", "townhall_repair_bench",
                 "soryal_yard_fitting", "soryal_bridge_fitting", "soryal_loft_fitting",
                 "soryal_yard_restored", "soryal_bridge_restored", "soryal_loft_restored",
                 "redline_aid_start", "redline_aid_finish"):
        blockstate = assets / "blockstates" / f"{name}.json"
        model = assets / "models/block" / f"{name}.json"
        blockstate.write_text(json.dumps({"variants": {"": {"model": f"gems:block/{name}"}}}) + "\n")
        model.write_text(json.dumps({"parent": "minecraft:block/cube_all",
                                     "textures": {"all": f"gems:block/{name}"}}) + "\n")
    for name in ("patchwork_brick", "patchwork_mosaic"):
        (assets / "items" / f"{name}.json").write_text(json.dumps({
            "model": {"type": "minecraft:model", "model": f"gems:block/{name}"}
        }) + "\n")
    for name in ("splitstone", "waymark_tile", "ali_high_map", "ali_gate_rubbing",
                 "ali_map_table", "ali_north_door", "ali_east_door", "ali_west_door",
                 "townhall_shortcut_board", "townhall_story_council",
                 "nour_relay_tower", "nour_relay_boat", "nour_relay_fog",
                 "ali_release_high", "ali_release_low",
                 "fam_dispatch_story", "fam_dispatch_school"):
        (assets / "blockstates" / f"{name}.json").write_text(json.dumps({
            "variants": {"": {"model": f"gems:block/{name}"}}
        }) + "\n")
        (assets / "models/block" / f"{name}.json").write_text(json.dumps({
            "parent": "minecraft:block/cube_all",
            "textures": {"all": f"gems:block/{name}"}
        }) + "\n")
    for name in ("splitstone", "waymark_tile"):
        (assets / "items" / f"{name}.json").write_text(json.dumps({
            "model": {"type": "minecraft:model", "model": f"gems:block/{name}"}
        }) + "\n")
    (assets / "items/shortcut_chalk.json").write_text(json.dumps({
        "model": {"type": "minecraft:model", "model": "gems:item/shortcut_chalk"}
    }) + "\n")
    (assets / "models/item/shortcut_chalk.json").write_text(json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "gems:item/shortcut_chalk"}
    }) + "\n")
    (assets / "items/recovery_slip.json").write_text(json.dumps({
        "model": {"type": "minecraft:model", "model": "gems:item/recovery_slip"}
    }) + "\n")
    (assets / "models/item/recovery_slip.json").write_text(json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "gems:item/recovery_slip"}
    }) + "\n")
    (assets / "items/forum_dispatch.json").write_text(json.dumps({
        "model": {"type": "minecraft:model", "model": "gems:item/forum_dispatch"}
    }) + "\n")
    (assets / "models/item/forum_dispatch.json").write_text(json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "gems:item/forum_dispatch"}
    }) + "\n")

    # The Court stations share one carved stone family. Their large central
    # symbols remain legible as floor blocks; the three ending tiles differ in
    # silhouette as well as hue. All geometry is deterministic pixel drawing.
    court_tiles = {
        "court_dais": ("#ddb98b", "dais"),
        "court_west_station": ("#8cc3d2", "mask"),
        "court_east_station": ("#e1b875", "key"),
        "court_west_mark": ("#8cc3d2", "west"),
        "court_east_mark": ("#e1b875", "east"),
        "court_remember_ending": ("#cbb2e4", "book"),
        "court_repair_ending": ("#e6a880", "bridge"),
        "court_release_ending": ("#8fd0bc", "road"),
        "townhall_open_archive": ("#cbb2e4", "book"),
        "townhall_relief_depot": ("#e6a880", "crate"),
        "townhall_crossroads": ("#8fd0bc", "crossroads"),
    }
    for name, (accent, glyph) in court_tiles.items():
        tile = recolor("slate_tiles.png", ("#171a27", "#292c3b", "#44475a", "#666579"))
        pen = ImageDraw.Draw(tile)
        if name.startswith("court_") and name.endswith("_ending"):
            # Curtain wings and a lit lintel identify the finale floor stations.
            pen.rectangle((1, 1, 14, 14), fill="#251e30", outline="#d1a77e")
            pen.rectangle((2, 3, 3, 13), fill="#844d68")
            pen.rectangle((12, 3, 13, 13), fill="#844d68")
            pen.line((4, 2, 11, 2), fill="#e6c192", width=2)
            pen.rectangle((3, 4, 12, 12), fill="#202637", outline=accent)
        elif name.startswith("townhall_"):
            # Civic plaques have a bright header and open face at eye level.
            pen.rectangle((1, 1, 14, 14), fill="#334b52", outline="#d4b783")
            pen.rectangle((2, 2, 13, 3), fill="#8ec7bd")
            pen.rectangle((3, 4, 12, 12), fill="#202f3a", outline=accent)
        else:
            pen.rectangle((1, 1, 14, 14), outline="#a79078")
            pen.rectangle((3, 3, 12, 12), fill="#202637", outline=accent)
        if glyph == "dais":
            pen.ellipse((5, 5, 10, 10), outline=accent, width=2)
            pen.point((8, 8), fill="#fff0c6")
        elif glyph == "mask":
            pen.polygon(((4, 5), (8, 4), (12, 5), (11, 10), (8, 12), (5, 10)), outline=accent)
            pen.point((6, 7), fill="#fff0c6")
            pen.point((10, 7), fill="#fff0c6")
        elif glyph == "key":
            pen.ellipse((4, 4, 8, 8), outline=accent)
            pen.line((8, 8, 12, 12), fill=accent, width=2)
            pen.line((10, 10, 12, 8), fill=accent)
        elif glyph in ("west", "east"):
            points = ((4, 8), (10, 4), (10, 12)) if glyph == "west" else ((12, 8), (6, 4), (6, 12))
            pen.polygon(points, fill=accent)
        elif glyph == "book":
            pen.polygon(((4, 5), (8, 6), (8, 12), (4, 11)), fill=accent)
            pen.polygon(((8, 6), (12, 5), (12, 11), (8, 12)), fill="#f1debd")
        elif glyph == "bridge":
            pen.line((4, 11, 4, 7, 6, 5, 10, 5, 12, 7, 12, 11), fill=accent, width=2)
            pen.line((4, 11, 12, 11), fill="#f1debd", width=2)
        elif glyph == "crate":
            pen.rectangle((4, 6, 11, 11), fill=accent, outline="#f1debd")
            pen.line((4, 8, 11, 8), fill="#49384a")
            pen.line((8, 6, 8, 11), fill="#49384a")
            pen.rectangle((6, 4, 9, 5), fill="#f1debd")
        elif glyph == "crossroads":
            pen.rectangle((7, 4, 9, 11), fill=accent)
            pen.rectangle((4, 7, 12, 9), fill=accent)
            pen.rectangle((7, 7, 9, 9), fill="#f1debd")
        else:
            pen.line((4, 10, 8, 5, 12, 10), fill=accent, width=2)
            pen.line((5, 12, 8, 8, 11, 12), fill="#f1debd", width=2)
        tile.save(TARGET / f"{name}.png")
        (assets / "blockstates" / f"{name}.json").write_text(json.dumps({
            "variants": {"": {"model": f"gems:block/{name}"}}
        }) + "\n")
        (assets / "models/block" / f"{name}.json").write_text(json.dumps({
            "parent": "minecraft:block/cube_all", "textures": {"all": f"gems:block/{name}"}
        }) + "\n")


if __name__ == "__main__":
    main()
