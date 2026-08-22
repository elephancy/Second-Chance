"""Build original standalone expedition structures for Minecraft 26.3.

This file owns only the three optional side sites. It does not regenerate or
alter any friend district. Run from the repository root with Python 3.
"""

from __future__ import annotations

import json
from pathlib import Path

from generate_landmarks import Landmark


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/data/gems"


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def post(site: Landmark, x: int, z: int, top: int, stone: str) -> None:
    site.pillar(x, z, 0, top, stone)
    site.put(x, top + 1, z, "minecraft:lantern")


def entry_grade(site: Landmark, xs: range, steps: tuple[tuple[int, int], ...],
                support: str, stair_prefix: str, facing: str, apron_z: int) -> None:
    """A three-rise, three-wide exterior route into a floor at local y=3."""
    for x in xs:
        site.put(x, 0, apron_z, f"minecraft:{stair_prefix}_slab[type=bottom]")
        for z, y in steps:
            if y > 0:
                site.fill(x, 0, z, x, y - 1, z, f"minecraft:{support}")
            site.put(x, y, z, f"minecraft:{stair_prefix}_stairs[facing={facing},half=bottom]")
            for clear_y in range(y + 1, y + 4):
                site.put(x, clear_y, z, "minecraft:air")


def lost_trail() -> Landmark:
    a = Landmark("lost_trail", (43, 15, 43))
    chalk = "minecraft:polished_tuff"
    moss = "minecraft:mossy_stone_bricks"
    copper = "minecraft:weathered_cut_copper"
    path = [(x, 7) for x in range(5, 38)]
    path += [(37, z) for z in range(8, 36)]
    path += [(x, 35) for x in range(6, 37)]
    for x, z in path:
        a.fill(x - 1, 0, z - 1, x + 1, 2, z + 1, moss)
        a.fill(x - 1, 3, z - 1, x + 1, 3, z + 1, chalk)
        if (x + z) % 7 == 0:
            a.put(x, 3, z, copper)
    for x, z in ((5, 7), (37, 7), (37, 35), (6, 35)):
        a.fill(x - 3, 0, z - 3, x + 3, 2, z + 3, moss)
        a.fill(x - 3, 3, z - 3, x + 3, 3, z + 3, chalk)
        for ox in (-3, 3):
            for oz in (-3, 3):
                post(a, x + ox, z + oz, 6, "minecraft:tuff_bricks")
        a.fill(x - 3, 7, z - 3, x + 3, 7, z + 3, "minecraft:dark_oak_slab[type=bottom]")
        a.fill(x - 1, 7, z - 3, x + 1, 7, z - 3, copper)
        a.fill(x - 1, 7, z + 3, x + 1, 7, z + 3, copper)
    for x, z, marker in ((5, 7, "trail_origin"), (37, 7, "trail_turn"),
                         (37, 35, "trail_finish")):
        a.put(x, 4, z, f"gems:{marker}")
        a.put(x, 3, z, "minecraft:chiseled_tuff")
    a.sign(4, 4, 10, ("LOST TRAIL", "Blue stone bends", "away from this", "first marker."))
    a.sign(36, 4, 10, ("BLUE BEND", "The ribbon was", "laid beyond", "this turn."))
    a.sign(36, 4, 38, ("FAR MARK", "The ribbon ends", "where the road", "does not."))
    for x, z in ((12, 12), (20, 12), (27, 17), (18, 22), (9, 28), (28, 29)):
        a.fill(x - 1, 0, z - 1, x + 1, 2, z + 1, moss)
        a.pillar(x, z, 3, 6, "minecraft:chiseled_tuff")
        a.put(x, 7, z, "minecraft:lightning_rod[facing=up]")
        a.put(x - 1, 3, z, "minecraft:blue_glazed_terracotta")
    entry_grade(a, range(4, 7), ((13, 0), (12, 1), (11, 2)),
                "polished_tuff", "polished_tuff", "north", 14)
    return a


def star_cipher() -> Landmark:
    a = Landmark("star_cipher", (37, 22, 37))
    dark = "minecraft:polished_blackstone_bricks"
    pale = "minecraft:polished_diorite"
    bronze = "minecraft:waxed_oxidized_cut_copper"
    a.fill(5, 0, 5, 31, 2, 31, dark)
    a.fill(6, 3, 6, 30, 3, 30, pale)
    a.ring(5, 5, 31, 31, 4, bronze)
    for x in range(9, 28, 3):
        a.put(x, 3, 18, "minecraft:blue_glazed_terracotta")
    for z in range(9, 28, 3):
        a.put(18, 3, z, "minecraft:yellow_glazed_terracotta")
    for x, z in ((5, 5), (5, 31), (31, 5), (31, 31)):
        a.fill(x - 2, 0, z - 2, x + 2, 9, z + 2, dark)
        a.fill(x - 1, 4, z - 1, x + 1, 8, z + 1, "minecraft:air")
        a.ring(x - 2, z - 2, x + 2, z + 2, 10, bronze)
        a.put(x, 11, z, "minecraft:sea_lantern")
    for x in (11, 18, 25):
        for z in (11, 25):
            a.pillar(x, z, 4, 10, dark)
            a.put(x, 11, z, "minecraft:chiseled_polished_blackstone")
            a.put(x, 12, z, "minecraft:end_rod[facing=up]")
    a.ring(10, 10, 26, 26, 13, bronze)
    for x in range(10, 27):
        a.put(x, 14, 10, "minecraft:blue_stained_glass")
        a.put(x, 14, 26, "minecraft:blue_stained_glass")
    for z in range(11, 26):
        a.put(10, 14, z, "minecraft:blue_stained_glass")
        a.put(26, 14, z, "minecraft:blue_stained_glass")
    for pos, marker in { (18, 6): "cipher_plaque", (9, 18): "cipher_sun",
                         (18, 18): "cipher_star", (27, 18): "cipher_moon",
                         (18, 29): "cipher_seal" }.items():
        x, z = pos
        a.put(x, 4, z, f"gems:{marker}")
        a.put(x, 3, z, "minecraft:chiseled_polished_blackstone")
    a.sign(17, 4, 7, ("STAR CIPHER", "Day names first", "Distant fires", "Night names last"))
    a.sign(17, 4, 30, ("FINAL SEAL", "Three lights", "must answer", "before this one"))
    entry_grade(a, range(18, 21), ((3, 0), (4, 1), (5, 2)),
                "polished_blackstone_bricks", "polished_blackstone_brick", "south", 2)
    a.fill(18, 7, 5, 20, 7, 5, bronze)
    return a


