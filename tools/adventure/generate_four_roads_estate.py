"""Author the Four Roads Estate as 25 versioned, rigid 26.3 jigsaw pieces.

All geometry and routes are deterministic. The old Ninefold Maze is untouched.
The fixed 47-block module width keeps each NBT template within vanilla's
structure-template limit. A named center jigsaw anchors the 235-block layout.
"""

from __future__ import annotations

import json
import random
from collections import deque
from pathlib import Path

from generate_landmarks import Landmark
from generate_maze import png_rgba


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/data/gems"
TEST_DATA = ROOT / "src/gametest/resources/data/gems"
REPORT = ROOT / "reports/four-roads-estate-layout-26.3.json"
STAGED_WORLDGEN = ROOT / "reports/asset_trials/four_roads_estate/promotion"
MODULE = 47
CELLS = 6
CELL_PITCH = 7
GRID = 5
Y_FLOOR = 4
VERSION = "v1"
SECTOR_ROWS = ("NNNNE", "WNNEE", "WWMEE", "WWSSE", "WSSSS")
SECTORS = "NESW"
GATES = {"N": (14, 0), "E": (29, 14), "S": (14, 29), "W": (0, 14)}
INNER = {"N": (14, 11), "E": (18, 14), "S": (14, 18), "W": (11, 14)}
PALETTE = {
    "N": {"wall": "minecraft:mossy_stone_bricks", "cap": "minecraft:azalea_leaves[persistent=true]",
          "trim": "gems:storybook_path", "floor": "minecraft:stone_bricks", "light": "minecraft:ochre_froglight"},
    "E": {"wall": "minecraft:calcite", "cap": "minecraft:polished_diorite_slab[type=bottom]",
          "trim": "gems:gallery_prism", "floor": "minecraft:smooth_stone", "light": "minecraft:sea_lantern"},
    "S": {"wall": "minecraft:prismarine_bricks", "cap": "minecraft:oxidized_cut_copper_slab[type=bottom]",
          "trim": "gems:harbor_wave_tile", "floor": "minecraft:polished_tuff", "light": "minecraft:sea_lantern"},
    "W": {"wall": "minecraft:deepslate_bricks", "cap": "minecraft:weathered_cut_copper_slab[type=bottom]",
          "trim": "gems:station_dial", "floor": "minecraft:polished_deepslate", "light": "minecraft:shroomlight"},
}
TITLES = {"N": "THE GARDEN ROAD", "E": "THE MIRROR ROAD",
          "S": "THE SLUICE ROAD", "W": "THE CLOCK ROAD"}


def sector(mx: int, mz: int) -> str:
    return SECTOR_ROWS[mz][mx]


def module_of(cell: tuple[int, int]) -> tuple[int, int]:
    return cell[0] // CELLS, cell[1] // CELLS


def cell_center(cell: tuple[int, int]) -> tuple[int, int]:
    return 5 + (cell[0] % CELLS) * CELL_PITCH, 5 + (cell[1] % CELLS) * CELL_PITCH


def edge(a: tuple[int, int], b: tuple[int, int]):
    return tuple(sorted((a, b)))


def adjacent(cell: tuple[int, int], allowed: set[tuple[int, int]]):
    x, z = cell
    for dx, dz in ((0, -1), (1, 0), (0, 1), (-1, 0)):
        other = (x + dx, z + dz)
        if other in allowed:
            yield other


def shortest(edges, start, end):
    links: dict[tuple[int, int], set[tuple[int, int]]] = {}
    for a, b in edges:
        links.setdefault(a, set()).add(b)
        links.setdefault(b, set()).add(a)
    prior = {start: None}
    queue = deque([start])
    while queue:
        node = queue.popleft()
        if node == end:
            break
        for other in sorted(links.get(node, ())):
            if other not in prior:
                prior[other] = node
                queue.append(other)
    if end not in prior:
        raise AssertionError(f"Disconnected route {start} -> {end}")
    route = []
    node = end
    while node is not None:
        route.append(node)
        node = prior[node]
    return list(reversed(route))


