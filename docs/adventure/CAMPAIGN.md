# Second Chance — campaign contract

This is the campaign plan for Second Chance.
The Gems of Fate mechanics reference remains the source of truth for existing
Gems mechanics; this document defines the new campaign's player-facing goals.
The fixed 18-person implementation roster is in
[`FROZEN_PERSON_SCOPE_26_3.md`](FROZEN_PERSON_SCOPE_26_3.md).

Development targets Minecraft 26.3 and fresh worlds. Pre-release world and
template versions are not supported. The current build source and its active
structure set define what new worlds generate; old experiment journals are not
part of the campaign contract. Asset licenses and credits remain required for
anything included in the mod.

## Premise and tone

Exaggerated accounts of a friend group have begun to change familiar Overworld
places. The player follows discrepancies between what people say and what is
there. Each landmark makes one account tangible. The campaign gradually moves
from familiar Minecraft exploration to the mythic and ends at the source of
the change. The story comes from personality and relationships, never from a
literal reenactment of an event in the supplied chat.

Dialogue is English. Humor should sound dry, quick and specific to the group;
there are no generic meme lines. Short chat quotations may be used only after
speaker, context and suitability have been checked. Private phone numbers,
slurs, sexual threats and personal details are excluded from shipped content.

Seven opponents use fictional titles until defeated. Every eligible player
receives a guaranteed lore item with the real identity after the victory.
Victory does not imply instant reconciliation. Eleven other named people appear
as allies or neutral characters; their detailed characterization needs
evidence from the user's notes/chat, not the earlier placeholder roles.

## Branching story and endings

The finished campaign needs several persistent player decisions that lead to
materially different routes through the later expeditions and finale. At least
three endings must have different playable objectives, character alliances,
world changes and epilogues. Changing only the final dialogue does not meet
this requirement. Choices must be understandable without revealing the result
in advance, fair to a solo player, saved for a shared server world and legible
to people who join after a choice. Rematches and postgame play must remain
possible without silently reversing the world's ending. The townhall council
saves an early route choice. The three route-specific chapters and endings
exist in source; their natural-world and multiplayer play acceptance remains
open. Othman's optional archive lead is a
separate earlier choice, not an ending system.
The route design and acceptance criteria are in [ENDINGS.md](ENDINGS.md).

## Expedition contract

Each expedition has all of these parts:

1. A clue that can be found through normal play and points to a place.
2. A designed landscape and multiple authored buildings or structures, with
   a readable silhouette, route choices, secrets and environmental lore.
3. One environmental rule that changes exploration or combat without locking
   out any starting gem or a solo player.
4. An ally or neutral character with agency, relationships and a reaction to
   events elsewhere in the campaign.
5. Encounter families that prepare players for the area's boss language.
6. A unique, challenging, replayable boss with fair tells, a clear spatial
   hook, co-op behavior and an authored arena.
7. Guaranteed identity lore and a signature reward that changes a future
   action; repeatable local loot supports exploration and rematches.
8. A persistent world or character change, including late-join catch-up.

The campaign is a solo-completable path with deliberately supported co-op.
Participation credit, personal lore drops, and rewards cannot rely on the
last hit. PvP rivalry is an optional path within this one mod. Existing Gems
energy and ability rules remain authoritative; campaign access cannot depend
on PvP energy gain. Boss progression is tracked separately from energy so a
death never removes an already unlocked route.

## The town and casino

The friend group's town is a discoverable place in the same Overworld, not a
fixed-coordinate paste over a player's build. Its casino is a social stop
between expeditions. Games must have actual player decisions and visible game
state; a button that immediately rolls a payout is not a finished game.
Support at least six distinct games, including a playable rock-paper-scissors
tournament, cards, a wheel, reels, trivia and one cooperative table. Wagers
use earnable in-game currency only. Server-side sessions validate proximity,
identity, balance and one settlement per round. Win/loss rules and rewards are
shown before a player commits. Losing a casino round cannot remove a gem,
permanent heart or campaign unlock.

Ragab also runs a free daily group tournament. Three solo tables rotate each
Minecraft day, and the two-player Signal Table is always an optional fourth.
Three different featured wins complete the shared day; one player can finish
the three solo games, while a group can divide them. The first completed day
permanently opens Ragab's domain. The townhall Tournament Board shows the
world score and gives each player one small provision kit after a completed
day. Free tournament rounds do not debit or pay emeralds, and day rollover
cannot carry an unfinished result into the next challenge.

The town needs authored public routes, the casino floor, distinct game tables,
shops and spaces that fit the characters. NPCs must be present through safe
world generation and react to player progress. Dialogue and jokes should come
from verified group context; exact quotes require speaker and context checks.

## Friend domains and a changing Overworld

