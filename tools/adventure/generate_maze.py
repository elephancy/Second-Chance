"""Build the original Ninefold Maze without changing the district generator.

The source graph is deterministic. Nine fixed jigsaw pieces form a 141 x 141
plan; each has three fully carved levels. Run from any directory with Python.
"""

from __future__ import annotations

import json
import random
import struct
import zlib
from collections import deque
from pathlib import Path

from generate_landmarks import Landmark


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/data/gems"
REPORT = ROOT / "reports/maze-layout-26.3.json"
GRID = 18
MODULE_CELLS = 6
MODULE = 47
FLOORS = (4, 13, 22)
START = (1, 16)
FIRST_STAIR = (16, 16)
SECOND_STAIR = (1, 1)
FINISH = (16, 1)
SEED = 26030091


def neighbors(cell: tuple[int, int]):
    x, z = cell
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        other = (x + dx, z + dz)
        if 0 <= other[0] < GRID and 0 <= other[1] < GRID:
            yield other


def edge(a: tuple[int, int], b: tuple[int, int]):
    return tuple(sorted((a, b)))


def graph_for_floor(floor: int):
    rng = random.Random(SEED + floor * 117)
    visited = {(0, 0)}
    stack = [(0, 0)]
    edges = set()
    while stack:
        options = [node for node in neighbors(stack[-1]) if node not in visited]
        if options:
            chosen = rng.choice(options)
            edges.add(edge(stack[-1], chosen))
            visited.add(chosen)
            stack.append(chosen)
        else:
            stack.pop()
    assert len(visited) == GRID * GRID and len(edges) == GRID * GRID - 1
    tree = set(edges)
    # Additional routes are intentional, readable shortcuts. The solved route
    # remains present even if a later generation revision changes these loops.
    missing = [edge(node, other) for x in range(GRID) for z in range(GRID)
               for node in [(x, z)] for other in neighbors(node)
               if edge(node, other) not in edges]
    missing = sorted(set(missing))
    rng.shuffle(missing)
    edges.update(missing[:34 + floor * 4])
    # The jigsaw attachment sits inside an actual passage at every join.
    fixed = [((11, 8), (12, 8)), ((6, 8), (5, 8)),
             ((8, 6), (8, 5)), ((8, 11), (8, 12)),
             ((5, 2), (6, 2)), ((11, 2), (12, 2)),
             ((5, 14), (6, 14)), ((11, 14), (12, 14))]
    edges.update(edge(a, b) for a, b in fixed)
    return edges, tree


def path(tree, start, target):
    adjacent = {node: [] for node in ((x, z) for x in range(GRID) for z in range(GRID))}
    for a, b in tree:
        adjacent[a].append(b)
        adjacent[b].append(a)
    previous = {start: None}
    queue = deque([start])
    while queue:
        current = queue.popleft()
        if current == target:
            break
        for other in adjacent[current]:
            if other not in previous:
                previous[other] = current
                queue.append(other)
    result = []
    current = target
    while current is not None:
        result.append(current)
        current = previous[current]
    return list(reversed(result))