def skywatch_lookout() -> Landmark:
    a = Landmark("skywatch_lookout", (31, 30, 31))
    base = "minecraft:stone_bricks"
    roof = "minecraft:cut_copper"
    a.fill(4, 0, 4, 26, 2, 26, base)
    a.fill(5, 3, 5, 25, 3, 25, "minecraft:polished_andesite")
    a.ring(4, 4, 26, 26, 4, "minecraft:chiseled_stone_bricks")
    for x, z in ((5, 5), (5, 25), (25, 5), (25, 25)):
        post(a, x, z, 8, "minecraft:stone_bricks")
    for y in range(4, 25):
        a.ring(10, 10, 20, 20, y, base if y % 4 else roof)
        a.fill(11, y, 11, 19, y, 19, "minecraft:air")
    a.fill(9, 24, 9, 21, 24, 21, roof)
    a.fill(11, 24, 11, 19, 24, 19, "minecraft:polished_andesite")
    a.put(11, 24, 15, "minecraft:air")
    a.fill(14, 4, 10, 16, 6, 10, "minecraft:air")
    for y in range(4, 25):
        a.put(11, y, 15, "minecraft:ladder[facing=east]")
    for y in (8, 12, 16, 20):
        for x in (13, 17):
            a.put(x, y, 10, "minecraft:light_blue_stained_glass_pane[north=true]")
            a.put(x, y, 20, "minecraft:light_blue_stained_glass_pane[south=true]")
        for z in (13, 17):
            a.put(10, y, z, "minecraft:light_blue_stained_glass_pane[west=true]")
            a.put(20, y, z, "minecraft:light_blue_stained_glass_pane[east=true]")
    for x in (10, 20):
        for z in (10, 20):
            a.pillar(x, z, 25, 27, base)
            a.put(x, 28, z, "minecraft:lightning_rod[facing=up]")
    a.put(15, 25, 15, "gems:vista_dial")
    a.put(15, 24, 15, "minecraft:lodestone")
    a.sign(15, 4, 8, ("SKYWATCH", "A sighting rest", "waits above", "the inner stair"))
    a.sign(15, 25, 12, ("SKYWATCH DIAL", "Glass to brass", "Dawn cut in rim", "Sky overhead"))
    for x, z in ((6, 15), (24, 15), (15, 24)):
        a.fill(x - 2, 3, z - 2, x + 2, 3, z + 2, roof)
        a.pillar(x, z, 4, 7, "minecraft:chiseled_stone_bricks")
        a.put(x, 8, z, "minecraft:sea_lantern")
    entry_grade(a, range(14, 17), ((2, 0), (3, 1), (4, 2)),
                "stone_bricks", "stone_brick", "south", 1)
    a.fill(14, 7, 4, 16, 7, 4, roof)
    return a


SITES = (
    ("lost_trail", "minecraft:plains", 51, 23, 710381),
    ("star_cipher", "minecraft:desert", 63, 31, 710382),
    ("skywatch_lookout", "minecraft:meadow", 67, 33, 710383),
)


def register(name: str, biome: str, spacing: int, separation: int, salt: int) -> None:
    write_json(DATA / f"tags/worldgen/biome/has_structure/{name}.json", {"values": [biome]})
    for folder in ("atlas_target", "themed_target"):
        write_json(DATA / f"tags/worldgen/structure/{folder}/{name}.json",
                   {"values": [f"gems:{name}"]})
    write_json(DATA / f"worldgen/template_pool/{name}.json", {
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {
            "element_type": "minecraft:single_pool_element", "location": f"gems:{name}",
            "processors": {"processors": []}, "projection": "rigid"}}],
    })
    write_json(DATA / f"worldgen/structure/{name}.json", {
        "type": "minecraft:jigsaw", "biomes": f"#gems:has_structure/{name}",
        "max_distance_from_center": 48, "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": 1, "spawn_overrides": {}, "start_height": {"absolute": 0},
        "start_pool": f"gems:{name}", "step": "surface_structures",
        "terrain_adaptation": "beard_thin", "use_expansion_hack": False,
    })
    write_json(DATA / f"worldgen/structure_set/{name}.json", {
        "placement": {"type": "minecraft:random_spread", "salt": salt,
                      "separation": separation, "spacing": spacing},
        "structures": [{"structure": f"gems:{name}", "weight": 1}],
    })


def main() -> None:
    sites = (lost_trail(), star_cipher(), skywatch_lookout())
    for site, (_, biome, spacing, separation, salt) in zip(sites, SITES):
        grade = [block for block, _ in site.blocks.values() if "_stairs[" in block]
        apron = [block for block, _ in site.blocks.values() if "_slab[" in block]
        if len(grade) < 9 or len(apron) < 3:
            raise ValueError(f"{site.name}: missing exterior three-rise entry grade")
        summary = site.write()
        register(site.name, biome, spacing, separation, salt)
        print(json.dumps(summary, sort_keys=True))


if __name__ == "__main__":
    main()
