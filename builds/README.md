# Editing a Second Chance build

This is the Minecraft 26.3 build source. Each active structure piece has one
editable `.schem` file listed in `manifest.json`. The matching `.nbt` under
`src/main/resources/data/gems/structure/` is generated output. Gradle runs the
converter before it packages resources. Unchanged source produces identical
output bytes, and a second build skips unchanged conversions.

## Edit an existing place

Open the relevant `.schem` in a schematic editor, edit its blocks, and save it
at the same path. Run `./gradlew build` (or `gradlew.bat build` on Windows).
`minecraft:structure_void` means **leave the world untouched** at that cell;
`minecraft:air` means **clear the cell**. Preserve the schematic's dimensions,
entry/exit jigsaws, and gameplay entities unless you intentionally update the
matching marker record in `manifest.json`. The converter stops if required
markers disappear or an unapproved block ID appears.

Standard Sponge v3 block entities and entities are imported. Check the
resulting build in a fresh world: the converter verifies data and markers, not
whether a doorway is walkable or a facade fits its terrain.

## Import or export

From the repository root:

```text
python -B tools/adventure/schematic_pipeline.py inventory
python -B tools/adventure/schematic_pipeline.py export src/main/resources/data/gems/structure/NAME.nbt NAME.schem
python -B tools/adventure/schematic_pipeline.py export src/main/resources/data/gems/structure/NAME.nbt NAME.litematic
python -B tools/adventure/schematic_pipeline.py import NAME.litematic NAME.nbt
```

To make an imported file the source of an active piece, place it in this folder
and point that piece's `source` field in `manifest.json` to it. The build then
converts it automatically. For a new piece, its template pool and any gameplay
markers must also be defined. Keep credits and permission evidence for any
community build; a free download by itself does not grant redistribution.

The importer accepts Sponge schematic v2/v3 and Litematic files with 26.3 data
version. It merges multiple positive-size Litematic regions and normalizes
their offsets. Negative-size regions, conflicting overlaps, unknown blocks,
and unsupported entity layouts stop with an error. Older Minecraft data
versions must first be migrated in game; changing the version number alone
does not migrate block/entity data. New block IDs must be checked against the
26.3 registry before adding them to `known_blocks.txt`.

The converter currently writes generated NBT in the resource source directory
because existing worldgen verifiers read those files directly. The schematic
and manifest are the authored source; do not hand-edit generated NBT. Existing
world chunks are never replaced by rebuilding the mod.

Format references: [Sponge Schematic v3](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md)
and [Litematica's file reader/writer](https://github.com/maruohon/litematica/blob/pre-rewrite/fabric/1.21.1-masa/src/main/java/fi/dy/masa/litematica/schematic/LitematicaSchematic.java).
