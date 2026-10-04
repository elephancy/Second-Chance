# Independent exploration sites (26.3)

Three original structures generate separately from the 18 friend districts. They use
their own data files and do not change old chunks or existing district templates.
Each raised entry now has a three-wide exterior stair grade and a low apron,
bounded to the authored site footprint.

| Site | Where to look | What to do | First-clear supplies |
| --- | --- | --- | --- |
| Lost Trail | Plains | Touch the first marker, follow the blue bend, then touch the far marker. | Four golden carrots |
| Star Cipher | Desert | Read the plaque. Touch Sun, Star, Moon, then the seal. | Eight lapis lazuli |
| Skywatch Lookout | Meadow | Climb inside. Hold a spyglass, face east under open sky, then use the dial. | Four experience bottles |
| Labyrinth | See the maze's own worldgen file | Reach its valid exit after entering the same generated instance. | Three echo shards |

Every site instance has a stable ID from its generated origin. The first discovery
is shared with the server, while each eligible player has their own first-clear
supplies. Companions within 24 blocks share a finish when they followed the
same trail markers, solved the same cipher, or stood near the lookout dial
with their own spyglass facing east under open sky.
A player with a full inventory can return later; the entitlement persists
in the world save and the issuance receipt saves with their inventory. Clearing
one site does not deny friends a later first clear. Players may share the clues.

The persistent trail and cipher steps survive restart. Their marker blocks have
no survival item, so normal players cannot move or duplicate puzzle endpoints.
The runtime checks the original marker still exists before accepting a finish.
It does no chunk or inventory scan every tick. Pending deliveries are checked
once every five seconds for online players and on join.

After any personal first clear, sneak-use a marker at any of the three side
sites to rest for the road: Speed and Night Vision for 90 seconds. The rest
recharges after ten minutes, including across logout and restart. This is a
travel convenience earned by exploring, while each site's supplies remain
once per player and instance.

## Integration

- During mod init, call `ExplorationBlocks.init()` before worldgen data loads.
- During adventure event registration, call `ExplorationRuntime.register()` once.
- The maze calls `ExplorationRewards.complete(level, ExplorationProgress.activityId("maze", entrance.getX(), entrance.getZ()), entrance, eligiblePlayers)` after its own server-side exit check. `completeForIds` preserves rewards for eligible players who disconnect before delivery.
- Atlas targets for deliberate searching: `lost_trail`, `star_cipher`, `skywatch_lookout`.
- Source generators: `tools/adventure/generate_exploration_sites.py` and `tools/adventure/generate_exploration_textures.py`. The latter draws new glyphs over the bundled CC0 marble tile; no AI imagery or third-party build is used.

Natural spawn, stair/ladder traversal, co-op, and visual fit
still need the assembled client's end-to-end pass. Source data alone is not a
playable acceptance result.

An opt-in final gate is `NaturalExplorationSiteClientGameTest`. Run it with a
fresh directory such as
`./gradlew runNaturalExplorationClient -PnaturalExplorationRun=exploration-final-a`.
It locates each actual structure start in a generated world, scans its placed
markers, checks walkable puzzle paths and the tower's continuous climb, runs
the server puzzle callbacks, checks reward receipts, and takes exterior
screenshots. A pass still needs a human review of the rendered sites and an
actual survival walk/climb; scripted callbacks do not prove physical input.
The harness also traces a standing route from natural terrain across each
placed entry grade to the first marker or tower ladder.
