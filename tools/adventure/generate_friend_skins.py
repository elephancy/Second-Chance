"""Regenerate the shipped, fictional town-resident skins deterministically.

The designer reads fixed original sources under tools/adventure/source_friend_skins,
not these output files. Re-running this entrypoint produces the same PNG bytes.
"""

from pathlib import Path

from prototype_friend_skins import build_skins


OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/textures/entity/friend"


if __name__ == "__main__":
    skins = build_skins(OUT)
    print(f"Drawn {len(skins)} district-specific town outfits in {OUT}")
