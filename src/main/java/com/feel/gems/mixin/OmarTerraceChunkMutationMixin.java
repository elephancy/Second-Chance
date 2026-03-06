package com.feel.gems.mixin;

import com.feel.gems.adventure.worldgen.OmarRetainingTerrace;
import com.feel.gems.adventure.undercard.UndercardShellCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records guarded post-generation edits before vanilla changes a chunk. */
@Mixin(LevelChunk.class)
public abstract class OmarTerraceChunkMutationMixin {
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void gems$markOmarTerraceDirty(BlockPos pos, BlockState state, int flags,
            CallbackInfoReturnable<BlockState> callback) {
        OmarRetainingTerrace.recordMutation((LevelChunk) (Object) this, pos);
        UndercardShellCustody.recordMutation((LevelChunk) (Object) this, pos);
    }
}
