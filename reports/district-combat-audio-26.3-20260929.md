# District combat audio, 26.3

Four boss districts previously shared magical lock/strike effects. Their bosses and regional sentries now use distinct foley families, preserving the same server-authoritative sound timing. Redline's successful shelter cue also changes to a separate light metal hit.

| District | Call | Lock | Strike | Other |
| --- | --- | --- | --- | --- |
| Lastlight station | Clockwork metal click | Metal latch | Bell impact | — |
| Rehearsal stage | Costume strap | Stage plank | Heavy timber impact | — |
| Branch archive | Page flip | Book close | Glass break | — |
| Redline refuge | Metal vessel warning | Medium plate hit | Heavy plate hit | Light tin shelter hit |

All 13 files are unchanged Ogg Vorbis selections from Kenney's CC0 [RPG Audio](https://kenney.nl/assets/rpg-audio) and [Impact Sounds](https://kenney.nl/assets/impact-sounds). Both archives' included `License.txt` files explicitly permit commercial use. `tools/adventure/import_district_combat_audio.py` checks the licenses, Ogg headers, and 0.05–1.5 second duration and carries the exact original filenames. The source archives were downloaded into ignored `reports/audio-source/`; they are not part of the distributable jar.

| Output file | Duration | SHA-256 |
| --- | ---: | --- |
| `lastlight_call.ogg` | 0.45s | `9851a69d0c613e13bceef08060ecc4148f098ef487927cbebe270d642398a3b3` |
| `lastlight_lock.ogg` | 0.26s | `ba9ba60b172b3ebc131a940f25793cd2e207aca7af73dc80d637277f060f1708` |
| `lastlight_strike.ogg` | 1.48s | `94b8bb5f2d43ab65e4bcc32b28562416e9bc2c51d9fd4be1e333660ee52f977f` |
| `rehearsal_call.ogg` | 0.72s | `f29560a2aea95bbfeb643e1e8f56ec2b6f7e32f32ce4e140645a36db81de3253` |
| `rehearsal_lock.ogg` | 0.78s | `8403cd7a043f0391574c88c55dd205743725b5363d2407f6de145f201690b68f` |
| `rehearsal_strike.ogg` | 0.31s | `15ff82332f342c30e469215539e1cc57e11b221aa2cf7e11e811d313a9927f3d` |
| `branch_call.ogg` | 0.43s | `3409042acbfaebc24c1a6327dc073e36f9250841712e96c5040da566a97df73c` |
| `branch_lock.ogg` | 0.23s | `81e976532565f4372abd14e83d2684195fa548d0a28d345de221e56052454f32` |
| `branch_strike.ogg` | 0.24s | `b44b39a940e8948e74b9bd3776bff980df43cf220abdaf0d467dc4c43c0244a5` |
| `redline_call.ogg` | 1.46s | `159def979e8e386c2c539f5e99cc30a080eb2dcb6c911fa2e4ccc0785b2522fd` |
| `redline_lock.ogg` | 0.61s | `b670d30452983369771081319c268475edaaafe782bf4e8d70bd7eef09d8823c` |
| `redline_strike.ogg` | 0.49s | `112d4f93ddcc370b410630f971c0f5d991856102da9c76bc5c5540d388e75aaa` |
| `redline_shelter.ogg` | 0.16s | `f4bbee66ca191ec744b84fc07ac9ed2a6fda955d51cfed8360f12b62815d3cbd` |

`python tools/adventure/verify_audio_manifest.py` passes: all 33 registered events have matching Ogg Vorbis files and English subtitle keys present in `en_us.json`. Combat captions describe the encounter state. Build and in-game sound playback remain to verify after concurrent terrain/multiplayer work settles. I have not auditioned the clips, so sonic fit and mix are open acceptance checks.
