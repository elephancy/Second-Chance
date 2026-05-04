package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class AliRouteStateTest {
    @Test
    void aCorrectDeductionOpensWithoutPlaqueClicks() {
        AliRouteState state = new AliRouteState();
        assertEquals(AliRouteState.Choice.WRONG, state.choose(false));
        assertFalse(state.solved());
        assertEquals(AliRouteState.Choice.SOLVED, state.choose(true));
        assertTrue(state.solved());
        state.read(AliRouteState.HIGH_MAP);
        assertFalse(state.ready());
        state.read(AliRouteState.GATE_RUBBING);
        assertTrue(state.ready());
        assertEquals(AliRouteState.Choice.ALREADY_SOLVED, state.choose(false));
    }

    @Test
    void shortcutRangeAndMarkProximityHaveSeparateBounds() {
        BlockPos origin = new BlockPos(0, 70, 0);
        assertTrue(ShortcutRoute.inRange(origin, new BlockPos(128, 102, 0)));
        assertFalse(ShortcutRoute.inRange(origin, new BlockPos(129, 70, 0)));
        assertFalse(ShortcutRoute.inRange(origin, new BlockPos(0, 103, 0)));
        assertTrue(ShortcutRoute.near(origin, new BlockPos(4, 70, 0)));
        assertFalse(ShortcutRoute.near(origin, new BlockPos(4, 70, 1)));
    }
}
