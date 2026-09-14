# Lastlight Seegras street v1 — modified work notice

`lastlight_seegras_street_v1.nbt` is a **modified partial work** derived from
*Minecraft: Medieval/Baroque Town* by **Seegras** (2012). The original,
unmodified source is `reports/source/seegras/BaroqueTown.schematic`, obtained
directly from
https://seegras.discordia.ch/Blog/content/BaroqueTown.schematic and documented
at https://seegras.discordia.ch/Blog/minecraft-medieval-baroque-town/.
Original source SHA-256:
`2db56a655eee6a8ec32930ae617c7b081112ab673b96bc1653204242391f91d7`.

The original and this modified candidate are subject to the **Open Publication
License v1.0** (OPUBL-1.0): https://spdx.org/licenses/OPUBL-1.0.html. The
creator's page states no additional license options. Seegras is credited as
the original author and publisher. No endorsement by Seegras is implied.

Modified by **Second Chance project contributors with Codex assistance** on
**30 September 2026**. The reproducible script
`tools/adventure/convert_seegras_lastlight.py` crops source x=28..74,
y=10..40, z=30..76; removes source entities, inventories, block entities and
terrain; explicitly maps reviewed legacy block values to Minecraft 26.3 and
Lastlight blocks; opens a public copper-rail street; and adds two furnished
rooms, clock/station details, controlled new loot and a versioned connector.
The exact mapping counts and resulting hash are in
`lastlight_seegras_street_v1.json`. The paired
`lastlight_exchange_seegras_v3.nbt` is derived from the mod's own Lastlight
v2 template and adds a south seam; it contains no Seegras geometry.

Both NBT files remain research candidates under `reports/candidates/` and
are **not** active worldgen structures or bundled mod-jar resources. If the
street is later packaged, this notice and license reference must accompany
that distributed copy.
