package com.feel.gems.adventure.boss;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** Light only the four intact cues in the authored Unwritten stage. */
final class CadenzaRestoration {
    private CadenzaRestoration() { }

    static int light(ServerLevel world, BlockPos center) {
        if (center == null || !world.getBlockState(center.below()).is(Blocks.LODESTONE)) return 0;
        int changed = 0;
        for (int dx : new int[] {-5, 5}) {
            for (int dz : new int[] {-5, 5}) {
                BlockPos cue = center.offset(dx, 0, dz);
                if (world.getBlockState(cue).is(Blocks.CONCRETE.purple())
                        && world.setBlock(cue, Blocks.SEA_LANTERN.defaultBlockState(), 3)) changed++;
            }
        }
        return changed;
    }
}
