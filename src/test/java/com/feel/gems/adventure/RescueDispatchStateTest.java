package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class RescueDispatchStateTest {
    @Test
    void rallyIsSharedOnceAndExpiresAfterThreeMinutes() {
        RescueDispatchState state = new RescueDispatchState();
        BlockPos first = new BlockPos(120, 72, -30);
        BlockPos second = new BlockPos(-40, 65, 290);
        assertTrue(state.call(first, 100));
        assertFalse(state.call(second, 101));
        assertEquals(first, state.activePoint(100 + RescueDispatchState.WINDOW_TICKS - 1));
        assertEquals(1, state.remaining(100 + RescueDispatchState.WINDOW_TICKS - 1));
        assertNull(state.activePoint(100 + RescueDispatchState.WINDOW_TICKS));
        assertTrue(state.call(second, 100 + RescueDispatchState.WINDOW_TICKS));
        assertEquals(second, state.activePoint(100 + RescueDispatchState.WINDOW_TICKS));
    }

    @Test
    void rallySurvivesStateEncoding() {
        RescueDispatchState state = new RescueDispatchState();
        BlockPos point = new BlockPos(-170, 64, 800);
        state.call(point, 500);
        var encoded = RescueDispatchState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        RescueDispatchState restored = RescueDispatchState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(point, restored.activePoint(501));
        assertEquals(RescueDispatchState.WINDOW_TICKS - 1, restored.remaining(501));
    }
}
