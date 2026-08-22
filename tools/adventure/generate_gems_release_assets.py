"""Build first-party Gems art, import proven source sprites, and synthesize audio.

RunicPixels weapons and Karsiori gems are pinned to their original archives.
Use --check to verify the shipped assets.
"""

from __future__ import annotations

import argparse
import hashlib
import math
import random
import shutil
import struct
import subprocess
import zlib
from pathlib import Path
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "gems/src/main/resources/assets/gems"
INK = (20, 20, 39, 255)
GOLD = (240, 196, 104, 255)
WHITE = (249, 244, 231, 255)
CC0_EXTERNAL = {
    "textures/item/items/energy_upgrade.png": "72992f148db2c5c3b210fc344e911f5b73e0f14b74357a196847eeef08a07f05",
    "textures/item/items/gem_augment_focus.png": "3847d6d981e2e0d57807dbdc212a94895638c0b0514f2c49465598f5e5ac506c",
    "textures/item/items/gem_augment_persistence.png": "dcce155e10143ca8638a09e36a453dc1cf9de862c61280654d486e7a444d04f9",
    "textures/item/items/gem_augment_resonance.png": "6fd616216ab3d7e81e4a03fd7e8ecd2f8652b18f49f80a449846e121f4bda86d",
    "textures/item/items/gem_purchase.png": "8b25ab4b6c9fc8bb3aff7ebbb4bbc3bda1260fb926323bf54828cfbe9cb97e13",
    "textures/item/items/gem_trader.png": "122446e71732d093d47160115bf66ba08e89865ee145a10429df77ef9b95665a",
    "textures/item/items/legendary_inscription_edge.png": "6b109a652dad341ea28276132c74c554741fcf2c0f2370c6871525dc2294a99a",
    "textures/item/items/legendary_inscription_swift.png": "d8bef2400074ae927e1cf4a5237679dc5df766e48ff2f60f8f65f7b890588ee7",
    "textures/item/items/legendary_inscription_ward.png": "126f11f0d2b79c607b314fc2ee6fd0854540fb11de34e7f2dddea31c9b9c8bfa",
    "textures/particle/gem_ring.png": "d937750e953d62397717c05fb165e44b2d2cd8286a548aedfcd82b7f11e43e2b",
    "textures/particle/gem_star.png": "a7beb944d7fbb86f8cfa3f8ff0f31889cf03ac93e57b9112374e88ae8ba24fa6",
    "sounds/arcane_chime.ogg": "84a70f63f3f21e5ba4a48285b760a663d422ad3feddba2f3cbadd94b76cdc52e",
    "sounds/phase_shift.ogg": "0a18ee3460cf3fd79c3771bbf94c20c26d34b65ab60d0b0e15694ffcad815955",
    "sounds/surge_pulse.ogg": "9fac648a1de869cf9ca2f4b3f1cd47f1766a3ee9d2fdec45831234401d7b2392",
}

# Exact pixel matches against the retired project sprites. Unmatched sprites
# have no inferred provenance and remain first-party replacements.
RUNIC_ARCHIVE = ROOT / "tools/assets/runicpixels-swordtember-2023.zip"
RUNIC_ARCHIVE_SHA256 = "2ffa606b7506d50fec41a3d17c99a55b3ee03fd5068953b6cd8f67331e45930c"
RUNIC_SWORDTEMBER = {
    "textures/item/legendary/weapons/blood_oath_blade.png": "SwordtemberSword27.png",
    "textures/item/legendary/weapons/demolition_blade.png": "SwordtemberSword7.png",
    "textures/item/legendary/weapons/duelists_rapier.png": "SwordtemberSword18.png",
    "textures/item/legendary/weapons/experience_blade.png": "SwordtemberSword1.png",
    "textures/item/legendary/weapons/third_strike_blade.png": "SwordtemberSword30.png",
    "textures/item/legendary/weapons/vampiric_edge.png": "SwordtemberSword22.png",
    "textures/item/legendary/tools/challengers_gauntlet.png": "SwordtemberSword5a.png",
    "textures/item/legendary/tools/earthsplitter_pick.png": "SwordtemberSword8.png",
    "textures/item/legendary/tools/gladiators_mark.png": "SwordtemberSword20.png",
    "textures/item/legendary/tools/hunters_trophy_necklace.png": "SwordtemberSword17.png",
    "textures/item/legendary/tools/reversal_mirror.png": "SwordtemberSword10.png",
    "textures/item/legendary/tools/soul_shackle.png": "SwordtemberSword19.png",
}


