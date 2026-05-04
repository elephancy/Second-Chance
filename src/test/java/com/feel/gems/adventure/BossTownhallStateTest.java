package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class BossTownhallStateTest {
    @Test
    void approachMarksBelongToEachExpedition() {
        AngleApproachState state = new AngleApproachState();
        BlockPos framefall = new BlockPos(120, 70, -450);
        BlockPos branch = new BlockPos(-800, 96, 1100);
        assertTrue(state.mark(LandmarkKey.FRAMEFALL, framefall));
        assertTrue(state.mark(LandmarkKey.BRANCH, branch));
        assertFalse(state.mark(LandmarkKey.FRAMEFALL, framefall));
        assertEquals(framefall, state.approach(LandmarkKey.FRAMEFALL));
        assertEquals(branch, state.approach(LandmarkKey.BRANCH));
        assertNull(state.approach(LandmarkKey.LASTLIGHT));
    }

    @Test
    void sharedReturnExpiresAndCannotBeOverwrittenEarly() {
        LastlightReturnState state = new LastlightReturnState();
        BlockPos first = new BlockPos(20, 64, 30);
        BlockPos second = new BlockPos(22, 65, 36);
        assertTrue(state.mark(first, 100));
        assertFalse(state.mark(second, 101));
        assertEquals(first, state.activePoint(100 + LastlightReturnState.WINDOW_TICKS - 1));
        assertEquals(1, state.remaining(100 + LastlightReturnState.WINDOW_TICKS - 1));
        assertNull(state.activePoint(100 + LastlightReturnState.WINDOW_TICKS));
        assertTrue(state.mark(second, 100 + LastlightReturnState.WINDOW_TICKS));
        assertEquals(second, state.activePoint(100 + LastlightReturnState.WINDOW_TICKS));
    }

    @Test
    void returnRangeChecksHorizontalAndHeightBounds() {
        BlockPos origin = new BlockPos(0, 70, 0);
        assertTrue(TownhallSafeGround.within(origin, new BlockPos(128, 102, 0), 128, 32));
        assertFalse(TownhallSafeGround.within(origin, new BlockPos(129, 70, 0), 128, 32));
        assertFalse(TownhallSafeGround.within(origin, new BlockPos(0, 103, 0), 128, 32));
    }
}