def piece_tree():
    """One seam per module-tree edge; four long gate-to-mansion spines."""
    parent = {(2, 2): None}
    spines = {
        "N": ((2, 1), (1, 1), (1, 0), (2, 0)),
        "E": ((3, 2), (3, 1), (4, 1), (4, 2)),
        "S": ((2, 3), (3, 3), (3, 4), (2, 4)),
        "W": ((1, 2), (1, 3), (0, 3), (0, 2)),
    }
    for wing, spine in spines.items():
        previous = (2, 2)
        for node in spine:
            assert sector(*node) == wing
            parent[node] = previous
            previous = node
        remaining = sorted((mx, mz) for mz in range(GRID) for mx in range(GRID)
                           if sector(mx, mz) == wing and (mx, mz) not in parent)
        for node in remaining:
            adjacent_parent = sorted(other for other in
                                     ((node[0] - 1, node[1]), (node[0] + 1, node[1]),
                                      (node[0], node[1] - 1), (node[0], node[1] + 1))
                                     if other in parent and sector(*other) == wing)
            assert adjacent_parent, (wing, node)
            parent[node] = adjacent_parent[0]
    assert len(parent) == 25, parent
    return parent


def seam_edge(a: tuple[int, int], b: tuple[int, int]):
    ax, az = a
    bx, bz = b
    if bx == ax + 1:
        return edge((ax * CELLS + 5, az * CELLS + 2),
                    (bx * CELLS, bz * CELLS + 2))
    if bx == ax - 1:
        return seam_edge(b, a)
    if bz == az + 1:
        return edge((ax * CELLS + 2, az * CELLS + 5),
                    (bx * CELLS + 2, bz * CELLS))
    return seam_edge(b, a)


def make_road(name: str, parent):
    cells = {(x, z) for x in range(GRID * CELLS) for z in range(GRID * CELLS)
             if sector(*module_of((x, z))) == name}
    assert len(cells) == 216
    forced = {seam_edge(node, prev) for node, prev in parent.items()
              if prev is not None and sector(*node) == sector(*prev) == name}
    for attempt in range(500):
        rng = random.Random(2603117 + 101 * ord(name) + attempt)
        start = GATES[name]
        visited = {start}
        stack = [start]
        edges = set(forced)
        while stack:
            options = [node for node in adjacent(stack[-1], cells)
                       if node not in visited and
                       (module_of(node) == module_of(stack[-1])
                        or edge(stack[-1], node) in forced)]
            if options:
                node = rng.choice(options)
                edges.add(edge(stack[-1], node))
                visited.add(node)
                stack.append(node)
            else:
                stack.pop()
        assert visited == cells
        route = shortest(edges, start, INNER[name])
        if len(route) < 65 or len({module_of(node) for node in route}) < 4:
            continue
        extras = sorted({edge(node, other) for node in cells for other in adjacent(node, cells)
                         if module_of(node) == module_of(other)} - edges)
        rng.shuffle(extras)
        loops = 0
        for candidate in extras:
            trial = edges | {candidate}
            if len(shortest(trial, start, INNER[name])) >= 55:
                edges = trial
                loops += 1
            if loops >= 14:
                break
        if loops >= 10:
            return cells, edges, shortest(edges, start, INNER[name]), loops
    raise AssertionError(f"Could not find a long, looping {name} road")


def module_name(mx: int, mz: int) -> str:
    return f"four_roads_estate_{VERSION}_{mz}_{mx}"


def connectors(a: Landmark, mx: int, mz: int, parent):
    a.jigsaw(23, 6, 23, "north_up", "gems:four_roads_center_anchor"
             if (mx, mz) == (2, 2) else "gems:four_roads_unused", "minecraft:empty")
    for node, prev in parent.items():
        if prev is None:
            continue
        if (mx, mz) not in (node, prev):
            continue
        other = node if (mx, mz) == prev else prev
        dx, dz = other[0] - mx, other[1] - mz
        direction = {(0, -1): "north", (1, 0): "east",
                     (0, 1): "south", (-1, 0): "west"}[(dx, dz)]
        x, z = {"north": (19, 0), "east": (46, 19),
                "south": (19, 46), "west": (0, 19)}[direction]
        left, right = sorted((node, prev))
        connector = f"gems:four_roads_{left[0]}_{left[1]}_{right[0]}_{right[1]}"
        a.jigsaw(x, 6, z, f"{direction}_up", connector,
                 f"gems:{module_name(*node)}" if (mx, mz) == prev else "minecraft:empty")


def carve(a: Landmark, x: int, z: int):
    a.fill(x - 2, 5, z - 2, x + 2, 8, z + 2, "minecraft:air")


