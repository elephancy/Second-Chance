"""Import original CC0 legendary item icons and compose Hunter's Sight draw states.

Direct imports retain source PNG bytes. The bow draw states combine two original
7Soul1 sprites by rotating and translating the arrow; no prior art is redrawn.
Pillow is needed only when refreshing these checked-in PNGs, not for Gradle.
"""
from __future__ import annotations

import argparse
import hashlib
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "gems/src/main/resources/assets/gems/textures/item/legendary"
ARCHIVES = {
    "ranged-7soul1_20201212.zip": "b6d556e5dee1e4fd77fcf8513a9c739d4cbd1c862233869317d420f58d3224e4",
    "tool-7soul1_20201222.zip": "4dfdd64ca9eda3cb81c37b7649fb4d5d31113934dd1436feaf338a4a007fd7fe",
    "jewelry-7soul1_20201212.zip": "7cd8d154048464d60f6f9bf82db6289950939dd4e36849b3fb028a498800155d",
    "stave-7soul1_20201212.zip": "f14c5c2f5acb6b4d95d6602fbe7eeb7702f46171545a96016812ac4af46bd0c6",
    "tool-ocal_20230225.zip": "dc5e13999c0d419f82b0addfd35ca3dfdceb1831160a86e44f057cf4737ee8f7",
}
DIRECT = {
    "tools/chrono_charm.png": ("tool-7soul1_20201222.zip", "32x32/clock.png"),
    "tools/gem_seer.png": ("jewelry-7soul1_20201212.zip", "32x32/necklace_03.png"),
    "tools/hypno_staff.png": ("stave-7soul1_20201212.zip", "32x32/staff_10.png"),
    "tools/recall_relic.png": ("jewelry-7soul1_20201212.zip", "32x32/necklace_02.png"),
    "tools/tracker_compass.png": ("tool-ocal_20230225.zip", "32x32/compass_01.png"),
    "weapons/hunters_sight_bow.png": ("ranged-7soul1_20201212.zip", "32x32/bow_15.png"),
}


def png_bytes(image: Image.Image) -> bytes:
    stream = BytesIO()
    image.save(stream, format="PNG")
    return stream.getvalue()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    sources = {}
    for name, digest in ARCHIVES.items():
        path = ROOT / "tools/assets" / name
        data = path.read_bytes()
        if hashlib.sha256(data).hexdigest() != digest:
            raise SystemExit(f"Source archive changed: {name}")
        sources[name] = ZipFile(BytesIO(data))
    try:
        outputs = {}
        for target, (archive, member) in DIRECT.items():
            data = sources[archive].read(member)
            with Image.open(BytesIO(data)) as image:
                if image.size != (32, 32):
                    raise SystemExit(f"Source icon is not 32x32: {member}")
            outputs[target] = data
        ranged = sources["ranged-7soul1_20201212.zip"]
        with Image.open(BytesIO(ranged.read("32x32/bow_15.png"))) as original_bow:
            bow = original_bow.convert("RGBA")
        with Image.open(BytesIO(ranged.read("32x32/arrow_01.png"))) as original_arrow:
            arrow = original_arrow.convert("RGBA").rotate(180)
        for frame, (dx, dy) in enumerate(((-6, 6), (-3, 3), (0, 0))):
            canvas = bow.copy()
            layer = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
            layer.paste(arrow, (dx, dy))
            canvas.alpha_composite(layer)
            outputs[f"weapons/hunters_sight_bow_pulling_{frame}.png"] = png_bytes(canvas)
        for target, data in outputs.items():
            path = ASSETS / target
            if args.check:
                if not path.is_file() or path.read_bytes() != data:
                    raise SystemExit(f"Imported icon differs: {target}")
            else:
                path.write_bytes(data)
            print(f"{target}: SHA-256 {hashlib.sha256(path.read_bytes()).hexdigest()}")
    finally:
        for source in sources.values():
            source.close()


if __name__ == "__main__":
    main()
