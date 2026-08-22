"""Editable .schem/.litematic sources for Minecraft 26.3 structures.

Only Python's standard library is used. See ``--help`` for commands.
Unknown block IDs, malformed palettes and missing markers fail before output.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import math
import re
import struct
from collections import deque
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/data/gems"
SOURCES = ROOT / "builds"
MANIFEST = SOURCES / "manifest.json"
DATA_VERSION = 5023
MAX_NBT_BYTES = 150_000_000
MAX_BLOCKS = 4_000_000
ID = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")


@dataclass(frozen=True)
class Tag:
    kind: int
    value: object


def tag(kind: int, value: object) -> Tag:
    return Tag(kind, value)


def c(**fields: Tag) -> Tag:
    return tag(10, fields)


def s(value: str) -> Tag:
    return tag(8, value)


def i(value: int) -> Tag:
    return tag(3, value)


def lst(kind: int, values: list[Tag]) -> Tag:
    return tag(9, (kind, values))


def arr(kind: int, values: object) -> Tag:
    return tag(kind, values)


class Reader:
    def __init__(self, raw: bytes):
        self.raw = memoryview(raw)
        self.at = 0

    def take(self, length: int) -> bytes:
        if length < 0 or self.at + length > len(self.raw):
            raise ValueError("Truncated NBT")
        result = self.raw[self.at:self.at + length].tobytes()
        self.at += length
        return result

    def num(self, fmt: str):
        return struct.unpack(">" + fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self) -> str:
        return self.take(self.num("H")).decode("utf-8")

    def payload(self, kind: int, depth: int = 0) -> Tag:
        if depth > 64:
            raise ValueError("NBT nesting is too deep")
        if kind in (1, 2, 3, 4, 5, 6):
            return tag(kind, self.num({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind]))
        if kind == 7:
            return arr(7, self.take(self.num("i")))
        if kind == 8:
            return s(self.string())
        if kind == 9:
            child, size = self.num("B"), self.num("i")
            if size < 0 or size > MAX_BLOCKS * 8 or (size and child == 0):
                raise ValueError("Invalid NBT list")
            return lst(child, [self.payload(child, depth + 1) for _ in range(size)])
        if kind == 10:
            result = {}
            while (child := self.num("B")) != 0:
                name = self.string()
                if name in result:
                    raise ValueError(f"Duplicate NBT tag {name}")
                result[name] = self.payload(child, depth + 1)
            return tag(10, result)
        if kind in (11, 12):
            size = self.num("i")
            if size < 0 or size > MAX_BLOCKS * 8:
                raise ValueError("Invalid NBT array")
            fmt = "i" if kind == 11 else "q"
            return arr(kind, [self.num(fmt) for _ in range(size)])
        raise ValueError(f"Unsupported NBT tag {kind}")


def read_nbt(path: Path) -> Tag:
    raw = path.read_bytes()
    if raw.startswith(b"\x1f\x8b"):
        raw = gzip.decompress(raw)
    if len(raw) > MAX_NBT_BYTES:
        raise ValueError(f"{path}: NBT is too large")
    reader = Reader(raw)
    if reader.num("B") != 10:
        raise ValueError(f"{path}: expected a compound root")
    reader.string()
    root = reader.payload(10)
    if reader.at != len(raw):
        raise ValueError(f"{path}: trailing NBT bytes")
    return root


def _utf(value: str) -> bytes:
    raw = value.encode("utf-8")
    if len(raw) > 65535:
        raise ValueError("NBT string exceeds 65535 bytes")
    return struct.pack(">H", len(raw)) + raw


def _payload(value: Tag) -> bytes:
    kind, raw = value.kind, value.value
    if kind in (1, 2, 3, 4, 5, 6):
        return struct.pack(">" + {1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind], raw)
    if kind == 7:
        return struct.pack(">i", len(raw)) + bytes(raw)
    if kind == 8:
        return _utf(raw)
    if kind == 9:
        child, values = raw
        if any(value.kind != child for value in values):
            raise ValueError("Mixed NBT list types")
        return bytes([child]) + struct.pack(">i", len(values)) + b"".join(_payload(v) for v in values)
    if kind == 10:
        return b"".join(bytes([v.kind]) + _utf(k) + _payload(v) for k, v in raw.items()) + b"\x00"
    if kind in (11, 12):
        fmt = "i" if kind == 11 else "q"
        return struct.pack(">i", len(raw)) + b"".join(struct.pack(">" + fmt, v) for v in raw)
    raise ValueError(f"Unsupported NBT tag {kind}")


def write_nbt(path: Path, value: Tag, root_name: str = "") -> None:
    packed = encode_nbt(value, root_name)
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists() or path.read_bytes() != packed:
        path.write_bytes(packed)


def encode_nbt(value: Tag, root_name: str = "") -> bytes:
    raw = b"\x0a" + _utf(root_name) + _payload(value)
    packed = bytearray(gzip.compress(raw, compresslevel=9, mtime=0))
    packed[9] = 0xff
    return bytes(packed)


def field(value: Tag, name: str, kind: int | None = None) -> Tag:
    if value.kind != 10 or name not in value.value:
        raise ValueError(f"Missing NBT field {name}")
    result = value.value[name]
    if kind is not None and result.kind != kind:
        raise ValueError(f"NBT field {name} has wrong type")
    return result


def optional(value: Tag, name: str, default: Tag) -> Tag:
    return value.value.get(name, default)


def list_values(value: Tag) -> list[Tag]:
    if value.kind != 9:
        raise ValueError("Expected NBT list")
    return value.value[1]


def positive_size(value: object) -> tuple[int, int, int]:
    size = tuple(int(v) for v in value)
    if len(size) != 3 or any(v < 1 or v > 512 for v in size) or size[0] * size[1] * size[2] > MAX_BLOCKS:
        raise ValueError(f"Unsupported structure size {size}")
    return size


def state_string(value: Tag) -> str:
    name = field(value, "id", 8).value
    properties = optional(value, "properties", c())
    if properties.kind != 10:
        raise ValueError("Invalid block properties")
    if not properties.value:
        return name
    return name + "[" + ",".join(f"{key}={v.value}" for key, v in sorted(properties.value.items())) + "]"


def state_nbt(raw: str) -> Tag:
    name, _, props = raw.partition("[")
    if not ID.fullmatch(name):
        raise ValueError(f"Invalid block ID: {name}")
    if not props:
        return c(id=s(name))
    if not props.endswith("]"):
        raise ValueError(f"Invalid block properties: {raw}")
    pairs = {}
    for pair in props[:-1].split(","):
        key, sep, value = pair.partition("=")
        if not sep or not re.fullmatch(r"[a-z0-9_]+", key) or not re.fullmatch(r"[a-z0-9_]+", value) or key in pairs:
            raise ValueError(f"Invalid block properties: {raw}")
        pairs[key] = s(value)
    return c(id=s(name), properties=tag(10, dict(sorted(pairs.items()))))


def litematic_state_nbt(raw: str) -> Tag:
    vanilla = state_nbt(raw)
    fields = {"Name": field(vanilla, "id", 8)}
    if "properties" in vanilla.value:
        fields["Properties"] = vanilla.value["properties"]
    return tag(10, fields)


def litematic_state_string(value: Tag) -> str:
    fields = {"id": field(value, "Name", 8)}
    if "Properties" in value.value:
        fields["properties"] = value.value["Properties"]
    return state_string(tag(10, fields))


def validate_states(states: set[str], known_ids: set[str]) -> None:
    for state in states:
        state_nbt(state)
        name = state.split("[", 1)[0]
        if name not in known_ids and name != "minecraft:structure_void":
            raise ValueError(f"Unknown block ID {name}; verify it in 26.3 and add it to builds/known_blocks.txt")


def read_known_ids() -> set[str]:
    path = SOURCES / "known_blocks.txt"
    return {line.strip() for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.startswith("#")}


def position(value: Tag) -> tuple[int, int, int]:
    if value.kind not in (9, 11):
        raise ValueError("Expected integer position")
    raw = list_values(value) if value.kind == 9 else value.value
    return tuple(int(v.value if isinstance(v, Tag) else v) for v in raw)


def structure_model(root: Tag) -> tuple[tuple[int, int, int], dict[tuple[int, int, int], tuple[str, Tag | None]], list[Tag], int]:
    size = positive_size(position(field(root, "size")))
    palette = [state_string(v) for v in list_values(field(root, "palette"))]
    blocks = {}
    for block in list_values(field(root, "blocks")):
        pos = position(field(block, "pos"))
        if any(pos[n] < 0 or pos[n] >= size[n] for n in range(3)) or pos in blocks:
            raise ValueError(f"Out-of-bounds or duplicate block {pos}")
        index = field(block, "state", 3).value
        if not 0 <= index < len(palette):
            raise ValueError("Invalid structure palette index")
        blocks[pos] = (palette[index], block.value.get("nbt"))
    entities = list_values(optional(root, "entities", lst(10, [])))
    data_version = field(root, "DataVersion", 3).value
    return size, blocks, entities, data_version


def make_structure(size, blocks, entities, data_version=DATA_VERSION) -> Tag:
    states = sorted({state for state, _ in blocks.values()})
    indices = {state: n for n, state in enumerate(states)}
    records = []
    for pos, (state, extra) in sorted(blocks.items()):
        entry = {"pos": lst(3, [i(v) for v in pos]), "state": i(indices[state])}
        if extra is not None:
            entry["nbt"] = extra
        records.append(tag(10, entry))
    return c(size=lst(3, [i(v) for v in size]),
             palette=lst(10, [state_nbt(v) for v in states]),
             blocks=lst(10, records), entities=lst(10, entities),
             DataVersion=i(data_version))


def cells(size):
    width, height, length = size
    for y in range(height):
        for z in range(length):
            for x in range(width):
                yield x, y, z


def explicit_void_bits(size, blocks) -> bytes:
    bits = bytearray((size[0] * size[1] * size[2] + 7) // 8)
    for index, pos in enumerate(cells(size)):
        if pos in blocks and blocks[pos][0] == "minecraft:structure_void":
            bits[index // 8] |= 1 << (index % 8)
    return bytes(bits)


def restore_explicit_voids(container: Tag, size, blocks) -> None:
    bits = optional(container, "GemsExplicitVoid", arr(7, b"")).value
    if not bits:
        return
    if len(bits) != (size[0] * size[1] * size[2] + 7) // 8:
        raise ValueError("Invalid explicit structure-void mask")
    for index, pos in enumerate(cells(size)):
        if bits[index // 8] & (1 << (index % 8)):
            # A visual editor may replace this old void with a real block. In
            # that case the edited cell wins; the bit only preserves unchanged
            # explicit voids through the first conversion.
            if pos not in blocks:
                blocks[pos] = ("minecraft:structure_void", None)


def varints(indices: list[int]) -> bytes:
    out = bytearray()
    for value in indices:
        while value > 0x7f:
            out.append((value & 0x7f) | 0x80)
            value >>= 7
        out.append(value)
    return bytes(out)


def unvarints(raw: bytes, count: int) -> list[int]:
    result, value, shift = [], 0, 0
    for byte in raw:
        value |= (byte & 0x7f) << shift
        if byte & 0x80:
            shift += 7
            if shift > 28:
                raise ValueError("Invalid schematic varint")
        else:
            result.append(value)
            value = shift = 0
    if shift or len(result) != count:
        raise ValueError(f"Expected {count} schematic blocks, got {len(result)}")
    return result


def to_schem(structure: Tag) -> Tag:
    size, blocks, entities, data_version = structure_model(structure)
    states = sorted({state for state, _ in blocks.values()} | {"minecraft:structure_void"})
    indices = {state: n for n, state in enumerate(states)}
    data = varints([indices[blocks[pos][0] if pos in blocks else "minecraft:structure_void"] for pos in cells(size)])
    block_entities = []
    for pos, (_, extra) in sorted(blocks.items()):
        if extra is not None:
            if extra.kind != 10:
                raise ValueError("Block entity NBT must be compound")
            block_entities.append(c(Pos=arr(11, list(pos)),
                                    Id=field(extra, "id", 8), Data=extra))
    schematic_entities = []
    for entry in entities:
        entity_nbt = field(entry, "nbt", 10)
        schematic_entities.append(c(Pos=field(entry, "pos"),
                                    Id=field(entity_nbt, "id", 8), Data=entity_nbt))
    palette = tag(10, {state: i(index) for state, index in indices.items()})
    width, height, length = size
    return c(Schematic=c(Version=i(3), DataVersion=i(data_version),
                         Width=tag(2, width), Height=tag(2, height), Length=tag(2, length),
                         Offset=arr(11, [0, 0, 0]),
                         Blocks=c(Palette=palette, Data=arr(7, data),
                                  BlockEntities=lst(10, block_entities)),
                         Entities=lst(10, schematic_entities),
                         GemsExplicitVoid=arr(7, explicit_void_bits(size, blocks))))


def _schem_model(root: Tag):
    source = root.value.get("Schematic", root)
    version = field(source, "Version").value
    if version not in (2, 3):
        raise ValueError(f"Only Sponge .schem v2/v3 are supported, got {version}")
    size = positive_size([field(source, n).value for n in ("Width", "Height", "Length")])
    offset = optional(source, "Offset", arr(11, [0, 0, 0])).value
    if list(offset) != [0, 0, 0]:
        raise ValueError("Nonzero .schem Offset is ambiguous; reset the origin before importing")
    section = source.value.get("Blocks", source)
    palette = field(section, "Palette", 10).value
    by_index = {}
    for state, index in palette.items():
        if index.kind != 3 or index.value in by_index:
            raise ValueError("Invalid schematic palette")
        by_index[index.value] = state
    data = field(section, "Data", 7).value if version == 3 else field(source, "BlockData", 7).value
    indices = unvarints(data, size[0] * size[1] * size[2])
    blocks = {}
    for pos, index in zip(cells(size), indices):
        if index not in by_index:
            raise ValueError(f"Unknown schematic palette index {index}")
        state = by_index[index]
        if state != "minecraft:structure_void":
            blocks[pos] = (state, None)
    block_entities = list_values(optional(section, "BlockEntities", lst(10, [])))
    for entry in block_entities:
        pos = position(field(entry, "Pos"))
        if pos not in blocks:
            raise ValueError(f"Block entity at {pos} has no block")
        extra = entry.value.get("Data")
        if extra is None:
            extra = tag(10, {key: value for key, value in entry.value.items() if key not in ("Pos", "Id")})
            if "Id" in entry.value:
                extra.value["id"] = entry.value["Id"]
        blocks[pos] = (blocks[pos][0], extra)
    restore_explicit_voids(source, size, blocks)
    entities = []
    for entry in list_values(optional(source, "Entities", lst(10, []))):
        xyz = [v.value for v in list_values(field(entry, "Pos"))]
        if len(xyz) != 3:
            raise ValueError("Invalid schematic entity position")
        data = optional(entry, "Data", c())
        entity_nbt = tag(10, {**data.value, "id": field(entry, "Id", 8)})
        entities.append(c(pos=lst(6, [tag(6, float(v)) for v in xyz]),
                          blockPos=lst(3, [i(math.floor(v)) for v in xyz]), nbt=entity_nbt))
    return size, blocks, entities, field(source, "DataVersion", 3).value


def _pack_longs(indices: list[int], bits: int) -> list[int]:
    words = [0] * ((len(indices) * bits + 63) // 64)
    for index, value in enumerate(indices):
        bit = index * bits
        word, shift = divmod(bit, 64)
        words[word] |= value << shift
        if shift + bits > 64:
            words[word + 1] |= value >> (64 - shift)
    return [v - (1 << 64) if v >= (1 << 63) else v for v in words]


def to_litematic(structure: Tag, name: str) -> Tag:
    size, blocks, entities, data_version = structure_model(structure)
    states = ["minecraft:air"] + sorted(
        ({state for state, _ in blocks.values()} | {"minecraft:structure_void"}) - {"minecraft:air"})
    ids = {state: index for index, state in enumerate(states)}
    bits = max(2, (len(states) - 1).bit_length())
    values = [ids[blocks[pos][0] if pos in blocks else "minecraft:structure_void"] for pos in cells(size)]
    tiles = []
    for pos, (_, extra) in sorted(blocks.items()):
        if extra:
            tiles.append(tag(10, {**extra.value, "x": i(pos[0]), "y": i(pos[1]), "z": i(pos[2])}))
    litematic_entities = []
    for entry in entities:
        entity_nbt = field(entry, "nbt", 10)
        litematic_entities.append(tag(10, {**entity_nbt.value, "Pos": field(entry, "pos")}))
    x, y, z = size
    region = c(Position=c(x=i(0), y=i(0), z=i(0)), Size=c(x=i(x), y=i(y), z=i(z)),
               BlockStatePalette=lst(10, [litematic_state_nbt(v) for v in states]),
               BlockStates=arr(12, _pack_longs(values, bits)), TileEntities=lst(10, tiles),
               Entities=lst(10, litematic_entities), PendingBlockTicks=lst(10, []), PendingFluidTicks=lst(10, []))
    return c(Version=i(6), SubVersion=i(1), MinecraftDataVersion=i(data_version),
             Metadata=c(Name=s(name), Author=s("Second Chance"), Description=s("Editable build source"),
                        RegionCount=i(1), TotalBlocks=i(len(blocks)), TotalVolume=i(x * y * z),
                        EnclosingSize=c(x=i(x), y=i(y), z=i(z))),
             Regions=tag(10, {name: region}),
             GemsExplicitVoid=arr(7, explicit_void_bits(size, blocks)))


def _litematic_model(root: Tag):
    regions = field(root, "Regions", 10).value
    if not regions:
        raise ValueError("Litematic has no regions")
    decoded = []
    for name, region in sorted(regions.items()):
        size_raw = field(region, "Size", 10)
        signed = [field(size_raw, axis).value for axis in ("x", "y", "z")]
        if any(v < 1 for v in signed):
            raise ValueError(f"{name}: negative/zero Litematic region size needs re-orienting in the editor")
        size = positive_size(signed)
        origin_raw = field(region, "Position", 10)
        origin = tuple(field(origin_raw, axis).value for axis in ("x", "y", "z"))
        palette = [litematic_state_string(v) for v in list_values(field(region, "BlockStatePalette"))]
        bits = max(2, (len(palette) - 1).bit_length())
        longs = field(region, "BlockStates", 12).value
        mask = (1 << bits) - 1
        local_blocks = {}
        for index, pos in enumerate(cells(size)):
            bit = index * bits
            word, shift = divmod(bit, 64)
            if word >= len(longs):
                raise ValueError(f"{name}: truncated Litematic BlockStates")
            value = (longs[word] & ((1 << 64) - 1)) >> shift
            if shift + bits > 64:
                if word + 1 >= len(longs):
                    raise ValueError(f"{name}: truncated Litematic BlockStates")
                value |= (longs[word + 1] & ((1 << 64) - 1)) << (64 - shift)
            value &= mask
            if value >= len(palette):
                raise ValueError(f"{name}: unknown Litematic palette index {value}")
            state = palette[value]
            if state != "minecraft:structure_void":
                local_blocks[pos] = (state, None)
        for extra in list_values(optional(region, "TileEntities", lst(10, []))):
            pos = tuple(field(extra, axis).value for axis in ("x", "y", "z"))
            if pos not in local_blocks:
                raise ValueError(f"{name}: tile entity at {pos} has no block")
            local_blocks[pos] = (local_blocks[pos][0], tag(10, {k: v for k, v in extra.value.items() if k not in ("x", "y", "z")}))
        local_entities = list_values(optional(region, "Entities", lst(10, [])))
        for entity in local_entities:
            if entity.kind != 10 or "id" not in entity.value or "Pos" not in entity.value:
                raise ValueError(f"{name}: entity lacks id or Pos")
        decoded.append((name, origin, size, local_blocks, local_entities))
    mins = tuple(min(origin[axis] for _, origin, _, _, _ in decoded) for axis in range(3))
    maxs = tuple(max(origin[axis] + size[axis] for _, origin, size, _, _ in decoded) for axis in range(3))
    combined_size = positive_size(maxs[axis] - mins[axis] for axis in range(3))
    blocks = {}
    entities = []
    for name, origin, _, local_blocks, local_entities in decoded:
        for pos, block in local_blocks.items():
            translated = tuple(origin[axis] + pos[axis] - mins[axis] for axis in range(3))
            if translated in blocks and blocks[translated] != block:
                raise ValueError(f"{name}: overlapping Litematic regions disagree at {translated}")
            blocks[translated] = block
        shift = tuple(origin[axis] - mins[axis] for axis in range(3))
        for entity in local_entities:
            xyz = [v.value for v in list_values(field(entity, "Pos"))]
            if len(xyz) != 3:
                raise ValueError(f"{name}: invalid entity Pos")
            placed = [xyz[axis] + shift[axis] for axis in range(3)]
            extra = {k: v for k, v in entity.value.items() if k != "Pos"}
            for axis, key in enumerate(("TileX", "TileY", "TileZ")):
                if key in extra:
                    extra[key] = i(field(entity, key).value + shift[axis])
            entities.append(c(pos=lst(6, [tag(6, float(v)) for v in placed]),
                              blockPos=lst(3, [i(math.floor(v)) for v in placed]),
                              nbt=tag(10, extra)))
    restore_explicit_voids(root, combined_size, blocks)
    return combined_size, blocks, entities, field(root, "MinecraftDataVersion", 3).value


def import_source(path: Path, known_ids: set[str]) -> Tag:
    root = read_nbt(path)
    if path.suffix == ".schem":
        size, blocks, entities, data_version = _schem_model(root)
    elif path.suffix == ".litematic":
        size, blocks, entities, data_version = _litematic_model(root)
    else:
        raise ValueError(f"Unsupported editable format: {path}")
    validate_states({state for state, _ in blocks.values()}, known_ids)
    if data_version != DATA_VERSION:
        raise ValueError(f"{path}: source data version {data_version} != Minecraft 26.3 ({DATA_VERSION}); migrate in-game first")
    return make_structure(size, blocks, entities, data_version)


def _local_id(raw: str) -> str | None:
    if raw == "minecraft:empty":
        return None
    if not raw.startswith("gems:") or not ID.fullmatch(raw):
        raise ValueError(f"Unexpected worldgen reference {raw}")
    return raw.split(":", 1)[1]


def inventory() -> dict:
    """Follow placed structure sets, root pools and all NBT jigsaw child pools."""
    worldgen = DATA / "worldgen"
    structures = set()
    for path in sorted((worldgen / "structure_set").glob("*.json")):
        structure_set = json.loads(path.read_text(encoding="utf-8"))
        if structure_set.get("placement", {}).get("frequency", 1) <= 0:
            continue
        for item in structure_set["structures"]:
            local = _local_id(item["structure"])
            if local:
                structures.add(local)
    pool_queue = deque()
    for name in sorted(structures):
        path = worldgen / "structure" / f"{name}.json"
        if not path.is_file():
            raise ValueError(f"Missing structure definition: {path}")
        root = json.loads(path.read_text(encoding="utf-8"))
        local = _local_id(root["start_pool"])
        if local:
            pool_queue.append(local)
    pools, templates = set(), set()
    while pool_queue:
        name = pool_queue.popleft()
        if name in pools:
            continue
        path = worldgen / "template_pool" / f"{name}.json"
        if not path.is_file():
            raise ValueError(f"Missing template pool: {path}")
        pools.add(name)
        data = json.loads(path.read_text(encoding="utf-8"))
        fallback = _local_id(data.get("fallback", "minecraft:empty"))
        if fallback:
            pool_queue.append(fallback)
        for choice in data.get("elements", []):
            element = choice["element"]
            if element.get("element_type") != "minecraft:single_pool_element":
                raise ValueError(f"Unsupported pool element in {path}: {element.get('element_type')}")
            local = _local_id(element["location"])
            if local is None or local in templates:
                continue
            source = DATA / "structure" / f"{local}.nbt"
            if not source.is_file():
                raise ValueError(f"Missing structure template: {source}")
            templates.add(local)
            _, blocks, _, _ = structure_model(read_nbt(source))
            for state, extra in blocks.values():
                if state.split("[", 1)[0] != "minecraft:jigsaw":
                    continue
                if extra is None:
                    raise ValueError(f"Jigsaw in {source} lacks block entity data")
                child = _local_id(field(extra, "pool", 8).value)
                if child:
                    pool_queue.append(child)
    return {"structure_sets": len(list((worldgen / "structure_set").glob("*.json"))),
            "structures": sorted(structures), "pools": sorted(pools), "templates": sorted(templates)}


def reachable_manifest_pieces(entries: dict) -> set[str]:
    """Cheap reachability check using reviewed connector metadata, not parsed NBT."""
    worldgen = DATA / "worldgen"
    pools = deque()
    for path in (worldgen / "structure_set").glob("*.json"):
        structure_set = json.loads(path.read_text(encoding="utf-8"))
        if structure_set.get("placement", {}).get("frequency", 1) <= 0:
            continue
        for choice in structure_set["structures"]:
            structure = _local_id(choice["structure"])
            if structure:
                definition = json.loads((worldgen / "structure" / f"{structure}.json").read_text(encoding="utf-8"))
                root_pool = _local_id(definition["start_pool"])
                if root_pool:
                    pools.append(root_pool)
    seen_pools, pieces = set(), set()
    while pools:
        pool = pools.popleft()
        if pool in seen_pools:
            continue
        seen_pools.add(pool)
        path = worldgen / "template_pool" / f"{pool}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        fallback = _local_id(data.get("fallback", "minecraft:empty"))
        if fallback:
            pools.append(fallback)
        for choice in data.get("elements", []):
            piece = _local_id(choice["element"]["location"])
            if piece is None:
                continue
            pieces.add(piece)
            if piece not in entries:
                continue
            for connector in entries[piece]["markers"]["jigsaws"]:
                child = _local_id(connector[3])
                if child:
                    pools.append(child)
    return pieces


def markers(structure: Tag) -> dict:
    _, blocks, entities, _ = structure_model(structure)
    for state, extra in blocks.values():
        if state.split("[", 1)[0] == "minecraft:jigsaw" and extra is None:
            raise ValueError("Jigsaw block is missing its required connector data")
    jigsaws = sorted([list(pos) + [field(extra, "pool", 8).value]
                      for pos, (state, extra) in blocks.items()
                      if state.split("[", 1)[0] == "minecraft:jigsaw" and extra is not None])
    block_entities = sorted([list(pos) + [field(extra, "id", 8).value]
                             for pos, (_, extra) in blocks.items() if extra is not None])
    entity_places = sorted([[field(field(entity, "nbt", 10), "id", 8).value,
                             list_values(field(entity, "pos"))[0].value,
                             list_values(field(entity, "pos"))[1].value,
                             list_values(field(entity, "pos"))[2].value]
                            for entity in entities])
    return {"jigsaws": jigsaws, "block_entities": block_entities,
            "entities": entity_places}


def bootstrap(names: list[str]) -> None:
    SOURCES.mkdir(parents=True, exist_ok=True)
    entries = {}
    known = set()
    for name in names:
        source = DATA / "structure" / f"{name}.nbt"
        structure = read_nbt(source)
        _, blocks, _, version = structure_model(structure)
        if version != DATA_VERSION:
            raise ValueError(f"{source}: data version {version} != {DATA_VERSION}")
        known.update(state.split("[", 1)[0] for state, _ in blocks.values())
        target = SOURCES / f"{name}.schem"
        write_nbt(target, to_schem(structure))
        entries[name] = {"source": target.name, "markers": markers(structure)}
    (SOURCES / "known_blocks.txt").write_text(
        "# Verified 26.3 block IDs from the initial campaign structures.\n"
        "# Add a new ID only after checking its 26.3 registry name.\n" +
        "\n".join(sorted(known)) + "\n", encoding="utf-8")
    MANIFEST.write_text(json.dumps({"minecraft": "26.3", "pieces": entries}, indent=2) + "\n", encoding="utf-8")
    print(f"Exported {len(entries)} editable .schem sources to {SOURCES}")


def normalize_source_names() -> None:
    """Give artists stable source names while packaged template IDs are still in use."""
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    planned = {}
    for name, spec in manifest["pieces"].items():
        source = SOURCES / spec["source"]
        canonical = re.sub(r"_v[0-9]+(?=(_[0-9]+)*$)", "", source.stem) + source.suffix
        if canonical in planned and planned[canonical] != name:
            raise ValueError(f"Canonical schematic name collision: {planned[canonical]} and {name} -> {canonical}")
        planned[canonical] = name
    for canonical, name in sorted(planned.items()):
        spec = manifest["pieces"][name]
        source = SOURCES / spec["source"]
        target = SOURCES / canonical
        if source != target:
            if target.exists():
                raise ValueError(f"Refusing to overwrite existing source {target}")
            source.rename(target)
            spec["source"] = target.name
    MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Named {len(planned)} editable sources without packaged version suffixes")


def build() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if manifest.get("minecraft") != "26.3":
        raise ValueError("Manifest must target Minecraft 26.3")
    entries = manifest["pieces"]
    reachable = reachable_manifest_pieces(entries)
    if reachable != set(entries):
        raise ValueError(f"Schematic manifest must cover exactly the reachable pieces; "
                         f"missing={sorted(reachable - set(entries))}, "
                         f"inactive={sorted(set(entries) - reachable)}")
    known = read_known_ids()
    known_hash = hashlib.sha256((SOURCES / "known_blocks.txt").read_bytes()).hexdigest()
    script_hash = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    cache_path = ROOT / "build/schematic-cache.json"
    cache = json.loads(cache_path.read_text(encoding="utf-8")) if cache_path.is_file() else {}
    updated_cache = {}
    staged = []
    for name, spec in sorted(entries.items()):
        if not re.fullmatch(r"[a-z0-9_]+", name):
            raise ValueError(f"Invalid structure name {name}")
        source = SOURCES / spec["source"]
        if source.parent != SOURCES or source.suffix not in (".schem", ".litematic"):
            raise ValueError(f"Invalid schematic path {source}")
        output = DATA / "structure" / f"{name}.nbt"
        fingerprint = hashlib.sha256(source.read_bytes() + known_hash.encode() +
                                     script_hash.encode() +
                                     json.dumps(spec["markers"], sort_keys=True).encode()).hexdigest()
        previous = cache.get(name, {})
        if (previous.get("fingerprint") == fingerprint and output.is_file() and
                hashlib.sha256(output.read_bytes()).hexdigest() == previous.get("output_sha256")):
            updated_cache[name] = previous
            continue
        structure = import_source(source, known)
        if markers(structure) != spec["markers"]:
            raise ValueError(f"{source}: required jigsaw or entity marker changed; update its manifest entry deliberately")
        packed = encode_nbt(structure)
        staged.append((output, packed))
        updated_cache[name] = {"fingerprint": fingerprint,
                               "output_sha256": hashlib.sha256(packed).hexdigest()}
    for path, packed in staged:
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.is_file() or path.read_bytes() != packed:
            path.write_bytes(packed)
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    cache_path.write_text(json.dumps(updated_cache, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Validated {len(entries)} editable pieces; regenerated {len(staged)} templates")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_subparsers(dest="action", required=True)
    actions.add_parser("inventory", help="Print reachable worldgen pieces from structure sets")
    start = actions.add_parser("bootstrap", help="Export reachable NBT pieces to canonical .schem sources")
    start.add_argument("--names", nargs="*", help="Explicit template names; default is reachable inventory")
    actions.add_parser("build", help="Regenerate NBT from manifest schematic sources")
    actions.add_parser("normalize", help="Remove packaged version suffixes from editable source names")
    export = actions.add_parser("export", help="Export one NBT as .schem or .litematic")
    export.add_argument("source", type=Path)
    export.add_argument("output", type=Path)
    convert = actions.add_parser("import", help="Import one .schem/.litematic to a Minecraft NBT template")
    convert.add_argument("source", type=Path)
    convert.add_argument("output", type=Path)
    args = parser.parse_args()
    if args.action == "inventory":
        print(json.dumps(inventory(), indent=2))
    elif args.action == "bootstrap":
        bootstrap(sorted(args.names or inventory()["templates"]))
    elif args.action == "build":
        build()
    elif args.action == "normalize":
        normalize_source_names()
    elif args.action == "export":
        structure = read_nbt(args.source)
        if args.output.suffix == ".schem":
            write_nbt(args.output, to_schem(structure))
        elif args.output.suffix == ".litematic":
            write_nbt(args.output, to_litematic(structure, args.source.stem))
        else:
            raise ValueError("Export output must end in .schem or .litematic")
    elif args.action == "import":
        write_nbt(args.output, import_source(args.source, read_known_ids()))


if __name__ == "__main__":
    main()
