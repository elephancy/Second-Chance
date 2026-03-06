package com.feel.gems.adventure.worldgen;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Noise-only shoal, boat approach and seabed preflight for new v5 harbors. */
final class NourCoastalNoiseSurvey {
    enum Reason { ACCEPT, HEIGHT, FOOTING, INTERSECTION, BOAT_APPROACH, WATER_SIDE }

    private record Column(int topY, boolean water, int groundY) { }

    private NourCoastalNoiseSurvey() { }

    static Reason assess(Structure.GenerationContext context,
                         PoolElementStructurePiece root,
                         List<PoolElementStructurePiece> pieces) {
        BoundingBox rootBox = root.getBoundingBox();
        int baseY = root.getPosition().getY();
        if (baseY < 60 || baseY > 62 || rootBox.maxY() >= context.heightAccessor().getMaxY()
                || rootBox.minY() < context.heightAccessor().getMinY()) return Reason.HEIGHT;
        for (PoolElementStructurePiece piece : pieces) {
            BoundingBox box = piece.getBoundingBox();
            if (piece.getPosition().getY() != baseY) return Reason.HEIGHT;
            Reason foundation = footing(context, box, baseY);
            if (foundation != Reason.ACCEPT) return foundation;
        }
        // A boat must be able to reach the south mooring from open water. No
        // approach blocks are written beyond the saved root footprint.
        for (int lane = -1; lane <= 1; lane++) {
            for (int distance = 1; distance <= 12; distance++) {
                BlockPos approach = local(root, 23 + lane, 46 + distance);
                Column column = sample(context, approach.getX(), approach.getZ());
                if (!column.water() || column.topY() < 61
                        || column.topY() > 64
                        || column.groundY() < baseY - 24) {
                    return Reason.BOAT_APPROACH;
                }
            }
        }
        // The visible horizon on either side of the mooring must also be sea.
        int wet = 0;
        for (int lane : new int[]{-8, 0, 8}) {
            for (int distance : new int[]{15, 20, 25}) {
                BlockPos sea = local(root, 23 + lane, 46 + distance);
                Column column = sample(context, sea.getX(), sea.getZ());
                if (column.water() && column.topY() >= 61
                        && column.topY() <= 64
                        && column.groundY() >= baseY - 24) wet++;
            }
        }
        return wet >= 7 ? Reason.ACCEPT : Reason.WATER_SIDE;
    }

    private static Reason footing(Structure.GenerationContext context,
                                  BoundingBox box, int baseY) {
        int wet = 0;
        int samples = 0;
        int hardIntersections = 0;
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int x = box.minX() + 4; x <= box.maxX() - 4; x += 8) {
            for (int z = box.minZ() + 4; z <= box.maxZ() - 4; z += 8) {
                Column column = sample(context, x, z);
                // All three pieces are elevated, dry decks. Their 25-block
                // piles must reach the actual shallow seabed after carvers.
                if (column.groundY() < baseY - (column.water() ? 24 : 8)) {
                    return Reason.FOOTING;
                }
                if (column.groundY() > baseY + 6) return Reason.INTERSECTION;
                if (column.groundY() > baseY + 4) hardIntersections++;
                if (column.water()) wet++;
                else {
                    low = Math.min(low, column.groundY());
                    high = Math.max(high, column.groundY());
                }
                samples++;
            }
        }
        if (samples < 20 || wet * 4 < samples * 3
                || (wet < samples && high - low > 7)) {
            return Reason.FOOTING;
        }
        // Small cuts are confined to the template volume. Reject a hillside
        // that would turn the structure's air mask into bulk excavation.
        return hardIntersections * 8 <= samples
                ? Reason.ACCEPT : Reason.INTERSECTION;
    }

    private static Column sample(Structure.GenerationContext context, int x, int z) {
        int topY = context.chunkGenerator().getBaseHeight(x, z,
                Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState()) - 1;
        NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                context.heightAccessor(), context.randomState());
        boolean water = column.getBlock(topY).is(Blocks.WATER);
        int ground = column.getBlock(topY).isSolid() ? topY : Integer.MIN_VALUE;
        if (water) {
            for (int depth = 1; depth <= 24; depth++) {
                int y = topY - depth;
                if (column.getBlock(y).isSolid()) {
                    ground = y;
                    break;
                }
            }
        }
        return new Column(topY, water, ground);
    }

    private static BlockPos local(PoolElementStructurePiece root, int x, int z) {
        return StructureTemplate.transform(new BlockPos(x, 0, z), Mirror.NONE,
                root.getRotation(), BlockPos.ZERO).offset(root.getPosition());
    }
}
