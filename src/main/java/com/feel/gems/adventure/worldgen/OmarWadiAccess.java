package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.DomainBlocks;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Opens the new Lost Wadi portal only after its whole descending walk is saved. */
public final class OmarWadiAccess {
    private static final Map<ServerLevel, Pending> PENDING = new HashMap<>();
    private static final BlockState SIGNATURE =
            Blocks.CHISELED_RED_SANDSTONE.defaultBlockState();
    private static final BlockState SEAL = Blocks.CUT_SANDSTONE.defaultBlockState();

    private OmarWadiAccess() { }

    public static void init() {
        OmarRetainingTerrace.init();
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
            if (level.dimension() != Level.OVERWORLD) return;
            Pending pending = PENDING.computeIfAbsent(level, ignored -> new Pending());
            discover(level, chunk, pending);
            for (Request request : pending.requests.values()) {
                ChunkPos markerChunk = chunkPos(request.marker());
                if (Math.abs(chunk.getPos().x() - markerChunk.x()) <= 3
                        && Math.abs(chunk.getPos().z() - markerChunk.z()) <= 3) {
                    pending.ready.add(request.marker());
                }
            }
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            Pending pending = PENDING.get(level);
            if (pending == null) return;
            pending.requests.entrySet().removeIf(entry -> {
                boolean rootUnloaded = chunkPos(entry.getKey()).equals(chunk.getPos());
                if (rootUnloaded) pending.ready.remove(entry.getKey());
                return rootUnloaded;
            });
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            Pending pending = PENDING.get(level);
            if (pending == null || pending.ready.isEmpty()) return;
            // A successful plan saves loaded chunks three times. Avoid
            // stacking multiple new districts' saves in a single tick.
            Iterator<BlockPos> next = pending.ready.iterator();
            BlockPos marker = next.next();
            next.remove();
            Request request = pending.requests.get(marker);
            if (request == null) return;
            try {
                GuardedDistrictBridges.Plan plan = plan(level, request);
                OmarRetainingTerrace.Result terrace = OmarRetainingTerrace.add(
                        level, plan, marker, request.outward());
                if (terrace == OmarRetainingTerrace.Result.WAIT) return;
                if (terrace == OmarRetainingTerrace.Result.BLOCKED) {
                    pending.requests.remove(marker);
                    GemsMod.LOGGER.warn("Omar's Wadi retaining terrace remains sealed at {}; exact new-start terrain proof failed",
                            marker);
                    return;
                }
                OmarRetainingTerrace.beginAuthoredWrite();
                GuardedDistrictBridges.PlaceResult result;
                try {
                    result = GuardedDistrictBridges.place(level, plan);
                } finally {
                    OmarRetainingTerrace.endAuthoredWrite();
                }
                if (result == GuardedDistrictBridges.PlaceResult.COMPLETE) {
                    OmarRetainingTerrace.completed(level, marker);
                }
                if (result == GuardedDistrictBridges.PlaceResult.WAIT) return;
                if (result == GuardedDistrictBridges.PlaceResult.DEFERRED) {
                    pending.ready.add(marker);
                    return;
                }
                pending.requests.remove(marker);
                if (result == GuardedDistrictBridges.PlaceResult.BLOCKED) {
                    GemsMod.LOGGER.warn("Omar's Wadi portal remains sealed at {}; its complete dry stair is unavailable",
                            marker);
                }
            } catch (RuntimeException failure) {
                pending.requests.remove(marker);
                GemsMod.LOGGER.error("Omar's Wadi portal remains sealed after an access failure at {}",
                        marker, failure);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING.clear());
    }