def marks(routes):
    first, second, third = routes
    # Each ordered marker is on a guaranteed route through the floor.
    return {
        (0, first[max(2, len(first) // 8)]): "maze_sun",
        (0, first[max(3, len(first) // 4)]): "maze_moon",
        (0, first[max(4, len(first) * 3 // 8)]): "maze_star",
        (0, first[max(5, len(first) // 2)]): "maze_checkpoint_one",
        (1, second[max(2, len(second) // 3)]): "maze_mirror_amber",
        (1, second[max(3, len(second) * 2 // 3)]): "maze_mirror_blue",
        (1, second[max(4, len(second) * 3 // 4)]): "maze_checkpoint_two",
        (2, third[max(2, len(third) // 5)]): "maze_witness_one",
        (2, third[max(3, len(third) * 2 // 5)]): "maze_witness_two",
        (2, third[max(4, len(third) * 3 // 5)]): "maze_witness_three",
        (2, third[max(5, len(third) * 4 // 5)]): "maze_checkpoint_three",
        (0, START): "maze_entry",
        (2, FINISH): "maze_exit",
    }


def module_name(mx: int, mz: int) -> str:
    return f"ninefold_maze_{mz}_{mx}"


def module(mx: int, mz: int, graphs, routes, markers) -> Landmark:
    name = module_name(mx, mz)
    a = Landmark(name, (MODULE, 43, MODULE))
    palette = ["minecraft:polished_tuff", "minecraft:deepslate_bricks",
               "minecraft:polished_blackstone_bricks"]
    wall = palette[(mx + mz) % 3]
    a.fill(0, 0, 0, 46, 3, 46, "minecraft:stone_bricks")
    a.fill(0, 4, 0, 46, 28, 46, wall)
    # Deep foundations and a continuous roof give all nine modules one visual
    # identity, while copper ribs and coloured bay windows identify each wing.
    for x in (0, 46):
        for z in (0, 46):
            a.fill(x, 4, z, x, 33, z, "minecraft:weathered_cut_copper")
    for y in (4, 13, 22, 28):
        a.ring(0, 0, 46, 46, y, "minecraft:oxidized_cut_copper")
    a.fill(0, 29, 0, 46, 29, 46, "minecraft:deepslate_tile_slab[type=bottom]")
    for x in range(2, 45, 7):
        for z in (0, 46):
            a.fill(x, 6, z, x + 2, 7, z, "minecraft:blue_stained_glass")
            a.fill(x, 15, z, x + 2, 16, z, "minecraft:orange_stained_glass")
            a.fill(x, 24, z, x + 2, 25, z, "minecraft:purple_stained_glass")
    for z in range(2, 45, 7):
        for x in (0, 46):
            a.fill(x, 6, z, x, 7, z + 2, "minecraft:blue_stained_glass")
            a.fill(x, 15, z, x, 16, z + 2, "minecraft:orange_stained_glass")
            a.fill(x, 24, z, x, 25, z + 2, "minecraft:purple_stained_glass")
    for floor, base in enumerate(FLOORS):
        edges = graphs[floor]
        for cz in range(MODULE_CELLS):
            for cx in range(MODULE_CELLS):
                gx, gz = mx * MODULE_CELLS + cx, mz * MODULE_CELLS + cz
                x, z = 5 + cx * 7, 5 + cz * 7
                a.fill(x - 2, base + 1, z - 2, x + 2, base + 3, z + 2,
                       "minecraft:air")
                a.fill(x - 2, base, z - 2, x + 2, base, z + 2,
                       "minecraft:polished_deepslate" if floor == 0 else
                       "minecraft:polished_tuff" if floor == 1 else
                       "minecraft:cut_copper")
                a.put(x, base, z, "minecraft:chiseled_polished_blackstone")
                # A small raised fixture turns otherwise identical junctions
                # into legible spaces without narrowing their walking lane.
                if (gx + gz + floor * 3) % 11 == 0:
                    a.put(x - 2, base + 1, z - 2, "minecraft:copper_bulb[lit=true]")
                node = (gx, gz)
                for other in neighbors(node):
                    if edge(node, other) not in edges:
                        continue
                    dx, dz = other[0] - gx, other[1] - gz
                    if dx > 0:
                        a.fill(x + 3, base + 1, z - 1,
                               46 if cx == 5 else x + 4, base + 3, z + 1, "minecraft:air")
                    elif dx < 0:
                        a.fill(0 if cx == 0 else x - 4, base + 1, z - 1,
                               x - 3, base + 3, z + 1, "minecraft:air")
                    elif dz > 0:
                        a.fill(x - 1, base + 1, z + 3,
                               x + 1, base + 3, 46 if cz == 5 else z + 4, "minecraft:air")
                    else:
                        a.fill(x - 1, base + 1, 0 if cz == 0 else z - 4,
                               x + 1, base + 3, z - 3, "minecraft:air")
                marker = markers.get((floor, node))
                if marker:
                    a.put(x + 2, base + 1, z + 2, f"gems:{marker}")
                    a.put(x + 2, base, z + 2, "minecraft:chiseled_copper")
                    if marker.startswith("maze_checkpoint") or marker == "maze_entry":
                        a.put(x - 2, base + 1, z + 2, "gems:maze_reset")
                if node not in routes[floor] and (gx * 17 + gz * 7 + floor) % 29 == 0:
                    a.put(x, base, z, f"gems:maze_trap_{('echo', 'dart', 'snare', 'return')[(gx + gz + floor) % 4]}")
                if node not in routes[floor] and (gx * 13 + gz * 11 + floor) % 43 == 0:
                    a.chest(x, base + 1, z, "south", "ninefold_maze_cache")
                if node not in routes[floor] and (gx + gz * 3 + floor) % 37 == 0:
                    a.put(x - 1, base + 1, z + 1, "minecraft:cobweb")
                    a.put(x - 2, base + 1, z + 1, "minecraft:pointed_dripstone[vertical_direction=up]")
    # Two traversable ladder shafts force the main route through all three
    # floors. Each has a backing column, a floor opening and clear landing.
    for lower, cell in ((0, FIRST_STAIR), (1, SECOND_STAIR)):
        if cell[0] // 6 != mx or cell[1] // 6 != mz:
            continue
        x, z = 5 + (cell[0] % 6) * 7, 5 + (cell[1] % 6) * 7
        start_y, end_y = FLOORS[lower], FLOORS[lower + 1]
        for y in range(start_y + 1, end_y + 2):
            a.put(x + 3, y, z + 2, "minecraft:polished_deepslate")
            a.put(x + 2, y, z + 2, "minecraft:ladder[facing=west]")
        a.put(x + 2, end_y + 1, z + 2, "minecraft:ladder[facing=west]")
        a.put(x + 1, end_y, z + 2, "minecraft:polished_tuff")
        if lower == 0:
            a.sign(x - 2, start_y + 1, z + 2,
                   ("MIRROR FLOOR", "Warm light on", "the near glass", "Cold light far"))
        else:
            a.sign(x - 2, start_y + 1, z + 2,
                   ("WITNESS FLOOR", "Three voices", "wait beneath", "the last crown"))
    # Static, uniquely named joins prevent rotation from changing the topology.
    # Four center arms and four corners are exactly three jigsaw depths.
    joins = {
        (1, 1): [("north", "n"), ("east", "e"), ("south", "s"), ("west", "w")],
        (1, 0): [("south", "n"), ("west", "nw"), ("east", "ne")],
        (1, 2): [("north", "s"), ("west", "sw"), ("east", "se")],
        (0, 1): [("east", "w")], (2, 1): [("west", "e")],
        (0, 0): [("east", "nw")], (2, 0): [("west", "ne")],
        (0, 2): [("east", "sw")], (2, 2): [("west", "se")],
    }
    child = {"n": (1, 0), "e": (2, 1), "s": (1, 2), "w": (0, 1),
             "nw": (0, 0), "ne": (2, 0), "sw": (0, 2), "se": (2, 2)}
    for direction, join in joins[(mx, mz)]:
        if join in child and (mx, mz) == (1, 1) or (
            (mx, mz) == (1, 0) and join in ("nw", "ne")
        ) or ((mx, mz) == (1, 2) and join in ("sw", "se")):
            pool = f"gems:{module_name(*child[join])}"
        else:
            pool = "minecraft:empty"
        orientation = {"north": "north_up", "south": "south_up",
                       "east": "east_up", "west": "west_up"}[direction]
        x, z = {"north": (19, 0), "south": (19, 46),
                "west": (0, 19), "east": (46, 19)}[direction]
        # North/south pieces attach at x=19, east/west at z=19; the graph
        # always opens these seam cells via the fixed edges above.
        a.jigsaw(x, 6, z, orientation, f"gems:ninefold_{join}", pool)
    if (mx, mz) == (0, 2):
        # The gateway is monumental but open; it is never an unbreakable wall
        # over the original terrain, and the player must opt into the maze.
        a.fill(0, 5, 30, 9, 8, 36, "minecraft:air")
        a.fill(0, 4, 29, 0, 13, 29, "minecraft:weathered_cut_copper")
        a.fill(0, 4, 37, 0, 13, 37, "minecraft:weathered_cut_copper")
        a.fill(0, 13, 29, 0, 13, 37, "minecraft:weathered_cut_copper")
        # The template is projected from its base, four blocks below the first
        # maze floor. Grade the west gate within this piece so an explorer can
        # climb in from natural ground without building a staircase.
        a.fill(0, 0, 31, 9, 8, 35, "minecraft:air")
        for x in range(10):
            height = min(x, 4)
            a.fill(x, 0, 31, x, height, 35,
                   "minecraft:polished_deepslate" if x >= 4 else
                   "minecraft:stone_bricks")
        a.sign(8, 5, 30, ("NINEFOLD MAZE", "Day yields to", "night; the last", "lights remain."))
    if (mx, mz) == (2, 0):
        a.fill(36, 23, 11, 46, 26, 13, "minecraft:air")
        a.sign(40, 23, 11, ("LAST DOOR", "Three floors", "leave a mark", "on one door."))
    # Repeated towers, crenellations, and stained glass make this a skyline
    # destination; the maze itself remains readable at human scale.
    for x, z in ((2, 2), (44, 2), (2, 44), (44, 44)):
        a.fill(x - 1, 29, z - 1, x + 1, 32, z + 1, "minecraft:oxidized_cut_copper")
        a.put(x, 33, z, "minecraft:lightning_rod")
    # Each wing has a roof pavilion. The high central crown, lower arm crowns,
    # and four corner crowns break up the 141-block roofline from far away.
    crown = 41 if (mx, mz) == (1, 1) else 38 if mx == 1 or mz == 1 else 35
    a.fill(17, 29, 17, 29, 30, 29, "minecraft:cut_copper")
    for x, z in ((17, 17), (17, 29), (29, 17), (29, 29)):
        a.fill(x, 31, z, x, crown - 2, z, "minecraft:weathered_cut_copper")
    for y in range(32, crown - 1, 3):
        a.ring(17, 17, 29, 29, y, "minecraft:cut_copper")
        for x, z in ((23, 17), (23, 29), (17, 23), (29, 23)):
            a.put(x, y + 1, z, "minecraft:light_blue_stained_glass")
    a.fill(17, crown - 1, 17, 29, crown - 1, 29, "minecraft:oxidized_cut_copper")
    a.fill(20, crown, 20, 26, crown, 26, "minecraft:cut_copper_slab[type=bottom]")
    a.put(23, crown + 1, 23, "minecraft:lightning_rod")
    return a


def write_json(relative: str, value):
    target = DATA / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def write_worldgen():
    for mz in range(3):
        for mx in range(3):
            name = module_name(mx, mz)
            write_json(f"worldgen/template_pool/{name}.json", {
                "fallback": "minecraft:empty", "elements": [{"weight": 1, "element": {
                    "element_type": "minecraft:single_pool_element",
                    "location": f"gems:{name}", "projection": "rigid",
                    "processors": {"processors": []}}}]})
    write_json("worldgen/structure/ninefold_maze.json", {
        "type": "minecraft:jigsaw", "biomes": "#gems:has_structure/ninefold_maze",
        "max_distance_from_center": 100, "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": 3, "spawn_overrides": {}, "start_height": {"absolute": 0},
        "start_pool": "gems:ninefold_maze_1_1", "step": "surface_structures",
        "terrain_adaptation": "beard_thin", "use_expansion_hack": False})
    write_json("worldgen/structure_set/ninefold_maze.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 26030091,
                      "separation": 72, "spacing": 128},
        "structures": [{"structure": "gems:ninefold_maze", "weight": 1}]})
    write_json("tags/worldgen/biome/has_structure/ninefold_maze.json", {
        "values": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:savanna"]})
    write_json("tags/worldgen/structure/atlas_target/ninefold_maze.json", {
        "values": ["gems:ninefold_maze"]})
    write_json("tags/worldgen/structure/themed_target/ninefold_maze.json", {
        "values": ["gems:ninefold_maze"]})
    write_json("loot_table/chests/ninefold_maze_cache.json", {
        "type": "minecraft:chest", "pools": [{"rolls": {"type": "minecraft:uniform", "min": 1, "max": 2},
            "entries": [{"type": "minecraft:item", "name": item, "weight": weight}
                        for item, weight in [("minecraft:torch", 8), ("minecraft:bread", 6),
                                             ("minecraft:copper_ingot", 5), ("minecraft:echo_shard", 1)]]}]})


def png_rgba(pixels):
    def chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(
            ">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + b"".join(bytes(pixel) for pixel in row) for row in pixels)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">2I5B", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def write_assets():
    assets = ROOT / "src/main/resources/assets/gems"
    names = ["entry", "exit", "reset", "sun", "moon", "star",
             "checkpoint_one", "checkpoint_two", "checkpoint_three",
             "mirror_amber", "mirror_blue", "witness_one", "witness_two",
             "witness_three", "trap_echo", "trap_dart", "trap_snare", "trap_return"]
    colors = {
        "entry": (69, 211, 207), "exit": (253, 211, 96), "reset": (192, 151, 232),
        "sun": (255, 196, 79), "moon": (161, 191, 248), "star": (244, 135, 210),
        "checkpoint_one": (71, 190, 230), "checkpoint_two": (71, 190, 230),
        "checkpoint_three": (71, 190, 230),
        "mirror_amber": (246, 178, 76), "mirror_blue": (91, 147, 249),
        "witness_one": (223, 119, 182), "witness_two": (223, 119, 182),
        "witness_three": (223, 119, 182),
        "trap_echo": (140, 112, 215), "trap_dart": (217, 105, 80),
        "trap_snare": (126, 170, 91), "trap_return": (213, 82, 145),
    }
    for index, name in enumerate(names):
        block = f"maze_{name}"
        glow = colors[name]
        pixels = []
        for y in range(16):
            row = []
            for x in range(16):
                border = x in (0, 1, 14, 15) or y in (0, 1, 14, 15)
                ring = 24 <= (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 46
                spoke = (x + 2 * y + index) % 7 == 0 and 3 <= x <= 12 and 3 <= y <= 12
                center = 6 <= x <= 9 and 6 <= y <= 9
                if border:
                    color = (29, 39, 49, 255)
                elif ring or spoke or center:
                    color = (*glow, 255)
                else:
                    color = (61 + (x + y) % 4 * 3, 73, 84, 255)
                row.append(color)
            pixels.append(row)
        texture = assets / f"textures/block/{block}.png"
        texture.parent.mkdir(parents=True, exist_ok=True)
        texture.write_bytes(png_rgba(pixels))
        for relative, content in ((f"models/block/{block}.json", {
                "parent": "minecraft:block/cube_all", "textures": {"all": f"gems:block/{block}"}}),
                                  (f"blockstates/{block}.json", {"variants": {"": {"model": f"gems:block/{block}"}}})):
            target = assets / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps(content, separators=(",", ":")) + "\n", encoding="utf-8")


def main():
    graphs = []
    trees = []
    for floor in range(3):
        graph, tree = graph_for_floor(floor)
        graphs.append(graph)
        trees.append(tree)
    routes = [path(trees[0], START, FIRST_STAIR),
              path(trees[1], FIRST_STAIR, SECOND_STAIR),
              path(trees[2], SECOND_STAIR, FINISH)]
    markers = marks(routes)
    if len(markers) != 13:
        raise AssertionError("Maze markers collide on the guaranteed route")
    written = []
    for mz in range(3):
        for mx in range(3):
            written.append(module(mx, mz, graphs, routes, markers).write())
    write_worldgen()
    write_assets()
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps({"footprint": [141, 141], "floors": 3,
                                  "cells": 972, "main_route_cells": list(map(len, routes)),
                                  "extra_loops": [len(g) - len(t) for g, t in zip(graphs, trees)],
                                  "markers": {f"{floor}:{x},{z}": name
                                              for (floor, (x, z)), name in markers.items()},
                                  "pieces": written}, indent=2) + "\n", encoding="utf-8")
    print(f"Ninefold Maze: 9 pieces, 141 x 141, 3 floors, {sum(map(len, routes))} route cells")


if __name__ == "__main__":
    main()
