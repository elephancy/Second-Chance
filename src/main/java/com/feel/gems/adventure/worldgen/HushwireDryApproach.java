package com.feel.gems.adventure.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Bounded natural-terrain survey; no chunk lookup or terrain mutation. */
final class HushwireDryApproach {
    static final int LENGTH = 12;
    private static final int HALF_WIDTH = 1;
    private static final int HEADROOM = 3;

    record Column(int feet, boolean dry, boolean clear) { }

    @FunctionalInterface
    interface Terrain {
        Column at(int x, int z);
    }

    private HushwireDryApproach() { }

    static boolean connected(Terrain terrain, BlockPos stair,
                             Direction outward, Direction lateral,
                             int stairFeet) {
        if (terrain == null || stair == null || outward == null || lateral == null
                || outward.getAxis() == lateral.getAxis()) return false;
        int previous = stairFeet;
        for (int depth = 1; depth <= LENGTH; depth++) {
            BlockPos center = stair.relative(outward, depth);
            Column middle = terrain.at(center.getX(), center.getZ());
            if (!walkable(middle, previous)) return false;
            for (int side = -HALF_WIDTH; side <= HALF_WIDTH; side += 2) {
                BlockPos flank = center.relative(lateral, side);
                if (!walkable(terrain.at(flank.getX(), flank.getZ()), middle.feet()))
                    return false;
            }
            previous = middle.feet();
        }
        return true;
    }

    private static boolean walkable(Column column, int priorFeet) {
        return column != null && column.dry() && column.clear()
                && Math.abs(column.feet() - priorFeet) <= 1;
    }

    static boolean connected(Structure.GenerationContext context,
                             PoolElementStructurePiece piece, int localX,
                             int localZ, int stairFeet) {
        Direction outward = piece.getRotation().rotate(Direction.SOUTH);
        Direction lateral = piece.getRotation().rotate(Direction.EAST);
        BlockPos stair = StructureTemplate.transform(new BlockPos(localX, 0, localZ),
                Mirror.NONE, piece.getRotation(), BlockPos.ZERO).offset(piece.getPosition());
        return connected((x, z) -> sample(context, x, z), stair,
                outward, lateral, stairFeet);
    }

    static Column sample(Structure.GenerationContext context, int x, int z) {
        int feet = context.chunkGenerator().getBaseHeight(x, z,
                Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        if (feet <= context.heightAccessor().getMinY()
                || feet + HEADROOM >= context.heightAccessor().getMaxY())
            return null;
        NoiseColumn noise = context.chunkGenerator().getBaseColumn(x, z,
                context.heightAccessor(), context.randomState());
        BlockState support = noise.getBlock(feet - 1);
        boolean dry = support.isSolid() && support.getFluidState().isEmpty();
        boolean clear = true;
        for (int y = feet; y < feet + HEADROOM; y++)
            clear &= noise.getBlock(y).isAir();
        return new Column(feet, dry, clear);
    }

    static boolean solidContact(Structure.GenerationContext context, int x, int z,
                                int fromY, int toY) {
        NoiseColumn noise = context.chunkGenerator().getBaseColumn(x, z,
                context.heightAccessor(), context.randomState());
        for (int y = fromY; y <= toY; y++) {
            BlockState state = noise.getBlock(y);
            if (state.isSolid() && state.getFluidState().isEmpty()) return true;
        }
        return false;
    }
}
