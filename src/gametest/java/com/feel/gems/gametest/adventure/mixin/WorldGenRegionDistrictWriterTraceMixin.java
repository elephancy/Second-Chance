package com.feel.gems.gametest.adventure.mixin;

import com.feel.gems.gametest.adventure.trace.DistrictWriterTrace;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opt-in recorder; the production mod does not load this GameTest mixin. */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionDistrictWriterTraceMixin {
    @Unique
    private static final ThreadLocal<ArrayDeque<DistrictWriterTrace.Pending>> GEMS_TRACE_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "setBlock", at = @At("HEAD"))
    private void gems$beforeDistrictWrite(BlockPos pos, BlockState state,
            int flags, int updateLimit, CallbackInfoReturnable<Boolean> callback) {
        if (!DistrictWriterTrace.enabled()) return;
        GEMS_TRACE_STACK.get().push(DistrictWriterTrace.before(
                (WorldGenRegion) (Object) this, pos, state));
    }

    @Inject(method = "setBlock", at = @At("RETURN"))
    private void gems$afterDistrictWrite(BlockPos pos, BlockState state,
            int flags, int updateLimit, CallbackInfoReturnable<Boolean> callback) {
        if (!DistrictWriterTrace.enabled()) return;
        ArrayDeque<DistrictWriterTrace.Pending> stack = GEMS_TRACE_STACK.get();
        DistrictWriterTrace.after((WorldGenRegion) (Object) this,
                stack.pop(), callback.getReturnValueZ());
        if (stack.isEmpty()) GEMS_TRACE_STACK.remove();
    }
}
