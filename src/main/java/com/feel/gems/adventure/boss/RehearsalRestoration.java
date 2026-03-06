package com.feel.gems.adventure.boss;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** Turn only intact authored footlights gold after the first world victory. */
public final class RehearsalRestoration {
    private RehearsalRestoration() { }

    public static int relight(ServerLevel world, BlockPos[] stagePositions) {
        int changed = 0;
        for (BlockPos standing : stagePositions) {
            if (standing == null || !world.getBlockState(standing.below()).is(Blocks.TARGET)) continue;
            for (int[] offset : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
                BlockPos light = standing.below().offset(offset[0], 0, offset[1]);
                if (world.getBlockState(light).is(Blocks.CONCRETE.purple())
                        && world.setBlock(light, Blocks.CONCRETE.yellow().defaultBlockState(), 3)) {
                    changed++;
                }
            }
        }
        return changed;
    }
}