def road_piece(mx: int, mz: int, cells, edges, route, parent):
    wing = sector(mx, mz)
    p = PALETTE[wing]
    a = Landmark(module_name(mx, mz), (MODULE, 18, MODULE))
    # Explicit air clears pre-existing canopy throughout every new-generation
    # module. Omitted template cells would leave trunks above the maze walls.
    a.fill(0, 5, 0, 46, 17, 46, "minecraft:air")
    # One walking plane over a four-block spanning slab. Structure afterPlace
    # adds columns and retaining edges only in the current generating chunk.
    a.fill(0, 0, 0, 46, 3, 46, "minecraft:stone_bricks")
    a.fill(0, 4, 0, 46, 4, 46, p["floor"])
    a.fill(0, 5, 0, 46, 9, 46, p["wall"])
    for cz in range(CELLS):
        for cx in range(CELLS):
            node = (mx * CELLS + cx, mz * CELLS + cz)
            x, z = cell_center(node)
            carve(a, x, z)
            a.put(x, 4, z, p["trim"])
            for other in adjacent(node, cells):
                if edge(node, other) not in edges:
                    continue
                dx, dz = other[0] - node[0], other[1] - node[1]
                if dx > 0:
                    a.fill(x + 3, 5, z - 1, 46 if cx == 5 else x + 4, 8, z + 1, "minecraft:air")
                elif dx < 0:
                    a.fill(0 if cx == 0 else x - 4, 5, z - 1, x - 3, 8, z + 1, "minecraft:air")
                elif dz > 0:
                    a.fill(x - 1, 5, z + 3, x + 1, 8, 46 if cz == 5 else z + 4, "minecraft:air")
                else:
                    a.fill(x - 1, 5, 0 if cz == 0 else z - 4, x + 1, 8, z - 3, "minecraft:air")
            # Floor lamps keep stray mobs out of every room and corridor.
            a.put(x + 2, 4, z - 2, p["light"])
            if wing == "N":
                # Climbing hedge crowns, planters and pale blossom make this a
                # garden labyrinth rather than a recoloured stone corridor.
                a.put(x - 2, 5, z + 2, "minecraft:flowering_azalea")
                a.put(x - 2, 9, z + 2, "minecraft:flowering_azalea_leaves[persistent=true]")
                if (cx + cz) % 2 == 0:
                    a.put(x + 2, 5, z + 2, "minecraft:moss_carpet")
            elif wing == "E":
                # Blue and violet light in the dead-end bays acts as the
                # mirror road's repeated visual compass.
                a.put(x - 2, 5, z + 2, "minecraft:blue_stained_glass")
                a.put(x - 2, 6, z + 2, "minecraft:purple_stained_glass")
                a.put(x + 2, 9, z + 2, "minecraft:amethyst_block")
            elif wing == "S":
                # Recessed one-block-deep channels are optional detours. The
                # guaranteed route and side exits stay dry and breathable.
                if node not in route and (node[0] + 2 * node[1]) % 4 == 0:
                    for xx in (x - 1, x, x + 1):
                        a.put(xx, 4, z, "minecraft:water")
                a.put(x - 2, 5, z + 2, "minecraft:water_cauldron[level=3]")
            else:
                # A lit copper warning line runs beside each possible dart
                # lane. Timing is optional: the dry side route always works.
                a.put(x - 2, 5, z + 2, "minecraft:copper_bulb[lit=true]")
                if (cx + cz) % 2 == 0:
                    a.put(x + 2, 9, z + 2, "minecraft:lightning_rod")
            if node not in route and (node[0] * 17 + node[1] * 13) % 41 == 0:
                a.put(x, 4, z, f"gems:estate_trap_{wing.lower()}")
            if node not in route and (node[0] * 23 + node[1] * 11) % 53 == 0:
                a.chest(x - 2, 5, z + 2, "south", "four_roads_estate_cache")
            if node in route[18::18] and node != INNER[wing]:
                a.put(x + 2, 4, z + 2, "gems:estate_reset")
            if (cx + cz) % 3 == 0:
                for px, pz in ((x - 2, z - 2), (x + 2, z + 2)):
                    a.put(px, 9, pz, p["cap"])
    # Two optional Sluice Road basins overlap the actual saved seam room
    # centers. Keep the three-wide endpoint floor dry even when the adjacent
    # rooms retain their decorative water. The interior stair builder owns
    # only steps 1..11; it must not need to repair authored endpoints.
    if (mx, mz) in ((2, 4), (4, 4)):
        a.fill(4, 4, 18, 6, 4, 20, p["floor"])
    # Edge parapet, glass/window rhythm, and a different crown for each road.
    for x in range(0, 47, 7):
        for z in (0, 46):
            a.fill(x, 9, z, min(46, x + 1), 11, z, p["trim"])
    for z in range(0, 47, 7):
        for x in (0, 46):
            a.fill(x, 9, z, x, 11, min(46, z + 1), p["trim"])
    for x, z in ((3, 3), (43, 3), (3, 43), (43, 43)):
        a.fill(x - 1, 10, z - 1, x + 1, 14, z + 1, p["trim"])
        a.put(x, 15, z, p["light"])
    if GATES[wing] in cells and module_of(GATES[wing]) == (mx, mz):
        node = GATES[wing]
        x, z = cell_center(node)
        for lateral in (-1, 0, 1):
            a.put(x + lateral if wing in "NS" else x, 4,
                  z if wing in "NS" else z + lateral,
                  f"gems:estate_entry_{wing.lower()}")
        a.put(x + 2 if wing in "NS" else x, 4,
              z if wing in "NS" else z + 2, "gems:estate_reset")
        # A three-wide protected apron from the first room to the outer edge.
        if wing == "N":
            for offset in range(5):
                a.fill(x - 1, 0, offset, x + 1, offset, offset, "minecraft:stone_bricks")
                a.fill(x - 1, offset + 1, offset, x + 1, 8, offset, "minecraft:air")
        elif wing == "E":
            for offset in range(5):
                xx = 46 - offset
                a.fill(xx, 0, z - 1, xx, offset, z + 1, "minecraft:smooth_stone")
                a.fill(xx, offset + 1, z - 1, xx, 8, z + 1, "minecraft:air")
        elif wing == "S":
            for offset in range(5):
                zz = 46 - offset
                a.fill(x - 1, 0, zz, x + 1, offset, zz, "minecraft:polished_tuff")
                a.fill(x - 1, offset + 1, zz, x + 1, 8, zz, "minecraft:air")
        else:
            for offset in range(5):
                a.fill(offset, 0, z - 1, offset, offset, z + 1, "minecraft:polished_deepslate")
                a.fill(offset, offset + 1, z - 1, offset, 8, z + 1, "minecraft:air")
        a.sign(max(1, x - 2), 5, max(1, z - 2),
               (TITLES[wing][:15], "One road is", "enough to reach", "the house."))
    # The last room opens into a five-high mansion portico.
    if INNER[wing] in cells and module_of(INNER[wing]) == (mx, mz):
        x, z = cell_center(INNER[wing])
        if wing == "N":
            a.fill(x - 2, 5, z, x + 2, 9, 46, "minecraft:air")
        elif wing == "E":
            a.fill(0, 5, z - 2, x, 9, z + 2, "minecraft:air")
        elif wing == "S":
            a.fill(x - 2, 5, 0, x + 2, 9, z, "minecraft:air")
        else:
            a.fill(x, 5, z - 2, 46, 9, z + 2, "minecraft:air")
    connectors(a, mx, mz, parent)
    return a


