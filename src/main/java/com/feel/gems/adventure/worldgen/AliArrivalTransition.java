package com.feel.gems.adventure.worldgen;

import com.feel.gems.adventure.DomainBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Center-chunk-only entry finish for newly generated four-piece Ali v5 starts. */
final class AliArrivalTransition {
    private static final int MAX_ROUTE_CELLS = 20 * 9;
    private static final int CLEAR_HEIGHT = 6;
    private static final int SIGHT_HALF_WIDTH = 4;
    private static final int MAX_GRADE = 4;

    private AliArrivalTransition() { }

    static boolean gradeCenterChunk(WorldGenRegion region, ChunkGenerator generator,
            BoundingBox chunkBox, PoolElementStructurePiece root,
            PoolElementStructurePiece carrier, List<StructurePiece> pieces) {
        ChunkPos center = region.getCenter();
        if (region.getChunk(center.x(), center.z()).getPersistedStatus()
                .isOrAfter(ChunkStatus.FULL) || pieces.size() != 4
                || carrier.getBoundingBox().getYSpan() != 1) return false;
        List<BlockPos> route = AliDryArrivalStructure.arrivalRoute(region, generator, root);
        if (route.isEmpty() || route.size() > MAX_ROUTE_CELLS) return false;
        Map<Long, BlockPos> columns = new LinkedHashMap<>();
        for (BlockPos floor : route)
            columns.put(BlockPos.asLong(floor.getX(), 0, floor.getZ()), floor);
        Direction lateral = root.getRotation().rotate(Direction.EAST);
        // Every route step owns its full three-wide walking surface.
        for (BlockPos floor : route) {
            BoundingBox apron = carrier.getBoundingBox();
            for (int side : new int[]{-1, 1}) {
                BlockPos shoulder = floor.relative(lateral, side);
                if (shoulder.getX() < apron.minX() || shoulder.getX() > apron.maxX()
                        || shoulder.getZ() < apron.minZ()
                        || shoulder.getZ() > apron.maxZ()) return false;
                long key = BlockPos.asLong(shoulder.getX(), 0, shoulder.getZ());
                BlockPos already = columns.get(key);
                if (already != null) {
                    if (already.getY() != floor.getY()) return false;
                    continue;
                }
                int feet = AliDryArrivalStructure.walkableFeet(region, generator,
                        shoulder.getX(), shoulder.getZ());
                if (feet == Integer.MIN_VALUE || Math.abs(feet - floor.getY() - 1) > MAX_GRADE)
                    return false;
                columns.put(key, shoulder.atY(floor.getY()));
            }
        }
        Map<BlockPos, BlockState> writes = new LinkedHashMap<>();
        int localCore = 0;
        for (BlockPos target : columns.values()) {
            if (!chunkBox.isInside(target)) continue;
            boolean core = route.contains(target);
            List<Write> column = planColumn(region, generator, carrier,
                    pieces, target, core);
            if (column == null) return false;
            if (core) localCore++;
            for (Write write : column) {
                BlockState old = writes.putIfAbsent(write.pos(), write.state());
                if (old != null && !old.equals(write.state())) return false;
            }
        }
        if (localCore == 0 || writes.size() > MAX_ROUTE_CELLS * (MAX_GRADE + 4))
            return false;
        for (var write : writes.entrySet())
            region.setBlock(write.getKey(), write.getValue(), 2);
        return true;
    }

    private static List<Write> planColumn(WorldGenRegion region,
            ChunkGenerator generator, PoolElementStructurePiece carrier,
            List<StructurePiece> pieces, BlockPos target, boolean core) {
        BoundingBox apron = carrier.getBoundingBox();
        if (target.getX() < apron.minX() || target.getX() > apron.maxX()
                || target.getZ() < apron.minZ() || target.getZ() > apron.maxZ())
            return null;
        for (StructurePiece piece : pieces) {
            if (piece != carrier && piece.getBoundingBox().isInside(target)) return null;
        }
        int groundY = actualGround(region, target);
        if (groundY == Integer.MIN_VALUE) return null;
        BlockState ground = region.getBlockState(target.atY(groundY));
        if (!AliDryArrivalStructure.naturalGradeBlock(ground)) return null;
        List<Write> writes = new ArrayList<>();
        int min = target.getY() > groundY ? groundY + 1 : target.getY();
        int max = Math.max(target.getY() + 3, groundY);
        for (int y = min; y <= max; y++) {
            BlockPos at = target.atY(y);
            if (!region.ensureCanWrite(at) || region.getBlockEntity(at) != null)
                return null;
            BlockState found = region.getBlockState(at);
            BlockState replacement;
            if (y < target.getY()) {
                if (!found.isAir()) return null;
                replacement = DomainBlocks.SPLITSTONE.defaultBlockState();
            } else if (y == target.getY()) {
                if (!found.isAir() && !AliDryArrivalStructure.naturalGradeBlock(found))
                    return null;
                replacement = core ? DomainBlocks.WAYMARK_TILE.defaultBlockState()
                        : DomainBlocks.SPLITSTONE.defaultBlockState();
            } else {
                if (!found.isAir() && !AliDryArrivalStructure.naturalGradeBlock(found))
                    return null;
                replacement = Blocks.AIR.defaultBlockState();
            }
            if (!found.equals(replacement)) writes.add(new Write(at, replacement));
        }
        return List.copyOf(writes);
    }

