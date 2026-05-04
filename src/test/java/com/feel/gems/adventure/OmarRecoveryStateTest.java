package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.serialization.JsonOps;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class OmarRecoveryStateTest {
    private static final UUID OWNER = UUID.fromString("30d98955-d10f-4ddb-85cf-24b56ff598ca");
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final Identifier NETHER = Identifier.withDefaultNamespace("the_nether");

    @Test
    void latestFallAndDimensionSurviveSerialization() {
        OmarRecoveryState state = new OmarRecoveryState();
        state.record(OWNER, OVERWORLD, new BlockPos(100, 65, -20));
        state.record(OWNER, NETHER, new BlockPos(-55, 72, 18));

        var encoded = OmarRecoveryState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        OmarRecoveryState restored = OmarRecoveryState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(NETHER, restored.trace(OWNER).dimension());
        assertEquals(new BlockPos(-55, 72, 18), restored.trace(OWNER).pos());
        assertNull(restored.trace(UUID.randomUUID()));
    }

    @Test
    void onlySameDimensionAndNearbyFallsRevealAHeadingOrSweep() {
        OmarRecoveryState.Trace trace = new OmarRecoveryState.Trace(OVERWORLD, 0, 70, 0);
        assertEquals(OmarRecoveryPlan.Range.OTHER_DIMENSION,
                OmarRecoveryPlan.range(NETHER, new BlockPos(0, 70, 0), trace));
        assertEquals(OmarRecoveryPlan.Range.FAR,
                OmarRecoveryPlan.range(OVERWORLD, new BlockPos(1025, 70, 0), trace));
        assertEquals(OmarRecoveryPlan.Range.BEARING,
                OmarRecoveryPlan.range(OVERWORLD, new BlockPos(33, 70, 0), trace));
        assertEquals(OmarRecoveryPlan.Range.SWEEP,
                OmarRecoveryPlan.range(OVERWORLD, new BlockPos(32, 70, 0), trace));
    }
}
