# Second Chance — Server Owner Guide

This guide is written for Minecraft server owners and admins (not mod developers).

## Requirements

- Minecraft: `26.3`
- Java: `>=25`
- Fabric Loader: `>=0.19.5` (required)
- Fabric API: `0.162.0+26.3` (tested)

Optional:
- `spark` (profiling)

## Install

1. Install Fabric Loader and Fabric API on your server.
2. Put the matching `gems-<version>.jar` and `second-chance-<version>.jar`
   alongside Fabric API and TerraBlender Fabric for 26.3 in your server `mods/`
   folder. Start a new world; pre-release saves are outside this build's scope.
3. Start the server once with your Fabric server launcher, for example `java -Xmx2G -jar <fabric-server-launcher.jar>`, to generate configs under `config/gems/`.

## Config files

- Main balance file: `config/gems/balance.json`
  - Reload at runtime (op level 2+): `/gems reloadBalance`
  - Dump effective (clamped) values (op level 2+): `/gems dumpBalance` (writes `config/gems/balance.effective.json`)
- Disabled gems and powers: `config/gems/disables.json`
  - Reload at runtime (op level 2+): `/gems reloadDisables`
- Client config: `config/gems/client.json` (per-client, not synced to server)
  - Players can toggle their own passives and choose keybind mode; see the [Gems controls guide](https://github.com/elephancy/Gems/blob/main/docs/CONTROLS_AND_HUD.md).

For config keys, units, and safety clamps, see the [Gems balance reference](https://github.com/elephancy/Gems/blob/main/balance.md).

### Changing rules on a dedicated server

The in-game Settings screen edits world rules only for an integrated single-player world. On a remote server it shows the player's own controls; its local files do not change the server. Operators should:

1. Stop making concurrent edits to the two server files and keep a copy of the current JSON.
2. On the **server host**, add only the values to override in `config/gems/balance.json`, or edit IDs in `config/gems/disables.json`. An empty balance object `{}` means all defaults.
3. Run `/gems reloadBalance` or `/gems reloadDisables` from the server console or as an op in-game. A malformed file is kept and refused, so correct it and retry.
4. Run `/gems dumpBalance` when changing balance values. Compare `balance.effective.json` with the intended values to see clamps before inviting players back into a fight.

Client effects and control mode are personal. Each player changes those in Gems Settings; there is no server-side `client.json` sync.

## Permissions and commands

### Player commands

- `/gems` or `/gems help` lists player commands in game.
- `/secondchance` or `/secondchance help` points players to the Atlas and guidebook.
- `/gems status` shows your gem, energy, and unlocked powers.
- `/gems trust <players>`, `/gems untrust <players>`, and `/gems trustlist`
  manage who may use your gem.
- `/gems trade <gem>` trades for a gem you can claim.
- `/gems track <player>`
- `/gems bounty` opens the bounty board; `/gems assassin leave` is available after the choice unlocks.

### Admin commands (op level 2+)

Run `/gems admin help` for topic-based help (`setup`, `players`, `powers`,
`server`, `testing`). Second Chance has its own `/secondchance admin` helpers.
Run `/secondchance admin help` for a short command list. Use
`/secondchance admin sites <overworld|nether|end>` and
`/secondchance admin bosses` to browse valid IDs in game. Tab completion also
lists dimensions, sites, bosses, gems, and mod items:

- `/secondchance admin progress <player>` shows which bosses that player has found
  or cleared, and the shared Nether, End, and story choice progress.
- `/secondchance admin find <site>` searches for a naturally generated build and
  returns its start coordinates. Tab completion lists site names, such as
  `ninefold_maze`, `undercard`, `branch_underarchive`,
  `nour_sunken_relay`, `lastlight_ash_annex`, and `unwritten_end_source`.
  Run it as a Creative operator in the build's dimension.
  The search is bounded and may take several seconds. Its coordinates are a
  structure start, not a guaranteed safe landing: switch to Spectator mode and
  inspect the site before using vanilla `/tp`.
- `/secondchance admin kit <player>` gives missing travel tools (Rumor Atlas,
  Angle Lens, Return Stub) if the player has enough free inventory slots.
- `/secondchance admin grantClear <player> <boss>` grants one boss clear and its
  personal reward entitlement so later chapters can be staged quickly.
  This also opens that boss's shared world reward. It is a Creative-operator or
  console shortcut and cannot be undone by this command. It **does not** test
  the boss fight, puzzles, building access, natural discovery, or physical
  changes normally made in the arena when the boss falls.

`kit` also requires a Creative operator or the server console. For build layout
experiments, use vanilla `/place structure` only in a disposable test world;
large sites with custom terrain and connections still need natural generation
to judge the full player experience. The commands above never place or replace
terrain.

- `/gems reloadBalance`, `/gems reloadDisables`, `/gems dumpBalance`
- Player state:
  - `/gems admin status [players]` (omit the player to inspect yourself in game)
  - `/gems admin resync <players>`
  - `/gems admin reset <player>`
  - `/gems admin setGem <player> <gem>`
  - `/gems admin reclaimGem <players>`
  - `/gems admin setEnergy <players> <energy>`
  - `/gems admin setHearts <player> <hearts>`
  - `/gems admin giveItem <player> <itemId>`
  - `/gems admin giveAllGems <players>`
  - `/gems admin clearGems <players>`
  - `/gems admin cooldowns <players> <disabled>`
  - `/gems admin legendaryCooldowns <players> <disabled>`
  - `/gems admin stats show <players>`
  - `/gems admin stats reset <players>`
- Testing helpers:
  - `/gems admin cast <player> <slot>`
  - `/gems admin perf reset`
  - `/gems admin perf snapshot [windowTicks]`
  - `/gems admin stress start <players> <seconds> <periodTicks> <mode> <cycleGems> <forceEnergy10>`
  - `/gems admin stress stop <players>`

## Performance tips

- If your server is large, consider lowering particle/sound load via the global visual knobs in `config/gems/balance.json` (see the [Gems balance reference](https://github.com/elephancy/Gems/blob/main/balance.md)).
- Use `spark` to capture CPU profiles during heavy PvP and adjust the most expensive abilities (radii, durations, particle caps).

## Troubleshooting

- Config reload fails: `balance.json` is invalid JSON, or values were clamped; use `/gems dumpBalance` to see the effective values.
- Clients can’t change keybinds: keybinds are configured in Minecraft options under Controls -> Gems; client config is not synced server-side.
