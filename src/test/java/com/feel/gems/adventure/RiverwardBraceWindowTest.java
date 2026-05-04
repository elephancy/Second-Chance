package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RiverwardBraceWindowTest {
    @Test
    void onlyTheShelteredRefugeOpensForThreeSeconds() {
        RiverwardBraceWindow window = new RiverwardBraceWindow();
        assertFalse(window.canRepair(RepairRouteState.WEST_BRACE, 100));
        window.shelteredPulse(RepairRouteState.WEST_BRACE, 160);
        assertTrue(window.canRepair(RepairRouteState.WEST_BRACE, 160));
        assertFalse(window.canRepair(RepairRouteState.EAST_BRACE, 160));
        assertTrue(window.canRepair(RepairRouteState.WEST_BRACE, 220));
        assertFalse(window.canRepair(RepairRouteState.WEST_BRACE, 221));
        window.shelteredPulse(RepairRouteState.EAST_BRACE, 320);
        assertTrue(window.canRepair(RepairRouteState.EAST_BRACE, 350));
        assertFalse(window.canRepair(RepairRouteState.WEST_BRACE, 350));
    }
}
