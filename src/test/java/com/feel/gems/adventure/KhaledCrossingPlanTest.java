package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class KhaledCrossingPlanTest {
    @Test
    void requiresBothRefugesInOrderAndFinishesBeforeTheLimit() {
        KhaledCrossingPlan plan = new KhaledCrossingPlan(100);
        assertEquals(KhaledCrossingPlan.Event.NONE, plan.visit(false, true));
        assertFalse(plan.finish(110));
        assertEquals(KhaledCrossingPlan.Event.WEST_REACHED, plan.visit(true, false));
        assertEquals(KhaledCrossingPlan.Event.EAST_REACHED, plan.visit(false, true));
        assertTrue(plan.finish(100 + 20 * 75));
        assertFalse(plan.finish(100 + 20 * 75 + 1));
        assertTrue(plan.expired(100 + 20 * 75 + 1));
    }

    @Test
    void warnsBeforePulseAndOnlyMarkedShelterSurvives() {
        KhaledCrossingPlan exposed = new KhaledCrossingPlan(0);
        assertEquals(KhaledCrossingPlan.Event.NONE, exposed.tick(199, false));
        assertEquals(KhaledCrossingPlan.Event.WARNING, exposed.tick(200, false));
        assertEquals(KhaledCrossingPlan.Event.NONE, exposed.tick(230, false));
        assertEquals(KhaledCrossingPlan.Event.FLOODED, exposed.tick(240, false));

        KhaledCrossingPlan sheltered = new KhaledCrossingPlan(0);
        assertEquals(KhaledCrossingPlan.Event.WARNING, sheltered.tick(200, true));
        assertEquals(KhaledCrossingPlan.Event.SHELTERED, sheltered.tick(240, true));
        assertEquals(KhaledCrossingPlan.Event.WARNING, sheltered.tick(360, true));
        assertEquals(KhaledCrossingPlan.Event.EXPIRED, sheltered.tick(20 * 75 + 1, true));
    }
}
