package com.feel.gems.adventure.nether;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class EmberV6ReadinessStateTest {
    @Test
    void savedOwnerAndGeometrySurviveRestartAndReadyCanBeDemoted() {
        ChunkPos sealedOwner = new ChunkPos(-45, 8);
        BlockPos sealedRoot = new BlockPos(-697, 44, 105);
        BlockPos sealedArch = new BlockPos(-713, 45, 58);
        ChunkPos readyOwner = new ChunkPos(-16, -42);
        BlockPos readyRoot = new BlockPos(-233, 67, -695);
        BlockPos readyArch = new BlockPos(-217, 68, -648);
        EmberV6ReadinessState state = new EmberV6ReadinessState();
        state.observe(sealedOwner, sealedRoot, sealedArch,
                EmberV6ReadinessState.Verdict.SEALED);
        state.observe(readyOwner, readyRoot, readyArch,
                EmberV6ReadinessState.Verdict.READY);

        var encoded = EmberV6ReadinessState.CODEC.encodeStart(JsonOps.INSTANCE, state)
                .getOrThrow();
        EmberV6ReadinessState restored = EmberV6ReadinessState.CODEC
                .parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(EmberV6ReadinessState.Verdict.SEALED,
                restored.verdict(sealedOwner, sealedRoot, sealedArch));
        assertEquals(EmberV6ReadinessState.Verdict.READY,
                restored.verdict(readyOwner, readyRoot, readyArch));
        assertEquals(EmberV6ReadinessState.Verdict.UNKNOWN,
                restored.verdict(new ChunkPos(-15, -42), readyRoot, readyArch));
        assertEquals(EmberV6ReadinessState.Verdict.UNKNOWN,
                restored.verdict(readyOwner, readyRoot.offset(1, 0, 0), readyArch));
        assertEquals(EmberV6ReadinessState.Verdict.UNKNOWN,
                restored.verdict(readyOwner, readyRoot, readyArch.offset(1, 0, 0)));

        restored.observe(readyOwner, readyRoot, readyArch,
                EmberV6ReadinessState.Verdict.SEALED);
        assertEquals(EmberV6ReadinessState.Verdict.SEALED,
                restored.verdict(readyOwner, readyRoot, readyArch));
    }
}
