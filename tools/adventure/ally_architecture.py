"""Original Yehia architecture builders for Minecraft 26.3.

Functions accept the landmark generator module so both the canonical data pack
and inactive preview report use exactly the same block compositions.
"""
from __future__ import annotations

PALE = "minecraft:polished_diorite"
ARCHIVE = "gems:archive_limestone"
INK = "gems:archive_inlay"
COPPER = "minecraft:weathered_cut_copper"
TIMBER = "minecraft:stripped_birch_log[axis=y]"

def shift_x(piece, amount: int, name: str,
            width: int, height: int):
    if piece.entities:
        raise ValueError(f"{name}: cannot shift entities without updating NBT")
    piece.blocks = {(x + amount, y, z): block
                    for (x, y, z), block in piece.blocks.items()}
    piece.chests = [(x + amount, y, z) for x, y, z in piece.chests]
    piece.signs = [(x + amount, y, z) for x, y, z in piece.signs]
    piece.size = (width, height, piece.size[2])
    piece.name = name
    return piece


def yehia_story_lodge_v3(base):
    a = base.yehia_story_lodge_arrival_v2()
    a.name = "yehia_story_lodge_v3"
    a.jigsaw(46, 5, 23, "east_up", "gems:yehia_truth_garden_v3_entry",
             "gems:yehia_truth_garden_v3")
    a.jigsaw(0, 5, 23, "west_up", "gems:yehia_prop_house_v3_entry",
             "gems:yehia_prop_house_v3")
    # Replace the single continuous moss lid with three intersecting story
    # houses. The pale central ridge, timber western wing and flowering eastern
    # wing keep the original ground-floor story room and all clue stations.
    a.fill(7, 13, 7, 39, 25, 40, "minecraft:air")
    for x in range(7, 19):
        height = 14 + max(0, 6 - abs(x - 12))
        a.fill(x, height, 8, x, height, 34,
               "minecraft:dark_oak_planks" if x % 3 else "minecraft:moss_block")
        for z in (8, 34):
            a.fill(x, 13, z, x, height - 1, z, "minecraft:calcite")
    for x in range(17, 30):
        height = 14 + max(0, 6 - abs(x - 23))
        a.fill(x, height, 7, x, height, 38,
               "minecraft:birch_planks" if x in (17, 29) else
               "minecraft:moss_block")
        for z in (7, 38):
            a.fill(x, 13, z, x, height - 1, z, "minecraft:calcite")
        if 21 <= x <= 25:
            a.fill(x, height, 15, x, height, 25,
                   "minecraft:yellow_stained_glass")
    for x in range(28, 41):
        height = 14 + max(0, 7 - abs(x - 34))
        a.fill(x, height, 8, x, height, 35,
               "minecraft:cherry_planks" if x % 3 else
               "minecraft:flowering_azalea_leaves[persistent=true]")
        for z in (8, 35):
            a.fill(x, 13, z, x, height - 1, z,
                   "minecraft:cherry_planks")
    # Dormer windows and a brick flue register as individual village roofs.
    for x, z in ((12, 34), (23, 38), (34, 35)):
        a.fill(x - 1, 15, z, x + 1, 17, z,
               "minecraft:yellow_stained_glass")
        a.fill(x - 2, 18, z - 1, x + 2, 18, z + 1,
               "minecraft:dark_oak_planks" if x == 12 else
               "minecraft:cherry_planks" if x == 34 else
               "minecraft:birch_planks")
    a.fill(31, 17, 13, 33, 24, 15, "minecraft:bricks")
    a.fill(30, 25, 12, 34, 25, 16,
           "minecraft:brick_slab[type=bottom]")
    a.put(32, 25, 14, "minecraft:campfire[lit=true]")
    # A large mushroom storytelling shelter sits west of the approach, with
    # an open stage facing the three original lodge gables.
    for x in (5, 14):
        for z in (35, 43):
            a.pillar(x, z, 5, 12, TIMBER)
    a.fill(4, 13, 34, 15, 13, 44, "minecraft:red_mushroom_block")
    for inset in range(3):
        a.fill(4 + inset, 14 + inset, 34 + inset,
               15 - inset, 14 + inset, 44 - inset,
               "minecraft:red_mushroom_block" if inset != 1 else
               "minecraft:mushroom_stem")
    a.fill(7, 4, 36, 12, 4, 42, "gems:storybook_path")
    a.fill(7, 5, 37, 12, 5, 38, "minecraft:birch_planks")
    a.put(9, 5, 39, "minecraft:lectern[facing=east]")
    for z in (36, 41):
        a.fill(5, 5, z, 5, 6, z + 1, "minecraft:bookshelf")
    # A tall eastern lantern tree, with a reachable viewing nook, balances
    # the mushroom and is visible above the existing turf roof.
    a.fill(36, 4, 38, 43, 4, 44, "gems:storybook_path")
    for x in (37, 42):
        for z in (38, 43):
            a.pillar(x, z, 5, 17, TIMBER)
    a.fill(37, 13, 38, 42, 13, 43, "minecraft:birch_planks")
    a.fill(38, 14, 39, 41, 16, 42, "minecraft:air")
    a.fill(38, 18, 39, 41, 18, 42,
           "minecraft:azalea_leaves[persistent=true]")
    a.fill(37, 19, 38, 42, 19, 43,
           "minecraft:flowering_azalea_leaves[persistent=true]")
    a.fill(39, 20, 40, 40, 20, 41, "minecraft:sea_lantern")
    a.pillar(38, 40, 5, 13, "minecraft:ladder[facing=east]")
    a.put(40, 14, 40, "minecraft:barrel[facing=up]")
    return a


