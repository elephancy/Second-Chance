package com.feel.gems.adventure.estate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class EstateRouteProgressTest {
    @Test
    void eachWingRequiresItsOwnOrderedFourSeams() {
        assertWing('N', new int[]{2, 1, 6, 7, 12});
        assertWing('E', new int[]{14, 9, 8, 13, 12});
        assertWing('S', new int[]{22, 23, 18, 17, 12});
        assertWing('W', new int[]{10, 15, 16, 11, 12});
    }

    @Test
    void armedSeamRejectsAnEditedWallDetourAcrossAdjacentModules() {
        EstateRouteProgress progress = new EstateRouteProgress('N');
        EstateRouteSite.Seam seam = new EstateRouteSite.Seam(2, 1,
                new BlockPos(100, 68, 100), new BlockPos(100, 68, 88),
                Direction.NORTH);
        BlockPos authoredLane = new BlockPos(100, 69, 94);
        BlockPos wallDetour = new BlockPos(108, 69, 94);
        assertTrue(progress.acceptsSeamPosition(seam, wallDetour));
        progress.enterSeam(true);
        assertTrue(progress.acceptsSeamPosition(seam, authoredLane));
        assertFalse(progress.acceptsSeamPosition(seam, wallDetour));
        assertFalse(progress.arrive(1, false));
    }

    @Test
    void nextModuleBoundaryIsNotTheSeamExit() {
        EstateRouteProgress progress = new EstateRouteProgress('N');
        EstateRouteSite.Seam seam = new EstateRouteSite.Seam(2, 1,
                new BlockPos(100, 68, 100), new BlockPos(100, 68, 88),
                Direction.NORTH);
        BlockPos boundary = new BlockPos(100, 69, 94);
        BlockPos wallDetour = new BlockPos(106, 69, 94);
        assertFalse(progress.continueToExit(1, seam, boundary, false));
        progress.enterSeam(true);
        assertFalse(progress.continueToExit(6, seam, boundary, false));
        assertFalse(progress.continueToExit(1, seam, wallDetour, false));
        assertTrue(progress.continueToExit(1, seam, boundary, false));
        assertEquals(2, progress.current());
        assertTrue(progress.seamEntered());
        assertTrue(progress.continueToExit(1, seam,
                new BlockPos(100, 69, 93), true));
        assertEquals(1, progress.current());
        assertFalse(progress.seamEntered());
    }

    private static void assertWing(char wing, int[] expected) {
        EstateRouteProgress progress = new EstateRouteProgress(wing);
        assertEquals(expected[0], progress.current());
        assertFalse(progress.complete());
        for (int i = 1; i < expected.length; i++) {
            assertEquals(expected[i], progress.next());
            assertFalse(progress.arrive(expected[i], true));
            progress.enterSeam(false);
            assertFalse(progress.arrive(expected[i], true));
            progress.enterSeam(true);
            assertFalse(progress.arrive(expected[i], false));
            assertFalse(progress.arrive(i + 1 < expected.length ? expected[i + 1] : -1,
                    true));
            assertTrue(progress.arrive(expected[i], true));
        }
        assertTrue(progress.complete());
        assertEquals(-1, progress.next());
    }
}