    private static void discover(ServerLevel level, LevelChunk chunk, Pending pending) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (!section.maybeHas(state -> state.is(Blocks.OBSERVER))) continue;
            int y0 = level.getMinY() + index * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.is(Blocks.OBSERVER)) continue;
                        Direction outward = state.getValue(ObserverBlock.FACING);
                        if (!outward.getAxis().isHorizontal()) continue;
                        BlockPos marker = new BlockPos(chunk.getPos().getMinBlockX() + x,
                                y0 + y, chunk.getPos().getMinBlockZ() + z);
                        if (!chunk.getBlockState(marker.above()).equals(SIGNATURE)
                                || !chunk.getBlockState(marker.above(3))
                                        .is(DomainBlocks.LOST_TILE)) continue;
                        pending.requests.put(marker, new Request(marker, outward));
                        pending.ready.add(marker);
                    }
                }
            }
        }
    }

    private static GuardedDistrictBridges.Plan plan(ServerLevel level, Request request) {
        BlockPos marker = request.marker();
        Direction outward = request.outward();
        LevelChunk root = level.getChunkSource().getChunkNow(marker.getX() >> 4,
                marker.getZ() >> 4);
        if (root == null) throw new IllegalStateException("Wadi request chunk unloaded");
        BlockState pendingMarker = root.getBlockState(marker);
        if (!pendingMarker.is(Blocks.OBSERVER)
                || pendingMarker.getValue(ObserverBlock.FACING) != outward) {
            throw new IllegalStateException("Wadi request marker changed");
        }
        GuardedDistrictBridges.Plan plan =
                new GuardedDistrictBridges.Plan("Omar", marker)
                        .finish(marker, pendingMarker,
                                DomainBlocks.LOST_CLAY.defaultBlockState())
                        .requireState(marker.above(), SIGNATURE)
                        .requireState(marker.above(3),
                                DomainBlocks.LOST_TILE.defaultBlockState());
        int baseY = marker.getY() - 1;
        for (int distance = 1; distance <= 13; distance++) {
            int floorY = baseY + 5 - distance;
            int firstSide = distance <= 7 ? -1 : distance == 8 ? 0
                    : distance <= 12 ? 1 : 0;
            for (int side = firstSide; side <= firstSide + 1; side++) {
                BlockPos floor = transverse(marker.relative(outward, distance),
                        outward, side).atY(floorY);
                plan.exterior(floor, DomainBlocks.LOST_TILE.defaultBlockState());
                plan.requireAir(floor.above());
                plan.requireAir(floor.above(2));
            }
        }
        for (int side = -1; side <= 0; side++) {
            BlockPos gate = transverse(marker, outward, side);
            plan.gate(gate.above(4), SEAL);
            plan.gate(gate.above(5), SEAL);
        }
        BlockPos dryLanding = transverse(marker.relative(outward, 13), outward, 2)
                .atY(baseY - 9);
        plan.passableDryStanding(dryLanding.above());
        return plan;
    }

    /** Exact v5 hillside transitions, invoked only after the saved start is proven. */
    static boolean adaptFreshSlope(ServerLevel level, GuardedDistrictBridges.Plan plan,
                                   BlockPos marker, Direction outward) {
        int baseY = marker.getY() - 1;
        for (int distance = 1; distance <= 13; distance++) {
            int floorY = baseY + 5 - distance;
            int firstSide = distance <= 7 ? -1 : distance == 8 ? 0
                    : distance <= 12 ? 1 : 0;
            for (int side = firstSide; side <= firstSide + 1; side++) {
                BlockPos floor = transverse(marker.relative(outward, distance),
                        outward, side).atY(floorY);
                LevelChunk chunk = level.getChunkSource().getChunkNow(
                        floor.getX() >> 4, floor.getZ() >> 4);
                if (chunk == null) return false;
                BlockState current = chunk.getBlockState(floor);
                if (!current.isAir() && !current.is(DomainBlocks.LOST_TILE)) {
                    if ((!OmarRetainingTerrace.natural(current)
                            && !current.is(Blocks.SHORT_GRASS)
                            && !current.is(DomainBlocks.LOST_CLAY))
                            || !current.getFluidState().isEmpty()
                            || chunk.getBlockEntity(floor) != null) return false;
                    plan.veneerExterior(floor, current);
                }
                for (int rise = 1; rise <= 2; rise++) {
                    BlockPos head = floor.above(rise);
                    BlockState cover = chunk.getBlockState(head);
                    if (cover.isAir()) continue;
                    if (!cover.is(Blocks.SHORT_GRASS)
                            || !cover.getFluidState().isEmpty()
                            || chunk.getBlockEntity(head) != null) return false;
                    plan.veneerAir(head, cover);
                }
            }
        }
        BlockPos landing = transverse(marker.relative(outward, 13), outward, 2)
                .atY(baseY - 8);
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                landing.getX() >> 4, landing.getZ() >> 4);
        if (chunk == null) return false;
        BlockState ground = chunk.getBlockState(landing);
        if (!ground.isAir()) {
            if (!OmarRetainingTerrace.natural(ground)
                    || !ground.getFluidState().isEmpty()
                    || chunk.getBlockEntity(landing) != null
                    || !chunk.getBlockState(landing.above()).isAir()
                    || !chunk.getBlockState(landing.above(2)).isAir()) return false;
            plan.shiftPassableDryStanding(landing, landing.above());
        }
        return true;
    }

    private static BlockPos transverse(BlockPos pos, Direction outward, int side) {
        return pos.offset(-outward.getStepZ() * side, 0,
                outward.getStepX() * side);
    }

    private static ChunkPos chunkPos(BlockPos pos) {
        return new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private record Request(BlockPos marker, Direction outward) { }

    private static final class Pending {
        private final Map<BlockPos, Request> requests = new HashMap<>();
        private final Set<BlockPos> ready = new HashSet<>();
    }
}
