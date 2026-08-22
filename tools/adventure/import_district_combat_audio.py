"""Copy unchanged CC0 district combat cues from two Kenney audio packs.

Usage: python tools/adventure/import_district_combat_audio.py <rpg-audio.zip> <impact-sounds.zip>
Sources: https://kenney.nl/assets/rpg-audio and https://kenney.nl/assets/impact-sounds
"""

import hashlib
import sys
import zipfile
from pathlib import Path

from import_hushwire_audio import duration_seconds


OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/sounds"
RPG_CUES = {
    "Audio/metalClick.ogg": "lastlight_call.ogg",
    "Audio/metalLatch.ogg": "lastlight_lock.ogg",
    "Audio/clothBelt.ogg": "rehearsal_call.ogg",
    "Audio/bookFlip2.ogg": "branch_call.ogg",
    "Audio/bookClose.ogg": "branch_lock.ogg",
    "Audio/metalPot1.ogg": "redline_call.ogg",
}
IMPACT_CUES = {
    "Audio/impactBell_heavy_000.ogg": "lastlight_strike.ogg",
    "Audio/impactPlank_medium_000.ogg": "rehearsal_lock.ogg",
    "Audio/impactWood_heavy_000.ogg": "rehearsal_strike.ogg",
    "Audio/impactGlass_heavy_000.ogg": "branch_strike.ogg",
    "Audio/impactPlate_medium_000.ogg": "redline_lock.ogg",
    "Audio/impactPlate_heavy_000.ogg": "redline_strike.ogg",
    "Audio/impactTin_medium_000.ogg": "redline_shelter.ogg",
}


def copy_cues(pack: Path, cues: dict[str, str]) -> None:
    with zipfile.ZipFile(pack) as archive:
        license_text = archive.read("License.txt").decode("utf-8")
        if "Creative Commons Zero, CC0" not in license_text:
            raise ValueError(f"{pack}: source license is not the checked CC0 license")
        for source, name in cues.items():
            data = archive.read(source)
            if not data.startswith(b"OggS"):
                raise ValueError(f"{source}: not an Ogg file")
            duration = duration_seconds(data)
            if not 0.05 <= duration <= 1.5:
                raise ValueError(f"{source}: {duration:.2f}s is outside combat cue range")
            (OUT / name).write_bytes(data)
            print(f"{name} <- {pack.name}:{source}: {duration:.2f}s, "
                  f"SHA-256 {hashlib.sha256(data).hexdigest()}")


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: import_district_combat_audio.py <rpg-audio.zip> <impact-sounds.zip>")
    OUT.mkdir(parents=True, exist_ok=True)
    copy_cues(Path(sys.argv[1]), RPG_CUES)
    copy_cues(Path(sys.argv[2]), IMPACT_CUES)


if __name__ == "__main__":
    main()
