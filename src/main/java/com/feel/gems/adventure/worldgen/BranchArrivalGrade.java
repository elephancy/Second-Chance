package com.feel.gems.adventure.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/** A five-row, soil-only approach to the versioned Branch south stair. */
final class BranchArrivalGrade {
    private static final Identifier ROOT = Identifier.fromNamespaceAndPath(
            "gems", "branch_vault_vista_v7");
    private static final int GATE_OFFSET = -2;
    private static final int HALF_WIDTH = 2;
    private static final int LENGTH = 5;

    private BranchArrivalGrade() { }

    /** Geometry only: no world reads, chunk loads, or writes. */
    static List<Column> plan(BoundingBox root, Direction outward, Direction lateral) {
        if (!horizontalPerpendicular(outward, lateral)) {
            throw new IllegalArgumentException("Branch gate needs perpendicular horizontal axes");
        }
        int centerX = (root.minX() + root.maxX()) / 2;
        int centerZ = (root.minZ() + root.maxZ()) / 2;
        int edgeX = outward == Direction.EAST ? root.maxX()
                : outward == Direction.WEST ? root.minX() : centerX;
        int edgeZ = outward == Direction.SOUTH ? root.maxZ()
                : outward == Direction.NORTH ? root.minZ() : centerZ;
        List<Column> columns = new ArrayList<>(LENGTH * (HALF_WIDTH * 2 + 1));
        for (int depth = 1; depth <= LENGTH; depth++) {
            for (int side = -HALF_WIDTH; side <= HALF_WIDTH; side++) {
                int offset = GATE_OFFSET + side;
                BlockPos top = new BlockPos(
                        edgeX + outward.getStepX() * depth + lateral.getStepX() * offset,
                        root.minY() + depth,
                        edgeZ + outward.getStepZ() * depth + lateral.getStepZ() * offset);
                columns.add(new Column(depth, side, top));
            }
        }
        return List.copyOf(columns);
    }

    /** Read-only preflight. Empty means the full approach lacks a three-wide safe lane. */
    static List<Write> preflight(WorldGenRegion region, StructureStart start,
            List<Column> columns) {
        Map<Integer, List<Column>> safeRows = new HashMap<>();
        List<Write> writes = new ArrayList<>();
        for (Column column : columns) {
            BlockPos top = column.top();
            if (!region.hasChunk(top.getX() >> 4, top.getZ() >> 4)
                    || insideAnyPiece(start, top.getX(), top.getZ())) continue;
            int groundY = naturalSoilBelow(region, top);
            if (groundY == Integer.MIN_VALUE) continue;
            safeRows.computeIfAbsent(column.depth(), ignored -> new ArrayList<>()).add(column);
            for (int y = groundY + 1; y <= top.getY(); y++) {
                BlockPos pos = top.atY(y);
                writes.add(new Write(pos, y == top.getY()
                        ? surface(top).defaultBlockState() : Blocks.DIRT.defaultBlockState()));
            }
        }
        for (int depth = 1; depth <= LENGTH; depth++) {
            List<Column> row = safeRows.getOrDefault(depth, List.of());
            boolean lane = false;
            for (int first = -HALF_WIDTH; first <= HALF_WIDTH - 2; first++) {
                int a = first;
                if (row.stream().anyMatch(column -> column.side() == a)
                        && row.stream().anyMatch(column -> column.side() == a + 1)
                        && row.stream().anyMatch(column -> column.side() == a + 2)
                        && first <= 0 && first + 2 >= 0) {
                    lane = true;
                    break;
                }
            }
            if (!lane) return List.of();
        }
        return List.copyOf(writes);
    }

    /** The caller supplies the already discovered start; only center may change. */
    static boolean applyCenterChunk(WorldGenRegion region, ChunkPos center,
            StructureStart start, PoolElementStructurePiece root) {
        if (region.getChunk(center.x(), center.z()).getPersistedStatus()
                .isOrAfter(ChunkStatus.FULL)
                || !(root.getElement() instanceof SinglePoolElement single)
                || !ROOT.equals(single.getTemplateLocation())) return false;
        List<Column> columns = plan(root.getBoundingBox(),
                root.getRotation().rotate(Direction.SOUTH),
                root.getRotation().rotate(Direction.EAST));
        List<Write> writes = preflight(region, start, columns);
        if (writes.isEmpty()) return false;
        List<Write> local = writes.stream().filter(write ->
                (write.pos().getX() >> 4) == center.x()
                        && (write.pos().getZ() >> 4) == center.z()).toList();
        if (local.isEmpty() || local.stream().anyMatch(write ->
                !region.ensureCanWrite(write.pos()))) return false;
        for (Write write : local) {
            region.setBlock(write.pos(), write.state(), 2);
        }
        return true;
    }

    private static int naturalSoilBelow(WorldGenRegion region, BlockPos top) {
        for (int y = top.getY(); y >= top.getY() - 2; y--) {
            BlockPos pos = top.atY(y);
            BlockState state = region.getBlockState(pos);
            if (naturalSoil(state)) {
                if (!state.getFluidState().isEmpty()
                        || !region.getBlockState(pos.below()).isSolid()
                        || !clearHeadroom(region, top)) return Integer.MIN_VALUE;
                return y;
            }
            if (!replaceableGroundCover(state) || region.getBlockEntity(pos) != null) {
                return Integer.MIN_VALUE;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean clearHeadroom(WorldGenRegion region, BlockPos top) {
        for (int rise = 1; rise <= 2; rise++) {
            BlockPos pos = top.above(rise);
            BlockState state = region.getBlockState(pos);
            if (!state.getFluidState().isEmpty()
                    || !state.getCollisionShape(region, pos).isEmpty()
                    || region.getBlockEntity(pos) != null) return false;
        }
        return true;
    }

    private static boolean naturalSoil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL);
    }

    private static boolean replaceableGroundCover(BlockState state) {
        return state.isAir() || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LEAF_LITTER);
    }

    private static net.minecraft.world.level.block.Block surface(BlockPos top) {
        int hash = top.getX() * 0x9E3779B9 ^ top.getZ() * 0x85EBCA6B;
        hash ^= hash >>> 16;
        return Math.floorMod(hash, 3) == 0 ? Blocks.COARSE_DIRT : Blocks.PODZOL;
    }

    private static boolean insideAnyPiece(StructureStart start, int x, int z) {
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (x >= box.minX() && x <= box.maxX()
                    && z >= box.minZ() && z <= box.maxZ()) return true;
        }
        return false;
    }

    private static boolean horizontalPerpendicular(Direction a, Direction b) {
        return a.getAxis().isHorizontal() && b.getAxis().isHorizontal()
                && a.getAxis() != b.getAxis();
    }

    record Column(int depth, int side, BlockPos top) { }
    record Write(BlockPos pos, BlockState state) { }
}
