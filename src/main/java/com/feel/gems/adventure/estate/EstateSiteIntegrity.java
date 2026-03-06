package com.feel.gems.adventure.estate;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.worldgen.site.GuardedNewChunkSiteAccess;
import com.feel.gems.adventure.worldgen.site.SiteAccess;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Read-only, loaded-chunk sweep for an authored estate start. */
public final class EstateSiteIntegrity {
    public enum Result {
        READY, WRONG_PIECES, CHUNK_UNAVAILABLE, OLD_CHUNK,
        PROTECTION_UNPROVEN, PROTECTED_AREA, SUBSTRATE_MISMATCH,
        CANOPY_INTRUSION, UNSUPPORTED_FOUNDATION, EXTERIOR_UNBOUNDED, BROKEN_SEAM,
        MISSING_MARKER, BLOCKED_LANDING
    }

    private enum Marker { NORTH, EAST, SOUTH, WEST, FOYER, RETURN }
    private static final int APRON = 12;
    private static final ResourceKey<Biome> SUBSTRATE = ResourceKey.create(
            Registries.BIOME, Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
                    "estate_commons"));
    private EstateSiteIntegrity() { }

    /** Local route safety only. Full foreign-start proof remains the separate
     *  placement/audit gate and cannot be inferred from this result. */
    static Result inspectEntryLoaded(ServerLevel level, EstateRouteSite.Site site,
                                     BlockPos marker, char wing) {
        if (level.getChunkSource().getChunkNow(marker.getX() >> 4,
                marker.getZ() >> 4) == null) return Result.CHUNK_UNAVAILABLE;
        if (site == null || site.gate(wing) == null
                || !site.gate(wing).marker().equals(marker)
                || EstateBlocks.wing(level.getBlockState(marker).getBlock()) != wing
                || !clearLanding(level, marker)) return Result.BLOCKED_LANDING;
        EstateRouteSite.Gate selected = site.gate(wing);
        BlockPos end = selected.at(selected.distance() + 8, 1);
        BlockPos opposite = selected.at(selected.distance() + 8, -1);
        Result age = localLoaded(level, site, marker, end, opposite);
        return age == Result.READY
                ? exteriorApproach(level, marker, site.bounds()) : age;
    }

    /** Inspect one seam at the moment a player enters it, with no chunk request. */
    static Result inspectSeamLoaded(ServerLevel level, EstateRouteSite.Site site,
                                    EstateRouteSite.Seam seam) {
        BlockPos side = seam.at(12, 2);
        BlockPos other = seam.at(12, -2);
        Result age = localLoaded(level, site, seam.from(), side, other);
        if (age != Result.READY) return age;
        int previous = seam.from().getY();
        for (int step = 0; step <= 12; step++) {
            int y = seam.floorAt(step);
            if (Math.abs(y - previous) > 1) return Result.BROKEN_SEAM;
            previous = y;
            for (int lane = -1; lane <= 1; lane++) {
                BlockPos floor = seam.at(step, lane);
                if (!walkableFloor(level, floor)
                        || step > 0 && step < 12
                                && !level.getBlockState(floor).is(Blocks.SMOOTH_STONE))
                    return Result.BROKEN_SEAM;
                if (step > 0 && step < 12) {
                    for (int depth = 1; depth <= 2; depth++) {
                        var stringer = level.getBlockState(floor.below(depth));
                        if (!stringer.isSolid() || !stringer.getFluidState().isEmpty())
                            return Result.BROKEN_SEAM;
                    }
                }
                if (step % 6 == 0 && !anchoredBent(level, floor))
                    return Result.UNSUPPORTED_FOUNDATION;
            }
        }
        return Result.READY;
    }

    /** Called once when a traversing player enters a chunk, never each tick. */
    static Result inspectTraversedChunk(ServerLevel level, EstateRouteSite.Site site,
                                        ChunkPos chunk) {
        if (site == null) return Result.WRONG_PIECES;
        Result age = loadedNewChunk(level, chunk);
        return age != Result.READY ? age
                : site.sameLoadedChunk(level, chunk) ? Result.READY : Result.WRONG_PIECES;
    }

    /** Fresh central controls; this does not claim full-site readiness. */
    static Result inspectFoyerLoaded(ServerLevel level, EstateRouteSite.Site site,
                                     BlockPos foyer) {
        if (site == null || !site.foyer().equals(foyer)
                || !clearLanding(level, foyer)
                || level.getBlockState(foyer).getBlock() != EstateBlocks.FOYER)
            return Result.BLOCKED_LANDING;
        Result age = localLoaded(level, site,
                new BlockPos(foyer.getX() - 8, foyer.getY(),
                        foyer.getZ() - 8),
                new BlockPos(foyer.getX() + 8, foyer.getY(), foyer.getZ() + 8), foyer);
        if (age != Result.READY) return age;
        for (int x = foyer.getX() - 8; x <= foyer.getX() + 8; x++) {
            for (int z = foyer.getZ() - 8; z <= foyer.getZ() + 8; z++) {
                BlockPos at = new BlockPos(x, foyer.getY(), z);
                if (level.getBlockState(at).getBlock() == EstateBlocks.RETURN
                        && clearLanding(level, at)) return Result.READY;
            }
        }
        return Result.MISSING_MARKER;
    }

    private static Result localLoaded(ServerLevel level, EstateRouteSite.Site site,
                                      BlockPos first, BlockPos second, BlockPos third) {
        int minX = Math.min(first.getX(), Math.min(second.getX(), third.getX()));
        int maxX = Math.max(first.getX(), Math.max(second.getX(), third.getX()));
        int minZ = Math.min(first.getZ(), Math.min(second.getZ(), third.getZ()));
        int maxZ = Math.max(first.getZ(), Math.max(second.getZ(), third.getZ()));
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                Result result = loadedNewChunk(level, new ChunkPos(cx, cz));
                if (result != Result.READY) return result;
                if (!site.sameLoadedChunk(level, new ChunkPos(cx, cz)))
                    return Result.WRONG_PIECES;
            }
        }
        return Result.READY;
    }

    private static Result loadedNewChunk(ServerLevel level, ChunkPos chunk) {
        GuardedNewChunkSiteAccess access = new GuardedNewChunkSiteAccess(level, Set.of());
        if (!access.canInspectChunk(chunk)) return Result.CHUNK_UNAVAILABLE;
        return access.isNewChunk(chunk) ? Result.READY : Result.OLD_CHUNK;
    }

    /**
     * Full-footprint inspection is deliberately opt-in, not a per-tick test.
     * It never requests a chunk. A pending chunk is not considered a safe
     * arrival. All 25 saved pieces, every occupied chunk and the six control
     * marker kinds must be present before returning READY.
     */
    public static Result inspectLoaded(ServerLevel level, StructureStart start) {
        if (start == null || !start.isValid()
                || !FourRoadsEstateStructure.completeGrid(start.getPieces())) {
            return Result.WRONG_PIECES;
        }
        BoundingBox bounds = start.getBoundingBox();
        EstateModuleGraph.Module[] modules = EstateModuleGraph.modules(start.getPieces());
        if (modules == null || EstateModuleGraph.seams(modules).size() != 24) {
            return Result.WRONG_PIECES;
        }
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "four_roads_estate_v1"));
        Structure estate = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(key);
        if (estate == null) return Result.PROTECTION_UNPROVEN;
        GuardedNewChunkSiteAccess age = new GuardedNewChunkSiteAccess(level, Set.of(estate));
        for (int cx = (bounds.minX() - APRON) >> 4;
             cx <= (bounds.maxX() + APRON) >> 4; cx++) {
            for (int cz = (bounds.minZ() - APRON) >> 4;
                 cz <= (bounds.maxZ() + APRON) >> 4; cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                if (!age.canInspectChunk(chunk)) return Result.CHUNK_UNAVAILABLE;
                if (!age.isNewChunk(chunk)) return Result.OLD_CHUNK;
            }
        }
        SiteAccess.Inspection protection = age.protectedVolume(bounds);
        if (protection == SiteAccess.Inspection.UNKNOWN) return Result.PROTECTION_UNPROVEN;
        if (protection == SiteAccess.Inspection.CONFLICT) return Result.PROTECTED_AREA;
        EstateRouteSite.Site savedRoute = EstateRouteSite.readLoaded(level,
                bounds.getCenter());
        if (!EstateRouteSite.matchesStart(savedRoute, start)) return Result.WRONG_PIECES;
        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                if (!savedRoute.sameLoadedChunk(level, new ChunkPos(cx, cz)))
                    return Result.WRONG_PIECES;
            }
        }
        if (!allowedCenterBiome(level, modules[12])) return Result.SUBSTRATE_MISMATCH;
        if (!supportedPlinth(level, modules)) return Result.UNSUPPORTED_FOUNDATION;
        if (!boundedExterior(level, modules, bounds)) return Result.EXTERIOR_UNBOUNDED;
        if (!clearRoadPassages(level, start, modules, bounds))
            return Result.CANOPY_INTRUSION;
        if (!walkableSeams(level, modules)) return Result.BROKEN_SEAM;

        EnumSet<Marker> found = EnumSet.noneOf(Marker.class);
        EnumSet<Marker> openApproaches = EnumSet.noneOf(Marker.class);
        EnumSet<Marker> pendingApproaches = EnumSet.noneOf(Marker.class);
        for (EstateModuleGraph.Module module : modules) {
            BoundingBox box = module.piece().getBoundingBox();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos marker = new BlockPos(x, module.floorY(), z);
                    LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    if (chunk == null) return Result.CHUNK_UNAVAILABLE;
                    Marker kind = kind(chunk.getBlockState(marker).getBlock());
                    if (kind == null) continue;
                    if (!clearLanding(level, marker)) return Result.BLOCKED_LANDING;
                    found.add(kind);
                    if (kind != Marker.FOYER && kind != Marker.RETURN) {
                        Result approach = exteriorApproach(level, marker, bounds);
                        if (approach == Result.READY) openApproaches.add(kind);
                        else if (approach == Result.CHUNK_UNAVAILABLE) pendingApproaches.add(kind);
                    }
                }
            }
        }
        if (found.size() != Marker.values().length) return Result.MISSING_MARKER;
        if (openApproaches.size() == 4) return Result.READY;
        if (pendingApproaches.size() > 0) return Result.CHUNK_UNAVAILABLE;
        return Result.BLOCKED_LANDING;
    }

    private static boolean allowedCenterBiome(ServerLevel level,
                                              EstateModuleGraph.Module mansion) {
        BoundingBox box = mansion.piece().getBoundingBox();
        var biome = level.getBiome(new BlockPos(box.getCenter().getX(),
                mansion.floorY(), box.getCenter().getZ()));
        return biome.is(SUBSTRATE) || biome.is(Biomes.DESERT)
                || biome.is(Biomes.PLAINS);
    }

    /** Compare the authored walking air, not harmless foliage above outer walls. */
    private static boolean clearRoadPassages(ServerLevel level, StructureStart start,
            EstateModuleGraph.Module[] modules, BoundingBox bounds) {
        long began = System.nanoTime();
        Set<Long> transitions = new HashSet<>();
        for (EstateModuleGraph.Seam seam : EstateModuleGraph.seams(modules)) {
            // afterPlace reauthors these five-wide stair volumes. The strict
            // three-lane floor/head/stringer audit runs immediately afterward.
            for (int step = 0; step <= 12; step++) {
                for (int lane = -2; lane <= 2; lane++) {
                    BlockPos pos = seam.at(step, lane);
                    transitions.add(ChunkPos.pack(pos.getX(), pos.getZ()));
                }
            }
        }
        List<EstateTerrainPlan.Gate> gates = EstateTerrainPlan.gates(start.getPieces());
        if (gates.size() != 4) return false;
        Set<Long> gateOpenings = new HashSet<>();
        for (EstateTerrainPlan.Gate gate : gates) {
            // The slope from each room to the wall is authored by afterPlace;
            // the loaded exteriorApproach audit checks all three lanes.
            for (int step = 1; step <= gate.distance(); step++) {
                for (int lane = -1; lane <= 1; lane++) {
                    BlockPos pos = gate.at(step, lane);
                    transitions.add(ChunkPos.pack(pos.getX(), pos.getZ()));
                    if (step == gate.distance())
                        gateOpenings.add(ChunkPos.pack(pos.getX(), pos.getZ()));
                }
            }
        }
        int checked = 0;
        for (EstateModuleGraph.Module module : modules) {
            if (module.id() == 12) continue; // The mansion has authored logs.
            PoolElementStructurePiece piece = module.piece();
            if (!(piece.getElement() instanceof SinglePoolElement single)) return false;
            var template = level.getServer().getStructureTemplateManager()
                    .getOrCreate(single.getTemplateLocation());
            var settings = new StructurePlaceSettings().setRotation(piece.getRotation());
            for (var authoredAir : template.filterBlocks(piece.getPosition(),
                    settings, Blocks.AIR)) {
                BlockPos pos = authoredAir.pos();
                int localY = pos.getY() - piece.getPosition().getY();
                if (localY < 5 || localY > 8
                        || transitions.contains(ChunkPos.pack(pos.getX(), pos.getZ())))
                    continue;
                var actual = level.getBlockState(pos);
                if (!actual.isAir() || !actual.getFluidState().isEmpty()) {
                    if (Boolean.getBoolean("gems.fourRoadsEstateGate"))
                        System.out.println("FOUR_ROADS_ESTATE_PASSAGE_OBSTRUCTION pos="
                                + pos + " state=" + actual
                                + " template=" + single.getTemplateLocation());
                    return false;
                }
                checked++;
            }
            BoundingBox box = piece.getBoundingBox();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (x != bounds.minX() && x != bounds.maxX()
                            && z != bounds.minZ() && z != bounds.maxZ()) continue;
                    if (gateOpenings.contains(ChunkPos.pack(x, z))) continue;
                    for (int y = box.minY() + 5; y <= box.minY() + 9; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        var wall = level.getBlockState(pos);
                        if (!wall.isSolid() || !wall.getFluidState().isEmpty()) {
                            if (Boolean.getBoolean("gems.fourRoadsEstateGate"))
                                System.out.println("FOUR_ROADS_ESTATE_PERIMETER_BREACH pos="
                                        + pos + " state=" + wall);
                            return false;
                        }
                    }
                }
            }
        }
        if (Boolean.getBoolean("gems.fourRoadsEstateGate"))
            System.out.println("FOUR_ROADS_ESTATE_PASSAGE_AUDIT checked=" + checked
                    + " ms=" + (System.nanoTime() - began) / 1_000_000);
        return true;
    }

    /** A 12-span deck rests on 2x2 piers and shallow cross-beams. */
    private static boolean supportedPlinth(ServerLevel level,
                                           EstateModuleGraph.Module[] modules) {
        for (EstateModuleGraph.Module module : modules) {
            BoundingBox bounds = module.piece().getBoundingBox();
            int baseY = module.baseY();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    boolean pier = EstateRaisedGeometry.footing(bounds, x, z);
                    if (EstateRaisedGeometry.beam(bounds, x, z)) {
                        for (int y = baseY - 1; y >= baseY - 2; y--) {
                            var state = level.getBlockState(new BlockPos(x, y, z));
                            if (!state.isSolid() || !state.getFluidState().isEmpty())
                                return false;
                        }
                    }
                    if (!pier) continue;
                    int naturalRun = 0;
                    boolean anchored = false;
                    for (int depth = 1;
                         depth <= EstateRaisedGeometry.MAX_SUPPORT_DEPTH; depth++) {
                        BlockPos pos = new BlockPos(x, baseY - depth, z);
                        var state = level.getBlockState(pos);
                        if (!state.isSolid() || !state.getFluidState().isEmpty()) return false;
                        if (!state.is(Blocks.STONE_BRICKS)
                                && !state.is(Blocks.DEEPSLATE_BRICKS)
                                && !state.is(Blocks.SMOOTH_STONE)
                                && ++naturalRun == 2) {
                            anchored = true;
                            break;
                        } else if (state.is(Blocks.STONE_BRICKS)
                                || state.is(Blocks.DEEPSLATE_BRICKS)
                                || state.is(Blocks.SMOOTH_STONE)) {
                            naturalRun = 0;
                        }
                    }
                    if (!anchored) return false;
                }
            }
        }
        return true;
    }

    /** All four visible edges meet water or natural ground within the pier cap. */
    private static boolean boundedExterior(ServerLevel level,
                                           EstateModuleGraph.Module[] modules,
                                           BoundingBox site) {
        long began = System.nanoTime();
        for (int x = site.minX(); x <= site.maxX(); x++) {
            if (!boundedExteriorColumn(level, modules, x, site.minZ())
                    || !boundedExteriorColumn(level, modules, x, site.maxZ()))
                return false;
        }
        for (int z = site.minZ() + 1; z < site.maxZ(); z++) {
            if (!boundedExteriorColumn(level, modules, site.minX(), z)
                    || !boundedExteriorColumn(level, modules, site.maxX(), z))
                return false;
        }
        if (Boolean.getBoolean("gems.fourRoadsEstateGate"))
            System.out.println("FOUR_ROADS_ESTATE_EXTERIOR_AUDIT columns="
                    + 2 * site.getXSpan() + 2 * (site.getZSpan() - 2)
                    + " ms=" + (System.nanoTime() - began) / 1_000_000);
        return true;
    }

    private static boolean boundedExteriorColumn(ServerLevel level,
            EstateModuleGraph.Module[] modules, int x, int z) {
        EstateModuleGraph.Module owner = null;
        for (EstateModuleGraph.Module module : modules) {
            BoundingBox box = module.piece().getBoundingBox();
            if (x >= box.minX() && x <= box.maxX()
                    && z >= box.minZ() && z <= box.maxZ()) {
                if (owner != null) return false;
                owner = module;
            }
        }
        if (owner == null) return false;
        for (int depth = 1; depth <= EstateRaisedGeometry.MAX_SUPPORT_DEPTH; depth++) {
            BlockPos pos = new BlockPos(x, owner.baseY() - depth, z);
            var state = level.getBlockState(pos);
            if (state.is(Blocks.WATER)
                    || !state.getFluidState().isEmpty()
                            && (state.is(Blocks.SEAGRASS)
                                    || state.is(Blocks.TALL_SEAGRASS)
                                    || state.is(Blocks.KELP)
                                    || state.is(Blocks.KELP_PLANT)
                                    || state.is(Blocks.SEA_PICKLE)
                                    || state.is(Blocks.BUBBLE_COLUMN))) return true;
            if (state.isSolid() && state.getFluidState().isEmpty()) {
                if (!state.is(Blocks.STONE_BRICKS)
                        && !state.is(Blocks.DEEPSLATE_BRICKS)
                        && !state.is(Blocks.SMOOTH_STONE)) return true;
                continue;
            }
            if (state.isAir()) continue;
            if (Boolean.getBoolean("gems.fourRoadsEstateGate"))
                System.out.println("FOUR_ROADS_ESTATE_EXTERIOR_OBSTRUCTION pos="
                        + pos + " state=" + state);
            return false;
        }
        if (Boolean.getBoolean("gems.fourRoadsEstateGate"))
            System.out.println("FOUR_ROADS_ESTATE_EXTERIOR_DROP x=" + x + " z=" + z
                    + " baseY=" + owner.baseY());
        return false;
    }

    private static boolean walkableSeams(ServerLevel level,
                                         EstateModuleGraph.Module[] modules) {
        for (EstateModuleGraph.Seam seam : EstateModuleGraph.seams(modules)) {
            int previous = seam.from().getY();
            for (int step = 0; step <= 12; step++) {
                int y = seam.floorAt(step);
                if (Math.abs(y - previous) > 1) return false;
                previous = y;
                for (int lane = -1; lane <= 1; lane++) {
                    BlockPos floor = seam.at(step, lane);
                    if (!walkableFloor(level, floor)
                            || step > 0 && step < 12
                                    && !level.getBlockState(floor).is(Blocks.SMOOTH_STONE)) {
                        return false;
                    }
                    if (step > 0 && step < 12) {
                        for (int depth = 1; depth <= 2; depth++) {
                            var stringer = level.getBlockState(floor.below(depth));
                            if (!stringer.isSolid() || !stringer.getFluidState().isEmpty())
                                return false;
                        }
                    }
                    if (step % 6 == 0 && !anchoredBent(level, floor)) return false;
                }
            }
        }
        return true;
    }

    private static boolean anchoredBent(ServerLevel level, BlockPos floor) {
        int naturalRun = 0;
        for (int depth = 1; depth <= EstateRaisedGeometry.MAX_SUPPORT_DEPTH; depth++) {
            var state = level.getBlockState(floor.below(depth));
            if (!state.isSolid() || !state.getFluidState().isEmpty()) return false;
            if (state.is(Blocks.STONE_BRICKS) || state.is(Blocks.DEEPSLATE_BRICKS)
                    || state.is(Blocks.SMOOTH_STONE)) {
                naturalRun = 0;
            } else if (++naturalRun == 2) {
                return true;
            }
        }
        return false;
    }

    /** A local entry or return can be checked without loading the far wings. */
    public static boolean clearLanding(ServerLevel level, BlockPos marker) {
        if (!level.getWorldBorder().isWithinBounds(marker)
                || level.getChunkSource().getChunkNow(marker.getX() >> 4,
                        marker.getZ() >> 4) == null) return false;
        Block stateBlock = level.getBlockState(marker).getBlock();
        return kind(stateBlock) != null
                && !level.getBlockState(marker).getCollisionShape(level, marker).isEmpty()
                && level.getBlockState(marker.above()).getCollisionShape(level, marker.above()).isEmpty()
                && level.getBlockState(marker.above(2)).getCollisionShape(level, marker.above(2)).isEmpty()
                && level.getFluidState(marker.above()).isEmpty()
                && level.getFluidState(marker.above(2)).isEmpty();
    }

    /** Three walkable lanes from an outer control stone to eight blocks beyond the wall. */
    private static Result exteriorApproach(ServerLevel level, BlockPos marker,
                                           BoundingBox bounds) {
        int west = marker.getX() - bounds.minX();
        int east = bounds.maxX() - marker.getX();
        int north = marker.getZ() - bounds.minZ();
        int south = bounds.maxZ() - marker.getZ();
        int distance = Math.min(Math.min(west, east), Math.min(north, south));
        if (distance > 12) return Result.BLOCKED_LANDING;
        int dx = distance == west ? -1 : distance == east ? 1 : 0;
        int dz = dx != 0 ? 0 : distance == north ? -1 : 1;
        // Worldgen grades this authored apron toward the untouched column just
        // outside the body. Its roof and parapet can be above the walkable
        // floor, so a heightmap is only authoritative beyond the body.
        int outerX = marker.getX() + dx * (distance + 1);
        int outerZ = marker.getZ() + dz * (distance + 1);
        LevelChunk outerChunk = level.getChunkSource().getChunkNow(outerX >> 4,
                outerZ >> 4);
        if (outerChunk == null) return Result.CHUNK_UNAVAILABLE;
        int outerY = outerChunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                outerX & 15, outerZ & 15);
        if (Math.abs(outerY - marker.getY()) > distance)
            return Result.BLOCKED_LANDING;
        int[] previous = {marker.getY(), marker.getY(), marker.getY()};
        for (int step = 1; step <= distance + 8; step++) {
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            for (int lane = -1; lane <= 1; lane++) {
                int x = marker.getX() + dx * step + dz * lane;
                int z = marker.getZ() + dz * step + dx * lane;
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) return Result.CHUNK_UNAVAILABLE;
                int y = step <= distance
                        ? marker.getY() + Math.round((outerY - marker.getY())
                                * (float) step / distance)
                        : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                x & 15, z & 15);
                BlockPos floor = new BlockPos(x, y, z);
                if (!level.getWorldBorder().isWithinBounds(floor)
                        || Math.abs(y - previous[lane + 1]) > 1
                        || (step <= distance
                                && !level.getBlockState(floor).is(Blocks.SMOOTH_STONE))
                        || !walkableFloor(level, floor)) return Result.BLOCKED_LANDING;
                previous[lane + 1] = y;
                low = Math.min(low, y);
                high = Math.max(high, y);
            }
            if (high - low > 1) return Result.BLOCKED_LANDING;
        }
        return Result.READY;
    }

    private static boolean walkableFloor(ServerLevel level, BlockPos floor) {
        return !level.getBlockState(floor).is(BlockTags.LOGS)
                && !level.getBlockState(floor).is(BlockTags.LEAVES)
                && !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                && level.getFluidState(floor).isEmpty()
                && level.getBlockState(floor.above()).getCollisionShape(level,
                        floor.above()).isEmpty()
                && level.getFluidState(floor.above()).isEmpty()
                && level.getBlockState(floor.above(2)).getCollisionShape(level,
                        floor.above(2)).isEmpty()
                && level.getFluidState(floor.above(2)).isEmpty();
    }

    private static Marker kind(Block block) {
        if (block == EstateBlocks.ENTRY_N) return Marker.NORTH;
        if (block == EstateBlocks.ENTRY_E) return Marker.EAST;
        if (block == EstateBlocks.ENTRY_S) return Marker.SOUTH;
        if (block == EstateBlocks.ENTRY_W) return Marker.WEST;
        if (block == EstateBlocks.FOYER) return Marker.FOYER;
        if (block == EstateBlocks.RETURN) return Marker.RETURN;
        return null;
    }
}