    private static int actualGround(WorldGenRegion region, BlockPos target) {
        int lowest = target.getY() - MAX_GRADE;
        for (int y = target.getY() + MAX_GRADE; y >= lowest; y--) {
            BlockPos at = target.atY(y);
            BlockState found = region.getBlockState(at);
            if (AliDryArrivalStructure.naturalGradeBlock(found)) return y;
            if (!found.isAir()) return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    /** Top-layer foliage is removed only above an exact four-piece new route. */
    static boolean clearCenterChunkCanopy(WorldGenRegion region, ChunkPos center,
            StructureStart start) {
        PoolElementStructurePiece carrier = AliDryArrivalStructure.currentV5Carrier(start);
        if (carrier == null || region.getChunk(center.x(), center.z())
                .getPersistedStatus().isOrAfter(ChunkStatus.FULL)) return false;
        BoundingBox apron = carrier.getBoundingBox();
        Set<BlockPos> paved = new LinkedHashSet<>();
        for (int x = Math.max(center.getMinBlockX(), apron.minX());
                x <= Math.min(center.getMaxBlockX(), apron.maxX()); x++) {
            for (int z = Math.max(center.getMinBlockZ(), apron.minZ());
                    z <= Math.min(center.getMaxBlockZ(), apron.maxZ()); z++) {
                for (int y = apron.minY() - MAX_GRADE;
                        y <= apron.minY() + MAX_GRADE; y++) {
                    BlockPos floor = new BlockPos(x, y, z);
                    if (region.getBlockState(floor).is(DomainBlocks.WAYMARK_TILE)
                            || region.getBlockState(floor).is(DomainBlocks.SPLITSTONE)
                            && hasAdjacentWaymark(region, floor)) paved.add(floor);
                }
            }
        }
        if (paved.isEmpty() || paved.size() > MAX_ROUTE_CELLS) return false;
        Set<BlockPos> clear = new LinkedHashSet<>();
        for (BlockPos floor : paved) {
            for (int rise = 1; rise <= CLEAR_HEIGHT; rise++) {
                BlockPos at = floor.above(rise);
                BlockState cover = region.getBlockState(at);
                if (naturalCover(cover)) {
                    if (!region.ensureCanWrite(at)
                            || region.getBlockEntity(at) != null) return false;
                    clear.add(at);
                } else if (!cover.isAir()) {
                    return false;
                }
            }
        }
        // Keep the same six-block ceiling, but open the rest of the exact
        // nine-wide arrival carrier where natural spruce screens the facade.
        Direction lateral = AliDryArrivalStructure.currentV5Root(start)
                .getRotation().rotate(Direction.EAST);
        Set<BlockPos> sightline = new LinkedHashSet<>();
        for (BlockPos floor : paved) {
            for (int side = -SIGHT_HALF_WIDTH; side <= SIGHT_HALF_WIDTH; side++) {
                BlockPos column = floor.relative(lateral, side);
                if (column.getX() < apron.minX() || column.getX() > apron.maxX()
                        || column.getZ() < apron.minZ() || column.getZ() > apron.maxZ()
                        || column.getX() < center.getMinBlockX()
                        || column.getX() > center.getMaxBlockX()
                        || column.getZ() < center.getMinBlockZ()
                        || column.getZ() > center.getMaxBlockZ()) continue;
                sightline.add(column);
            }
        }
        for (BlockPos floor : sightline) {
            for (int rise = 1; rise <= CLEAR_HEIGHT; rise++) {
                BlockPos at = floor.above(rise);
                if (naturalLeaves(region.getBlockState(at))
                        && region.ensureCanWrite(at)
                        && region.getBlockEntity(at) == null) clear.add(at);
            }
        }
        if (clear.size() > MAX_ROUTE_CELLS * CLEAR_HEIGHT) return false;
        for (BlockPos at : clear) region.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
        return !clear.isEmpty();
    }

    private static boolean hasAdjacentWaymark(WorldGenRegion region, BlockPos floor) {
        for (Direction side : Direction.Plane.HORIZONTAL)
            if (region.getBlockState(floor.relative(side))
                    .is(DomainBlocks.WAYMARK_TILE)) return true;
        return false;
    }

    private static boolean naturalCover(BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;
        if (state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.SNOW)) return true;
        return naturalLeaves(state);
    }

    private static boolean naturalLeaves(BlockState state) {
        return state.getFluidState().isEmpty()
                && state.is(BlockTags.LEAVES) && state.getBlock() instanceof LeavesBlock
                && !state.getValue(LeavesBlock.PERSISTENT);
    }

    private record Write(BlockPos pos, BlockState state) { }
}
