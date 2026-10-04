# Second Chance: divergent campaign routes

This is the implementation contract for the three playable routes requested
after the original campaign plan. Finale gameplay and epilogue services are now
implemented in source but lack integrated play acceptance. The shared world can
now save one route choice after the group has recovered at least two boss identities;
all seven expeditions and friend domains remain discoverable in every route.
The decision changes what players do between victories, how the last chapter
plays, who helps them, and the persistent world epilogue. It never changes gem
energy, deletes a reward or traps a late joiner.

## What the choice means to a player

The changed places come from stories the group keeps repeating. After two
identities are uncovered, a Story Council appears at the townhall. Its three
plain-language choices are:

| Route | Question | Main activity | Last chapter | World outcome |
| --- | --- | --- | --- | --- |
| **Remember** | Which parts were real, and who gets to tell them? | Compare public and private records in Inkbound, Archive Commons and Storybook Vale; preserve conflicting testimony instead of forcing a single answer | Assemble a living record while an adversary tries to erase inconvenient voices; clues and witnesses matter more than speed | An open archive and readable memory markers appear in the town; altered districts stay, but their origins are acknowledged |
| **Repair** | Who is left to fix the damage? | Rebuild physical links through Riverward, Redline and Soryal's yard with found materials and defended work sites | Keep a repair route open while protecting workers; shelter and placement choices matter more than a code puzzle | The town gains a working relief depot, repaired approaches and safer shared routes |
| **Release** | Can people choose a new story together? | Connect Nour's signals, Ali's shortcuts and Fam's forum; carry a message along different routes, solo or split with friends | Open a changing route through the source while coordinating timed signals; movement and communication matter | Public signal points and new optional passages appear, and the town becomes a crossroads |

The route names are not a morality test. Each path should have appealing gains
and a real cost, without labeling one the correct ending. Friends who disagree
must have character-specific reactions drawn from established behavior; direct
chat quotes still require archive proof and are not invented for a missing
speaker. Ending prose must explain what changed in everyday language.

At the Court conclusion, a short paged epilogue now describes the route's
outcome and the two civic services this world chose not to build. Supporting
friends and one friend who disagrees speak in newly authored English dialogue;
these lines are not archive quotations. Remember's account changes with
Othman's published lead. Players at the Court see the scene when the finale
ends. Any player can reread it from the finished Court tile or by sneak-using
the completed townhall council, including after a restart or late join.

The required End Source chapter now carries route evidence into the Court.
Remember arrives with its west account already protected and must carry the
east witness. Repair arrives with the west work site braced and must supply the
east site, then defend the line. Release arrives with the first signal carried
and follows the remaining changing calls. The source also grants each route's
longer timing window. These openings apply only after that world's End Source
clear; a previously completed Court ending remains complete. At the End
observatory, the return arch works without a completed source, and sneak-use
on any surviving source mark offers the same safe Overworld return.

## Multiplayer and save contract

- A route is proposed at one physical Story Council in the townhall. The UI
  explains that confirming selects this world's route. Nearby players can
  change their votes before confirmation. A solo player confirms with a second
  deliberate action. The chosen route is saved in world data.
- Every online player who joins the townhall decision must have an opportunity
  to vote; ties do not resolve silently. The selected route and next objective
  are visible on the council and in the guidebook for late joiners.
- Route objectives can be completed by one player or split among a group and
  save as shared world progress. No step requires a particular gem or PvP.
- After an ending, the other route choices remain visible as history but cannot
  overwrite this world's outcome. Boss rematches and normal exploration stay
  available. A different world can make a different choice.

## Acceptance for each route

1. Three distinct field objectives in at least three friend or expedition
   structures, with actual travel and local mechanics rather than desk clicks.
2. A distinct final playable objective, threat pattern and staging. Shared
   assets may connect the routes, but the objective cannot be the same task
   under another name.
3. At least three character reactions before the finale and an ending scene
   that depends on the route and earlier choices such as Othman's published
   lead. These are authored English lines; exact archive quotations are used
   only when speaker and context have been verified.
