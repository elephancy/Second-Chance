"""Import real slot art for Hold and Spin without redrawing its symbols.

Source: Ville Seppänen, Slots Symbols, CC BY 4.0.
Run: python tools/adventure/import_casino_reel_art.py [--check]
"""
from __future__ import annotations

import argparse
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ARCHIVE = ROOT / "tools/assets/ville-seppanen-slots-symbols.zip"
OUT = ROOT / "src/main/resources/assets/gems/textures/gui/casino"
ARCHIVE_SHA256 = "c51fc193d3dbb2a06b9e8904c61f5c0f76fd8f9019ad6c3d8536d782b582ef6c"
SOURCES = {
    "seven": "77986671c0610ff5123e0a1e256100c69eecee30407418c5eaf4aa15c1e31afc",
    "diamond": "e5f65669dc1735fa503fb7eeb87ee2dbbfc25ee60da383e2f3a72bb286428608",
    "bell": "e8dcd6563ba818bf8bf35fded9736430a6d65807851b0fc6b6c8508b204dd502",
    "clover": "293fd91e5401cb365dfb2e38d04b4787da651b33566763cfb9932f9efe129381",
}


def png_bytes(image: Image.Image) -> bytes:
    output = BytesIO()
    image.save(output, format="PNG", optimize=True)
    return output.getvalue()


def compose_scene(source: dict[str, bytes]) -> bytes:
    # Only the felt, window borders, and layout are drawn here. Every symbol
    # retains the artist's actual pixels, resized as a transparent source PNG.
    scene = Image.new("RGBA", (320, 56), "#10252c")
    draw = ImageDraw.Draw(scene)
    draw.rectangle((2, 2, 317, 53), outline="#dfbd79", width=2)
    draw.rectangle((6, 6, 313, 49), fill="#183d43", outline="#608185")
    for name, x in zip(SOURCES, (38, 100, 162, 224)):
        draw.rectangle((x, 8, x + 55, 47), fill="#0d202a", outline="#d2ad69", width=2)
        symbol = Image.open(BytesIO(source[name])).convert("RGBA")
        symbol = symbol.resize((40, 40), Image.Resampling.LANCZOS)
        scene.alpha_composite(symbol, (x + 8, 8))
    return png_bytes(scene)


def compose_mark(source: bytes) -> bytes:
    image = Image.open(BytesIO(source)).convert("RGBA")
    return png_bytes(image.resize((20, 20), Image.Resampling.LANCZOS))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    assert sha256(ARCHIVE.read_bytes()).hexdigest() == ARCHIVE_SHA256, "slot source archive changed"
    with ZipFile(ARCHIVE) as archive:
        source = {name: archive.read(f"{name}.png") for name in SOURCES}
    for name, expected in SOURCES.items():
        assert sha256(source[name]).hexdigest() == expected, f"{name} source changed"

    outputs = {OUT / "reels" / f"{name}.png": data for name, data in source.items()}
    outputs[OUT / "tables/reels_scene.png"] = compose_scene(source)
    outputs[OUT / "tables/reels_mark.png"] = compose_mark(source["seven"])
    for path, data in outputs.items():
        if args.check:
            assert path.read_bytes() == data, f"stale casino art: {path}"
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    print(f"{'Verified' if args.check else 'Imported'} {len(outputs)} source-backed reel textures")


if __name__ == "__main__":
    main()

