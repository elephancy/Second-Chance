package com.feel.gems.adventure.worldgen;

import java.util.ArrayList;
import java.util.List;
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

/** A shallow natural-soil swale in front of the versioned Fam east entrance. */
final class FamArrivalGrade {
    private static final Identifier ROOT = Identifier.fromNamespaceAndPath(
            "gems", "fam_forum_east_wayfinding_v7");
    private static final Identifier REFERENCE = Identifier.fromNamespaceAndPath(
            "gems", "fam_east_reference_v1");
    private static final int FIRST_DEPTH = 24;
    private static final int LAST_DEPTH = 33;
    private static final int HALF_WIDTH = 4;

    private FamArrivalGrade() { }

    /** Pure geometry; the reference carrier rotates with the same root. */
    static List<Column> plan(BoundingBox root, Direction outward, Direction lateral) {
        if (!outward.getAxis().isHorizontal() || !lateral.getAxis().isHorizontal()
                || outward.getAxis() == lateral.getAxis()) {
            throw new IllegalArgumentException("Fam entrance needs perpendicular horizontal axes");
        }
        int centerX = (root.minX() + root.maxX()) / 2;
        int centerZ = (root.minZ() + root.maxZ()) / 2;
        int edgeX = outward == Direction.EAST ? root.maxX()
                : outward == Direction.WEST ? root.minX() : centerX;
        int edgeZ = outward == Direction.SOUTH ? root.maxZ()
                : outward == Direction.NORTH ? root.minZ() : centerZ;
        List<Column> columns = new ArrayList<>((LAST_DEPTH - FIRST_DEPTH + 1)
                * (HALF_WIDTH * 2 + 1));
        for (int depth = FIRST_DEPTH; depth <= LAST_DEPTH; depth++) {
            for (int side = -HALF_WIDTH; side <= HALF_WIDTH; side++) {
                columns.add(new Column(depth, side,
                        new BlockPos(edgeX + outward.getStepX() * depth
                                + lateral.getStepX() * side,
                                root.minY() - 1,
                                edgeZ + outward.getStepZ() * depth
                                        + lateral.getStepZ() * side)));
            }
        }
        return List.copyOf(columns);
    }

    /** Called only after an active Fam start has been found in a new chunk. */
    static boolean applyCenterChunk(WorldGenRegion region, ChunkPos center,
            StructureStart start, PoolElementStructurePiece root,
            List<BoundingBox> protectedPieces) {
        if (region.getChunk(center.x(), center.z()).getPersistedStatus()
                .isOrAfter(ChunkStatus.FULL)
                || !(root.getElement() instanceof SinglePoolElement single)
                || !ROOT.equals(single.getTemplateLocation())) return false;

        List<Write> writes = new ArrayList<>();
        boolean hasLocalColumn = false;
        for (Column column : plan(root.getBoundingBox(),
                root.getRotation().rotate(Direction.SOUTH),
                root.getRotation().rotate(Direction.WEST))) {
            BlockPos base = column.base();
            if ((base.getX() >> 4) != center.x()
                    || (base.getZ() >> 4) != center.z()) continue;
            hasLocalColumn = true;
            List<Write> columnWrites = preflightColumn(region, start,
                    protectedPieces, column);
            if (columnWrites.isEmpty()) {
                // The central three-wide walk must remain continuous. Flank
                // cells can retain their original vegetation and contour.
                if (Math.abs(column.side()) <= 1) return false;
                continue;
            }
            writes.addAll(columnWrites);
        }
        if (!hasLocalColumn || writes.isEmpty()
                || writes.stream().anyMatch(write -> !region.ensureCanWrite(write.pos()))) {
            return false;
        }
        for (Write write : writes) region.setBlock(write.pos(), write.state(), 2);
        return true;
    }

    private static List<Write> preflightColumn(WorldGenRegion region, StructureStart start,
            List<BoundingBox> protectedPieces, Column column) {
        BlockPos base = column.base();
        if (insideOtherPiece(start, base.getX(), base.getZ())
                || insideOtherProtectedPiece(start, protectedPieces,
                        base.getX(), base.getZ())) return List.of();
        BlockPos top = base.above();
        BlockPos plant = base.above(2);
        BlockPos head = base.above(3);
        BlockState baseState = region.getBlockState(base);
        BlockState topState = region.getBlockState(top);
        BlockState plantState = region.getBlockState(plant);
        BlockState headState = region.getBlockState(head);
        if (!baseState.is(Blocks.DIRT) || !topState.is(Blocks.GRASS_BLOCK)
                || !movablePlant(plantState)
                || !headState.getFluidState().isEmpty()
                || !headState.getCollisionShape(region, head).isEmpty()
                || region.getBlockEntity(base) != null
                || region.getBlockEntity(top) != null
                || region.getBlockEntity(plant) != null
                || region.getBlockEntity(head) != null) return List.of();
        // Lift existing low vegetation down with the grass rather than leave
        // floating flowers or strip the approach of its natural color.
        return List.of(new Write(plant, Blocks.AIR.defaultBlockState()),
                new Write(base, Blocks.GRASS_BLOCK.defaultBlockState()),
                new Write(top, plantState));
    }

    private static boolean movablePlant(BlockState state) {
        return state.isAir() || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.PINK_TULIP) || state.is(Blocks.WHITE_TULIP);
    }

    private static boolean insideOtherPiece(StructureStart start, int x, int z) {
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof PoolElementStructurePiece pooled
                    && pooled.getElement() instanceof SinglePoolElement single
                    && REFERENCE.equals(single.getTemplateLocation())) continue;
            if (contains(piece.getBoundingBox(), x, z)) return true;
        }
        return false;
    }

    private static boolean insideOtherProtectedPiece(StructureStart start,
            List<BoundingBox> protectedPieces, int x, int z) {
        for (BoundingBox box : protectedPieces) {
            if (!contains(box, x, z)) continue;
            boolean ownCarrier = false;
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof PoolElementStructurePiece pooled
                        && pooled.getElement() instanceof SinglePoolElement single
                        && REFERENCE.equals(single.getTemplateLocation())
                        && sameBox(box, piece.getBoundingBox())) {
                    ownCarrier = true;
                    break;
                }
            }
            if (!ownCarrier) return true;
        }
        return false;
    }

    private static boolean contains(BoundingBox box, int x, int z) {
        return x >= box.minX() && x <= box.maxX()
                && z >= box.minZ() && z <= box.maxZ();
    }

    private static boolean sameBox(BoundingBox a, BoundingBox b) {
        return a.minX() == b.minX() && a.maxX() == b.maxX()
                && a.minY() == b.minY() && a.maxY() == b.maxY()
                && a.minZ() == b.minZ() && a.maxZ() == b.maxZ();
    }

    record Column(int depth, int side, BlockPos base) { }
    private record Write(BlockPos pos, BlockState state) { }
}
