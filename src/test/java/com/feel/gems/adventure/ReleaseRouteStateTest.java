package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class ReleaseRouteStateTest {
    @Test
    void chalkTripMustConnectTwoNearbyRecordedAnchorsAfterNoursRelay() {
        ReleaseRouteState state = new ReleaseRouteState();
        BlockPos high = new BlockPos(60, 72, 10);
        BlockPos low = new BlockPos(-10, 64, 12);
        assertFalse(state.recordAliAnchor(ReleaseRouteState.HIGH_MAP, high));
        assertFalse(state.finishAliShortcut());
        assertTrue(state.finishNourRelay());
        assertTrue(state.recordAliAnchor(ReleaseRouteState.HIGH_MAP, high));
        assertFalse(state.recordAliAnchor(ReleaseRouteState.HIGH_MAP, high));
        assertFalse(state.recordAliAnchor(ReleaseRouteState.CUT_PASSAGE,
                new BlockPos(500, 64, 12)));
        assertTrue(state.recordAliAnchor(ReleaseRouteState.CUT_PASSAGE, low));
        assertTrue(state.aliAnchorsReady());
        assertFalse(state.matchesAliTrip(high, new BlockPos(0, 64, 0)));
        assertTrue(state.matchesAliTrip(high.above(), low.above()));
        assertTrue(state.matchesAliTrip(low.above(), high.above()));
        assertTrue(state.finishAliShortcut());
        assertTrue(state.aliShortcutComplete());
        assertFalse(state.matchesAliTrip(high.above(), low.above()));
        assertFalse(state.finishAliShortcut());
    }

    @Test
    void forumDispatchNeedsBothStopsAndTheCurrentPhysicalMessage() {
        ReleaseRouteState state = new ReleaseRouteState();
        assertTrue(state.finishNourRelay());
        assertTrue(state.recordAliAnchor(ReleaseRouteState.HIGH_MAP, new BlockPos(0, 64, 0)));
        assertTrue(state.recordAliAnchor(ReleaseRouteState.CUT_PASSAGE, new BlockPos(30, 64, 0)));
        assertTrue(state.finishAliShortcut());
        int first = state.startFamDispatch(100);
        assertTrue(first > 0);
        assertFalse(state.finishFamDispatch(first, 100));
        assertTrue(state.stampFamDispatch(first, ReleaseRouteState.FAM_STORY, 110));
        assertFalse(state.stampFamDispatch(first, ReleaseRouteState.FAM_STORY, 111));
        assertFalse(state.finishFamDispatch(first, 112));
        assertFalse(state.stampFamDispatch(first, ReleaseRouteState.FAM_SCHOOL, 100 + 20 * 60 * 5 + 1));
        int second = state.startFamDispatch(100 + 20 * 60 * 5 + 1);
        assertTrue(second > first);
        assertFalse(state.stampFamDispatch(first, ReleaseRouteState.FAM_SCHOOL, 100 + 20 * 60 * 5 + 2));
        assertTrue(state.stampFamDispatch(second, ReleaseRouteState.FAM_SCHOOL, 100 + 20 * 60 * 5 + 2));
        assertTrue(state.stampFamDispatch(second, ReleaseRouteState.FAM_STORY, 100 + 20 * 60 * 5 + 3));
        assertTrue(state.finishFamDispatch(second, 100 + 20 * 60 * 5 + 4));
        assertTrue(state.famDispatchComplete());
        assertFalse(state.startFamDispatch(100 + 20 * 60 * 5 + 5) > 0);
    }
}
