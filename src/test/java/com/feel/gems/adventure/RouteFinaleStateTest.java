package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteFinaleStateTest {
    @Test
    void repairMaterialsCannotCompleteTheFinaleAndStopChangingAfterItEnds() {
        RouteFinaleState state = new RouteFinaleState();
        assertFalse(state.complete());
        assertFalse(state.deliverRepairSupply(0));
        assertFalse(state.deliverRepairSupply(3));
        assertTrue(state.deliverRepairSupply(1));
        assertFalse(state.deliverRepairSupply(1));
        assertTrue(state.deliverRepairSupply(2));
        assertEquals(3, state.repairSupplies());
        assertFalse(state.complete());
        assertTrue(state.finish());
        assertFalse(state.finish());
        assertFalse(state.deliverRepairSupply(1));
    }

    @Test
    void civicServiceRequiresTheEndingAndAllowsOneClaimPerPlayerPerGameDay() {
        RouteFinaleState state = new RouteFinaleState();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertFalse(state.claimService(first, 0));
        assertTrue(state.finish());
        assertFalse(state.claimService(first, -1));
        assertTrue(state.claimService(first, 0));
        assertFalse(state.claimService(first, 0));
        assertEquals(1, state.nextServiceDay(first));
        assertTrue(state.claimService(second, 0));
        assertTrue(state.claimService(first, 1));
        assertFalse(state.claimService(first, 0));
        assertEquals(2, state.nextServiceDay(first));
    }
}
