package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OmarLostStateTest {
    @Test
    void theMissingObjectCanBeChosenFromEnvironmentalEvidence() {
        OmarLostState state = new OmarLostState();
        assertEquals(OmarLostState.Choice.WRONG, state.choose(OmarLostState.CLOCK));
        assertEquals(OmarLostState.Choice.SOLVED, state.choose(OmarLostState.COMPASS));
        assertTrue(state.solved());
        state.record(OmarLostState.RECEIPT);
        assertFalse(state.ready());
        state.record(OmarLostState.LEDGER);
        assertTrue(state.ready());
        assertEquals(OmarLostState.Choice.ALREADY_SOLVED, state.choose(OmarLostState.BOOK));
        assertEquals(OmarLostState.Choice.ALREADY_SOLVED, state.choose(OmarLostState.COMPASS));
    }
}
