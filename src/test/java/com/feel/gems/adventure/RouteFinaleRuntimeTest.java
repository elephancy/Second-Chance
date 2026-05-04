package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class RouteFinaleRuntimeTest {
    @Test
    void sideRoomsFollowTheRootMarksAfterRotation() {
        BlockPos dais = new BlockPos(23, 70, 32);
        BlockPos westMark = dais.offset(-5, 0, -9);
        BlockPos eastMark = dais.offset(5, 0, -9);
        assertEquals(dais.offset(-34, 0, -7),
                RouteFinaleRuntime.sideRoom(dais, westMark, eastMark, true));
        assertEquals(dais.offset(35, 0, -7),
                RouteFinaleRuntime.sideRoom(dais, westMark, eastMark, false));

        BlockPos rotatedWest = dais.offset(9, 0, -5);
        BlockPos rotatedEast = dais.offset(9, 0, 5);
        assertEquals(dais.offset(7, 0, -34),
                RouteFinaleRuntime.sideRoom(dais, rotatedWest, rotatedEast, true));
        assertEquals(dais.offset(7, 0, 35),
                RouteFinaleRuntime.sideRoom(dais, rotatedWest, rotatedEast, false));
    }

    @Test
    void aSecondCourtCannotReplaceLiveSharedProgress() {
        BlockPos firstDais = new BlockPos(0, 70, 0);
        BlockPos secondDais = new BlockPos(192, 70, 0);
        RouteFinaleRuntime.Attempt remember = new RouteFinaleRuntime.Attempt(
                CampaignRouteState.Path.REMEMBER, firstDais,
                firstDais.offset(-5, 0, 0), firstDais.offset(5, 0, 0),
                firstDais.offset(-34, 0, -7), firstDais.offset(35, 0, -7), 100);
        remember.bound = 1;
        assertTrue(remember.blocksOtherDais(secondDais, 200));
        assertEquals(1, remember.bound);
        assertFalse(remember.blocksOtherDais(firstDais, 200));
        assertFalse(remember.blocksOtherDais(secondDais, 12_101));

        RouteFinaleRuntime.Attempt release = new RouteFinaleRuntime.Attempt(
                CampaignRouteState.Path.RELEASE, firstDais,
                firstDais.offset(-5, 0, 0), firstDais.offset(5, 0, 0),
                firstDais.offset(-34, 0, -7), firstDais.offset(35, 0, -7), 100);
        assertTrue(release.blocksOtherDais(secondDais, 3_700));
        assertFalse(release.blocksOtherDais(secondDais, 3_701));
    }

    @Test
    void nearbySecondCourtStationCannotAdvanceActiveCourt() {
        BlockPos dais = new BlockPos(0, 70, 0);
        BlockPos westRoom = dais.offset(-34, 0, -7);
        BlockPos eastRoom = dais.offset(35, 0, -7);
        RouteFinaleRuntime.Attempt attempt = new RouteFinaleRuntime.Attempt(
                CampaignRouteState.Path.REMEMBER, dais,
                dais.offset(-5, 0, -9), dais.offset(5, 0, -9),
                westRoom, eastRoom, 100);
        BlockPos secondCourtWestRoom = dais.offset(78, 0, -7);
        assertTrue(attempt.nearby(secondCourtWestRoom));
        assertTrue(attempt.owns(RouteFinaleRuntime.Station.WEST_ROOM, westRoom));
        assertTrue(attempt.owns(RouteFinaleRuntime.Station.EAST_ROOM, eastRoom));
        assertFalse(attempt.owns(RouteFinaleRuntime.Station.WEST_ROOM, secondCourtWestRoom));
        assertFalse(attempt.owns(RouteFinaleRuntime.Station.EAST_ROOM, westRoom));
    }

    @Test
    void crewMustBeShieldedFromATelegraphedStrikeBeforeRepairCanFinish() {
        RouteFinaleRuntime.Attempt attempt = new RouteFinaleRuntime.Attempt(
                CampaignRouteState.Path.REPAIR, BlockPos.ZERO,
                new BlockPos(-5, 0, 0), new BlockPos(5, 0, 0),
                new BlockPos(-34, 0, -7), new BlockPos(35, 0, -7), 100);
        attempt.held = 3;
        attempt.westHoldTicks = 80;
        attempt.eastHoldTicks = 80;
        attempt.firstRepairHoldAt = 100;
        assertFalse(attempt.repairReady(100));
        assertEquals(RouteFinaleRuntime.RepairHazard.NONE,
                attempt.advanceRepairHazard(100, 1, true));
        assertEquals(-1, attempt.repairStrikeAt);
        assertEquals(RouteFinaleRuntime.RepairHazard.NONE,
                attempt.advanceRepairHazard(100, 3, false));
        assertEquals(RouteFinaleRuntime.RepairHazard.NONE,
                attempt.advanceRepairHazard(219, 3, false));
        assertEquals(RouteFinaleRuntime.RepairHazard.WARNING,
                attempt.advanceRepairHazard(220, 3, false));
        assertEquals(RouteFinaleRuntime.RepairHazard.BREACHED,
                attempt.advanceRepairHazard(300, 3, false));
        assertEquals(2, attempt.held);
        assertEquals(0, attempt.westHoldTicks);
        assertFalse(attempt.defendedStrike);
        assertEquals(RouteFinaleRuntime.RepairHazard.WARNING,
                attempt.advanceRepairHazard(420, 3, false));
        assertEquals(RouteFinaleRuntime.RepairHazard.GUARDED,
                attempt.advanceRepairHazard(500, 3, true));
        assertTrue(attempt.defendedStrike);
        assertFalse(attempt.repairReady(500));
        attempt.held = 3;
        attempt.firstRepairHoldAt = 500;
        assertTrue(attempt.repairReady(501));
    }

    @Test
    void repairWindowExpiresAfterThirtySecondsAndCanBeRetried() {
        RouteFinaleRuntime.Attempt attempt = new RouteFinaleRuntime.Attempt(
                CampaignRouteState.Path.REPAIR, BlockPos.ZERO,
                new BlockPos(-5, 0, 0), new BlockPos(5, 0, 0),
                new BlockPos(-34, 0, -7), new BlockPos(35, 0, -7), 100);
        assertFalse(attempt.repairExpired(1000));
        attempt.firstRepairHoldAt = 100;
        attempt.held = 1;
        attempt.westHoldTicks = 80;
        attempt.readyAnnounced = true;
        attempt.defendedStrike = true;
        attempt.repairStrikeAt = 690;
        attempt.repairWarned = true;
        attempt.repairTarget = 2;
        assertFalse(attempt.repairExpired(700));
        assertTrue(attempt.repairExpired(701));
        attempt.resetRepairHolds();
        assertEquals(0, attempt.held);
        assertEquals(0, attempt.westHoldTicks);
        assertEquals(-1, attempt.firstRepairHoldAt);
        assertEquals(-1, attempt.repairStrikeAt);
        assertEquals(1, attempt.repairTarget);
        assertFalse(attempt.repairWarned);
        assertFalse(attempt.defendedStrike);
        assertFalse(attempt.readyAnnounced);
        assertFalse(attempt.repairExpired(702));
        attempt.held = 3;
        attempt.firstRepairHoldAt = 702;
        assertFalse(attempt.repairReady(703));
    }

    @Test
    void endEvidenceChangesEachCourtOpeningWithoutChangingOldWorlds() {
        BlockPos dais = BlockPos.ZERO;
        BlockPos west = new BlockPos(-5, 0, 0);
        BlockPos east = new BlockPos(5, 0, 0);
        BlockPos westRoom = new BlockPos(-34, 0, -7);
        BlockPos eastRoom = new BlockPos(35, 0, -7);
        for (CampaignRouteState.Path path : CampaignRouteState.Path.values()) {
            RouteFinaleRuntime.Attempt oldWorld = new RouteFinaleRuntime.Attempt(
                    path, dais, west, east, westRoom, eastRoom, 100, false);
            RouteFinaleRuntime.Attempt withSource = new RouteFinaleRuntime.Attempt(
                    path, dais, west, east, westRoom, eastRoom, 100, true);
            switch (path) {
                case REMEMBER -> {
                    assertEquals(0, oldWorld.bound);
                    assertEquals(1, withSource.bound);
                    assertEquals(0, oldWorld.effectiveRepairSupplies(0));
                }
                case REPAIR -> {
                    assertEquals(0, oldWorld.effectiveRepairSupplies(0));
                    assertEquals(1, withSource.effectiveRepairSupplies(0));
                    assertEquals(3, withSource.effectiveRepairSupplies(2));
                }
                case RELEASE -> {
                    assertEquals(0, oldWorld.releaseStep);
                    assertEquals(1, withSource.releaseStep);
                    assertFalse(oldWorld.nextRelease() == withSource.nextRelease());
                }
            }
        }
    }
}
