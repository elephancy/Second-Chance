package com.feel.gems.adventure.boss;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** Recolor only intact authored route ends; safe to retry after a partial save. */
public final class BranchRestoration {
    private BranchRestoration() { }

    public static int reveal(ServerLevel world, BlockPos center) {
        if (center == null || !world.getBlockState(center.below()).is(Blocks.LODESTONE)) return 0;
        int changed = 0;
        for (int[] offset : new int[][]{{-7, 0}, {7, 0}, {0, -7}, {0, 7}}) {
            BlockPos end = center.below().offset(offset[0], 0, offset[1]);
            if (world.getBlockState(end).is(Blocks.CONCRETE.blue())
                    && world.setBlock(end, Blocks.GOLD_BLOCK.defaultBlockState(), 3)) changed++;
        }
        return changed;
    }
}
