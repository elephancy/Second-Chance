package com.feel.gems.adventure.boss;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** Turn intact authored receivers gold; safe to retry after a partial save. */
final class EncoreRestoration {
    private EncoreRestoration() { }

    static int reveal(ServerLevel world, BlockPos center, int quarterTurns) {
        if (center == null || !world.getBlockState(center.below()).is(Blocks.LODESTONE)) return 0;
        int changed = 0;
        for (EncorePattern.Receiver receiver : EncorePattern.Receiver.values()) {
            BlockPos pad = EncorePattern.pad(center, receiver, quarterTurns).below();
            if (world.getBlockState(pad).is(Blocks.CONCRETE.blue())
                    && world.setBlock(pad, Blocks.GOLD_BLOCK.defaultBlockState(), 3)) changed++;
        }
        return changed;
    }
}
