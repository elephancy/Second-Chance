package com.feel.gems.adventure.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class EncorePatternTest {
    @Test
    void oneReceiverIsRealAndTheNextCallMoves() {
        EncorePattern pattern = new EncorePattern();
        assertEquals(EncorePattern.Receiver.LEFT, pattern.next(0));
        assertTrue(pattern.isReal(EncorePattern.Receiver.LEFT));
        assertFalse(pattern.isReal(EncorePattern.Receiver.RIGHT));
        assertEquals(EncorePattern.Receiver.RIGHT, pattern.next(0));
        assertFalse(pattern.isReal(EncorePattern.Receiver.LEFT));
        assertEquals(EncorePattern.Receiver.LEFT, pattern.next(1));
        assertEquals(EncorePattern.Receiver.BACK, pattern.next(1));
    }

    @Test
    void receiverPositionsRotateWithTheGeneratedHall() {
        BlockPos center = new BlockPos(100, 70, -40);
        assertEquals(center.offset(0, 0, 9),
                EncorePattern.pad(center, EncorePattern.Receiver.BACK, 0));
        assertEquals(center.offset(-9, 0, 0),
                EncorePattern.pad(center, EncorePattern.Receiver.BACK, 1));
        assertEquals(center.offset(0, 0, -9),
                EncorePattern.pad(center, EncorePattern.Receiver.BACK, 2));
        assertEquals(center.offset(9, 0, 0),
                EncorePattern.pad(center, EncorePattern.Receiver.BACK, 3));
        assertEquals(center.offset(6, 0, -4),
                EncorePattern.pad(center, EncorePattern.Receiver.LEFT, 1));
    }
}