Every named friend controls a discoverable area, including friends who are not
one of the seven major bosses. A domain needs a coherent silhouette, roof and
wall grammar, block palette, local vegetation, mobs, loot and one playable
environmental rule. The style should express that friend's verified identity
and the area's story; it cannot be a palette swap of the next domain. Feel's
Hushwire is a dark gothic signal district. A Japanese-inspired paper-and-timber
workshop direction now belongs to Hamed, whose verified
"Fish Story 2" line gives it a story-making reason to exist. The area contract
for all eighteen friends is in [FRIEND_DOMAINS.md](FRIEND_DOMAINS.md).

The Overworld must gain new biomes, building families and useful block sets,
with the domains integrated into new terrain rather than isolated flat pads.
Existing world builds must remain intact. Materials acquired locally should
support useful variants and decoration outside the domain.

Conquering a domain unlocks its signature service in the townhall for everyone
on the server, including late joiners. Townhall services must be distinct,
bounded and fun in solo and co-op; permanent campaign progress is independent
of a player's current gem energy. The townhall should visibly change as
services become available and explain each one in plain language.

The first service is Hushwire's Echo Scout. Clearing Hushwire once in a
world opens the townhall ledger to all players, including people who join
later. Each player can call an echo for thirty seconds with a one-minute
recharge. The other domain services exist in source; their complete in-game
use and visual acceptance remain open.

New asset work uses verified free source files and records author, license,
original file and transformation. Variations are deterministic edits of source
pixels or meshes; do not use AI image generation. Architectural references
guide original builds and do not authorize copying a schematic or photo.

## Seven landmarks

These are working place and in-world boss titles. Real identities are listed
only in this developer document; data and dialogue before a win use titles.

| Landmark | Person | Physical identity and main building | Encounter hook | Signature reward direction |
| --- | --- | --- | --- | --- |
| Framefall | Teckno | A ravine-side gallery, open sculpture court and restoration workshop; framed sightlines and colored glass | The Painted Verdict changes firing angles after each judged exchange, so the fixed sculpture and planters offer different cover | A portable frame that lets players create a temporary angle or bridge |
| Lastlight Exchange | Zalabany | A station layered with older masonry, a late annex, clock tower and market vault | The Last Arrival adds a readable late phase that changes the route through the arena | A timed return point or safe delayed retrieval |
| Rehearsal Works | Tofy | A timber and brass stage complex, rigging towers, backstage paths and prop foundry | The Unbroken Punchline improvises counters after repeated player actions; being the group's target is never his entire identity | A rehearsal mark that records and replays a chosen movement |
| Branch Vault | Safwat | A monumental archive, branching halls, solver's chamber and roof escape | The Second-Move Solver predicts one route, then must recover when players change plan | A route tool that exposes alternate entrances and exits |
| Redline House | Rubius | A warm but defensive waystation, interlinked rescue bridges and planning room | The Red Line Between intercepts players and protects threatened allies without an abstract dialogue trial | A rescue line with a team use and solo use |
| Hushwire | Feel | A transmitter tower, record hall, wrong-way signs and acoustic undercroft | The Empty Encore rewards patience against provocations, with honest tells for every bait | A signal tool that can reveal false cues and call a companion |
| Unwritten Court | Strad | An amphitheater, changing stage, service tunnels and overlooked steward's room | The Stray Cadenza responds to the party's habits from a bounded, learnable move set | A stage mark that alters a future encounter choice |

Place names and silhouettes will be judged in game. A building generator or a
valid NBT template is asset source, not proof of visual quality or playability.
The regions need surrounding routes, landscape rules, side structures,
interiors, environmental storytelling, and real loot placement before they
meet this contract.

## Loot and lore rules

- Every landmark has repeatable supplies, a guarded optional cache, and a
  fixed progression reward. Common chests cannot randomly contain the unique
  victory identity item.
- Loot should express the location: restoration materials in Framefall,
  salvaged old stock in Lastlight, stage tools in Rehearsal Works, maps and
  compasses in Branch Vault, rescue supplies in Redline House, signal parts in
  Hushwire, and adaptable combat tools in Unwritten Court.
- Short inscriptions appear where a player naturally pauses or changes route.
  The boss reveal is a full lore item given to each participant, not a chance
  from a shared chest.
- No structure, chest or quest may overwrite a player's existing base or
  require a specific gem. Generated landmarks must be discoverable and
  protected from softlocks caused by prior world exploration.

## Release gates

Keep implementation checks focused while the campaign is being built. The
requested end-to-end acceptance is one concentrated pass after the full
campaign is integrated. It must cover normal survival discovery, solo wins,
loot and rewards, and a two-client dedicated-server run for all seven areas
and bosses, plus late join, rejoin, replay, casino recovery,
and measured server/client performance. The intended 15–25 hour first
playthrough is a design target, not a verified duration.

