package com.feel.gems.adventure.boss;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** Recolor only intact authored rescue pads; safe to retry after a partial save. */
final class RedlineRestoration {
    private RedlineRestoration() { }

    static int reveal(ServerLevel world, BlockPos center) {
        if (center == null || !world.getBlockState(center.below()).is(Blocks.LODESTONE)) return 0;
        int changed = 0;
        for (RedlineGuardPlan.Wing wing : RedlineGuardPlan.Wing.values()) {
            BlockPos pad = center.offset(wing.dx * 9, -1, wing.dz * 9);
            if (world.getBlockState(pad).is(Blocks.CONCRETE.yellow())
                    && world.setBlock(pad, Blocks.CONCRETE.lime().defaultBlockState(), 3)) changed++;
        }
        return changed;
    }
}
