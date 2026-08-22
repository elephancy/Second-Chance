"""Draw original 16-pixel stage glyphs for Cadenza's act telegraphs.

No downloaded or AI-generated source is used. The small, solid silhouettes are
deliberately different so the warning is legible without relying on color.
Run from the repository root with `python tools/adventure/generate_cadenza_marks.py`.
"""

from pathlib import Path
import struct
import zlib


OUT = Path("src/main/resources/assets/gems/textures/particle")
COLORS = {
    "spotlight": (195, 139, 235, 255),
    "crosscut": (239, 112, 104, 255),
    "chorus": (128, 220, 232, 255),
    "lock": (248, 210, 105, 255),
}
WHITE = (252, 248, 233, 255)


def line(pixels, x0, y0, x1, y1, color):
    dx, dy = abs(x1 - x0), abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    error = dx - dy
    while True:
        if 0 <= x0 < 16 and 0 <= y0 < 16:
            pixels[y0][x0] = color
        if (x0, y0) == (x1, y1):
            break
        twice = 2 * error
        if twice > -dy:
            error -= dy
            x0 += sx
        if twice < dx:
            error += dx
            y0 += sy


def dot(pixels, x, y, color):
    for yy in (y, y + 1):
        for xx in (x, x + 1):
            pixels[yy][xx] = color


def glyph(kind):
    pixels = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]
    color = COLORS[kind]
    if kind == "spotlight":
        # An eye/spotlight enclosing a fixed target.
        for a, b in (((2, 7), (5, 4)), ((5, 4), (10, 4)),
                     ((10, 4), (13, 7)), ((13, 8), (10, 11)),
                     ((10, 11), (5, 11)), ((5, 11), (2, 8))):
            line(pixels, *a, *b, color)
        dot(pixels, 7, 7, WHITE)
        line(pixels, 7, 1, 8, 1, color)
        line(pixels, 7, 14, 8, 14, color)
    elif kind == "crosscut":
        # Two intersecting cuts, with open ends distinct from the lock square.
        for offset in (0, 1):
            line(pixels, 3, 3 + offset, 12, 12 + offset, color)
            line(pixels, 12, 3 + offset, 3, 12 + offset, color)
        dot(pixels, 7, 7, WHITE)
    elif kind == "chorus":
        # Three voices, one at the center and two to either side.
        for x, top in ((3, 6), (7, 3), (11, 6)):
            dot(pixels, x, top, WHITE)
            line(pixels, x, top + 3, x, 12, color)
            line(pixels, x + 1, top + 3, x + 1, 12, color)
        line(pixels, 2, 13, 13, 13, color)
    else:
        # A closed bracket/square: the pattern has stopped following the player.
        line(pixels, 3, 3, 12, 3, color)
        line(pixels, 12, 3, 12, 12, color)
        line(pixels, 12, 12, 3, 12, color)
        line(pixels, 3, 12, 3, 3, color)
        line(pixels, 6, 6, 9, 9, WHITE)
        line(pixels, 9, 6, 6, 9, WHITE)
    return pixels


def png_bytes(pixels):
    # Nearest-neighbor 2x scale keeps hard Minecraft-sized pixels.
    raw = bytearray()
    for y in range(32):
        raw.append(0)  # PNG filter: none.
        for x in range(32):
            raw.extend(pixels[y // 2][x // 2])
    def chunk(tag, body):
        return (struct.pack(">I", len(body)) + tag + body
                + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF))
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", 32, 32, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for kind in COLORS:
        (OUT / f"cadenza_{kind}.png").write_bytes(png_bytes(glyph(kind)))


if __name__ == "__main__":
    main()