KARSIORI_ARCHIVE = ROOT / "tools/assets/karsiori-pixel-art-gems-animated.zip"
KARSIORI_ARCHIVE_SHA256 = "cb72c6994fb45128c400db2b5922f0a0827e6ae6f2737bcbbc132a48ddf05a41"
# The source art is never redrawn. import_karsiori_gems.py centers the original
# RGBA frame on a transparent 32px canvas without changing a source pixel.
KARSIORI_GEMS = {
    "air": (1, "TURQUOISE", "4e31fefcdb862d790e475e6acb836db452b846df27dba671d3c5fb297df6123e"),
    "astra": (3, "LILAC", "2a0cc2a1b6d8057b7600fcf08a9fc310e751b1c0204ee5428febe7c4e7a5d8fd"),
    "beacon": (5, "GOLD", "0498f5c125b6e1288fda753c2660d613e7a1a06755a451e640f39b2c1b48a606"),
    "chaos": (9, "LILAC", "1c8eb567c2d8764190e82b3e7ad4d2a7f6f91271c3db347438331cb0d0bb9e92"),
    "duelist": (4, "DARK BLUE", "fa48d7c02fb814e6e540d7650dae0ea9623465ea3d28f530ee9395006bd294e8"),
    "fire": (7, "RED", "55e1cd2a3ed2828d0ed47a7287465b0aa0d3dd3283a9bd203dae2adc0f94f8c8"),
    "flux": (2, "PURPLE", "b9adf234043031f5cd4549b3d6ce0b22e4af43dbae71c4926041a23611b4ab55"),
    "hunter": (1, "LIGHT GREEN", "3df17f7dbed337cc9be386c0beae976c6ebb0568c547b3e8331fec6d9a23bd89"),
    "life": (6, "LIGHT GREEN", "e1be4b4ed2d491b33140a5a07792633d88958637dd73af767f0613845526ddab"),
    "pillager": (8, "RED", "b1be3ce391679e6819f4409ad857e049725e9179e2e66326ee17fcc6d96580d1"),
    "prism": (3, "TURQUOISE", "519388917b0903643a1d131fc69ad23987bbccf1b6aabc204d37c597d8f6fb36"),
    "puff": (5, "LILAC", "03df67b50eec2db4668f34945ccb74fe446444934e89d9f49ee0428b6e1c9d44"),
    "reaper": (4, "PURPLE", "5445ecc391f39b395934d3138e943ff3459bdafcbcb2edd46ae46c3371e5eb9d"),
    "sentinel": (8, "BLUE", "a01e1eece2754b04ad3b2715ca14371bf76c0080408af64e9dd4a986446612b9"),
    "space": (9, "DARK BLUE", "078ba337c7c76d58054a66ccd3725682db9048ef840059d4cb5bf1f3447b4e76"),
    "speed": (1, "GOLD", "e6954d02e28026fb642461d853b1e091b7bff89ca5bac21a69d6fe4a889f47ff"),
    "spy": (7, "DARK BLUE", "a8ff8eb54740931d66371321370cc37096ffde3f44253b7296a24d77dafb8ced"),
    "strength": (4, "RED", "46813248830900a52243c6367063026081d2aab95214bcea1b2be99427655371"),
    "summoner": (6, "TURQUOISE", "ffdf34f0744d392386916bdaa781c125c5e5d18898112a77003e80255cc1e1ee"),
    "terror": (8, "PURPLE", "83a61f5473073b335b2c2922aea658806f955f161eaca3ce3e9a8744fc08ba71"),
    "trickster": (2, "GOLD", "81dacd2c330c47e5a40880588c243dfbd1ca1a62ffd0188bad08c90cdf98f746"),
    "void": (6, "PURPLE", "21fde96f5080d1ad7003be886f5f47badf8bc5905bfbcc8b2973bcc4a36dea43"),
    "wealth": (3, "GOLD", "7f756c032f54ba3e25ccedb60533a2496e0446273e6d82e0a04142d376bf2a55"),
}


