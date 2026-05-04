package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RepairRouteStateTest {
    @Test
    void eachPhysicalSiteCountsOnceAndCompletionNeedsAllThree() {
        RepairRouteState state = new RepairRouteState();
        assertFalse(state.soryalComplete());
        assertTrue(state.recordRepair(RepairRouteState.BRIDGE));
        assertFalse(state.recordRepair(RepairRouteState.BRIDGE));
        assertEquals(1, state.repairedCount());
        assertTrue(state.recordRepair(RepairRouteState.LOFT));
        assertFalse(state.soryalComplete());
        assertTrue(state.recordRepair(RepairRouteState.YARD));
        assertTrue(state.soryalComplete());
        assertEquals(3, state.repairedCount());
        assertThrows(IllegalArgumentException.class, () -> state.recordRepair(8));
    }

    @Test
    void riverwardNeedsSoryalAndTwoDifferentBracesBeforeFinishing() {
        RepairRouteState state = new RepairRouteState();
        assertFalse(state.recordRiverwardBrace(RepairRouteState.WEST_BRACE));
        assertFalse(state.finishRiverward());
        assertFalse(state.finishRedline());
        state.recordRepair(RepairRouteState.YARD);
        state.recordRepair(RepairRouteState.BRIDGE);
        state.recordRepair(RepairRouteState.LOFT);
        assertTrue(state.recordRiverwardBrace(RepairRouteState.WEST_BRACE));
        assertFalse(state.recordRiverwardBrace(RepairRouteState.WEST_BRACE));
        assertFalse(state.finishRiverward());
        assertTrue(state.recordRiverwardBrace(RepairRouteState.EAST_BRACE));
        assertEquals(2, state.riverwardBraceCount());
        assertTrue(state.finishRiverward());
        assertTrue(state.riverwardComplete());
        assertFalse(state.finishRiverward());
        assertTrue(state.finishRedline());
        assertTrue(state.redlineComplete());
        assertFalse(state.finishRedline());
    }
}
