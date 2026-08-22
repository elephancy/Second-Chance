"""Print a small, read-only summary of an NBT or Sponge schematic file."""

from __future__ import annotations

import gzip
import json
import struct
import sys
from collections import Counter
from pathlib import Path


class Reader:
    def __init__(self, data: bytes):
        self.data = memoryview(data)
        self.pos = 0

    def take(self, count: int) -> bytes:
        if count < 0 or self.pos + count > len(self.data):
            raise ValueError("Truncated or invalid NBT")
        result = self.data[self.pos:self.pos + count].tobytes()
        self.pos += count
        return result

    def number(self, format_: str):
        return struct.unpack(">" + format_, self.take(struct.calcsize(format_)))[0]

    def text(self) -> str:
        return self.take(self.number("H")).decode("utf-8")

    def payload(self, kind: int):
        if kind in (1, 2, 3, 4, 5, 6):
            return self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 7:
            return self.take(self.number("i"))
        if kind == 8:
            return self.text()
        if kind == 9:
            child, count = self.number("B"), self.number("i")
            if count < 0:
                raise ValueError("Negative NBT list length")
            return [self.payload(child) for _ in range(count)]
        if kind == 10:
            result = {}
            while (child := self.number("B")) != 0:
                key = self.text()
                if key in result:
                    raise ValueError(f"Duplicate NBT key: {key}")
                result[key] = self.payload(child)
            return result
        if kind in (11, 12):
            count = self.number("i")
            if count < 0:
                raise ValueError("Negative NBT array length")
            format_ = "i" if kind == 11 else "q"
            return [self.number(format_) for _ in range(count)]
        raise ValueError(f"Unsupported NBT tag: {kind}")


def read(path: Path) -> dict:
    data = path.read_bytes()
    if data.startswith(b"\x1f\x8b"):
        data = gzip.decompress(data)
    if len(data) > 100_000_000:
        raise ValueError("Schematic is too large for this inspector")
    reader = Reader(data)
    if reader.number("B") != 10:
        raise ValueError("Expected an NBT compound root")
    root_name = reader.text()
    result = reader.payload(10)
    if reader.pos != len(data):
        raise ValueError("Trailing bytes after NBT root")
    return {"root_name": root_name, "value": result}


def summary(path: Path) -> dict:
    parsed = read(path)
    value = parsed["value"]
    schematic = value.get("Schematic", value)
    blocks = schematic.get("Blocks", schematic)
    palette = blocks.get("Palette", schematic.get("Palette", {}))
    result = {
        "file": str(path),
        "root_name": parsed["root_name"],
        "version": schematic.get("Version"),
        "data_version": schematic.get("DataVersion"),
        "size": [schematic.get(key) for key in ("Width", "Height", "Length")],
        "palette_count": len(palette),
        "block_data_bytes": len(blocks.get("Data", schematic.get("BlockData", b""))),
        "entities": len(schematic.get("Entities", [])),
        "block_entities": len(blocks.get("BlockEntities", schematic.get("BlockEntities", []))),
        "palette": sorted(palette),
    }
    result["palette_namespaces"] = dict(Counter(name.split(":", 1)[0] for name in palette))
    data = blocks.get("Data", schematic.get("BlockData", b""))
    if data and palette:
        indices = []
        value, shift = 0, 0
        for byte in data:
            value |= (byte & 0x7F) << shift
            if byte & 0x80:
                shift += 7
                if shift > 28:
                    raise ValueError("Invalid schematic palette index")
            else:
                indices.append(value)
                value, shift = 0, 0
        if shift:
            raise ValueError("Truncated schematic palette index")
        width, height, length = result["size"]
        if len(indices) != width * height * length:
            raise ValueError("Schematic block count does not match dimensions")
        by_id = {index: name for name, index in palette.items()}
        counts = Counter(by_id[index].split("[", 1)[0] for index in indices)
        result["most_common_blocks"] = counts.most_common(20)
        result["non_air_blocks"] = len(indices) - counts["minecraft:air"]
        occupied = [(index % width, index // (width * length),
                     (index // width) % length)
                    for index, block in enumerate(indices)
                    if by_id[block] != "minecraft:air"]
        if occupied:
            result["occupied_bounds"] = [
                [min(point[axis] for point in occupied),
                 max(point[axis] for point in occupied)] for axis in range(3)
            ]
    return result


if __name__ == "__main__":
    print(json.dumps(summary(Path(sys.argv[1])), indent=2))
