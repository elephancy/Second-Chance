package com.feel.gems.adventure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** Pure range and dimension rules for the transferable recovery slip. */
final class OmarRecoveryPlan {
    static final int REACH = 1024;
    static final int SWEEP_REACH = 32;

    enum Range { OTHER_DIMENSION, FAR, BEARING, SWEEP }

    private OmarRecoveryPlan() { }

    static Range range(Identifier currentDimension, BlockPos player, OmarRecoveryState.Trace trace) {
        if (!currentDimension.equals(trace.dimension())) return Range.OTHER_DIMENSION;
        long dx = (long) trace.x() - player.getX();
        long dz = (long) trace.z() - player.getZ();
        long distance = dx * dx + dz * dz;
        if (distance > (long) REACH * REACH) return Range.FAR;
        return distance <= (long) SWEEP_REACH * SWEEP_REACH ? Range.SWEEP : Range.BEARING;
    }
}
