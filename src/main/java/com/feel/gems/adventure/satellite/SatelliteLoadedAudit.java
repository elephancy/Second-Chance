package com.feel.gems.adventure.satellite;

import com.feel.gems.GemsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/** One-shot, read-only proof of a placed, saved satellite start. Never loads chunks. */
public final class SatelliteLoadedAudit {
    public enum Verdict {
        READY, WRONG_START, CHUNK_UNAVAILABLE, PROTECTION_UNPROVEN,
        STRUCTURE_CONFLICT, WRONG_DEPTH, EXTERIOR_GAP, BLOCKED_LANDING,
        MISSING_CONTROL, DEEP_DARK, SCULK, ANCIENT_CITY
    }

    public enum Site { OCEAN, ARCHIVE }

    private static final ResourceKey<Structure> CITY = ResourceKey.create(
            Registries.STRUCTURE, Identifier.withDefaultNamespace("ancient_city"));
    private static final Identifier OCEAN_V1 = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "nour_sunken_relay_v1");
    private static final Identifier OCEAN_V2 = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "nour_sunken_relay_v2");
    private static final Identifier OCEAN_V3 = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "nour_sunken_relay_v3");
    private static final Identifier OCEAN_V4 = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "nour_sunken_relay_v4");
    private static final Identifier ARCHIVE_V1 = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "branch_underarchive_v1");
    private static final Identifier ARCHIVE_V2 = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "branch_underarchive_v2");

    private SatelliteLoadedAudit() { }

    public static Verdict inspectLoaded(ServerLevel level, StructureStart own, Site site) {
        if (own == null || !own.isValid() || own.getPieces().size() != 1) {
            return Verdict.WRONG_START;
        }
        StructurePiece ownPiece = own.getPieces().getFirst();
        if (!(ownPiece instanceof PoolElementStructurePiece pooled)
                || !(pooled.getElement() instanceof SinglePoolElement single)) {
            return Verdict.WRONG_START;
        }
        Identifier template = single.getTemplateLocation();
        int height = template.equals(OCEAN_V4) ? 50 : site == Site.OCEAN ? 43 : 58;
        if (site == Site.OCEAN ? !template.equals(OCEAN_V1) && !template.equals(OCEAN_V2)
                && !template.equals(OCEAN_V3) && !template.equals(OCEAN_V4)
                : !template.equals(ARCHIVE_V1) && !template.equals(ARCHIVE_V2))
            return Verdict.WRONG_START;
        BoundingBox box = ownPiece.getBoundingBox();
        if (box.getXSpan() != 47 || box.getYSpan() != height || box.getZSpan() != 47
                || box.minY() <= level.getMinY() || box.maxY() >= level.getMaxY()) {
            return Verdict.WRONG_START;
        }
        BlockPos root = new BlockPos(box.minX() + 23,
                box.minY() + (site == Site.OCEAN ? 19 : 28), box.minZ() + 23);
        if (site == Site.OCEAN ? box.minY() < SatelliteTerrainBounds.OCEAN_MIN_Y
                || box.minY() > SatelliteTerrainBounds.OCEAN_MAX_Y
                : root.getY() < SatelliteTerrainBounds.ARCHIVE_ROOT_MIN_Y
                || root.getY() > SatelliteTerrainBounds.ARCHIVE_ROOT_MAX_Y)
            return Verdict.WRONG_DEPTH;

        int apron = site == Site.OCEAN ? 8 : 2;
        BoundingBox protectedBox = new BoundingBox(box.minX() - apron, box.minY() - 1,
                box.minZ() - apron, box.maxX() + apron, box.maxY() + 1,
                box.maxZ() + apron);
        Verdict available = loaded(level, protectedBox);
        if (available != Verdict.READY) return available;
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(ResourceKey.create(Registries.STRUCTURE,
                        Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
                                site == Site.OCEAN ? "nour_sunken_relay_v1"
                                        : "branch_underarchive_v1")));
        if (structure == null) return Verdict.PROTECTION_UNPROVEN;
        Verdict neighbors = protectedNeighbors(level, own, structure, protectedBox);
        if (neighbors != Verdict.READY) return neighbors;

        Block expectedRoot = site == Site.OCEAN ? SatelliteBlocks.SUNKEN_ROOT
                : SatelliteBlocks.UNDERARCHIVE_ROOT;
        if (!level.getBlockState(root).is(expectedRoot)) return Verdict.WRONG_START;
        BlockPos entry = site == Site.OCEAN ? root.above(19) : root.above(22);
        BlockPos interior = site == Site.OCEAN ? root.below(13) : root.below(22);
        if (!safe(level, entry) || !safe(level, interior)) return Verdict.BLOCKED_LANDING;
        if (site == Site.OCEAN) {
            if (!hasControl(level, box, 37, SatelliteBlocks.SUNKEN_BUOY)
                    || !hasControl(level, box, 5, SatelliteBlocks.SUNKEN_RETURN)) {
                return Verdict.MISSING_CONTROL;
            }
            // Earlier saved templates retain their original broad control
            // check. V4's five stations have fixed, rotated positions, so a
            // moved or missing hydrophone cannot leave the site marked ready.
            if (template.equals(OCEAN_V4)
                    && !oceanV4Controls(level, box, pooled.getRotation())) {
                return Verdict.MISSING_CONTROL;
            }
            return oceanSeam(level, box,
                    template.equals(OCEAN_V2) || template.equals(OCEAN_V3)
                            || template.equals(OCEAN_V4), template.equals(OCEAN_V4));
        }
        if (!hasControl(level, box, 49, SatelliteBlocks.UNDERARCHIVE_CLUE)
                || !hasControl(level, box, 5, SatelliteBlocks.UNDERARCHIVE_RETURN)) {
            return Verdict.MISSING_CONTROL;
        }
        Verdict seam = archiveSeam(level, box, pooled.getRotation());
        return seam == Verdict.READY ? archiveHazards(level, root, box) : seam;
    }

    private static Verdict loaded(ServerLevel level, BoundingBox box) {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                    return Verdict.CHUNK_UNAVAILABLE;
                }
            }
        }
        return Verdict.READY;
    }

    /** Ignore exactly the saved start's structure/start-chunk pair, never its whole type. */
    private static Verdict protectedNeighbors(ServerLevel level, StructureStart own,
                                               Structure ownStructure, BoundingBox volume) {
        long ownChunk = own.getChunkPos().pack();
        for (int cx = volume.minX() >> 4; cx <= volume.maxX() >> 4; cx++) {
            for (int cz = volume.minZ() >> 4; cz <= volume.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                for (var entry : chunk.getAllReferences().entrySet()) {
                    for (long reference : entry.getValue()) {
                        LevelChunk startChunk = level.getChunkSource().getChunkNow(
                                ChunkPos.getX(reference), ChunkPos.getZ(reference));
                        if (startChunk == null) return Verdict.PROTECTION_UNPROVEN;
                        StructureStart other = startChunk.getStartForStructure(entry.getKey());
                        if (other == null || !other.isValid()) return Verdict.PROTECTION_UNPROVEN;
                        if (entry.getKey() == ownStructure && reference == ownChunk) continue;
                        for (StructurePiece piece : other.getPieces()) {
                            if (piece.getBoundingBox().intersects(volume)) {
                                return Verdict.STRUCTURE_CONFLICT;
                            }
                        }
                    }
                }
            }
        }
        return Verdict.READY;
    }

    private static Verdict oceanSeam(ServerLevel level, BoundingBox box,
                                     boolean retainedFoot, boolean v4) {
        // The authored foot is the bounded foundation. The entire dry chamber
        // must remain unflooded even where a natural trench rises against the
        // outer glass; no water is cleared beyond the saved footprint.
        for (int x = 0; x < 47; x++) {
            for (int z = 0; z < 47; z++) {
                if (octagon(x, z, retainedFoot ? 23 : 21)) {
                    for (int y = 0; y <= 3; y++) {
                        var foot = level.getBlockState(new BlockPos(box.minX() + x,
                                box.minY() + y, box.minZ() + z));
                        if (!foot.isSolid() || !foot.getFluidState().isEmpty()
                                || retainedFoot && !foot.is(SatelliteBlocks.SUNKEN_DECK_TILE))
                            return gap(level, "ocean foot", box.minX() + x,
                                    box.minY() + y, box.minZ() + z);
                    }
                    if (retainedFoot && !octagon(x, z, 21)) {
                        var cap = level.getBlockState(new BlockPos(box.minX() + x,
                                box.minY() + 4, box.minZ() + z));
                        Block expected = octagon(x, z, 22)
                                ? SatelliteBlocks.SUNKEN_DECK_TILE
                                : SatelliteBlocks.SUNKEN_WAVE_INLAY;
                        if (!cap.is(expected) || !cap.getFluidState().isEmpty())
                            return gap(level, "ocean seawall cap", box.minX() + x,
                                    box.minY() + 4, box.minZ() + z);
                    }
                }
                if (!octagon(x, z, 17)) continue;
                for (int y = 5; y <= 19; y++) {
                    if (!level.getFluidState(new BlockPos(box.minX() + x,
                            box.minY() + y, box.minZ() + z)).isEmpty())
                        return gap(level, "ocean chamber flood", box.minX() + x,
                                box.minY() + y, box.minZ() + z);
                }
            }
        }
        // V1 needs the original four natural layers beyond its small foot.
        // Later versions have four verified authored layers through the footprint edge.
        // Both still require the same exterior seabed height and water column.
        for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
            for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
                if (x > box.minX() - 1 && x < box.maxX() + 1
                        && z > box.minZ() - 1 && z < box.maxZ() + 1) continue;
                int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
                // Final carving can deepen water at an empty corner of the
                // square template box. That corner cannot undercut the
                // octagonal sealed foot; a trench beside the foot still fails.
                boolean emptyCornerTrench = v4 && floor >= box.minY() - 4
                        && floor < box.minY()
                        && !touchesRetainedFoot(box, x, z);
                if (!(v4 ? SatelliteTerrainBounds.oceanV4Floor(box.minY(), floor)
                                || emptyCornerTrench
                        : SatelliteTerrainBounds.oceanFloor(box.minY(), floor))) {
                    return gap(level, "ocean exterior floor " + floor, x, floor, z);
                }
                if (v4 && floor == box.minY()) {
                    for (int y = floor - 3; y <= floor; y++) {
                        if (!drySolid(level, x, y, z))
                            return gap(level, "ocean level-bed support", x, y, z);
                    }
                }
                if (!retainedFoot) {
                    for (int y = floor - 3; y <= floor; y++) {
                        var support = level.getBlockState(new BlockPos(x, y, z));
                        if (!support.isSolid() || !support.getFluidState().isEmpty()
                                || support.hasBlockEntity()) {
                            return gap(level, "ocean exterior support", x, y, z);
                        }
                    }
                }
                for (int y = floor + 1; y <= 62; y++) {
                    if (!level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER)) {
                        return gap(level, "ocean exterior water", x, y, z);
                    }
                }
            }
        }
        // A boat needs open water outside the floating dock, not an adjacent shore.
        for (int x = box.minX() - 8; x <= box.maxX() + 8; x += 4) {
            for (int z = box.minZ() - 8; z <= box.maxZ() + 8; z += 4) {
                if (x >= box.minX() && x <= box.maxX()
                        && z >= box.minZ() && z <= box.maxZ()) continue;
                if (level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1
                        > SatelliteTerrainBounds.OCEAN_SHORE_MAX_Y)
                    return gap(level, "ocean approach shore", x,
                            level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1, z);
                for (int y : new int[]{55, 62}) {
                    if (!level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER)) {
                        return gap(level, "ocean approach water", x, y, z);
                    }
                }
                if (v4 && (!level.getBlockState(new BlockPos(x, 63, z)).isAir()
                        || !level.getBlockState(new BlockPos(x, 64, z)).isAir())) {
                    return gap(level, "ocean approach air", x, 63, z);
                }
            }
        }
        return Verdict.READY;
    }

    private static boolean touchesRetainedFoot(BoundingBox box, int x, int z) {
        int localX = Math.max(0, Math.min(46, x - box.minX()));
        int localZ = Math.max(0, Math.min(46, z - box.minZ()));
        return octagon(localX, localZ, 23);
    }

    private static boolean oceanV4Controls(ServerLevel level, BoundingBox box,
                                           Rotation rotation) {
        return v4Control(level, box, rotation, 0, -6, 37, SatelliteBlocks.SUNKEN_BUOY)
                && v4Control(level, box, rotation, -11, 0, 5,
                        SatelliteBlocks.SUNKEN_WEST_HYDROPHONE)
                && v4Control(level, box, rotation, 11, 0, 5,
                        SatelliteBlocks.SUNKEN_EAST_HYDROPHONE)
                && v4Control(level, box, rotation, 0, 0, 5,
                        SatelliteBlocks.SUNKEN_TUNING_CONSOLE)
                && v4Control(level, box, rotation, 0, -8, 5,
                        SatelliteBlocks.SUNKEN_RETURN);
    }

    private static boolean v4Control(ServerLevel level, BoundingBox box,
                                     Rotation rotation, int east, int north,
                                     int localY, Block control) {
        BlockPos pos = SatelliteWalkIn.point(box, rotation, east, north,
                box.minY() + localY);
        return level.getBlockState(pos).is(control);
    }

    private static Verdict gap(ServerLevel level, String reason, int x, int y, int z) {
        if (Boolean.getBoolean("gems.satellitePlacementDiagnostics")) {
            GemsMod.LOGGER.warn("Satellite saved-world safety gap: {} at {},{},{}",
                    reason, x, y, z);
        }
        return Verdict.EXTERIOR_GAP;
    }

    private static boolean octagon(int x, int z, int radius) {
        int dx = Math.abs(x - 23);
        int dz = Math.abs(z - 23);
        return Math.max(dx, dz) <= radius && dx + dz <= radius * 3 / 2;
    }

    private static Verdict archiveSeam(ServerLevel level, BoundingBox box,
                                       Rotation rotation) {
        // The 41-wide authored stone shell, its floor and the narrow ascent
        // tube protect the dry hall. Natural caves outside that shell need not
        // be filled or erased in neighboring chunks.
        for (int x = 3; x <= 43; x++) {
            for (int z = 3; z <= 43; z++) {
                for (int y : new int[]{0, 1}) {
                    if (!drySolid(level, box.minX() + x, box.minY() + y,
                            box.minZ() + z)) return Verdict.EXTERIOR_GAP;
                }
                if (x > 4 && x < 42 && z > 4 && z < 42) continue;
                for (int y = 2; y <= 30; y++) {
                    if (!drySolid(level, box.minX() + x, box.minY() + y,
                            box.minZ() + z)) return Verdict.EXTERIOR_GAP;
                }
            }
        }
        for (int x = 19; x <= 27; x++) {
            for (int z = 19; z <= 27; z++) {
                if (x > 19 && x < 27 && z > 19 && z < 27) continue;
                for (int y = 31; y <= 48; y++) {
                    if (!drySolid(level, box.minX() + x, box.minY() + y,
                            box.minZ() + z)) return Verdict.EXTERIOR_GAP;
                }
            }
        }
        for (int x = 7; x <= 39; x++) {
            for (int z = 7; z <= 39; z++) {
                for (int y = 5; y <= 27; y++) {
                    if (!level.getFluidState(new BlockPos(box.minX() + x,
                            box.minY() + y, box.minZ() + z)).isEmpty())
                        return Verdict.EXTERIOR_GAP;
                }
            }
        }
        int centerX = box.minX() + 23;
        int centerZ = box.minZ() + 23;
        for (int x : new int[]{box.minX() + 4, centerX, box.maxX() - 4}) {
            for (int z : new int[]{box.minZ() + 4, centerZ, box.maxZ() - 4}) {
                for (int y = box.minY() - 4; y < box.minY(); y++) {
                    if (!drySolid(level, x, y, z)) return Verdict.EXTERIOR_GAP;
                }
            }
        }
        for (int lateral = -1; lateral <= 1; lateral++) {
            for (int outward = 7; outward <= 8; outward++) {
                BlockPos walk = SatelliteWalkIn.point(box, rotation,
                        lateral, outward, box.minY() + 49);
                int x = walk.getX();
                int z = walk.getZ();
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        x, z) - 1;
                if (surface < box.minY() + 48 || surface > box.minY() + 50
                        || !drySolid(level, x, surface, z)) return Verdict.EXTERIOR_GAP;
                BlockPos feet = new BlockPos(x, surface + 1, z);
                if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        || !level.getBlockState(feet.above())
                                .getCollisionShape(level, feet.above()).isEmpty()
                        || !level.getFluidState(feet).isEmpty()
                        || !level.getFluidState(feet.above()).isEmpty())
                    return Verdict.BLOCKED_LANDING;
            }
        }
        return Verdict.READY;
    }

    private static boolean drySolid(ServerLevel level, int x, int y, int z) {
        var state = level.getBlockState(new BlockPos(x, y, z));
        return state.isSolid() && state.getFluidState().isEmpty();
    }

    private static Verdict archiveHazards(ServerLevel level, BlockPos root, BoundingBox box) {
        Structure city = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(CITY);
        if (city == null) return Verdict.PROTECTION_UNPROVEN;
        int minX = root.getX() - 128, maxX = root.getX() + 128;
        int minZ = root.getZ() - 128, maxZ = root.getZ() + 128;
        Verdict available = loaded(level, new BoundingBox(minX, level.getMinY(), minZ,
                maxX, box.maxY(), maxZ));
        if (available != Verdict.READY) return available;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (!chunk.getReferencesForStructure(city).isEmpty()
                        || chunk.getStartForStructure(city) != null) return Verdict.ANCIENT_CITY;
                var sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    int baseY = level.getSectionYFromSectionIndex(index) << 4;
                    if (baseY > box.maxY()) break;
                    var section = sections[index];
                    // Palette rejection makes the full halo cheap in ordinary terrain.
                    if (section.getBiomes().maybeHas(biome -> biome.is(Biomes.DEEP_DARK))) {
                        for (int wx = Math.max(cx << 4, minX);
                             wx <= Math.min((cx << 4) + 15, maxX); wx += 4) {
                            for (int wz = Math.max(cz << 4, minZ);
                                 wz <= Math.min((cz << 4) + 15, maxZ); wz += 4) {
                                for (int y = Math.max(baseY, level.getMinY());
                                     y <= Math.min(baseY + 15, box.maxY()); y += 4) {
                                    if (level.getBiome(new BlockPos(wx, y, wz))
                                            .is(Biomes.DEEP_DARK)) return Verdict.DEEP_DARK;
                                }
                            }
                        }
                    }
                    int sculkMinX = root.getX() - 64, sculkMaxX = root.getX() + 64;
                    int sculkMinZ = root.getZ() - 64, sculkMaxZ = root.getZ() + 64;
                    if ((cx << 4) > sculkMaxX || (cx << 4) + 15 < sculkMinX
                            || (cz << 4) > sculkMaxZ || (cz << 4) + 15 < sculkMinZ
                            || !section.maybeHas(SatelliteLoadedAudit::sculk)) continue;
                    for (int x = Math.max(0, sculkMinX - (cx << 4));
                         x <= Math.min(15, sculkMaxX - (cx << 4)); x++) {
                        for (int z = Math.max(0, sculkMinZ - (cz << 4));
                             z <= Math.min(15, sculkMaxZ - (cz << 4)); z++) {
                            for (int y = Math.max(0, level.getMinY() - baseY);
                                 y <= Math.min(15, box.maxY() - baseY); y++) {
                                if (sculk(section.getBlockState(x, y, z))) return Verdict.SCULK;
                            }
                        }
                    }
                }
            }
        }
        return Verdict.READY;
    }

    private static boolean sculk(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.SCULK) || state.is(Blocks.SCULK_VEIN)
                || state.is(Blocks.SCULK_SENSOR) || state.is(Blocks.SCULK_SHRIEKER)
                || state.is(Blocks.SCULK_CATALYST);
    }

    private static boolean hasControl(ServerLevel level, BoundingBox box, int localY,
                                      Block control) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (level.getBlockState(new BlockPos(x, box.minY() + localY, z))
                        .is(control)) return true;
            }
        }
        return false;
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        if (!level.getWorldBorder().isWithinBounds(feet)
                || level.getChunkSource().getChunkNow(feet.getX() >> 4,
                        feet.getZ() >> 4) == null) return false;
        return !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(feet.above()).isEmpty();
    }
}
