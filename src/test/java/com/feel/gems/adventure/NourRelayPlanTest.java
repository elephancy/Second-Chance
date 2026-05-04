package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NourRelayPlanTest {
    @Test
    void eitherHouseCanAnswerFirstButBothMustAnswerBeforeWindowCloses() {
        NourRelayPlan plan = new NourRelayPlan(100);
        assertFalse(plan.ready(100));
        assertTrue(plan.record(NourRelayPlan.FOGHOUSE, 140));
        assertFalse(plan.record(NourRelayPlan.FOGHOUSE, 150));
        assertEquals(1, plan.count());
        assertTrue(plan.record(NourRelayPlan.BOATHOUSE, 170));
        assertTrue(plan.ready(170));
        assertEquals(2, plan.count());
        assertFalse(plan.ready(3701));
        assertThrows(IllegalArgumentException.class, () -> plan.record(4, 200));
    }

    @Test
    void lateHouseCannotFinishAnExpiredWindow() {
        NourRelayPlan plan = new NourRelayPlan(100);
        assertTrue(plan.record(NourRelayPlan.BOATHOUSE, 100));
        assertFalse(plan.record(NourRelayPlan.FOGHOUSE, 3701));
        assertFalse(plan.ready(3701));
    }
}
