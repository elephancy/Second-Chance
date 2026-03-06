package com.feel.gems.adventure.undercard;

import com.feel.gems.adventure.worldgen.site.GuardedNewChunkSiteAccess;
import com.feel.gems.adventure.worldgen.site.SiteAccess;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.AABB;

/** Loaded-only post-generation audit before opening the one sealed ladder rung. */
public final class UndercardSiteIntegrity {
    public enum Status { READY, WAIT, REJECTED }
    public record Inspection(Status status, String reason, BlockPos root, BlockPos gate) {
        static Inspection waitFor(String reason) {
            return new Inspection(Status.WAIT, reason, null, null);
        }
        static Inspection reject(String reason) {
            return new Inspection(Status.REJECTED, reason, null, null);
        }
        static Inspection ready(BlockPos root, BlockPos gate) {
            return new Inspection(Status.READY, "ready", root.immutable(), gate.immutable());
        }
    }

    private static final int HAZARD_RADIUS = UndercardSite.HAZARD_HALO;
    private static final int LOADED_HAZARD_CORE = 64;
    private static final int APPROACH = 8;
    private static final ResourceKey<Structure> CITY = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath("minecraft", "ancient_city"));

    private UndercardSiteIntegrity() { }

    /** Never loads chunks or changes blocks. Called only for a saved structure start. */
    public static Inspection inspectLoaded(ServerLevel level, StructureStart start,
                                           Structure structure) {
        if (start == null || !start.isValid()
                || !UndercardStructure.oneAuthoredPiece(start.getPieces())) {
            return Inspection.reject("wrong saved piece");
        }
        BoundingBox box = start.getPieces().getFirst().getBoundingBox();
        if (box.minY() < level.getMinY() || box.maxY() >= level.getMaxY()) {
            return Inspection.reject("world height");
        }
        GuardedNewChunkSiteAccess access = new GuardedNewChunkSiteAccess(level,
                Set.of(structure));
        for (int cx = (box.minX() - APPROACH) >> 4;
             cx <= (box.maxX() + APPROACH) >> 4; cx++) {
            for (int cz = (box.minZ() - APPROACH) >> 4;
                 cz <= (box.maxZ() + APPROACH) >> 4; cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                if (!access.canInspectChunk(chunk)) return Inspection.waitFor("site chunk unloaded");
                if (!access.isNewChunk(chunk)) return Inspection.reject("old chunk in site or approach");
            }
        }
        Inspection neighbors = referencedStarts(level, box, structure);
        if (neighbors != null) return neighbors;
        SiteAccess.Inspection protectedArea = access.protectedVolume(box);
        if (protectedArea == SiteAccess.Inspection.UNKNOWN) {
            return Inspection.waitFor("other structure start unavailable");
        }
        if (protectedArea == SiteAccess.Inspection.CONFLICT) {
            return Inspection.reject("other structure overlaps venue");
        }
        BlockPos root = new BlockPos(box.getCenter().getX(), box.minY() + 22,
                box.getCenter().getZ());
        if (!level.getBlockState(root).is(UndercardBlocks.ROOT)) {
            return Inspection.reject("saved root marker missing");
        }
        BlockPos gate = gate(level, root);
        if (gate == null) return Inspection.reject("sealed gate or ladder backing missing");
        if (!ladder(level, gate)) return Inspection.reject("ladder shaft broken");
        if (!surfaceApproach(level, root, gate)) return Inspection.reject("surface entry blocked");
        boolean sealedV5 = ((SinglePoolElement) ((PoolElementStructurePiece)
                start.getPieces().getFirst()).getElement()).getTemplateLocation()
                .getPath().equals("undercard_ring_v5");
        if (!roomEnvelope(level, box, gate, sealedV5)) {
            return Inspection.reject("room shell breached");
        }
        // Fluid updates can briefly cross the hall after a newly generated
        // aquifer drains. Keep the bounded load and retry before deciding the
        // saved route is permanently blocked.
        if (!reachableLitRoute(level, box, gate))
            return Inspection.waitFor("route still dark or blocked");
        Inspection hazard = noDeepHazard(level, root);
        if (hazard.status() != Status.READY) return hazard;
        if (hostileInside(level, box)) return Inspection.reject("hostile mob inside venue");
        return Inspection.ready(root, gate);
    }

    /** Resolve only saved starts referenced by the already loaded venue chunks. */
    private static Inspection referencedStarts(ServerLevel level, BoundingBox box,
                                               Structure self) {
        Set<Long> seen = new HashSet<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) return Inspection.waitFor("venue chunk unloaded");
                for (var entry : chunk.getAllReferences().entrySet()) {
                    if (entry.getKey() == self) continue;
                    for (long reference : entry.getValue()) {
                        if (!seen.add(reference)) continue;
                        if (seen.size() > 24) {
                            return Inspection.reject("too many neighboring structure starts");
                        }
                        int startX = ChunkPos.getX(reference);
                        int startZ = ChunkPos.getZ(reference);
                        if (level.getChunkSource().getChunkNow(startX, startZ) == null) {
                            // The reference was written by a generated start.
                            // Load that exact chunk for the overlap check;
                            // never search or generate an arbitrary radius.
                            level.getChunk(startX, startZ);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static BlockPos gate(ServerLevel level, BlockPos root) {
        BlockPos found = null;
        for (int[] offset : new int[][]{{0, 20}, {20, 0}, {0, -20}, {-20, 0}}) {
            BlockPos candidate = root.offset(offset[0], 11, offset[1]);
            Block block = level.getBlockState(candidate).getBlock();
            if (block != UndercardBlocks.GATE && block != Blocks.LADDER) continue;
            if (!level.getBlockState(candidate.below()).is(Blocks.LADDER)
                    || !level.getBlockState(candidate.above()).is(Blocks.LADDER)) continue;
            if (found != null) return null;
            found = candidate;
        }
        return found;
    }

    private static boolean ladder(ServerLevel level, BlockPos gate) {
        Direction support = level.getBlockState(gate.below())
                .getValue(LadderBlock.FACING).getOpposite();
        for (int y = gate.getY() - 28; y <= gate.getY() + 1; y++) {
            BlockPos rung = new BlockPos(gate.getX(), y, gate.getZ());
            if (rung.equals(gate)) {
                if (!level.getBlockState(rung).is(UndercardBlocks.GATE)
                        && !level.getBlockState(rung).is(Blocks.LADDER)) return false;
            } else if (!level.getBlockState(rung).is(Blocks.LADDER)) return false;
            if (!level.getFluidState(rung).isEmpty()) return false;
            BlockPos back = rung.relative(support);
            if (!level.getBlockState(back).isSolid()) return false;
        }
        return true;
    }

    private static boolean surfaceApproach(ServerLevel level, BlockPos root, BlockPos gate) {
        int dx = Integer.compare(gate.getX(), root.getX());
        int dz = Integer.compare(gate.getZ(), root.getZ());
        if (Math.abs(dx) + Math.abs(dz) != 1) return false;
        BlockPos doorway = gate.offset(-6 * dx, 0, -6 * dz);
        if (!walkable(level, doorway.below())) {
            traceApproach("doorway", doorway.below(), gate.getY() - 1, level);
            return false;
        }
        int prior = gate.getY() - 1;
        for (int step = 2; step <= 10; step++) {
            int x = doorway.getX() - step * dx;
            int z = doorway.getZ() - step * dz;
            LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) return false;
            // LevelChunk returns the top solid Y; ServerLevel.getHeight
            // returns the first free Y and is the API that needs minus one.
            int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    x & 15, z & 15);
            if (Math.abs(y - prior) > 1 || !walkable(level, new BlockPos(x, y, z))) {
                traceApproach("step=" + step, new BlockPos(x, y, z), prior, level);
                return false;
            }
            prior = y;
        }
        return true;
    }

    private static void traceApproach(String stage, BlockPos floor, int prior,
                                      ServerLevel level) {
        if (Boolean.getBoolean("gems.undercardNaturalDiagnostic")) {
            System.out.println("UNDERCARD_LOADED_ENTRY_FAIL " + stage
                    + " floor=" + floor + " previousY=" + prior
                    + " floorState=" + level.getBlockState(floor)
                    + " feetState=" + level.getBlockState(floor.above()));
        }
    }

    private static boolean roomEnvelope(ServerLevel level, BoundingBox box,
                                        BlockPos gate, boolean sealedV5) {
        int y0 = box.minY();
        for (int x = box.minX() + 7; x <= box.maxX() - 7; x += 4) {
            for (int z = box.minZ() + 7; z <= box.maxZ() - 7; z += 4) {
                for (int y : new int[]{y0, y0 + 1, y0 + 2, y0 + 22}) {
                    BlockPos pos = new BlockPos(x, y, z);
                    var state = level.getBlockState(pos);
                    // The sealed service shaft deliberately crosses the roof
                    // plane. Its backing and every rung were already checked
                    // by ladder(); permit this one verified cell only.
                    if (x == gate.getX() && z == gate.getZ() && y == y0 + 22) {
                        if (!state.is(Blocks.LADDER)
                                || !state.getFluidState().isEmpty()) return false;
                    } else if (!state.isSolid() || !state.getFluidState().isEmpty()) {
                        traceEnvelope(pos, state.toString());
                        return false;
                    }
                }
            }
        }
        if (sealedV5) {
            // The four occupied halls form a cross. Inspect their actual
            // outer retaining walls; corner terrain is not a room wall.
            for (int y = y0 + 2; y <= y0 + 23; y += 3) {
                for (int offset = 14; offset <= 48; offset += 3) {
                    for (BlockPos pos : List.of(
                            new BlockPos(box.minX() + 2, y, box.minZ() + offset),
                            new BlockPos(box.maxX() - 2, y, box.minZ() + offset),
                            new BlockPos(box.minX() + offset, y, box.minZ() + 2),
                            new BlockPos(box.minX() + offset, y, box.maxZ() - 2))) {
                        var state = level.getBlockState(pos);
                        if (!state.isSolid() || !state.getFluidState().isEmpty()) {
                            traceEnvelope(pos, state.toString());
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static void traceEnvelope(BlockPos pos, String state) {
        if (Boolean.getBoolean("gems.undercardNaturalDiagnostic")
                || Boolean.getBoolean("gems.undercardV5Acceptance")) {
            System.out.println("UNDERCARD_LOADED_SHELL_FAIL pos=" + pos
                    + " state=" + state);
        }
    }

    private static boolean reachableLitRoute(ServerLevel level, BoundingBox box,
                                             BlockPos gate) {
        int floorY = box.minY() + 4;
        BlockPos bell = null;
        BlockPos exit = null;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                BlockPos pos = new BlockPos(x, floorY + 1, z);
                if (level.getBlockState(pos).is(UndercardBlocks.BELL)) bell = pos;
                if (level.getBlockState(pos).is(UndercardBlocks.EXIT)) exit = pos;
            }
        }
        if (bell == null || exit == null) return false;
        BlockPos shaft = new BlockPos(gate.getX(), floorY + 1, gate.getZ());
        Set<BlockPos> reached = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos near : horizontal(bell)) {
            if (standing(level, near, floorY)) {
                queue.add(near);
                reached.add(near);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (level.getBrightness(LightLayer.BLOCK, current) < 1) {
                if (Boolean.getBoolean("gems.undercardNaturalDiagnostic")) {
                    System.out.println("UNDERCARD_LOADED_ROUTE_FAIL darkCell=" + current);
                }
                return false;
            }
            for (BlockPos next : horizontal(current)) {
                if (next.getX() < box.minX() + 4 || next.getX() > box.maxX() - 4
                        || next.getZ() < box.minZ() + 4 || next.getZ() > box.maxZ() - 4
                        || !reached.add(next)) continue;
                if (standing(level, next, floorY)) queue.add(next);
            }
        }
        boolean exitReached = horizontal(exit).stream().anyMatch(reached::contains);
        boolean shaftReached = horizontal(shaft).stream().anyMatch(reached::contains);
        // The wider v4 hall must keep every authored wing open after terrain
        // and neighboring structures have generated. A plain ring-to-shaft
        // route would miss a sealed training hall or crowd gallery.
        boolean wingsReached = box.getXSpan() != 63 || (
                reached.size() >= 700
                && reached.stream().anyMatch(pos -> pos.getX() <= box.minX() + 8)
                && reached.stream().anyMatch(pos -> pos.getX() >= box.maxX() - 8)
                && reached.stream().anyMatch(pos -> pos.getZ() <= box.minZ() + 12)
                && reached.stream().anyMatch(pos -> pos.getZ() >= box.maxZ() - 8));
        int gateLight = level.getBrightness(LightLayer.BLOCK, gate.above());
        if (Boolean.getBoolean("gems.undercardNaturalDiagnostic")
                && (!exitReached || !shaftReached || !wingsReached || gateLight < 1)) {
            System.out.println("UNDERCARD_LOADED_ROUTE_FAIL exitReached=" + exitReached
                    + " shaftReached=" + shaftReached + " gateLight=" + gateLight
                    + " wingsReached=" + wingsReached + " reached=" + reached.size());
        }
        return exitReached && shaftReached && wingsReached && gateLight >= 1;
    }

    private static List<BlockPos> horizontal(BlockPos pos) {
        return List.of(pos.north(), pos.east(), pos.south(), pos.west());
    }

    private static boolean standing(ServerLevel level, BlockPos feet, int floorY) {
        if (feet.getY() != floorY + 1) return false;
        return walkable(level, feet.below());
    }

    private static boolean walkable(ServerLevel level, BlockPos floor) {
        BlockPos feet = floor.above();
        return level.getBlockState(floor).isSolid()
                && level.getFluidState(floor).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level,
                        feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty();
    }

    private static Inspection noDeepHazard(ServerLevel level, BlockPos root) {
        Structure city = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(CITY);
        for (int cx = (root.getX() - HAZARD_RADIUS) >> 4;
             cx <= (root.getX() + HAZARD_RADIUS) >> 4; cx++) {
            for (int cz = (root.getZ() - HAZARD_RADIUS) >> 4;
                 cz <= (root.getZ() + HAZARD_RADIUS) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    // The generation survey already rejected Deep Dark and
                    // possible ancient-city starts through the full halo.
                    // Keep checking any outer chunks that happen to be loaded,
                    // but do not require a player to keep all 128 blocks in
                    // memory merely to open the entrance. The nearby core
                    // must still be present so real sculk/structure evidence
                    // around the venue cannot be skipped.
                    if (chunkTouchesCore(root, cx, cz)) {
                        return Inspection.waitFor("near hazard chunk unloaded");
                    }
                    continue;
                }
                if (city != null) {
                    var references = chunk.getAllReferences().get(city);
                    if (references != null && !references.isEmpty()) {
                        return Inspection.reject("ancient city intersects hazard halo");
                    }
                }
                var sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    var section = sections[index];
                    if (!section.maybeHas(state -> state.is(Blocks.SCULK_SENSOR)
                            || state.is(Blocks.SCULK_SHRIEKER))) continue;
                    int baseY = level.getSectionYFromSectionIndex(index) << 4;
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                var state = section.getBlockState(x, y, z);
                                if (!state.is(Blocks.SCULK_SENSOR)
                                        && !state.is(Blocks.SCULK_SHRIEKER)) continue;
                                BlockPos pos = new BlockPos((cx << 4) + x,
                                        baseY + y, (cz << 4) + z);
                                if (pos.distSqr(root) <= (long) HAZARD_RADIUS * HAZARD_RADIUS) {
                                    return Inspection.reject("sculk trigger within hazard halo");
                                }
                            }
                        }
                    }
                }
            }
        }
        return Inspection.ready(root, root);
    }

    private static boolean chunkTouchesCore(BlockPos root, int cx, int cz) {
        int x0 = cx << 4;
        int z0 = cz << 4;
        int dx = Math.max(0, Math.max(x0 - root.getX(), root.getX() - (x0 + 15)));
        int dz = Math.max(0, Math.max(z0 - root.getZ(), root.getZ() - (z0 + 15)));
        return dx * dx + dz * dz <= LOADED_HAZARD_CORE * LOADED_HAZARD_CORE;
    }

    private static boolean hostileInside(ServerLevel level, BoundingBox box) {
        AABB inner = new AABB(box.minX() + 4, box.minY() + 4, box.minZ() + 4,
                box.maxX() - 3, box.minY() + 23, box.maxZ() - 3);
        return !level.getEntitiesOfClass(Mob.class, inner, mob ->
                mob.getType().getCategory() == MobCategory.MONSTER).isEmpty();
    }

}