def verify_karsiori_assets() -> None:
    if hashlib.sha256(KARSIORI_ARCHIVE.read_bytes()).hexdigest() != KARSIORI_ARCHIVE_SHA256:
        raise ValueError("Karsiori source archive hash changed")
    with ZipFile(KARSIORI_ARCHIVE) as source:
        for name, (number, color, digest) in KARSIORI_GEMS.items():
            member = (f"Pixel Art Gem Pack - Animated/GEM {number}/{color}/"
                      f"GEM {number} - {color} - 0000.png")
            if not source.read(member).startswith(b"\x89PNG\r\n\x1a\n"):
                raise ValueError(f"Karsiori source frame changed: {member}")
            path = ASSETS / f"textures/item/gems/{name}_gem.png"
            data = path.read_bytes()
            if hashlib.sha256(data).hexdigest() != digest:
                raise ValueError(f"Karsiori gem asset changed: {path}")
            if struct.unpack(">II", data[16:24]) != (32, 32):
                raise ValueError(f"Karsiori gem must be 32x32: {path}")


SOURCE_LEGENDARY_ARCHIVES = {
    "ranged-7soul1_20201212.zip": "b6d556e5dee1e4fd77fcf8513a9c739d4cbd1c862233869317d420f58d3224e4",
    "tool-7soul1_20201222.zip": "4dfdd64ca9eda3cb81c37b7649fb4d5d31113934dd1436feaf338a4a007fd7fe",
    "jewelry-7soul1_20201212.zip": "7cd8d154048464d60f6f9bf82db6289950939dd4e36849b3fb028a498800155d",
    "stave-7soul1_20201212.zip": "f14c5c2f5acb6b4d95d6602fbe7eeb7702f46171545a96016812ac4af46bd0c6",
    "tool-ocal_20230225.zip": "dc5e13999c0d419f82b0addfd35ca3dfdceb1831160a86e44f057cf4737ee8f7",
}
SOURCE_LEGENDARY_HASHES = {
    "textures/item/legendary/tools/chrono_charm.png": "0a6c71c9ae21ec6ca42fc4c2e1d00f8db033d0e4e349dd823e457d50fe9d4117",
    "textures/item/legendary/tools/gem_seer.png": "58c36272003c9e2fb21af8590447540dc6cff40dc1892e3225e62f09fd2b2238",
    "textures/item/legendary/tools/hypno_staff.png": "1b1a96edcf3bb0316b4370223c6029913ba3f57d4316a2587e6a1e1f571f28f2",
    "textures/item/legendary/tools/recall_relic.png": "7ad8b6b22a3adeb161ec1d568f8192ef7f199edc53e81155974f018efd8afce1",
    "textures/item/legendary/tools/tracker_compass.png": "a5556317613fbad85328f4bf4a1fd53342ecd4287ac98db831b99fcc5aacb6a9",
    "textures/item/legendary/weapons/hunters_sight_bow.png": "8213ee2721f6d460826137243fb029d7eda31711894311e31831740bdec99c3e",
    "textures/item/legendary/weapons/hunters_sight_bow_pulling_0.png": "34e99612dca5aafc059a1d7687f06c92aa40f0893a6a348153981cb6f0b982f5",
    "textures/item/legendary/weapons/hunters_sight_bow_pulling_1.png": "d37429ef5c4b84715c4f84772c1c1a72476d2346e28c1572d538b0fea9d0ee7d",
    "textures/item/legendary/weapons/hunters_sight_bow_pulling_2.png": "7e1d2d17c76a43d913cd653a2e99c051c1ddd355c4496397a2041a7abf5f2c7a",
}


def verify_legendary_sources() -> None:
    for name, digest in SOURCE_LEGENDARY_ARCHIVES.items():
        path = ROOT / "tools/assets" / name
        if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError(f"Legendary source archive changed: {path}")
    for name, digest in SOURCE_LEGENDARY_HASHES.items():
        path = ASSETS / name
        data = path.read_bytes()
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError(f"Legendary source icon changed: {path}")
        if struct.unpack(">II", data[16:24]) != (32, 32):
            raise ValueError(f"Legendary source icon must be 32x32: {path}")