def mansion_pavilion(a: Landmark, axis: str, side: int, center: int) -> None:
    """Project a detailed, symmetrical bay out of one mansion elevation."""
    inward = 1 if side == 0 else -1

    def face(lo: int, hi: int, depth_lo: int, depth_hi: int,
             y0: int, y1: int, block: str) -> None:
        d0, d1 = side + inward * depth_lo, side + inward * depth_hi
        if axis == "x":
            a.fill(lo, y0, min(d0, d1), hi, y1, max(d0, d1), block)
        else:
            a.fill(min(d0, d1), y0, lo, max(d0, d1), y1, hi, block)

    # The face is two blocks ahead of the main wall, with a visible return on
    # either side. It does not enter the five-wide central road portico.
    face(center - 4, center + 4, 0, 2, 5, 30, "gems:archive_limestone")
    face(center - 4, center + 4, 0, 2, 5, 6, "gems:station_ashlar")
    for pier in (center - 4, center + 4):
        face(pier, pier, 0, 0, 7, 30, "gems:station_ashlar")
        for y in (12, 21, 30):
            face(pier - 1, pier + 1, 0, 1, y, y, "minecraft:chiseled_tuff")
    for base, top in ((8, 10), (17, 19), (26, 28)):
        face(center - 2, center + 2, 0, 0, base, top,
             "minecraft:light_blue_stained_glass")
        face(center, center, 0, 0, base, top, "minecraft:dark_oak_log")
        face(center - 3, center + 3, 0, 0, base - 1, base - 1,
             "minecraft:polished_tuff")
        face(center - 3, center + 3, 0, 0, top + 1, top + 1,
             "minecraft:dark_oak_planks")
        for jamb in (center - 3, center + 3):
            face(jamb, jamb, 0, 0, base, top, "minecraft:dark_oak_log")
    for y in (13, 22, 31):
        face(center - 5, center + 5, 0, 3, y, y,
             "minecraft:weathered_cut_copper")
    # A stepped limestone gable protrudes in front of the long copper roof.
    for y, half in ((32, 4), (33, 3), (34, 3), (35, 2), (36, 2), (37, 1)):
        face(center - half, center + half, 0, 1, y, y,
             "gems:archive_limestone")
        for edge in (center - half, center + half):
            face(edge, edge, 0, 1, y, y, "minecraft:oxidized_cut_copper")
    face(center, center, 0, 0, 34, 35, "minecraft:orange_stained_glass")
    face(center - 1, center + 1, 0, 2, 38, 38, "minecraft:cut_copper")


