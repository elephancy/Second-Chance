"""Deterministic 32px Art Deco scoreboard tile, drawn from simple geometry.

This source creates its own pixels; it uses no generative model or outside art.
"""

from pathlib import Path
import struct
import zlib

SIZE = 32
OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/textures/block/townhall_tournament_board.png"
INK = (13, 37, 46, 255)
TEAL = (24, 80, 85, 255)
GOLD = (211, 154, 68, 255)
LIGHT = (250, 219, 145, 255)
SCORE = (73, 172, 157, 255)
pixels = [[INK for _ in range(SIZE)] for _ in range(SIZE)]


def rect(x0, y0, x1, y1, color):
    for y in range(max(0, y0), min(SIZE, y1 + 1)):
        for x in range(max(0, x0), min(SIZE, x1 + 1)):
            pixels[y][x] = color


rect(1, 1, 30, 30, GOLD)
rect(3, 3, 28, 28, INK)
rect(5, 5, 26, 26, TEAL)
rect(7, 7, 24, 24, INK)
rect(10, 2, 21, 4, LIGHT)
rect(12, 4, 19, 6, GOLD)
rect(14, 6, 17, 8, LIGHT)
for y in (11, 16, 21):
    rect(10, y, 21, y + 1, SCORE)
    rect(10, y + 2, 18, y + 2, GOLD)
for x in (2, 27):
    for y in (8, 13, 18, 23):
        rect(x, y, x + 2, y + 1, LIGHT)
rect(13, 28, 18, 29, LIGHT)


def chunk(name, data):
    return (struct.pack(">I", len(data)) + name + data
            + struct.pack(">I", zlib.crc32(name + data) & 0xffffffff))


raw = b"".join(b"\x00" + b"".join(bytes(pixel) for pixel in row) for row in pixels)
png = (b"\x89PNG\r\n\x1a\n"
       + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
       + chunk(b"IDAT", zlib.compress(raw, level=9))
       + chunk(b"IEND", b""))
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(png)
print(OUT)
