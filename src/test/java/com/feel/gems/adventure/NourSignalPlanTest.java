package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NourSignalPlanTest {
    @Test
    void observedCallWorksWithoutChartClicksAndMistakesResetOnlyTheLamps() {
        NourSignalPlan plan = new NourSignalPlan();
        assertEquals(NourSignalPlan.Result.NEXT, plan.press(NourSignalPlan.Color.BLUE));
        assertEquals(NourSignalPlan.Result.RESET, plan.press(NourSignalPlan.Color.WHITE));
        plan.readBoat();
        assertFalse(plan.ready());
        plan.readFog();
        assertTrue(plan.ready());
        assertTrue(plan.ready());
        assertEquals(NourSignalPlan.Result.NEXT, plan.press(NourSignalPlan.Color.BLUE));
        assertEquals(NourSignalPlan.Result.NEXT, plan.press(NourSignalPlan.Color.AMBER));
        assertEquals(NourSignalPlan.Result.COMPLETE, plan.press(NourSignalPlan.Color.WHITE));
    }
}