def mansion_piece(parent):
    a = Landmark(module_name(2, 2), (MODULE, 48, MODULE))
    a.fill(0, 0, 0, 46, 3, 46, "minecraft:stone_bricks")
    a.fill(0, 4, 0, 46, 4, 46, "gems:compass_mosaic")
    a.fill(0, 5, 0, 46, 31, 46, "minecraft:air")
    # Central mansion: stone plinth, two occupied stories, open atrium,
    # copper roof and four taller corner turrets. Its gatehouses extend into
    # the four inner road modules for a broad cross-shaped estate silhouette.
    for y, wall in ((5, "gems:refuge_brick"), (14, "minecraft:tuff_bricks"),
                    (23, "gems:archive_limestone")):
        for yy in range(y, y + 8):
            a.ring(2, 2, 44, 44, yy, wall)
        for xx in (2, 44):
            for zz in range(4, 43, 6):
                a.fill(xx, y + 3, zz, xx, y + 5, zz + 1, "minecraft:light_blue_stained_glass")
                a.fill(xx, y + 2, zz - 1, xx, y + 2, zz + 2, "minecraft:dark_oak_planks")
        for zz in (2, 44):
            for xx in range(4, 43, 6):
                a.fill(xx, y + 3, zz, xx + 1, y + 5, zz, "minecraft:light_blue_stained_glass")
                a.fill(xx - 1, y + 2, zz, xx + 2, y + 2, zz, "minecraft:dark_oak_planks")
        a.ring(1, 1, 45, 45, y + 7, "minecraft:cut_copper")
    for floor_y in (13, 22):
        a.ring(2, 2, 44, 44, floor_y, "minecraft:polished_tuff")
        a.ring(8, 8, 38, 38, floor_y, "minecraft:dark_oak_planks")
    for x in (7, 39):
        for z in (7, 39):
            a.fill(x - 3, 5, z - 3, x + 3, 41, z + 3, "gems:station_ashlar")
            for yy in (10, 19, 28, 36):
                for px, pz in ((x - 3, z), (x + 3, z), (x, z - 3), (x, z + 3)):
                    a.put(px, yy, pz, "minecraft:orange_stained_glass")
                    a.put(px, yy + 1, pz, "minecraft:orange_stained_glass")
            for yy, radius in ((42, 3), (43, 2), (44, 2), (45, 1)):
                a.fill(x - radius, yy, z - radius, x + radius, yy, z + radius,
                       "minecraft:oxidized_cut_copper")
            a.put(x, 46, z, "minecraft:lightning_rod")
    for y in (12, 21, 30):
        a.ring(0, 0, 46, 46, y, "minecraft:weathered_cut_copper")
    # Long pitched copper roofs and front gables break the former flat slab.
    for x in range(2, 45):
        slope = min(x - 2, 44 - x) // 2
        a.fill(x, 31 + slope, 2, x, 31 + slope, 44,
               "minecraft:oxidized_cut_copper" if slope % 3 else "minecraft:weathered_cut_copper")
    a.fill(21, 42, 2, 25, 42, 44, "minecraft:cut_copper")
    for z in (2, 44):
        for y in range(32, 42):
            span = max(1, (42 - y) * 2)
            a.fill(23 - span, y, z, 23 + span, y, z, "gems:archive_limestone")
        a.fill(22, 37, z, 24, 39, z, "minecraft:light_blue_stained_glass")
    # Each elevation has two full-height projecting bays. Their deep reveals,
    # masonry piers and separate gables give all four roads a proper arrival
    # facade rather than a flat band of repeated windows.
    for axis in ("x", "z"):
        for side in (0, 46):
            for center in (14, 32):
                mansion_pavilion(a, axis, side, center)
    for x, z in ((16, 16), (30, 16), (16, 30), (30, 30)):
        a.fill(x - 1, 38, z - 1, x + 1, 43, z + 1, "minecraft:cut_copper")
        a.put(x, 44, z, "minecraft:sea_lantern")
    # Four five-wide porticoes lead directly to the central hall.
    for x0, z0, x1, z1 in ((18, 0, 23, 8), (38, 18, 46, 23),
                            (18, 38, 23, 46), (0, 18, 8, 23)):
        a.fill(x0, 5, z0, x1, 10, z1, "minecraft:air")
        a.fill(x0, 4, z0, x1, 4, z1, "gems:waymark_tile")
    for xx, zz in ((17, 2), (25, 2), (17, 44), (25, 44),
                   (2, 17), (2, 25), (44, 17), (44, 25)):
        a.fill(xx, 5, zz, xx, 12, zz, "gems:station_ashlar")
        a.put(xx, 13, zz, "minecraft:chiseled_tuff")
    for x in range(13, 34, 5):
        for z in range(13, 34, 5):
            a.put(x, 4, z, "gems:compass_mosaic")
            a.put(x, 30, z, "minecraft:sea_lantern")
    for x in (9, 16, 30, 37):
        for z in (9, 16, 30, 37):
            a.put(x, 4, z, "minecraft:sea_lantern")
    for x, z in ((13, 23), (33, 23), (23, 13), (23, 33)):
        a.fill(x - 1, 5, z - 1, x + 1, 5, z + 1, "minecraft:dark_oak_planks")
        a.put(x, 6, z, "minecraft:lantern[hanging=false]")
    # The threshold awards the estate clear on contact. A separate return
    # stone in the same open foyer sends visitors to their chosen outer gate.
    a.put(23, 4, 23, "gems:estate_foyer")
    a.put(23, 4, 28, "gems:estate_return")
    a.sign(21, 5, 28, ("THE FOUR ROADS", "Reach this house", "to win. Use the", "return stone."))
    connectors(a, 2, 2, parent)
    return a


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def write_worldgen():
    for mz in range(GRID):
        for mx in range(GRID):
            name = module_name(mx, mz)
            write_json(DATA / f"worldgen/template_pool/{name}.json", {
                "fallback": "minecraft:empty", "elements": [{"weight": 1, "element": {
                    "element_type": "minecraft:single_pool_element", "location": f"gems:{name}",
                    "projection": "rigid", "processors": {"processors": []}}}]})
    write_json(DATA / "worldgen/structure/four_roads_estate_v1.json", {
        "type": "gems:four_roads_estate_v1", "biomes": "#gems:has_structure/four_roads_estate_v1",
        "max_distance_from_center": 124, "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": 5, "start_height": {"absolute": 0}, "start_pool": "gems:four_roads_estate_v5_2_2",
        "start_jigsaw_name": "gems:four_roads_center_anchor", "step": "surface_structures",
        "spawn_overrides": {}, "terrain_adaptation": "none", "use_expansion_hack": False})
    # Keep natural placement opt-in until independent worlds prove the complete
    # support, four approaches and saved controls. Main resources contain no
    # active structure set while the production gate remains open.
    write_json(TEST_DATA / "worldgen/structure_set/four_roads_estate_v1.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 2603117,
                      "separation": 15, "spacing": 16},
        "structures": [{"structure": "gems:four_roads_estate_v1", "weight": 1}]})
    write_json(DATA / "tags/worldgen/biome/has_structure/four_roads_estate_v1.json", {
        "values": ["gems:estate_commons", "minecraft:desert",
                   "minecraft:plains"]})
    for folder in ("atlas_target", "themed_target"):
        write_json(DATA / f"tags/worldgen/structure/{folder}/four_roads_estate_v1.json",
                   {"values": ["gems:four_roads_estate_v1"]})
    write_json(DATA / "loot_table/chests/four_roads_estate_cache.json", {
        "type": "minecraft:chest", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": item, "weight": weight}
            for item, weight in (("minecraft:bread", 8), ("minecraft:torch", 8),
                                 ("minecraft:copper_ingot", 5), ("minecraft:echo_shard", 1))]}]})


