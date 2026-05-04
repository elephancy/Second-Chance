package com.feel.gems.adventure.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class StageOpeningStateTest {
    @Test
    void openingCyclesAndIsConsumedOnlyOnce() {
        StageOpeningState state = new StageOpeningState();
        assertNull(state.pending());
        assertEquals("Spotlight", state.cycle().label());
        assertEquals("Crosscut", state.cycle().label());
        assertEquals(CadenzaPattern.Act.CROSSCUT, state.consume());
        assertNull(state.pending());
        assertNull(state.consume());
    }

    @Test
    void pendingOpeningSurvivesStateEncoding() {
        StageOpeningState state = new StageOpeningState();
        state.cycle();
        state.cycle();
        state.cycle();
        var encoded = StageOpeningState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        StageOpeningState restored = StageOpeningState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals("Chorus", restored.pending().label());
        assertEquals(CadenzaPattern.Act.CHORUS, restored.consume());
        assertNull(restored.pending());
    }
}
