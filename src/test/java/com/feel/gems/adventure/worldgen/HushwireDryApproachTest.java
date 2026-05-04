package com.feel.gems.adventure.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class HushwireDryApproachTest {
    private static final BlockPos WEST_GATE = new BlockPos(466, 62, -1433);

    @Test
    void rejectsTheWetNaturalWestBank() {
        assertFalse(HushwireDryApproach.connected((x, z) ->
                        x >= 460 && x <= 464 && z >= -1435 && z <= -1431
                                ? new HushwireDryApproach.Column(63, false, true)
                                : new HushwireDryApproach.Column(63, true, true),
                WEST_GATE, Direction.WEST, Direction.NORTH, 63));
    }

    @Test
    void acceptsAConnectedDryBankAtAnyRotation() {
        HushwireDryApproach.Terrain dry = (x, z) ->
                new HushwireDryApproach.Column(63, true, true);
        for (Direction outward : Direction.Plane.HORIZONTAL) {
            Direction lateral = outward.getClockWise();
            assertTrue(HushwireDryApproach.connected(dry, WEST_GATE,
                    outward, lateral, 63));
        }
    }

    @Test
    void rejectsCliffGapCanopyAndMissingFlank() {
        assertFalse(HushwireDryApproach.connected((x, z) ->
                        new HushwireDryApproach.Column(x == 462 ? 66 : 63, true, true),
                WEST_GATE, Direction.WEST, Direction.NORTH, 63));
        assertFalse(HushwireDryApproach.connected((x, z) ->
                        new HushwireDryApproach.Column(63, true, x != 462),
                WEST_GATE, Direction.WEST, Direction.NORTH, 63));
        assertFalse(HushwireDryApproach.connected((x, z) ->
                        x == 462 && z == -1434 ? null
                                : new HushwireDryApproach.Column(63, true, true),
                WEST_GATE, Direction.WEST, Direction.NORTH, 63));
    }
}
