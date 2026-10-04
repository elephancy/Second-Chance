# Shared site placement contract (26.3)

## What is implemented

`com.feel.gems.adventure.worldgen.site` contains immutable `SiteDefinition`,
`SiteVolume`, and `SiteCandidate` types. `SitePlanner.candidate` selects one
deterministic position and rotation per site and region from the world seed.
Changing a site's template version does not change its position. Definitions
declare the dimension, biome allowlist, Y range, setting (surface, ocean,
underground, Nether, or End), slope, water depth, support, roof, hazard
clearance, and rotated build/foundation/approach/clearance volumes.

`SitePlacementPlan` lists exact block writes and read requirements.
`SitePreflight.check` rejects a plan if its ID/version or dimension differs,
any footprint chunk is unavailable or not stamped new, any volume overlaps a
protected structure or cannot be inspected, the biome or terrain survey fails,
a write lies outside its declared volume or write zone, a block entity is in
the way, or a current state violates the selected replacement rule. It checks
every x/z column of the declared volumes and every clearance cell. It never
loads chunks or writes blocks. An arithmetic overflow or world-border site is
rejected.

`WorldGenRegionSiteAccess` uses the actual 26.3 generation region and refuses
multi-chunk transactions with `NEEDS_SCHEDULER`. A 26.3 late feature sees only
the immediate 3x3 chunk reference ring and `ensureCanWrite` restricts it to
the center chunk. `hasChunk` can report a cached neighbor outside that ring,
so every read is explicitly bounded.

`GuardedNewChunkSiteAccess` stamps chunks on `CHUNK_GENERATE` in all dimensions
and reads only already-loaded chunks. Call `GuardedSiteScheduler.init()` during
mod initialization before generation begins. Existing chunks have no stamp and
cannot pass the guarded preflight. Its generic terrain probe reads actual
heightmaps, support, water, and roof cells. A site with templates already
placed must supply a specialist probe that distinguishes its roof from the
natural ground. The generic probe returns
zero hazard clearance until a site-specific adapter proves its required sculk,
ancient-city, lava, or void-edge exclusion. A site with a positive hazard
minimum cannot pass on an unproved generic adapter.

`GuardedSiteScheduler.submit` accepts a deterministic saved-marker request.
The site must rediscover and resubmit that marker on chunk load after restart.
The server-thread queue waits for all footprint chunks to be loaded, stamped
new, and preflighted. It supports at most 24 chunks, 128 air-only writes,
and 150,000 declared volume cells in one bounded transaction. It tries one
request at most once per second. Missing chunks wake that request after a
chunk-load event; unresolved protected references back off from 2 to 60
seconds. An unproved terrain survey is rejected instead of rescanned every
tick. The commit flushes only body and gate chunks that actually change.
It persists a fingerprinted PREPARED receipt on the root chunk before the
first write. It flushes the changed footprint before opening any authored
sealed gate, then flushes again and persists COMPLETE. A matching PREPARED
receipt permits idempotent intended-state writes on restart. A changed plan
fingerprint or player-modified cell remains sealed and blocked.
There is no multi-tick write cursor: the 128-write cap keeps each operation
small, and a crash can replay its intended cells from the PREPARED receipt.
Larger write manifests are rejected for generation-time structure pieces or
a separately designed staged journal.

The guarded scheduler intentionally rejects natural-terrain and fluid
replacement after a chunk reaches FULL. A new-generation stamp proves age,
not freedom from later player edits. Terrain carving, ocean displacement, and
large structural writes must occur during generation or through a stronger
edit-tracking transaction.

## Concrete path for large builds

The estate has an active `FourRoadsEstateStructure` codec. It delegates
piece planning to the fixed jigsaw graph, then rejects a start unless the
saved builder contains exactly the 25 distinct versioned modules, a 235 by
235 nonoverlapping grid in one rotation, and plausible dry, supported,
low-slope noise terrain throughout the footprint and a twelve-block halo.
`LargeSiteNoiseSurvey` is reusable for other surface builds. The estate
structure JSON uses this custom type, and `four_roads_estate_v1` is in the
active structure set. This checks generation-time noise only.

`EstateSiteIntegrity.inspectLoaded` is an explicit, read-only activation
sweep. It requires all occupied chunks loaded and stamped as newly generated,
the 25 saved pieces, no conflicting structure reference, all four entrance
marker kinds, the foyer and return stone, clear standing room, and a
three-wide walkable line from each gate through its wall and eight blocks
outside. It never loads chunks. On a missing chunk or unproved reference, it
returns a pending/unsafe result. This is not a per-click or per-tick call;
`FourRoadsEstateStructure.completeGrid` alone reads 25 saved piece records
and can cheaply guard ordinary interactions. The full sweep still cannot
prove that every maze corridor, lighting condition or foundation survives
natural decoration; those require the final natural-world gate.

For the 235x235 four-maze estate, the custom `Structure` creates one saved
start at `STRUCTURE_STARTS`. Its `findGenerationPoint` accepts a fixed
25-piece layout only after surveying the *noise* biome, base height, water,
and support across the bounds. Vanilla saves references and places pieces in
their own chunks during structure generation. This generation-time survey
cannot inspect eventual trees, player edits, block entities, or later
decoration.

The estate's `estate_commons` biome avoids conflicting tree decoration, and
the structure's full-footprint survey rejects unsuitable ground before a
start is saved. Its authored pieces supply foundations and retaining edges.
`EstateArrivalClearanceFeature` makes bounded arrival repairs in newly
generating chunks only, checking saved estate references and foreign
structures before each write. It cannot atomically rewrite the entire
15x15-chunk site. `EstateSiteIntegrity` checks loaded entries, joins and the
foyer when a player reaches them; unsafe or unresolved areas stay closed.
The underground Undercard likewise places its main shell during generation
and handles its entrance with a guarded follow-up rather than a broad late
terrain rewrite.

Ocean sites must prove seabed support and water depth. Nether sites must prove
lava clearance and enough solid roof/support. End sites must prove island
support and void-edge distance. Underground sites must inspect a loaded halo
for sculk/deep-dark and ancient-city structures; Y level alone is insufficient.
These specialist hazard surveys and the natural-world visual/path/light checks
are integration work, not guarantees supplied by the generic adapter.

## Integration rule

An area owner defines its volumes, emits a deterministic exact plan, and
supplies a site-specific `SiteAccess` hazard proof where needed. Its saved root
marker stays sealed until `GuardedSiteScheduler` reports completion. The area
owner must register marker discovery on chunk load, including restart. Do not
call a late feature to write a multi-chunk footprint, and do not treat a
successful preflight as proof that a later feature cannot grow vegetation or
that lighting, navigation, rewards, and multiplayer work. Those require the
consolidated natural-world gate.
