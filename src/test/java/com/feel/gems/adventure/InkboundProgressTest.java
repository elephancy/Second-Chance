package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class InkboundProgressTest {
    @Test
    void everyDifferentPairUnlocksOnceForTheWholeWorld() {
        InkboundProgress progress = new InkboundProgress(0);
        assertEquals(-1, InkboundProgress.pair(0, 0));
        assertEquals(0, InkboundProgress.pair(1, 0));
        assertEquals(1, InkboundProgress.pair(0, 2));
        assertEquals(2, InkboundProgress.pair(2, 1));
        assertTrue(progress.record(InkboundProgress.pair(0, 1)));
        assertFalse(progress.record(InkboundProgress.pair(1, 0)));
        assertFalse(progress.unlocked());
        assertTrue(progress.record(InkboundProgress.pair(2, 0)));
        assertTrue(progress.record(InkboundProgress.pair(1, 2)));
        assertTrue(progress.unlocked());
        assertEquals(7, new InkboundProgress(progress.triedMask()).triedMask());
    }
}
