package com.feel.gems.adventure.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VerdictSequenceTest {
    @Test
    void eachShotHasAReadableTellAndRecoveryWindow() {
        VerdictSequence sequence = new VerdictSequence();
        assertFalse(sequence.tick());
        sequence.start();
        assertEquals(VerdictSequence.Phase.TELL, sequence.phase());
        for (int i = 0; i < VerdictSequence.LOCK_TICK - 1; i++) sequence.tick();
        assertFalse(sequence.aimLocked());
        sequence.tick();
        assertTrue(sequence.aimLocked());
        for (int i = VerdictSequence.LOCK_TICK; i < VerdictSequence.TELL_TICKS - 1; i++) {
            assertFalse(sequence.tick());
        }
        assertTrue(sequence.tick());
        assertEquals(VerdictSequence.Phase.FIRE, sequence.phase());
        for (int i = 0; i < VerdictSequence.FIRE_TICKS; i++) sequence.tick();
        assertEquals(VerdictSequence.Phase.RECOVERY, sequence.phase());
        for (int i = 0; i < VerdictSequence.RECOVERY_TICKS; i++) sequence.tick();
        assertEquals(VerdictSequence.Phase.TELL, sequence.phase());
        assertFalse(sequence.aimLocked());
    }

    @Test
    void unloadingCanResetToSafeDormantState() {
        VerdictSequence sequence = new VerdictSequence();
        sequence.start();
        for (int i = 0; i < VerdictSequence.TELL_TICKS; i++) sequence.tick();
        sequence.reset();
        assertEquals(VerdictSequence.Phase.DORMANT, sequence.phase());
        assertFalse(sequence.tick());
        sequence.start();
        assertEquals(0, sequence.ticks());
        assertEquals(VerdictSequence.Phase.TELL, sequence.phase());
    }
}