def runic_assets() -> dict[Path, bytes]:
    if hashlib.sha256(RUNIC_ARCHIVE.read_bytes()).hexdigest() != RUNIC_ARCHIVE_SHA256:
        raise ValueError("RunicPixels source archive hash changed")
    with ZipFile(RUNIC_ARCHIVE) as source:
        prefix = "SwordTember2023-RunicPixels/"
        return {ASSETS / target: source.read(prefix + original)
                for target, original in RUNIC_SWORDTEMBER.items()}


class Canvas:
    def __init__(self, size: int = 32) -> None:
        self.size = size
        self.pixels = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]

    def point(self, x: int, y: int, color: tuple[int, int, int, int]) -> None:
        if 0 <= x < self.size and 0 <= y < self.size:
            self.pixels[y][x] = color

    def polygon(self, vertices: list[tuple[int, int]], color: tuple[int, int, int, int]) -> None:
        for y in range(max(0, min(v[1] for v in vertices)),
                       min(self.size, max(v[1] for v in vertices) + 1)):
            for x in range(max(0, min(v[0] for v in vertices)),
                           min(self.size, max(v[0] for v in vertices) + 1)):
                inside = False
                for index, (ax, ay) in enumerate(vertices):
                    bx, by = vertices[(index + 1) % len(vertices)]
                    if (ay > y + 0.5) != (by > y + 0.5) and x + 0.5 < (
                            (bx - ax) * (y + 0.5 - ay) / (by - ay) + ax):
                        inside = not inside
                if inside:
                    self.point(x, y, color)

    def line(self, x0: int, y0: int, x1: int, y1: int,
             color: tuple[int, int, int, int]) -> None:
        steps = max(abs(x1 - x0), abs(y1 - y0))
        for step in range(steps + 1):
            self.point(round(x0 + (x1 - x0) * step / max(1, steps)),
                       round(y0 + (y1 - y0) * step / max(1, steps)), color)

    def disk(self, cx: int, cy: int, rx: int, ry: int,
             color: tuple[int, int, int, int], hollow: bool = False) -> None:
        for y in range(cy - ry, cy + ry + 1):
            for x in range(cx - rx, cx + rx + 1):
                distance = ((x - cx) / max(1, rx)) ** 2 + ((y - cy) / max(1, ry)) ** 2
                if distance <= 1 and (not hollow or distance >= 0.62):
                    self.point(x, y, color)

    def png(self) -> bytes:
        scan = b"".join(b"\x00" + b"".join(bytes(pixel) for pixel in row)
                        for row in self.pixels)
        def chunk(tag: bytes, value: bytes) -> bytes:
            return (struct.pack(">I", len(value)) + tag + value
                    + struct.pack(">I", zlib.crc32(tag + value) & 0xffffffff))
        return (b"\x89PNG\r\n\x1a\n"
                + chunk(b"IHDR", struct.pack(">IIBBBBB", self.size, self.size,
                                             8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(scan, level=9))
                + chunk(b"IEND", b""))


def facet(canvas: Canvas, outline: list[tuple[int, int]],
          top: tuple[int, int, int, int], left: tuple[int, int, int, int],
          right: tuple[int, int, int, int], core: tuple[int, int, int, int],
          center: tuple[int, int] = (16, 16)) -> None:
    canvas.polygon(outline, INK)
    inner = [(x + (1 if x < center[0] else -1 if x > center[0] else 0),
              y + (1 if y < center[1] else -1 if y > center[1] else 0))
             for x, y in outline]
    canvas.polygon(inner, core)
    upper = min(outline, key=lambda xy: xy[1])
    lower = max(outline, key=lambda xy: xy[1])
    west = min(outline, key=lambda xy: xy[0])
    east = max(outline, key=lambda xy: xy[0])
    canvas.polygon([upper, west, center], left)
    canvas.polygon([upper, east, center], top)
    canvas.polygon([west, lower, center], core)
    canvas.polygon([east, lower, center], right)
    if canvas.size == 32:
        # Inset light and a shaded lower lip make the large gems read as cut
        # stones at inventory size. The original silhouette and marks remain.
        plane = [row[:] for row in canvas.pixels]
        def blend(color: tuple[int, int, int, int], target: tuple[int, int, int, int],
                  weight: int) -> tuple[int, int, int, int]:
            return tuple((color[channel] * (8 - weight) + target[channel] * weight) // 8
                         for channel in range(3)) + (255,)
        for y in range(1, 31):
            for x in range(1, 31):
                color = plane[y][x]
                if color[3] == 0 or color == INK:
                    continue
                if x < center[0] and y < center[1]:
                    color = blend(color, WHITE, 1)
                elif x > center[0] and y > center[1]:
                    color = blend(color, INK, 2)
                if plane[y - 1][x] == INK or plane[y][x - 1] == INK:
                    color = blend(color, WHITE, 3)
                elif plane[y + 1][x] == INK or plane[y][x + 1] == INK:
                    color = blend(color, INK, 3)
                canvas.point(x, y, color)
    canvas.line(upper[0], upper[1] + 2, center[0], center[1], WHITE)


def dummy_egg() -> bytes:
    canvas = Canvas(18)
    canvas.polygon([(8, 1), (12, 1), (15, 5), (17, 11),
                    (15, 16), (12, 18), (6, 18), (2, 14), (1, 9),
                    (4, 3)], INK)
    canvas.polygon([(8, 3), (12, 3), (14, 6), (15, 11),
                    (13, 15), (11, 16), (7, 16), (4, 13), (3, 9),
                    (5, 5)], (189, 172, 145, 255))
    canvas.polygon([(7, 4), (11, 4), (12, 7), (9, 8), (5, 7)], WHITE)
    canvas.point(6, 10, (80, 125, 158, 255))
    canvas.point(11, 12, (80, 125, 158, 255))
    canvas.point(9, 15, GOLD)
    return canvas.png()


def brand_icon() -> bytes:
    canvas = Canvas()
    canvas.polygon([(16, 1), (30, 11), (26, 26), (16, 31),
                    (6, 26), (2, 11)], INK)
    canvas.polygon([(16, 3), (27, 12), (24, 24), (16, 28),
                    (8, 24), (5, 12)], (51, 67, 111, 255))
    canvas.polygon([(16, 4), (25, 12), (16, 16), (7, 12)], (116, 211, 220, 255))
    canvas.polygon([(7, 12), (16, 16), (9, 24)], (60, 119, 179, 255))
    canvas.polygon([(25, 12), (16, 16), (23, 24)], (227, 117, 180, 255))
    canvas.polygon([(9, 24), (16, 16), (23, 24), (16, 28)], (155, 91, 185, 255))
    canvas.line(16, 5, 16, 16, WHITE)
    canvas.line(12, 7, 8, 12, GOLD)
    canvas.point(16, 2, GOLD)
    return canvas.png()


def tab_icon() -> bytes:
    canvas = Canvas()
    for vertices, top, left, right, core in (
            ([(15, 2), (25, 12), (17, 24), (7, 12)],
             (245, 208, 109, 255), (177, 84, 167, 255),
             (239, 120, 118, 255), (109, 69, 161, 255)),
            ([(6, 15), (14, 21), (8, 31), (1, 23)],
             (116, 230, 209, 255), (44, 120, 169, 255),
             (79, 178, 203, 255), (40, 73, 141, 255)),
            ([(24, 16), (31, 22), (25, 31), (18, 24)],
             (247, 164, 125, 255), (129, 71, 169, 255),
             (229, 100, 150, 255), (101, 64, 143, 255))):
        cx = round(sum(x for x, _ in vertices) / len(vertices))
        cy = round(sum(y for _, y in vertices) / len(vertices))
        facet(canvas, vertices, top, left, right, core, (cx, cy))
    return canvas.png()


def heart_icon() -> bytes:
    canvas = Canvas()
    outline = [(3, 7), (7, 3), (12, 3), (16, 7), (20, 3), (25, 3),
               (29, 7), (29, 15), (26, 20), (16, 30), (6, 20), (3, 15)]
    canvas.polygon(outline, INK)
    canvas.polygon([(5, 8), (8, 5), (12, 5), (16, 10), (20, 5), (24, 5),
                    (27, 8), (27, 15), (24, 19), (16, 27), (8, 19), (5, 15)],
                   (188, 47, 70, 255))
    canvas.polygon([(6, 8), (9, 5), (12, 5), (15, 10), (15, 23),
                    (8, 17), (6, 14)], (235, 77, 91, 255))
    canvas.polygon([(19, 8), (22, 6), (25, 8), (25, 11), (22, 9)],
                   (255, 174, 159, 255))
    canvas.line(8, 8, 11, 6, WHITE)
    canvas.line(16, 26, 23, 19, (255, 130, 124, 255))
    return canvas.png()


def metal_pcm() -> bytes:
    rate = 24_000
    noise = random.Random(0x4D4554414C)
    output = bytearray()
    for index in range(round(rate * 0.43)):
        t = index / rate
        attack = min(1.0, t * 850)
        decay = math.exp(-15.5 * t)
        partials = (0.48 * math.sin(2 * math.pi * 610 * t)
                    + 0.23 * math.sin(2 * math.pi * 1047 * t)
                    + 0.17 * math.sin(2 * math.pi * 1653 * t)
                    + 0.09 * math.sin(2 * math.pi * 2411 * t))
        transient = noise.uniform(-1, 1) * math.exp(-95 * t) * 0.24
        value = attack * decay * partials + transient
        output.extend(struct.pack("<h", max(-32768, min(32767, round(value * 27000)))))
    return bytes(output)


def ogg(ffmpeg: str) -> bytes:
    command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin",
               "-f", "s16le", "-ar", "24000", "-ac", "1", "-i", "pipe:0",
               "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
               "-c:a", "libvorbis", "-q:a", "3", "-f", "ogg", "pipe:1"]
    result = subprocess.run(command, input=metal_pcm(), capture_output=True, check=True)
    if not result.stdout.startswith(b"OggS"):
        raise RuntimeError("FFmpeg did not encode Ogg Vorbis")
    return result.stdout


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--ffmpeg", default=shutil.which("ffmpeg"))
    args = parser.parse_args()
    if not args.ffmpeg:
        parser.error("FFmpeg with libvorbis is required; pass --ffmpeg PATH")
    outputs = {
        ASSETS / "icon.png": brand_icon(),
        ASSETS / "textures/item/creative_tab_icon.png": tab_icon(),
        ASSETS / "textures/item/items/heart.png": heart_icon(),
        ASSETS / "textures/item/test_dummy_spawn_egg.png": dummy_egg(),
        ASSETS / "sounds/metal_pipe.ogg": ogg(args.ffmpeg),
    }
    outputs.update(runic_assets())
    verify_karsiori_assets()
    verify_legendary_sources()
    for path, data in outputs.items():
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                raise SystemExit(f"Asset differs: {path}")
        else:
            path.write_bytes(data)
    if args.check:
        allowed = (set(outputs) | {ASSETS / name for name in CC0_EXTERNAL}
                   | {ASSETS / f"textures/item/gems/{name}_gem.png" for name in KARSIORI_GEMS}
                   | {ASSETS / name for name in SOURCE_LEGENDARY_HASHES})
        actual = set(ASSETS.rglob("*.png")) | set(ASSETS.rglob("*.ogg"))
        if actual != allowed:
            raise SystemExit(f"Unreviewed asset inventory: +{actual - allowed}, -{allowed - actual}")
        for name, digest in CC0_EXTERNAL.items():
            path = ASSETS / name
            if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
                raise SystemExit(f"CC0 asset changed without provenance review: {path}")
        for path in outputs:
            if path.suffix == ".png":
                size = struct.unpack(">II", path.read_bytes()[16:24])
                expected = (18, 18) if path.name == "test_dummy_spawn_egg.png" else (32, 32)
                if size != expected:
                    raise SystemExit(f"PNG dimension changed: {path}: {size} != {expected}")
    print(f"{'Verified' if args.check else 'Generated'} {len(outputs)} Gems assets")


if __name__ == "__main__":
    main()
