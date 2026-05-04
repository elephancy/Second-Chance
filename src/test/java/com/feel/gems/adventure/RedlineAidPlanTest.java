package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RedlineAidPlanTest {
    @Test
    void requiresSustainedShelterInOrderAndFinishesBeforeTimeout() {
        RedlineAidPlan plan = new RedlineAidPlan(100);
        for (int tick = 105; tick <= 135; tick += 5) {
            assertEquals(RedlineAidPlan.Event.NONE, plan.tick(tick, true, false));
        }
        assertEquals(RedlineAidPlan.Event.NONE, plan.tick(140, false, false));
        for (int tick = 145; tick < 200; tick += 5) {
            assertEquals(RedlineAidPlan.Event.NONE, plan.tick(tick, true, false));
        }
        assertEquals(RedlineAidPlan.Event.EAST_HELD, plan.tick(200, true, false));
        assertFalse(plan.ready(200));
        for (int tick = 205; tick < 260; tick += 5) {
            assertEquals(RedlineAidPlan.Event.NONE, plan.tick(tick, false, true));
        }
        assertEquals(RedlineAidPlan.Event.WEST_HELD, plan.tick(260, false, true));
        assertTrue(plan.ready(260));
        assertFalse(plan.ready(2501));
        assertEquals(RedlineAidPlan.Event.EXPIRED, plan.tick(2501, false, false));
    }
}
