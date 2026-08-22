"""Import three unchanged CC0 maze cues from Kenney's Impact Sounds 1.0.

Usage: python tools/adventure/import_maze_audio.py <impact-sounds.zip>
Source: https://kenney.nl/assets/impact-sounds
"""

import hashlib
import sys
import zipfile
from pathlib import Path

from import_hushwire_audio import duration_seconds


OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/sounds"
CUES = {
    "Audio/impactGlass_medium_002.ogg": (
        "maze_seal.ogg", "48616f22c95589ed9fe55babf0fd012721d7b2ef1b9e85c2ad6408a2cd7c164f"),
    "Audio/impactMining_001.ogg": (
        "maze_trap.ogg", "61235a18de3f3d92118235398222dbf51d448f1be70b1c8bc962b10b86f2d9c6"),
    "Audio/impactMetal_light_002.ogg": (
        "maze_reset.ogg", "25a96f90a9a1f88a531e824e126f0519504625e5635e65a72e4f31611428db29"),
}


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: import_maze_audio.py <impact-sounds.zip>")
    with zipfile.ZipFile(sys.argv[1]) as archive:
        license_text = archive.read("License.txt").decode("utf-8")
        if "Impact Sounds (1.0)" not in license_text or "Creative Commons Zero, CC0" not in license_text:
            raise ValueError("source is not the checked Kenney CC0 Impact Sounds 1.0 pack")
        OUT.mkdir(parents=True, exist_ok=True)
        for source, (name, expected_hash) in CUES.items():
            data = archive.read(source)
            digest = hashlib.sha256(data).hexdigest()
            duration = duration_seconds(data)
            if digest != expected_hash or not data.startswith(b"OggS") or not 0.2 <= duration <= 1.0:
                raise ValueError(f"{source}: source hash or Ogg duration changed")
            target = OUT / name
            if not target.is_file() or target.read_bytes() != data:
                target.write_bytes(data)
            print(f"{name} <- {source}: {duration:.3f}s, SHA-256 {digest}")


if __name__ == "__main__":
    main()
