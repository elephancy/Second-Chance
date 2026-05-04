package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SoryalRepairStateTest {
    @Test
    void observedWearCanStartForgivingSharedPattern() {
        SoryalRepairState state = new SoryalRepairState();
        assertEquals(SoryalRepairState.Mark.PROGRESS, state.mark(SoryalRepairState.COPPER));
        assertEquals(1, state.step());
        assertEquals(SoryalRepairState.Mark.WRONG, state.mark(SoryalRepairState.BRICK));
        state.record(SoryalRepairState.SCRAP);
        assertFalse(state.ready());
        state.record(SoryalRepairState.LOFT);
        assertTrue(state.ready());
        assertEquals(SoryalRepairState.Mark.PROGRESS, state.mark(SoryalRepairState.COPPER));
        assertEquals(1, state.step());
        assertEquals(SoryalRepairState.Mark.WRONG, state.mark(SoryalRepairState.BRICK));
        assertEquals(0, state.step());
        assertEquals(SoryalRepairState.Mark.PROGRESS, state.mark(SoryalRepairState.COPPER));
        assertEquals(SoryalRepairState.Mark.PROGRESS, state.mark(SoryalRepairState.GLASS));
        assertEquals(SoryalRepairState.Mark.SOLVED, state.mark(SoryalRepairState.BRICK));
        assertTrue(state.solved());
        assertEquals(SoryalRepairState.Mark.ALREADY_SOLVED, state.mark(SoryalRepairState.BRICK));
    }
}