def yehia_truth_garden_v3(base):
    a = base.yehia_truth_garden()
    a.name = "yehia_truth_garden_v3"
    a.size = (31, 24, 21)
    a.jigsaw(0, 5, 10, "west_up", "gems:yehia_truth_garden_v3_entry")
    a.fill(20, 0, 2, 30, 3, 18, "gems:storybook_turf")
    a.fill(20, 4, 2, 30, 4, 18, "minecraft:moss_block")
    a.fill(19, 4, 8, 30, 4, 12, "gems:storybook_path")
    for x in (22, 29):
        for z in (4, 16):
            a.pillar(x, z, 5, 13, TIMBER)
    for x0, z0, x1, z1, y, leaves in (
            (20, 3, 30, 17, 14, "minecraft:azalea_leaves[persistent=true]"),
            (21, 4, 29, 16, 15,
             "minecraft:flowering_azalea_leaves[persistent=true]"),
            (22, 5, 28, 15, 16, "minecraft:azalea_leaves[persistent=true]")):
        a.ring(x0, z0, x1, z1, y, leaves)
    for x, z in ((22, 6), (28, 6), (22, 14), (28, 14)):
        a.pillar(x, z, 7, 16, TIMBER)
    a.fill(24, 17, 8, 26, 17, 12,
           "minecraft:flowering_azalea_leaves[persistent=true]")
    a.put(25, 18, 10, "minecraft:sea_lantern")
    a.fill(23, 4, 5, 27, 4, 7, "minecraft:water[level=0]")
    a.fill(23, 4, 13, 27, 4, 15, "minecraft:water[level=0]")
    a.fill(23, 5, 9, 27, 5, 11,
           "minecraft:birch_planks")
    a.put(25, 6, 10, "minecraft:lectern[facing=west]")
    return a


def yehia_prop_house_v3(base):
    a = shift_x(base.yehia_prop_house(), 10, "yehia_prop_house_v3", 31, 25)
    a.jigsaw(30, 5, 10, "east_up", "gems:yehia_prop_house_v3_entry")
    a.fill(0, 0, 2, 11, 3, 18, "gems:storybook_turf")
    a.fill(0, 4, 2, 11, 4, 18, "gems:storybook_path")
    for y in range(5, 13):
        a.ring(2, 4, 12, 16, y,
               "minecraft:calcite" if y in (5, 12) else
               "minecraft:stripped_birch_wood")
    a.fill(3, 5, 5, 11, 11, 15, "minecraft:air")
    a.fill(11, 5, 8, 14, 9, 12, "minecraft:air")
    for x in (3, 11):
        for z in (5, 15):
            a.pillar(x, z, 5, 16, TIMBER)
    for step in range(6):
        a.fill(1 + step, 16 + step, 3,
               13 - step, 16 + step, 17,
               "minecraft:red_wool" if step % 2 == 0 else
               "minecraft:moss_block")
    a.fill(11, 5, 8, 14, 9, 12, "minecraft:air")
    a.fill(11, 4, 8, 14, 4, 12, "gems:storybook_path")
    # South proscenium opens the prop workshop as a real stage; its side
    # towers, curtains and painted false arch carry the theatrical identity.
    a.fill(5, 5, 16, 9, 10, 16, "minecraft:air")
    for x in (2, 12):
        a.pillar(x, 17, 5, 18, TIMBER)
        a.put(x, 19, 17, "minecraft:lantern")
    for x in (4, 10):
        a.fill(x, 5, 16, x, 11, 16, "minecraft:red_wool")
    a.fill(4, 12, 16, 10, 12, 16, "minecraft:calcite")
    a.fill(5, 13, 16, 9, 13, 16, "minecraft:red_wool")
    a.fill(6, 14, 16, 8, 14, 16, "minecraft:yellow_stained_glass")
    a.put(7, 15, 16, "minecraft:sea_lantern")
    a.fill(4, 5, 7, 9, 5, 8, "minecraft:dark_oak_planks")
    a.put(6, 6, 7, "minecraft:loom[facing=east]")
    for z in (6, 13):
        a.fill(2, 7, z, 2, 10, z + 2,
               "minecraft:yellow_stained_glass")
    a.put(7, 5, 13, "minecraft:barrel[facing=up]")
    return a