4. A persistent, visible world change and useful postgame service. The result
   must survive restart and be clear to late joiners.
5. Solo, two-player, save/restart and rematch evidence during the one final
   integrated acceptance run. Automated state tests and rough previews are
   interim checks only.

## Implementation order

Build the world-saved council choice and its clear UI first, then the three
field routes and route-specific finales. Wire the conclusion to persistent
world changes and character responses only after the playable objectives
exist. Do not present the council choice itself as an ending.

The first council pass is implemented in source: a protected townhall station,
saved votes, a 30-second second-click confirmation, a majority among nearby
voters and a persistent world route. Remember now has its first additional
field step: after the choice, make two different prints at Hamed's print house,
then bind them together at a new station in the paper garden. Its second step
begins at Othman's Library: publish one of two leads, inspect evidence in
Branch Vault's map room and Lastlight's upper signal room, then compare both
witnesses in the library regardless of which lead was published. The third
step returns to Yehia's Vale: after the ordinary garden tale, start a second
one and walk tree, lantern, mushroom before closing it at the Prop House's
painted door. A wrong mark restarts only that reverse walk. These three field
steps save as shared world progress. Repair has its first new field objective:
after Soryal's floor puzzle, the group mends a damaged fitting in his yard,
scrap bridge and loft using locally available patchwork brick, copper and
glass. Each fitting becomes a distinct repaired block and its shared progress
saves. Its second objective starts after Khaled's ordinary crossing: shelter
on each marked refuge during a flood pulse, mend that refuge's brace during
the brief safe window, then reach the shieldworks finish mark. The braces
change visibly and persist for the world; players can split the repairs and
retry without losing a mended brace. The third Repair objective carries aid
from Redline's infirmary to its watchpost after the guardian's first defeat.
One player holds the restored east and west shelter pads for three seconds
each while any remaining watchkeepers threaten the outer route, then reaches the
watchpost in two minutes. Only the final handoff saves completion for the
world; an interrupted run restarts at the infirmary. Finale staging,
character reactions and world epilogues have source implementations. Council
voting and these field steps have source and focused checks, not physical
multiplayer acceptance.

Release now has its first new field objective at Nour's harbor. After the
ordinary three-lamp call, a player starts a three-minute relay at the radio
hall. The boathouse and foghouse each send one call in either order, and the
group returns to the hall to close the relay. Solo players can travel the
whole loop; two players can split the side houses. A timeout discards only
that active relay, while the original harbor unlock remains. Completion saves
for the world. Ali's additional objective records physical anchors in his
high map room and cut passage, then requires a successful Shortcut Chalk trip
between them. Either direction works, and different players may record the
two anchors. The final successful trip saves completion for the world. Fam's
additional Release objective issues a physical Forum Dispatch at the guide
board. Players may carry or hand it through the marked stops in the Story Hall
and Open School in either order, then return the same active dispatch to the
board within five minutes. A lost or timed-out dispatch can be replaced there;
replacement invalidates the old copy and resets only this delivery attempt.
Completion is world-shared. These are three field chapters, not a finale; the
route-specific final challenge, reactions and visible epilogue are implemented
in source. After all seven boss identities, the Court's center dais opens a
different final challenge for each route. Remember carries two fading
side-room witnesses to matching stage marks and publishes both, with Othman's
published lead changing the end text. Repair delivers copper and glass to
opposite work rooms, then holds two stage marks for four seconds each and
reaches the dais within 30 seconds of the first hold. A warning marks which
side faces the next strike; standing there shields the crew, and at least one
strike must be guarded. An unguarded hit breaks that side's held line.
Delivered materials persist if the line slips. Release
follows four alternating room and stage signals before a three-minute timer
ends. The finished dais changes to a route-specific tile. Three friends per
route have authored pre- and post-ending responses, separate from archival
chat quotes. On returning to town, the Story Council becomes a route-specific
archive, relief depot or crossroads desk with a world-saved daily kit claim
per player. These systems compile on 26.3. Their combat pressure, route
clarity, world placement, save/restart and two-player behavior still require
the final integrated play run.
