"""Author the new Four Roads mansion without rewriting any saved v1 piece.

The 47-square center keeps the old foundation, jigsaws, passage axes and
control tiles. The irregular house is original block geometry, with a tall
northwest observatory, unequal pitched wings and a working service court.
"""

from __future__ import annotations

from generate_four_roads_estate import MODULE, connectors, piece_tree
from generate_landmarks import Landmark


NAME = "four_roads_estate_v3_2_2"


def shell(a: Landmark, x0: int, z0: int, x1: int, z1: int,
          top: int, wall: str, trim: str) -> None:
    for y in range(5, top + 1):
        a.ring(x0, z0, x1, z1, y, wall)
    a.ring(x0, z0, x1, z1, 5, "gems:station_ashlar")
    a.ring(x0, z0, x1, z1, top, trim)
    a.fill(x0 + 1, 13, z0 + 1, x1 - 1, 13, z1 - 1,
           "minecraft:dark_oak_planks")
    a.fill(x0 + 1, 14, z0 + 1, x1 - 1, 14, z1 - 1, "minecraft:air")


def roof_x(a: Landmark, x0: int, z0: int, x1: int, z1: int,
           base: int, copper: str) -> None:
    """A solid two-sided roof whose long ridge runs north/south."""
    width = x1 - x0
    for x in range(x0, x1 + 1):
        y = base + min(x - x0, x1 - x) // 2
        a.fill(x, y, z0, x, y, z1, copper)
    ridge = x0 + width // 2
    a.fill(ridge, base + width // 4 + 1, z0, ridge, base + width // 4 + 1, z1,
           "minecraft:cut_copper")


