# Required Nether and End chapters (26.3)

Status: required campaign geography for fresh Minecraft 26.3 worlds. The
three sites, chapter records and natural structure sets are in the current
datapack. `RequiredChapters` activates the Court gate only when all three
sets are present. Natural travel and full campaign acceptance remain open.

## Story sequence

1. The townhall Story Council opens after two identities, as it does now.
   Its selected Remember, Repair or Release route stays world-shared.
2. Lastlight's Overworld station points to the **Ash Annex** in the Nether.
   Its missing timetable explains why a late arrival kept changing the
   group's route. Finding the three physical records and operating the
   departure board clears this chapter.
3. Redline's Overworld refuge points to the **Ember Bridge** in the Nether.
   Restore two shelters and keep a real crossing open during a bounded
   evacuation. A solo player can complete it; a team may split the work.
4. Recover all seven identity records and finish the chosen route's three
   existing field objectives. The Court then gives a clue to the **End
   Source** on an outer island. Its source room must be completed before the
   Court's final dais accepts the route-specific finale.
5. The End Source presents a distinct playable task for each route:
   Remember preserves two contradictory accounts; Repair stabilizes broken
   access while protecting work points; Release carries a signal through a
   changing passage. The result provides a concrete aid or rule change for
   that route's Court finale. The player can safely return to the Court.

The Nether chapters require meaningful interaction at their sites, beyond a
dimension visit. Their service upgrades are useful to everyone in the server
after a world clear.

## Site contracts

| Site | Architecture and local rule | Spawn and safety |
| --- | --- | --- |
| Lastlight Ash Annex | Basalt and brass rail depot, clock crown, ash-filter windows, missing platform, timetable vault and sheltered lava-side transfer. The late arrival alters a legible route; recovered records open the board. | Nether wastes with full supported depot footprint, stable headroom, no lava under public floors, roofed arrival, marked retreat to a return point. Reject cavern or roof collisions and nearby structures. |
| Redline Ember Bridge | Warm brick and copper refuge towers joined by a strong bridge, two repairable shelter bays and visible watch line. Evacuation plays over the actual span with clear rescue windows. | Warped Forest lava gorge where both banks can support the span and a player can reach an enclosed refuge. Reject interrupted banks, open lava on mandatory paths and unsafe portals/return pads. |
| Unwritten End Source | Pale observatory, chorus relief, mask fragments and changing but bounded light paths; each route uses the same estate geometry differently. The return arch is always reachable, including before completion. | Outer End island with broad supported foundation and set-back from void edge; reject thin/small islands and any approach that would need a blind leap or uncontrolled Enderman crowd in the puzzle room. |

The shared placement planner must inspect the full rotated structure, approach,
foundation, ceiling and return volumes in the destination dimension. A site
must reject an unsafe candidate before any partial write. Its generated entry
and exits need natural two-seed inspection and a live solo traversal. For the
Nether and End, verify portal or marked travel guidance both ways, restart
inside the site, late join, and failure recovery if the next candidate has
not generated. Do not change already generated or player-edited chunks.

## Cross-dimension state and discovery

Keep campaign SavedData on the Overworld server level, with versioned chapter
progress. Destination blocks send server-authoritative completion to that
state on the server thread. Player-scope reward receipts remain per player and
idempotent. The Atlas needs appended dimension-aware entries and explicit
language for which dimension to search; do not silently reuse existing page
indices or tell a player to search the Overworld for a Nether/End site.

The Court's current three finales stay at their saved Overworld start. Once
all three natural sets are active, an unfinished finale requires both Nether
clears and the chosen route's End Source clear after the existing seven
identities and route fields. A previously completed ending remains complete;
the Story Council points returning players to the new chapters without
reopening the finale. The End Source allows postgame attempts after the two
Nether clears, even if the old ending already happened.
World-saved return destinations must check the player actually has a safe
loaded place to arrive, and they must never strand a player in a missing or
partially generated site.