def write_assets():
    """Distinct geometric control tiles; these pixels are authored deterministically."""
    colors = {"entry_n": (117, 196, 103), "entry_e": (117, 205, 239),
              "entry_s": (58, 172, 206), "entry_w": (238, 160, 86),
              "foyer": (249, 215, 117), "return": (176, 151, 235),
              "reset": (111, 230, 205), "trap_n": (126, 164, 95),
              "trap_e": (112, 162, 195), "trap_s": (69, 137, 188),
              "trap_w": (189, 104, 79)}
    assets = ROOT / "src/main/resources/assets/gems"
    for name, glow in colors.items():
        pixels = []
        for y in range(16):
            row = []
            for x in range(16):
                border = x in (0, 1, 14, 15) or y in (0, 1, 14, 15)
                interior = 3 <= x <= 12 and 3 <= y <= 12
                if name.startswith("entry"):
                    point = ((name.endswith("n") and x in (7, 8) and y <= 10)
                             or (name.endswith("s") and x in (7, 8) and y >= 5)
                             or (name.endswith("e") and y in (7, 8) and x >= 5)
                             or (name.endswith("w") and y in (7, 8) and x <= 10))
                    motif = point or (abs(x - 7.5) + abs(y - 7.5) in (5, 6))
                elif name == "foyer":
                    motif = x in (7, 8) or y in (7, 8) or abs(x - 7.5) == abs(y - 7.5)
                elif name == "return":
                    motif = (x in (4, 5, 10, 11) and 4 <= y <= 11)
                    motif |= (y in (4, 5, 10, 11) and 4 <= x <= 11)
                elif name == "reset":
                    motif = abs(x - 7.5) + abs(y - 7.5) in (5, 6, 7)
                else:
                    motif = x in (6, 7, 8, 9) and (y in (4, 5, 6, 8, 9) or y == 11)
                if border:
                    color = (32, 42, 49, 255)
                elif interior and motif:
                    color = (*glow, 255)
                elif (x + y) % 5 == 0:
                    color = (71, 85, 86, 255)
                else:
                    color = (56, 69, 73, 255)
                row.append(color)
            pixels.append(row)
        block = "estate_" + name
        texture = assets / f"textures/block/{block}.png"
        texture.parent.mkdir(parents=True, exist_ok=True)
        texture.write_bytes(png_rgba(pixels))
        write_json(assets / f"models/block/{block}.json", {
            "parent": "minecraft:block/cube_all", "textures": {"all": f"gems:block/{block}"}})
        write_json(assets / f"blockstates/{block}.json", {
            "variants": {"": {"model": f"gems:block/{block}"}}})


def main():
    parent = piece_tree()
    roads = {name: make_road(name, parent) for name in SECTORS}
    written = []
    for mz in range(GRID):
        for mx in range(GRID):
            if (mx, mz) == (2, 2):
                written.append(mansion_piece(parent).write())
            else:
                wing = sector(mx, mz)
                cells, edges, route, _ = roads[wing]
                written.append(road_piece(mx, mz, cells, edges, route, parent).write())
    write_worldgen()
    write_assets()
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps({
        "version": VERSION, "footprint": [235, 235], "module_size": MODULE,
        "sector_rows": SECTOR_ROWS, "mansion_center": [2, 2],
        "terrain_adaptation": "none", "jigsaw_reach": 124,
        "roads": {name: {"cells": len(cells), "edges": len(edges),
                         "shortest_gate_to_mansion_cells": len(route),
                         "visited_modules": sorted({str(module_of(node)) for node in route}),
                         "extra_loops": loops}
                  for name, (cells, edges, route, loops) in roads.items()},
        "pieces": written}, indent=2) + "\n", encoding="utf-8")
    print("Four Roads Estate: 25 pieces, 235 x 235, four six-piece roads and central mansion")


if __name__ == "__main__":
    main()