def roof_z(a: Landmark, x0: int, z0: int, x1: int, z1: int,
           base: int, copper: str) -> None:
    """A roof with an east/west ridge; the two orientations break repetition."""
    width = z1 - z0
    for z in range(z0, z1 + 1):
        y = base + min(z - z0, z1 - z) // 2
        a.fill(x0, y, z, x1, y, z, copper)
    ridge = z0 + width // 2
    a.fill(x0, base + width // 4 + 1, ridge,
           x1, base + width // 4 + 1, ridge, "minecraft:cut_copper")


def window(a: Landmark, x: int, z: int, y: int,
           across_x: bool, glass: str = "minecraft:light_blue_stained_glass") -> None:
    if across_x:
        a.fill(x, y, z, x + 1, y + 2, z, glass)
        a.fill(x - 1, y - 1, z, x + 2, y - 1, z, "minecraft:dark_oak_planks")
        a.fill(x - 1, y + 3, z, x + 2, y + 3, z, "gems:station_ashlar")
    else:
        a.fill(x, y, z, x, y + 2, z + 1, glass)
        a.fill(x, y - 1, z - 1, x, y - 1, z + 2, "minecraft:dark_oak_planks")
        a.fill(x, y + 3, z - 1, x, y + 3, z + 2, "gems:station_ashlar")


def facade_details(a: Landmark) -> None:
    """Large, legible elevations on the two sides visible from each road."""
    # The red central hall has a stone base, timber belt, two unequal window
    # tiers and projecting east balcony instead of an unbroken wall plane.
    for y in (11, 20, 27):
        a.fill(14, y, 32, 32, y, 32,
               "minecraft:dark_oak_planks" if y == 20 else "gems:station_ashlar")
        a.fill(32, y, 14, 32, y, 32,
               "minecraft:dark_oak_planks" if y == 20 else "gems:station_ashlar")
    for x in (16, 26):
        window(a, x, 32, 7, True, "minecraft:orange_stained_glass")
        window(a, x, 32, 16, True, "minecraft:light_blue_stained_glass")
        a.fill(x - 1, 25, 32, x + 2, 25, 33, "minecraft:dark_oak_planks")
    for z in (16, 26):
        window(a, 32, z, 7, False, "minecraft:orange_stained_glass")
        window(a, 32, z, 16, False)
    a.fill(33, 13, 25, 37, 13, 31, "minecraft:dark_oak_planks")
    for x in (33, 37):
        a.fill(x, 14, 25, x, 15, 31, "minecraft:iron_bars")
    a.fill(34, 14, 31, 36, 15, 31, "minecraft:iron_bars")
    a.fill(32, 14, 27, 32, 16, 28, "minecraft:air")
    a.fill(34, 17, 25, 36, 17, 31, "minecraft:weathered_cut_copper")

    # Roof dormers sit on different slopes and do not repeat at the corners.
    for x, z in ((18, 20), (27, 25), (17, 34), (31, 37)):
        base = (29 + min(x - 13, 33 - x) // 2) if z < 29 else (
            19 + min(z - 28, 43 - z) // 2)
        a.fill(x - 2, base, z, x + 2, base + 2, z + 2,
               "gems:archive_limestone")
        a.fill(x - 1, base + 1, z + 2, x + 1, base + 2, z + 2,
               "minecraft:light_blue_stained_glass")
        a.fill(x - 3, base + 3, z - 1, x + 3, base + 3, z + 3,
               "minecraft:oxidized_cut_copper")
        a.fill(x - 2, base + 4, z, x + 2, base + 4, z + 2,
               "minecraft:cut_copper")

    # The long pale wing gets bay-height pilasters and a varied window rhythm.
    for x, z in ((10, 42), (17, 42), (27, 42), (32, 42),
                 (36, 9), (36, 14)):
        if z == 42:
            window(a, x, z, 7, True)
            if x in (10, 27):
                window(a, x, z, 14, True,
                       "minecraft:orange_stained_glass")
        else:
            window(a, x, z, 8, False)
    for x in (8, 13, 25, 34):
        a.fill(x, 6, 42, x, 17, 42, "gems:station_ashlar")
        a.fill(x - 1, 18, 41, x + 1, 18, 43, "minecraft:polished_tuff")

    # The observatory has broad framed lights, corner buttresses and one
    # clock above each approach. Its upper arcade is visibly open.
    for x, z in ((3, 3), (3, 13), (13, 3), (13, 13)):
        a.fill(x, 5, z, x, 37, z, "minecraft:polished_tuff")
    for y in (9, 19, 29):
        for side in (3, 13):
            a.fill(side, y, 6, side, y + 3, 10,
                   "minecraft:orange_stained_glass")
            a.fill(side, y - 1, 5, side, y - 1, 11,
                   "minecraft:dark_oak_planks")
            a.fill(side, y + 4, 5, side, y + 4, 11,
                   "gems:archive_limestone")
            a.fill(6, y, side, 10, y + 3, side,
                   "minecraft:orange_stained_glass")
            a.fill(5, y - 1, side, 11, y - 1, side,
                   "minecraft:dark_oak_planks")
            a.fill(5, y + 4, side, 11, y + 4, side,
                   "gems:archive_limestone")
    for x, z in ((3, 8), (13, 8), (8, 3), (8, 13)):
        a.put(x, 34, z, "gems:station_dial")

    # Stone arches identify the south walk-in loggia and the east glass court.
    for x in (15, 23, 31):
        a.pillar(x, 46, 6, 10, "gems:station_ashlar")
        a.fill(x - 1, 11, 45, x + 1, 11, 46, "minecraft:chiseled_tuff")
    for x in (16, 24):
        a.fill(x, 12, 45, x + 6, 12, 46,
               "minecraft:weathered_cut_copper")
    for z in (15, 23, 28):
        a.fill(46, 10, z, 46, 12, z, "gems:archive_limestone")
        a.put(46, 13, z, "minecraft:sea_lantern")


def manor() -> Landmark:
    a = Landmark(NAME, (MODULE, 48, MODULE))
    # Identical four-deep bearing slab and walking elevation to saved v1.
    a.fill(0, 0, 0, 46, 3, 46, "minecraft:stone_bricks")
    a.fill(0, 4, 0, 46, 4, 46, "gems:compass_mosaic")
    a.fill(0, 5, 0, 46, 47, 46, "minecraft:air")

    # Five unequal, overlapping volumes make a house rather than a box.
    shell(a, 10, 6, 36, 17, 22, "gems:archive_limestone",
          "minecraft:dark_oak_planks")  # north gallery
    roof_z(a, 9, 5, 37, 18, 23, "minecraft:oxidized_cut_copper")
    shell(a, 5, 11, 17, 34, 20, "gems:refuge_brick",
          "minecraft:polished_tuff")  # west library
    roof_x(a, 4, 10, 18, 35, 21, "minecraft:weathered_cut_copper")
    shell(a, 14, 14, 32, 32, 28, "minecraft:tuff_bricks",
          "gems:station_ashlar")  # tall central hall
    roof_x(a, 13, 13, 33, 33, 29, "minecraft:oxidized_cut_copper")
    shell(a, 8, 29, 34, 42, 18, "gems:archive_limestone",
          "minecraft:dark_oak_planks")  # lower south drawing wing
    roof_z(a, 7, 28, 35, 43, 19, "minecraft:weathered_cut_copper")
    shell(a, 30, 20, 43, 40, 14, "minecraft:tuff_bricks",
          "minecraft:dark_oak_planks")  # service wing
    roof_x(a, 29, 19, 44, 41, 15, "minecraft:oxidized_cut_copper")

    # One offset observatory, intentionally unlike the other three corners.
    a.fill(3, 5, 3, 13, 39, 13, "gems:station_ashlar")
    a.fill(5, 6, 5, 11, 37, 11, "minecraft:air")
    for y in (8, 18, 28, 37):
        a.fill(5, y, 5, 11, y, 11, "minecraft:dark_oak_planks")
    for y in (10, 20, 30, 36):
        for x, z in ((3, 7), (13, 7), (7, 3), (7, 13)):
            a.fill(x, y, z, x, y + 2, z, "minecraft:orange_stained_glass")
    for y in (14, 27, 38):
        a.ring(2, 2, 14, 14, y, "minecraft:polished_tuff")
    for x, z in ((3, 8), (13, 8), (8, 3), (8, 13)):
        a.put(x, 34, z, "gems:station_dial")
    # The open upper lookout and narrow west balcony break the tower shaft.
    for x in (5, 7, 9, 11):
        a.fill(x, 38, 3, x, 39, 3, "minecraft:air")
        a.fill(x, 38, 13, x, 39, 13, "minecraft:air")
    a.fill(0, 27, 5, 3, 27, 11, "minecraft:dark_oak_planks")
    a.fill(0, 28, 5, 0, 29, 11, "minecraft:iron_bars")
    for y, inset in ((40, 0), (41, 1), (42, 1), (43, 2), (44, 2), (45, 3)):
        a.fill(3 + inset, y, 3 + inset, 13 - inset, y, 13 - inset,
               "minecraft:oxidized_cut_copper")
    a.put(8, 46, 8, "minecraft:lightning_rod")

    # A projecting east bay, a south conservatory and mismatched chimneys.
    a.fill(39, 5, 26, 46, 12, 34, "gems:refuge_brick")
    a.fill(40, 6, 27, 45, 11, 33, "minecraft:air")
    a.fill(39, 13, 25, 46, 13, 35, "minecraft:weathered_cut_copper")
    for z in (27, 31):
        window(a, 46, z, 7, False, "minecraft:orange_stained_glass")
    a.fill(25, 5, 39, 35, 11, 45, "minecraft:tuff_bricks")
    a.fill(26, 6, 40, 34, 10, 44, "minecraft:air")
    a.fill(24, 12, 38, 36, 12, 46, "minecraft:light_blue_stained_glass")
    for x in (27, 32):
        a.pillar(x, 45, 5, 11, "gems:station_ashlar")
    for x, z, top in ((31, 9, 35), (36, 34, 26)):
        a.fill(x, 19, z, x + 2, top, z + 2, "gems:refuge_brick")
        a.fill(x - 1, top + 1, z - 1, x + 3, top + 1, z + 3,
               "minecraft:polished_tuff")
    # The south wing's tall limestone gable is offset from its glass bay.
    for y, half in ((20, 6), (21, 5), (22, 4), (23, 3), (24, 2), (25, 1)):
        a.fill(15 - half, y, 42, 15 + half, y, 43,
               "gems:archive_limestone")
    a.fill(14, 21, 43, 16, 23, 43, "minecraft:orange_stained_glass")

    # Window rhythms follow room size rather than a repeated facade grid.
    for x, z, y in ((14, 6, 9), (27, 6, 9), (32, 6, 16),
                    (11, 42, 8), (29, 42, 8), (18, 29, 18)):
        window(a, x, z, y, True)
    for x, z, y in ((5, 15, 8), (5, 27, 9), (17, 30, 16),
                    (43, 25, 8), (43, 36, 8), (32, 16, 18)):
        window(a, x, z, y, False)
    facade_details(a)

    # A deep north arrival porch and south loggia occupy different silhouettes.
    a.fill(15, 11, 0, 29, 11, 9, "minecraft:dark_oak_planks")
    a.fill(16, 12, 0, 28, 12, 9, "minecraft:oxidized_cut_copper")
    for x in (16, 28):
        for z in (0, 4, 8):
            a.pillar(x, z, 5, 10, "gems:station_ashlar")
            a.put(x, 11, z, "minecraft:chiseled_tuff")
    for y, half in ((13, 6), (14, 5), (15, 3), (16, 1)):
        a.fill(22 - half, y, 0, 22 + half, y, 1,
               "gems:archive_limestone")
    a.fill(15, 11, 38, 31, 11, 46, "minecraft:dark_oak_planks")
    a.fill(15, 12, 38, 31, 12, 46, "minecraft:weathered_cut_copper")
    for x in (15, 31):
        for z in (39, 43, 46):
            a.pillar(x, z, 5, 10, "gems:station_ashlar")
    for x in (16, 31):
        a.put(x, 10, 42, "minecraft:lantern[hanging=false]")

    # Distinct lateral entries: western clock arcade and eastern glass court.
    for x in (0, 4, 8):
        for z in (16, 27):
            a.pillar(x, z, 5, 10, "minecraft:deepslate_bricks")
    a.fill(0, 11, 15, 8, 11, 28, "minecraft:weathered_cut_copper")
    for x in (38, 42, 46):
        for z in (16, 27):
            a.pillar(x, z, 5, 10, "gems:archive_limestone")
    a.fill(38, 11, 15, 46, 11, 28, "minecraft:light_blue_stained_glass")

    # Purposeful rooms remain beside the cross-axial public routes.
    a.fill(7, 5, 26, 7, 9, 31, "minecraft:bookshelf")
    a.fill(10, 5, 31, 14, 5, 31, "minecraft:dark_oak_planks")
    a.put(11, 6, 31, "minecraft:lectern[facing=north]")
    a.fill(27, 5, 9, 32, 5, 12, "minecraft:dark_oak_planks")
    a.put(29, 6, 10, "minecraft:cartography_table")
    a.put(34, 5, 30, "minecraft:crafting_table")
    a.put(38, 5, 36, "minecraft:smoker[facing=west]")
    a.put(35, 5, 36, "minecraft:barrel[facing=up]")
    a.fill(27, 5, 34, 32, 5, 36, "minecraft:dark_oak_planks")
    for x in (27, 32):
        a.put(x, 6, 35, "minecraft:lantern[hanging=false]")
    for x, z in ((13, 24), (30, 23), (25, 15), (16, 30)):
        a.put(x, 5, z, "minecraft:dark_oak_stairs[facing=east]")
    # Room-side openings make the study, map room and service wing usable.
    for x, z in ((14, 26), (31, 12), (30, 27), (16, 34)):
        a.fill(x, 5, z, x + 1, 7, z, "minecraft:air")
        a.fill(x, 4, z, x + 1, 4, z, "minecraft:dark_oak_planks")

    # Reassert all four old five-wide public paths after every facade and
    # furnishing operation. Their exact Y4 floor and Y5..10 clearances are
    # gameplay contracts; the roofed hall can vary above that height.
    for x0, z0, x1, z1 in ((18, 0, 23, 46), (0, 18, 46, 23)):
        a.fill(x0, 5, z0, x1, 10, z1, "minecraft:air")
        a.fill(x0, 4, z0, x1, 4, z1, "gems:waymark_tile")
    for x, z, block in ((19, 2, "minecraft:ochre_froglight"),
                        (44, 19, "minecraft:sea_lantern"),
                        (19, 44, "minecraft:pearlescent_froglight"),
                        (2, 19, "minecraft:shroomlight")):
        a.put(x, 11, z, block)

    # Saved controls retain their original local positions and function.
    a.put(23, 4, 23, "gems:estate_foyer")
    a.put(23, 4, 28, "gems:estate_return")
    a.sign(21, 5, 28, ("THE FOUR ROADS", "Reach this house", "to win. Use the", "return stone."))
    connectors(a, 2, 2, piece_tree())
    return a


if __name__ == "__main__":
    result = manor().write()
    print(result)
