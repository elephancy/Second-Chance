"""Author Second Chance places as vanilla Minecraft structure templates.

Run from the repository root with `python tools/adventure/generate_landmarks.py`.
No third-party Python packages are required. The source, rather than the binary
NBT, records the architecture and can be reviewed or refined by artists.
"""

from __future__ import annotations

import gzip
import json
import struct
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from inspect_schematic import read as read_schematic
import ally_architecture


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/data/gems"
SIZE = (47, 31, 47)
DATA_VERSION = 5023  # Minecraft 26.3 world version.
VERIFIED_QUOTES = frozenset(
    line
    for file in (DATA / "friends").glob("*.json")
    for line in json.loads(file.read_text(encoding="utf-8")).get("lines", [])
)


@dataclass(frozen=True)
class Tag:
    kind: int
    value: object


def string(value: str) -> Tag:
    return Tag(8, value)


def byte(value: int) -> Tag:
    return Tag(1, value)


def number(value: float) -> Tag:
    return Tag(6, value)


def integer(value: int) -> Tag:
    return Tag(3, value)


def compound(value: dict[str, Tag]) -> Tag:
    return Tag(10, value)


def sequence(kind: int, value: list[Tag]) -> Tag:
    return Tag(9, (kind, value))


def _utf(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _payload(tag: Tag) -> bytes:
    if tag.kind == 1:
        return struct.pack(">b", tag.value)
    if tag.kind == 3:
        return struct.pack(">i", tag.value)
    if tag.kind == 6:
        return struct.pack(">d", tag.value)
    if tag.kind == 8:
        return _utf(tag.value)
    if tag.kind == 9:
        kind, values = tag.value
        if any(value.kind != kind for value in values):
            raise ValueError("NBT list contains mixed types")
        return bytes([kind]) + struct.pack(">i", len(values)) + b"".join(_payload(value) for value in values)
    if tag.kind == 10:
        return b"".join(bytes([value.kind]) + _utf(name) + _payload(value)
                        for name, value in tag.value.items()) + b"\x00"
    raise ValueError(f"Unsupported NBT tag {tag.kind}")


def _nbt(root: Tag) -> bytes:
    return b"\x0a\x00\x00" + _payload(root)


def _state(raw: str) -> Tag:
    if "[" not in raw:
        return compound({"id": string(raw)})
    name, properties = raw[:-1].split("[", 1)
    props = dict(part.split("=", 1) for part in properties.split(","))
    encoded = compound({
        key: string(value) for key, value in sorted(props.items())
    })
    return compound({"id": string(name), "properties": encoded})


class Landmark:
    def __init__(self, name: str, size: tuple[int, int, int] = SIZE):
        self.name = name
        self.size = size
        self.blocks: dict[tuple[int, int, int], tuple[str, dict[str, Tag] | None]] = {}
        self.chests: list[tuple[int, int, int]] = []
        self.signs: list[tuple[int, int, int]] = []
        self.entities: list[Tag] = []

    def put(self, x: int, y: int, z: int, block: str, nbt: dict[str, Tag] | None = None) -> None:
        if not (0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]):
            raise ValueError(f"{self.name}: block outside template at {(x, y, z)}")
        self.blocks[(x, y, z)] = (block, nbt)

    def fill(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, block: str) -> None:
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    self.put(x, y, z, block)

    def pillar(self, x: int, z: int, bottom: int, top: int, block: str) -> None:
        self.fill(x, bottom, z, x, top, z, block)

    def ring(self, x0: int, z0: int, x1: int, z1: int, y: int, block: str) -> None:
        self.fill(x0, y, z0, x1, y, z0, block)
        self.fill(x0, y, z1, x1, y, z1, block)
        self.fill(x0, y, z0, x0, y, z1, block)
        self.fill(x1, y, z0, x1, y, z1, block)

    def chest(self, x: int, y: int, z: int, facing: str = "south", loot: str | None = None) -> None:
        self.put(x, y, z, f"minecraft:chest[facing={facing}]", {
            "id": string("minecraft:chest"),
            "LootTable": string(f"gems:chests/{loot or self.name}"),
        })
        self.chests.append((x, y, z))

    def supply_barrel(self, x: int, y: int, z: int, loot: str) -> None:
        self.put(x, y, z, "minecraft:barrel[facing=up]", {
            "id": string("minecraft:barrel"),
            "LootTable": string(f"gems:chests/{loot}"),
        })

    def jigsaw(self, x: int, y: int, z: int, orientation: str,
               connector: str, pool: str = "minecraft:empty") -> None:
        self.put(x, y, z, f"minecraft:jigsaw[orientation={orientation}]", {
            "id": string("minecraft:jigsaw"),
            "name": string(connector), "target": string(connector),
            "pool": string(pool), "final_state": string("minecraft:air"),
            "joint": string("aligned"),
        })

    def sign(self, x: int, y: int, z: int, lines: tuple[str, str, str, str], rotation: int = 8) -> None:
        # Since 1.21.5, sign text components are stored directly as NBT.
        # JSON wrapped in an NBT string renders literally on modern clients.
        messages = [compound({"text": string(line)}) for line in lines]
        face = compound({
            "messages": sequence(10, messages),
            "filtered_messages": sequence(10, messages),
            "color": string("black"),
        })
        self.put(x, y, z, f"minecraft:oak_sign[rotation={rotation}]", {
            "id": string("minecraft:sign"), "front_text": face, "back_text": face,
        })
        self.signs.append((x, y, z))

    def quote_sign(self, x: int, y: int, z: int,
                   lines: tuple[str, str, str, str], rotation: int = 8) -> None:
        if len(lines) != 4 or any(len(line) > 15 for line in lines):
            raise ValueError(f"{self.name}: quote does not fit a sign")
        quote = " ".join(line for line in lines if line)
        if quote not in VERIFIED_QUOTES:
            raise ValueError(f"{self.name}: sign is not an exact curated quote")
        current = self.blocks.get((x, y, z))
        support = self.blocks.get((x, y - 1, z))
        if current is not None and current[0] != "minecraft:air":
            raise ValueError(f"{self.name}: quote would replace a placed block")
        if support is None or support[0] == "minecraft:air":
            raise ValueError(f"{self.name}: quote sign has no support")
        self.sign(x, y, z, lines, rotation)

    def friend(self, x: int, y: int, z: int, friend_id: str) -> None:
        if not (0 <= x < self.size[0] and 0 <= y < self.size[1]
                and 0 <= z < self.size[2]):
            raise ValueError(f"{self.name}: friend outside template at {(x, y, z)}")
        self.entities.append(compound({
            "pos": sequence(6, [number(x + 0.5), number(y), number(z + 0.5)]),
            "blockPos": sequence(3, [integer(x), integer(y), integer(z)]),
            "nbt": compound({
                "id": string("gems:friend"),
                "FriendId": string(friend_id),
                "PersistenceRequired": byte(1),
            }),
        }))

    def painted_verdict(self, x: int, y: int, z: int) -> None:
        if self.name != "framefall_gallery" or not (0 <= x < self.size[0]
                and 0 <= y < self.size[1] and 0 <= z < self.size[2]):
            raise ValueError("The Painted Verdict belongs inside the Framefall gallery")
        self.entities.append(compound({
            "pos": sequence(6, [number(x + 0.5), number(y), number(z + 0.5)]),
            "blockPos": sequence(3, [integer(x), integer(y), integer(z)]),
            "nbt": compound({
                "id": string("gems:painted_verdict"),
                "FramefallArena": byte(1),
                "PersistenceRequired": byte(1),
            }),
        }))

    def last_arrival(self, x: int, y: int, z: int) -> None:
        if self.name != "lastlight_exchange" or not (0 <= x < self.size[0]
                and 0 <= y < self.size[1] and 0 <= z < self.size[2]):
            raise ValueError("The Last Arrival belongs inside Lastlight Exchange")
        self.entities.append(compound({
            "pos": sequence(6, [number(x + 0.5), number(y), number(z + 0.5)]),
            "blockPos": sequence(3, [integer(x), integer(y), integer(z)]),
            "nbt": compound({
                "id": string("gems:last_arrival"),
                "LastlightArena": byte(1),
                "PersistenceRequired": byte(1),
            }),
        }))

    def unbroken_punchline(self, x: int, y: int, z: int) -> None:
        if self.name != "rehearsal_works" or not (0 <= x < self.size[0]
                and 0 <= y < self.size[1] and 0 <= z < self.size[2]):
            raise ValueError("The Unbroken Punchline belongs inside Rehearsal Works")
        self.entities.append(compound({
            "pos": sequence(6, [number(x + 0.5), number(y), number(z + 0.5)]),
            "blockPos": sequence(3, [integer(x), integer(y), integer(z)]),
            "nbt": compound({
                "id": string("gems:unbroken_punchline"),
                "RehearsalArena": byte(1),
                "PersistenceRequired": byte(1),
            }),
        }))

    def second_move_solver(self, x: int, y: int, z: int) -> None:
        if self.name != "branch_vault" or not (0 <= x < self.size[0]
                and 0 <= y < self.size[1] and 0 <= z < self.size[2]):
            raise ValueError("The Second-Move Solver belongs inside Branch Vault")
        self.entities.append(compound({
            "pos": sequence(6, [number(x + 0.5), number(y), number(z + 0.5)]),
            "blockPos": sequence(3, [integer(x), integer(y), integer(z)]),
            "nbt": compound({
                "id": string("gems:second_move_solver"),
                "BranchArena": byte(1),
                "PersistenceRequired": byte(1),
            }),
        }))

    def red_line_between(self, x: int, y: int, z: int) -> None:
        if self.name != "redline_house" or not (0 <= x < self.size[0]
                and 0 <= y < self.size[1] and 0 <= z < self.size[2]):
            raise ValueError("The Red Line Between belongs inside Redline House")
        self.entities.append(compound({
            "pos": sequence(6, [number(x + 0.5), number(y), number(z + 0.5)]),
            "blockPos": sequence(3, [integer(x), integer(y), integer(z)]),
            "nbt": compound({
                "id": string("gems:red_line_between"),
                "RedlineArena": byte(1),
                "PersistenceRequired": byte(1),
            }),
        }))

    def empty_encore(self, x: int, y: int, z: int) -> None:
        if self.name != "hushwire" or not (0 <= x < self.size[0]
                and 0 <= y < self.size[1] and 0 <= z < self.size[2]):
            raise ValueError("The Empty Encore belongs inside Hushwire")
        self.entities.append(compound({
            "pos": sequence(6, [number(x + 0.5), number(y), number(z + 0.5)]),
            "blockPos": sequence(3, [integer(x), integer(y), integer(z)]),
            "nbt": compound({
                "id": string("gems:empty_encore"),
                "HushwireArena": byte(1),
                "PersistenceRequired": byte(1),
            }),
        }))

    def stray_cadenza(self, x: int, y: int, z: int) -> None:
        if self.name != "unwritten_court" or not (0 <= x < self.size[0]
                and 0 <= y < self.size[1] and 0 <= z < self.size[2]):
            raise ValueError("The Stray Cadenza belongs inside Unwritten Court")
        self.entities.append(compound({
            "pos": sequence(6, [number(x + 0.5), number(y), number(z + 0.5)]),
            "blockPos": sequence(3, [integer(x), integer(y), integer(z)]),
            "nbt": compound({
                "id": string("gems:stray_cadenza"),
                "UnwrittenArena": byte(1),
                "PersistenceRequired": byte(1),
            }),
        }))

    def sketchguard(self, x: int, y: int, z: int) -> None:
        if self.name not in {"framefall_atelier", "framefall_garden"} or not (
                0 <= x < self.size[0] and 0 <= y < self.size[1]
                and 0 <= z < self.size[2]):
            raise ValueError("Sketchguards belong in the Framefall approach")
        self.entities.append(compound({
            "pos": sequence(6, [number(x + 0.5), number(y), number(z + 0.5)]),
            "blockPos": sequence(3, [integer(x), integer(y), integer(z)]),
            "nbt": compound({
                "id": string("gems:sketchguard"),
                "PersistenceRequired": byte(1),
            }),
        }))

    def regional_sentry(self, x: int, y: int, z: int, sentry: str) -> None:
        homes = {
            "signal_warden": {"lastlight_market", "lastlight_signal_box"},
            "understudy": {"rehearsal_prop_foundry", "rehearsal_costume_house"},
            "margin_shade": {"branch_escape_tower", "branch_map_room"},
            "watchkeeper": {"redline_infirmary", "redline_watchpost"},
            "relay_phantom": {"hushwire_dishfield", "hushwire_relay_bunker"},
            "uncast": {"unwritten_stewards_annex", "unwritten_mask_archive"},
        }
        if (sentry not in homes or self.name not in homes[sentry] or not (
                0 <= x < self.size[0] and 0 <= y < self.size[1]
                and 0 <= z < self.size[2])):
            raise ValueError(f"{sentry} does not belong in {self.name}")
        self.entities.append(compound({
            "pos": sequence(6, [number(x + 0.5), number(y), number(z + 0.5)]),
            "blockPos": sequence(3, [integer(x), integer(y), integer(z)]),
            "nbt": compound({
                "id": string(f"gems:{sentry}"),
                "PersistenceRequired": byte(1),
            }),
        }))

    def foundation(self, floor: str, edge: str, clear_top: int = 22) -> None:
        # A raised plinth carries the architecture over ordinary plains terrain.
        # Clearing only the authored footprint avoids cutting a whole chunk.
        self.fill(3, 0, 3, 43, 3, 43, edge)
        self.fill(4, 4, 4, 42, 4, 42, floor)
        self.fill(4, 5, 4, 42, clear_top, 42, "minecraft:air")
        for y in range(1, 5):
            self.fill(20, y, 43 + (4 - y), 26, y, 46, edge)

    def write(self) -> dict:
        palette = sorted({block for block, _ in self.blocks.values()})
        indices = {block: index for index, block in enumerate(palette)}
        blocks = []
        for (x, y, z), (block, extra) in sorted(self.blocks.items()):
            entry = {"pos": sequence(3, [integer(x), integer(y), integer(z)]),
                     "state": integer(indices[block])}
            if extra:
                entry["nbt"] = compound(extra)
            blocks.append(compound(entry))
        root = compound({
            "size": sequence(3, [integer(n) for n in self.size]),
            "palette": sequence(10, [_state(block) for block in palette]),
            "blocks": sequence(10, blocks),
            "entities": sequence(10, self.entities),
            "DataVersion": integer(DATA_VERSION),
        })
        target = DATA / "structure" / f"{self.name}.nbt"
        target.parent.mkdir(parents=True, exist_ok=True)
        packed = bytearray(gzip.compress(_nbt(root), compresslevel=9, mtime=0))
        # Python 3.12 exposes zlib's platform OS byte (0x0a on Windows),
        # while Python 3.13 writes 0xff. The NBT payload is identical; use
        # the canonical unknown-OS header so both runtimes hash the same.
        packed[9] = 0xff
        target.write_bytes(packed)
        counts = Counter(block.split("[", 1)[0] for block, _ in self.blocks.values())
        return {
            "name": self.name, "size": self.size, "block_count": len(blocks),
            "palette_size": len(palette), "solid_blocks": len(blocks) - counts["minecraft:air"],
            "chests": self.chests, "signs": self.signs,
            "friends": len(self.entities),
            "dominant_blocks": counts.most_common(10),
        }


def framefall() -> Landmark:
    a = Landmark("framefall_gallery")
    a.foundation("minecraft:polished_andesite", "minecraft:stone_bricks", 14)
    # Gallery wings frame an open sculpture court. The copper roof has three
    # glazed roof cuts, and exterior towers make the silhouette readable afar.
    for x0, z0, x1, z1 in [(5, 6, 17, 39), (29, 6, 41, 39), (17, 6, 29, 17)]:
        for y in range(5, 15):
            a.ring(x0, z0, x1, z1, y, "minecraft:calcite" if y in (5, 14) else "minecraft:stone_bricks")
        a.fill(x0 + 1, 5, z0 + 1, x1 - 1, 12, z1 - 1, "minecraft:air")
        a.fill(x0, 15, z0, x1, 15, z1, "minecraft:oxidized_cut_copper")
        a.fill(x0 + 2, 15, z0 + 2, x1 - 2, 15, z1 - 2, "minecraft:light_blue_stained_glass")
    for x in (6, 40):
        for z in (7, 38):
            a.fill(x - 2, 5, z - 2, x + 2, 22, z + 2, "minecraft:tuff_bricks")
            a.fill(x - 1, 7, z - 1, x + 1, 21, z + 1, "minecraft:air")
            a.fill(x - 2, 23, z - 2, x + 2, 23, z + 2, "minecraft:oxidized_copper")
            a.put(x, 24, z, "minecraft:sea_lantern")
    for z in range(9, 38, 6):
        for x in (6, 16, 30, 40):
            a.pillar(x, z, 5, 14, "minecraft:polished_deepslate")
            a.put(x, 11, z, "minecraft:sea_lantern")
    # The inward colonnades are open; the outward bays are glazed, which
    # makes the court visible from the side rather than hiding it in a box.
    for z in (11, 20, 29, 35):
        for x in (5, 41):
            a.fill(x, 7, z - 1, x, 11, z + 1, "minecraft:light_blue_stained_glass")
            a.put(x, 12, z, "minecraft:calcite")
        for x in (17, 29):
            a.fill(x, 6, z - 1, x, 11, z + 1, "minecraft:air")
            a.put(x, 12, z, "minecraft:calcite")
    for x0, x1 in ((5, 17), (29, 41)):
        for slope in range(1, 5):
            a.fill(x0 + slope, 15 + slope, 6, x1 - slope, 15 + slope, 39,
                   "minecraft:oxidized_cut_copper")
        a.fill(x0 + 5, 20, 9, x1 - 5, 20, 36, "minecraft:light_blue_stained_glass")
        for z in (10, 22, 34):
            a.fill(x0 + 3, 18, z, x1 - 3, 19, z + 1, "minecraft:cut_copper")
    for slope in range(1, 4):
        a.fill(17, 15 + slope, 6 + slope, 29, 15 + slope, 17 - slope,
               "minecraft:oxidized_cut_copper")
    for x in (6, 40):
        for z in (7, 38):
            a.ring(x - 2, z - 2, x + 2, z + 2, 16, "minecraft:cut_copper")
            a.fill(x - 1, 24, z - 1, x + 1, 24, z + 1, "minecraft:oxidized_copper")
            a.put(x, 25, z, "minecraft:sea_lantern")
    for z in (12, 22, 32):
        for x in (11, 35):
            a.fill(x - 2, 8, z, x + 2, 11, z, "minecraft:blue_stained_glass")
            a.fill(x - 1, 9, z, x + 1, 10, z, "minecraft:yellow_concrete")
            a.put(x, 9, z, "minecraft:black_concrete")
    # A visible route enters through the southern arcade and reaches the court.
    a.fill(21, 5, 39, 25, 12, 42, "minecraft:air")
    a.fill(21, 5, 5, 25, 12, 7, "minecraft:air")
    for z in range(19, 39):
        for x in range(18, 29):
            if 19 <= z <= 38 and (x in (18, 28) or z in (19, 38)):
                a.put(x, 4, z, "minecraft:cut_copper")
            elif (x + z) % 6 == 0:
                a.put(x, 4, z, "minecraft:cyan_terracotta")
    for y in range(5, 13):
        width = max(0, 5 - abs(y - 8))
        a.fill(23 - width, y, 28, 23 + width, y, 28, "minecraft:polished_blackstone")
    a.fill(21, 12, 26, 25, 12, 30, "minecraft:oxidized_copper")
    a.put(23, 13, 28, "minecraft:sea_lantern")
    for x, z in ((19, 21), (27, 21), (19, 35), (27, 35)):
        a.fill(x - 1, 5, z - 1, x + 1, 5, z + 1, "minecraft:moss_block")
        a.put(x, 6, z, "minecraft:azalea_leaves[persistent=true]")
    for z in (20, 28, 36):
        a.fill(20, 5, z, 26, 5, z, "minecraft:cut_copper")
    # Three fixed jigsaw connections turn the gallery into an expedition
    # campus. The connector blocks sit on the template edge, so attached
    # pieces do not overlap the root piece's bounding box.
    a.fill(40, 5, 21, 46, 9, 25, "minecraft:air")
    a.fill(40, 4, 21, 46, 4, 25, "minecraft:polished_andesite")
    a.fill(0, 5, 21, 6, 9, 25, "minecraft:air")
    a.fill(0, 4, 21, 6, 4, 25, "minecraft:polished_andesite")
    a.fill(21, 5, 0, 25, 9, 7, "minecraft:air")
    a.fill(21, 4, 0, 25, 4, 7, "minecraft:polished_andesite")
    a.jigsaw(46, 5, 23, "east_up", "gems:framefall_atelier_entry",
             "gems:framefall_atelier")
    a.jigsaw(0, 5, 23, "west_up", "gems:framefall_garden_entry",
             "gems:framefall_garden")
    a.jigsaw(23, 5, 0, "north_up", "gems:framefall_observatory_entry",
             "gems:framefall_observatory")
    a.chest(9, 5, 34, "east")
    a.chest(37, 5, 12, "west")
    a.sign(23, 5, 35, ("The image moved.", "The wall did not.", "Check the angle.", ""))
    a.painted_verdict(23, 5, 23)
    return a


def framefall_gallery_walk() -> Landmark:
    """New starts keep the original Gallery and use an approach-ready Atelier."""
    a = framefall()
    a.name = "framefall_gallery_walk"
    a.jigsaw(46, 5, 23, "east_up", "gems:framefall_atelier_entry",
             "gems:framefall_atelier_walk")
    return a


def framefall_gallery_facade() -> Landmark:
    """Future starts select the pale promenade without changing saved pieces."""
    a = framefall_gallery_walk()
    a.name = "framefall_gallery_facade"
    a.jigsaw(46, 5, 23, "east_up", "gems:framefall_atelier_entry",
             "gems:framefall_atelier_facade")
    return a


def framefall_gallery_transition_v3() -> Landmark:
    """New starts alone select the widened visitor approach."""
    a = framefall_gallery_facade()
    a.name = "framefall_gallery_transition_v3"
    a.jigsaw(46, 5, 23, "east_up", "gems:framefall_atelier_entry",
             "gems:framefall_atelier_transition_v3")
    for x in (12, 34):
        a.pillar(x, 42, 7, 14, "gems:gallery_plane")
        a.put(x, 15, 42, "gems:gallery_prism")
    a.fill(18, 14, 42, 28, 14, 42, "gems:gallery_plane")
    a.put(23, 15, 42, "gems:gallery_prism")
    return a


def framefall_atelier() -> Landmark:
    a = Landmark("framefall_atelier", (19, 18, 19))
    a.fill(0, 0, 0, 18, 3, 18, "minecraft:stone_bricks")
    a.fill(0, 4, 0, 18, 4, 18, "minecraft:polished_andesite")
    a.fill(1, 5, 1, 17, 16, 17, "minecraft:air")
    for y in range(5, 12):
        a.ring(2, 2, 17, 16, y, "minecraft:calcite")
    a.fill(3, 5, 3, 16, 10, 15, "minecraft:air")
    for x in (3, 8, 13, 17):
        a.pillar(x, 2, 5, 12, "minecraft:cut_copper")
        a.pillar(x, 16, 5, 12, "minecraft:cut_copper")
    for z in (5, 11):
        for x in (2, 17):
            a.fill(x, 7, z, x, 9, z + 2, "minecraft:blue_stained_glass")
    for slope in range(5):
        a.fill(2 + slope, 12 + slope, 2, 17 - slope, 12 + slope, 16,
               "minecraft:oxidized_cut_copper")
    a.fill(8, 17, 4, 11, 17, 14, "minecraft:light_blue_stained_glass")
    a.fill(0, 5, 7, 4, 9, 11, "minecraft:air")
    a.fill(5, 5, 5, 7, 6, 7, "minecraft:polished_deepslate")
    a.fill(12, 5, 10, 15, 6, 13, "minecraft:polished_deepslate")
    for x, z in ((6, 6), (14, 12)):
        a.put(x, 7, z, "minecraft:sea_lantern")
    a.chest(15, 5, 5, "west", "framefall_gallery")
    a.sign(10, 5, 13, ("Restoration note:", "the frame is", "original. The view", "is an addition."))
    a.sign(5, 5, 14, ("i don't like", "drawing horses", "", ""))
    a.sign(7, 5, 8, ("Blue tracks you.", "Orange locks.", "Use real cover.", ""))
    a.sketchguard(10, 5, 9)
    a.jigsaw(0, 5, 9, "west_up", "gems:framefall_atelier_entry")
    return a


def framefall_atelier_walk() -> Landmark:
    """The new eastern door feeds the old root-facing Atelier passage."""
    a = framefall_atelier()
    a.name = "framefall_atelier_walk"
    a.fill(13, 5, 0, 18, 11, 3, "minecraft:air")
    a.fill(13, 4, 0, 18, 4, 4, "minecraft:polished_andesite")
    a.fill(13, 12, 0, 18, 12, 0, "minecraft:oxidized_cut_copper")
    a.jigsaw(16, 5, 0, "north_up", "gems:framefall_promenade_entry",
             "gems:framefall_promenade")
    return a


def framefall_atelier_facade() -> Landmark:
    a = framefall_atelier_walk()
    a.name = "framefall_atelier_facade"
    a.jigsaw(16, 5, 0, "north_up", "gems:framefall_promenade_entry",
             "gems:framefall_promenade_facade")
    return a


def framefall_atelier_transition_v3() -> Landmark:
    a = framefall_atelier_facade()
    a.name = "framefall_atelier_transition_v3"
    a.jigsaw(16, 5, 0, "north_up", "gems:framefall_promenade_entry",
             "gems:framefall_promenade_transition_v3")
    for x in (4, 14):
        a.pillar(x, 1, 6, 11, "gems:gallery_plane")
        a.put(x, 12, 1, "gems:gallery_prism")
    return a


def framefall_promenade() -> Landmark:
    """A framed walking route clears only its authored forest corridor."""
    a = Landmark("framefall_promenade", (19, 27, 47))
    for z in range(47):
        progress = 46 - z
        floor_y = 4 + progress * 6 // 46
        center_x = 9 + progress // 20
        width = 3 if z > 15 else 4
        for x in range(max(0, center_x - width), min(18, center_x + width) + 1):
            a.fill(x, 0, z, x, floor_y - 1, z, "minecraft:tuff_bricks")
            rim = abs(x - center_x) == width
            tile = ("minecraft:oxidized_cut_copper" if rim else
                    "minecraft:cut_copper" if z % 8 == 0 else
                    "minecraft:polished_andesite")
            a.put(x, floor_y, z, tile)
            # Air is limited to the route. It removes a tree crown over the
            # walk without cutting a rectangular clearing through the forest.
            a.fill(x, floor_y + 1, z, x, 25, z, "minecraft:air")
        if z > 0 and 4 + (47 - z) * 6 // 46 > floor_y:
            for x in range(max(0, center_x - 2), min(18, center_x + 2) + 1):
                a.put(x, floor_y, z,
                      "minecraft:stone_brick_stairs[facing=north,half=bottom]")
    # An open terminal court makes the Gallery visible before the forest walk.
    for z in range(2, 15):
        floor_y = 4 + (46 - z) * 6 // 46
        for x in range(0, 19):
            if abs(x - 9) + abs(z - 8) > 7:
                continue
            a.fill(x, 0, z, x, floor_y - 1, z, "minecraft:tuff_bricks")
            a.put(x, floor_y, z,
                  "minecraft:cut_copper" if (x + z) % 7 == 0 else
                  "minecraft:polished_andesite")
            a.fill(x, floor_y + 1, z, x, 25, z, "minecraft:air")
    # The route is cut into a forest slope. Dress only the two cut faces, with
    # deeper piers and copper joints, so the surrounding trees remain a grove
    # rather than becoming a rectangular clearing.
    for z in range(3, 47):
        progress = 46 - z
        floor_y = 4 + progress * 6 // 46
        center_x = 9 + progress // 20
        width = 3 if z > 15 else 4
        court_radius = max(0, 7 - abs(z - 8)) if z < 15 else 0
        edge = max(width + 1, court_radius + 1)
        for side in (-1, 1):
            face_x = center_x + side * edge
            back_x = face_x + side
            if not (0 <= face_x < 19 and 0 <= back_x < 19):
                continue
            a.fill(back_x, 0, z, back_x, floor_y + 5, z,
                   "minecraft:tuff_bricks")
            a.fill(face_x, 0, z, face_x, floor_y + 5, z,
                   "minecraft:deepslate_bricks")
            a.put(face_x, floor_y + 6, z, "minecraft:oxidized_cut_copper")
            a.put(back_x, floor_y + 6, z, "minecraft:cut_copper")
            if z % 8 == 3:
                a.fill(face_x, floor_y + 1, z, face_x, floor_y + 7, z,
                       "minecraft:polished_deepslate")
                a.put(face_x, floor_y + 8, z, "minecraft:cut_copper")
            elif z % 8 == 6:
                a.put(face_x, floor_y + 3, z,
                      "minecraft:light_blue_stained_glass")
                a.put(face_x, floor_y + 4, z,
                      "minecraft:light_blue_stained_glass")
    # Offset frames repeat the Gallery's copper and blue lens language while
    # leaving the middle of the path fully open to the sky and to players.
    for z in (18, 30, 42):
        floor_y = 4 + (46 - z) * 6 // 46
        center_x = 9 + (46 - z) // 20
        left, right = center_x - 5, center_x + 5
        for x in (left, right):
            if 0 <= x < 19:
                a.fill(x, 0, z, x, floor_y, z, "minecraft:tuff_bricks")
                a.pillar(x, z, floor_y + 1, floor_y + 12,
                         "minecraft:polished_deepslate")
                a.put(x, floor_y + 13, z, "minecraft:cut_copper")
        for x in range(max(0, left), min(18, right) + 1):
            if x not in (left, right):
                a.put(x, floor_y + 13, z, "minecraft:oxidized_cut_copper")
        a.put(center_x, floor_y + 13, z, "minecraft:sea_lantern")
    a.fill(14, 0, 8, 16, 10, 10, "minecraft:tuff_bricks")
    a.fill(14, 11, 8, 16, 12, 10, "minecraft:oxidized_cut_copper")
    a.put(15, 13, 9, "minecraft:sea_lantern")
    a.sign(15, 14, 9, ("Follow the", "copper frames", "to the Gallery.", ""))
    a.jigsaw(9, 5, 46, "south_up", "gems:framefall_promenade_entry")
    return a


def framefall_promenade_facade() -> Landmark:
    """Pale portals and gallery bays on the original sealed promenade walls."""
    a = framefall_promenade()
    a.name = "framefall_promenade_facade"
    # The seed-A hillside sheds a falling-water column through the visitor
    # boundary above this route. Catch it on a shallow, contained copper hood
    # while retaining six clear blocks above the unchanged walking surface.
    a.fill(4, 17, 0, 16, 17, 7, "minecraft:oxidized_cut_copper")
    a.ring(4, 0, 16, 7, 18, "minecraft:cut_copper")
    a.put(10, 16, 3, "minecraft:sea_lantern")
    for first, last in ((21, 26), (36, 41)):
        for z in range(first, last + 1):
            progress = 46 - z
            floor_y = 4 + progress * 6 // 46
            center_x = 9 + progress // 20
            for side in (-1, 1):
                face_x = center_x + side * 4
                for y in range(floor_y + 1, floor_y + 6):
                    block = a.blocks.get((face_x, y, z), ("", None))[0]
                    if block != "minecraft:light_blue_stained_glass":
                        a.put(face_x, y, z, "minecraft:calcite")
    for z in (18, 30, 42):
        floor_y = 4 + (46 - z) * 6 // 46
        center_x = 9 + (46 - z) // 20
        for x in (center_x - 5, center_x + 5):
            a.pillar(x, z, floor_y + 1, floor_y + 12,
                     "minecraft:calcite")
            a.put(x, floor_y + 13, z, "minecraft:cut_copper")
        for x in range(center_x - 4, center_x + 5):
            a.put(x, floor_y + 13, z, "minecraft:polished_diorite")
        a.put(center_x, floor_y + 13, z, "minecraft:sea_lantern")
    return a


def framefall_promenade_transition_v3() -> Landmark:
    """A wider, authored forest-to-gallery passage for future starts only."""
    previous = framefall_promenade_facade()
    a = Landmark("framefall_promenade_transition_v3", (31, 27, 47))
    for (x, y, z), (state, nbt) in previous.blocks.items():
        a.put(x + 6, y, z, state, nbt)
    a.signs = [(x + 6, y, z) for x, y, z in previous.signs]

    # Broad, irregular shoulders replace the hard forest/trench boundary.
    # Only cells authored in this new template are touched; old pieces and
    # already saved chunks keep their existing blocks.
    for z in range(2, 46):
        progress = 46 - z
        floor_y = 4 + progress * 6 // 46
        center = 15 + progress // 20
        shoulder = 2 + (1 if z % 9 in (1, 2, 3, 4) else 0)
        for side in (-1, 1):
            for distance in range(7, 11 + shoulder):
                x = center + side * distance
                if not 0 <= x < 31:
                    continue
                a.fill(x, 0, z, x, floor_y - 1, z, "minecraft:tuff_bricks")
                a.put(x, floor_y, z,
                      "gems:gallery_plane" if distance == 7 and z % 8 < 3 else
                      "minecraft:polished_andesite")
                a.fill(x, floor_y + 1, z, x, 25, z, "minecraft:air")
        if z % 9 == 3:
            for side in (-1, 1):
                x = center + side * 8
                a.pillar(x, z, floor_y + 1, floor_y + 4,
                         "minecraft:polished_deepslate")
                a.put(x, floor_y + 5, z, "minecraft:cut_copper")

    # One destination-scale open lens gate is legible down the walking axis.
    # The visitor lane remains nine blocks wide and unobstructed below it.
    gate_z = 12
    gate_y = 4 + (46 - gate_z) * 6 // 46
    for x in (7, 23):
        a.pillar(x, gate_z, gate_y + 1, gate_y + 12,
                 "minecraft:calcite")
        a.put(x, gate_y + 13, gate_z, "minecraft:cut_copper")
    a.fill(7, gate_y + 13, gate_z, 23, gate_y + 13, gate_z,
           "minecraft:cut_copper")
    a.fill(10, gate_y + 14, gate_z, 20, gate_y + 14, gate_z,
           "minecraft:oxidized_cut_copper")
    a.fill(13, gate_y + 15, gate_z, 17, gate_y + 15, gate_z,
           "minecraft:calcite")
    a.put(15, gate_y + 15, gate_z, "minecraft:sea_lantern")
    for x in (11, 19):
        a.put(x, gate_y + 13, gate_z, "gems:gallery_prism")
    a.fill(9, gate_y + 8, gate_z, 9, gate_y + 11, gate_z,
           "minecraft:light_blue_stained_glass")
    a.fill(21, gate_y + 8, gate_z, 21, gate_y + 11, gate_z,
           "minecraft:light_blue_stained_glass")
    return a


def framefall_garden() -> Landmark:
    a = Landmark("framefall_garden", (19, 15, 19))
    a.fill(0, 0, 0, 18, 3, 18, "minecraft:stone_bricks")
    a.fill(0, 4, 0, 18, 4, 18, "minecraft:moss_block")
    a.fill(0, 5, 0, 18, 14, 18, "minecraft:air")
    for x in (2, 9, 16):
        for z in (2, 16):
            a.pillar(x, z, 5, 10, "minecraft:calcite")
            a.put(x, 11, z, "minecraft:cut_copper")
    for z in (2, 9, 16):
        for x in (2, 16):
            a.pillar(x, z, 5, 10, "minecraft:calcite")
            a.put(x, 11, z, "minecraft:cut_copper")
    a.ring(2, 2, 16, 16, 12, "minecraft:oxidized_cut_copper")
    a.fill(3, 4, 8, 18, 4, 10, "minecraft:polished_andesite")
    a.fill(8, 4, 3, 10, 4, 15, "minecraft:polished_andesite")
    for x, z in ((5, 5), (13, 5), (5, 13), (13, 13)):
        a.fill(x - 1, 5, z - 1, x + 1, 5, z + 1, "minecraft:cut_copper")
        a.pillar(x, z, 6, 8, "minecraft:polished_blackstone")
        a.put(x, 9, z, "minecraft:amethyst_block")
    a.fill(7, 5, 7, 11, 5, 11, "minecraft:polished_deepslate")
    a.pillar(9, 9, 6, 10, "minecraft:calcite")
    a.fill(8, 11, 8, 10, 11, 10, "minecraft:oxidized_copper")
    a.chest(4, 5, 15, "south", "framefall_gallery")
    a.sign(11, 5, 15, ("The labels were", "written first.", "The statues", "disagree."))
    a.sign(12, 5, 11, ("Stand behind", "stone when the", "line turns gold.", ""))
    a.quote_sign(7, 5, 14, ("Ragebaited to", "touch grass", "", ""))
    a.sketchguard(13, 5, 9)
    a.jigsaw(18, 5, 9, "east_up", "gems:framefall_garden_entry")
    return a


def framefall_observatory() -> Landmark:
    a = Landmark("framefall_observatory", (17, 25, 17))
    a.fill(0, 0, 0, 16, 3, 16, "minecraft:stone_bricks")
    a.fill(0, 4, 0, 16, 4, 16, "minecraft:polished_andesite")
    a.fill(0, 5, 0, 16, 24, 16, "minecraft:air")
    for y in range(5, 19):
        a.ring(2, 2, 14, 13, y, "minecraft:stone_bricks")
    a.fill(3, 5, 3, 13, 17, 12, "minecraft:air")
    for x in (2, 14):
        for z in (2, 13):
            a.pillar(x, z, 5, 22, "minecraft:polished_deepslate")
    for y in (9, 14, 19):
        a.ring(1, 1, 15, 14, y, "minecraft:cut_copper")
    for x in (5, 9):
        for z in (2, 13):
            a.fill(x, 7, z, x + 2, 11, z, "minecraft:light_blue_stained_glass")
    a.fill(6, 5, 13, 10, 10, 16, "minecraft:air")
    for rise in range(1, 13):
        x = 3 + (rise % 9)
        a.fill(x, 5, 5 + rise // 4, x + 1, 5 + rise, 6 + rise // 4,
               "minecraft:polished_andesite")
    a.fill(3, 19, 3, 13, 19, 12, "minecraft:oxidized_cut_copper")
    a.fill(5, 19, 5, 11, 19, 10, "minecraft:light_blue_stained_glass")
    for y in range(20, 24):
        a.ring(3 + (y - 20), 3 + (y - 20), 13 - (y - 20), 12 - (y - 20),
               y, "minecraft:oxidized_copper")
    a.chest(11, 5, 10, "west", "framefall_gallery")
    a.sign(8, 5, 11, ("From above, the", "paths align.", "From below,", "they never did."))
    a.jigsaw(8, 5, 16, "south_up", "gems:framefall_observatory_entry")
    return a


def attach_east(root: Landmark, satellite: str, floor: str) -> None:
    """Carve a walkable connection from the main landmark into a side building."""
    root.fill(39, 5, 21, 46, 9, 25, "minecraft:air")
    root.fill(39, 4, 21, 46, 4, 25, floor)
    root.jigsaw(46, 5, 23, "east_up", f"gems:{satellite}_entry", f"gems:{satellite}")


def east_satellite(name: str, plinth: str, floor: str) -> Landmark:
    a = Landmark(name, (21, 22, 21))
    a.fill(0, 0, 0, 20, 3, 20, plinth)
    a.fill(0, 4, 0, 20, 4, 20, floor)
    a.fill(0, 5, 0, 20, 21, 20, "minecraft:air")
    return a


def finish_east_satellite(a: Landmark) -> Landmark:
    a.fill(0, 5, 8, 4, 9, 12, "minecraft:air")
    a.jigsaw(0, 5, 10, "west_up", f"gems:{a.name}_entry")
    if len(a.chests) != 1 or not a.signs:
        raise ValueError(f"{a.name}: side building needs loot and lore")
    return a


def attach_west(root: Landmark, satellite: str, floor: str) -> None:
    """A second route out of the landmark, opposite its existing side building."""
    root.fill(0, 5, 21, 7, 9, 25, "minecraft:air")
    root.fill(0, 4, 21, 7, 4, 25, floor)
    root.jigsaw(0, 5, 23, "west_up", f"gems:{satellite}_entry", f"gems:{satellite}")


def west_satellite(name: str, plinth: str, floor: str,
                   height: int = 22) -> Landmark:
    a = Landmark(name, (21, height, 21))
    a.fill(0, 0, 0, 20, 3, 20, plinth)
    a.fill(0, 4, 0, 20, 4, 20, floor)
    a.fill(0, 5, 0, 20, height - 1, 20, "minecraft:air")
    return a


def finish_west_satellite(a: Landmark) -> Landmark:
    a.fill(16, 5, 8, 20, 9, 12, "minecraft:air")
    a.jigsaw(20, 5, 10, "east_up", f"gems:{a.name}_entry")
    if len(a.chests) != 1 or not a.signs:
        raise ValueError(f"{a.name}: side building needs loot and lore")
    return a


def lastlight_signal_box() -> Landmark:
    a = west_satellite("lastlight_signal_box", "minecraft:stone_bricks", "minecraft:cut_sandstone")
    # A raised switch room watches three short track ends. Its brick base,
    # glazed operating room, overhanging roof and exterior stair make the
    # lookout recognizable from the market as well as reachable on foot.
    for z in (4, 9, 15):
        a.fill(2, 5, z, 13, 5, z, "minecraft:dark_oak_planks")
        a.fill(2, 6, z - 1, 13, 6, z - 1, "minecraft:iron_bars")
    for y in range(5, 11):
        a.ring(4, 4, 15, 16, y, "minecraft:brick")
    a.fill(15, 5, 9, 15, 7, 11, "minecraft:air")
    for z in (6, 14):
        a.fill(4, 7, z, 4, 8, z, "minecraft:yellow_stained_glass")
        a.fill(15, 7, z, 15, 8, z, "minecraft:yellow_stained_glass")
    for x in (6, 12):
        a.fill(x, 7, 4, x, 8, 4, "minecraft:yellow_stained_glass")
        a.fill(x, 7, 16, x, 8, 16, "minecraft:yellow_stained_glass")
    for x in (4, 15):
        for z in (4, 16):
            a.pillar(x, z, 5, 15, "minecraft:brick")
    a.fill(4, 12, 4, 15, 12, 16, "minecraft:dark_oak_planks")
    a.fill(5, 13, 5, 14, 15, 15, "minecraft:yellow_stained_glass")
    a.fill(6, 13, 6, 13, 15, 14, "minecraft:air")
    for rise in range(4):
        a.fill(3 + rise, 16 + rise, 3, 16 - rise, 16 + rise, 17,
               "minecraft:oxidized_cut_copper")
    a.fill(8, 20, 5, 11, 20, 15, "minecraft:yellow_stained_glass")
    for z in (5, 10, 15):
        a.fill(4, 13, z, 4, 15, z + 1, "minecraft:yellow_stained_glass")
        a.fill(15, 13, z, 15, 15, z + 1, "minecraft:yellow_stained_glass")
    # The upper glass shell otherwise reads as decoration from below. Open a
    # real doorway and run a narrow stair along the east face to its landing.
    a.fill(14, 13, 9, 15, 14, 10, "minecraft:air")
    a.fill(15, 12, 8, 18, 12, 10, "minecraft:dark_oak_planks")
    for step in range(8):
        z = 17 - step
        y = 5 + step
        a.put(17, y, z, "minecraft:dark_oak_stairs[facing=north,half=bottom]")
        if step and z >= 13:
            a.fill(17, 5, z, 17, y - 1, z, "minecraft:brick")
        a.put(18, y + 1, z, "minecraft:dark_oak_fence")
    for z in (8, 10):
        a.put(18, 13, z, "minecraft:dark_oak_fence")
    for x in (7, 9, 11):
        a.put(x, 13, 7, "minecraft:lever[face=floor,facing=north,powered=false]")
    a.put(10, 13, 12, "gems:lastlight_witness")
    for x in (6, 10, 14):
        a.put(x, 7, 6, "minecraft:redstone_lamp[lit=true]")
    a.put(10, 11, 10, "minecraft:lantern[hanging=true]")
    a.chest(6, 5, 14, "east", "lastlight_exchange")
    a.sign(14, 5, 15, ("The board says", "on time.", "The clock says", "ask the board."))
    a.regional_sentry(10, 5, 12, "signal_warden")
    return finish_west_satellite(a)


def lastlight_signal_box_v2() -> Landmark:
    a = lastlight_signal_box()
    a.name = "lastlight_signal_box_v2"
    for z in (4, 16):
        a.fill(5, 10, z, 14, 10, z, "gems:station_ashlar")
    for x in (4, 15):
        a.fill(x, 10, 5, x, 10, 15, "gems:station_ashlar")
    a.fill(7, 18, 3, 13, 18, 3, "gems:station_ashlar")
    a.put(10, 19, 3, "gems:station_dial")
    return a


def rehearsal_costume_house() -> Landmark:
    a = west_satellite("rehearsal_costume_house", "minecraft:deepslate_tiles", "minecraft:spruce_planks")
    # Bright dressing alcoves flank a narrow runway; a loft stores the props
    # that did not survive the first rehearsal.
    for x in (3, 17):
        a.fill(x, 5, 3, x, 13, 17, "minecraft:stripped_spruce_log[axis=y]")
    for z in (3, 17):
        a.fill(3, 5, z, 17, 13, z, "minecraft:spruce_planks")
    for z in (6, 11, 15):
        a.fill(4, 5, z, 8, 10, z, "minecraft:purple_wool")
        a.fill(12, 5, z, 16, 10, z, "minecraft:red_wool")
        a.fill(5, 6, z, 7, 8, z, "minecraft:air")
        a.fill(13, 6, z, 15, 8, z, "minecraft:air")
    a.fill(4, 14, 4, 16, 14, 16, "minecraft:dark_oak_planks")
    a.fill(8, 14, 4, 12, 14, 16, "minecraft:air")
    for rise in range(4):
        a.fill(2 + rise, 16 + rise, 2, 18 - rise, 16 + rise, 18,
               "minecraft:oxidized_cut_copper")
    a.fill(8, 20, 5, 12, 20, 15, "minecraft:purple_stained_glass")
    for x in (4, 8, 12, 16):
        a.fill(x, 7, 2, x + 1, 12, 2,
               "minecraft:purple_wool" if x % 8 == 4 else "minecraft:red_wool")
    for z in (5, 10, 16):
        a.put(10, 6, z, "minecraft:sea_lantern")
    a.chest(5, 5, 14, "south", "rehearsal_works")
    a.sign(15, 5, 5, ("If it fits,", "keep it.", "If it doesn't,", "call it a prop."))
    a.regional_sentry(10, 5, 8, "understudy")
    return finish_west_satellite(a)


def rehearsal_costume_house_v2() -> Landmark:
    a = rehearsal_costume_house()
    a.name = "rehearsal_costume_house_v2"
    for x in (4, 10, 16):
        a.pillar(x, 1, 6, 14, "gems:stage_timber")
        a.put(x, 15, 1, "minecraft:cut_copper")
    a.fill(4, 16, 1, 16, 16, 1, "gems:stage_curtain")
    a.fill(7, 12, 2, 13, 12, 2, "gems:stage_curtain")
    return a


def branch_map_room() -> Landmark:
    a = west_satellite("branch_map_room", "minecraft:deepslate_bricks", "minecraft:polished_deepslate")
    # A compass floor and four unequal shelves make the right exit legible
    # only after players have crossed the room.
    for y in range(5, 12):
        a.ring(3, 3, 17, 17, y, "minecraft:tuff_bricks")
    a.fill(4, 5, 4, 16, 10, 16, "minecraft:air")
    for d in range(1, 7):
        for x, z in ((10 - d, 10), (10 + d, 10), (10, 10 - d), (10, 10 + d)):
            a.put(x, 5, z, "minecraft:calcite")
    a.put(10, 5, 10, "minecraft:sea_lantern")
    a.put(10, 6, 7, "gems:branch_witness")
    for x, z0, z1 in ((5, 5, 8), (5, 12, 16), (15, 5, 7), (15, 11, 16)):
        a.fill(x, 6, z0, x, 9, z1, "minecraft:bookshelf")
    for rise in range(3):
        a.ring(2 + rise, 2 + rise, 18 - rise, 18 - rise,
               12 + rise, "minecraft:chiseled_tuff")
    a.fill(5, 14, 5, 15, 14, 15, "minecraft:light_blue_stained_glass")
    for x in (4, 16):
        for z in (4, 16):
            a.pillar(x, z, 12, 17, "minecraft:cut_copper")
            a.put(x, 18, z, "minecraft:sea_lantern")
    a.chest(6, 5, 15, "east", "branch_vault")
    a.sign(14, 5, 6, ("Every route", "is drawn here.", "Only one of them", "was drawn first."))
    a.quote_sign(12, 5, 14, ("the game", "never existed", "", ""))
    a.quote_sign(8, 5, 14, ("no matter what", "i choose ill be", "on the winning", "team"))
    a.regional_sentry(10, 6, 10, "margin_shade")
    return finish_west_satellite(a)


def branch_map_room_v2() -> Landmark:
    a = branch_map_room()
    a.name = "branch_map_room_v2"
    for x in (5, 15):
        a.pillar(x, 3, 5, 12, "gems:folio_stone")
    a.fill(5, 13, 3, 15, 13, 3, "gems:folio_stone")
    a.put(10, 14, 3, "gems:branch_index")
    return a


def redline_watchpost() -> Landmark:
    a = west_satellite("redline_watchpost", "minecraft:stone_bricks", "minecraft:spruce_planks")
    # An open rescue lookout gives a safe line back to the infirmary.
    for x in (4, 16):
        for z in (4, 16):
            a.pillar(x, z, 5, 18, "minecraft:stripped_spruce_log[axis=y]")
    a.fill(4, 10, 4, 16, 10, 16, "minecraft:dark_oak_planks")
    a.fill(7, 10, 7, 13, 10, 13, "minecraft:air")
    a.fill(4, 18, 4, 16, 18, 16, "minecraft:dark_oak_planks")
    for y in (11, 14, 17):
        a.ring(4, 4, 16, 16, y, "minecraft:spruce_fence")
    for step in range(1, 10):
        a.fill(5 + step, 5 + step, 6, 6 + step, 5 + step, 7, "minecraft:stone_bricks")
    a.fill(7, 19, 7, 13, 19, 13, "minecraft:yellow_stained_glass")
    a.put(10, 20, 10, "minecraft:sea_lantern")
    a.chest(5, 5, 15, "east", "redline_house")
    a.sign(14, 5, 5, ("If you can see", "the light,", "someone can see", "you coming."))
    a.put(15, 4, 15, "minecraft:gold_block")
    a.put(15, 4, 14, "gems:redline_aid_finish")
    a.regional_sentry(15, 5, 10, "watchkeeper")
    return finish_west_satellite(a)


def redline_watchpost_v2() -> Landmark:
    a = redline_watchpost()
    a.name = "redline_watchpost_v2"
    # A brick beacon tower and pale sightline crown make the rescue station
    # readable from the main court and match the infirmary's shelter crest.
    for x in (4, 16):
        for z in (4, 16):
            a.pillar(x, z, 5, 13, "gems:refuge_brick")
    for y in (6, 10, 14):
        a.ring(4, 4, 16, 16, y, "gems:refuge_brick")
    a.fill(4, 18, 4, 16, 18, 4, "gems:refuge_brick")
    for x in (5, 8, 12, 15):
        a.put(x, 17, 4, "gems:shelter_emblem")
    a.put(10, 19, 4, "gems:shelter_emblem")
    return a


def hushwire_relay_bunker() -> Landmark:
    a = west_satellite("hushwire_relay_bunker", "minecraft:blackstone",
                       "gems:hushwire_slate", height=27)
    # A small relay crypt shares the record hall's pointed blue glazing and
    # pale tracery. Keep the dog-leg entry and receiver floor open for combat.
    for y in range(5, 11):
        a.ring(3, 3, 17, 17, y, "gems:hushwire_slate")
    a.fill(4, 5, 4, 16, 9, 16, "minecraft:air")
    for x in (3, 17):
        for z in (6, 14):
            a.fill(x, 7, z, x, 8, z + 1, "minecraft:oxidized_copper_grate")
            a.pillar(x, z - 1, 5, 12, "gems:hushwire_tracery")
    for x in (6, 14):
        for z in (3, 17):
            a.fill(x, 7, z, x + 1, 8, z, "minecraft:oxidized_copper_grate")
    # The old square grille openings made the side elevations read as a box.
    # Tall paired lancets, recessed into slate, now repeat the hall windows.
    for x in (3, 17):
        for z in (7, 13):
            a.fill(x, 6, z, x, 10, z + 1, "gems:hushwire_tracery")
            a.fill(x, 7, z, x, 9, z, "minecraft:blue_stained_glass")
            a.put(x, 10, z, "minecraft:light_blue_stained_glass")
            a.put(x, 8, z + 1, "minecraft:blue_stained_glass")
    for z in (4, 10, 16):
        a.fill(1, 5, z, 2, 8, z, "gems:hushwire_slate")
        a.fill(2, 9, z, 2, 12, z, "gems:hushwire_tracery")
        a.put(1, 9, z, "minecraft:polished_blackstone_stairs[facing=east]")
    for z in (4, 16):
        a.fill(18, 5, z, 19, 8, z, "gems:hushwire_slate")
        a.fill(18, 9, z, 18, 12, z, "gems:hushwire_tracery")
        a.put(19, 9, z, "minecraft:polished_blackstone_stairs[facing=west]")
    for z in (3, 17):
        for x in (7, 10, 13):
            a.fill(x, 7, z, x, 9, z, "minecraft:blue_stained_glass")
            a.put(x, 10, z, "minecraft:light_blue_stained_glass")
            a.fill(x - 1, 6, z - (1 if z == 3 else -1),
                   x - 1, 11, z - (1 if z == 3 else -1),
                   "gems:hushwire_tracery")
    # A dog-leg blast baffle leaves the center lane and all three receiver
    # marks clear for the Relay Phantom's warning pattern.
    a.fill(13, 5, 8, 13, 8, 10, "gems:hushwire_slate")
    a.fill(14, 5, 8, 16, 5, 8, "minecraft:cut_copper")
    for x0, x1, z in ((5, 8, 6), (12, 15, 6), (5, 8, 15)):
        a.fill(x0, 5, z, x1, 5, z, "minecraft:cut_copper")
        for x in range(x0, x1 + 1, 2):
            if (x, z) != (14, 6):
                a.put(x, 6, z, "minecraft:note_block")
    for x, z in ((6, 9), (14, 9), (10, 6)):
        a.put(x, 10, z, "minecraft:redstone_lamp[lit=true]")
    for x, z in ((6, 12), (14, 12), (10, 16)):
        a.put(x, 4, z, "minecraft:blue_concrete")
        a.put(x, 10, z, "minecraft:sea_lantern")
    # The heavy entry porch sits outside the floor's clear east-side corridor.
    a.fill(18, 5, 6, 19, 9, 7, "gems:hushwire_slate")
    a.fill(18, 5, 13, 19, 9, 14, "gems:hushwire_slate")
    a.fill(16, 10, 6, 20, 10, 14, "gems:hushwire_tracery")
    a.fill(17, 11, 7, 20, 11, 13, "minecraft:deepslate_tiles")
    for x in (2, 18):
        for z in (5, 15):
            a.fill(x, 5, z, x, 11, z + 1, "gems:hushwire_slate")
            a.fill(x, 12, z, x, 12, z + 1, "gems:hushwire_tracery")
    for x in range(2, 19):
        roof_y = 12 + (8 - abs(x - 10)) * 3 // 4
        a.fill(x, roof_y, 2, x, roof_y, 18, "minecraft:deepslate_tiles")
    for z in (3, 17):
        for x in range(3, 18):
            roof_y = 12 + (8 - abs(x - 10)) * 3 // 4
            a.fill(x, 11, z, x, roof_y - 1, z, "gems:hushwire_slate")
    # A stepped cornice and raised spine make the roof legible from the hall.
    for z in (1, 19):
        for x in range(2, 19):
            roof_y = 12 + (8 - abs(x - 10)) * 3 // 4
            a.put(x, roof_y, z, "minecraft:polished_blackstone_stairs[facing=north]"
                  if z == 1 else "minecraft:polished_blackstone_stairs[facing=south]")
    a.fill(9, 19, 8, 11, 19, 17, "gems:hushwire_tracery")
    for z in (3, 17):
        for x in (5, 10, 15):
            a.put(x, 13, z, "gems:hushwire_tracery")
            a.put(x, 14, z, "minecraft:blue_stained_glass")
    for z in (3, 17):
        a.fill(9, 13, z, 11, 14, z, "minecraft:blue_stained_glass")
        a.put(10, 15, z, "minecraft:light_blue_stained_glass")
    # Two uneven signal turrets give the crypt its own silhouette.
    a.fill(5, 14, 5, 7, 16, 7, "gems:hushwire_slate")
    for y in (15, 16, 17):
        for x in (5, 7):
            for z in (5, 7):
                a.put(x, y, z, "gems:hushwire_tracery")
    a.fill(6, 16, 5, 6, 17, 5, "minecraft:blue_stained_glass")
    a.fill(6, 16, 7, 6, 17, 7, "minecraft:blue_stained_glass")
    a.fill(5, 18, 5, 7, 18, 7, "minecraft:deepslate_tiles")
    a.put(6, 19, 6, "minecraft:lightning_rod[facing=up]")
    a.fill(13, 14, 5, 15, 16, 7, "gems:hushwire_slate")
    a.put(14, 17, 6, "minecraft:sea_lantern")
    a.fill(14, 18, 6, 14, 19, 6, "minecraft:cut_copper")
    a.put(14, 20, 6, "minecraft:lightning_rod[facing=up]")
    # The public front is a signal chapel, not a second grid of windows. A
    # walk-in portico leads through the existing receiver room; the broad
    # diamond lens and unequal towers make its silhouette readable at range.
    a.fill(8, 5, 17, 11, 8, 17, "minecraft:air")
    a.fill(7, 4, 17, 12, 4, 20, "minecraft:cut_copper")
    a.fill(8, 5, 18, 11, 8, 20, "minecraft:air")
    for x in (7, 12):
        a.fill(x, 5, 18, x, 10, 18, "gems:hushwire_tracery")
        a.put(x, 11, 18, "minecraft:oxidized_cut_copper")
    a.fill(8, 9, 18, 11, 9, 18, "minecraft:polished_blackstone_stairs[facing=south]")
    # A shallow, original block mosaic reads as a tuning dial rather than a
    # copied rose window. Its bright center is the signal origin.
    for x in range(5, 14):
        for y in range(10, 19):
            distance = abs(x - 9) + abs(y - 14)
            if distance > 4:
                continue
            if distance == 4:
                block = "gems:hushwire_tracery"
            elif distance == 0:
                block = "minecraft:sea_lantern"
            elif x == 9 or y == 14:
                block = "minecraft:light_blue_stained_glass"
            else:
                block = "minecraft:blue_stained_glass"
            a.put(x, y, 18, block)
    # An offset enclosed receiver tower balances the shorter western mast.
    # Both sit outside the east connector and the interior combat floor.
    for y in range(5, 20):
        a.ring(14, 17, 18, 20, y, "gems:hushwire_slate")
    a.fill(15, 6, 18, 17, 18, 19, "minecraft:air")
    for y in range(10, 17):
        a.put(16, y, 20, "minecraft:blue_stained_glass")
    a.put(16, 17, 20, "minecraft:light_blue_stained_glass")
    for y in range(12, 19):
        a.put(18, y, 18, "minecraft:blue_stained_glass")
    a.fill(14, 20, 17, 18, 20, 20, "minecraft:oxidized_cut_copper")
    a.fill(15, 21, 18, 17, 21, 19, "gems:hushwire_tracery")
    a.fill(16, 22, 18, 16, 23, 19, "minecraft:cut_copper")
    a.put(16, 24, 19, "minecraft:sea_lantern")
    a.put(16, 25, 19, "minecraft:lightning_rod[facing=up]")
    a.pillar(3, 18, 5, 18, "gems:hushwire_slate")
    a.put(3, 19, 18, "gems:hushwire_tracery")
    a.put(3, 20, 18, "minecraft:sea_lantern")
    a.put(3, 21, 18, "minecraft:lightning_rod[facing=up]")
    for x in (5, 13):
        a.fill(x, 5, 18, x, 8, 19, "gems:hushwire_slate")
        a.put(x, 9, 19, "minecraft:oxidized_copper_bulb[lit=true]")
    a.chest(5, 5, 15, "east", "hushwire")
    a.sign(14, 5, 6, ("The signal", "came through.", "The sender", "did not."))
    a.regional_sentry(10, 5, 12, "relay_phantom")
    return finish_west_satellite(a)


def unwritten_mask_archive() -> Landmark:
    a = west_satellite("unwritten_mask_archive", "minecraft:deepslate_bricks", "minecraft:polished_andesite")
    # Paired mask walls lead to a quiet, empty center: a place for the people
    # who were not cast in the story.
    for y in range(5, 13):
        a.ring(3, 3, 17, 17, y, "minecraft:quartz_block")
    a.fill(4, 5, 4, 16, 11, 16, "minecraft:air")
    for z in (5, 9, 13):
        for x in (4, 16):
            a.fill(x, 6, z, x, 9, z + 2, "minecraft:polished_blackstone")
            a.put(x, 7, z + 1, "minecraft:white_wool")
    for rise in range(3):
        a.ring(2 + rise, 2 + rise, 18 - rise, 18 - rise,
               13 + rise, "minecraft:dark_oak_planks")
    a.fill(5, 15, 5, 15, 15, 15, "minecraft:purple_stained_glass")
    for x in (5, 10, 15):
        a.fill(x, 7, 3, x + 1, 10, 3, "minecraft:polished_blackstone")
        a.put(x, 8, 2, "minecraft:white_wool")
    a.put(10, 6, 10, "minecraft:sea_lantern")
    a.put(10, 5, 12, "gems:court_west_station")
    a.chest(5, 5, 15, "east", "unwritten_court")
    a.sign(14, 5, 5, ("A part for", "everyone.", "The empty mask", "is still a part."))
    a.regional_sentry(10, 5, 8, "uncast")
    return finish_west_satellite(a)


def unwritten_mask_archive_v2() -> Landmark:
    a = unwritten_mask_archive()
    a.name = "unwritten_mask_archive_v2"
    for y in (5, 11):
        a.ring(3, 3, 17, 17, y, "gems:court_plaster")
    for x in (5, 10, 15):
        a.put(x, 8, 2, "gems:mask_relief")
    for z in (6, 10, 14):
        a.put(3, 9, z, "gems:mask_relief")
        a.put(17, 9, z, "gems:mask_relief")
    a.ring(2, 2, 18, 18, 15, "gems:court_plaster")
    return a


def lastlight_market() -> Landmark:
    a = east_satellite("lastlight_market", "minecraft:stone_bricks", "minecraft:cut_sandstone")
    # Three staggered old-stock stalls face a newer copper shelter. A raised
    # luggage bridge crosses above the central walkway.
    for z0 in (2, 8, 14):
        a.fill(8, 5, z0, 18, 5, z0 + 4, "minecraft:dark_oak_planks")
        for x in (8, 18):
            for z in (z0, z0 + 4):
                a.pillar(x, z, 6, 10, "minecraft:dark_oak_log[axis=y]")
        a.fill(7, 11, z0 - 1, 19, 11, z0 + 5, "minecraft:oxidized_cut_copper")
        a.fill(9, 12, z0, 17, 12, z0 + 4, "minecraft:yellow_stained_glass")
        for x in (10, 13, 16):
            a.put(x, 6, z0 + 1, "minecraft:barrel[facing=up]")
    a.fill(4, 14, 3, 6, 14, 17, "minecraft:dark_oak_planks")
    for z in (3, 10, 17):
        a.pillar(5, z, 5, 13, "minecraft:chiseled_sandstone")
        a.put(5, 15, z, "minecraft:lantern")
    a.chest(17, 6, 16, "west", "lastlight_exchange")
    a.sign(6, 5, 17, ("Older stock.", "Newer labels.", "Ask which clock", "priced it."))
    a.regional_sentry(6, 5, 12, "signal_warden")
    return finish_east_satellite(a)


def lastlight_market_v2() -> Landmark:
    a = lastlight_market()
    a.name = "lastlight_market_v2"
    # One continuous departure canopy ties the three old-stock stalls to the
    # signal box instead of leaving them as repeated isolated booths.
    for x in (7, 18):
        a.pillar(x, 19, 5, 13, "gems:station_ashlar")
    a.fill(7, 14, 2, 18, 14, 19, "minecraft:oxidized_cut_copper")
    for z in (4, 10, 16):
        a.fill(9, 15, z, 16, 15, z, "gems:station_ashlar")
        a.put(13, 16, z, "gems:station_dial")
    a.fill(8, 5, 19, 18, 5, 19, "gems:station_ashlar")
    return a


def rehearsal_prop_foundry() -> Landmark:
    a = east_satellite("rehearsal_prop_foundry", "minecraft:deepslate_tiles", "minecraft:spruce_planks")
    # The front room is a workshop; the back is a vertical prop store whose
    # catwalk gives a different route above the encounter approach.
    for x in (5, 12, 18):
        for z in (3, 17):
            a.pillar(x, z, 5, 18, "minecraft:stripped_spruce_log[axis=y]")
    a.fill(5, 16, 3, 18, 16, 17, "minecraft:dark_oak_planks")
    a.fill(7, 16, 5, 16, 16, 15, "minecraft:air")
    for y in (9, 14):
        a.fill(5, y, 3, 18, y, 4, "minecraft:dark_oak_planks")
        a.fill(5, y, 16, 18, y, 17, "minecraft:dark_oak_planks")
        a.fill(17, y, 5, 18, y, 15, "minecraft:dark_oak_planks")
    for z in (5, 9, 13):
        a.fill(9, 5, z, 14, 6, z + 1, "minecraft:polished_andesite")
        a.put(10, 7, z, "minecraft:anvil[facing=north]")
        a.put(13, 7, z, "minecraft:crafting_table")
    for x in (6, 11, 16):
        a.fill(x, 17, 5, x, 20, 15, "minecraft:chain")
        a.put(x, 16, 10, "minecraft:lantern[hanging=true]")
    a.fill(16, 5, 5, 18, 11, 7, "minecraft:barrel[facing=up]")
    a.chest(7, 5, 15, "east", "rehearsal_works")
    a.sign(6, 5, 6, ("Props are real", "until the scene", "ends. The scene", "has not ended."))
    a.quote_sign(6, 5, 14, ("Yoo wait let's", "implement this", "power system in", "fish story 2"))
    a.regional_sentry(6, 5, 10, "understudy")
    return finish_east_satellite(a)


def rehearsal_prop_foundry_v2() -> Landmark:
    a = rehearsal_prop_foundry()
    a.name = "rehearsal_prop_foundry_v2"
    for x in (5, 12, 18):
        a.pillar(x, 18, 5, 16, "gems:stage_timber")
        a.put(x, 17, 18, "minecraft:cut_copper")
    a.fill(5, 18, 18, 18, 18, 18, "gems:stage_curtain")
    a.fill(9, 13, 3, 14, 13, 3, "gems:stage_curtain")
    return a


def branch_escape_tower() -> Landmark:
    a = east_satellite("branch_escape_tower", "minecraft:deepslate_bricks", "minecraft:polished_deepslate")
    # Two offset stair cores expose a deliberate wrong turn. The roof platform
    # is visible from the vault and gives a real overlook of the branches.
    for y in range(5, 19):
        a.ring(5, 4, 18, 17, y, "minecraft:tuff_bricks")
    a.fill(6, 5, 5, 17, 17, 16, "minecraft:air")
    for y in (9, 13, 18):
        a.fill(5, y, 4, 18, y, 17, "minecraft:chiseled_tuff")
        a.fill(7, y, 6, 16, y, 15, "minecraft:air")
    for rise in range(1, 14):
        x = 7 + (rise % 5)
        z = 7 + (rise // 5) * 3
        a.fill(x, 5, z, x + 1, 5 + rise, z + 1, "minecraft:deepslate_bricks")
    for y in (7, 12, 16):
        a.fill(5, y, 8, 5, y + 2, 11, "minecraft:iron_bars")
        a.fill(18, y, 10, 18, y + 2, 13, "minecraft:iron_bars")
    a.ring(4, 3, 19, 18, 19, "minecraft:cut_copper")
    a.put(12, 20, 11, "minecraft:sea_lantern")
    a.chest(16, 5, 15, "west", "branch_vault")
    a.sign(6, 5, 15, ("The first exit", "is the fastest.", "The second one", "is still here."))
    a.regional_sentry(16, 5, 10, "margin_shade")
    return finish_east_satellite(a)


def branch_escape_tower_v2() -> Landmark:
    a = branch_escape_tower()
    a.name = "branch_escape_tower_v2"
    for x in (5, 18):
        a.pillar(x, 3, 6, 18, "gems:folio_stone")
        a.put(x, 19, 3, "minecraft:cut_copper")
    a.fill(5, 20, 3, 18, 20, 3, "gems:folio_stone")
    a.put(12, 21, 3, "gems:branch_index")
    return a


def redline_infirmary() -> Landmark:
    a = east_satellite("redline_infirmary", "minecraft:stone_bricks", "minecraft:spruce_planks")
    # A long triage hall branches into three open treatment alcoves. The
    # clerestory and balcony make it read as a refuge from outside.
    for y in range(5, 13):
        a.ring(4, 3, 18, 18, y, "minecraft:spruce_planks")
    a.fill(5, 5, 4, 17, 11, 17, "minecraft:air")
    for z in (5, 10, 15):
        a.fill(10, 5, z, 17, 5, z + 2, "minecraft:white_wool")
        a.fill(11, 6, z, 16, 6, z, "minecraft:spruce_trapdoor[facing=south,half=top,open=false]")
        a.put(16, 7, z + 2, "minecraft:lantern")
    for x in (7, 12, 17):
        a.fill(x, 8, 3, x + 1, 10, 3, "minecraft:yellow_stained_glass")
        a.fill(x, 8, 18, x + 1, 10, 18, "minecraft:yellow_stained_glass")
    for slope in range(1, 5):
        a.fill(4 + slope, 13 + slope, 3, 18 - slope, 13 + slope, 18,
               "minecraft:dark_oak_planks")
    a.fill(9, 18, 5, 13, 18, 16, "minecraft:glass")
    a.chest(6, 5, 16, "east", "redline_house")
    a.sign(7, 5, 6, ("No one needs", "permission to", "rest here.", "Even you."))
    a.put(7, 4, 15, "minecraft:gold_block")
    a.put(7, 4, 14, "gems:redline_aid_start")
    a.regional_sentry(7, 5, 11, "watchkeeper")
    return finish_east_satellite(a)


def redline_infirmary_v2() -> Landmark:
    a = redline_infirmary()
    a.name = "redline_infirmary_v2"
    # Broad brick eaves and repeated shelter marks give the medical wing a
    # facade distinct from the lookout while retaining the aid start tile.
    for x in (5, 17):
        a.pillar(x, 3, 5, 12, "gems:refuge_brick")
    a.fill(5, 13, 3, 17, 13, 3, "gems:refuge_brick")
    for x in (7, 11, 15):
        a.fill(x - 1, 9, 3, x + 1, 9, 3, "gems:refuge_brick")
        a.put(x, 10, 3, "gems:shelter_emblem")
    a.fill(4, 14, 4, 18, 14, 4, "minecraft:white_concrete")
    a.put(11, 14, 3, "gems:shelter_emblem")
    return a


def hushwire_dishfield() -> Landmark:
    a = east_satellite("hushwire_dishfield", "minecraft:blackstone", "gems:hushwire_slate")
    # Three unequal listening spires echo the hall's pointed silhouette. The
    # trench still offers cover against the Relay Phantom's warning pattern.
    for x, z, height in ((8, 6, 14), (16, 8, 19), (12, 16, 16)):
        a.fill(x - 1, 5, z - 1, x + 1, height, z + 1, "gems:hushwire_slate")
        for side in (-1, 1):
            a.fill(x + side * 2, 5, z, x + side * 2, height - 3, z,
                   "gems:hushwire_slate")
            a.put(x + side * 2, height - 2, z, "gems:hushwire_tracery")
        a.fill(x - 2, height, z - 2, x + 2, height, z + 2,
               "gems:hushwire_tracery")
        a.fill(x - 1, height + 1, z - 1, x + 1, height + 1, z + 1,
               "minecraft:blue_stained_glass")
        a.put(x, height + 2, z, "minecraft:sea_lantern")
        if height + 3 < a.size[1]:
            a.put(x, height + 3, z, "minecraft:lightning_rod[facing=up]")
    a.fill(5, 4, 10, 19, 4, 12, "minecraft:air")
    a.fill(5, 2, 10, 19, 2, 12, "gems:hushwire_slate")
    for x in (6, 13, 19):
        a.put(x, 5, 9, "minecraft:redstone_lamp[lit=true]")
    a.chest(18, 5, 17, "west", "hushwire")
    a.sign(6, 5, 17, ("Three receivers.", "Four voices.", "One of them is", "the weather."))
    for x, z in ((2, 14), (10, 14), (6, 18)):
        a.put(x, 4, z, "minecraft:blue_concrete")
    a.regional_sentry(6, 5, 14, "relay_phantom")
    return finish_east_satellite(a)


def unwritten_stewards_annex() -> Landmark:
    a = east_satellite("unwritten_stewards_annex", "minecraft:deepslate_bricks", "minecraft:polished_andesite")
    # Small practical rooms sit below an extravagant rehearsal balcony; this
    # building gives the Court a backstage human scale.
    for y in range(5, 12):
        a.ring(4, 3, 18, 17, y, "minecraft:stone_bricks")
    a.fill(5, 5, 4, 17, 10, 16, "minecraft:air")
    for x in (9, 14):
        a.fill(x, 5, 4, x, 10, 16, "minecraft:dark_oak_planks")
        a.fill(x, 5, 9, x, 8, 11, "minecraft:air")
    a.fill(4, 12, 3, 18, 12, 17, "minecraft:dark_oak_planks")
    a.fill(7, 12, 5, 15, 12, 15, "minecraft:air")
    for x in (5, 9, 14, 18):
        a.pillar(x, 3, 13, 19, "minecraft:polished_blackstone_bricks")
        a.pillar(x, 17, 13, 19, "minecraft:polished_blackstone_bricks")
    a.fill(4, 20, 3, 18, 20, 17, "minecraft:oxidized_cut_copper")
    a.fill(6, 20, 5, 16, 20, 15, "minecraft:red_stained_glass")
    for x in (6, 11, 16):
        a.put(x, 6, 15, "minecraft:barrel[facing=up]")
    a.put(11, 5, 12, "gems:court_east_station")
    a.chest(16, 5, 6, "west", "unwritten_court")
    a.sign(6, 5, 15, ("The stage gets", "the applause.", "The crew gets", "the keys."))
    a.regional_sentry(11, 5, 10, "uncast")
    return finish_east_satellite(a)


def unwritten_stewards_annex_v2() -> Landmark:
    a = unwritten_stewards_annex()
    a.name = "unwritten_stewards_annex_v2"
    for y in (5, 10):
        a.ring(4, 3, 18, 17, y, "gems:court_plaster")
    for x in (5, 9, 14, 18):
        a.pillar(x, 3, 13, 19, "gems:court_plaster")
        a.put(x, 18, 2, "gems:mask_relief")
    a.fill(4, 20, 3, 18, 20, 3, "gems:court_plaster")
    a.put(11, 21, 3, "gems:mask_relief")
    return a


def lastlight() -> Landmark:
    a = Landmark("lastlight_exchange")
    a.foundation("minecraft:cut_sandstone", "minecraft:stone_bricks", 14)
    # The two perpendicular sets of copper tracks remain aligned with both
    # attack phases even if world generation rotates the whole station.
    for track in (13, 22, 31):
        a.fill(track, 4, 8, track, 4, 34, "minecraft:cut_copper")
        a.fill(8, 4, track, 34, 4, track, "minecraft:cut_copper")
    # An old station carries a clearly newer off-axis annex and tall clock.
    for y in range(5, 14):
        a.ring(6, 10, 35, 35, y, "minecraft:brick" if y % 3 else "minecraft:chiseled_sandstone")
    a.fill(7, 5, 11, 34, 12, 34, "minecraft:air")
    a.fill(6, 14, 10, 35, 14, 35, "minecraft:dark_oak_planks")
    for z in range(11, 35, 4):
        a.fill(8, 15, z, 33, 15, z, "minecraft:dark_oak_log[axis=x]")
    for z in (14, 22, 30):
        a.fill(6, 7, z, 6, 10, z + 2, "minecraft:yellow_stained_glass")
        a.fill(35, 7, z, 35, 10, z + 2, "minecraft:yellow_stained_glass")
    for slope in range(1, 7):
        a.fill(6 + slope, 14 + slope, 10, 35 - slope, 14 + slope, 35,
               "minecraft:dark_oak_planks")
    a.fill(19, 21, 13, 22, 21, 32, "minecraft:yellow_stained_glass")
    for z in (14, 22, 30):
        a.fill(6, 7, z, 6, 10, z + 2, "minecraft:air")
        a.fill(35, 7, z, 35, 10, z + 2, "minecraft:air")
        for x in (6, 35):
            a.put(x, 11, z + 1, "minecraft:chiseled_sandstone")
    for x in range(9, 35, 5):
        a.pillar(x, 37, 5, 11, "minecraft:cut_sandstone")
        a.put(x, 12, 37, "minecraft:lantern")
    a.fill(8, 12, 35, 35, 12, 39, "minecraft:oxidized_cut_copper")
    a.fill(19, 5, 35, 24, 11, 42, "minecraft:air")
    a.fill(8, 5, 18, 33, 5, 20, "minecraft:polished_andesite")
    a.fill(8, 5, 25, 33, 5, 27, "minecraft:polished_andesite")
    for x in (11, 18, 25, 32):
        a.pillar(x, 22, 5, 13, "minecraft:dark_oak_log[axis=y]")
        a.put(x, 11, 22, "minecraft:lantern[hanging=true]")
    a.fill(31, 5, 5, 42, 12, 15, "minecraft:polished_blackstone_bricks")
    a.fill(32, 5, 6, 41, 11, 14, "minecraft:air")
    a.fill(34, 5, 14, 38, 10, 16, "minecraft:air")
    # The annex threshold is the boss's second-stage destination. A lodestone
    # below its feet stays discoverable when the entire structure rotates.
    a.put(37, 4, 15, "minecraft:lodestone")
    a.fill(31, 13, 5, 42, 13, 15, "minecraft:oxidized_cut_copper")
    a.fill(37, 5, 7, 42, 26, 12, "minecraft:tuff_bricks")
    a.fill(38, 8, 8, 41, 24, 11, "minecraft:air")
    a.fill(39, 8, 12, 40, 10, 12, "minecraft:air")
    for y in (13, 19, 25):
        a.ring(36, 6, 43, 13, y, "minecraft:cut_copper")
    for x in range(38, 42):
        for z in range(8, 12):
            a.put(x, 17, z, "minecraft:yellow_stained_glass")
    a.put(39, 17, 7, "minecraft:gold_block")
    for x, z in ((37, 9), (42, 9), (39, 6), (39, 13)):
        a.put(x, 17, z, "minecraft:quartz_block")
    a.put(39, 18, 9, "minecraft:gold_block")
    for y in range(26, 30):
        a.fill(37 + (y - 26) // 2, y, 7 + (y - 26) // 2,
               42 - (y - 26) // 2, y, 12 - (y - 26) // 2,
               "minecraft:oxidized_copper")
    a.chest(12, 5, 13)
    a.chest(39, 8, 9)
    a.sign(22, 5, 31, ("The annex was", "not on the plan.", "It is older than", "the timetable."))
    a.sign(26, 5, 32, ("Gold follows you.", "Red locks a track.", "Step clear, then", "follow the annex."))
    a.quote_sign(25, 5, 34, ("anti pi-time", "propaganda", "", ""))
    a.last_arrival(22, 5, 22)
    attach_east(a, "lastlight_market", "minecraft:cut_sandstone")
    attach_west(a, "lastlight_signal_box", "minecraft:cut_sandstone")
    return a


def lastlight_exchange_v2() -> Landmark:
    a = lastlight()
    a.name = "lastlight_exchange_v2"
    # The public headhouse and off-axis annex share an ashlar datum and
    # clock marks, while the track grid and boss threshold stay clear.
    for x in (11, 17, 29, 35):
        a.pillar(x, 40, 5, 12, "gems:station_ashlar")
        a.put(x, 13, 40, "minecraft:cut_copper")
    a.fill(11, 14, 40, 35, 14, 40, "minecraft:oxidized_cut_copper")
    a.fill(17, 15, 40, 29, 15, 40, "gems:station_ashlar")
    a.put(23, 16, 40, "gems:station_dial")
    a.fill(36, 13, 6, 43, 13, 13, "gems:station_ashlar")
    for x, z in ((37, 9), (42, 9), (39, 6), (39, 13)):
        a.put(x, 17, z, "gems:station_dial")
    a.jigsaw(46, 5, 23, "east_up", "gems:lastlight_market_entry",
             "gems:lastlight_market_v2")
    a.jigsaw(0, 5, 23, "west_up", "gems:lastlight_signal_box_entry",
             "gems:lastlight_signal_box_v2")
    return a


def rehearsal() -> Landmark:
    a = Landmark("rehearsal_works")
    a.foundation("minecraft:spruce_planks", "minecraft:deepslate_tiles", 24)
    # Backstage is explorable on two levels; rigging and three raised stages
    # give players real movement choices instead of a flat combat circle.
    for x in (7, 15, 23, 31, 39):
        for z in (7, 15, 23, 31, 39):
            a.pillar(x, z, 5, 23, "minecraft:stripped_spruce_log[axis=y]")
            a.put(x, 24, z, "minecraft:copper_block")
    for y in (12, 20):
        for z in (7, 23, 39):
            a.fill(7, y, z, 39, y, z, "minecraft:dark_oak_planks")
        for x in (7, 23, 39):
            a.fill(x, y, 7, x, y, 39, "minecraft:dark_oak_planks")
    a.fill(10, 6, 10, 20, 6, 20, "minecraft:polished_andesite")
    a.fill(26, 8, 10, 37, 8, 21, "minecraft:oxidized_cut_copper")
    a.fill(15, 10, 26, 32, 10, 37, "minecraft:crimson_planks")
    for x0, y, z0, x1, z1 in [(10, 6, 10, 20, 20), (26, 8, 10, 37, 21), (15, 10, 26, 32, 37)]:
        a.ring(x0, z0, x1, z1, y, "minecraft:cut_copper")
    for y in range(5, 12):
        a.fill(7 + y - 5, y, 6, 9 + y - 5, y, 8, "minecraft:dark_oak_planks")
    for rise, x in enumerate(range(26, 30), start=5):
        a.fill(x, 5, 8 + rise - 5, x + 2, rise, 8 + rise - 5,
               "minecraft:dark_oak_planks")
    for rise, x in enumerate(range(15, 21), start=5):
        a.fill(x, 5, 25, x + 2, rise, 25, "minecraft:crimson_planks")
    a.fill(5, 5, 41, 41, 8, 42, "minecraft:air")
    for z in (8, 23, 38):
        a.fill(4, 5, z, 6, 13, z, "minecraft:purple_stained_glass")
        a.fill(40, 5, z, 42, 13, z, "minecraft:purple_stained_glass")
    for z in (7, 23, 39):
        for x in range(9, 40, 10):
            a.fill(x, 20, z, x + 5, 20, z + 3, "minecraft:purple_wool")
            a.fill(x, 21, z + 1, x + 5, 21, z + 2, "minecraft:magenta_wool")
    for x in (12, 24, 36):
        a.fill(x, 17, 6, x, 23, 6, "minecraft:chain")
        a.fill(x, 17, 40, x, 23, 40, "minecraft:chain")
        a.put(x, 16, 6, "minecraft:lantern[hanging=true]")
        a.put(x, 16, 40, "minecraft:lantern[hanging=true]")
    for z in (18, 28):
        a.fill(6, 5, z, 9, 11, z + 4, "minecraft:brick")
        a.fill(7, 6, z + 1, 8, 10, z + 3, "minecraft:air")
        a.fill(6, 12, z, 9, 12, z + 4, "minecraft:cut_copper")
    # Target blocks identify all three stages after template rotation. Four
    # purple footlights around each become gold after the first world victory.
    for x, y, z in ((14, 6, 14), (30, 8, 14), (25, 10, 30)):
        a.put(x, y, z, "minecraft:target")
        for dx, dz in ((-3, 0), (3, 0), (0, -3), (0, 3)):
            a.put(x + dx, y, z + dz, "minecraft:purple_concrete")
    a.chest(11, 7, 11)
    a.chest(36, 9, 19)
    a.sign(23, 5, 40, ("Again from the top.", "Keep the mistake.", "It was better.", ""))
    a.sign(20, 5, 39, ("Gold follows.", "Pink locks the mark.", "Move before it falls.", "Then strike."))
    a.unbroken_punchline(14, 7, 14)
    attach_east(a, "rehearsal_prop_foundry", "minecraft:spruce_planks")
    attach_west(a, "rehearsal_costume_house", "minecraft:spruce_planks")
    return a


def rehearsal_works_v2() -> Landmark:
    a = rehearsal()
    a.name = "rehearsal_works_v2"
    # The open proscenium announces the stage from the forest while keeping
    # the lower visitor aisle and all three combat marks untouched.
    for x in (15, 31):
        a.pillar(x, 42, 5, 16, "gems:stage_timber")
        a.put(x, 17, 42, "minecraft:cut_copper")
    a.fill(15, 18, 42, 31, 18, 42, "gems:stage_timber")
    a.fill(17, 19, 42, 29, 19, 42, "gems:stage_curtain")
    for x in (9, 37):
        a.fill(x, 9, 41, x, 15, 41, "gems:stage_curtain")
    a.jigsaw(46, 5, 23, "east_up", "gems:rehearsal_prop_foundry_entry",
             "gems:rehearsal_prop_foundry_v2")
    a.jigsaw(0, 5, 23, "west_up", "gems:rehearsal_costume_house_entry",
             "gems:rehearsal_costume_house_v2")
    return a


def branch_vault() -> Landmark:
    a = Landmark("branch_vault")
    a.foundation("minecraft:polished_deepslate", "minecraft:deepslate_bricks", 23)
    for x0, z0, x1, z1 in [(5, 5, 41, 11), (5, 35, 41, 41), (5, 11, 11, 35), (35, 11, 41, 35)]:
        a.fill(x0, 5, z0, x1, 19, z1, "minecraft:deepslate_tiles")
        a.fill(x0 + 1, 6, z0 + 1, x1 - 1, 18, z1 - 1, "minecraft:air")
        a.fill(x0, 20, z0, x1, 20, z1, "minecraft:chiseled_tuff")
    for x in (13, 23, 33):
        for z in (13, 23, 33):
            a.fill(x - 2, 5, z - 2, x + 2, 15, z + 2, "minecraft:tuff_bricks")
            a.fill(x - 1, 6, z - 1, x + 1, 14, z + 1, "minecraft:air")
            a.put(x, 15, z, "minecraft:sea_lantern")
    for x in (13, 23, 33):
        a.fill(x - 1, 5, 11, x + 1, 12, 35, "minecraft:air")
    for z in (13, 23, 33):
        a.fill(11, 5, z - 1, 35, 12, z + 1, "minecraft:air")
    for x in (11, 35):
        for z in (11, 35):
            a.pillar(x, z, 5, 25, "minecraft:polished_blackstone_bricks")
    # Deep bays expose the branching interior. A stepped outer cornice and
    # narrow windows break the long walls into legible archive stacks.
    for x in (14, 23, 32):
        for z in (5, 41):
            a.fill(x - 2, 7, z, x + 2, 14, z, "minecraft:air")
            a.fill(x - 1, 9, z, x + 1, 13, z, "minecraft:iron_bars")
            a.fill(x - 3, 6, z, x - 3, 18, z, "minecraft:polished_deepslate")
            a.fill(x + 3, 6, z, x + 3, 18, z, "minecraft:polished_deepslate")
    for z in (14, 23, 32):
        for x in (5, 41):
            a.fill(x, 7, z - 2, x, 14, z + 2, "minecraft:air")
            a.fill(x, 9, z - 1, x, 13, z + 1, "minecraft:iron_bars")
            for offset in (-3, 3):
                a.fill(x, 6, z + offset, x, 18, z + offset, "minecraft:polished_deepslate")
    for y in (21, 23):
        a.ring(7 - (y - 21), 7 - (y - 21), 39 + (y - 21),
               39 + (y - 21), y, "minecraft:cut_copper")
    for x in (15, 23, 31):
        a.fill(x - 1, 21, 5, x + 1, 21, 41, "minecraft:oxidized_cut_copper")
        a.put(x, 22, 23, "minecraft:sea_lantern")
    a.fill(17, 16, 17, 29, 16, 29, "minecraft:polished_deepslate")
    a.ring(17, 17, 29, 29, 17, "minecraft:cut_copper")
    for i in range(12):
        a.fill(12 + i, 5, 22, 12 + i, 5 + i, 24, "minecraft:deepslate_bricks")
        a.fill(12 + i, 5 + i, 22, 12 + i, 5 + i, 24,
               "minecraft:deepslate_tile_stairs[facing=west]")
    # Keep the ground-floor crossing walkable beneath the upper stair flight.
    a.fill(22, 5, 22, 24, 11, 24, "minecraft:air")
    a.fill(21, 5, 35, 25, 12, 43, "minecraft:air")
    # The south junction is a real four-way arena. Cyan floor lines show the
    # four routes; the four blue ends turn gold after the first world victory.
    for delta in range(-10, 11):
        a.put(23 + delta, 4, 33, "minecraft:cyan_concrete")
        a.put(23, 4, 33 + delta, "minecraft:cyan_concrete")
    a.put(23, 4, 33, "minecraft:lodestone")
    for dx, dz in ((-7, 0), (7, 0), (0, -7), (0, 7)):
        a.put(23 + dx, 4, 33 + dz, "minecraft:blue_concrete")
    a.chest(23, 17, 23)
    a.chest(13, 5, 33)
    a.sign(25, 5, 38, ("The route was", "perfectly planned.", "The exit moved.", ""))
    a.sign(21, 5, 38, ("Cyan predicts.", "Red commits.", "Change corridors.", "Then close in."))
    a.second_move_solver(23, 5, 33)
    attach_east(a, "branch_escape_tower", "minecraft:polished_deepslate")
    attach_west(a, "branch_map_room", "minecraft:polished_deepslate")
    return a


def branch_vault_v2() -> Landmark:
    a = branch_vault()
    a.name = "branch_vault_v2"
    # Layered index towers make the four-way library legible from the taiga.
    # The boss's ground-level route marks and cleared crossing stay untouched.
    for x in (12, 34):
        a.pillar(x, 42, 6, 18, "gems:folio_stone")
        a.put(x, 19, 42, "gems:branch_index")
    a.fill(12, 20, 42, 34, 20, 42, "gems:folio_stone")
    for x in (17, 23, 29):
        a.put(x, 21, 42, "gems:branch_index")
    a.jigsaw(46, 5, 23, "east_up", "gems:branch_escape_tower_entry",
             "gems:branch_escape_tower_v2")
    a.jigsaw(0, 5, 23, "west_up", "gems:branch_map_room_entry",
             "gems:branch_map_room_v2")
    return a


def redline() -> Landmark:
    a = Landmark("redline_house")
    a.foundation("minecraft:spruce_planks", "minecraft:stone_bricks", 14)
    # A safe-looking house spans a crossed pair of bridges; all four paths
    # visibly lead to a central room, but each has its own sheltered alcove.
    for x0, z0, x1, z1 in [(16, 5, 30, 17), (16, 29, 30, 41), (5, 16, 17, 30), (29, 16, 41, 30)]:
        for y in range(5, 12):
            a.ring(x0, z0, x1, z1, y, "minecraft:spruce_planks" if y != 11 else "minecraft:dark_oak_planks")
        a.fill(x0 + 1, 5, z0 + 1, x1 - 1, 10, z1 - 1, "minecraft:air")
        a.fill(x0, 12, z0, x1, 12, z1, "minecraft:dark_oak_planks")
        for x in range(x0 + 2, x1 - 1, 4):
            a.put(x, 8, z0, "minecraft:glass_pane")
            a.put(x, 8, z1, "minecraft:glass_pane")
    a.fill(17, 5, 17, 29, 16, 29, "minecraft:stone_bricks")
    a.fill(18, 5, 18, 28, 15, 28, "minecraft:air")
    a.fill(17, 17, 17, 29, 17, 29, "minecraft:dark_oak_planks")
    a.fill(19, 17, 19, 27, 17, 27, "minecraft:glass")
    for x in (17, 29):
        for z in (17, 29):
            a.pillar(x, z, 5, 22, "minecraft:stripped_spruce_log[axis=y]")
    for x0, z0, x1, z1 in [(21, 5, 25, 18), (21, 28, 25, 41), (5, 21, 18, 25), (28, 21, 41, 25)]:
        a.fill(x0, 5, z0, x1, 10, z1, "minecraft:air")
    for y in range(5, 10):
        a.fill(21, y, 42 + y - 5, 25, y, 46, "minecraft:stone_bricks")
    for x, z in [(11, 25), (25, 11), (35, 25), (25, 35)]:
        a.put(x, 6, z, "minecraft:lantern")
    # The four yellow shelters sit at the end of lines visible from the
    # central guard. Leave two clear blocks above each so a player can touch it.
    for delta in range(-11, 12):
        if delta == 0:
            a.put(23, 4, 23, "minecraft:lodestone")
        else:
            floor = "minecraft:yellow_concrete" if abs(delta) == 9 else "minecraft:red_concrete"
            a.put(23 + delta, 4, 23, floor)
            a.put(23, 4, 23 + delta, floor)
    for x0, z0, x1, z1 in [(16, 5, 30, 17), (16, 29, 30, 41),
                            (5, 16, 17, 30), (29, 16, 41, 30)]:
        # Offset gables articulate four distinct rescue wings.
        for slope in range(1, 5):
            a.fill(x0 + slope, 12 + slope, z0, x1 - slope, 12 + slope, z1,
                   "minecraft:dark_oak_planks")
        a.fill(x0 + 5, 17, z0 + 2, x1 - 5, 17, z1 - 2,
               "minecraft:yellow_stained_glass")
    for x in (8, 38):
        for z in (8, 38):
            a.pillar(x, z, 5, 16, "minecraft:stone_bricks")
            a.fill(x - 1, 17, z - 1, x + 1, 17, z + 1, "minecraft:cut_copper")
            a.put(x, 18, z, "minecraft:lantern")
    a.chest(23, 5, 8)
    a.chest(37, 5, 24)
    a.sign(23, 5, 37, ("A red line", "marks a shelter.", "Reach its gold", "tile to help."))
    a.red_line_between(23, 5, 23)
    attach_east(a, "redline_infirmary", "minecraft:spruce_planks")
    attach_west(a, "redline_watchpost", "minecraft:spruce_planks")
    return a


def redline_house_v2() -> Landmark:
    a = redline()
    a.name = "redline_house_v2"
    # The four wings share a brick ground floor, pale cornice, and shelter
    # crests. Keep the interior red warning lines and gold refuge pads open.
    for x0, z0, x1, z1 in ((16, 5, 30, 17), (16, 29, 30, 41),
                            (5, 16, 17, 30), (29, 16, 41, 30)):
        for y in range(5, 8):
            a.ring(x0, z0, x1, z1, y, "gems:refuge_brick")
        a.ring(x0, z0, x1, z1, 11, "minecraft:white_concrete")
        for x in range(x0 + 2, x1 - 1, 4):
            a.put(x, 8, z0, "gems:shelter_emblem")
            a.put(x, 8, z1, "gems:shelter_emblem")
    # Four large porticoes read from the meadow without touching the arena.
    for x, z in ((23, 42), (4, 23), (42, 23), (23, 4)):
        if z in (4, 42):
            for edge in (x - 7, x + 7):
                a.pillar(edge, z, 5, 13, "gems:refuge_brick")
            a.fill(x - 7, 14, z, x + 7, 14, z, "gems:refuge_brick")
            a.put(x, 15, z, "gems:shelter_emblem")
        else:
            for edge in (z - 7, z + 7):
                a.pillar(x, edge, 5, 13, "gems:refuge_brick")
            a.fill(x, 14, z - 7, x, 14, z + 7, "gems:refuge_brick")
            a.put(x, 15, z, "gems:shelter_emblem")
    for x0, z0, x1, z1 in ((21, 5, 25, 18), (21, 28, 25, 41),
                            (5, 21, 18, 25), (28, 21, 41, 25)):
        a.fill(x0, 5, z0, x1, 10, z1, "minecraft:air")
    a.chests.clear()
    a.signs.clear()
    a.chest(23, 5, 8, loot="redline_house")
    a.chest(37, 5, 24, loot="redline_house")
    a.sign(23, 5, 37, ("A red line", "marks a shelter.", "Reach its gold", "tile to help."))
    a.jigsaw(46, 5, 23, "east_up", "gems:redline_infirmary_entry",
             "gems:redline_infirmary_v2")
    a.jigsaw(0, 5, 23, "west_up", "gems:redline_watchpost_entry",
             "gems:redline_watchpost_v2")
    return a


def redline_house_arrival_v3() -> Landmark:
    """New-start stair access at both long ends of the rescue hall."""
    a = redline_house_v2()
    a.name = "redline_house_arrival_v3"
    for near_north in (True, False):
        for step in range(5):
            z = step if near_north else 46 - step
            # Replace only the five-wide entry through the old raised plinth.
            a.fill(21, step + 1, z, 25, 10, z, "minecraft:air")
            a.fill(21, 0, z, 25, step, z, "minecraft:stone_bricks")
            a.fill(21, step, z, 25, step, z, "minecraft:polished_diorite")
            a.put(23, step, z, "minecraft:red_concrete")
        # Lead the eye through each door to the original interior cross.
        for z in (range(5, 12) if near_north else range(35, 42)):
            a.put(23, 4, z, "minecraft:red_concrete")
    return a


def hushwire() -> Landmark:
    a = Landmark("hushwire")
    a.foundation("gems:hushwire_slate", "minecraft:blackstone", 14)
    # Feel's record hall is a tall, narrow gothic silhouette: real lancet
    # windows, outer buttresses, a stepped roof and a dark listening spire.
    for y in range(5, 14):
        a.ring(5, 8, 41, 38, y, "gems:hushwire_slate")
    a.fill(6, 5, 9, 40, 12, 37, "minecraft:air")
    for z in range(11, 37, 5):
        a.fill(8, 5, z, 17, 7, z + 1, "minecraft:dark_oak_planks")
        a.fill(29, 5, z, 38, 7, z + 1, "minecraft:dark_oak_planks")
        a.put(9, 8, z, "minecraft:redstone_lamp[lit=true]")
        a.put(37, 8, z, "minecraft:redstone_lamp[lit=true]")
    for z in (12, 20, 28, 35):
        for x in (3, 42):
            a.fill(x, 5, z, x + 1, 14, z + 1, "gems:hushwire_slate")
            a.fill(x, 14, z - 1, x + 2, 14, z + 2, "gems:hushwire_tracery")
            a.fill(x, 15, z - 1, x + 2, 15, z + 2, "minecraft:deepslate_tiles")
        for x in (5, 41):
            a.fill(x, 7, z + 3, x, 11, z + 5, "minecraft:blue_stained_glass")
            a.put(x, 12, z + 4, "minecraft:light_blue_stained_glass")
            for edge in (z + 2, z + 6):
                if edge < 38:
                    a.fill(x, 6, edge, x, 12, edge, "minecraft:polished_blackstone_bricks")
                    a.put(x, 13, edge, "gems:hushwire_tracery")
    # A pointed front window makes the hall read as one district landmark.
    for x in (11, 35):
        a.fill(x, 7, 38, x + 2, 11, 38, "minecraft:blue_stained_glass")
        a.put(x + 1, 12, 38, "minecraft:light_blue_stained_glass")
        a.fill(x - 1, 6, 38, x - 1, 12, 38, "minecraft:polished_blackstone_bricks")
        a.fill(x + 3, 6, 38, x + 3, 12, 38, "minecraft:polished_blackstone_bricks")
        a.put(x - 1, 13, 38, "gems:hushwire_tracery")
        a.put(x + 3, 13, 38, "gems:hushwire_tracery")
    for x in range(4, 43):
        roof_y = 15 + (19 - abs(x - 23)) // 3
        a.fill(x, roof_y, 7, x, roof_y, 39, "minecraft:deepslate_tiles")
    for z in (8, 38):
        for x in range(5, 42):
            top = 15 + (19 - abs(x - 23)) // 3
            a.fill(x, 14, z, x, top - 1, z, "gems:hushwire_slate")
        for x, y in ((23, 17), (22, 18), (23, 18), (24, 18), (23, 19)):
            a.put(x, y, z, "minecraft:light_blue_stained_glass")
        for x, y in ((21, 18), (25, 18), (23, 16), (23, 20)):
            a.put(x, y, z, "gems:hushwire_tracery")
    a.fill(16, 14, 14, 30, 14, 32, "gems:hushwire_slate")
    for y in range(15, 28):
        a.ring(17, 15, 29, 31, y, "gems:hushwire_slate")
    a.fill(18, 15, 16, 28, 27, 30, "minecraft:air")
    for x in (17, 29):
        for z in (15, 31):
            a.pillar(x, z, 15, 29, "gems:hushwire_tracery")
    for z in (18, 24, 28):
        a.fill(21, 20, 15, 25, 24, 15, "minecraft:blue_stained_glass")
        a.put(23, 25, 15, "minecraft:light_blue_stained_glass")
        a.fill(21, 20, 31, 25, 24, 31, "minecraft:blue_stained_glass")
        a.put(23, 25, 31, "minecraft:light_blue_stained_glass")
        for x in (17, 29):
            a.fill(x, 20, z, x, 24, z + 1, "minecraft:blue_stained_glass")
            a.put(x, 25, z, "minecraft:light_blue_stained_glass")
    a.ring(16, 14, 30, 32, 28, "minecraft:deepslate_tiles")
    a.ring(19, 17, 27, 29, 29, "minecraft:deepslate_tiles")
    a.fill(22, 30, 21, 24, 30, 25, "gems:hushwire_tracery")
    a.fill(22, 5, 38, 26, 12, 42, "minecraft:air")
    for x, y in ((20, 10), (21, 11), (22, 12), (23, 13), (24, 13),
                 (25, 13), (26, 12), (27, 11), (28, 10)):
        a.put(x, y, 38, "minecraft:polished_blackstone_bricks")
    a.put(24, 13, 38, "gems:hushwire_tracery")
    for y in range(5, 13):
        a.fill(10 + y - 5, y, 9, 11 + y - 5, y, 11, "gems:hushwire_slate")
    for y in range(15, 19):
        a.fill(19, 15, y - 3, 20, y, y - 3, "gems:hushwire_slate")
    for y in range(24, 30):
        a.fill(23, y, 11, 23, y, 35, "minecraft:lightning_rod[facing=up]")
    for x in (19, 27):
        for z in (18, 28):
            a.fill(x, 20, z, x, 27, z, "minecraft:chain")
            a.put(x, 19, z, "minecraft:redstone_lamp[lit=true]")
    # Three identical receivers make the first flicker untrustworthy. Only
    # the boss's later steady cyan tell reveals which receiver will pulse.
    a.put(23, 4, 23, "minecraft:lodestone")
    for x, z in ((19, 17), (27, 17), (23, 32)):
        a.put(x, 4, z, "minecraft:blue_concrete")
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            a.put(x + dx, 4, z + dz, "minecraft:cut_copper")
    a.chest(10, 5, 33)
    a.chest(27, 19, 28)
    a.sign(23, 5, 35, ("Three calls.", "Only one holds.", "Wait for blue", "before moving."))
    a.empty_encore(23, 5, 23)
    attach_east(a, "hushwire_dishfield", "minecraft:polished_blackstone")
    attach_west(a, "hushwire_relay_bunker", "minecraft:polished_blackstone")
    return a


def unwritten() -> Landmark:
    a = Landmark("unwritten_court")
    a.foundation("minecraft:polished_andesite", "minecraft:deepslate_bricks", 14)
    # Concentric seating frames a mutable stage. The steward's small room
    # behind it is easy to miss but gives the monument a human second scale.
    cx = cz = 23
    for x in range(5, 42):
        for z in range(5, 42):
            radius = max(abs(x - cx), abs(z - cz))
            if 10 <= radius <= 18:
                y = 5 + (radius - 10) // 2
                a.put(x, y, z, "minecraft:quartz_block" if radius % 2 else "minecraft:polished_deepslate")
                if y > 5:
                    a.fill(x, 5, z, x, y - 1, z, "minecraft:deepslate_bricks")
            elif radius <= 9:
                a.put(x, 5, z, "minecraft:dark_oak_planks")
    a.ring(13, 13, 33, 33, 5, "minecraft:cut_copper")
    for x in (7, 39):
        for z in (7, 39):
            a.pillar(x, z, 5, 24, "minecraft:polished_blackstone_bricks")
            a.fill(x - 2, 25, z - 2, x + 2, 25, z + 2, "minecraft:oxidized_copper")
            a.put(x, 26, z, "minecraft:sea_lantern")
    for z in range(6, 41, 4):
        a.pillar(4, z, 5, 14, "minecraft:tuff_bricks")
        a.pillar(42, z, 5, 14, "minecraft:tuff_bricks")
    for x in range(6, 41, 4):
        a.pillar(x, 4, 5, 14, "minecraft:tuff_bricks")
        a.pillar(x, 42, 5, 14, "minecraft:tuff_bricks")
    a.fill(19, 5, 38, 27, 15, 42, "minecraft:air")
    # The gate starts on the plinth at y=4; the audience tier at z=37 is
    # already y=7. Two broad steps bridge that height without a jump.
    for z, top in ((39, 5), (38, 6)):
        a.fill(21, 5, z, 25, top, z, "minecraft:deepslate_bricks")
        a.fill(21, top, z, 25, top, z, "minecraft:quartz_block")
        a.put(23, top, z, "minecraft:cut_copper")
    a.fill(17, 5, 5, 29, 12, 12, "minecraft:stone_bricks")
    a.fill(18, 6, 6, 28, 11, 11, "minecraft:air")
    a.fill(22, 5, 12, 24, 10, 13, "minecraft:air")
    for x in range(18, 29, 3):
        a.fill(x, 13, 6, x + 1, 17, 6, "minecraft:cut_copper")
        a.fill(x, 17, 6, x + 1, 17, 12, "minecraft:oxidized_cut_copper")
    # The steward's room becomes a theatrical back wall: an open doorway below
    # a blank script panel, framed by two mismatched mask niches. It faces the
    # arena but stays behind the floor cues and every finale station.
    for x, face in ((17, "minecraft:quartz_block"),
                    (29, "minecraft:polished_blackstone_bricks")):
        a.fill(x, 12, 12, x, 20, 13, face)
        a.fill(x - 1, 20, 11, x + 1, 20, 14, "minecraft:oxidized_cut_copper")
    a.fill(19, 18, 13, 27, 18, 13, "minecraft:cut_copper")
    a.fill(21, 19, 13, 25, 19, 13, "minecraft:oxidized_cut_copper")
    a.fill(22, 20, 13, 24, 20, 13, "minecraft:cut_copper")
    a.put(23, 21, 13, "minecraft:sea_lantern")
    a.fill(22, 12, 13, 24, 17, 13, "minecraft:light_gray_stained_glass")
    for x in (22, 24):
        a.fill(x, 13, 14, x, 16, 14, "minecraft:quartz_block")
    a.fill(23, 14, 14, 23, 16, 14, "minecraft:purple_stained_glass")
    for center, face, eye in ((19, "minecraft:quartz_block", "minecraft:amethyst_block"),
                              (27, "minecraft:polished_blackstone", "minecraft:sea_lantern")):
        a.fill(center - 1, 13, 13, center + 1, 16, 13, face)
        a.put(center, 15, 14, eye)
        a.put(center, 13, 14, "minecraft:cut_copper")
    # Side audience galleries deliberately use different finishes. Their
    # upper frames make the theater visible from outside while a broad gap at
    # z=21..25 keeps both satellite connections and cross-aisles open.
    for x, column, trim in ((6, "minecraft:quartz_block", "minecraft:cut_copper"),
                            (40, "minecraft:polished_blackstone_bricks",
                             "minecraft:oxidized_cut_copper")):
        for start, end in ((10, 18), (28, 36)):
            for z in (start, end):
                a.pillar(x, z, 9, 17, column)
                a.put(x, 18, z, "minecraft:sea_lantern")
            a.fill(x, 19, start, x, 19, end, trim)
            for z in range(start + 2, end - 1, 3):
                a.fill(x, 14, z, x, 17, z, "minecraft:purple_stained_glass"
                       if x == 6 else "minecraft:light_gray_stained_glass")
        # A shallow balcony lip breaks up the long bare seating elevation.
        for start, end in ((11, 17), (29, 35)):
            a.fill(x - 1, 9, start, x + 1, 9, end, trim)
    # The entrance frame announces the court as a stage without closing the
    # central south route, moving the dais, or covering the arena.
    a.pillar(17, 40, 5, 17, "minecraft:quartz_block")
    a.pillar(29, 40, 5, 17, "minecraft:polished_blackstone_bricks")
    a.fill(17, 18, 40, 29, 18, 40, "minecraft:oxidized_cut_copper")
    a.fill(20, 19, 40, 26, 19, 40, "minecraft:cut_copper")
    a.put(23, 20, 40, "minecraft:sea_lantern")
    for z in (15, 31):
        for x in (15, 31):
            a.pillar(x, z, 6, 12, "minecraft:cut_copper")
            a.put(x, 13, z, "minecraft:sea_lantern")
    for x in range(17, 30, 3):
        a.fill(x, 5, 23, x, 5, 29, "minecraft:red_terracotta")
    # Four unlit stage cues are restored only after the first world victory.
    for x, z in ((18, 18), (28, 18), (18, 28), (28, 28)):
        a.put(x, 5, z, "minecraft:purple_concrete")
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            a.put(x + dx, 5, z + dz, "minecraft:cut_copper")
    a.put(23, 4, 23, "minecraft:lodestone")
    a.put(23, 5, 23, "minecraft:air")
    a.put(23, 6, 23, "minecraft:air")
    a.put(18, 5, 23, "gems:court_west_mark")
    a.put(28, 5, 23, "gems:court_east_mark")
    a.put(23, 5, 32, "gems:court_dais")
    a.fill(19, 5, 20, 27, 5, 21, "minecraft:polished_blackstone")
    a.chest(20, 6, 8)
    a.chest(27, 6, 8)
    a.sign(26, 7, 36, ("There was no", "script for this.", "Someone still", "set the chairs."))
    a.stray_cadenza(23, 5, 23)
    attach_east(a, "unwritten_stewards_annex", "minecraft:polished_andesite")
    attach_west(a, "unwritten_mask_archive", "minecraft:polished_andesite")
    # Carving the jigsaw corridors exposes the y=4 plinth beside y=7 seats.
    # Stair-step both sides so players can return from either side building.
    for x, top in ((5, 4), (6, 5), (7, 6),
                   (39, 6), (40, 5), (41, 4)):
        for z in range(22, 25):
            if top > 4:
                a.fill(x, 5, z, x, top, z, "minecraft:deepslate_bricks")
                a.put(x, top, z, "minecraft:quartz_block")
                a.put(x, top + 1, z, "minecraft:air")
                a.put(x, top + 2, z, "minecraft:air")
    return a


def unwritten_court_v2() -> Landmark:
    a = unwritten()
    a.name = "unwritten_court_v2"
    # A broad plaster fly-tower and paired mask wings give the open arena
    # a theatrical skyline while all finale stations stay on the floor.
    a.ring(4, 4, 42, 42, 16, "gems:court_plaster")
    for x in (7, 39):
        for z in (7, 39):
            a.pillar(x, z, 12, 23, "gems:court_plaster")
            a.put(x, 24, z, "gems:mask_relief")
    for x in (11, 35):
        for z in (11, 35):
            a.pillar(x, z, 10, 17, "gems:court_plaster")
            a.put(x, 18, z, "gems:mask_relief")
    a.fill(16, 21, 12, 30, 21, 12, "gems:court_plaster")
    for x in (18, 23, 28):
        a.put(x, 22, 12, "gems:mask_relief")
    a.fill(17, 18, 40, 29, 18, 40, "gems:court_plaster")
    a.put(23, 19, 40, "gems:mask_relief")
    a.jigsaw(46, 5, 23, "east_up", "gems:unwritten_stewards_annex_entry",
             "gems:unwritten_stewards_annex_v2")
    a.jigsaw(0, 5, 23, "west_up", "gems:unwritten_mask_archive_entry",
             "gems:unwritten_mask_archive_v2")
    return a


def inkbound_workshop() -> Landmark:
    """Hamed's comic workshop: veranda, paper rooms and a garden court."""
    a = Landmark("inkbound_workshop")
    a.foundation("minecraft:grass_block", "minecraft:stone_bricks", 25)
    a.fill(19, 4, 33, 27, 4, 46, "minecraft:polished_andesite")
    a.fill(10, 4, 8, 36, 4, 36, "minecraft:dark_oak_planks")
    for y in range(5, 14):
        a.ring(12, 10, 34, 32, y,
               "gems:inkbound_inkstone" if y in (5, 13) else "gems:inkbound_screen")
    a.fill(13, 5, 11, 33, 12, 31, "minecraft:air")
    # Deep eaves and the veranda distinguish the workshop from the tall
    # Hushwire silhouette. Blue ink roof edges repeat across both annexes.
    a.ring(10, 8, 36, 34, 5, "minecraft:dark_oak_planks")
    for x in (10, 18, 28, 36):
        for z in (8, 34):
            a.pillar(x, z, 6, 12, "minecraft:dark_oak_log[axis=y]")
            a.put(x, 13, z, "gems:inkbound_inkstone")
    for slope in range(6):
        a.fill(9, 14 + slope, 7 + slope, 37, 14 + slope, 35 - slope,
               "minecraft:deepslate_tiles")
        a.fill(9, 14 + slope, 7 + slope, 37, 14 + slope, 7 + slope,
               "gems:inkbound_inkstone")
        a.fill(9, 14 + slope, 35 - slope, 37, 14 + slope, 35 - slope,
               "gems:inkbound_inkstone")
    a.fill(9, 20, 20, 37, 20, 22, "minecraft:deepslate_tile_slab[type=bottom]")
    a.fill(21, 5, 32, 25, 10, 36, "minecraft:air")
    a.fill(21, 13, 32, 25, 13, 33, "minecraft:dark_oak_planks")
    for z in (13, 19, 25):
        for x in (12, 34):
            a.fill(x, 7, z, x, 10, z + 2, "minecraft:light_blue_stained_glass")
            a.put(x, 11, z + 1, "gems:inkbound_inkstone")
    for x in (16, 30):
        a.fill(x, 5, 15, x, 11, 15, "minecraft:dark_oak_log[axis=y]")
        a.fill(x, 5, 27, x, 11, 27, "minecraft:dark_oak_log[axis=y]")
    a.fill(18, 5, 18, 28, 5, 26, "minecraft:polished_andesite")
    a.fill(20, 6, 19, 26, 6, 24, "minecraft:dark_oak_planks")
    a.fill(22, 7, 21, 24, 7, 23, "gems:inkbound_inkstone")
    a.put(23, 8, 22, "minecraft:lantern[hanging=false]")
    # The three labelled drafting desks invite a small, peaceful experiment.
    for x, color in ((17, "minecraft:cyan_concrete"),
                     (23, "minecraft:magenta_concrete"),
                     (29, "minecraft:orange_concrete")):
        a.fill(x - 1, 5, 12, x + 1, 5, 13, "minecraft:dark_oak_planks")
        a.put(x, 6, 12, color)
    a.sign(17, 6, 14, ("Draw a route.", "Try a power.", "Keep the one", "that makes sense."))
    a.quote_sign(27, 5, 29, ("Proof that", "everyone can", "be redeemed", ""))
    a.friend(23, 5, 29, "hamed")
    a.chest(15, 5, 26)
    a.chest(31, 5, 26)
    # Garden paths are inset into the site and reach both side buildings.
    for x, z in ((7, 7), (39, 7), (7, 38), (39, 38)):
        a.fill(x - 2, 4, z - 2, x + 2, 4, z + 2, "minecraft:moss_block")
        a.pillar(x, z, 5, 10, "minecraft:cherry_log[axis=y]")
        a.fill(x - 2, 11, z - 2, x + 2, 12, z + 2,
               "minecraft:cherry_leaves[persistent=true]")
        a.put(x, 13, z, "minecraft:cherry_leaves[persistent=true]")
    a.fill(5, 5, 14, 8, 5, 20, "minecraft:stone_bricks")
    a.fill(6, 5, 15, 7, 5, 19, "minecraft:water[level=0]")
    a.fill(38, 5, 29, 41, 5, 35, "minecraft:stone_bricks")
    a.fill(39, 5, 30, 40, 5, 34, "minecraft:water[level=0]")
    attach_east(a, "inkbound_print_house", "minecraft:polished_andesite")
    attach_west(a, "inkbound_garden", "minecraft:polished_andesite")
    return a


def inkbound_print_house() -> Landmark:
    a = east_satellite("inkbound_print_house", "minecraft:stone_bricks",
                       "minecraft:dark_oak_planks")
    for y in range(5, 13):
        a.ring(3, 3, 18, 17, y,
               "gems:inkbound_inkstone" if y in (5, 12) else "gems:inkbound_screen")
    a.fill(4, 5, 4, 17, 11, 16, "minecraft:air")
    for x in (3, 10, 18):
        for z in (3, 17):
            a.pillar(x, z, 5, 13, "minecraft:dark_oak_log[axis=y]")
    for slope in range(4):
        a.fill(2, 13 + slope, 2 + slope, 19, 13 + slope, 18 - slope,
               "minecraft:deepslate_tiles")
        a.fill(2, 13 + slope, 2 + slope, 19, 13 + slope, 2 + slope,
               "gems:inkbound_inkstone")
        a.fill(2, 13 + slope, 18 - slope, 19, 13 + slope, 18 - slope,
               "gems:inkbound_inkstone")
    # The long east face used to be an uninterrupted paper grid. A framed
    # gallery of glazed panels, bracketed eaves and an ink-blue ridge now make
    # this annex legible as a workshop from the outer district path.
    for z in (5, 12):
        a.fill(18, 7, z, 18, 10, z + 2, "minecraft:light_blue_stained_glass")
        a.fill(18, 6, z - 1, 18, 6, z + 3, "gems:inkbound_inkstone")
        a.fill(18, 11, z - 1, 18, 11, z + 3, "gems:inkbound_inkstone")
        for edge in (z - 1, z + 3):
            a.pillar(18, edge, 7, 10, "minecraft:dark_oak_log[axis=y]")
        a.put(18, 8, z + 1, "minecraft:dark_oak_fence")
    for z in (3, 17):
        a.put(19, 12, z, "minecraft:dark_oak_fence")
        a.put(19, 11, z, "minecraft:lantern[hanging=true]")
    a.ring(1, 1, 20, 19, 12, "gems:inkbound_inkstone")
    for x in (1, 20):
        for z in (1, 19):
            a.put(x, 13, z, "minecraft:deepslate_tile_stairs[facing=east,half=top]")
    a.fill(5, 17, 9, 16, 17, 11, "minecraft:deepslate_tiles")
    a.fill(6, 18, 10, 15, 18, 10, "gems:inkbound_inkstone")
    for x in (6, 10, 15):
        a.put(x, 19, 10, "minecraft:chiseled_deepslate")
    for x0 in (5, 13):
        a.fill(x0, 7, 17, x0 + 2, 10, 17,
               "minecraft:light_blue_stained_glass")
        a.fill(x0, 11, 17, x0 + 2, 11, 17,
               "minecraft:dark_oak_planks")
    for z in (6, 14):
        a.fill(4, 11, z, 17, 11, z, "minecraft:dark_oak_planks")
        for x in (6, 11, 16):
            a.put(x, 10, z, "minecraft:lantern[hanging=true]")
    a.fill(8, 11, 18, 12, 11, 20, "minecraft:deepslate_tile_slab[type=bottom]")
    a.fill(8, 5, 18, 12, 5, 20, "minecraft:polished_andesite")
    a.sign(10, 6, 19, ("INKBOUND", "PRINT HOUSE", "Ideas are made", "here."))
    for x, note in ((7, "spark"), (11, "shelter"), (15, "route")):
        a.fill(x - 1, 5, 7, x + 1, 6, 9, "minecraft:dark_oak_planks")
        a.put(x, 7, 8, f"gems:inkbound_{note}")
    a.fill(3, 5, 8, 5, 9, 12, "minecraft:air")
    a.put(11, 5, 13, "minecraft:dark_oak_planks")
    a.put(11, 6, 13, "gems:inkbound_press")
    a.chest(15, 5, 14, "west", "inkbound_workshop")
    a.sign(10, 5, 15, ("PRINT HOUSE", "Tap two notes.", "Use the press.", "Try all 3 pairs."))
    return finish_east_satellite(a)


def inkbound_garden() -> Landmark:
    a = west_satellite("inkbound_garden", "minecraft:stone_bricks",
                       "minecraft:moss_block")
    a.fill(3, 4, 8, 18, 4, 12, "minecraft:polished_andesite")
    a.fill(8, 4, 3, 12, 4, 18, "minecraft:polished_andesite")
    for x, z in ((4, 4), (16, 4), (4, 16), (16, 16)):
        a.fill(x - 2, 4, z - 2, x + 2, 4, z + 2, "minecraft:moss_block")
        a.pillar(x, z, 5, 9, "minecraft:cherry_log[axis=y]")
        a.fill(x - 2, 10, z - 2, x + 2, 11, z + 2,
               "minecraft:cherry_leaves[persistent=true]")
    # The open reading pavilion keeps the binding table and a clear route in
    # from the workshop. A broad lower eave and a smaller lantern cap carry
    # the same ink-blue silhouette as the print house without copying its box.
    a.fill(6, 4, 6, 14, 4, 14, "minecraft:dark_oak_planks")
    a.fill(10, 4, 10, 20, 4, 10, "minecraft:polished_andesite")
    for x in (7, 13):
        for z in (7, 13):
            a.pillar(x, z, 5, 11, "minecraft:dark_oak_log[axis=y]")
            a.put(x, 12, z, "gems:inkbound_inkstone")
    for z in (7, 13):
        a.fill(7, 11, z, 13, 11, z, "minecraft:dark_oak_log[axis=x]")
    for x in (7, 13):
        a.fill(x, 11, 7, x, 11, 13, "minecraft:dark_oak_log[axis=z]")
    for z in (7, 13):
        for x in (8, 12):
            a.fill(x, 6, z, x, 9, z, "gems:inkbound_screen")
    for edge in range(5, 16):
        a.put(edge, 11, 5, "minecraft:deepslate_tile_stairs[facing=north,half=top]")
        a.put(edge, 11, 15, "minecraft:deepslate_tile_stairs[facing=south,half=top]")
        a.put(5, 11, edge, "minecraft:deepslate_tile_stairs[facing=west,half=top]")
        a.put(15, 11, edge, "minecraft:deepslate_tile_stairs[facing=east,half=top]")
    a.fill(5, 12, 5, 15, 12, 15, "minecraft:deepslate_tiles")
    a.ring(5, 5, 15, 15, 12, "gems:inkbound_inkstone")
    a.fill(6, 13, 6, 14, 13, 14, "minecraft:deepslate_tiles")
    a.ring(6, 6, 14, 14, 13, "gems:inkbound_inkstone")
    a.fill(7, 14, 7, 13, 14, 13, "minecraft:deepslate_tiles")
    a.ring(7, 7, 13, 13, 14, "gems:inkbound_inkstone")
    # A separate raised hood breaks the roof into two readable tiers. Small
    # corner finials make the eaves visible from the arriving garden path.
    for x in (5, 15):
        for z in (5, 15):
            a.put(x, 13, z, "minecraft:deepslate_tile_stairs[facing=east,half=top]")
    for x in (8, 12):
        for z in (8, 12):
            a.pillar(x, z, 15, 16, "minecraft:dark_oak_log[axis=y]")
    for edge in range(7, 14):
        a.put(edge, 16, 7, "minecraft:deepslate_tile_stairs[facing=north,half=top]")
        a.put(edge, 16, 13, "minecraft:deepslate_tile_stairs[facing=south,half=top]")
        a.put(7, 16, edge, "minecraft:deepslate_tile_stairs[facing=west,half=top]")
        a.put(13, 16, edge, "minecraft:deepslate_tile_stairs[facing=east,half=top]")
    a.fill(7, 17, 7, 13, 17, 13, "minecraft:deepslate_tiles")
    a.ring(7, 7, 13, 13, 17, "gems:inkbound_inkstone")
    a.fill(8, 18, 8, 12, 18, 12, "minecraft:deepslate_tiles")
    a.fill(9, 19, 9, 11, 19, 11, "gems:inkbound_inkstone")
    a.put(10, 20, 10, "minecraft:lightning_rod")
    for z in (6, 14):
        a.put(10, 11, z, "minecraft:lantern[hanging=true]")
    # The open court is a group of small places rather than a roof dropped
    # into grass: a side reading alcove, a north paper canopy and the arrival
    # gate each have a different height, but share the same timber/ink trim.
    a.fill(2, 4, 8, 6, 4, 12, "minecraft:dark_oak_planks")
    for z in (8, 12):
        a.pillar(2, z, 5, 8, "minecraft:dark_oak_log[axis=y]")
        a.pillar(5, z, 5, 8, "minecraft:dark_oak_log[axis=y]")
        a.fill(3, 6, z, 4, 8, z, "gems:inkbound_screen")
    a.fill(1, 9, 7, 6, 9, 13, "minecraft:deepslate_tiles")
    a.ring(1, 7, 6, 13, 9, "gems:inkbound_inkstone")
    a.fill(2, 10, 8, 5, 10, 12, "minecraft:deepslate_tiles")
    a.put(3, 5, 10, "minecraft:lectern[facing=east,has_book=false]")
    a.put(4, 5, 11, "minecraft:dark_oak_stairs[facing=north]")
    a.fill(7, 4, 2, 13, 4, 6, "minecraft:dark_oak_planks")
    a.fill(10, 4, 2, 10, 4, 6, "minecraft:polished_andesite")
    for x in (8, 12):
        a.pillar(x, 2, 5, 8, "minecraft:dark_oak_log[axis=y]")
        a.put(x, 7, 4, "gems:inkbound_screen")
    a.fill(7, 9, 1, 13, 9, 6, "minecraft:deepslate_tiles")
    a.ring(7, 1, 13, 6, 9, "gems:inkbound_inkstone")
    a.fill(8, 10, 2, 12, 10, 5, "minecraft:deepslate_tiles")
    for x in (17, 19):
        for z in (7, 13):
            a.pillar(x, z, 5, 10, "minecraft:dark_oak_log[axis=y]")
            a.put(x, 11, z, "gems:inkbound_inkstone")
    a.fill(16, 11, 6, 20, 11, 14, "minecraft:deepslate_tiles")
    a.ring(16, 6, 20, 14, 11, "gems:inkbound_inkstone")
    a.fill(17, 12, 7, 19, 12, 13, "minecraft:deepslate_tiles")
    for z in (7, 13):
        a.put(18, 10, z, "minecraft:lantern[hanging=true]")
    for x0, z0, x1, z1 in ((2, 14, 5, 15), (15, 14, 18, 15)):
        a.fill(x0, 4, z0, x1, 4, z1, "minecraft:stone_bricks")
        a.fill(x0 + 1, 4, z0, x1 - 1, 4, z1, "minecraft:water[level=0]")
    for x in (8, 12):
        a.put(x, 5, 8, "minecraft:dark_oak_stairs[facing=south]")
    a.put(10, 5, 10, "gems:inkbound_inkstone")
    a.put(10, 6, 10, "gems:inkbound_binding")
    a.chest(10, 5, 15, "north", "inkbound_workshop")
    a.sign(7, 5, 12, ("PAPER GARDEN", "The story works", "when the path", "changes."))
    return finish_west_satellite(a)


def inkbound_workshop_quarter_v2() -> Landmark:
    """Versioned Inkbound hub; existing workshop saves keep their old bytes."""
    a = inkbound_workshop()
    a.name = "inkbound_workshop_quarter_v2"
    # A high paper lantern rises above the older low workshop roof. Its
    # staggered cap and glazed sides are visible across the wider quarter.
    a.fill(17, 20, 15, 29, 20, 27, "minecraft:dark_oak_planks")
    for x in (17, 29):
        for z in (15, 27):
            a.pillar(x, z, 21, 26, "minecraft:dark_oak_log[axis=y]")
    for x in range(19, 28, 4):
        for z in (15, 27):
            a.fill(x, 22, z, x + 2, 25, z, "gems:inkbound_screen")
    for z in range(18, 27, 4):
        for x in (17, 29):
            a.fill(x, 22, z, x, 25, z + 2, "gems:inkbound_screen")
    for x in (19, 23, 27):
        a.pillar(x, 15, 22, 26, "minecraft:dark_oak_log[axis=y]")
        a.pillar(x, 27, 22, 26, "minecraft:dark_oak_log[axis=y]")
    a.fill(16, 27, 14, 30, 27, 28, "minecraft:deepslate_tiles")
    a.ring(16, 14, 30, 28, 27, "gems:inkbound_inkstone")
    a.fill(18, 28, 16, 28, 28, 26, "minecraft:deepslate_tiles")
    a.fill(20, 29, 18, 26, 29, 24, "minecraft:deepslate_tiles")
    a.fill(22, 30, 20, 24, 30, 22, "gems:inkbound_inkstone")
    for x in (17, 29):
        for z in (18, 24):
            a.put(x, 26, z, "minecraft:dark_oak_fence")
            a.put(x, 25, z, "minecraft:lantern[hanging=true]")
    for x in (18, 23, 28):
        a.put(x, 21, 15, "minecraft:lantern[hanging=false]")
    # The old front was a plain door. A double-height timber gate now frames
    # the view from the south approach without touching the friend or quote.
    for x in (18, 28):
        a.pillar(x, 39, 5, 15, "minecraft:dark_oak_log[axis=y]")
        a.put(x, 16, 39, "gems:inkbound_inkstone")
    a.fill(18, 15, 39, 28, 15, 39, "minecraft:dark_oak_log[axis=x]")
    a.fill(17, 17, 38, 29, 17, 40, "minecraft:deepslate_tiles")
    a.ring(17, 38, 29, 40, 17, "gems:inkbound_inkstone")
    for x in (19, 23, 27):
        a.put(x, 16, 38, "gems:inkbound_screen")
        a.put(x, 18, 39, "minecraft:dark_oak_fence")
    for x in (18, 28):
        for z in (36, 42):
            a.put(x, 11, z, "minecraft:dark_oak_stairs[facing=south]")
    for x in (20, 26):
        a.put(x, 14, 39, "minecraft:lantern[hanging=true]")
    # Three child pieces spread the district over multiple chunks. The south
    # lane descends toward natural ground; old pools and old NBT stay intact.
    a.jigsaw(46, 5, 23, "east_up", "gems:inkbound_print_house_v2_entry",
             "gems:inkbound_print_house_v2")
    a.jigsaw(0, 5, 23, "west_up", "gems:inkbound_garden_v2_entry",
             "gems:inkbound_garden_v2")
    a.fill(20, 5, 39, 26, 9, 46, "minecraft:air")
    a.fill(20, 4, 39, 26, 4, 46, "minecraft:polished_andesite")
    a.jigsaw(23, 5, 46, "south_up", "gems:inkbound_arrival_lane_v2_entry",
             "gems:inkbound_arrival_lane_v2")
    return a


def inkbound_print_house_v2() -> Landmark:
    """A walkable press hall, tower and drying shed on the east side."""
    a = Landmark("inkbound_print_house_v2")
    paper = "gems:inkbound_screen"
    ink = "gems:inkbound_inkstone"
    wood = "minecraft:dark_oak_planks"
    log = "minecraft:dark_oak_log[axis=y]"
    roof = "minecraft:deepslate_tiles"
    # Only building footprints receive deep foundations; the surrounding
    # grove and path remain terrain, instead of a 47-square stone pad.
    for x0, z0, x1, z1 in ((6, 7, 31, 39), (31, 10, 43, 24),
                           (32, 27, 44, 41)):
        a.fill(x0, 0, z0, x1, 3, z1, "minecraft:stone_bricks")
        a.fill(x0, 4, z0, x1, 4, z1, wood)
        a.fill(x0, 5, z0, x1, 25, z1, "minecraft:air")
    a.fill(0, 3, 21, 7, 3, 25, "minecraft:stone_bricks")
    a.fill(0, 4, 21, 9, 4, 25, "minecraft:polished_andesite")
    a.fill(0, 5, 21, 9, 10, 25, "minecraft:air")
    for y in range(5, 15):
        a.ring(8, 9, 30, 37, y, ink if y in (5, 14) else paper)
    a.fill(9, 5, 10, 29, 13, 36, "minecraft:air")
    for x in (8, 15, 23, 30):
        for z in (9, 37):
            a.pillar(x, z, 5, 16, log)
    for z in (9, 17, 29, 37):
        for x in (8, 30):
            a.fill(x, 7, z, x, 11, z + 2, "minecraft:light_blue_stained_glass")
            a.put(x, 12, z + 1, ink)
    for x in (11, 20, 27):
        a.pillar(x, 9, 7, 14, log)
        a.put(x, 15, 9, "minecraft:dark_oak_stairs[facing=south]")
        a.put(x, 15, 10, "minecraft:lantern[hanging=true]")
    for z in (13, 24, 33):
        a.fill(7, 8, z, 7, 10, z + 2, "minecraft:light_blue_stained_glass")
        a.fill(31, 8, z, 31, 10, z + 2, "minecraft:light_blue_stained_glass")
    # Side aisle is open to the tower and drying yard. Press floor remains
    # free of columns around the three notes and the working press.
    a.fill(7, 5, 21, 10, 10, 25, "minecraft:air")
    a.fill(8, 4, 21, 10, 4, 25, wood)
    a.fill(29, 5, 17, 34, 10, 20, "minecraft:air")
    a.fill(29, 4, 17, 34, 4, 20, wood)
    # A long, raised ridge changes the hall silhouette and keeps its front
    # gable readable; exposed dark rafters break up the broad paper facade.
    for slope in range(9):
        y = 16 + slope
        a.fill(6 + slope, y, 7, 32 - slope, y, 39, roof)
        for z in (7, 14, 23, 31, 39):
            a.fill(6 + slope, y, z, 32 - slope, y, z, ink)
        a.fill(6 + slope, y, 7, 6 + slope, y, 39, ink)
        a.fill(32 - slope, y, 7, 32 - slope, y, 39, ink)
    a.fill(18, 25, 9, 20, 25, 37, ink)
    for x in (10, 16, 22, 28):
        a.put(x, 16, 8, "minecraft:dark_oak_stairs[facing=south]")
        a.put(x, 16, 38, "minecraft:dark_oak_stairs[facing=north]")
    a.fill(9, 5, 32, 28, 5, 35, wood)
    for x in (13, 19, 25):
        a.fill(x - 2, 5, 15, x + 2, 6, 17, wood)
    for x, note in ((13, "spark"), (19, "shelter"), (25, "route")):
        a.put(x, 7, 16, f"gems:inkbound_{note}")
        a.put(x, 6, 19, "minecraft:lectern[facing=south,has_book=false]")
    a.put(19, 5, 27, wood)
    a.put(19, 6, 27, "gems:inkbound_press")
    for x in (11, 18, 26):
        a.put(x, 5, 32, "minecraft:bookshelf")
        a.put(x + 1, 5, 32, "minecraft:barrel[facing=up]")
        a.put(x, 13, 20, "minecraft:lantern[hanging=true]")
    # A taller paper tower, with an actual ladder and gallery, makes the
    # production building legible from the opposite garden.
    for y in range(5, 23):
        a.ring(33, 11, 41, 22, y, ink if y in (5, 12, 22) else paper)
    a.fill(34, 5, 12, 40, 21, 21, "minecraft:air")
    a.fill(34, 12, 12, 40, 12, 21, wood)
    a.fill(38, 12, 17, 39, 12, 19, "minecraft:air")
    for y in range(6, 20):
        a.put(40, y, 18, "minecraft:ladder[facing=west]")
    for z in (13, 19):
        a.fill(41, 15, z, 41, 19, z + 1, "minecraft:light_blue_stained_glass")
    a.fill(32, 23, 10, 42, 23, 23, roof)
    a.fill(34, 24, 12, 40, 24, 21, roof)
    a.fill(36, 25, 14, 38, 25, 19, ink)
    a.put(37, 26, 16, "minecraft:lightning_rod")
    a.ring(31, 9, 43, 24, 18, ink)
    for x in (32, 42):
        for z in (11, 22):
            a.put(x, 17, z, "minecraft:dark_oak_fence")
    a.fill(41, 12, 15, 45, 12, 20, wood)
    a.fill(41, 13, 16, 41, 14, 18, "minecraft:air")
    a.fill(45, 13, 15, 45, 13, 20, "minecraft:dark_oak_fence")
    for x in range(41, 46):
        a.put(x, 13, 15, "minecraft:dark_oak_fence")
        a.put(x, 13, 20, "minecraft:dark_oak_fence")
    a.put(44, 13, 17, "minecraft:lantern")
    # The separate open drying shed supplies an inhabited, lower silhouette.
    for x in (33, 43):
        for z in (28, 40):
            a.pillar(x, z, 5, 12, log)
    for z in (29, 34, 39):
        a.fill(34, 8, z, 42, 8, z, "minecraft:dark_oak_fence")
        a.fill(34, 13, z, 42, 13, z, ink)
    a.fill(32, 13, 27, 44, 13, 41, roof)
    a.fill(34, 14, 29, 42, 14, 39, roof)
    a.fill(36, 15, 31, 40, 15, 37, ink)
    a.fill(29, 4, 31, 34, 4, 35, "minecraft:polished_andesite")
    for x in (34, 38, 42):
        a.put(x, 5, 35, "minecraft:loom[facing=south]")
    a.chest(26, 5, 33, "north", "inkbound_workshop")
    a.sign(12, 5, 23, ("PRINT HALL", "Try two notes.", "Use the press.", "Try every pair."))
    a.sign(36, 5, 33, ("DRYING SHED", "Paper rests here", "before a story", "takes shape."))
    a.jigsaw(0, 5, 23, "west_up", "gems:inkbound_print_house_v2_entry")
    return a


def inkbound_garden_v2() -> Landmark:
    """A garden pavilion between an archive hut and a screen theater."""
    a = Landmark("inkbound_garden_v2")
    paper = "gems:inkbound_screen"
    ink = "gems:inkbound_inkstone"
    wood = "minecraft:dark_oak_planks"
    log = "minecraft:dark_oak_log[axis=y]"
    roof = "minecraft:deepslate_tiles"
    for x0, z0, x1, z1 in ((5, 7, 16, 20), (16, 14, 33, 32),
                           (5, 27, 16, 42)):
        a.fill(x0, 0, z0, x1, 3, z1, "minecraft:stone_bricks")
        a.fill(x0, 4, z0, x1, 4, z1, wood)
        a.fill(x0, 5, z0, x1, 22, z1, "minecraft:air")
    a.fill(30, 3, 21, 46, 3, 25, "minecraft:stone_bricks")
    a.fill(30, 4, 21, 46, 4, 25, "minecraft:polished_andesite")
    a.fill(30, 5, 21, 46, 10, 25, "minecraft:air")
    for x, z in ((34, 9), (39, 15), (37, 35), (25, 38)):
        a.fill(x - 2, 4, z - 2, x + 2, 4, z + 2, "minecraft:moss_block")
        a.pillar(x, z, 5, 11, "minecraft:cherry_log[axis=y]")
        leaves = "minecraft:cherry_leaves[persistent=true]"
        a.fill(x - 4, 11, z - 1, x + 4, 11, z + 1, leaves)
        a.fill(x - 2, 11, z - 3, x + 2, 11, z + 3, leaves)
        a.fill(x - 3, 12, z - 2, x + 3, 12, z + 2, leaves)
        a.fill(x - 1, 12, z - 4, x + 1, 12, z + 4, leaves)
        a.fill(x - 2, 13, z - 2, x + 2, 13, z + 2, leaves)
        a.fill(x - 1, 14, z - 1, x + 1, 14, z + 1, leaves)
        for dx, dz in ((-4, 0), (4, 1), (-1, -4), (1, 4)):
            a.put(x + dx, 10, z + dz, leaves)
    # The binding pavilion is open on all four sides and has tiered eaves.
    for x in (18, 31):
        for z in (16, 30):
            a.pillar(x, z, 5, 12, log)
            a.put(x, 13, z, ink)
    for slope in range(4):
        a.fill(16 + slope, 14 + slope, 14 + slope,
               33 - slope, 14 + slope, 32 - slope, roof)
        a.ring(16 + slope, 14 + slope, 33 - slope, 32 - slope,
               14 + slope, ink)
    for x in (17, 24, 32):
        a.put(x, 14, 14, "minecraft:dark_oak_stairs[facing=south]")
        a.put(x, 14, 32, "minecraft:dark_oak_stairs[facing=north]")
    a.fill(21, 18, 19, 28, 18, 27, roof)
    a.ring(21, 19, 28, 27, 18, ink)
    a.put(24, 19, 23, "minecraft:lightning_rod")
    a.put(24, 5, 23, ink)
    a.put(24, 6, 23, "gems:inkbound_binding")
    for x, z in ((20, 23), (28, 23), (24, 18), (24, 28)):
        a.put(x, 5, z, "minecraft:dark_oak_stairs[facing=south]")
    for x in (18, 31):
        a.put(x, 12, 23, "minecraft:lantern[hanging=true]")
    # Two lived-in paper buildings give the garden depth at player height.
    for x0, z0, x1, z1, height in ((6, 8, 15, 19, 12),
                                  (6, 28, 15, 41, 15)):
        for y in range(5, height):
            a.ring(x0, z0, x1, z1, y,
                   ink if y in (5, height - 1) else paper)
        a.fill(x0 + 1, 5, z0 + 1, x1 - 1, height - 2, z1 - 1,
               "minecraft:air")
        for x in (x0, x1):
            for z in (z0, z1):
                a.pillar(x, z, 5, height, log)
        for slope in range(3):
            a.fill(x0 - 1 + slope, height + 1 + slope, z0 - 1,
                   x1 + 1 - slope, height + 1 + slope, z1 + 1,
                   roof)
            a.fill(x0 - 1 + slope, height + 1 + slope, z0 - 1,
                   x0 - 1 + slope, height + 1 + slope, z1 + 1, ink)
            a.fill(x1 + 1 - slope, height + 1 + slope, z0 - 1,
                   x1 + 1 - slope, height + 1 + slope, z1 + 1, ink)
        a.fill(x0 + 4, height + 4, z0 + 1, x0 + 5, height + 4, z1 - 1, ink)
        a.fill(x1, 7, z0 + 3, x1, 9, z0 + 5,
               "minecraft:light_blue_stained_glass")
        a.fill(x1 - 2, 5, z1, x1, 8, z1, "minecraft:air")
        a.fill(x1 - 3, 4, z1 + 1, x1, 4, z1 + 3,
               "minecraft:polished_andesite")
        for x in (x0 + 1, x0 + 4, x0 + 7):
            a.put(x, 9, z0, "gems:inkbound_screen")
            a.put(x, 10, z0, "minecraft:dark_oak_fence")
        for z in (z0 + 2, z1 - 2):
            a.put(x0, 10, z, "minecraft:dark_oak_fence")
            a.put(x0, 9, z, "minecraft:lantern[hanging=true]")
        a.put(x0 + 2, 5, z0 + 2, "minecraft:bookshelf")
        a.put(x0 + 3, 5, z0 + 2, "minecraft:lectern[facing=south,has_book=false]")
        a.put(x0 + 4, height - 2, z0 + 4, "minecraft:lantern[hanging=true]")
    a.fill(14, 4, 17, 22, 4, 25, "minecraft:polished_andesite")
    a.fill(14, 5, 20, 20, 9, 25, "minecraft:air")
    a.fill(14, 4, 29, 20, 4, 33, "minecraft:polished_andesite")
    a.fill(14, 5, 29, 17, 9, 33, "minecraft:air")
    a.chest(10, 5, 33, "south", "inkbound_workshop")
    a.sign(29, 5, 25, ("PAPER GARDEN", "Bind the plan", "after trying", "each pair."))
    a.sign(12, 5, 36, ("STORY ROOM", "The last page", "leaves room for", "another voice."))
    a.jigsaw(46, 5, 23, "east_up", "gems:inkbound_garden_v2_entry")
    return a


def inkbound_arrival_lane_v2() -> Landmark:
    """A narrow terraced entry with two roofed studios, not a flat pad."""
    a = Landmark("inkbound_arrival_lane_v2", (47, 19, 31))
    ink = "gems:inkbound_inkstone"
    paper = "gems:inkbound_screen"
    wood = "minecraft:dark_oak_planks"
    log = "minecraft:dark_oak_log[axis=y]"
    for z in range(31):
        level = 4 if z < 9 else 3 if z < 16 else 2 if z < 23 else 1
        a.fill(19, 0, z, 27, level - 1, z, "minecraft:stone_bricks")
        a.fill(20, level, z, 26, level, z,
               "minecraft:polished_andesite" if z % 4 else ink)
        a.fill(20, level + 1, z, 26, 12, z, "minecraft:air")
        for x in (19, 27):
            a.put(x, level, z, wood)
    # These small planters sit on the corridor edge; the world outside the
    # lane is untouched and can keep its natural slope and trees.
    for x, z in ((14, 5), (33, 7), (13, 21), (34, 23)):
        a.fill(x - 2, 0, z - 2, x + 2, 3, z + 2,
               "minecraft:stone_bricks")
        a.fill(x - 2, 4, z - 2, x + 2, 4, z + 2,
               "minecraft:moss_block")
        a.pillar(x, z, 5, 10, "minecraft:cherry_log[axis=y]")
        leaves = "minecraft:cherry_leaves[persistent=true]"
        a.fill(x - 3, 11, z - 1, x + 3, 11, z + 1, leaves)
        a.fill(x - 1, 11, z - 3, x + 1, 11, z + 3, leaves)
        a.fill(x - 2, 12, z - 2, x + 2, 12, z + 2, leaves)
        a.put(x, 13, z, leaves)
    for x0, z0, x1, z1 in ((3, 4, 16, 16), (31, 13, 44, 27)):
        a.fill(x0, 0, z0, x1, 3, z1, "minecraft:stone_bricks")
        a.fill(x0, 4, z0, x1, 4, z1, wood)
        a.fill(x0, 5, z0, x1, 15, z1, "minecraft:air")
        for y in range(5, 11):
            a.ring(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y,
                   ink if y in (5, 10) else paper)
        a.fill(x0 + 2, 5, z0 + 2, x1 - 2, 9, z1 - 2,
               "minecraft:air")
        for x in (x0 + 1, x1 - 1):
            for z in (z0 + 1, z1 - 1):
                a.pillar(x, z, 5, 12, log)
        for slope in range(3):
            a.fill(x0 + slope, 12 + slope, z0,
                   x1 - slope, 12 + slope, z1,
                   "minecraft:deepslate_tiles")
            for z in (z0, z0 + 4, z1 - 4, z1):
                a.fill(x0 + slope, 12 + slope, z,
                       x1 - slope, 12 + slope, z, ink)
        a.fill(x0 + 5, 15, z0 + 1, x0 + 7, 15, z1 - 1, ink)
        a.fill(x1 - 1, 6, z0 + 4, x1 - 1, 8, z0 + 6,
               "minecraft:light_blue_stained_glass")
        a.put(x0 + 4, 5, z0 + 4, "minecraft:loom[facing=south]")
        a.put(x0 + 5, 9, z0 + 5, "minecraft:lantern[hanging=true]")
    # Open the studios onto the stepped public walk and bridge the two
    # different floor heights. The rooms are explorable, not facade props.
    a.fill(15, 5, 12, 15, 8, 13, "minecraft:air")
    a.fill(16, 4, 12, 18, 4, 13, "minecraft:polished_andesite")
    a.fill(32, 5, 20, 32, 8, 21, "minecraft:air")
    a.fill(31, 4, 20, 32, 4, 21, "minecraft:polished_andesite")
    a.fill(28, 3, 20, 30, 3, 21, "minecraft:polished_andesite")
    for x in (15, 32):
        a.put(x, 9, 12 if x == 15 else 20,
              "minecraft:dark_oak_log[axis=y]")
    for x in (18, 28):
        a.pillar(x, 2, 5, 11, log)
        a.put(x, 12, 2, ink)
    a.fill(17, 12, 1, 29, 12, 3, "minecraft:deepslate_tiles")
    a.ring(17, 1, 29, 3, 12, ink)
    a.chest(8, 5, 10, "east", "inkbound_workshop")
    a.sign(18, 5, 6, ("INKBOUND GROVE", "Follow the path", "to Hamed's", "workshop."))
    a.sign(35, 5, 20, ("PAPER STUDIO", "Ideas take form", "before they go", "to the press."))
    a.jigsaw(23, 5, 0, "north_up", "gems:inkbound_arrival_lane_v2_entry")
    return a


def second_chance_town() -> Landmark:
    a = Landmark("second_chance_town", (63, 31, 63))
    a.fill(3, 0, 3, 59, 3, 59, "minecraft:stone_bricks")
    a.fill(4, 4, 4, 58, 4, 58, "minecraft:polished_andesite")
    a.fill(4, 5, 4, 58, 22, 58, "minecraft:air")
    # The central crossing leaves three obvious exits, but side lanes wind
    # around small kiosks for players who prefer to explore the square.
    a.fill(27, 4, 4, 35, 4, 58, "minecraft:calcite")
    a.fill(4, 4, 27, 58, 4, 35, "minecraft:calcite")
    for edge in (8, 54):
        a.ring(edge - 2, edge - 2, edge + 2, edge + 2, 4,
               "minecraft:oxidized_cut_copper")
    for x0, z0, x1, z1, roof in (
            (7, 7, 21, 20, "minecraft:yellow_wool"),
            (41, 7, 55, 20, "minecraft:cyan_wool"),
            (7, 42, 21, 55, "minecraft:purple_wool"),
            (41, 42, 55, 55, "minecraft:red_wool")):
        for x in (x0, x1):
            for z in (z0, z1):
                a.pillar(x, z, 5, 10, "minecraft:dark_oak_log[axis=y]")
        a.fill(x0, 11, z0, x1, 11, z1, roof)
        a.fill(x0 + 2, 12, z0 + 2, x1 - 2, 12, z1 - 2,
               "minecraft:oxidized_cut_copper")
        a.fill(x0 + 2, 5, z0 + 2, x1 - 2, 5, z1 - 2,
               "minecraft:dark_oak_planks")
        for x in (x0 + 2, x1 - 2):
            a.put(x, 6, z0 + 2, "minecraft:barrel[facing=up]")
    # A low fountain is a navigational landmark, not a wall across the route.
    a.ring(24, 24, 38, 38, 5, "minecraft:cut_copper")
    a.fill(25, 5, 25, 37, 5, 37, "minecraft:water[level=0]")
    a.fill(29, 5, 29, 33, 8, 33, "minecraft:calcite")
    a.fill(30, 9, 30, 32, 9, 32, "minecraft:light_blue_stained_glass")
    a.put(31, 10, 31, "minecraft:sea_lantern")
    for x, z in ((5, 31), (57, 31), (31, 5), (31, 57)):
        a.pillar(x, z, 5, 11, "minecraft:cut_copper")
        a.put(x, 12, z, "minecraft:sea_lantern")
    a.chest(12, 5, 48, "east", "second_chance_town")
    a.chest(50, 5, 14, "west", "second_chance_town")
    a.sign(31, 5, 40, ("If the story", "changes again,", "meet here.", "Bring a map."))
    a.quote_sign(31, 5, 50, ("Welcome to the", "group buddy", "", ""))
    a.fill(55, 5, 29, 62, 9, 33, "minecraft:air")
    a.fill(55, 4, 29, 62, 4, 33, "minecraft:calcite")
    a.jigsaw(62, 5, 31, "east_up", "gems:town_casino_entry", "gems:town_casino")
    a.fill(0, 5, 29, 7, 9, 33, "minecraft:air")
    a.fill(0, 4, 29, 7, 4, 33, "minecraft:calcite")
    a.jigsaw(0, 5, 31, "west_up", "gems:town_market_entry", "gems:town_market")
    a.fill(29, 5, 0, 33, 9, 7, "minecraft:air")
    a.fill(29, 4, 0, 33, 4, 7, "minecraft:calcite")
    a.jigsaw(31, 5, 0, "north_up", "gems:town_guesthouse_entry", "gems:town_guesthouse")
    a.fill(29, 5, 55, 33, 9, 62, "minecraft:air")
    a.fill(29, 4, 55, 33, 4, 62, "minecraft:calcite")
    a.jigsaw(31, 5, 62, "south_up", "gems:town_hall_entry", "gems:town_hall")
    # Ragab's three-piece gaming quarter runs along the east edge. Slim
    # wings sit beside the larger casino without overlapping its footprint.
    for center, child in ((7, "town_arcade"), (55, "town_lounge")):
        a.fill(55, 5, center - 2, 62, 9, center + 2, "minecraft:air")
        a.fill(55, 4, center - 2, 62, 4, center + 2,
               "minecraft:polished_andesite")
        a.jigsaw(62, 5, center, "east_up", f"gems:{child}_entry",
                 f"gems:{child}")
    return a


def second_chance_town_deco_v2() -> Landmark:
    """A distinct eastern promenade tying Ragab's three buildings to the hub."""
    a = second_chance_town()
    a.name = "second_chance_town_deco_v2"
    # The west market remains a colorful public bazaar. East of the fountain,
    # the ground and two kiosks change to Ragab's teal, copper and blackstone.
    for z in range(5, 59):
        for x in range(50, 63):
            if x in (50, 62):
                a.put(x, 4, z, "gems:deco_marquee")
            elif z % 7 == 0 and 52 <= x <= 60:
                a.put(x, 4, z, "minecraft:cut_copper")
            else:
                a.put(x, 4, z, "minecraft:polished_blackstone")
    for z0 in (7, 42):
        a.fill(41, 11, z0, 55, 11, z0 + 13, "minecraft:cut_copper")
        a.fill(43, 12, z0 + 2, 53, 12, z0 + 11, "gems:deco_faience")
        for z in (z0 + 2, z0 + 11):
            a.pillar(55, z, 5, 13, "minecraft:quartz_pillar[axis=y]")
            a.put(55, 14, z, "minecraft:sea_lantern")
    # The casino approach is a broad lit portal, not a fence across the road.
    for z in (25, 37):
        a.pillar(56, z, 5, 10, "minecraft:quartz_pillar[axis=y]")
        a.put(56, 11, z, "minecraft:sea_lantern")
    a.fill(56, 12, 25, 56, 12, 37, "minecraft:cut_copper")
    a.fill(56, 13, 29, 56, 13, 33, "gems:deco_marquee")
    a.put(56, 13, 31, "gems:deco_sunburst")
    for z in (23, 39):
        a.ring(53, z - 2, 59, z + 2, 5, "minecraft:cut_copper")
        a.fill(54, 5, z - 1, 58, 5, z + 1, "minecraft:moss_block")
        a.put(56, 6, z, "minecraft:flowering_azalea")
    a.sign(55, 5, 37, ("RAGAB'S ROW", "Arcade north", "Casino east", "Lounge south"))
    return a


def second_chance_town_civic_v3() -> Landmark:
    """Four occupied civic pavilions, with Ragab's Deco row on the east."""
    a = second_chance_town_deco_v2()
    a.name = "second_chance_town_civic_v3"
    stone = "minecraft:polished_tuff"
    pale = "minecraft:smooth_quartz"
    copper = "minecraft:oxidized_cut_copper"
    dark = "minecraft:polished_blackstone_bricks"
    # Retain the existing six jigsaw thresholds and the five-block roads.
    # Replace the old four flat-roof kiosks with traversable, unequal masses.
    pavilions = (
        # extent, high roof, street-facing side, identity, working furniture
        (7, 7, 21, 20, 20, "east", "minecraft:cartography_table"),
        (41, 7, 54, 20, 21, "west", "minecraft:barrel[facing=up]"),
        (7, 42, 21, 55, 19, "east", "minecraft:lectern[facing=east]"),
        (41, 42, 54, 55, 23, "west", "minecraft:loom[facing=west]"),
    )
    for index, (x0, z0, x1, z1, roof, street, furniture) in enumerate(pavilions):
        a.fill(x0, 5, z0, x1, 23, z1, "minecraft:air")
        a.fill(x0, 4, z0, x1, 4, z1, dark if index % 2 else stone)
        # A recessed lower storey, corner piers and a projecting cornice make
        # each building read as a place from the crossroad, not a solid cube.
        for y in range(5, 13):
            a.ring(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y,
                   pale if y in (5, 12) else stone)
        a.fill(x0 + 2, 5, z0 + 2, x1 - 2, 11, z1 - 2, "minecraft:air")
        for x in (x0 + 1, x1 - 1):
            for z in (z0 + 1, z1 - 1):
                a.pillar(x, z, 5, roof - 1, copper)
        for z in (z0 + 4, z1 - 5):
            for x in (x0 + 1, x1 - 1):
                a.fill(x, 7, z, x, 10, z + 2,
                       "minecraft:light_blue_stained_glass")
                a.put(x, 11, z + 1, "gems:deco_sunburst")
        # Doorway faces the public lane; the floor continues one block out.
        wall = x1 - 1 if street == "east" else x0 + 1
        a.fill(wall, 5, z0 + 5, wall, 9, z0 + 8, "minecraft:air")
        porch = wall + (1 if street == "east" else -1)
        for z in (z0 + 4, z0 + 9):
            a.pillar(porch, z, 5, 9, pale)
            a.put(porch, 10, z, copper)
        a.fill(porch, 10, z0 + 5, porch, 10, z0 + 8, copper)
        a.fill(porch, 11, z0 + 6, porch, 11, z0 + 7,
               "gems:deco_sunburst")
        a.fill(wall + (1 if street == "east" else -1), 4, z0 + 5,
               wall + (1 if street == "east" else -1), 4, z0 + 8, pale)
        a.fill(x0 + 3, 5, z0 + 3, x1 - 3, 5, z1 - 3,
               "minecraft:dark_oak_planks")
        a.put(x0 + 4, 5, z0 + 4, furniture)
        a.put(x1 - 4, 5, z1 - 4, "minecraft:lantern")
        a.put(x0 + 4, 5, z1 - 4, "minecraft:flower_pot")
        a.fill(x0, 13, z0, x1, 13, z1, copper)
        # Different set-backs and ridge directions across the four corners.
        upper_inset = 3 if index in (0, 3) else 4
        ux0, uz0 = x0 + upper_inset, z0 + upper_inset
        ux1, uz1 = x1 - upper_inset, z1 - upper_inset
        for y in range(14, roof - 2):
            a.ring(ux0, uz0, ux1, uz1, y,
                   dark if y % 3 else "gems:deco_faience")
        a.fill(ux0 + 1, 14, uz0 + 1, ux1 - 1, roof - 4, uz1 - 1,
               "minecraft:air")
        # A working upper room is reached from its ground-floor shop, not a
        # hollow inaccessible roof. The support also gives the ladder a wall.
        a.pillar(x0 + 4, z0 + 5, 6, 14, dark)
        a.pillar(x0 + 5, z0 + 5, 6, 14,
                 "minecraft:ladder[facing=east]")
        a.put(x0 + 6, 14, z0 + 5, "minecraft:barrel[facing=up]")
        for x in (ux0, ux1):
            a.fill(x, 16, uz0 + 2, x, min(19, roof - 4), uz1 - 2,
                   "minecraft:cyan_stained_glass")
        for step in range(3):
            a.fill(ux0 + step - 1, roof - 3 + step, uz0 + step - 1,
                   ux1 - step + 1, roof - 3 + step, uz1 - step + 1,
                   "minecraft:cut_copper" if step != 1 else dark)
        # Alternate ridge direction. This is a broad stepped pitched roof,
        # with a different height and footprint at every pavilion.
        for slope in range(4):
            if index % 2:
                a.fill(ux0 - 1, roof - 3 + slope, uz0 - 1 + slope,
                       ux1 + 1, roof - 3 + slope, uz1 + 1 - slope,
                       copper if slope % 2 == 0 else dark)
            else:
                a.fill(ux0 - 1 + slope, roof - 3 + slope, uz0 - 1,
                       ux1 + 1 - slope, roof - 3 + slope, uz1 + 1,
                       copper if slope % 2 == 0 else dark)
        # Balcony and paired brackets articulate the public frontage.
        a.fill(porch, 13, z0 + 3, porch, 13, z1 - 3,
               "minecraft:dark_oak_planks")
        for z in (z0 + 3, z1 - 3):
            a.pillar(porch, z, 14, 15, copper)
        a.fill(porch, 14, z0 + 4, porch, 14, z1 - 4,
               "minecraft:dark_oak_fence")
        a.put((ux0 + ux1) // 2, roof, (uz0 + uz1) // 2,
              "minecraft:sea_lantern")
        # A second public face marks the orthogonal lane with tall glazing.
        for x in (x0 + 5, x1 - 6):
            a.fill(x, 7, z0 + 1, x + 1, 10, z0 + 1,
                   "minecraft:light_blue_stained_glass")
            a.fill(x, 7, z1 - 1, x + 1, 10, z1 - 1,
                   "minecraft:light_blue_stained_glass")
    # The northwest map office owns a tall lantern tower; its glazed clock
    # face and cap break the square's skyline beyond the four roof peaks.
    for x in (9, 14):
        for z in (9, 14):
            a.pillar(x, z, 14, 19, pale)
    a.ring(9, 9, 14, 14, 19, copper)
    for y in range(20, 26):
        a.ring(9, 9, 14, 14, y,
               "minecraft:smooth_quartz" if y in (20, 25) else dark)
    a.fill(10, 21, 10, 13, 24, 13, "minecraft:air")
    for x in (10, 13):
        a.fill(x, 22, 9, x, 24, 9, "minecraft:light_blue_stained_glass")
    a.put(11, 22, 9, "gems:deco_sunburst")
    for tier in range(3):
        a.fill(8 + tier, 26 + tier, 8 + tier,
               15 - tier, 26 + tier, 15 - tier,
               copper if tier != 1 else dark)
    a.put(11, 29, 11, "minecraft:sea_lantern")
    # The southwest is an open reading arcade, not a fourth enclosed shop.
    # Its broad two-direction colonnade and timber canopy frame town meetings.
    a.fill(7, 5, 42, 23, 23, 55, "minecraft:air")
    a.fill(7, 4, 42, 21, 4, 55, "minecraft:polished_tuff")
    for x in (8, 20):
        for z in (43, 54):
            a.pillar(x, z, 5, 12, "minecraft:dark_oak_log[axis=y]")
            a.put(x, 13, z, copper)
    for z in (46, 50):
        a.pillar(8, z, 5, 12, "minecraft:dark_oak_log[axis=y]")
    a.fill(8, 12, 43, 20, 12, 54, "minecraft:dark_oak_planks")
    for slope in range(5):
        a.fill(7 + slope, 13 + slope, 42,
               21 - slope, 13 + slope, 55,
               "minecraft:dark_oak_planks" if slope % 2 else copper)
    a.fill(12, 18, 44, 16, 18, 53, "minecraft:light_blue_stained_glass")
    a.fill(10, 4, 45, 18, 4, 51, "gems:patchwork_mosaic")
    for z in (45, 52):
        a.fill(10, 5, z, 17, 5, z,
               "minecraft:dark_oak_slab[type=bottom]")
    for z in (47, 49):
        a.fill(8, 5, z, 8, 7, z, "minecraft:bookshelf")
    a.put(15, 5, 48, "minecraft:lectern[facing=east]")
    a.put(19, 5, 46, "minecraft:lantern")
    a.sign(19, 5, 50, ("READING ARCADE", "Plan a route", "Meet at the hall", ""))
    # Ragab's southeast workshop carries an exterior stepped lit relief.
    for z0, z1, y, material in ((45, 52, 16, copper),
                                (46, 51, 17, dark),
                                (47, 50, 18, "gems:deco_faience")):
        a.fill(54, y, z0, 54, y, z1, material)
    a.put(54, 19, 48, "gems:deco_sunburst")
    # The square gets a three-tier wayfinding beacon, flanking seating and
    # lighting. Its existing shallow fountain remains the center landmark.
    for y, radius in ((11, 3), (12, 2), (13, 1)):
        a.ring(31 - radius, 31 - radius, 31 + radius, 31 + radius,
               y, pale if y == 11 else copper)
    for x in (28, 34):
        for z in (28, 34):
            a.pillar(x, z, 5, 10, pale)
    a.put(31, 14, 31, "minecraft:sea_lantern")
    for x, z in ((22, 24), (40, 24), (22, 38), (40, 38)):
        a.fill(x - 1, 5, z, x + 1, 5, z, "minecraft:dark_oak_slab[type=bottom]")
        a.put(x, 6, z, "minecraft:lantern")
    # A broad eastern colonnade visually connects arcade, casino and lounge.
    # It stays above head height, leaving all three ground thresholds clear.
    for z in range(12, 53, 8):
        a.pillar(52, z, 5, 13, pale)
        a.put(52, 14, z, "gems:deco_sunburst")
        a.put(52, 15, z, "minecraft:sea_lantern")
    a.fill(52, 16, 12, 52, 16, 52, "minecraft:cut_copper")
    a.chest(12, 5, 48, "east", "second_chance_town")
    a.chest(50, 5, 14, "west", "second_chance_town")
    return a


def second_chance_town_arrival_v4() -> Landmark:
    """A civic square with two public ground approaches and clear destinations."""
    a = second_chance_town_civic_v3()
    a.name = "second_chance_town_arrival_v4"
    # Only this new root selects the stair-equipped buildings. Earlier root
    # pools keep their old child IDs for partially generated saved starts.
    a.jigsaw(0, 5, 31, "west_up", "gems:town_market_arrival_v2_entry",
             "gems:town_market_arrival_v2")
    a.jigsaw(31, 5, 0, "north_up", "gems:town_guesthouse_arrival_v2_entry",
             "gems:town_guesthouse_arrival_v2")
    a.jigsaw(31, 5, 62, "south_up", "gems:town_hall_arrival_v6_entry",
             "gems:town_hall_arrival_v6")
    # Three tall, open portals make the occupied destinations legible from
    # the crossing without narrowing any established five-block street.
    for x, z0, z1 in ((8, 25, 37),):
        for z in (z0, z1):
            a.pillar(x, z, 5, 11, "minecraft:smooth_quartz")
            a.put(x, 12, z, "minecraft:sea_lantern")
        a.fill(x, 12, z0 + 1, x, 12, z1 - 1, "minecraft:cut_copper")
        a.put(x, 12, 31, "gems:deco_sunburst")
    for z in (8, 54):
        for x in (25, 37):
            a.pillar(x, z, 5, 11, "minecraft:smooth_quartz")
            a.put(x, 12, z, "minecraft:sea_lantern")
        a.fill(26, 12, z, 36, 12, z, "minecraft:cut_copper")
        a.put(31, 12, z, "gems:deco_sunburst")
    a.sign(8, 5, 27, ("MARKET GATE", "Ground entry", "Town Hall via", "the square"))
    a.sign(35, 5, 8, ("GUESTHOUSE", "Ground entry", "Rest and return", "to the square"))
    a.sign(35, 5, 54, ("TOWN HALL", "Services inside", "Every friend", "leaves a mark"))
    return a


def khaled_floodgate() -> Landmark:
    """An inhabited bridge over a cut channel, with floodgate rooms on both banks."""
    a = Landmark("khaled_floodgate")
    a.foundation("minecraft:grass_block", "gems:riverward_limestone", 26)
    # The water is recessed below the public paving, rather than a blue stripe
    # laid over a flat template pad. Stone cutwaters protect the bridge piers.
    a.fill(5, 2, 19, 41, 3, 27, "minecraft:water[level=0]")
    a.fill(5, 4, 19, 41, 4, 27, "minecraft:air")
    for z in (18, 28):
        a.fill(5, 4, z, 41, 5, z, "gems:riverward_limestone")
        for x in range(7, 42, 6):
            a.put(x, 6, z, "minecraft:stone_brick_wall")
    for x in (8, 14, 32, 38):
        a.fill(x - 1, 4, 17, x + 1, 4, 18, "minecraft:polished_tuff")
        a.fill(x - 1, 4, 28, x + 1, 4, 29, "minecraft:polished_tuff")
    # A narrow covered crossing links the two annexes through the bridge's
    # refuge niches; both exits remain above the water rather than dead-end.
    a.fill(8, 5, 22, 38, 5, 24, "minecraft:polished_tuff")
    for x0, x1 in ((8, 15), (31, 38)):
        for z in (21, 25):
            a.fill(x0, 6, z, x1, 6, z, "gems:riverward_limestone")
        for x in range(x0 + 1, x1, 4):
            for z in (21, 25):
                a.put(x, 7, z, "minecraft:lantern")
    # Three arches, a broad upper deck and two pedestrian refuge niches give
    # the bridge a useful silhouette from the river as well as from the road.
    a.fill(19, 5, 15, 27, 5, 31, "minecraft:polished_tuff")
    for z in (19, 23, 27):
        for x in (19, 27):
            a.pillar(x, z, 2, 5, "gems:riverward_limestone")
            a.put(x + (1 if x == 19 else -1), 3, z,
                  "minecraft:stone_brick_stairs[facing=north,half=top]")
    for x in (19, 27):
        a.fill(x, 6, 19, x, 6, 27, "gems:riverward_limestone")
    for x0, x1 in ((16, 19), (27, 30)):
        a.fill(x0, 5, 21, x1, 5, 25, "minecraft:polished_tuff")
        a.ring(x0, 21, x1, 25, 6, "gems:riverward_limestone")
        a.fill(x0 + 1, 6, 22, x1 - 1, 6, 24, "minecraft:air")
        a.put(x0, 6, 23, "minecraft:air")
        a.put(x1, 6, 23, "minecraft:air")
        a.fill(x0, 9, 21, x1, 9, 25, "minecraft:weathered_cut_copper")
        a.put((x0 + x1) // 2, 8, 23, "minecraft:lantern[hanging=true]")
        for x in (x0, x1):
            a.put(x, 7, 23, "minecraft:lantern")
    a.put(17, 5, 23, "gems:khaled_west_refuge")
    a.put(29, 5, 23, "gems:khaled_east_refuge")
    a.put(16, 8, 23, "gems:khaled_west_brace")
    a.put(30, 8, 23, "gems:khaled_east_brace")
    a.fill(21, 4, 31, 25, 4, 32, "minecraft:polished_tuff")
    for x in range(21, 26):
        a.put(x, 4, 32, "minecraft:stone_brick_stairs[facing=north,half=bottom]")
    a.fill(21, 4, 33, 25, 4, 42, "minecraft:polished_tuff")
    a.sign(23, 5, 36, ("RIVERWARD", "The bank rises.", "The shelter holds.", "Watch the gauge."))
    # Paired occupied gatehouses share limestone piers, blue glazing and
    # weathered copper eaves, while the central road stays open.
    for x0, x1 in ((10, 20), (26, 36)):
        for y in range(5, 13):
            a.ring(x0, 6, x1, 17, y,
                   "gems:riverward_limestone" if y in (5, 12)
                   else "minecraft:tuff_bricks")
        a.fill(x0 + 1, 5, 7, x1 - 1, 11, 16, "minecraft:air")
        for x in (x0, x1):
            for z in (6, 17):
                a.pillar(x, z, 5, 15, "gems:riverward_limestone")
        for z in (9, 14):
            a.fill(x0, 7, z, x0, 9, z + 1, "minecraft:light_blue_stained_glass")
            a.fill(x1, 7, z, x1, 9, z + 1, "minecraft:light_blue_stained_glass")
        for rise in range(4):
            a.fill(x0 - 1 + rise, 13 + rise, 5 + rise,
                   x1 + 1 - rise, 13 + rise, 18 - rise,
                   "minecraft:weathered_cut_copper")
        a.fill(x0 + 2, 12, 8, x1 - 2, 12, 15, "minecraft:air")
        a.put((x0 + x1) // 2, 11, 11, "minecraft:lantern[hanging=true]")
        for left in (x0 + 2, x0 + 7):
            a.fill(left, 7, 17, left + 2, 9, 17,
                   "minecraft:light_blue_stained_glass")
            a.fill(left - 1, 10, 17, left + 3, 10, 18,
                   "minecraft:weathered_cut_copper")
    a.fill(20, 5, 10, 20, 8, 13, "minecraft:air")
    a.fill(26, 5, 10, 26, 8, 13, "minecraft:air")
    a.fill(21, 5, 5, 25, 11, 18, "minecraft:air")
    for z in (6, 17):
        a.fill(21, 12, z, 25, 13, z, "gems:riverward_limestone")
        a.put(23, 14, z, "gems:riverward_gauge")
    a.fill(21, 4, 5, 25, 4, 18, "minecraft:polished_tuff")
    a.friend(16, 5, 12, "khaled")
    a.chest(13, 5, 14)
    a.chest(33, 5, 14)
    a.put(16, 9, 17, "gems:riverward_gauge")
    a.put(30, 9, 17, "gems:riverward_gauge")
    attach_east(a, "khaled_shieldworks", "minecraft:polished_tuff")
    attach_west(a, "khaled_shelter_walk", "minecraft:polished_tuff")
    for z in range(22, 25):
        a.put(7, 4, z, "minecraft:stone_brick_stairs[facing=east,half=bottom]")
        a.put(39, 4, z, "minecraft:stone_brick_stairs[facing=west,half=bottom]")
    return a


def khaled_shieldworks() -> Landmark:
    a = east_satellite("khaled_shieldworks", "gems:riverward_limestone",
                       "minecraft:polished_tuff")
    for y in range(5, 11):
        a.ring(4, 3, 17, 17, y,
               "gems:riverward_limestone" if y in (5, 10)
               else "minecraft:tuff_bricks")
    a.fill(5, 5, 4, 16, 9, 16, "minecraft:air")
    for x in (4, 17):
        for z in (3, 17):
            a.pillar(x, z, 5, 15, "gems:riverward_limestone")
    for rise in range(4):
        a.fill(3 + rise, 12 + rise, 2 + rise,
               18 - rise, 12 + rise, 18 - rise,
               "minecraft:weathered_cut_copper")
    a.fill(8, 14, 6, 13, 16, 14, "minecraft:light_blue_stained_glass")
    a.fill(4, 6, 7, 4, 8, 13, "minecraft:light_blue_stained_glass")
    a.fill(17, 6, 7, 17, 8, 13, "minecraft:light_blue_stained_glass")
    a.fill(4, 5, 9, 5, 8, 11, "minecraft:air")
    # Street-facing forge bays show the workshop's purpose from the route.
    for x0 in (6, 13):
        a.fill(x0, 6, 17, x0 + 2, 9, 17, "minecraft:light_blue_stained_glass")
        a.fill(x0 - 1, 10, 17, x0 + 3, 10, 19,
               "minecraft:weathered_cut_copper")
        for x in (x0, x0 + 2):
            a.pillar(x, 18, 5, 9, "gems:riverward_limestone")
    a.fill(10, 5, 17, 12, 8, 17, "minecraft:air")
    a.fill(9, 11, 16, 13, 11, 20, "minecraft:oxidized_cut_copper")
    for x in (9, 13):
        a.pillar(x, 20, 5, 10, "gems:riverward_limestone")
        a.put(x, 9, 19, "minecraft:lantern")
    a.fill(10, 4, 17, 12, 4, 20, "minecraft:polished_tuff")
    a.put(11, 4, 14, "gems:khaled_route_finish")
    a.fill(14, 5, 8, 16, 5, 12, "minecraft:polished_deepslate")
    for x, block in ((7, "minecraft:anvil"), (10, "minecraft:smithing_table"),
                     (13, "minecraft:grindstone")):
        a.put(x, 5, 6, block)
        a.put(x, 6, 4, "gems:riverward_gauge")
    a.fill(15, 11, 4, 17, 17, 6, "minecraft:tuff_bricks")
    a.put(16, 18, 5, "minecraft:campfire[lit=true]")
    a.chest(14, 5, 15, "west", "khaled_floodgate")
    a.sign(9, 5, 15, ("SHIELDWORKS", "For the crossing.", "Take what helps.", "Leave the rest."))
    return finish_east_satellite(a)


def khaled_shelter_walk() -> Landmark:
    a = west_satellite("khaled_shelter_walk", "gems:riverward_limestone",
                       "minecraft:polished_tuff")
    # Open bays turn the roof into a legible refuge, not a closed corridor.
    for x in (3, 7, 10, 13, 17):
        for z in (3, 17):
            a.pillar(x, z, 5, 11, "gems:riverward_limestone")
    a.fill(2, 12, 2, 18, 12, 18, "minecraft:weathered_cut_copper")
    for rise in range(3):
        a.fill(3 + rise, 13 + rise, 3 + rise,
               17 - rise, 13 + rise, 17 - rise,
               "minecraft:oxidized_cut_copper")
    a.fill(7, 15, 6, 13, 15, 14, "minecraft:light_blue_stained_glass")
    for x in (5, 10, 15):
        a.fill(x - 1, 4, 8, x + 1, 4, 12, "gems:riverward_limestone")
        if x != 10:
            a.put(x, 5, 10, "gems:riverward_gauge")
        a.put(x, 11, 10, "minecraft:lantern[hanging=true]")
    a.put(10, 4, 10, "gems:khaled_route_start")
    for z in (5, 15):
        a.fill(3, 4, z, 17, 4, z, "minecraft:water[level=0]")
    a.chest(5, 5, 15, "east", "khaled_floodgate")
    a.sign(12, 5, 15, ("SHELTER WALK", "Water can pass.", "People can pause.", "Cross by cover."))
    return finish_west_satellite(a)


def nour_signal_harbor() -> Landmark:
    """A working shore station: tower, inhabited radio hall and boat landing."""
    a = Landmark("nour_signal_harbor")
    a.foundation("minecraft:sand", "gems:harbor_chalk", 27)
    # The south edge is a cut-in landing basin, with a dry route from both
    # jigsaw wings to the tower and a real pier over water.
    a.fill(7, 2, 32, 39, 3, 43, "minecraft:water[level=0]")
    a.fill(7, 4, 32, 39, 4, 43, "minecraft:air")
    a.fill(21, 4, 27, 25, 4, 41, "minecraft:spruce_planks")
    for z in (30, 35, 40):
        for x in (20, 26):
            a.pillar(x, z, 2, 5, "minecraft:stripped_spruce_log")
            a.put(x, 6, z, "minecraft:lantern")
    for x in (9, 37):
        a.fill(x, 4, 29, x + 1, 4, 39, "gems:harbor_wave_tile")
        for z in (29, 35, 39):
            a.pillar(x, z, 4, 7, "minecraft:stripped_spruce_log")
            a.put(x, 8, z, "minecraft:light_blue_banner")
    # The radio hall is inhabited, light on its shore face, and open toward
    # both clue houses. White masonry and blue glass repeat across the set.
    for y in range(5, 13):
        a.ring(12, 9, 34, 29, y,
               "gems:harbor_chalk" if y in (5, 12) else "minecraft:calcite")
    a.fill(13, 5, 10, 33, 11, 28, "minecraft:air")
    for x in (12, 34):
        for z in (9, 29):
            a.pillar(x, z, 5, 16, "gems:harbor_wave_tile")
    for x in (16, 23, 30):
        a.fill(x, 7, 9, x + 2, 10, 9, "minecraft:light_blue_stained_glass")
        a.fill(x, 7, 29, x + 2, 10, 29, "minecraft:light_blue_stained_glass")
    for z in (13, 23):
        a.fill(12, 7, z, 12, 10, z + 2, "minecraft:light_blue_stained_glass")
        a.fill(34, 7, z, 34, 10, z + 2, "minecraft:light_blue_stained_glass")
    a.fill(21, 5, 29, 25, 9, 29, "minecraft:air")
    for rise in range(4):
        a.fill(11 + rise, 13 + rise, 8 + rise,
               35 - rise, 13 + rise, 30 - rise,
               "minecraft:oxidized_cut_copper")
    a.fill(17, 16, 13, 29, 17, 25, "minecraft:air")
    # A visible beacon and two mast arms give this a radio silhouette rather
    # than making it another clock or gothic tower.
    for y in range(17, 26):
        a.ring(19, 12, 27, 20, y, "gems:harbor_chalk")
    a.fill(20, 17, 13, 26, 24, 19, "minecraft:air")
    for y in (19, 22):
        for z in (13, 18):
            a.fill(19, y, z, 19, y + 1, z + 1, "minecraft:light_blue_stained_glass")
            a.fill(27, y, z, 27, y + 1, z + 1, "minecraft:light_blue_stained_glass")
    a.fill(18, 26, 11, 28, 26, 21, "gems:harbor_wave_tile")
    a.fill(21, 27, 14, 25, 27, 18, "minecraft:sea_lantern")
    a.pillar(23, 16, 28, 30, "minecraft:lightning_rod")
    a.fill(15, 28, 16, 31, 28, 16, "minecraft:oxidized_copper")
    for x in (15, 31):
        a.put(x, 29, 16, "minecraft:end_rod")
    for x in (18, 28):
        a.fill(x, 24, 15, x, 24, 23, "minecraft:oxidized_copper")
        a.put(x, 24, 23, "minecraft:end_rod")
    # Three lamps are player choices; the two side-house plaques contain the
    # full ordering when read together.
    for x, lamp in ((18, "blue"), (23, "amber"), (28, "white")):
        a.fill(x - 1, 5, 23, x + 1, 5, 25, "gems:harbor_wave_tile")
        a.put(x, 6, 24, f"gems:nour_{lamp}_lamp")
        a.put(x, 8, 24, "minecraft:sea_lantern")
    a.friend(23, 5, 18, "nour")
    a.put(23, 4, 21, "gems:nour_relay_tower")
    a.chest(15, 5, 16)
    a.chest(31, 5, 16)
    a.sign(23, 5, 33, ("SIGNAL HARBOR", "The boat left.", "The fog replied.", "The berth waited."))
    a.quote_sign(17, 5, 18, ("TeamSpeak time", "", "", ""))
    attach_east(a, "nour_boathouse", "minecraft:spruce_planks")
    attach_west(a, "nour_foghouse", "minecraft:spruce_planks")
    return a


def nour_boathouse() -> Landmark:
    a = east_satellite("nour_boathouse", "gems:harbor_chalk", "minecraft:spruce_planks")
    for y in range(5, 12):
        a.ring(4, 3, 17, 17, y, "minecraft:calcite")
    a.fill(5, 5, 4, 16, 10, 16, "minecraft:air")
    for x in (4, 17):
        for z in (3, 17):
            a.pillar(x, z, 5, 14, "gems:harbor_wave_tile")
    a.fill(4, 7, 6, 4, 10, 10, "minecraft:light_blue_stained_glass")
    a.fill(17, 7, 6, 17, 10, 10, "minecraft:light_blue_stained_glass")
    a.fill(7, 5, 17, 14, 9, 17, "minecraft:air")
    for rise in range(4):
        a.fill(3 + rise, 12 + rise, 2 + rise,
               18 - rise, 12 + rise, 18 - rise, "minecraft:oxidized_cut_copper")
    # A taller pitched slipway roof and a hanging boat make this useful
    # building visibly different from the low fog signal house.
    for x in range(4, 18):
        roof_y = 17 - abs(x - 10) // 2
        a.fill(x, roof_y, 2, x, roof_y, 18, "minecraft:dark_prismarine")
        if 6 <= x <= 14:
            a.put(x, roof_y - 1, 17, "minecraft:light_blue_stained_glass")
    a.fill(8, 16, 17, 13, 16, 20, "minecraft:oxidized_copper")
    for x in (8, 13):
        a.pillar(x, 19, 8, 15, "minecraft:stripped_spruce_log")
    a.fill(8, 4, 17, 13, 4, 20, "minecraft:water[level=0]")
    a.fill(10, 5, 15, 11, 5, 20, "minecraft:spruce_planks")
    for x in (8, 13):
        a.put(x, 6, 16, "minecraft:spruce_stairs[facing=south]")
        a.put(x, 6, 17, "minecraft:spruce_stairs[facing=south]")
    a.fill(9, 6, 16, 12, 6, 18, "minecraft:dark_oak_slab")
    a.fill(10, 7, 16, 11, 7, 17, "minecraft:spruce_planks")
    a.fill(7, 5, 13, 14, 5, 16, "minecraft:dark_oak_planks")
    for x in (7, 14):
        a.put(x, 6, 15, "minecraft:chain")
        a.put(x, 7, 15, "minecraft:lantern[hanging=true]")
    a.put(10, 5, 10, "gems:nour_boat_clue")
    a.put(10, 4, 11, "gems:nour_relay_boat")
    a.chest(14, 5, 7, "west", "nour_signal_harbor")
    a.sign(11, 5, 14, ("BOATHOUSE LOG", "Blue sail gone", "at first bell.", "Berth: white."))
    return finish_east_satellite(a)


def nour_foghouse() -> Landmark:
    a = west_satellite("nour_foghouse", "gems:harbor_chalk", "minecraft:spruce_planks")
    for y in range(5, 10):
        a.ring(3, 3, 17, 17, y, "minecraft:calcite")
    a.fill(4, 5, 4, 16, 8, 16, "minecraft:air")
    for x in (3, 17):
        for z in (3, 17):
            a.pillar(x, z, 5, 13, "gems:harbor_wave_tile")
    a.fill(3, 7, 6, 3, 8, 13, "minecraft:light_blue_stained_glass")
    a.fill(17, 7, 6, 17, 8, 13, "minecraft:light_blue_stained_glass")
    for rise in range(3):
        a.fill(2 + rise, 10 + rise, 2 + rise,
               18 - rise, 10 + rise, 18 - rise, "minecraft:oxidized_cut_copper")
    # The horn mouths, glazed monitoring window and paired vent stacks make
    # the signal plant recognizable from the harbor approach.
    for x0 in (5, 13):
        a.fill(x0, 6, 17, x0 + 2, 9, 17, "minecraft:light_blue_stained_glass")
        a.fill(x0 - 1, 10, 17, x0 + 3, 10, 19, "gems:harbor_wave_tile")
        a.pillar(x0 + 1, 11, 12, 17, "minecraft:oxidized_copper")
        a.fill(x0, 18, 10, x0 + 2, 18, 12, "minecraft:weathered_copper")
        a.put(x0 + 1, 18, 13, "minecraft:iron_trapdoor[facing=south,open=false]")
    a.fill(9, 5, 17, 11, 8, 17, "minecraft:air")
    a.fill(8, 10, 16, 12, 10, 20, "minecraft:oxidized_cut_copper")
    for x in (8, 12):
        a.pillar(x, 20, 5, 9, "gems:harbor_wave_tile")
    a.fill(5, 5, 12, 15, 5, 14, "gems:harbor_wave_tile")
    for x in (6, 14):
        a.pillar(x, 12, 6, 9, "minecraft:iron_bars")
        a.put(x, 10, 12, "minecraft:sea_lantern")
    a.put(10, 5, 10, "gems:nour_fog_clue")
    a.put(10, 4, 11, "gems:nour_relay_fog")
    a.chest(5, 5, 7, "east", "nour_signal_harbor")
    a.sign(10, 5, 15, ("FOGHOUSE LOG", "Amber horn", "answered beyond", "the grey water."))
    return finish_west_satellite(a)


def town_casino() -> Landmark:
    a = Landmark("town_casino", (31, 25, 31))
    a.fill(0, 0, 0, 30, 3, 30, "minecraft:stone_bricks")
    a.fill(0, 4, 0, 30, 4, 30, "minecraft:polished_blackstone")
    a.fill(0, 5, 0, 30, 24, 30, "minecraft:air")
    # Six distinct table bays surround a lit central floor. The building
    # itself is a waypoint, while each table will later open its own game UI.
    for y in range(5, 15):
        a.ring(2, 2, 28, 28, y, "minecraft:dark_oak_planks")
    a.fill(3, 5, 3, 27, 13, 27, "minecraft:air")
    for x in (2, 28):
        for z in (5, 11, 19, 25):
            a.fill(x, 8, z, x, 11, z + 2, "minecraft:yellow_stained_glass")
    for z in (2, 28):
        for x in (5, 11, 19, 25):
            a.fill(x, 8, z, x + 2, 11, z, "minecraft:yellow_stained_glass")
    for z in (4, 16, 28):
        for x in (1, 29):
            a.pillar(x, z, 5, 14, "minecraft:cut_copper")
            a.put(x, 15, z, "minecraft:sea_lantern")
    for x in (4, 16, 28):
        for z in (1, 29):
            a.pillar(x, z, 5, 14, "minecraft:cut_copper")
            a.put(x, 15, z, "minecraft:sea_lantern")
    # Pale relief piers and lit copper crowns break up the dark side walls.
    # They sit one block beyond the wall, leaving every window and route open.
    for offset in (9, 23):
        for x, z in ((29, offset), (offset, 29)):
            a.pillar(x, z, 5, 12, "minecraft:quartz_pillar[axis=y]")
            a.put(x, 5, z, "minecraft:cut_copper")
            a.put(x, 12, z, "minecraft:yellow_concrete")
            a.put(x, 13, z, "minecraft:sea_lantern")
    a.ring(1, 1, 29, 29, 14, "minecraft:cut_copper")
    for x, z, carpet in ((7, 8, "minecraft:red_carpet"),
                          (18, 8, "minecraft:blue_carpet"),
                          (7, 18, "minecraft:green_carpet"),
                          (18, 18, "minecraft:purple_carpet"),
                          (7, 25, "minecraft:yellow_carpet"),
                          (18, 25, "minecraft:cyan_carpet")):
        a.fill(x, 5, z, x + 5, 5, z + 2, "minecraft:dark_oak_planks")
        a.fill(x + 1, 6, z, x + 4, 6, z + 2, carpet)
        a.put(x + 2, 6, z + 1, "minecraft:sea_lantern")
    for x, z, label in (
            (9, 6, ("RPS TOURNAMENT", "First to two", "Stake 3", "Ask Ragab")),
            (20, 6, ("TWENTY-ONE", "Hit or stand", "Stake 4", "Ask Ragab")),
            (9, 16, ("COLOR WHEEL", "Pick color/half", "Stake 2", "Ask Ragab")),
            (20, 16, ("HOLD AND SPIN", "Keep a symbol", "Stake 3", "Ask Ragab")),
            (9, 23, ("TRIVIA", "One question", "Stake 2", "Ask Ragab")),
            (20, 23, ("SIGNAL TABLE", "Two players", "Stake 2 each", "Ask Ragab")),
    ):
        a.sign(x, 5, z, label)
    a.ring(11, 11, 19, 19, 5, "minecraft:cut_copper")
    a.fill(12, 5, 12, 18, 5, 18, "minecraft:polished_andesite")
    for rise in range(5):
        a.ring(1 + rise, 1 + rise, 29 - rise, 29 - rise,
               15 + rise, "minecraft:oxidized_cut_copper")
    a.fill(8, 19, 8, 22, 19, 22, "minecraft:purple_stained_glass")
    # Four lit roof finials break the hall's silhouette above its copper roof.
    for x in (4, 26):
        for z in (4, 26):
            a.pillar(x, z, 5, 21, "minecraft:cut_copper")
            a.fill(x - 1, 22, z - 1, x + 1, 22, z + 1,
                   "minecraft:polished_blackstone")
            a.put(x, 23, z, "minecraft:sea_lantern")
    a.chest(24, 5, 23, "west", "second_chance_town")
    a.sign(15, 5, 22, ("Know the game.", "Know the stake.", "Only then", "place a bet."))
    a.quote_sign(13, 5, 23, ("even when i", "lose i win", "", ""))
    a.quote_sign(22, 5, 21, ("the game isnt", "the reward", "", ""))
    a.quote_sign(17, 5, 21, ("Pi chart", "pi time", "", ""))
    a.quote_sign(7, 5, 15, ("I just gotta", "roll the wheel", "some time", ""))
    a.pillar(1, 12, 5, 12, "minecraft:quartz_block")
    a.pillar(1, 18, 5, 12, "minecraft:quartz_block")
    a.fill(1, 13, 12, 1, 13, 18, "minecraft:quartz_block")
    a.friend(15, 5, 24, "ragab")
    a.fill(0, 5, 13, 4, 9, 17, "minecraft:air")
    a.fill(1, 5, 13, 5, 5, 17, "minecraft:red_carpet")
    # The west entry has an art-deco lintel, a copper canopy and bright
    # vertical columns. All entrance blocks below the canopy remain open.
    for z in (10, 20):
        a.pillar(0, z, 5, 19, "minecraft:quartz_pillar[axis=y]")
        a.put(0, 20, z, "minecraft:sea_lantern")
    a.fill(0, 11, 11, 4, 11, 19, "minecraft:cut_copper")
    a.fill(0, 12, 12, 0, 16, 18, "minecraft:polished_blackstone")
    a.fill(0, 13, 13, 0, 13, 17, "minecraft:yellow_concrete")
    a.fill(0, 15, 14, 0, 15, 16, "minecraft:cyan_concrete")
    a.fill(0, 17, 11, 0, 17, 19, "minecraft:cut_copper")
    # A tall stepped lantern marks the entrance from the town square.
    a.fill(0, 18, 13, 0, 19, 17, "minecraft:quartz_block")
    a.fill(0, 20, 14, 0, 22, 16, "minecraft:quartz_block")
    a.fill(0, 23, 15, 0, 23, 15, "minecraft:sea_lantern")
    a.fill(0, 19, 15, 0, 21, 15, "minecraft:cyan_concrete")
    a.put(0, 22, 15, "minecraft:yellow_concrete")
    a.jigsaw(0, 5, 15, "west_up", "gems:town_casino_entry")
    return a


def deco_wing(name: str) -> Landmark:
    a = Landmark(name, (21, 22, 15))
    a.fill(0, 0, 0, 20, 3, 14, "minecraft:stone_bricks")
    a.fill(0, 4, 0, 20, 4, 14, "minecraft:polished_blackstone")
    a.fill(0, 5, 0, 20, 21, 14, "minecraft:air")
    for y in range(5, 14):
        a.ring(2, 2, 18, 12, y,
               "gems:deco_faience" if y in (5, 13) else "minecraft:quartz_block")
    a.fill(3, 5, 3, 17, 12, 11, "minecraft:air")
    for x in (3, 10, 17):
        for z in (2, 12):
            a.pillar(x, z, 5, 15, "minecraft:cut_copper")
            a.put(x, 16, z, "minecraft:sea_lantern")
    for rise in range(4):
        a.ring(1 + rise, 1 + rise, 19 - rise, 13 - rise,
               14 + rise, "minecraft:oxidized_cut_copper")
    a.fill(6, 17, 4, 14, 17, 10, "minecraft:yellow_stained_glass")
    for z in (2, 12):
        for x in (6, 14):
            a.fill(x, 7, z, x + 1, 11, z,
                   "minecraft:yellow_stained_glass")
            a.fill(x, 6, z, x + 1, 6, z, "gems:deco_faience")
            a.fill(x, 12, z, x + 1, 12, z, "gems:deco_faience")
    a.fill(0, 5, 5, 4, 9, 9, "minecraft:air")
    a.fill(0, 4, 5, 4, 4, 9, "minecraft:polished_andesite")
    a.fill(0, 11, 4, 5, 11, 10, "minecraft:cut_copper")
    for z in (3, 11):
        a.pillar(0, z, 5, 17, "minecraft:quartz_pillar[axis=y]")
        a.put(0, 18, z, "minecraft:sea_lantern")
    a.jigsaw(0, 5, 7, "west_up", f"gems:{name}_entry")
    return a


def town_arcade() -> Landmark:
    a = deco_wing("town_arcade")
    # Vertical fins and colored light over an open practice floor echo the
    # cinema/arcade references while leaving a legible route inside.
    for x in (5, 9, 13, 17):
        a.pillar(x, 12, 6, 16, "gems:deco_faience")
        a.put(x, 17, 12, "minecraft:yellow_stained_glass")
    # An asymmetric clockless tower gives the arcade its own landmark profile.
    a.fill(3, 17, 2, 8, 20, 6, "minecraft:quartz_block")
    a.fill(4, 18, 2, 5, 20, 2, "minecraft:yellow_stained_glass")
    a.fill(6, 18, 2, 7, 20, 2, "gems:deco_faience")
    a.fill(2, 21, 1, 9, 21, 7, "minecraft:cut_copper")
    for x, color in ((6, "minecraft:red_carpet"),
                     (10, "minecraft:cyan_carpet"),
                     (14, "minecraft:purple_carpet")):
        a.fill(x, 5, 5, x + 1, 5, 8, color)
        a.put(x, 5, 10, "minecraft:polished_blackstone")
        a.put(x, 6, 10, "minecraft:sea_lantern")
    a.fill(7, 5, 3, 13, 5, 3, "minecraft:cut_copper")
    a.chest(16, 5, 9, "west", "second_chance_town")
    a.sign(8, 5, 10, ("THE ARCADE", "Warm up here.", "Ragab runs the", "games next door."))
    return a


def town_lounge() -> Landmark:
    a = deco_wing("town_lounge")
    # The lounge is an open courtyard under sparse copper pergola ribs. Its
    # silhouette is deliberately lower and more horizontal than the arcade.
    a.fill(1, 14, 1, 19, 18, 13, "minecraft:air")
    a.ring(2, 2, 18, 12, 14, "gems:deco_faience")
    for x in (3, 7, 11, 15, 18):
        a.fill(x, 16, 2, x, 16, 12, "minecraft:cut_copper")
    for z in (2, 12):
        for x in (3, 18):
            a.pillar(x, z, 14, 17, "minecraft:quartz_pillar[axis=y]")
            a.put(x, 18, z, "minecraft:sea_lantern")
    a.fill(5, 4, 4, 15, 4, 10, "minecraft:polished_andesite")
    a.ring(5, 4, 15, 10, 5, "gems:deco_faience")
    a.fill(6, 5, 5, 14, 5, 9, "minecraft:polished_blackstone")
    a.fill(8, 5, 6, 12, 5, 8, "minecraft:cut_copper")
    a.put(10, 6, 7, "minecraft:sea_lantern")
    for x, facing in ((5, "east"), (15, "west")):
        for z in (5, 9):
            a.put(x, 6, z,
                  f"minecraft:dark_oak_stairs[facing={facing},half=bottom,shape=straight]")
    for x in (4, 16):
        a.pillar(x, 1, 5, 19, "minecraft:quartz_pillar[axis=y]")
        a.put(x, 20, 1, "minecraft:sea_lantern")
    a.fill(4, 14, 1, 16, 14, 1, "gems:deco_faience")
    a.chest(16, 5, 9, "west", "second_chance_town")
    a.sign(8, 5, 10, ("THE LOUNGE", "Take a breath.", "The next round", "can wait."))
    return a


def town_casino_deco_v2() -> Landmark:
    """Ragab's taller stepped marquee and six distinct lit table canopies."""
    a = town_casino()
    a.name = "town_casino_deco_v2"
    # The sign is a three-dimensional stepped crown over the existing open
    # five-block entrance. It does not occupy the lobby or any game station.
    for y0, y1, x1, z0, z1, block in (
            (17, 18, 5, 8, 22, "minecraft:quartz_block"),
            (19, 20, 4, 10, 20, "minecraft:cut_copper"),
            (21, 22, 3, 12, 18, "minecraft:quartz_block")):
        a.fill(0, y0, z0, x1, y1, z1, block)
    a.fill(0, 17, 13, 0, 17, 17, "gems:deco_marquee")
    a.put(0, 17, 15, "gems:deco_sunburst")
    a.fill(0, 19, 13, 0, 19, 17, "minecraft:yellow_stained_glass")
    a.fill(0, 21, 14, 0, 21, 16, "minecraft:cyan_stained_glass")
    a.put(0, 23, 15, "minecraft:sea_lantern")
    # Ribs, window aprons and copper transoms carry the same visual language
    # around the sides instead of leaving plain uninterrupted dark walls.
    for z in (4, 8, 22, 26):
        a.pillar(1, z, 5, 15, "minecraft:quartz_pillar[axis=y]")
        a.put(1, 16, z, "gems:deco_faience")
    for x in (6, 12, 18, 24):
        for z in (1, 29):
            a.pillar(x, z, 6, 13, "minecraft:quartz_pillar[axis=y]")
            a.fill(x - 1, 14, z, x + 1, 14, z, "minecraft:cut_copper")
            a.put(x, 15, z, "minecraft:sea_lantern")
    for z in (2, 28):
        for x0 in (7, 13, 19):
            a.fill(x0, 6, z, x0 + 3, 6, z, "gems:deco_faience")
            a.fill(x0, 12, z, x0 + 3, 12, z, "minecraft:cut_copper")
    # Each game bay gets its own high framed canopy; colors are readable from
    # the aisle while the existing tables and signs remain untouched.
    for x, z, glass in (
            (7, 8, "minecraft:red_stained_glass"),
            (18, 8, "minecraft:blue_stained_glass"),
            (7, 18, "minecraft:green_stained_glass"),
            (18, 18, "minecraft:purple_stained_glass"),
            (7, 25, "minecraft:yellow_stained_glass"),
            (18, 25, "minecraft:cyan_stained_glass")):
        a.fill(x, 10, z, x + 5, 10, z + 2, "minecraft:cut_copper")
        a.fill(x + 1, 10, z + 1, x + 4, 10, z + 1, glass)
        a.put(x + 2, 9, z + 1, "minecraft:sea_lantern")
    # Version 1 keeps its original NBT and sign copy for existing worlds.
    # The six version 2 signs are usable seats, so tell players to interact.
    for x, z, lines in (
            (9, 6, ("RPS TOURNAMENT", "First to two", "Stake 3", "Use this sign")),
            (20, 6, ("TWENTY-ONE", "Hit or stand", "Stake 4", "Use this sign")),
            (9, 16, ("COLOR WHEEL", "Pick color/half", "Stake 2", "Use this sign")),
            (20, 16, ("HOLD AND SPIN", "Keep a symbol", "Stake 3", "Use this sign")),
            (9, 23, ("TRIVIA", "One question", "Stake 2", "Use this sign")),
            (20, 23, ("SIGNAL TABLE", "Two players", "Stake 2 each", "Use this sign"))):
        a.sign(x, 5, z, lines)
    a.signs = list(dict.fromkeys(a.signs))
    a.fill(13, 14, 13, 17, 14, 17, "gems:deco_marquee")
    a.put(15, 14, 15, "gems:deco_sunburst")
    a.put(15, 13, 15, "minecraft:sea_lantern")
    return a


def town_arcade_deco_v2() -> Landmark:
    """A stepped picture-house wing with three readable game displays."""
    a = town_arcade()
    a.name = "town_arcade_deco_v2"
    # Extend the asymmetric tower into a layered street-facing silhouette.
    a.fill(1, 14, 1, 9, 14, 7, "minecraft:cut_copper")
    a.fill(2, 16, 2, 8, 16, 6, "gems:deco_marquee")
    a.fill(3, 18, 2, 7, 19, 5, "minecraft:quartz_block")
    a.fill(4, 20, 2, 6, 20, 4, "minecraft:yellow_stained_glass")
    a.put(5, 20, 2, "gems:deco_sunburst")
    a.put(5, 21, 3, "minecraft:sea_lantern")
    for x, glass in ((6, "minecraft:red_stained_glass"),
                     (10, "minecraft:cyan_stained_glass"),
                     (14, "minecraft:purple_stained_glass")):
        a.fill(x, 7, 11, x + 1, 9, 11, glass)
        a.fill(x, 10, 11, x + 1, 10, 11, "minecraft:cut_copper")
    return a


def town_lounge_deco_v2() -> Landmark:
    """Open garden room with a patterned floor and a lower copper skyline."""
    a = town_lounge()
    a.name = "town_lounge_deco_v2"
    for x in range(5, 16):
        for z in range(4, 11):
            if x in (5, 15) or z in (4, 10) or (x + z) % 5 == 0:
                a.put(x, 4, z, "gems:deco_marquee")
    a.put(10, 5, 4, "gems:deco_sunburst")
    for x in (6, 10, 14):
        a.fill(x, 16, 2, x, 16, 12, "minecraft:cut_copper")
        a.put(x, 17, 7, "minecraft:sea_lantern")
    a.fill(6, 18, 3, 14, 18, 11, "minecraft:air")
    for z in (3, 11):
        a.fill(4, 15, z, 16, 15, z, "gems:deco_marquee")
    return a


def town_market() -> Landmark:
    a = west_satellite("town_market", "minecraft:stone_bricks", "minecraft:spruce_planks")
    for z0, colors in ((2, ("minecraft:yellow_wool", "minecraft:white_wool")),
                       (8, ("minecraft:cyan_wool", "minecraft:white_wool")),
                       (14, ("minecraft:red_wool", "minecraft:white_wool"))):
        for x in (4, 17):
            for z in (z0, z0 + 4):
                a.pillar(x, z, 5, 10, "minecraft:stripped_spruce_log[axis=y]")
        for x in range(3, 19):
            a.fill(x, 11, z0, x, 11, z0 + 4, colors[(x // 2) % 2])
        a.put(17, 10, z0 + 2, "minecraft:lantern[hanging=true]")
    # The middle canopy is the open route from town. Each stall has one
    # actual supply cache and a workstation, not rows of empty inventories.
    a.fill(5, 5, 4, 15, 5, 4, "minecraft:spruce_planks")
    a.put(6, 5, 4, "minecraft:smoker[facing=south]")
    a.supply_barrel(10, 5, 4, "town_market_food")
    a.put(6, 5, 3, "minecraft:hay_block")
    a.put(8, 5, 3, "minecraft:melon")
    a.fill(5, 5, 10, 7, 5, 10, "minecraft:spruce_planks")
    a.put(5, 5, 10, "minecraft:cartography_table")
    a.supply_barrel(7, 5, 10, "town_market_route")
    a.fill(5, 5, 16, 15, 5, 16, "minecraft:spruce_planks")
    a.put(6, 5, 16, "minecraft:crafting_table")
    a.supply_barrel(10, 5, 16, "town_market_tools")
    a.put(14, 5, 16, "minecraft:fletching_table")
    a.chest(16, 5, 16, "west", "second_chance_town")
    a.sign(14, 5, 5, ("TRAIL FOOD", "Take a snack", "before you", "head out."))
    a.sign(9, 5, 9, ("ROUTE DESK", "Light and maps", "Rumor Atlas", "finds places."))
    a.sign(14, 5, 15, ("FIELD KIT", "Tools for", "the long road.", ""))
    a.sign(6, 5, 18, ("Open when", "someone is here.", "Someone is", "always here."))
    a.quote_sign(12, 5, 13, ("totally normal", "friendship", "", ""))
    return finish_west_satellite(a)


def town_market_arrival_v2() -> Landmark:
    """A two-lane market stair cut into the authored four-block plinth."""
    a = town_market()
    a.name = "town_market_arrival_v2"
    a.jigsaw(20, 5, 10, "east_up", "gems:town_market_arrival_v2_entry")
    for x in range(5):
        for z in (11, 12):
            a.fill(x, x + 1, z, x, 10, z, "minecraft:air")
            a.put(x, x, z, "minecraft:cut_copper" if x in (0, 4)
                  else "minecraft:polished_blackstone_bricks")
        for edge in (10, 13):
            a.put(x, x + 1, edge, "gems:deco_faience")
    # The old canopy post at (4, 12) sits in the new stair lane. Its canopy
    # remains supported by the other columns and this broader entry frame.
    for z in (8, 15):
        a.pillar(0, z, 5, 10, "minecraft:smooth_quartz")
        a.put(0, 11, z, "minecraft:sea_lantern")
    a.fill(0, 11, 9, 0, 11, 14, "minecraft:cut_copper")
    a.put(0, 11, 11, "gems:deco_sunburst")
    a.sign(0, 5, 16, ("MARKET GATE", "Stairs to town", "Hall through", "the square"), 12)
    return a


def town_guesthouse() -> Landmark:
    a = Landmark("town_guesthouse", (21, 22, 21))
    a.fill(0, 0, 0, 20, 3, 20, "minecraft:stone_bricks")
    a.fill(0, 4, 0, 20, 4, 20, "minecraft:spruce_planks")
    a.fill(0, 5, 0, 20, 21, 20, "minecraft:air")
    for y in range(5, 12):
        a.ring(3, 3, 17, 17, y, "minecraft:brick")
    a.fill(4, 5, 4, 16, 10, 16, "minecraft:air")
    # Exposed beams and an actual skylight keep the common room bright.
    a.ring(4, 4, 16, 16, 12, "minecraft:dark_oak_planks")
    for z in (8, 13):
        a.fill(4, 12, z, 16, 12, z, "minecraft:stripped_spruce_log[axis=x]")
    for rise in range(4):
        a.fill(2 + rise, 13 + rise, 2, 18 - rise, 13 + rise, 18,
               "minecraft:oxidized_cut_copper")
    a.fill(8, 13, 5, 12, 16, 15, "minecraft:air")
    a.fill(8, 17, 5, 12, 17, 15, "minecraft:light_blue_stained_glass")
    for x in (5, 15):
        for z in (5, 15):
            a.put(x, 6, z, "minecraft:lantern")
    for z in (5, 10, 15):
        a.fill(3, 7, z, 3, 9, z + 1, "minecraft:light_blue_stained_glass")
        a.fill(17, 7, z, 17, 9, z + 1, "minecraft:light_blue_stained_glass")
    a.fill(5, 11, 3, 15, 11, 4, "minecraft:dark_oak_planks")
    a.fill(5, 12, 3, 15, 12, 3, "minecraft:spruce_fence")
    a.pillar(5, 3, 5, 12, "minecraft:cut_copper")
    a.pillar(15, 3, 5, 12, "minecraft:cut_copper")
    a.chest(5, 5, 15, "east", "second_chance_town")
    a.sign(15, 5, 6, ("If you missed", "the last trip,", "there's room for", "another."))
    a.quote_sign(14, 5, 12, ("im not building", "up to a joke", "", ""))
    a.quote_sign(10, 5, 10, ("Proof that", "everyone can be", "redeemed", ""))
    a.fill(8, 5, 16, 12, 9, 20, "minecraft:air")
    # A square-column porch makes the entrance visible from the town route.
    for x in (6, 14):
        a.put(x, 5, 19, "minecraft:polished_andesite")
        a.pillar(x, 19, 6, 10, "minecraft:stripped_spruce_log[axis=y]")
        a.put(x, 10, 19, "minecraft:cut_copper")
    a.fill(6, 11, 17, 14, 11, 20, "minecraft:dark_oak_planks")
    a.fill(7, 12, 18, 13, 12, 20, "minecraft:oxidized_cut_copper")
    for x in (8, 12):
        a.put(x, 10, 19, "minecraft:lantern[hanging=true]")
    for z in (3, 17):
        for x0 in (5, 14):
            a.fill(x0, 7, z, x0 + 1, 9, z, "minecraft:light_blue_stained_glass")
            a.fill(x0, 6, z, x0 + 1, 6, z, "minecraft:polished_andesite")
            a.fill(x0, 10, z, x0 + 1, 10, z, "minecraft:cut_copper")
    # Four usable beds leave a clear route from the porch to both residents.
    for x, z, color, facing, head_z in (
            (5, 7, "blue", "north", 6),
            (15, 9, "green", "north", 8),
            (6, 12, "red", "south", 13),
            (14, 14, "yellow", "south", 15)):
        a.put(x, 5, z, f"minecraft:{color}_bed[facing={facing},occupied=false,part=foot]")
        a.put(x, 5, head_z, f"minecraft:{color}_bed[facing={facing},occupied=false,part=head]")
    a.fill(8, 5, 13, 12, 5, 15, "minecraft:blue_carpet")
    a.fill(8, 5, 6, 12, 5, 8, "minecraft:yellow_carpet")
    a.sign(6, 5, 18, ("Rooms for", "travelers.", "Rest before", "the next route."))
    a.jigsaw(10, 5, 20, "south_up", "gems:town_guesthouse_entry")
    return a


def town_guesthouse_arrival_v2() -> Landmark:
    """An open garden stair brings visitors from ground into the guesthouse."""
    a = town_guesthouse()
    a.name = "town_guesthouse_arrival_v2"
    a.jigsaw(10, 5, 20, "south_up", "gems:town_guesthouse_arrival_v2_entry")
    for z in range(5):
        for x in (8, 9):
            a.fill(x, z + 1, z, x, 9, z, "minecraft:air")
            a.put(x, z, z, "minecraft:cut_copper" if z in (0, 4)
                  else "minecraft:polished_andesite")
        for edge in (7, 10):
            a.put(edge, z + 1, z, "minecraft:oxidized_cut_copper")
    # The existing central wall becomes a real garden-room door. Keep the
    # four beds, lore signs, roof, and town-facing porch at their old points.
    a.fill(8, 5, 3, 9, 9, 3, "minecraft:air")
    for z in list(range(6, 9)) + list(range(13, 16)):
        for x in (8, 9):
            a.put(x, 5, z, "minecraft:air")
    for x in (6, 12):
        a.pillar(x, 0, 5, 10, "minecraft:stripped_spruce_log[axis=y]")
        a.put(x, 11, 0, "minecraft:sea_lantern")
    a.fill(7, 11, 0, 11, 11, 0, "minecraft:cut_copper")
    a.put(9, 11, 0, "gems:deco_sunburst")
    a.sign(13, 5, 0, ("GUESTHOUSE", "Steps to rest", "Town square", "straight ahead"), 12)
    return a


def town_hall() -> Landmark:
    a = Landmark("town_hall", (31, 25, 31))
    a.fill(0, 0, 0, 30, 3, 30, "minecraft:stone_bricks")
    a.fill(0, 4, 0, 30, 4, 30, "minecraft:polished_andesite")
    a.fill(0, 5, 0, 30, 24, 30, "minecraft:air")
    # The town has one civic material language: pale stone, weathered copper,
    # dark timber and a skylit roof. Keep the center visible from the entrance.
    for y in range(5, 14):
        a.ring(3, 3, 27, 27, y, "minecraft:polished_tuff")
    a.fill(4, 5, 4, 26, 13, 26, "minecraft:air")
    for x in (3, 27):
        for z in (6, 13, 20, 25):
            a.fill(x, 5, z, x, 15, z + 1, "minecraft:quartz_pillar[axis=y]")
    for z in (3, 27):
        for x in (6, 24):
            a.fill(x, 5, z, x + 1, 15, z, "minecraft:quartz_pillar[axis=y]")
    for x in (3, 27):
        for z in (9, 16, 23):
            a.fill(x, 7, z, x, 11, z + 2, "minecraft:light_blue_stained_glass")
            a.put(x, 12, z + 1, "minecraft:yellow_stained_glass")
    for z in (3, 27):
        for x in (10, 20):
            a.fill(x, 7, z, x + 2, 11, z, "minecraft:light_blue_stained_glass")
            a.put(x + 1, 12, z, "minecraft:yellow_stained_glass")
    for x in range(2, 29):
        roof_y = 15 + (13 - abs(x - 15)) // 3
        a.fill(x, roof_y, 2, x, roof_y, 28, "minecraft:dark_prismarine")
    for z in (3, 27):
        for x in range(4, 27):
            roof_y = 15 + (13 - abs(x - 15)) // 3
            a.fill(x, 14, z, x, roof_y - 1, z, "minecraft:polished_tuff")
        a.fill(13, 17, z, 17, 18, z, "minecraft:light_blue_stained_glass")
    for x in range(13, 18):
        roof_y = 15 + (13 - abs(x - 15)) // 3
        a.fill(x, roof_y, 11, x, roof_y, 19, "minecraft:light_blue_stained_glass")
    # A pale cross remains open around the register. Copper borders indicate
    # counters, while the center mosaic points back to the entrance.
    a.fill(13, 4, 4, 17, 4, 26, "minecraft:calcite")
    a.fill(4, 4, 12, 26, 4, 13, "minecraft:calcite")
    a.fill(4, 4, 17, 26, 4, 18, "minecraft:calcite")
    a.fill(14, 4, 14, 16, 4, 16, "gems:patchwork_mosaic")
    for z in (9, 15, 20, 24):
        for x0, x1 in ((4, 11), (14, 16), (19, 26)):
            a.fill(x0, 5, z, x1, 5, z + 1, "minecraft:polished_tuff")
            a.fill(x0, 4, z - 1, x1, 4, z - 1, "minecraft:oxidized_cut_copper")
        for x in (12, 18):
            a.put(x, 4, z, "minecraft:calcite")
    # A flush label behind each service replaces the old separate sign plinth.
    # The shorter copy keeps the counter readable at ordinary screen widths.
    services = (
        (5, 9, "repair_bench", "REPAIR BENCH", "Soryal's yard", "Mend a tool"),
        (10, 9, "angle_desk", "ANGLE DESK", "Framefall", "Mark approach"),
        (15, 9, "story_council", "STORY COUNCIL", "Two boss wins", "Choose a path"),
        (20, 9, "tournament_board", "TOURNAMENT", "Ragab's games", "Daily supplies"),
        (25, 9, "shortcut_board", "SHORTCUT BOARD", "Ali's route", "Pair two marks"),
        (5, 15, "shelter_desk", "SHELTER DESK", "Khaled's route", "Brief shelter"),
        (15, 15, "ledger", "TOWN LEDGER", "Hushwire", "Call a scout"),
        (20, 15, "inkbound_desk", "INKBOUND DESK", "Hamed's notes", "Field plan"),
        (25, 15, "signal_table", "SIGNAL TABLE", "Nour's harbor", "Share a signal"),
        (5, 20, "return_board", "RETURN BOARD", "Lastlight", "Set way back"),
        (10, 20, "route_desk", "ROUTE DESK", "Branch", "Alternate route"),
        (15, 20, "rescue_dispatch", "RESCUE DISPATCH", "Redline", "Rally bell"),
        (20, 20, "stage_desk", "STAGE DESK", "Unwritten", "Pick opening"),
        (25, 20, "practice_stage", "PRACTICE STAGE", "Rehearsal", "Try a boss tell"),
        (5, 24, "story_trail", "STORY TRAIL", "Yehia's garden", "Find a stash"),
        (10, 24, "atlas_pin", "ATLAS PIN", "Adl's cairn", "Share a point"),
        (15, 24, "knowledge_shelf", "KNOWLEDGE SHELF", "Othman's notes", "Read the lead"),
        (20, 24, "welcome_board", "WELCOME BOARD", "Fam's forum", "Story hint"),
        (25, 24, "lost_found", "LOST AND FOUND", "Omar's search", "Find a lead"),
    )
    for x, z, block, name, source, purpose in services:
        a.put(x, 5, z, "minecraft:cut_copper")
        a.put(x, 6, z, f"gems:townhall_{block}")
        a.put(x, 6, z + 1, "minecraft:cut_copper")
        a.sign(x, 7, z + 1, (name, source, purpose, ""))
    # Three wayfinding signs at the threshold are enough to orient visitors.
    for x, lines in (
            (7, ("WEST WING", "Travel tools", "", "")),
            (15, ("TOWN HALL", "Stories, help", "", "")),
            (23, ("EAST WING", "Friends, games", "", ""))):
        a.put(x, 5, 5, "minecraft:cut_copper")
        a.sign(x, 6, 5, lines)
    a.chest(7, 5, 22, "east", "second_chance_town")
    a.fill(13, 5, 0, 17, 10, 3, "minecraft:air")
    a.fill(13, 4, 0, 17, 4, 3, "minecraft:calcite")
    a.jigsaw(15, 5, 0, "north_up", "gems:town_hall_entry")
    return a


def town_hall_alcoves_v2() -> Landmark:
    """Four legible service rooms for new towns; saved halls keep their old ID."""
    hall = town_hall()
    hall.name = "town_hall_alcoves_v2"

    # Keep all service blocks, inscriptions and the central aisle in place.
    # The continuous side counters become individual service pedestals.
    for z in (9, 15, 20, 24):
        for x0, x1 in ((4, 11), (19, 26)):
            hall.fill(x0, 5, z, x1, 5, z + 1, "minecraft:air")
        for x in (5, 10, 20, 25):
            hall.put(x, 5, z, "minecraft:cut_copper")
            hall.put(x, 5, z + 1, "minecraft:cut_copper")

    zones = (
        # name, extent, portal, floor, wall, frame, detail
        ("Prep", 4, 11, 7, 16, 12, 10, 14,
         "minecraft:orange_terracotta", "minecraft:bricks",
         "minecraft:cut_copper", "minecraft:yellow_terracotta"),
        ("Tactics", 19, 26, 7, 16, 18, 9, 15,
         "minecraft:cyan_terracotta", "minecraft:oxidized_cut_copper",
         "minecraft:light_blue_stained_glass", "minecraft:yellow_stained_glass"),
        ("Routes", 4, 11, 18, 26, 12, 19, 25,
         "minecraft:blue_terracotta", "minecraft:polished_deepslate",
         "minecraft:calcite", "minecraft:light_blue_stained_glass"),
        ("Stories", 19, 26, 18, 26, 18, 20, 25,
         "minecraft:green_terracotta", "minecraft:dark_oak_planks",
         "minecraft:bookshelf", "minecraft:moss_block"),
    )
    for name, x0, x1, z0, z1, wall_x, door0, door1, floor, wall, frame, detail in zones:
        hall.fill(x0, 4, z0, x1, 4, z1, floor)
        hall.fill(wall_x, 5, z0, wall_x, 9, z1, wall)
        hall.fill(wall_x, 5, door0, wall_x, 8, door1, "minecraft:air")
        hall.fill(wall_x, 10, z0, wall_x, 10, z1, frame)
        for jamb_z in (door0 - 1, door1 + 1):
            hall.fill(wall_x, 6, jamb_z, wall_x, 9, jamb_z, frame)
        if name == "Routes":
            for end_z in (z0, z1):
                hall.fill(wall_x, 5, end_z, wall_x, 8, end_z, frame)
        else:
            hall.put(wall_x, 8, z0 + 1, detail)
            hall.put(wall_x, 8, z1 - 1, detail)
        for x in (5, 10, 20, 25):
            if x0 <= x <= x1:
                for z in (9, 15, 20, 24):
                    if z0 <= z <= z1:
                        hall.put(x, 5, z, frame)
                        hall.put(x, 5, z + 1, frame)

    hall.fill(4, 5, 17, 11, 9, 17, "minecraft:polished_tuff")
    hall.fill(19, 5, 17, 26, 9, 17, "minecraft:polished_tuff")
    hall.sign(7, 6, 5, ("WEST SIDE", "Prep ahead", "Routes beyond", ""))
    hall.sign(15, 6, 5, ("TOWN HALL", "W Prep/Routes",
                         "E Tactics/Story", "Find your desk"))
    hall.sign(23, 6, 5, ("EAST SIDE", "Tactics ahead", "Stories beyond", ""))
    return hall


def town_hall_gallery_v3() -> Landmark:
    """An open civic gallery with four readable service groups for new towns."""
    hall = town_hall()
    hall.name = "town_hall_gallery_v3"

    # Keep the twenty services and their signs at their established coordinates.
    # Remove the unused length of each counter, including the central counters:
    # two clear lanes now pass on either side of the central service islands.
    for z in (9, 15, 20, 24):
        for x0, x1 in ((4, 11), (14, 16), (19, 26)):
            hall.fill(x0, 5, z, x1, 5, z + 1, "minecraft:air")
        for x in (5, 10, 15, 20, 25):
            hall.put(x, 5, z, "minecraft:polished_tuff")
            hall.put(x, 5, z + 1, "minecraft:polished_tuff")
            hall.put(x, 6, z + 1, "minecraft:polished_tuff")

    # A common pale field and slim accent outline distinguish destinations
    # without making four sealed rooms. Each side is open along its aisle edge.
    zones = (
        # floor rectangle, aisle edge, accent, category, cue, sign rotation
        (4, 11, 7, 16, 12, "minecraft:cut_copper", "PREP", "Tools + aid", 12),
        (19, 26, 7, 16, 18, "minecraft:oxidized_cut_copper",
         "TACTICS", "Bosses", 4),
        (4, 11, 18, 26, 12, "minecraft:polished_deepslate",
         "ROUTES", "Travel", 12),
        (19, 26, 18, 26, 18, "minecraft:moss_block",
         "STORIES", "Friends", 4),
    )
    for x0, x1, z0, z1, edge, accent, category, cue, facing in zones:
        hall.fill(x0, 4, z0, x1, 4, z1, "minecraft:smooth_stone")
        hall.fill(x0, 4, z0, x1, 4, z0, accent)
        hall.fill(x0, 4, z1, x1, 4, z1, accent)
        hall.fill(x0, 4, z0, x0, 4, z1, accent)
        hall.fill(x1, 4, z0, x1, 4, z1, accent)
        # Two narrow piers and a high lintel mark a wing but never wall it off.
        for z in (z0, z1):
            hall.pillar(edge, z, 5, 9, "minecraft:polished_tuff")
            hall.put(edge, 10, z, accent)
        hall.fill(edge, 10, z0 + 1, edge, 10, z1 - 1,
                  "minecraft:polished_tuff")
        hall.put(edge, 5, z0 + 1, "minecraft:polished_tuff")
        hall.put(edge, 6, z0 + 1, accent)
        hall.sign(edge, 7, z0 + 1, (category, cue, "", ""), facing)

    # Entry labels sit at the wing portals and above the central path. Nothing
    # at eye height blocks the view into the hall from its front door.
    for x in (7, 15, 23):
        hall.put(x, 5, 5, "minecraft:air")
        hall.put(x, 6, 5, "minecraft:air")
        hall.signs.remove((x, 6, 5))
    hall.fill(13, 9, 6, 17, 9, 6, "minecraft:polished_tuff")
    hall.put(15, 9, 6, "minecraft:cut_copper")
    hall.sign(15, 10, 6, ("TOWN HALL", "Follow colors", "Find your desk", ""))
    return hall


def town_hall_civic_v4() -> Landmark:
    """Open service gallery under four skylit wings and a stepped civic crown."""
    hall = town_hall_gallery_v3()
    hall.name = "town_hall_civic_v4"
    pale = "minecraft:smooth_quartz"
    copper = "minecraft:cut_copper"
    dark = "minecraft:polished_tuff"
    # A five-block-deep entry arcade makes the hall legible from the square.
    # The central five-block opening and original jigsaw remain untouched.
    for x in (5, 9, 21, 25):
        hall.pillar(x, 1, 5, 13, pale)
        hall.put(x, 14, 1, "gems:deco_sunburst")
    for x0, x1 in ((5, 9), (21, 25)):
        hall.fill(x0, 14, 0, x1, 14, 3, copper)
        hall.fill(x0 + 1, 15, 1, x1 - 1, 15, 2, dark)
    hall.fill(11, 13, 1, 19, 13, 2, copper)
    hall.fill(13, 14, 1, 17, 14, 2, dark)
    hall.put(15, 15, 1, "minecraft:sea_lantern")
    # Corner campaniles have unequal caps and lit recesses. They sit outside
    # the occupied service floor, so all twenty established desks still work.
    for x0, z0, top in ((0, 4, 22), (26, 4, 20),
                        (0, 24, 19), (26, 24, 23)):
        x1, z1 = x0 + 4, z0 + 4
        for y in range(5, top - 2):
            hall.ring(x0, z0, x1, z1, y,
                      pale if y in (5, 11, top - 3) else dark)
        hall.fill(x0 + 1, 5, z0 + 1, x1 - 1, top - 4, z1 - 1,
                  "minecraft:air")
        hall.fill(x0 + 1, 9, z0, x1 - 1, 12, z0,
                  "minecraft:light_blue_stained_glass")
        hall.fill(x0 + 1, 9, z1, x1 - 1, 12, z1,
                  "minecraft:light_blue_stained_glass")
        for rise in range(3):
            hall.fill(x0 + rise, top - 2 + rise, z0 + rise,
                      x1 - rise, top - 2 + rise, z1 - rise,
                      copper if rise != 1 else dark)
        hall.put(x0 + 2, min(24, top + 1), z0 + 2,
                 "minecraft:sea_lantern")
    # Four upper roof bays vary the silhouette without blocking the central
    # strip of glass above the ledger at (15, 19, 15).
    for x0, x1, z0, z1, crest in (
            (5, 11, 9, 14, 22), (19, 25, 9, 14, 21),
            (5, 11, 18, 23, 21), (19, 25, 18, 23, 23)):
        hall.ring(x0, z0, x1, z1, 18, copper)
        hall.fill(x0 + 1, 19, z0 + 1, x1 - 1, crest - 2, z1 - 1,
                  "minecraft:light_blue_stained_glass")
        hall.ring(x0, z0, x1, z1, crest - 1, dark)
        hall.fill(x0 + 1, crest, z0 + 1, x1 - 1, crest, z1 - 1,
                  copper)
    # Each group has a high decorative marker and a different civic material.
    # The y=5..8 walking and interaction volume is preserved in full.
    for x, z, accent in ((7, 8, "minecraft:cut_copper"),
                         (23, 8, "gems:deco_faience"),
                         (7, 19, "minecraft:polished_deepslate"),
                         (23, 19, "minecraft:moss_block")):
        hall.fill(x - 2, 12, z, x + 2, 12, z, dark)
        hall.put(x, 13, z, accent)
        hall.put(x, 14, z, "minecraft:sea_lantern")
    return hall


def town_hall_civic_v5() -> Landmark:
    """Current civic hall with the same open nineteen-service plan."""
    hall = town_hall_civic_v4()
    hall.name = "town_hall_civic_v5"
    return hall


def town_hall_arrival_v6() -> Landmark:
    """A visible front portal for the square's established service gallery."""
    hall = town_hall_civic_v5()
    hall.name = "town_hall_arrival_v6"
    hall.jigsaw(15, 5, 0, "north_up", "gems:town_hall_arrival_v6_entry")
    hall.fill(11, 4, 0, 19, 4, 3, "minecraft:smooth_quartz")
    hall.fill(13, 4, 0, 17, 4, 3, "minecraft:cut_copper")
    for x in (11, 19):
        hall.pillar(x, 1, 5, 12, "minecraft:smooth_quartz")
        hall.put(x, 13, 1, "minecraft:sea_lantern")
    hall.fill(12, 13, 1, 18, 13, 1, "minecraft:cut_copper")
    hall.put(15, 13, 1, "gems:deco_sunburst")
    hall.sign(11, 5, 2, ("TOWN HALL", "Routes + stories", "All services", "inside"))
    return hall


def fam_forum() -> Landmark:
    """A garden forum with a sheltered speaker's floor and open listening rings."""
    a = Landmark("fam_forum")
    a.foundation("gems:forum_mosaic", "minecraft:terracotta", 24)
    # The circles are low enough to sit on; four broad aisles stay clear. The
    # floor pattern continues into both wings instead of ending at a template edge.
    for x in range(5, 42):
        for z in range(5, 42):
            distance = (x - 23) ** 2 + (z - 23) ** 2
            aisle = abs(x - 23) <= 2 or abs(z - 23) <= 2
            if 130 <= distance <= 150 or 250 <= distance <= 278:
                a.put(x, 4, z, "gems:forum_mosaic")
                if not aisle:
                    a.put(x, 5, z, "minecraft:cut_sandstone_slab[type=bottom]")
            elif distance < 125 and (x + z) % 7 == 0:
                a.put(x, 4, z, "minecraft:orange_terracotta")
    # An open-air lecture pavilion: four columns, a shade roof, an accessible
    # platform and hanging lights. The surrounding rings remain walkable.
    a.fill(18, 4, 17, 28, 4, 29, "minecraft:smooth_sandstone")
    a.fill(20, 5, 19, 26, 5, 27, "minecraft:cut_sandstone")
    a.fill(21, 5, 27, 25, 5, 31, "minecraft:cut_sandstone")
    for x in (18, 28):
        for z in (17, 29):
            a.put(x, 5, z, "gems:forum_plaster")
            a.pillar(x, z, 6, 12, "minecraft:stripped_acacia_log")
            a.put(x, 13, z, "minecraft:chiseled_sandstone")
    a.fill(17, 14, 16, 29, 14, 30, "minecraft:orange_terracotta")
    a.fill(19, 15, 18, 27, 15, 28, "minecraft:blue_terracotta")
    a.fill(21, 16, 20, 25, 16, 26, "minecraft:yellow_terracotta")
    for x in (19, 27):
        for z in (18, 28):
            a.put(x, 13, z, "minecraft:lantern[hanging=true]")
    for x, z in ((11, 11), (35, 11), (11, 35), (35, 35)):
        a.ring(x - 2, z - 2, x + 2, z + 2, 5, "gems:forum_plaster")
        a.fill(x - 1, 5, z - 1, x + 1, 5, z + 1, "minecraft:moss_block")
        a.put(x, 6, z, "minecraft:flowering_azalea")
        a.pillar(x - 2, z - 2, 6, 9, "minecraft:acacia_fence")
        a.put(x - 2, 10, z - 2, "minecraft:blue_banner")
    # A framed southern entry gives the low assembly ground a readable face
    # from the glade, without closing its open sightline to the speaker.
    for x in (17, 29):
        a.put(x, 5, 40, "gems:forum_plaster")
        a.pillar(x, 40, 6, 11, "minecraft:stripped_acacia_log")
        a.put(x, 12, 40, "minecraft:chiseled_sandstone")
        a.put(x, 10, 39, "minecraft:blue_banner")
    a.fill(17, 13, 40, 29, 13, 40, "minecraft:acacia_planks")
    a.fill(20, 14, 40, 26, 14, 40, "minecraft:blue_terracotta")
    for x in (20, 26):
        a.put(x, 12, 40, "minecraft:lantern[hanging=true]")
    a.friend(23, 6, 22, "fam")
    a.put(23, 6, 25, "gems:fam_forum_board")
    a.chest(19, 5, 22)
    a.chest(27, 5, 22)
    a.sign(23, 5, 34, ("THE FORUM", "Hear both sides.", "Then make your", "own route."))
    a.quote_sign(23, 6, 31, ("Do we have a", "tutorial for", "this group?", ""))
    attach_east(a, "fam_story_hall", "gems:forum_mosaic")
    attach_west(a, "fam_open_school", "gems:forum_mosaic")
    return a


def fam_story_hall() -> Landmark:
    """A glazed discussion hall, enclosed and taller than the open school."""
    a = east_satellite("fam_story_hall", "minecraft:terracotta", "gems:forum_mosaic")
    for y in range(5, 12):
        a.ring(4, 3, 17, 17, y, "gems:forum_plaster")
    a.fill(5, 5, 4, 16, 10, 16, "minecraft:air")
    for x in (4, 17):
        for z in (3, 17):
            a.pillar(x, z, 5, 13, "minecraft:stripped_acacia_log")
    for z in (6, 12):
        a.fill(4, 7, z, 4, 9, z + 2, "minecraft:light_blue_stained_glass")
        a.fill(17, 7, z, 17, 9, z + 2, "minecraft:light_blue_stained_glass")
    # The public-facing south elevation has tall openings, relief piers and a
    # carved teaching emblem, so its silhouette does not become a blank box.
    for x in (6, 12):
        a.fill(x, 7, 17, x + 3, 10, 17, "minecraft:light_blue_stained_glass")
        a.fill(x, 6, 17, x + 3, 6, 17, "minecraft:cut_sandstone")
        a.fill(x, 11, 17, x + 3, 11, 17, "minecraft:blue_terracotta")
    for x in (4, 10, 17):
        a.pillar(x, 17, 6, 11, "minecraft:stripped_acacia_log")
        a.put(x, 12, 17, "minecraft:chiseled_sandstone")
    a.fill(8, 13, 18, 13, 14, 18, "gems:forum_plaster")
    a.fill(10, 14, 18, 11, 14, 18, "minecraft:blue_terracotta")
    a.put(10, 15, 18, "minecraft:yellow_terracotta")
    for x in (7, 14):
        a.put(x, 7, 18, "minecraft:blue_banner")
    a.fill(3, 12, 2, 18, 12, 18, "minecraft:blue_terracotta")
    a.fill(5, 13, 4, 16, 13, 16, "minecraft:orange_terracotta")
    a.fill(8, 14, 6, 13, 14, 14, "minecraft:yellow_terracotta")
    a.fill(9, 15, 7, 12, 15, 13, "minecraft:blue_terracotta")
    a.fill(4, 5, 8, 6, 9, 12, "minecraft:air")
    a.fill(7, 5, 7, 14, 5, 9, "minecraft:dark_oak_planks")
    for x in (7, 14):
        a.fill(x, 5, 11, x, 5, 14, "minecraft:acacia_slab[type=bottom]")
    a.put(10, 6, 8, "gems:fam_story_clue")
    a.put(12, 6, 8, "gems:fam_dispatch_story")
    a.sign(12, 6, 10, ("DISPATCH STOP", "Bring the forum", "message here", "then carry on"))
    a.chest(14, 5, 15, "west", "fam_forum")
    a.quote_sign(10, 5, 15, ("We really need", "to make one", "", ""))
    a.sign(11, 5, 5, ("STORY HALL", "One voice has", "a question.", "Find the rest."))
    return finish_east_satellite(a)


def fam_open_school() -> Landmark:
    """An outdoor teaching colonnade with a light pergola and long benches."""
    a = west_satellite("fam_open_school", "minecraft:terracotta", "gems:forum_mosaic")
    for x in (3, 8, 13, 18):
        for z in (3, 17):
            a.put(x, 5, z, "gems:forum_plaster")
            a.pillar(x, z, 6, 11, "minecraft:stripped_acacia_log")
            a.put(x, 12, z, "minecraft:chiseled_sandstone")
    for z in (3, 17):
        a.fill(3, 13, z, 18, 13, z, "minecraft:acacia_planks")
    for x in (3, 8, 13, 18):
        a.fill(x, 13, 3, x, 13, 17, "minecraft:acacia_planks")
    for x in (5, 10, 15):
        a.fill(x, 5, 5, x, 5, 15, "minecraft:cut_sandstone_slab[type=bottom]")
    a.fill(6, 5, 7, 12, 5, 9, "minecraft:dark_oak_planks")
    a.put(8, 6, 8, "gems:fam_school_clue")
    a.put(8, 6, 10, "gems:fam_dispatch_school")
    a.sign(12, 6, 10, ("DISPATCH STOP", "Bring the forum", "message here", "then carry on"))
    for x in (4, 17):
        a.put(x, 6, 10, "minecraft:blue_banner")
    for x, z in ((5, 5), (16, 15)):
        a.put(x, 6, z, "minecraft:lantern")
    a.chest(4, 5, 14, "east", "fam_forum")
    a.quote_sign(11, 6, 8, ("Me myself and I", "", "", ""))
    a.quote_sign(6, 5, 12, ("How about we", "have a nice",
                             "discussion on", "the group"))
    a.sign(10, 5, 15, ("OPEN SCHOOL", "Read together.", "A solo traveler", "has every clue."))
    return finish_west_satellite(a)


def adl_survey_house() -> Landmark:
    """A split-roof map house, lookout and visible survey baseline."""
    a = Landmark("adl_survey_house")
    a.foundation("minecraft:stone", "gems:survey_stone", 27)
    a.fill(9, 4, 8, 37, 4, 34, "gems:compass_mosaic")
    # Fixed radial lines lead the eye from the southern approach to both
    # lateral sightposts, even though the roof mass is asymmetrical.
    a.fill(21, 4, 33, 25, 4, 42, "gems:compass_mosaic")
    a.fill(4, 4, 21, 9, 4, 25, "gems:compass_mosaic")
    a.fill(37, 4, 21, 42, 4, 25, "gems:compass_mosaic")
    for y in range(5, 13):
        a.ring(9, 9, 37, 32, y, "gems:survey_stone")
    a.fill(10, 5, 10, 36, 12, 31, "minecraft:air")
    for x in (9, 37):
        for z in (9, 20, 32):
            a.pillar(x, z, 5, 15, "minecraft:stripped_spruce_log")
    for z in (9, 32):
        for x in (14, 30):
            a.pillar(x, z, 5, 14, "minecraft:stripped_spruce_log")
    for x in (13, 29):
        a.fill(x, 7, 32, x + 5, 10, 32, "minecraft:light_blue_stained_glass")
        a.fill(x, 7, 9, x + 5, 10, 9, "minecraft:light_blue_stained_glass")
    for z in (13, 25):
        a.fill(9, 7, z, 9, 10, z + 3, "minecraft:light_blue_stained_glass")
        a.fill(37, 7, z, 37, 10, z + 3, "minecraft:light_blue_stained_glass")
    a.fill(21, 5, 32, 25, 9, 32, "minecraft:air")
    # Two folded roof leaves meet along a copper rain channel. A thin strip
    # of skylight turns the working room into a map-reading space.
    for ridge in (16, 30):
        for x in range(ridge - 6, ridge + 7):
            height = 13 + max(0, (6 - abs(x - ridge)) // 2)
            a.fill(x, height, 8, x, height, 33, "minecraft:dark_oak_planks")
        a.fill(ridge, 16, 13, ridge, 16, 28, "minecraft:light_blue_stained_glass")
    a.fill(23, 13, 8, 23, 13, 33, "minecraft:weathered_cut_copper")
    a.fill(18, 4, 18, 28, 4, 28, "gems:compass_mosaic")
    a.put(23, 5, 23, "gems:adl_map_table")
    a.fill(18, 5, 20, 20, 5, 26, "minecraft:dark_oak_planks")
    a.fill(26, 5, 20, 28, 5, 26, "minecraft:dark_oak_planks")
    for x in (14, 33):
        for z in (13, 28):
            a.put(x, 6, z, "minecraft:lantern")
    # The rooftop observatory has a sheltered window ring and a reachable
    # ladder shaft from the map room rather than a decorative inaccessible cap.
    for y in range(17, 24):
        a.ring(19, 11, 27, 19, y, "gems:survey_stone")
    a.fill(20, 17, 12, 26, 22, 18, "minecraft:air")
    for y in (19, 21):
        for z in (13, 17):
            a.fill(19, y, z, 19, y + 1, z + 1, "minecraft:light_blue_stained_glass")
            a.fill(27, y, z, 27, y + 1, z + 1, "minecraft:light_blue_stained_glass")
    a.fill(18, 24, 10, 28, 24, 20, "minecraft:weathered_cut_copper")
    a.fill(20, 25, 12, 26, 25, 18, "minecraft:light_blue_stained_glass")
    a.pillar(23, 15, 26, 29, "minecraft:lightning_rod")
    a.fill(23, 5, 19, 23, 23, 19, "gems:survey_stone")
    a.fill(23, 5, 18, 23, 23, 18, "minecraft:ladder[facing=north]")
    # A standalone trig marker and red-white baseline flag distinguish the
    # survey ground from another village hall.
    a.pillar(35, 38, 5, 8, "gems:survey_stone")
    a.put(35, 9, 38, "minecraft:lodestone")
    # Both bearings meet behind the house on the observatory's center line.
    a.fill(21, 4, 2, 25, 4, 6, "gems:compass_mosaic")
    for x in (21, 25):
        for z in (2, 6):
            a.pillar(x, z, 5, 6, "gems:survey_stone")
    a.put(23, 5, 4, "gems:adl_cairn")
    for x in (12, 34):
        a.pillar(x, 38, 5, 10, "minecraft:spruce_fence")
        a.put(x, 11, 38, "minecraft:orange_banner")
    a.friend(16, 5, 23, "adl")
    a.chest(13, 5, 25)
    a.chest(34, 5, 25)
    a.sign(23, 5, 37, ("SURVEY HOUSE", "Two sightposts", "mark a path.", "Check both."))
    attach_east(a, "adl_sightpost", "gems:compass_mosaic")
    attach_west(a, "adl_chart_shelter", "gems:compass_mosaic")
    return a


def adl_survey_house_facade_v2() -> Landmark:
    """Reserve survey marks for trim, with broad stone and timber wall panels."""
    a = adl_survey_house()
    a.name = "adl_survey_house_facade_v2"
    # The old tiled wall is kept for saved structures. Only the new template
    # trades its large uninterrupted surfaces for a stone base and timber bays.
    for x in range(10, 37):
        for z in (9, 32):
            for y in (5, 6):
                if a.blocks.get((x, y, z), (None,))[0] == "gems:survey_stone":
                    a.put(x, y, z, "minecraft:polished_andesite")
            for y in (11, 12):
                if a.blocks.get((x, y, z), (None,))[0] == "gems:survey_stone":
                    a.put(x, y, z, "minecraft:dark_oak_planks")
    for z in range(10, 32):
        for x in (9, 37):
            for y in (5, 6, 11, 12):
                if a.blocks.get((x, y, z), (None,))[0] == "gems:survey_stone":
                    a.put(x, y, z, "minecraft:polished_andesite" if y < 7
                          else "minecraft:dark_oak_planks")
    for x0, x1 in ((10, 12), (18, 20), (26, 28), (35, 36)):
        for z in (9, 32):
            for x in range(x0, x1 + 1):
                for y in range(7, 11):
                    if a.blocks.get((x, y, z), (None,))[0] == "gems:survey_stone":
                        a.put(x, y, z, "minecraft:smooth_stone")
    return a


def adl_sightpost() -> Landmark:
    """An exposed triangulation deck with a tall but accessible lookout."""
    a = east_satellite("adl_sightpost", "gems:survey_stone", "gems:compass_mosaic")
    a.fill(4, 5, 7, 16, 5, 14, "minecraft:smooth_stone")
    for x in (6, 14):
        for z in (5, 15):
            a.pillar(x, z, 5, 19, "minecraft:stripped_spruce_log")
            a.put(x, 21, z, "minecraft:end_rod")
    for y in (9, 15):
        a.ring(5, 4, 15, 16, y, "minecraft:weathered_cut_copper")
    a.fill(7, 15, 6, 13, 15, 14, "minecraft:smooth_stone")
    a.ring(6, 5, 14, 15, 17, "minecraft:weathered_copper_grate")
    a.ring(6, 5, 14, 15, 20, "minecraft:weathered_cut_copper")
    a.fill(7, 20, 6, 13, 20, 14, "minecraft:light_blue_stained_glass")
    a.fill(9, 20, 9, 11, 20, 11, "minecraft:orange_terracotta")
    a.pillar(10, 4, 5, 15, "minecraft:stripped_spruce_log")
    a.fill(10, 6, 5, 10, 14, 5, "minecraft:ladder[facing=south]")
    a.pillar(10, 10, 5, 8, "gems:survey_stone")
    a.put(10, 9, 10, "minecraft:lodestone")
    a.put(10, 16, 10, "gems:adl_east_sight")
    a.put(10, 21, 10, "minecraft:lightning_rod")
    a.chest(15, 5, 13, "west", "adl_survey_house")
    a.sign(10, 5, 17, ("EAST SIGHTPOST", "The second line", "crosses beyond", "the map house."))
    return finish_east_satellite(a)


def adl_chart_shelter() -> Landmark:
    """A low protected map room with a fold of roof over its entry porch."""
    a = west_satellite("adl_chart_shelter", "gems:survey_stone", "gems:compass_mosaic")
    for y in range(5, 11):
        a.ring(3, 3, 17, 17, y, "gems:survey_stone")
    a.fill(4, 5, 4, 16, 9, 16, "minecraft:air")
    for x in (3, 17):
        for z in (3, 17):
            a.pillar(x, z, 5, 12, "minecraft:stripped_spruce_log")
    for z in (6, 13):
        a.fill(3, 7, z, 3, 8, z + 2, "minecraft:light_blue_stained_glass")
        a.fill(17, 7, z, 17, 8, z + 2, "minecraft:light_blue_stained_glass")
    for x in range(2, 19):
        height = 11 + (8 - abs(x - 10)) // 3
        a.fill(x, height, 2, x, height, 18, "minecraft:dark_oak_planks")
    a.fill(8, 14, 5, 12, 14, 14, "minecraft:light_blue_stained_glass")
    a.fill(7, 5, 7, 13, 5, 12, "minecraft:dark_oak_planks")
    a.put(10, 6, 9, "gems:adl_west_chart")
    for x in (5, 15):
        a.put(x, 6, 5, "minecraft:lantern")
    a.fill(17, 5, 8, 20, 9, 12, "minecraft:air")
    a.chest(5, 5, 14, "east", "adl_survey_house")
    a.sign(10, 5, 15, ("CHART SHELTER", "The first line", "starts at the", "map house."))
    return finish_west_satellite(a)


def othman_library() -> Landmark:
    """A public reading hall with a real gallery and two contrasting annexes."""
    a = Landmark("othman_library")
    # A chamfered three-step civic shore meets low coast without the library's
    # four-block limestone plinth ending in a sheer rectangle. Support remains
    # full-height beneath the east and west annex connectors.
    for x in range(47):
        for z in range(47):
            dx, dz = min(x, 46 - x), min(z, 46 - z)
            edge = min(dx, dz)
            if edge > 2 or dx + dz < 3:
                continue
            lane = 21 <= z <= 25 and (x <= 2 or x >= 44)
            top = 3 if lane else edge + 1
            base = ("minecraft:stone_bricks" if edge <= 1 else
                    "gems:archive_limestone")
            for y in range(top):
                a.put(x, y, z, base)
            surface = ("gems:archive_limestone" if lane or edge == 2 else
                       "minecraft:polished_diorite" if edge == 1 else
                       "minecraft:stone_bricks")
            a.put(x, top, z, surface)
    a.foundation("gems:archive_inlay", "gems:archive_limestone", 26)
    a.fill(20, 4, 32, 26, 4, 46, "gems:archive_inlay")
    # The former south ramp filled every row to the same height. These broad
    # one-block rises climb from shoreline Y1 to the Y4 portico floor.
    for z in range(43, 47):
        top = 47 - z
        for x in range(20, 27):
            a.fill(x, 0, z, x, top - 1, z, "gems:archive_limestone")
            a.put(x, top, z, "minecraft:polished_diorite")
            a.fill(x, top + 1, z, x, 5, z, "minecraft:air")
    for y in range(5, 17):
        a.ring(9, 8, 37, 33, y, "gems:archive_limestone")
    a.fill(10, 5, 9, 36, 16, 32, "minecraft:air")
    # Long desks and a central aisle make the room a place to use, not just a shell.
    for z in (14, 22, 29):
        for x0 in (14, 27):
            a.fill(x0, 5, z, x0 + 5, 5, z + 1, "minecraft:dark_oak_planks")
            a.put(x0 + 2, 6, z, "minecraft:lantern")
    for x in (11, 35):
        for z in (12, 20, 28):
            a.pillar(x, z, 5, 17, "minecraft:polished_diorite")
    for x in (13, 29):
        a.fill(x, 7, 33, x + 4, 12, 33, "minecraft:light_blue_stained_glass")
        a.fill(x + 1, 13, 33, x + 3, 13, 33, "minecraft:light_blue_stained_glass")
    for z in (12, 25):
        for x in (9, 37):
            a.fill(x, 7, z, x, 12, z + 3, "minecraft:light_blue_stained_glass")
    a.fill(20, 5, 32, 26, 9, 33, "minecraft:air")
    a.fill(20, 4, 33, 26, 4, 42, "minecraft:polished_diorite")
    for x in (9, 37):
        for z in (9, 17, 25, 32):
            a.fill(x - 1, 5, z, x + 1, 15, z, "minecraft:polished_diorite")
    # The accessible upper gallery frames a light well over the reading desks.
    a.ring(11, 10, 35, 30, 11, "minecraft:dark_oak_planks")
    a.ring(12, 11, 34, 29, 11, "minecraft:dark_oak_planks")
    a.ring(13, 12, 33, 28, 12, "minecraft:dark_oak_fence")
    a.fill(12, 5, 14, 12, 11, 14, "minecraft:ladder[facing=east]")
    for x in (11, 35):
        for z in (10, 18, 26):
            a.fill(x, 5, z, x, 10, z + 4, "minecraft:bookshelf")
    # Coffered interior ribs sit below a steep reading-hall roof, so the
    # building has a recognisable roofline at a distance as well as a ceiling.
    a.fill(9, 17, 8, 37, 17, 33, "gems:archive_limestone")
    for x in range(10, 37, 5):
        a.fill(x, 18, 9, x, 18, 32, "minecraft:polished_diorite")
    for z in range(10, 33, 5):
        a.fill(10, 18, z, 36, 18, z, "minecraft:polished_diorite")
    a.fill(17, 17, 13, 29, 17, 28, "minecraft:light_blue_stained_glass")
    for x in range(8, 39):
        roof_y = 18 + (15 - abs(x - 23)) // 3
        a.fill(x, roof_y, 7, x, roof_y, 34, "minecraft:dark_prismarine")
        if x in (8, 38):
            a.fill(x, roof_y, 7, x, roof_y, 34, "minecraft:polished_diorite")
    for x in range(21, 26):
        roof_y = 18 + (15 - abs(x - 23)) // 3
        a.fill(x, roof_y, 13, x, roof_y, 28, "minecraft:light_blue_stained_glass")
    # A projecting portico makes the south door visible from the approach.
    a.fill(18, 4, 33, 28, 4, 39, "minecraft:polished_diorite")
    for x in (19, 27):
        for z in (35, 38):
            a.pillar(x, z, 5, 14, "minecraft:polished_diorite")
    a.fill(18, 15, 33, 28, 15, 39, "minecraft:dark_prismarine")
    a.fill(20, 16, 35, 26, 16, 39, "gems:archive_limestone")
    a.fill(22, 17, 37, 24, 17, 39, "minecraft:dark_prismarine")
    a.fill(37, 5, 21, 39, 9, 25, "minecraft:air")
    a.fill(7, 5, 21, 9, 9, 25, "minecraft:air")
    a.put(23, 5, 12, "gems:othman_publish_desk")
    a.put(23, 5, 16, "gems:othman_keep_desk")
    a.put(23, 5, 20, "gems:othman_comparison")
    a.friend(21, 5, 24, "othman")
    a.chest(34, 5, 29)
    a.chest(12, 5, 29)
    a.sign(23, 5, 39, ("PUBLIC LIBRARY", "Two annexes hold", "different leads.", "Read both."))
    a.sign(26, 5, 13, ("SHARE CATALOG", "Tell everyone", "about Branch's", "upper route."))
    a.sign(26, 5, 17, ("SHARE STUDY", "Tell everyone", "about Lastlight's", "upper room."))
    a.quote_sign(18, 5, 37, ("imagine if we", "played", "multiplayer", "minecraft"))
    attach_east(a, "othman_catalog_rotunda", "gems:archive_inlay")
    attach_west(a, "othman_study_wing", "gems:archive_inlay")
    return a


def othman_catalog_rotunda() -> Landmark:
    """A small public catalog with a different, octagonal silhouette."""
    a = east_satellite("othman_catalog_rotunda", "gems:archive_limestone",
                       "gems:archive_inlay")
    for y in range(5, 12):
        a.ring(3, 3, 17, 17, y, "gems:archive_limestone")
    a.fill(4, 5, 4, 16, 10, 16, "minecraft:air")
    for x0, z0 in ((3, 3), (14, 3), (3, 14), (14, 14)):
        a.fill(x0, 5, z0, x0 + 3, 12, z0 + 3, "minecraft:air")
    for x, z in ((6, 3), (14, 3), (3, 6), (17, 6),
                 (3, 14), (17, 14), (6, 17), (14, 17)):
        a.pillar(x, z, 5, 13, "minecraft:polished_diorite")
    for x in (5, 15):
        a.fill(x, 7, 8, x, 10, 12, "minecraft:light_blue_stained_glass")
    for z in (5, 15):
        a.fill(8, 7, z, 12, 10, z, "minecraft:light_blue_stained_glass")
    for y, radius, diagonal in ((13, 8, 12), (14, 6, 9), (15, 4, 6)):
        for x in range(10 - radius, 11 + radius):
            for z in range(10 - radius, 11 + radius):
                if abs(x - 10) + abs(z - 10) <= diagonal:
                    a.put(x, y, z, "minecraft:dark_prismarine")
    a.fill(8, 16, 8, 12, 16, 12, "minecraft:light_blue_stained_glass")
    a.fill(9, 17, 9, 11, 17, 11, "minecraft:dark_prismarine")
    for x, z in ((7, 7), (13, 7), (7, 13), (13, 13)):
        a.fill(x, 5, z, x, 8, z + 1, "minecraft:bookshelf")
    a.put(10, 5, 10, "gems:othman_public_catalog")
    a.chest(14, 5, 13, "west", "othman_library")
    a.sign(10, 5, 17, ("CATALOG ROTUNDA", "One lead is", "for everyone.", "Read it first."))
    a.quote_sign(6, 5, 13, ("odd name, i", "remember it", "being much", "simpler"))
    return finish_east_satellite(a)


def othman_study_wing() -> Landmark:
    """A low cloister of private study niches around a quiet skylight."""
    a = west_satellite("othman_study_wing", "gems:archive_limestone",
                       "gems:archive_inlay")
    for y in range(5, 11):
        a.ring(3, 3, 17, 17, y, "gems:archive_limestone")
    a.fill(4, 5, 4, 16, 9, 16, "minecraft:air")
    for x in (5, 15):
        for z in (5, 15):
            a.pillar(x, z, 5, 12, "minecraft:polished_diorite")
    for x in (3, 17):
        for z in (6, 13):
            a.fill(x, 7, z, x, 9, z + 2, "minecraft:light_blue_stained_glass")
    a.ring(2, 2, 18, 18, 11, "minecraft:dark_prismarine")
    a.fill(4, 12, 4, 16, 12, 16, "minecraft:dark_prismarine")
    a.fill(7, 12, 7, 13, 12, 13, "minecraft:light_blue_stained_glass")
    for x in (5, 15):
        a.fill(x, 5, 6, x, 8, 9, "minecraft:bookshelf")
    a.fill(7, 5, 13, 13, 5, 14, "minecraft:dark_oak_planks")
    a.put(10, 6, 13, "gems:othman_private_note")
    a.chest(5, 5, 14, "east", "othman_library")
    a.sign(10, 5, 16, ("STUDY WING", "The other lead", "can stay private.", "Both count."))
    a.quote_sign(7, 5, 15, ("BRO I REMEMBER", "THAT GAME", "", ""))
    return finish_west_satellite(a)


def othman_library_bridge() -> Landmark:
    """New themed-only root; legacy saved room templates keep their IDs."""
    a = othman_library()
    a.name = "othman_library_bridge"
    a.jigsaw(0, 5, 23, "west_up", "gems:othman_study_wing_entry",
             "gems:othman_study_bridge_wing")
    return a


def othman_study_bridge_wing() -> Landmark:
    a = othman_study_wing()
    a.name = "othman_study_bridge_wing"
    # The Archive's west room now includes a narrow public viaduct. Keeping it
    # in the same rigid jigsaw piece preserves the room's connector and avoids
    # a second independent terrain match across the river.
    bridge = 26
    if a.entities:
        raise ValueError("Othman: shift bridge entities before widening the wing")
    a.blocks = {(x + bridge, y, z): state
                for (x, y, z), state in a.blocks.items()}
    a.chests = [(x + bridge, y, z) for x, y, z in a.chests]
    a.signs = [(x + bridge, y, z) for x, y, z in a.signs]
    a.size = (47, 22, 21)
    a.fill(25, 5, 8, 30, 9, 12, "minecraft:air")
    for x in range(26):
        deck = 1 if x < 4 else 2 if x < 8 else 3 if x < 12 else 4
        a.fill(x, deck + 1, 8, x, 9, 12, "minecraft:air")
        a.fill(x, deck, 8, x, deck, 12, "minecraft:polished_diorite")
        a.put(x, deck, 10, "gems:archive_inlay")
        for z in (7, 13):
            a.put(x, deck + 1, z, "gems:archive_limestone")
    # A paired colonnade marks three shallow arches. A worldgen hook continues
    # only these six stone feet through genuine air/water to firm riverbed.
    for x in (4, 12, 20):
        for z in (7, 13):
            a.fill(x, 0, z, x, 4, z, "minecraft:stone_bricks")
            a.put(x, 5, z, "minecraft:polished_diorite")
    for x in range(4, 21):
        for z in (7, 13):
            if x not in (4, 12, 20):
                a.put(x, 2, z, "minecraft:polished_diorite")
    return a


def othman_library_arrival() -> Landmark:
    """Versioned new-start root so saved Archive bridge pieces keep their bytes."""
    a = othman_library_bridge()
    a.name = "othman_library_arrival"
    a.jigsaw(0, 5, 23, "west_up", "gems:othman_study_arrival_wing_entry",
             "gems:othman_study_arrival_wing")
    return a


def othman_library_bank_entry_v2() -> Landmark:
    """Keep the accepted hall geometry while gating a new-chunk bank clearance."""
    a = othman_library_arrival()
    a.name = "othman_library_bank_entry_v2"
    return a


def othman_library_dry_arrival_v3() -> Landmark:
    """New starts carry a hidden local marker for radius-one terrain work."""
    a = othman_library_bank_entry_v2()
    a.name = "othman_library_dry_arrival_v3"
    a.jigsaw(0, 5, 23, "west_up", "gems:othman_study_dry_arrival_wing_v3_entry",
             "gems:othman_study_dry_arrival_wing_v3")
    # The observer is below the unchanged perimeter floor. Its facing encodes
    # orientation within this chunk, even if the adjacent inlay is unplaced.
    a.put(3, 2, 2, "minecraft:observer[facing=south]")
    a.put(3, 2, 3, "minecraft:amethyst_block")
    return a


def othman_library_north_arrival_v4() -> Landmark:
    """New starts mark an optional north portal on the existing dry plinth."""
    a = othman_library_dry_arrival_v3()
    a.name = "othman_library_north_arrival_v4"
    a.jigsaw(0, 5, 23, "west_up", "gems:othman_study_north_arrival_wing_v4_entry",
             "gems:othman_study_north_arrival_wing_v4")
    # Move the marker nearer the center of the north route, so the full
    # landing-to-hall check fits in this chunk's one-neighbor read radius.
    # The old v3 marker remains only in the saved v3 template.
    a.put(3, 2, 2, "gems:archive_limestone")
    a.put(3, 2, 3, "gems:archive_limestone")
    a.put(5, 2, 15, "minecraft:observer[facing=south]")
    a.put(5, 3, 15, "minecraft:polished_diorite")
    a.put(5, 2, 16, "minecraft:amethyst_block")
    return a


def othman_library_colonnade_v5() -> Landmark:
    """Give the existing public entry a tall, readable Archive facade.

    Every added solid is above the already-authored portico floor or on its
    existing columns and roof. The natural woodland and bank stay untouched.
    Saved v4 starts retain their template and marker.
    """
    a = othman_library_north_arrival_v4()
    a.name = "othman_library_colonnade_v5"
    # A second tier turns the small porch canopy into the library's frontispiece.
    # The stair, entry sign, gameplay stations and buried route marker are kept.
    for x in (19, 27):
        for z in (35, 38):
            a.pillar(x, z, 5, 21, "minecraft:polished_diorite")
            for y in (9, 14, 20):
                a.put(x, y, z, "gems:archive_inlay")
    for y, half_width in ((16, 5), (17, 5), (18, 4), (19, 4),
                          (20, 3), (21, 3), (22, 2), (23, 1)):
        a.fill(23 - half_width, y, 38, 23 + half_width, y, 39,
               "gems:archive_limestone")
        if y in (17, 18, 19, 20) and half_width >= 3:
            a.fill(22, y, 39, 24, y, 39,
                   "minecraft:light_blue_stained_glass")
        a.fill(23 - half_width, y + 1, 37,
               23 + half_width, y + 1, 37, "minecraft:dark_prismarine")
    a.put(23, 24, 38, "minecraft:sea_lantern")
    for x in (20, 26):
        a.fill(x, 16, 34, x, 19, 37, "gems:archive_limestone")
        a.fill(x, 18, 35, x, 19, 36, "minecraft:light_blue_stained_glass")
    # Slim side ribs and brass-colored lamps repeat the reading-hall rhythm.
    for z in (34, 38):
        for x in (18, 28):
            a.fill(x, 15, z, x, 19, z, "minecraft:polished_diorite")
            a.put(x, 20, z, "minecraft:sea_lantern")
    a.fill(20, 14, 35, 26, 14, 38, "minecraft:dark_oak_planks")
    return a


def othman_library_colonnade_v6() -> Landmark:
    """Version the public hall before applying the new-chunk south vista."""
    a = othman_library_colonnade_v5()
    a.name = "othman_library_colonnade_v6"
    return a


def othman_study_arrival_wing() -> Landmark:
    """A narrow bank landing leads into the existing viaduct design."""
    a = othman_study_bridge_wing()
    a.name = "othman_study_arrival_wing"
    if a.entities:
        raise ValueError("Othman: shift bridge entities before widening the wing")
    a.blocks = {(x + 8, y, z): state for (x, y, z), state in a.blocks.items()}
    a.chests = [(x + 8, y, z) for x, y, z in a.chests]
    a.signs = [(x + 8, y, z) for x, y, z in a.signs]
    a.size = (55, 22, 21)
    a.jigsaw(54, 5, 10, "east_up", "gems:othman_study_arrival_wing_entry")
    for x in range(8):
        deck = 0 if x < 4 else 1
        a.fill(x, deck + 1, 8, x, 9, 12, "minecraft:air")
        a.fill(x, deck, 8, x, deck, 12, "minecraft:polished_diorite")
        a.put(x, deck, 10, "gems:archive_inlay")
        for z in (7, 13):
            a.put(x, deck + 1, z, "gems:archive_limestone")
    for z in (7, 13):
        a.pillar(2, z, 1, 4, "minecraft:polished_diorite")
    a.fill(2, 5, 7, 2, 5, 13, "minecraft:dark_prismarine")
    return a


def othman_study_dry_arrival_wing_v3() -> Landmark:
    """The accepted wing with an identifiable local bank threshold."""
    a = othman_study_arrival_wing()
    a.name = "othman_study_dry_arrival_wing_v3"
    a.jigsaw(54, 5, 10, "east_up", "gems:othman_study_dry_arrival_wing_v3_entry")
    # The flush observer encodes the inward axis in its own block state, so
    # this piece does not depend on a neighboring chunk's placement order.
    a.put(2, 0, 10, "minecraft:observer[facing=east]")
    a.put(3, 0, 10, "minecraft:sea_lantern")
    return a


def othman_study_north_arrival_wing_v4() -> Landmark:
    """Keep C's bank arrival while identifying a new dry-bank-only marker."""
    a = othman_study_dry_arrival_wing_v3()
    a.name = "othman_study_north_arrival_wing_v4"
    a.jigsaw(54, 5, 10, "east_up", "gems:othman_study_north_arrival_wing_v4_entry")
    # This flush inlay distinguishes v4 from the old sea-lantern marker.
    a.put(3, 0, 10, "gems:archive_inlay")
    return a


def othman_library_east_gallery_v2() -> Landmark:
    """New themed starts keep the accepted west arrival and gain an east entry."""
    a = othman_library_arrival()
    a.name = "othman_library_east_gallery_v2"
    a.jigsaw(46, 5, 23, "east_up", "gems:othman_catalog_hillside_v2_entry",
             "gems:othman_catalog_hillside_v2")
    return a


def othman_catalog_hillside_v2() -> Landmark:
    """A hilltop entry meets the public catalog through an upper reading stair.

    Only the narrow authored route writes outside the rotunda. The earlier
    rotunda, bridge and arrival templates are retained byte-for-byte for saves.
    """
    previous = othman_catalog_rotunda()
    a = Landmark("othman_catalog_hillside_v2", (32, 41, 33))
    for (x, y, z), (state, nbt) in previous.blocks.items():
        a.put(x, y, z + 12, state, nbt)
    a.chests = [(x, y, z + 12) for x, y, z in previous.chests]
    a.signs = [(x, y, z + 12) for x, y, z in previous.signs]
    # The root door remains exactly opposite its jigsaw connector. The
    # original catalog and the main hall still meet at ground-floor level.
    a.jigsaw(0, 5, 22, "west_up", "gems:othman_catalog_hillside_v2_entry")

    # At the measured seed-B hill, z=23 is the Y101 high approach and z=3
    # is the Y86 saddle. A three-wide path descends fifteen blocks over twenty
    # horizontal blocks. The limited air envelope gives headroom without
    # replacing a rectangle of natural hillside or its trees.
    for z in range(3, 24):
        deck = 15 + (z - 3) * 15 // 20
        a.fill(26, deck + 1, z, 28, deck + 3, z, "minecraft:air")
        a.fill(26, deck, z, 28, deck, z, "minecraft:polished_diorite")
        a.put(27, deck, z, "gems:archive_inlay")
        if z % 4 == 3:
            for x in (25, 29):
                a.put(x, deck + 1, z, "gems:archive_limestone")
    # Slim under-deck feet support the short spans over the seed-B saddle.
    # The selector keeps this piece out of lowland starts, where long exposed
    # columns would otherwise be unavoidable.
    for z in (7, 13, 19):
        deck = 15 + (z - 3) * 15 // 20
        for x in (26, 28):
            a.fill(x, 0, z, x, deck - 1, z, "gems:archive_limestone")

    # A shallow gallery crosses the north saddle above the natural overhang,
    # then reaches the rotunda's raised entrance. It does not force a tunnel
    # westward through the hill at the failed Y86 turn.
    for x in range(14, 28):
        deck = 20 - (x - 14) * 5 // 13
        a.fill(x, deck + 1, 2, x, deck + 3, 4, "minecraft:air")
        a.fill(x, deck, 2, x, deck, 4, "minecraft:polished_diorite")
        a.put(x, deck, 3, "gems:archive_inlay")
    # The crossing also occupies z=4 at x=27; restore its route marker after
    # the north gallery lays its three-wide deck over the descending path.
    a.put(27, 15, 4, "gems:archive_inlay")
    for z in range(4, 16):
        a.fill(13, 21, z, 15, 23, z, "minecraft:air")
        a.fill(13, 20, z, 15, 20, z, "minecraft:polished_diorite")
        a.put(14, 20, z, "gems:archive_inlay")

    # The catalog's new glazed stair tower takes visitors down from Y91 to
    # the original Y75 floor. Two flights fit inside its east side; the
    # central public catalog block, original loot and main-hall door remain.
    a.fill(12, 6, 15, 16, 24, 27, "minecraft:air")
    for z in range(15, 27):
        deck = 20 - min(10, max(0, z - 16))
        a.fill(13, deck, z, 15, deck, z, "minecraft:polished_diorite")
        a.put(14, deck, z, "gems:archive_inlay")
        a.fill(13, deck + 1, z, 15, deck + 3, z, "minecraft:air")
        for x in (12, 16):
            a.fill(x, deck + 1, z, x, deck + 2, z,
                   "minecraft:light_blue_stained_glass")
    a.fill(9, 6, 27, 16, 24, 27, "minecraft:air")
    a.fill(13, 10, 27, 15, 10, 27, "minecraft:polished_diorite")
    for x in range(9, 15):
        deck = 10 - (14 - x)
        a.put(x, deck, 27, "gems:archive_inlay")
        a.fill(x, deck + 1, 27, x, deck + 3, 27, "minecraft:air")
    for z in (15, 27):
        for x in (12, 16):
            a.pillar(x, z, 18, 25, "minecraft:polished_diorite")
    a.fill(12, 25, 15, 16, 25, 27, "minecraft:dark_prismarine")
    a.fill(13, 23, 15, 15, 24, 15, "minecraft:light_blue_stained_glass")
    # The glazed index lantern is attached to the rotunda, not set as a pier
    # between the visitor and building. Its high profile marks the entry from
    # the elevated woodland while the lower roof stays in the hill's shadow.
    for y in range(26, 39):
        a.ring(13, 19, 15, 23, y,
               "minecraft:light_blue_stained_glass" if y % 3 else
               "gems:archive_limestone")
    a.fill(12, 39, 18, 16, 39, 24, "minecraft:dark_prismarine")
    a.put(14, 40, 21, "minecraft:sea_lantern")

    # A small open hilltop reading shelter gives the high approach an actual
    # destination. It is on the path, not a tall foreground obstruction.
    a.fill(26, 31, 23, 28, 35, 27, "minecraft:air")
    a.fill(29, 31, 24, 29, 34, 26, "minecraft:air")
    a.fill(25, 30, 23, 29, 30, 27, "gems:archive_inlay")
    for x in (25, 29):
        for z in (23, 27):
            a.pillar(x, z, 31, 35, "minecraft:polished_diorite")
    a.fill(25, 36, 23, 29, 36, 27, "minecraft:dark_prismarine")
    a.fill(26, 36, 24, 28, 36, 26, "minecraft:light_blue_stained_glass")
    a.sign(25, 31, 25, ("EAST CATALOG", "Follow inlay", "to the public", "shelves below."))
    return a


def yehia_story_lodge() -> Landmark:
    """Three turf gables shelter a story room; its side paths carry the answer."""
    a = Landmark("yehia_story_lodge")
    a.foundation("gems:storybook_path", "gems:storybook_turf", 23)
    a.fill(4, 4, 4, 42, 4, 42, "minecraft:moss_block")
    a.fill(9, 4, 9, 37, 4, 33, "gems:storybook_path")
    a.fill(4, 4, 21, 42, 4, 25, "gems:storybook_path")
    a.fill(20, 4, 32, 26, 4, 46, "gems:storybook_path")
    # A long, low communal room is nested into planted earth rather than
    # standing as another rectangular stone hall.
    for y in range(5, 13):
        a.ring(8, 8, 38, 32, y, "gems:storybook_turf")
    a.fill(9, 5, 9, 37, 11, 31, "minecraft:air")
    for z in (9, 20, 32):
        for x in (9, 19, 27, 37):
            a.pillar(x, z, 5, 13, "minecraft:stripped_birch_log")
    # Three projecting gables make the arrival face read like a small village.
    for x0, x1 in ((10, 18), (19, 27), (28, 36)):
        a.fill(x0, 5, 32, x1, 11, 37, "minecraft:calcite")
        a.fill(x0 + 1, 5, 33, x1 - 1, 10, 37, "minecraft:air")
        a.fill(x0 + 1, 5, 37, x1 - 1, 11, 37, "minecraft:calcite")
        for x in (x0, x1):
            a.pillar(x, 37, 5, 13, "minecraft:stripped_birch_log")
        middle = (x0 + x1) // 2
        for x in range(x0 - 1, x1 + 2):
            roof_y = 15 + max(0, 4 - abs(x - middle))
            a.fill(x, roof_y, 32, x, roof_y, 40, "minecraft:moss_block")
            if x0 <= x <= x1:
                a.fill(x, 12, 38, x, roof_y - 1, 38, "minecraft:calcite")
        a.fill(x0, 12, 37, x1, 12, 37, "minecraft:stripped_birch_log[axis=x]")
    for x in range(7, 40):
        roof_y = 13 + max(0, 8 - abs(x - 23)) // 2
        a.fill(x, roof_y, 7, x, roof_y, 33, "minecraft:moss_block")
        if x % 4 == 0:
            for z in (12, 24):
                a.put(x, roof_y + 1, z, "minecraft:short_grass")
        if x in (10, 16, 30, 36):
            a.fill(x, roof_y + 1, 10, x, roof_y + 1, 29,
                   "minecraft:azalea_leaves[persistent=true]")
    # One brick flue and a moss-framed ridge break the continuous roof mass.
    a.fill(30, 15, 14, 32, 23, 16, "minecraft:bricks")
    a.fill(30, 24, 14, 32, 24, 16, "minecraft:brick_slab[type=bottom]")
    a.put(31, 24, 15, "minecraft:campfire[lit=true]")
    # Small round windows and a broad central door create a readable face.
    for x in (14, 32):
        a.fill(x - 1, 7, 37, x + 1, 9, 37, "minecraft:yellow_stained_glass")
        a.put(x, 6, 37, "gems:storybook_path")
        a.put(x, 10, 37, "minecraft:yellow_stained_glass")
    a.fill(21, 5, 31, 25, 9, 39, "minecraft:air")
    a.fill(21, 4, 32, 25, 4, 42, "gems:storybook_path")
    for x in (20, 26):
        a.pillar(x, 39, 5, 10, "minecraft:stripped_birch_log")
    a.fill(20, 11, 37, 26, 11, 39, "minecraft:moss_block")
    a.fill(22, 12, 38, 24, 12, 39, "minecraft:moss_block")
    for x in (8, 38):
        for z in (13, 25):
            a.fill(x, 7, z, x, 9, z + 2, "minecraft:yellow_stained_glass")
    for x, z in ((6, 8), (40, 8), (6, 37), (40, 37)):
        a.pillar(x, z, 5, 9, "minecraft:stripped_birch_log")
        a.fill(x - 2, 10, z - 2, x + 2, 11, z + 2,
               "minecraft:azalea_leaves[persistent=true]")
        a.put(x, 12, z, "minecraft:azalea_leaves[persistent=true]")
    for x in (11, 14, 32, 35):
        for z in (38, 41):
            a.put(x, 5, z, "minecraft:flowering_azalea")
    # The story table is the return point after examining both side places.
    a.fill(18, 5, 15, 28, 5, 17, "minecraft:stripped_oak_wood")
    a.put(23, 6, 16, "gems:yehia_story_table")
    a.fill(12, 5, 23, 17, 5, 24, "minecraft:dark_oak_planks")
    a.fill(29, 5, 23, 34, 5, 24, "minecraft:dark_oak_planks")
    a.friend(20, 5, 25, "yehia")
    a.chest(12, 5, 28)
    a.chest(34, 5, 28)
    a.sign(23, 5, 41, ("STORY LODGE", "Two tellings", "share a garden.", "Only one grew."))
    a.quote_sign(13, 5, 35, ("this tells a", "story", "", ""))
    attach_east(a, "yehia_truth_garden", "gems:storybook_path")
    attach_west(a, "yehia_prop_house", "gems:storybook_path")
    return a


def yehia_story_lodge_arrival_v2() -> Landmark:
    """A visible five-wide walk descends the lodge plinth to natural ground."""
    a = yehia_story_lodge()
    a.name = "yehia_story_lodge_arrival_v2"
    # The legacy foundation ends in a sheer turf wall at z=46. Cut its center
    # back to a broad stepped approach while leaving the story-room threshold,
    # side connections, signs, chests, and all clue stations in place.
    for z, tread_y in ((43, 4), (44, 3), (45, 2), (46, 1)):
        for x in range(21, 26):
            a.fill(x, tread_y + 1, z, x, 7, z, "minecraft:air")
            a.put(x, tread_y, z, "gems:storybook_path")
        for x in (20, 26):
            a.put(x, tread_y + 1, z, "minecraft:birch_fence")
    # Two high contrast entrance posts remain visible above flowers and grass
    # from the natural southern approach.
    for x in (19, 27):
        a.pillar(x, 42, 4, 8, "minecraft:stripped_birch_log")
        a.put(x, 9, 42, "minecraft:lantern")
    return a


def yehia_truth_garden() -> Landmark:
    """A roofless spiral garden makes the real trail visible from above."""
    a = east_satellite("yehia_truth_garden", "gems:storybook_turf",
                       "minecraft:moss_block")
    for x in range(2, 19):
        for z in range(2, 19):
            radius = (x - 10) ** 2 + (z - 10) ** 2
            if 54 <= radius <= 76 or 18 <= radius <= 30:
                a.put(x, 4, z, "gems:storybook_path")
            elif radius < 76 and (x + z) % 7 == 0:
                a.put(x, 5, z, "minecraft:short_grass")
    for x, z in ((5, 5), (15, 5), (5, 15), (15, 15)):
        a.pillar(x, z, 5, 7, "minecraft:stripped_birch_log")
        a.fill(x - 2, 8, z - 2, x + 2, 8, z + 2,
               "minecraft:azalea_leaves[persistent=true]")
    # The stations are legible landmarks rather than three unmarked floor tiles.
    a.pillar(4, 10, 5, 8, "minecraft:mushroom_stem")
    a.fill(2, 9, 8, 6, 9, 12, "minecraft:red_mushroom_block")
    a.put(10, 5, 4, "minecraft:stripped_birch_log")
    a.pillar(10, 4, 6, 10, "minecraft:stripped_birch_log")
    a.put(10, 11, 4, "minecraft:lantern[hanging=true]")
    a.pillar(16, 12, 5, 9, "minecraft:oak_log")
    a.fill(14, 10, 10, 18, 12, 14,
           "minecraft:flowering_azalea_leaves[persistent=true]")
    a.ring(7, 7, 13, 13, 5, "minecraft:birch_planks")
    for x in (7, 13):
        for z in (7, 13):
            a.pillar(x, z, 6, 10, "minecraft:stripped_birch_log")
    a.ring(6, 6, 14, 14, 11, "minecraft:moss_block")
    a.fill(8, 12, 8, 12, 12, 12, "minecraft:moss_block")
    a.put(10, 5, 10, "gems:yehia_truth_clue")
    for x, z, name in ((6, 10, "mushroom"), (10, 6, "lantern"),
                       (14, 10, "tree")):
        a.put(x, 5, z, f"gems:yehia_{name}_mark")
    a.chest(14, 5, 14, "west", "yehia_story_lodge")
    a.sign(10, 5, 16, ("GARDEN TALE", "A cap kept rain", "from a small flame.", "Roots took light."))
    a.quote_sign(6, 5, 14, ("team gnome is", "winning tho", "", ""))
    return finish_east_satellite(a)


def yehia_prop_house() -> Landmark:
    """A small working prop shop, with a false door set against the wall."""
    a = west_satellite("yehia_prop_house", "gems:storybook_turf",
                       "gems:storybook_path")
    for y in range(5, 11):
        a.ring(3, 3, 17, 17, y, "gems:storybook_turf")
    a.fill(4, 5, 4, 16, 9, 16, "minecraft:air")
    for x in (3, 17):
        for z in (3, 17):
            a.pillar(x, z, 5, 12, "minecraft:stripped_birch_log")
    for x in range(2, 19):
        roof_y = 11 + max(0, 8 - abs(x - 10)) // 2
        a.fill(x, roof_y, 2, x, roof_y, 18, "minecraft:moss_block")
        if x in (4, 15):
            a.fill(x, roof_y + 1, 4, x, roof_y + 1, 16,
                   "minecraft:azalea_leaves[persistent=true]")
    for z in (5, 13):
        a.fill(3, 7, z, 3, 8, z + 2, "minecraft:yellow_stained_glass")
    a.fill(7, 5, 6, 13, 5, 8, "minecraft:dark_oak_planks")
    a.fill(9, 6, 7, 11, 8, 7, "minecraft:red_wool")
    a.put(10, 6, 6, "minecraft:oak_door[facing=south,half=lower,hinge=left,open=false]")
    a.put(10, 7, 6, "minecraft:oak_door[facing=south,half=upper,hinge=left,open=false]")
    a.put(10, 5, 12, "minecraft:dark_oak_planks")
    a.put(10, 6, 12, "gems:yehia_prop_clue")
    # A theatrical fake door and red awning face the garden path.
    a.fill(7, 5, 17, 13, 10, 17, "minecraft:calcite")
    a.fill(9, 5, 17, 11, 8, 17, "minecraft:red_wool")
    a.put(10, 7, 17, "minecraft:yellow_glazed_terracotta")
    for x in (7, 13):
        a.pillar(x, 19, 5, 9, "minecraft:stripped_birch_log")
    a.fill(6, 10, 17, 14, 10, 20, "minecraft:red_wool")
    a.fill(8, 11, 18, 12, 11, 20, "minecraft:moss_block")
    a.chest(5, 5, 14, "east", "yehia_story_lodge")
    a.sign(10, 5, 15, ("PROP HOUSE", "A painted door", "is a good joke.", "It is not a path."))
    a.quote_sign(7, 5, 15, ("im not building", "up to a joke", "", ""))
    return finish_west_satellite(a)


def yehia_story_lodge_v3() -> Landmark:
    return ally_architecture.yehia_story_lodge_v3(sys.modules[__name__])


def yehia_truth_garden_v3() -> Landmark:
    return ally_architecture.yehia_truth_garden_v3(sys.modules[__name__])


def yehia_prop_house_v3() -> Landmark:
    return ally_architecture.yehia_prop_house_v3(sys.modules[__name__])


def omar_lost_quarter() -> Landmark:
    """Two inhabited earthen towers shadow a narrow missing-object lane."""
    a = Landmark("omar_lost_quarter")
    # Only this coastal quarter gets a three-block sandstone shore outside its
    # clay plinth. Keep full-height support under all three satellite lanes;
    # the normal foundation and its south entrance are written afterward.
    for x in range(47):
        for z in range(47):
            edge = min(x, 46 - x, z, 46 - z)
            if edge > 2:
                continue
            lane = ((21 <= z <= 25 and (x <= 2 or x >= 44))
                    or (21 <= x <= 25 and z <= 2))
            top = 3 if lane else edge + 1
            base = "minecraft:sandstone" if edge <= 1 else "gems:lost_clay"
            for y in range(top):
                a.put(x, y, z, base)
            surface = ("gems:lost_clay" if lane or edge == 2 else
                       "minecraft:cut_sandstone" if edge == 1 else
                       "minecraft:sand")
            a.put(x, top, z, surface)
    a.foundation("gems:lost_tile", "gems:lost_clay", 27)
    a.fill(4, 4, 4, 42, 4, 42, "minecraft:coarse_dirt")
    a.fill(20, 4, 6, 26, 4, 46, "gems:lost_tile")
    # Four inhabited towers leave a cross-lane to both side buildings. Uneven
    # heights, paired recessed windows and open parapets avoid one long slab.
    for x0, x1, z0, z1, top in ((8, 19, 8, 19, 22),
                                (8, 19, 27, 38, 26),
                                (27, 38, 8, 19, 25),
                                (27, 38, 27, 38, 21)):
        for y in range(5, top):
            a.ring(x0, z0, x1, z1, y, "gems:lost_clay")
        a.fill(x0 + 1, 5, z0 + 1, x1 - 1, top - 1, z1 - 1, "minecraft:air")
        for y in (10, 17):
            a.ring(x0 - 1, z0 - 1, x1 + 1, z1 + 1, y, "minecraft:cut_sandstone")
        for floor_y in (12, 18):
            if floor_y < top - 2:
                a.fill(x0 + 1, floor_y, z0 + 1, x1 - 1, floor_y, z1 - 1,
                       "minecraft:dark_oak_planks")
        ladder_x = x0 + 1 if x0 == 8 else x1 - 1
        ladder_z = z0 + 3
        facing = "east" if x0 == 8 else "west"
        for y in range(5, top - 1):
            a.put(ladder_x, y, ladder_z, f"minecraft:ladder[facing={facing}]")
        if x0 == 27:
            a.fill(x0, 5, z0 + 5, x0, 8, z0 + 7, "minecraft:air")
        else:
            a.fill(x1, 5, z0 + 5, x1, 8, z0 + 7, "minecraft:air")
        for z in (z0 + 2, z1 - 3):
            for x in (x0, x1):
                a.fill(x, 13, z, x, 15, z + 1, "minecraft:light_blue_stained_glass")
                a.fill(x - 1, 16, z, x + 1, 16, z + 1, "minecraft:dark_oak_planks")
        a.fill(x0 + 1, top, z0 + 1, x1 - 1, top, z1 - 1, "minecraft:terracotta")
        a.ring(x0, z0, x1, z1, top, "gems:lost_tile")
        for x in range(x0, x1 + 1, 3):
            a.put(x, top + 1, z0, "gems:lost_tile")
            a.put(x, top + 1, z1, "gems:lost_tile")
        for z in range(z0, z1 + 1, 3):
            a.put(x0, top + 1, z, "gems:lost_tile")
            a.put(x1, top + 1, z, "gems:lost_tile")
    # A covered bridge joins the front pair above the narrow public lane.
    a.fill(19, 17, 14, 27, 17, 18, "minecraft:dark_oak_planks")
    for x in range(20, 27):
        for z in (14, 18):
            a.put(x, 18, z, "minecraft:dark_oak_fence")
    a.fill(18, 21, 13, 28, 21, 19, "minecraft:cyan_terracotta")
    a.fill(19, 18, 15, 19, 20, 17, "minecraft:air")
    a.fill(27, 18, 15, 27, 20, 17, "minecraft:air")
    # Three removable-looking records sit along one wall, leaving a clear lane.
    for z, block in ((13, "omar_compass_choice"), (18, "omar_clock_choice"),
                     (27, "omar_book_choice")):
        a.put(26, 5, z, "minecraft:dark_oak_planks")
        a.put(26, 6, z, f"gems:{block}")
    a.friend(23, 5, 30, "omar")
    a.chest(12, 5, 29)
    a.chest(34, 5, 29)
    for x in (18, 28):
        a.pillar(x, 38, 5, 12, "minecraft:cut_sandstone")
        a.put(x, 13, 38, "gems:lost_tile")
    a.fill(18, 14, 36, 28, 14, 40, "minecraft:cyan_terracotta")
    a.fill(20, 4, 35, 26, 4, 46, "gems:lost_tile")
    a.sign(23, 5, 42, ("LOST QUARTER", "The shelf has", "three labels.", "Two came home."))
    a.sign(23, 5, 10, ("LOST OBJECTS", "One empty place", "has no return", "in either record."))
    a.quote_sign(22, 5, 38, ("auto put me in", "the losing team", "please", ""))
    a.quote_sign(22, 5, 37, ("no matter what", "i choose ill be",
                             "on the winning", "team"))
    attach_east(a, "omar_exchange_court", "gems:lost_tile")
    attach_west(a, "omar_memory_loft", "gems:lost_tile")
    # A third, quieter route leads behind the quarter to its reclaimed well.
    a.fill(21, 5, 0, 25, 9, 8, "minecraft:air")
    a.fill(21, 4, 0, 25, 4, 8, "gems:lost_tile")
    a.jigsaw(23, 5, 0, "north_up", "gems:omar_return_oasis_entry",
             "gems:omar_return_oasis")
    return a


def omar_exchange_court() -> Landmark:
    """A shaded open market court, not another closed tower."""
    a = east_satellite("omar_exchange_court", "gems:lost_clay",
                       "minecraft:coarse_dirt")
    a.fill(0, 4, 8, 20, 4, 12, "gems:lost_tile")
    a.fill(8, 4, 2, 12, 4, 18, "gems:lost_tile")
    for index, (x0, z0, x1, z1) in enumerate(((3, 3, 8, 7), (12, 3, 17, 7),
                                               (3, 13, 8, 17), (12, 13, 17, 17))):
        for x in (x0, x1):
            for z in (z0, z1):
                a.pillar(x, z, 5, 9 + index % 3, "minecraft:cut_sandstone")
        canopy_y = 10 + index % 3
        a.fill(x0 - 1, canopy_y, z0 - 1, x1 + 1, canopy_y, z1 + 1,
               "minecraft:cyan_terracotta" if index % 2 == 0 else "minecraft:cut_sandstone")
        a.ring(x0 - 1, z0 - 1, x1 + 1, z1 + 1, canopy_y + 1, "gems:lost_tile")
        a.fill(x0 + 1, 5, z0 + 1, x1 - 1, 5, z1 - 1,
               "minecraft:dark_oak_planks")
    for z in (7, 13):
        a.pillar(1, z, 5, 12, "minecraft:cut_sandstone")
    a.fill(1, 13, 7, 1, 13, 13, "gems:lost_tile")
    a.ring(7, 7, 13, 13, 5, "gems:lost_clay")
    a.fill(8, 5, 8, 12, 5, 12, "minecraft:water[level=0]")
    a.put(10, 5, 10, "minecraft:sea_lantern")
    a.put(10, 5, 5, "minecraft:dark_oak_planks")
    a.put(10, 6, 5, "gems:omar_clock_receipt")
    a.chest(15, 5, 15, "west", "omar_lost_quarter")
    a.sign(10, 5, 16, ("EXCHANGE COURT", "The clock was", "collected before", "dusk."))
    a.quote_sign(6, 5, 13, ("the game never", "existed", "", ""))
    return finish_east_satellite(a)


def omar_memory_loft() -> Landmark:
    """A narrow storage house with an actually reachable upper ledger."""
    a = west_satellite("omar_memory_loft", "gems:lost_clay", "gems:lost_tile")
    for y in range(5, 17):
        a.ring(3, 3, 17, 17, y, "gems:lost_clay")
    a.fill(4, 5, 4, 16, 16, 16, "minecraft:air")
    a.fill(4, 12, 4, 16, 12, 16, "minecraft:dark_oak_planks")
    a.fill(4, 17, 4, 16, 17, 16, "minecraft:cyan_terracotta")
    a.ring(2, 2, 18, 18, 18, "gems:lost_tile")
    # A narrower rooftop record room breaks the square roofline.
    for y in range(18, 21):
        a.ring(6, 5, 14, 13, y, "gems:lost_clay")
    a.fill(7, 18, 6, 13, 20, 12, "minecraft:air")
    a.fill(7, 21, 6, 13, 21, 12, "minecraft:cyan_terracotta")
    a.ring(6, 5, 14, 13, 21, "gems:lost_tile")
    a.fill(6, 13, 7, 6, 19, 7, "gems:lost_clay")
    a.fill(7, 13, 7, 7, 19, 7, "minecraft:ladder[facing=east]")
    for x in (8, 12):
        a.fill(x, 19, 5, x, 20, 5, "minecraft:light_blue_stained_glass")
    for x in (3, 17):
        for z in (3, 17):
            a.pillar(x, z, 5, 19, "minecraft:cut_sandstone")
    for z in (6, 13):
        a.fill(3, 8, z, 3, 10, z + 2, "minecraft:light_blue_stained_glass")
        a.fill(17, 13, z, 17, 15, z + 2, "minecraft:light_blue_stained_glass")
    a.fill(4, 6, 10, 4, 12, 10, "gems:lost_clay")
    a.fill(5, 6, 10, 5, 12, 10, "minecraft:ladder[facing=east]")
    a.put(10, 12, 10, "minecraft:dark_oak_planks")
    a.put(10, 13, 10, "gems:omar_book_record")
    a.chest(13, 5, 14, "west", "omar_lost_quarter")
    a.sign(11, 13, 14, ("MEMORY LOFT", "The book came", "back at dawn.", "Read the ledger."))
    a.quote_sign(7, 5, 15, ("the level of", "betrayal i am", "feeling right", "now"))
    return finish_west_satellite(a)


def omar_return_oasis() -> Landmark:
    """A credited desert oasis rebuilt as Omar's tiled public well and route."""
    a = Landmark("omar_return_oasis", (21, 22, 21))
    # Step the exposed foundation down toward the natural coast. The center
    # lane stays level with Omar's root, while a western stair climbs from the
    # shallows to the well. A flat four-block wall read as a square pier here.
    for x in range(21):
        for z in range(21):
            edge = min(x, 20 - x, z, 20 - z)
            top = min(4, edge + 1)
            if 5 <= x <= 17 and z >= 16:
                top = 4  # joined public deck, sign, loot and jigsaw route
            if z in (16, 17, 18) and x <= 4:
                top = max(top, min(4, x + 1))  # walkable west-to-east rise
            base = "minecraft:sandstone" if edge <= 1 else "gems:lost_clay"
            for y in range(top):
                a.put(x, y, z, base)
            surface = ("minecraft:sand" if edge == 0 else
                       "minecraft:cut_sandstone" if edge == 1 else
                       "gems:lost_clay" if edge == 2 else
                       "minecraft:coarse_dirt")
            a.put(x, top, z, surface)
            a.fill(x, top + 1, z, x, 21, z, "minecraft:air")

    source = read_schematic(ROOT / "tools/assets/simple_structures_desert/oasis_desert_st_3.nbt")["value"]
    if source["size"] != [13, 12, 13] or source["DataVersion"] != 3465:
        raise ValueError("Omar oasis source changed; review the adaptation before regenerating")
    for entry in source["blocks"]:
        state = source["palette"][entry["state"]]
        name = state["Name"]
        if name in {"minecraft:air", "minecraft:cobweb", "minecraft:poppy",
                    "minecraft:tall_grass"}:
            continue
        x, y, z = entry["pos"]
        if name == "minecraft:sandstone":
            name = "gems:lost_clay"
        elif name == "minecraft:grass_block":
            name = "minecraft:coarse_dirt"
        elif name == "minecraft:suspicious_sand":
            # The 1.19-era brushable block stores legacy item NBT. The Lost
            # Wadi loot chest below uses the current 26.3 loot table instead.
            name = "gems:lost_tile"
        else:
            props = state.get("Properties", {})
            if props:
                name += "[" + ",".join(f"{key}={value}"
                                       for key, value in sorted(props.items())) + "]"
        a.put(x + 4, y + 4, z + 3, name)

    # A public ceramic walk and gate tie the imported basin to the four clay
    # towers. These are new to the source, as are the clue, lighting and loot.
    a.ring(7, 8, 13, 14, 4, "gems:lost_tile")
    a.fill(9, 4, 14, 11, 4, 20, "gems:lost_tile")
    for x in (5, 15):
        a.pillar(x, 17, 5, 9, "gems:lost_clay")
        a.put(x, 10, 17, "gems:lost_tile")
        a.put(x, 11, 17, "minecraft:lantern")
    a.fill(6, 10, 17, 14, 10, 17, "minecraft:cyan_terracotta")
    for x in (7, 10, 13):
        a.put(x, 11, 17, "gems:lost_tile")
    a.pillar(17, 9, 5, 7, "gems:lost_clay")
    a.put(17, 8, 9, "gems:lost_tile")
    a.chest(16, 5, 18, "west", "omar_lost_quarter")
    a.sign(10, 5, 18, ("RETURN OASIS", "The water kept", "no lost object.", "Read both records."))
    a.jigsaw(10, 5, 20, "south_up", "gems:omar_return_oasis_entry")
    return a


def soryal_repair_yard() -> Landmark:
    """An old brick works stitched to a brighter salvage hall and public court."""
    a = Landmark("soryal_repair_yard")
    a.foundation("gems:patchwork_brick", "minecraft:coarse_dirt", 27)
    a.fill(5, 4, 6, 41, 4, 42, "minecraft:coarse_dirt")
    a.fill(20, 4, 31, 27, 4, 45, "gems:patchwork_mosaic")
    a.fill(0, 4, 21, 10, 4, 25, "gems:patchwork_mosaic")
    a.fill(37, 4, 21, 46, 4, 25, "gems:patchwork_mosaic")
    # The older masonry wing is left exposed where the new glazed wing joins it.
    for y in range(5, 16):
        a.ring(8, 8, 23, 32, y, "gems:patchwork_brick")
    a.fill(9, 5, 9, 22, 15, 31, "minecraft:air")
    for y in range(5, 18):
        a.ring(24, 12, 38, 32, y, "minecraft:weathered_cut_copper")
    a.fill(25, 5, 13, 37, 17, 31, "minecraft:air")
    a.fill(23, 5, 20, 25, 9, 24, "minecraft:air")
    a.fill(16, 5, 32, 20, 9, 32, "minecraft:air")
    a.fill(29, 5, 32, 33, 9, 32, "minecraft:air")
    a.fill(10, 8, 32, 14, 12, 32, "minecraft:iron_bars")
    a.fill(25, 8, 32, 27, 14, 32, "minecraft:light_blue_stained_glass")
    a.fill(34, 8, 32, 36, 14, 32, "minecraft:light_blue_stained_glass")
    for x in (8, 15, 23):
        a.pillar(x, 33, 5, 14, "minecraft:cut_sandstone")
    for x in (24, 28, 37):
        a.pillar(x, 33, 5, 17, "minecraft:weathered_cut_copper")
    a.fill(15, 12, 33, 21, 12, 36, "minecraft:deepslate_tiles")
    a.fill(28, 13, 33, 35, 13, 36, "minecraft:light_blue_stained_glass")
    a.fill(8, 11, 33, 23, 11, 33, "gems:patchwork_brick")
    for z in (13, 19, 27):
        a.fill(8, 8, z, 8, 11, z + 2, "minecraft:iron_bars")
        a.fill(38, 8, z, 38, 14, z + 2, "minecraft:light_blue_stained_glass")
    for x in (12, 18):
        a.fill(x, 9, 8, x + 2, 12, 8, "minecraft:iron_bars")
    for x in (27, 33):
        a.fill(x, 9, 12, x + 2, 15, 12, "minecraft:light_blue_stained_glass")
    # A stepped sawtooth roof exposes repaired glass strips between brick bays.
    for z in range(8, 33):
        roof_y = 16 + (z // 5) % 2
        a.fill(7, roof_y, z, 24, roof_y, z,
               "minecraft:light_blue_stained_glass" if z % 5 == 0 else "minecraft:deepslate_tiles")
    for x in range(24, 40):
        roof_y = 19 + max(0, (8 - abs(x - 31)) // 4)
        a.fill(x, roof_y, 11, x, roof_y, 33,
               "minecraft:light_blue_stained_glass" if x % 4 == 0 else "minecraft:weathered_cut_copper")
    a.fill(10, 16, 10, 13, 25, 13, "gems:patchwork_brick")
    a.fill(11, 26, 11, 12, 26, 12, "minecraft:deepslate_tiles")
    # A surviving wall section and an exposed new repair frame occupy opposite
    # corners of the court without hiding the three floor pieces.
    a.fill(8, 5, 37, 14, 10, 37, "gems:patchwork_brick")
    a.fill(10, 7, 37, 11, 9, 37, "minecraft:air")
    a.fill(8, 11, 36, 14, 11, 38, "minecraft:deepslate_tiles")
    for x in (32, 39):
        for z in (36, 41):
            a.pillar(x, z, 5, 13, "minecraft:weathered_cut_copper")
    a.fill(31, 14, 35, 40, 14, 42, "minecraft:light_blue_stained_glass")
    a.ring(31, 35, 40, 42, 15, "minecraft:weathered_cut_copper")
    # The gantry leaves the whole ground route readable, with a second layer
    # of walkable space visible through the glass roof.
    a.fill(9, 13, 22, 37, 13, 24, "minecraft:dark_oak_planks")
    for x in range(9, 38):
        a.put(x, 14, 22, "minecraft:iron_bars")
    a.fill(10, 5, 23, 10, 13, 23, "gems:patchwork_brick")
    a.fill(11, 5, 23, 11, 13, 23, "minecraft:ladder[facing=east]")
    a.fill(7, 5, 21, 10, 9, 25, "minecraft:air")
    a.fill(37, 5, 21, 40, 9, 25, "minecraft:air")
    a.friend(23, 5, 38, "soryal")
    for x, block in ((20, "soryal_copper_mark"), (23, "soryal_glass_mark"),
                     (26, "soryal_brick_mark")):
        a.put(x, 5, 36, f"gems:{block}")
    a.put(18, 5, 38, "gems:soryal_yard_fitting")
    a.chest(13, 5, 28)
    a.chest(33, 5, 27)
    a.sign(23, 5, 43, ("REPAIR YARD", "Three seams in", "one old break.", "Read their wear."))
    a.quote_sign(23, 5, 40, ("lossy", "compression", "algorithms ftw", "ts so goated fr"))
    attach_east(a, "soryal_scrap_bridge", "gems:patchwork_mosaic")
    attach_west(a, "soryal_repair_loft", "gems:patchwork_mosaic")
    return a


def soryal_scrap_bridge() -> Landmark:
    """An outdoor salvage span exposing the first half of the repair order."""
    a = east_satellite("soryal_scrap_bridge", "gems:patchwork_brick",
                       "minecraft:coarse_dirt")
    a.fill(0, 4, 8, 20, 4, 12, "gems:patchwork_mosaic")
    for x in (4, 16):
        for z in (5, 15):
            a.pillar(x, z, 5, 14, "minecraft:weathered_cut_copper")
    a.fill(4, 15, 5, 16, 15, 15, "minecraft:weathered_cut_copper")
    for z in (6, 10, 14):
        a.fill(5, 16, z, 15, 16, z, "minecraft:light_blue_stained_glass")
    a.pillar(17, 5, 5, 20, "minecraft:weathered_cut_copper")
    a.fill(9, 20, 5, 18, 20, 5, "minecraft:weathered_cut_copper")
    a.fill(11, 14, 5, 11, 19, 5, "minecraft:chain")
    a.fill(10, 12, 4, 12, 13, 6, "gems:patchwork_brick")
    a.fill(4, 9, 8, 16, 9, 12, "minecraft:dark_oak_planks")
    for x in range(4, 17):
        for z in (8, 12):
            a.put(x, 10, z, "minecraft:iron_bars")
    for x, z in ((6, 4), (14, 16), (16, 4)):
        a.fill(x, 5, z, x + 2, 7, z + 2, "gems:patchwork_brick")
        a.put(x + 1, 8, z + 1, "minecraft:weathered_cut_copper")
    a.put(10, 5, 16, "gems:soryal_scrap_clue")
    a.put(10, 5, 14, "gems:soryal_bridge_fitting")
    a.chest(15, 5, 14, "west", "soryal_repair_yard")
    a.sign(10, 5, 18, ("SCRAP BRIDGE", "Rivet dust below", "bright shards.", "The joint held."))
    a.quote_sign(6, 5, 16, ("congrats on", "escaping", "", ""))
    return finish_east_satellite(a)


def soryal_repair_loft() -> Landmark:
    """An old brick archive with a new glass upper room and second fragment."""
    a = west_satellite("soryal_repair_loft", "gems:patchwork_brick",
                       "gems:patchwork_mosaic")
    for y in range(5, 13):
        a.ring(3, 3, 17, 17, y, "gems:patchwork_brick")
    a.fill(4, 5, 4, 16, 12, 16, "minecraft:air")
    a.fill(4, 12, 4, 16, 12, 16, "minecraft:dark_oak_planks")
    for y in range(13, 18):
        a.ring(5, 5, 15, 15, y, "minecraft:weathered_cut_copper")
    a.fill(6, 13, 6, 14, 17, 14, "minecraft:air")
    for x in (5, 15):
        a.fill(x, 14, 7, x, 16, 13, "minecraft:light_blue_stained_glass")
    for z in (5, 15):
        a.fill(7, 14, z, 13, 16, z, "minecraft:light_blue_stained_glass")
    a.fill(4, 18, 4, 16, 18, 16, "minecraft:deepslate_tiles")
    a.fill(6, 19, 6, 14, 19, 14, "minecraft:weathered_cut_copper")
    a.fill(8, 20, 8, 12, 20, 12, "minecraft:light_blue_stained_glass")
    a.fill(6, 12, 2, 14, 12, 4, "minecraft:weathered_cut_copper")
    for x in (6, 14):
        a.pillar(x, 2, 5, 11, "minecraft:cut_sandstone")
    a.fill(7, 13, 3, 13, 16, 3, "minecraft:light_blue_stained_glass")
    a.pillar(3, 3, 13, 20, "gems:patchwork_brick")
    a.fill(4, 6, 6, 4, 13, 6, "gems:patchwork_brick")
    a.fill(5, 6, 6, 5, 13, 6, "minecraft:ladder[facing=east]")
    a.put(10, 13, 10, "gems:soryal_loft_clue")
    a.put(12, 13, 10, "gems:soryal_loft_fitting")
    a.chest(12, 5, 14, "west", "soryal_repair_yard")
    a.sign(11, 13, 13, ("REPAIR LOFT", "Bright shards", "under an older", "brick patch."))
    a.quote_sign(7, 5, 15, ("totally normal", "friendship", "", ""))
    return finish_west_satellite(a)


def ali_splitstone_cloister() -> Landmark:
    """A roofless map cloister held between four fractured stone needles."""
    a = Landmark("ali_splitstone_cloister")
    a.foundation("gems:splitstone", "minecraft:stone", 27)
    a.fill(5, 4, 5, 41, 4, 42, "gems:waymark_tile")
    a.fill(16, 4, 16, 30, 4, 30, "minecraft:moss_block")
    a.fill(21, 4, 31, 25, 4, 46, "gems:waymark_tile")
    for x0, x1, z0, z1, top in ((8, 14, 8, 15, 25),
                                (32, 38, 8, 15, 22),
                                (8, 14, 32, 39, 20),
                                (32, 38, 32, 39, 24)):
        a.fill(x0, 5, z0, x1, top, z1, "gems:splitstone")
        for y in (10, 16):
            if y < top:
                a.fill(x0, y, z0, x1, y, z1, "minecraft:polished_andesite")
        a.fill(x0 + 1, top + 1, z0 + 1, x1 - 1, top + 1, z1 - 1,
               "minecraft:light_blue_stained_glass")
        a.ring(x0, z0, x1, z1, top + 1, "gems:waymark_tile")
        # Cut a long, inset map slit into each needle. Broken side ribs and
        # unequal crown teeth keep the four masses from reading as plain boxes.
        a.fill(x0 + 2, 7, z1, x1 - 2, top - 3, z1, "minecraft:polished_blackstone")
        a.fill(x0 + 2, 9, z1, x1 - 2, 12, z1,
               "minecraft:light_blue_stained_glass")
        a.fill(x0 + 2, top - 8, z1, x1 - 2, top - 6, z1,
               "minecraft:light_blue_stained_glass")
        a.pillar(x0 - 1, z1, 5, min(top - 4, 15), "minecraft:polished_andesite")
        a.pillar(x1 + 1, z1, 5, min(top - 7, 12), "minecraft:polished_andesite")
        a.fill(x0, top + 2, z0, x0 + 1, top + 3, z0 + 1, "gems:splitstone")
        a.fill(x1 - 1, top + 2, z1 - 1, x1, top + 2, z1, "gems:splitstone")
    # Colonnades frame an open center. Four broad gates connect the court to
    # the atlas approach and both side maps without a blind puzzle corridor.
    for x in (16, 23, 30):
        for z in (16, 30):
            a.pillar(x, z, 5, 12, "minecraft:polished_andesite")
    for z in (20, 26):
        for x in (16, 30):
            a.pillar(x, z, 5, 12, "minecraft:polished_andesite")
    a.ring(15, 15, 31, 31, 13, "gems:splitstone")
    a.ring(16, 16, 30, 30, 14, "gems:waymark_tile")
    for x in (16, 23, 30):
        a.fill(x - 1, 12, 16, x + 1, 12, 17, "minecraft:polished_andesite")
        a.fill(x - 1, 12, 29, x + 1, 12, 30, "minecraft:polished_andesite")
    for z in (20, 26):
        a.fill(16, 12, z - 1, 17, 12, z + 1, "minecraft:polished_andesite")
        a.fill(29, 12, z - 1, 30, 12, z + 1, "minecraft:polished_andesite")
    for x in (18, 28):
        for z in (18, 28):
            a.put(x, 5, z, "minecraft:lantern")
    # The raised path between north needles is an optional overlook rather
    # than the only route to the clues or the friend.
    a.fill(14, 18, 10, 32, 18, 13, "minecraft:dark_oak_planks")
    for x in range(15, 32):
        a.put(x, 19, 10, "minecraft:iron_bars")
    a.fill(13, 5, 12, 13, 18, 12, "gems:splitstone")
    a.fill(14, 5, 12, 14, 18, 12, "minecraft:ladder[facing=east]")
    # Worn stone leads to the uninked arch, so the surviving route can be
    # deduced from the court itself without clicking both records.
    a.put(23, 5, 33, "gems:ali_map_table")
    for z in (34, 35, 36):
        a.put(28, 4, z, "minecraft:mossy_stone_bricks")
    for x, block in ((18, "ali_north_door"), (23, "ali_east_door"),
                     (28, "ali_west_door")):
        a.put(x, 5, 37, f"gems:{block}")
    a.friend(23, 5, 41, "ali")
    a.chest(18, 5, 23)
    a.chest(28, 5, 23)
    a.sign(23, 5, 44, ("SPLITSTONE", "The atlas is", "newer than the", "wear in stone."))
    a.sign(23, 5, 35, ("THREE DOORS", "Three worn arches", "Two lines of ink", "One blank edge"))
    attach_east(a, "ali_high_map_room", "gems:waymark_tile")
    attach_west(a, "ali_cut_passage", "gems:waymark_tile")
    return a


def ali_splitstone_cloister_facade_v2() -> Landmark:
    """Give the four map needles broad cut-stone faces and a quiet court floor."""
    a = ali_splitstone_cloister()
    a.name = "ali_splitstone_cloister_facade_v2"
    # Keep the directional mosaic as crossing paths; the former full-field
    # repetition made the court and all four towers read as one flat pattern.
    for x in range(5, 42):
        for z in range(5, 43):
            if 21 <= x <= 25 or 21 <= z <= 25:
                continue
            if a.blocks.get((x, 4, z), (None,))[0] == "gems:waymark_tile":
                a.put(x, 4, z, "minecraft:polished_andesite")
    for x0, x1, z0, z1, top in ((8, 14, 8, 15, 25),
                                (32, 38, 8, 15, 22),
                                (8, 14, 32, 39, 20),
                                (32, 38, 32, 39, 24)):
        # Broad, unequal side cuts change the silhouette from a distance.
        for x, start_z, end_z, cap in ((x0, z0 + 1, z0 + 3, top - 4),
                                       (x1, z1 - 3, z1 - 1, top - 7)):
            for z in range(start_z, end_z + 1):
                for y in range(6, cap + 1):
                    if a.blocks.get((x, y, z), (None,))[0] == "gems:splitstone":
                        a.put(x, y, z, "minecraft:polished_andesite")
        # A dark two-block high base anchors each tower. The existing inset
        # glass slit, broken crowns, and compass-facing markers remain.
        for x in range(x0 + 1, x1):
            for z in (z0, z1):
                for y in (5, 6):
                    if a.blocks.get((x, y, z), (None,))[0] == "gems:splitstone":
                        a.put(x, y, z, "minecraft:polished_blackstone")
    return a


def ali_high_map_room() -> Landmark:
    """A high, glazed survey room reached through the eastern stone needle."""
    a = east_satellite("ali_high_map_room", "gems:splitstone",
                       "gems:waymark_tile")
    for y in range(5, 17):
        a.ring(5, 4, 17, 16, y, "gems:splitstone")
    a.fill(6, 5, 5, 16, 16, 15, "minecraft:air")
    a.fill(6, 12, 5, 16, 12, 15, "minecraft:dark_oak_planks")
    a.fill(6, 6, 7, 6, 13, 7, "minecraft:ladder[facing=east]")
    for z in (6, 12):
        a.fill(5, 13, z, 5, 15, z + 2, "minecraft:light_blue_stained_glass")
        a.fill(17, 8, z, 17, 10, z + 2, "minecraft:light_blue_stained_glass")
    a.fill(4, 17, 3, 18, 17, 17, "minecraft:polished_andesite")
    a.fill(6, 18, 5, 16, 18, 15, "minecraft:light_blue_stained_glass")
    a.ring(4, 3, 18, 17, 19, "gems:waymark_tile")
    for x in (4, 18):
        for z in (3, 17):
            a.pillar(x, z, 5, 20, "minecraft:polished_andesite")
    # A public lower arch, two unequal window bays and a raised glass map
    # lantern make the room legible from the cloister approach.
    a.fill(9, 5, 16, 13, 9, 17, "minecraft:air")
    a.fill(8, 5, 17, 8, 11, 17, "minecraft:polished_andesite")
    a.fill(14, 5, 17, 14, 11, 17, "minecraft:polished_andesite")
    a.fill(8, 11, 17, 14, 11, 17, "gems:waymark_tile")
    a.fill(6, 8, 17, 7, 10, 17, "minecraft:light_blue_stained_glass")
    a.fill(15, 8, 17, 17, 10, 17, "minecraft:light_blue_stained_glass")
    a.ring(8, 6, 14, 12, 19, "gems:splitstone")
    a.fill(9, 19, 7, 13, 20, 11, "minecraft:light_blue_stained_glass")
    a.ring(8, 6, 14, 12, 21, "gems:waymark_tile")
    a.fill(5, 5, 8, 7, 9, 12, "minecraft:air")
    a.put(11, 13, 10, "gems:ali_high_map")
    a.put(13, 12, 10, "gems:ali_release_high")
    a.chest(14, 5, 14, "west", "ali_splitstone_cloister")
    a.sign(12, 13, 13, ("HIGH MAP", "North line clear", "East line clear", "Paper ends here"))
    return finish_east_satellite(a)


def ali_cut_passage() -> Landmark:
    """A low rock-cut route preserving the rubbing of an uncharted west arch."""
    a = west_satellite("ali_cut_passage", "gems:splitstone",
                       "gems:waymark_tile")
    a.fill(2, 5, 3, 7, 15, 17, "gems:splitstone")
    a.fill(13, 5, 3, 18, 19, 17, "gems:splitstone")
    a.fill(8, 4, 3, 12, 4, 18, "gems:waymark_tile")
    for z in (5, 9, 14):
        a.fill(7, 11, z, 13, 11, z, "minecraft:polished_andesite")
        a.fill(8, 12, z, 12, 12, z, "gems:waymark_tile")
    a.fill(13, 5, 8, 20, 9, 12, "minecraft:air")
    a.fill(7, 5, 13, 8, 9, 15, "minecraft:air")
    # Staggered cliff crowns and a cross-cut above the walk make this a
    # carved passage rather than two square blocks beside a corridor.
    a.fill(2, 16, 4, 4, 18, 9, "gems:splitstone")
    a.fill(4, 16, 6, 7, 16, 11, "minecraft:moss_block")
    a.fill(14, 20, 4, 17, 21, 8, "gems:splitstone")
    a.fill(16, 20, 8, 18, 20, 13, "minecraft:moss_block")
    a.fill(3, 8, 17, 5, 13, 17, "minecraft:air")
    a.fill(15, 10, 17, 17, 15, 17, "minecraft:air")
    a.fill(8, 13, 8, 12, 13, 10, "gems:waymark_tile")
    for x in (8, 12):
        a.fill(x, 14, 8, x, 14, 10, "minecraft:iron_bars")
    a.put(4, 15, 11, "minecraft:lantern")
    a.put(16, 19, 12, "minecraft:lantern")
    a.put(10, 5, 14, "gems:ali_gate_rubbing")
    a.put(10, 4, 12, "gems:ali_release_low")
    a.chest(5, 5, 13, "east", "ali_splitstone_cloister")
    a.sign(10, 5, 17, ("GATE RUBBING", "Boot grit caught", "in a third arch.", "No ink survives."))
    return finish_west_satellite(a)


ROOT_BUILDERS = (framefall, lastlight, rehearsal, branch_vault, redline, hushwire,
                 unwritten, second_chance_town, inkbound_workshop,
                 khaled_floodgate, nour_signal_harbor, fam_forum, adl_survey_house,
                 othman_library, yehia_story_lodge, omar_lost_quarter,
                 soryal_repair_yard, ali_splitstone_cloister)
SATELLITE_BUILDERS = (framefall_atelier, framefall_garden, framefall_observatory,
                      lastlight_market, lastlight_signal_box,
                      rehearsal_prop_foundry, rehearsal_costume_house,
                      branch_escape_tower, branch_map_room,
                      redline_infirmary, redline_watchpost,
                      hushwire_dishfield, hushwire_relay_bunker,
                      unwritten_stewards_annex, unwritten_mask_archive,
                      town_casino, town_arcade, town_lounge, town_market,
                      town_guesthouse, town_hall,
                      inkbound_print_house, inkbound_garden,
                      khaled_shieldworks, khaled_shelter_walk,
                      nour_boathouse, nour_foghouse,
                      fam_story_hall, fam_open_school,
                      adl_sightpost, adl_chart_shelter,
                      othman_catalog_rotunda, othman_study_wing,
                      yehia_truth_garden, yehia_prop_house,
                      omar_exchange_court, omar_memory_loft, omar_return_oasis,
                      soryal_scrap_bridge, soryal_repair_loft,
                      ali_high_map_room, ali_cut_passage)
BUILDERS = ROOT_BUILDERS + SATELLITE_BUILDERS
OTHMAN_BRIDGE_BUILDERS = (othman_library_bridge, othman_study_bridge_wing,
                          othman_library_arrival, othman_study_arrival_wing,
                          othman_library_bank_entry_v2,
                          othman_library_dry_arrival_v3,
                          othman_study_dry_arrival_wing_v3,
                          othman_library_north_arrival_v4,
                          othman_library_colonnade_v5,
                          othman_library_colonnade_v6,
                          othman_study_north_arrival_wing_v4,
                          othman_library_east_gallery_v2,
                          othman_catalog_hillside_v2)
FRAMEFALL_WALK_BUILDERS = (framefall_gallery_walk, framefall_atelier_walk,
                          framefall_promenade, framefall_gallery_facade,
                          framefall_atelier_facade, framefall_promenade_facade,
                          framefall_gallery_transition_v3,
                          framefall_atelier_transition_v3,
                          framefall_promenade_transition_v3)
INKBOUND_VERSION_BUILDERS = (inkbound_workshop_quarter_v2,
                             inkbound_print_house_v2, inkbound_garden_v2,
                             inkbound_arrival_lane_v2)
TOWNHALL_VERSION_BUILDERS = (town_hall_alcoves_v2, town_hall_gallery_v3,
                             town_hall_civic_v4, town_hall_civic_v5,
                             town_hall_arrival_v6)
TOWN_VERSION_BUILDERS = (second_chance_town_deco_v2,
                         second_chance_town_civic_v3,
                         second_chance_town_arrival_v4)
TOWN_ARRIVAL_BUILDERS = (town_market_arrival_v2,
                         town_guesthouse_arrival_v2)
RAGAB_VERSION_BUILDERS = (town_casino_deco_v2, town_arcade_deco_v2,
                          town_lounge_deco_v2)
LASTLIGHT_VERSION_BUILDERS = (lastlight_exchange_v2, lastlight_market_v2,
                              lastlight_signal_box_v2)
REHEARSAL_VERSION_BUILDERS = (rehearsal_works_v2,
                              rehearsal_prop_foundry_v2,
                              rehearsal_costume_house_v2)
BRANCH_VERSION_BUILDERS = (branch_vault_v2, branch_escape_tower_v2,
                           branch_map_room_v2)
REDLINE_VERSION_BUILDERS = (redline_house_v2, redline_house_arrival_v3,
                            redline_infirmary_v2,
                            redline_watchpost_v2)
UNWRITTEN_VERSION_BUILDERS = (unwritten_court_v2,
                              unwritten_stewards_annex_v2,
                              unwritten_mask_archive_v2)
VISUAL_ACCESS_VERSION_BUILDERS = (adl_survey_house_facade_v2,
                                  yehia_story_lodge_arrival_v2,
                                  ali_splitstone_cloister_facade_v2)
YEHIA_VERSION_BUILDERS = (yehia_story_lodge_v3,
                          yehia_truth_garden_v3,
                          yehia_prop_house_v3)

SATELLITES_BY_ROOT = {
    "framefall_gallery": ("framefall_atelier", "framefall_garden", "framefall_observatory"),
    "lastlight_exchange": ("lastlight_market", "lastlight_signal_box"),
    "rehearsal_works": ("rehearsal_prop_foundry", "rehearsal_costume_house"),
    "branch_vault": ("branch_escape_tower", "branch_map_room"),
    "redline_house": ("redline_infirmary", "redline_watchpost"),
    "hushwire": ("hushwire_dishfield", "hushwire_relay_bunker"),
    "unwritten_court": ("unwritten_stewards_annex", "unwritten_mask_archive"),
    "second_chance_town": ("town_casino", "town_arcade", "town_lounge",
                           "town_market", "town_guesthouse", "town_hall"),
    "inkbound_workshop": ("inkbound_print_house", "inkbound_garden"),
    "khaled_floodgate": ("khaled_shieldworks", "khaled_shelter_walk"),
    "nour_signal_harbor": ("nour_boathouse", "nour_foghouse"),
    "fam_forum": ("fam_story_hall", "fam_open_school"),
    "adl_survey_house": ("adl_sightpost", "adl_chart_shelter"),
    "othman_library": ("othman_catalog_rotunda", "othman_study_wing"),
    "yehia_story_lodge": ("yehia_truth_garden", "yehia_prop_house"),
    "omar_lost_quarter": ("omar_exchange_court", "omar_memory_loft",
                          "omar_return_oasis"),
    "soryal_repair_yard": ("soryal_scrap_bridge", "soryal_repair_loft"),
    "ali_splitstone_cloister": ("ali_high_map_room", "ali_cut_passage"),
}

WORLD = {
    "framefall_gallery": {
        "biomes": ["gems:framefall_ravine"],
        "loot": [("minecraft:amethyst_shard", 8, 2, 5),
                 ("minecraft:copper_ingot", 6, 2, 6),
                 ("minecraft:blue_dye", 5, 2, 4),
                 ("minecraft:spyglass", 2, 1, 1),
                 ("gems:gallery_plane", 4, 2, 4),
                 ("gems:gallery_prism", 2, 1, 2),
                 ("gems:gem_augment_focus", 1, 1, 1)],
    },
    "lastlight_exchange": {
        "biomes": ["gems:lastlight_yard"],
        "loot": [("minecraft:gold_ingot", 5, 1, 4),
                 ("minecraft:copper_ingot", 8, 2, 5),
                 ("minecraft:clock", 3, 1, 1),
                 ("minecraft:recovery_compass", 1, 1, 1),
                 ("gems:station_ashlar", 4, 2, 4),
                 ("gems:station_dial", 2, 1, 2),
                 ("minecraft:bread", 8, 2, 5)],
    },
    "rehearsal_works": {
        "biomes": ["gems:rehearsal_thicket", "minecraft:forest", "minecraft:sparse_jungle"],
        "loot": [("minecraft:string", 8, 2, 6),
                 ("minecraft:leather", 6, 1, 4),
                 ("minecraft:slime_ball", 5, 1, 3),
                 ("minecraft:crossbow", 2, 1, 1),
                 ("gems:stage_timber", 4, 2, 4),
                 ("gems:stage_curtain", 2, 1, 2),
                 ("gems:gem_augment_persistence", 1, 1, 1)],
    },
    "branch_vault": {
        "biomes": ["gems:branch_taiga", "minecraft:taiga", "minecraft:old_growth_pine_taiga"],
        "loot": [("minecraft:paper", 8, 2, 6),
                 ("minecraft:redstone", 7, 2, 6),
                 ("minecraft:compass", 4, 1, 1),
                 ("minecraft:book", 6, 1, 3),
                 ("gems:folio_stone", 4, 2, 4),
                 ("gems:branch_index", 2, 1, 2),
                 ("gems:gem_augment_focus", 1, 1, 1)],
    },
    "redline_house": {
        "biomes": ["gems:redline_refuge", "minecraft:plains", "minecraft:meadow"],
        "loot": [("minecraft:golden_carrot", 6, 1, 4),
                 ("minecraft:shield", 2, 1, 1),
                 ("minecraft:arrow", 7, 4, 10),
                 ("minecraft:bread", 8, 2, 6),
                 ("gems:refuge_brick", 4, 2, 4),
                 ("gems:shelter_emblem", 2, 1, 2),
                 ("gems:gem_augment_resonance", 1, 1, 1)],
    },
    "hushwire": {
        "biomes": ["gems:hushwire_heath", "minecraft:dark_forest"],
        "loot": [("minecraft:redstone", 8, 2, 7),
                 ("minecraft:echo_shard", 2, 1, 2),
                 ("minecraft:note_block", 4, 1, 2),
                 ("minecraft:copper_ingot", 6, 2, 5),
                 ("gems:gem_augment_resonance", 1, 1, 1)],
    },
    "unwritten_court": {
        "biomes": ["gems:unwritten_stage"],
        "loot": [("minecraft:arrow", 8, 4, 12),
                 ("minecraft:feather", 6, 2, 5),
                 ("minecraft:golden_apple", 2, 1, 1),
                 ("gems:court_plaster", 4, 2, 4),
                 ("gems:mask_relief", 2, 1, 2),
                 ("minecraft:bow", 2, 1, 1),
                 ("gems:gem_augment_persistence", 1, 1, 1)],
    },
    "second_chance_town": {
        "biomes": ["gems:deco_quarter", "minecraft:plains", "minecraft:meadow", "minecraft:forest"],
        "loot": [("minecraft:emerald", 5, 1, 3),
                 ("minecraft:bread", 8, 2, 5),
                 ("minecraft:paper", 6, 2, 6),
                 ("minecraft:compass", 2, 1, 1),
                 ("gems:deco_faience", 3, 1, 3),
                 ("gems:deco_marquee", 2, 1, 2),
                 ("gems:deco_sunburst", 1, 1, 1),
                 ("gems:gem_augment_focus", 1, 1, 1)],
    },
    "inkbound_workshop": {
        "biomes": ["gems:inkbound_grove", "minecraft:cherry_grove", "minecraft:forest", "minecraft:flower_forest"],
        "loot": [("minecraft:paper", 8, 2, 6),
                 ("minecraft:ink_sac", 7, 1, 3),
                 ("minecraft:book", 5, 1, 2),
                 ("gems:inkbound_screen", 3, 1, 2),
                 ("gems:inkbound_inkstone", 2, 1, 1)],
    },
    "khaled_floodgate": {
        "biomes": ["gems:riverward_banks", "minecraft:swamp", "minecraft:river"],
        "loot": [("minecraft:shield", 3, 1, 1),
                 ("minecraft:iron_ingot", 8, 2, 5),
                 ("minecraft:bread", 7, 2, 5),
                 ("minecraft:water_bucket", 2, 1, 1),
                 ("gems:riverward_limestone", 3, 1, 3),
                 ("gems:riverward_gauge", 2, 1, 1)],
    },
    "nour_signal_harbor": {
        "biomes": ["gems:signal_coast"],
        "loot": [("minecraft:compass", 3, 1, 1),
                 ("minecraft:spyglass", 2, 1, 1),
                 ("minecraft:cod", 7, 2, 5),
                 ("minecraft:copper_ingot", 7, 2, 5),
                 ("gems:signal_buoy", 2, 1, 1),
                 ("gems:harbor_chalk", 3, 1, 3),
                 ("gems:harbor_wave_tile", 2, 1, 2)],
    },
    "fam_forum": {
        "biomes": ["gems:forum_glade"],
        "loot": [("minecraft:book", 7, 1, 2),
                 ("minecraft:paper", 9, 2, 6),
                 ("minecraft:bread", 6, 2, 4),
                 ("minecraft:flower_pot", 5, 1, 2),
                 ("gems:forum_plaster", 4, 2, 4),
                 ("gems:forum_mosaic", 3, 1, 3)],
    },
    "adl_survey_house": {
        "biomes": ["gems:survey_highlands"],
        "loot": [("minecraft:compass", 5, 1, 1),
                 ("minecraft:spyglass", 4, 1, 1),
                 ("minecraft:paper", 8, 2, 6),
                 ("minecraft:copper_ingot", 6, 1, 4),
                 ("gems:survey_stone", 4, 2, 4),
                 ("gems:compass_mosaic", 3, 1, 3)],
    },
    "othman_library": {
        "biomes": ["gems:archive_commons"],
        "loot": [("minecraft:book", 8, 1, 3),
                 ("minecraft:paper", 8, 2, 6),
                 ("minecraft:compass", 3, 1, 1),
                 ("minecraft:lapis_lazuli", 5, 1, 4),
                 ("gems:archive_limestone", 4, 2, 4),
                 ("gems:archive_inlay", 3, 1, 3)],
    },
    "yehia_story_lodge": {
        "biomes": ["gems:storybook_vale"],
        "loot": [("minecraft:book", 7, 1, 2),
                 ("minecraft:cookie", 8, 2, 5),
                 ("minecraft:glow_berries", 7, 2, 5),
                 ("minecraft:paper", 6, 1, 4),
                 ("gems:storybook_turf", 4, 2, 4),
                 ("gems:storybook_path", 3, 1, 3)],
    },
    "omar_lost_quarter": {
        "biomes": ["gems:lost_wadi"],
        "loot": [("minecraft:compass", 5, 1, 1),
                 ("minecraft:clock", 5, 1, 1),
                 ("minecraft:paper", 8, 2, 6),
                 ("minecraft:emerald", 4, 1, 3),
                 ("gems:lost_clay", 4, 2, 4),
                 ("gems:lost_tile", 3, 1, 3)],
    },
    "soryal_repair_yard": {
        "biomes": ["gems:reclaimed_woodland"],
        "loot": [("minecraft:copper_ingot", 8, 2, 5),
                 ("minecraft:iron_ingot", 7, 1, 4),
                 ("minecraft:glass_pane", 5, 2, 6),
                 ("minecraft:paper", 5, 1, 3),
                 ("gems:patchwork_brick", 4, 2, 4),
                 ("gems:patchwork_mosaic", 3, 1, 3)],
    },
    "ali_splitstone_cloister": {
        "biomes": ["gems:splitstone_heights"],
        "loot": [("minecraft:paper", 8, 2, 6),
                 ("minecraft:compass", 4, 1, 1),
                 ("minecraft:spyglass", 3, 1, 1),
                 ("minecraft:iron_ingot", 5, 1, 3),
                 ("gems:splitstone", 4, 2, 4),
                 ("gems:waymark_tile", 3, 1, 3)],
    },
}

ALLY_START_TEMPLATES = {
    "khaled_floodgate": "gems:khaled_riverward_floodgate_v8",
    "soryal_repair_yard": "gems:soryal_reclamation_district_v7",
    "adl_survey_house": "gems:adl_bearing_hall_v9",
    "fam_forum": "gems:fam_forum_civic_arcades_v8",
    "nour_signal_harbor": "gems:nour_signal_harbor_station_v5",
    "omar_lost_quarter": "gems:omar_lost_quarter_wadi_v5",
    "ali_splitstone_cloister": "gems:ali_splitstone_cloister_arrival_v4",
    "yehia_story_lodge": "gems:yehia_story_lodge_arrival_v4",
}
ALLY_CHILD_TEMPLATES = {
    "khaled_shieldworks": "gems:khaled_tide_engine_v6",
    "khaled_shelter_walk": "gems:khaled_refuge_clinic_v6",
    "adl_sightpost": "gems:adl_sight_tower_v9",
    "adl_chart_shelter": "gems:adl_chart_library_v9",
    "fam_story_hall": "gems:fam_story_hall_assembly_v6",
    "fam_open_school": "gems:fam_open_school_assembly_v6",
    "nour_boathouse": "gems:nour_boathouse_station_v5",
    "nour_foghouse": "gems:nour_foghouse_station_v5",
    "omar_exchange_court": "gems:omar_exchange_court_caravan_v3",
    "omar_memory_loft": "gems:omar_memory_loft_caravan_v3",
    "omar_return_oasis": "gems:omar_return_oasis_caravan_v3",
    "ali_high_map_room": "gems:ali_high_map_room_survey_v3",
    "ali_cut_passage": "gems:ali_cut_passage_survey_v3",
    "yehia_truth_garden": "gems:yehia_truth_garden_v3",
    "yehia_prop_house": "gems:yehia_prop_house_v3",
}


def _json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def _resource_files(name: str, serial: int) -> None:
    spec = WORLD[name]
    dense_custom_biome = name in {
        "framefall_gallery", "lastlight_exchange", "unwritten_court"
    }
    _json(DATA / f"tags/worldgen/biome/has_structure/{name}.json",
          {"values": spec["biomes"]})
    _json(DATA / f"tags/worldgen/structure/atlas_target/{name}.json",
          {"values": [f"gems:{name}"]})
    _json(DATA / f"worldgen/structure/{name}.json", {
        "type": "gems:soryal_shore_v1" if name == "soryal_repair_yard"
                else "minecraft:jigsaw",
        "biomes": f"#gems:has_structure/{name}",
        "max_distance_from_center": 80 if name in {
            "framefall_gallery", "inkbound_workshop", "lastlight_exchange",
            "rehearsal_works", "branch_vault", "redline_house",
            "hushwire", "omar_lost_quarter", "ali_splitstone_cloister",
            "adl_survey_house", "fam_forum", "nour_signal_harbor"} else
            64 if name in {"second_chance_town", "yehia_story_lodge",
                           "khaled_floodgate", "soryal_repair_yard"} else 48,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": 3 if name == "framefall_gallery" else 2,
        "spawn_overrides": {},
        "start_height": {"absolute": 0},
        "start_pool": f"gems:{name}",
        "step": "surface_structures",
        "terrain_adaptation": "beard_thin",
        "use_expansion_hack": False,
    })
    _json(DATA / f"worldgen/structure_set/{name}.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 68251000 + serial,
                      "separation": 10 if name == "nour_signal_harbor" else
                          6 if dense_custom_biome else 24,
                      "spacing": 32 if name == "nour_signal_harbor" else
                          12 if dense_custom_biome else 64},
        "structures": [{"structure": f"gems:{name}", "weight": 1}],
    })
    _json(DATA / f"worldgen/template_pool/{name}.json", {
        "elements": [{"element": {
            "element_type": "minecraft:single_pool_element",
            "location": "gems:framefall_gallery_v6" if name == "framefall_gallery"
                else "gems:lastlight_exchange_arrival_v4" if name == "lastlight_exchange"
                else "gems:rehearsal_works_north_entry_v7" if name == "rehearsal_works"
                else "gems:branch_vault_vista_v7" if name == "branch_vault"
                else "gems:redline_house_civic_v4" if name == "redline_house"
                else "gems:hushwire_record_hall_v6" if name == "hushwire"
                else "gems:unwritten_court_v4" if name == "unwritten_court"
                else "gems:hamed_workshop_cherry_v6" if name == "inkbound_workshop"
                else "gems:second_chance_town_arrival_v4" if name == "second_chance_town"
                else ALLY_START_TEMPLATES[name] if name in ALLY_START_TEMPLATES
                else f"gems:{name}",
            "processors": {"processors": []},
            "projection": "rigid",
        }, "weight": 1}],
        "fallback": "minecraft:empty",
    })
    for satellite in SATELLITES_BY_ROOT[name]:
        _json(DATA / f"worldgen/template_pool/{satellite}.json", {
            "elements": [{"element": {
                "element_type": "minecraft:single_pool_element",
                "location": "gems:town_hall_civic_v5" if satellite == "town_hall"
                    else f"gems:{satellite}_v5" if satellite in {
                        "rehearsal_prop_foundry", "rehearsal_costume_house"}
                    else f"gems:{satellite}_v3" if satellite in {
                        "branch_escape_tower", "branch_map_room",
                        "redline_infirmary", "redline_watchpost"}
                    else "gems:hushwire_receiver_field_v4" if satellite == "hushwire_dishfield"
                    else "gems:hushwire_relay_crypt_v4" if satellite == "hushwire_relay_bunker"
                    else f"gems:{satellite}_deco_v2" if satellite in {
                        "town_casino", "town_arcade", "town_lounge"}
                    else ALLY_CHILD_TEMPLATES.get(satellite, f"gems:{satellite}"),
                "processors": {"processors": []},
                "projection": "rigid",
            }, "weight": 1}],
            "fallback": "minecraft:empty",
        })
    entries = []
    for item, weight, minimum, maximum in spec["loot"]:
        entry = {"type": "minecraft:item", "name": item, "weight": weight}
        if minimum != 1 or maximum != 1:
            entry["functions"] = [{
                "function": "minecraft:set_count",
                "count": {"type": "minecraft:uniform", "min": minimum, "max": maximum},
            }]
        entries.append(entry)
    _json(DATA / f"loot_table/chests/{name}.json", {
        "type": "minecraft:chest",
        "pools": [{"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4},
                   "entries": entries}],
    })
    if name == "soryal_repair_yard":
        # Restore old set/pool after common generation and add the v8 key.
        # The old registered placement remains visible to saved-start lookup
        # while frequency zero prevents another legacy start.
        _json(DATA / f"tags/worldgen/structure/atlas_target/{name}.json",
              {"values": ["gems:soryal_repair_yard_v8", f"gems:{name}"]})
        _json(DATA / f"tags/worldgen/biome/has_structure/{name}_v8.json",
              {"values": ["gems:reclaimed_woodland", "minecraft:river",
                          "minecraft:swamp"]})
        old = json.loads((DATA / f"worldgen/structure/{name}.json").read_text())
        _json(DATA / f"worldgen/structure/{name}_v8.json",
              {**old, "biomes": f"#gems:has_structure/{name}_v8",
               "start_pool": "gems:soryal_reclamation_district_v11",
               "terrain_adaptation": "none"})
        _json(DATA / f"worldgen/structure_set/{name}.json",
              {"placement": {"type": "minecraft:random_spread",
                             "salt": 68251017, "separation": 24,
                             "spacing": 64, "frequency": 0.0},
               "structures": [{"structure": f"gems:{name}", "weight": 1}]})
        _json(DATA / f"worldgen/structure_set/{name}_v8.json",
              {"placement": {"type": "minecraft:random_spread",
                             "salt": 68251019, "separation": 10,
                             "spacing": 32},
               "structures": [{"structure": f"gems:{name}_v8", "weight": 1}]})


def main() -> None:
    # Keep original and earlier versioned templates intact for existing saves.
    # The separate architecture module owns only the new expedition variants.
    from generate_expedition_architecture import (
        BUILDERS as EXPEDITION_ARCH_BUILDERS,
        ARRIVAL_BUILDERS as EXPEDITION_ARRIVAL_BUILDERS)
    from generate_redline_hushwire_architecture import (
        BUILDERS as REDLINE_HUSHWIRE_ARCH_BUILDERS,
        ARRIVAL_BUILDERS as HUSHWIRE_ARRIVAL_BUILDERS)
    from generate_lastlight_arrival_architecture import BUILDERS as LASTLIGHT_ARRIVAL_BUILDERS
    from generate_framefall_court_architecture import (
        BUILDERS as FRAMEFALL_COURT_ARCH_BUILDERS,
        ARRIVAL_BUILDERS as COURT_ARRIVAL_BUILDERS)
    from generate_ally_architecture import BUILDERS as ALLY_ARCH_BUILDERS
    from generate_adl_fam_nour_architecture_candidates import BUILDERS as FORUM_HARBOR_HIGHLANDS_BUILDERS
    from generate_nour_fam_adl_arrival_architecture import BUILDERS as FORUM_HARBOR_ARRIVAL_BUILDERS
    from generate_nour_harbor_v5 import BUILDERS as NOUR_HARBOR_V5_BUILDERS
    from generate_khaled_soryal_district_candidates import (
        khaled_root, khaled_flowworks, khaled_clinic,
        soryal_root, soryal_quay, soryal_hall)
    from generate_district_arrival_access import BUILDERS as DISTRICT_ACCESS_BUILDERS
    from generate_soryal_arrival_architecture import BUILDERS as SORYAL_ARRIVAL_BUILDERS
    from generate_ali_yehia_arrival_architecture import BUILDERS as ALI_YEHIA_ARRIVAL_BUILDERS
    from generate_ali_full_district import BUILDERS as ALI_FULL_V5_BUILDERS
    from generate_facade_scale_architecture import BUILDERS as FACADE_SCALE_BUILDERS
    from generate_facade_mass_architecture import BUILDERS as FACADE_MASS_BUILDERS
    from generate_rehearsal_north_architecture import BUILDERS as REHEARSAL_NORTH_BUILDERS
    from generate_adl_observatory_architecture import BUILDERS as ADL_OBSERVATORY_BUILDERS
    from generate_adl_west_elevations import BUILDERS as ADL_WEST_BUILDERS
    from generate_adl_ridge_fronts import BUILDERS as ADL_RIDGE_BUILDERS
    from generate_khaled_riverward_architecture import BUILDERS as KHALED_RIVERWARD_BUILDERS
    from generate_khaled_riverward_v6 import BUILDERS as KHALED_RIVERWARD_V6_BUILDERS
    from generate_khaled_riverward_v7 import BUILDERS as KHALED_RIVERWARD_V7_BUILDERS
    from generate_khaled_riverward_v8 import BUILDERS as KHALED_RIVERWARD_V8_BUILDERS
    from generate_branch_vista_v7 import (branch_vault_vista_v7,
                                          branch_outer_vista_v7)
    from generate_hushwire_district_v9 import (record_hall_v9,
                                               procession_v4)
    from generate_fam_monumental_forum import fam_root_v8
    from generate_fam_forum_v9 import BUILDERS as FAM_FORUM_V9_BUILDERS
    from generate_soryal_frontage_candidate import root_frontage_v4
    from generate_wadi_reclaim_facades import BUILDERS as WADI_RECLAIM_BUILDERS
    from generate_soryal_district_v6 import BUILDERS as SORYAL_DISTRICT_V6_BUILDERS
    from generate_soryal_district_v7 import BUILDERS as SORYAL_DISTRICT_V7_BUILDERS
    from generate_soryal_district_v8 import BUILDERS as SORYAL_DISTRICT_V8_BUILDERS
    from generate_soryal_district_v9 import BUILDERS as SORYAL_DISTRICT_V9_BUILDERS
    from generate_soryal_district_v10 import BUILDERS as SORYAL_DISTRICT_V10_BUILDERS
    from generate_othman_archive_v7 import BUILDERS as OTHMAN_ARCHIVE_V7_BUILDERS
    from generate_othman_archive_v8 import BUILDERS as OTHMAN_ARCHIVE_V8_BUILDERS
    from generate_othman_archive_v9 import BUILDERS as OTHMAN_ARCHIVE_V9_BUILDERS
    from generate_hamed_cherry_v3 import BUILDERS as HAMED_CHERRY_V3_BUILDERS
    from generate_hamed_cherry_v4 import BUILDERS as HAMED_CHERRY_V4_BUILDERS
    from generate_hamed_cherry_v5 import BUILDERS as HAMED_CHERRY_V5_BUILDERS
    from generate_hamed_cherry_v6 import BUILDERS as HAMED_CHERRY_V6_BUILDERS

    summary = [builder().write() for builder in BUILDERS]
    summary.extend(builder().write() for builder in OTHMAN_BRIDGE_BUILDERS)
    summary.extend(builder().write() for builder in OTHMAN_ARCHIVE_V7_BUILDERS)
    summary.extend(builder().write() for builder in OTHMAN_ARCHIVE_V8_BUILDERS)
    summary.extend(builder().write() for builder in OTHMAN_ARCHIVE_V9_BUILDERS)
    summary.extend(builder().write() for builder in FRAMEFALL_WALK_BUILDERS)
    summary.extend(builder().write() for builder in FRAMEFALL_COURT_ARCH_BUILDERS)
    summary.extend(builder().write() for builder in COURT_ARRIVAL_BUILDERS)
    summary.extend(builder().write() for builder in INKBOUND_VERSION_BUILDERS)
    summary.extend(builder().write() for builder in HAMED_CHERRY_V3_BUILDERS)
    summary.extend(builder().write() for builder in HAMED_CHERRY_V4_BUILDERS)
    summary.extend(builder().write() for builder in HAMED_CHERRY_V5_BUILDERS)
    summary.extend(builder().write() for builder in HAMED_CHERRY_V6_BUILDERS)
    summary.extend(builder().write() for builder in TOWNHALL_VERSION_BUILDERS)
    summary.extend(builder().write() for builder in TOWN_VERSION_BUILDERS)
    summary.extend(builder().write() for builder in TOWN_ARRIVAL_BUILDERS)
    summary.extend(builder().write() for builder in RAGAB_VERSION_BUILDERS)
    summary.extend(builder().write() for builder in LASTLIGHT_VERSION_BUILDERS)
    from convert_seegras_lastlight import package_for_game
    summary.extend(package_for_game())
    summary.extend(builder().write() for builder in LASTLIGHT_ARRIVAL_BUILDERS)
    summary.extend(builder().write() for builder in REHEARSAL_VERSION_BUILDERS)
    summary.extend(builder().write() for builder in BRANCH_VERSION_BUILDERS)
    summary.extend(builder().write() for builder in EXPEDITION_ARCH_BUILDERS)
    summary.extend(builder().write() for builder in EXPEDITION_ARRIVAL_BUILDERS)
    summary.extend(builder().write() for builder in REDLINE_VERSION_BUILDERS)
    summary.extend(builder().write() for builder in REDLINE_HUSHWIRE_ARCH_BUILDERS)
    summary.extend(builder().write() for builder in HUSHWIRE_ARRIVAL_BUILDERS)
    summary.extend(builder().write() for builder in (
        record_hall_v9, procession_v4))
    summary.extend(builder().write() for builder in UNWRITTEN_VERSION_BUILDERS)
    summary.extend(builder().write() for builder in VISUAL_ACCESS_VERSION_BUILDERS)
    summary.extend(builder().write() for builder in YEHIA_VERSION_BUILDERS)
    summary.extend(builder().write() for builder in ALLY_ARCH_BUILDERS)
    summary.extend(builder().write() for builder in FORUM_HARBOR_HIGHLANDS_BUILDERS)
    summary.extend(builder().write() for builder in FORUM_HARBOR_ARRIVAL_BUILDERS)
    summary.extend(builder().write() for builder in NOUR_HARBOR_V5_BUILDERS)
    summary.extend(builder().write() for builder in (
        khaled_root, khaled_flowworks, khaled_clinic,
        soryal_root, soryal_quay, soryal_hall))
    summary.extend(builder().write() for builder in DISTRICT_ACCESS_BUILDERS)
    summary.extend(builder().write() for builder in SORYAL_ARRIVAL_BUILDERS)
    summary.extend(builder(sys.modules[__name__]).write()
                   for builder in ALI_YEHIA_ARRIVAL_BUILDERS)
    summary.extend(builder().write() for builder in ALI_FULL_V5_BUILDERS)
    summary.extend(builder().write() for builder in FACADE_SCALE_BUILDERS)
    summary.extend(builder().write() for builder in FACADE_MASS_BUILDERS)
    summary.extend(builder().write() for builder in REHEARSAL_NORTH_BUILDERS)
    summary.extend(builder().write() for builder in ADL_OBSERVATORY_BUILDERS)
    summary.extend(builder().write() for builder in ADL_WEST_BUILDERS)
    summary.extend(builder().write() for builder in ADL_RIDGE_BUILDERS)
    summary.extend(builder().write() for builder in KHALED_RIVERWARD_BUILDERS)
    summary.extend(builder().write() for builder in KHALED_RIVERWARD_V6_BUILDERS)
    summary.extend(builder().write() for builder in KHALED_RIVERWARD_V7_BUILDERS)
    summary.extend(builder().write() for builder in KHALED_RIVERWARD_V8_BUILDERS)
    summary.extend(builder().write() for builder in (
        branch_vault_vista_v7, branch_outer_vista_v7,
        fam_root_v8, root_frontage_v4))
    summary.extend(builder().write() for builder in FAM_FORUM_V9_BUILDERS)
    summary.extend(builder().write() for builder in WADI_RECLAIM_BUILDERS)
    summary.extend(builder().write() for builder in SORYAL_DISTRICT_V6_BUILDERS)
    summary.extend(builder().write() for builder in SORYAL_DISTRICT_V7_BUILDERS)
    summary.extend(builder().write() for builder in SORYAL_DISTRICT_V8_BUILDERS)
    summary.extend(builder().write() for builder in SORYAL_DISTRICT_V9_BUILDERS)
    summary.extend(builder().write() for builder in SORYAL_DISTRICT_V10_BUILDERS)
    for serial, item in enumerate(summary[:len(ROOT_BUILDERS)]):
        # Keep established structure-set salts stable for downstream districts.
        _resource_files(item["name"], serial if serial < 9 else serial + 1)
    for name in ("othman_library_bridge", "othman_study_bridge_wing",
                 "othman_library_arrival", "othman_study_arrival_wing",
                 "othman_library_bank_entry_v2",
                 "othman_library_dry_arrival_v3",
                 "othman_study_dry_arrival_wing_v3",
                 "othman_library_north_arrival_v4",
                 "othman_library_colonnade_v5",
                 "othman_library_colonnade_v6",
                 "othman_study_north_arrival_wing_v4",
                 "othman_library_east_gallery_v2", "othman_catalog_hillside_v2",
                 "othman_library_archive_v7", "othman_catalog_rotunda_v3",
                 "othman_study_arrival_wing_v5",
                 "othman_library_archive_v8",
                 "othman_library_archive_v9",
                 "othman_library_archive_v10", "othman_catalog_rotunda_v4",
                 "othman_study_arrival_wing_v6", "othman_library_archive_v11",
                 "othman_catalog_rotunda_v5", "othman_study_arrival_wing_v7",
                 "framefall_gallery_walk", "framefall_atelier_walk",
                 "framefall_promenade", "framefall_gallery_facade",
                 "framefall_atelier_facade", "framefall_promenade_facade",
                 "framefall_gallery_transition_v3",
                 "framefall_atelier_transition_v3",
                 "framefall_promenade_transition_v3",
                 "framefall_gallery_v4", "framefall_atelier_v4",
                 "framefall_promenade_v4",
                 "inkbound_workshop_quarter_v2", "inkbound_print_house_v2",
                 "inkbound_garden_v2", "inkbound_arrival_lane_v2",
                 "hamed_workshop_cherry_v3", "hamed_print_hall_cherry_v3",
                 "hamed_paper_garden_cherry_v3", "hamed_cherry_lane_v3",
                 "hamed_workshop_cherry_v4", "hamed_print_hall_cherry_v4",
                 "hamed_cherry_lane_v4",
                 "hamed_workshop_cherry_v5", "hamed_print_hall_cherry_v5",
                 "hamed_cherry_lane_v5",
                 "hamed_workshop_cherry_v6", "hamed_print_hall_cherry_v6",
                 "town_hall_alcoves_v2", "town_hall_gallery_v3",
                 "town_hall_civic_v4", "town_hall_civic_v5",
                 "town_hall_arrival_v6", "town_market_arrival_v2",
                 "town_guesthouse_arrival_v2",
                 "town_casino_deco_v2",
                 "town_arcade_deco_v2", "town_lounge_deco_v2",
                 "lastlight_exchange_v2", "lastlight_exchange_seegras_v3",
                 "lastlight_seegras_street_v1", "lastlight_market_v2",
                 "lastlight_signal_box_v2",
                 "lastlight_exchange_arrival_v4",
                 "lastlight_seegras_street_arrival_v2",
                 "lastlight_market_bridge_v3",
                 "rehearsal_works_v2", "rehearsal_prop_foundry_v2",
                 "rehearsal_costume_house_v2",
                 "branch_vault_v2", "branch_escape_tower_v2",
                 "branch_map_room_v2",
                 "rehearsal_works_v3", "rehearsal_works_v4", "rehearsal_prop_foundry_v3",
                 "rehearsal_costume_house_v3",
                 "rehearsal_works_facade_v5", "rehearsal_prop_foundry_v4",
                 "rehearsal_costume_house_v4",
                 "rehearsal_works_marquee_v6", "rehearsal_prop_foundry_v5",
                 "rehearsal_costume_house_v5",
                 "rehearsal_works_north_entry_v7", "rehearsal_north_causeway_v7",
                 "branch_vault_v3", "branch_vault_arrival_v4",
                 "branch_vault_wayfinding_v5", "branch_vault_wayfinding_v6",
                 "branch_outer_wayfinder_v6",
                 "branch_vault_vista_v7", "branch_outer_vista_v7",
                 "branch_escape_tower_v3",
                 "branch_map_room_v3",
                 "redline_house_v2", "redline_house_arrival_v3",
                 "redline_infirmary_v2",
                 "redline_watchpost_v2",
                 "redline_house_civic_v4", "redline_infirmary_v3",
                 "redline_watchpost_v3", "hushwire_cathedral_v2",
                 "hushwire_signal_gate_v3",
                 "hushwire_dishfield_v2", "hushwire_relay_bunker_v2",
                 "hushwire_record_hall_v4", "hushwire_receiver_field_v3",
                 "hushwire_relay_crypt_v3",
                 "hushwire_record_hall_v5", "hushwire_receiver_field_v4",
                 "hushwire_relay_crypt_v4",
                 "hushwire_record_hall_v6", "hushwire_procession_v1",
                 "hushwire_record_hall_v7", "hushwire_procession_v2",
                 "hushwire_record_hall_v8", "hushwire_procession_v3",
                 "hushwire_record_hall_v9", "hushwire_procession_v4",
                 "unwritten_court_v2", "unwritten_stewards_annex_v2",
                 "unwritten_mask_archive_v2",
                 "unwritten_court_v3", "unwritten_court_v4", "unwritten_stewards_annex_v3",
                 "unwritten_mask_archive_v3",
                 "second_chance_town_deco_v2", "second_chance_town_civic_v3",
                 "second_chance_town_arrival_v4",
                 "adl_survey_house_facade_v2", "yehia_story_lodge_arrival_v2",
                 "adl_survey_house_highlands_v3",
                 "adl_sightpost_highlands_v3", "adl_chart_shelter_highlands_v3",
                 "fam_forum_assembly_v3", "fam_story_hall_assembly_v3",
                 "fam_open_school_assembly_v3",
                 "nour_signal_harbor_station_v3",
                 "nour_boathouse_station_v3", "nour_foghouse_station_v3",
                 "adl_survey_house_highlands_v4",
                 "adl_sightpost_highlands_v4", "adl_chart_shelter_highlands_v4",
                 "adl_survey_house_facade_v5", "adl_sightpost_highlands_v5",
                 "adl_chart_shelter_highlands_v5",
                 "adl_survey_house_instruments_v6",
                 "adl_sightpost_highlands_v6", "adl_chart_shelter_highlands_v6",
                 "adl_bearing_hall_v7", "adl_sight_tower_v7",
                 "adl_chart_library_v7",
                 "adl_bearing_hall_v8", "adl_sight_tower_v8",
                 "adl_chart_library_v8",
                 "adl_bearing_hall_v9", "adl_sight_tower_v9",
                 "adl_chart_library_v9",
                 "fam_forum_assembly_v4", "fam_story_hall_assembly_v4",
                 "fam_open_school_assembly_v4",
                 "fam_forum_facade_v5", "fam_story_hall_assembly_v5",
                 "fam_open_school_assembly_v5",
                 "fam_forum_terraces_v6", "fam_forum_east_wayfinding_v7",
                 "fam_forum_civic_arcades_v8",
                 "fam_forum_listening_court_v9",
                 "fam_story_hall_library_v9",
                 "fam_open_school_colonnade_v9",
                 "fam_east_reference_v1", "fam_story_hall_assembly_v6",
                 "fam_open_school_assembly_v6",
                 "nour_signal_harbor_station_v4",
                 "nour_boathouse_station_v4", "nour_foghouse_station_v4",
                 "nour_signal_harbor_station_v5",
                 "nour_boathouse_station_v5", "nour_foghouse_station_v5",
                 "khaled_riverward_district_v1", "khaled_flowworks_v1",
                 "khaled_refuge_clinic_v1",
                 "khaled_riverward_district_bank_v2",
                 "khaled_refuge_clinic_bank_v2",
                 "khaled_riverward_district_facade_v3",
                 "khaled_refuge_clinic_facade_v3",
                 "khaled_riverward_district_works_v4",
                 "khaled_flowworks_facade_v2",
                 "khaled_refuge_clinic_facade_v4",
                 "khaled_riverward_floodgate_v5",
                 "khaled_tide_engine_v3", "khaled_refuge_clinic_v5",
                 "khaled_riverward_floodgate_v6", "khaled_tide_engine_v4",
                 "khaled_riverward_floodgate_v7", "khaled_tide_engine_v5",
                 "khaled_riverward_floodgate_v8", "khaled_tide_engine_v6",
                 "khaled_refuge_clinic_v6",
                 "soryal_reclamation_district_v1",
                 "soryal_reclamation_district_arrival_v2",
                 "soryal_reclamation_district_arrival_v3",
                 "soryal_reclamation_district_frontage_v4",
                 "soryal_reclamation_district_works_v5",
                 "soryal_sorting_quay_v1",
                 "soryal_reuse_hall_v1",
                 "soryal_sorting_quay_v2", "soryal_reuse_hall_v2",
                 "soryal_reclamation_district_v6",
                 "soryal_sorting_quay_v3", "soryal_reuse_hall_v3",
                 "soryal_reclamation_district_v7",
                 "soryal_sorting_quay_v4", "soryal_reuse_hall_v4",
                 "soryal_reclamation_district_v8",
                 "soryal_sorting_quay_v5", "soryal_reuse_hall_v5",
                 "soryal_reclamation_district_v9",
                 "soryal_sorting_quay_v6", "soryal_reuse_hall_v6",
                 "soryal_reclamation_district_v10",
                 "soryal_sorting_quay_v7", "soryal_reuse_hall_v7",
                 "soryal_reclamation_district_v11",
                 "yehia_story_lodge_v3", "yehia_truth_garden_v3",
                 "yehia_prop_house_v3", "yehia_story_lodge_arrival_v4",
                 "ali_splitstone_cloister_facade_v2",
                 "omar_lost_quarter_caravan_v2", "omar_exchange_court_caravan_v2",
                 "omar_memory_loft_caravan_v2", "omar_return_oasis_caravan_v2",
                 "omar_lost_quarter_wadi_v3", "omar_lost_quarter_wadi_v4",
                 "omar_lost_quarter_wadi_v5",
                 "omar_exchange_court_caravan_v3",
                 "omar_memory_loft_caravan_v3",
                 "omar_return_oasis_caravan_v3",
                 "ali_splitstone_cloister_survey_v3",
                 "ali_splitstone_cloister_arrival_v4",
                 "ali_high_map_room_survey_v3", "ali_cut_passage_survey_v3",
                 "ali_splitstone_citadel_full_v5",
                 "ali_splitstone_citadel_full_v6",
                 "ali_high_atlas_hall_full_v5", "ali_cut_chapel_full_v5"):
        _json(DATA / f"worldgen/template_pool/{name}.json", {
            "elements": [{"element": {
                "element_type": "minecraft:single_pool_element",
                "location": f"gems:{name}",
                "processors": {"processors": []},
                "projection": "rigid",
            }, "weight": 1}],
            "fallback": "minecraft:empty",
        })
    target = ROOT / "docs/adventure/STRUCTURE_MANIFEST.json"
    target.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    for item in summary:
        print(f"{item['name']}: {item['solid_blocks']} solid blocks, "
              f"{len(item['chests'])} loot chests, {len(item['signs'])} signs")


if __name__ == "__main__":
    main()
