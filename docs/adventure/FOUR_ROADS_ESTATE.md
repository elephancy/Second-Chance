# Four Roads Estate

An abandoned cartographer's house stands where four roads meet. Each road offers a different way to read the same place. Reaching the lit central foyer through **any one road** clears the Estate; the other three remain optional discoveries.

| Road | Place and challenge |
| --- | --- |
| Garden | Moss, hedge crowns and hanging lamps; follow living sightlines while roots slow wrong turns. |
| Mirror | Pale stone, glass and prism inlays; use reflections and landmarks. |
| Sluice | Tuff and old waterworks; choose dry passages or short channel crossings. |
| Clock | Dark masonry, copper and warning lamps; read the machinery rhythm and avoid dart lanes. |

Every road has loops, supply detours and safe field markers. Sneak-use a marker to return to its saved safe point. No route requires a specific gem or teammate, and traps act on travelers individually.

The central mansion has four open porticoes, a cross-shaped hall, an observatory, library, map room, dining room and workroom. Its return stone works before or after a clear and sends a visitor to a safe outer gate without another maze crossing. The four roads each hold a different one-time survey cache. Completing all four grants a survey glass and extends field-marker rest from 90 seconds to three minutes. Routes, clears and undelivered rewards persist through restart.

## World generation

The current 26.3 source builds a **235 by 235** site from 25 rigid **47 by 47** modules. `M` is the mansion; each lettered road owns six modules:

```text
NNNNE
WNNEE
WWMEE
WWSSE
WSSSS
```

The active structure set is `gems:four_roads_estate_v1`. Its full placement must reserve the 25-piece footprint, four three-wide graded exterior arrivals, foundations, roof clearance and all 24 walkable joins. A candidate that cannot fit safely must be rejected without a partial structure or terrain rewrite. The loaded-site audit checks the placed pieces and approaches without a per-tick scan. The road and mansion are authored by `tools/adventure/generate_four_roads_estate.py` and `tools/adventure/generate_four_roads_estate_mansion_v3.py`; editable schematics are under `builds/`.

## Acceptance

The current source and structural checks establish the 25 modules, 24 joins and four authored routes. Final acceptance still needs independent natural sites with all four physical arrivals, an unassisted walk through each route, mansion entry and safe exit, day/night visual review, restart persistence, two-player behavior and representative placement/Atlas latency. See [acceptance](ACCEPTANCE_2_0.md) for the remaining release checks.

