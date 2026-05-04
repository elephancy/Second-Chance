package com.feel.gems.adventure.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RedlineGuardPlanTest {
    @Test
    void shelterMustBeReachedBeforeTheGuardLocks() {
        RedlineGuardPlan plan = new RedlineGuardPlan();
        assertEquals(RedlineGuardPlan.Wing.NORTH, plan.begin(RedlineGuardPlan.Wing.NORTH));
        assertFalse(plan.reach(RedlineGuardPlan.Wing.EAST));
        assertTrue(plan.reach(RedlineGuardPlan.Wing.NORTH));
        assertFalse(plan.reach(RedlineGuardPlan.Wing.NORTH));
        assertTrue(plan.broken());

        assertEquals(RedlineGuardPlan.Wing.EAST, plan.begin(RedlineGuardPlan.Wing.NORTH));
        plan.lock();
        assertFalse(plan.reach(RedlineGuardPlan.Wing.EAST));
        assertFalse(plan.broken());
    }

    @Test
    void targetWingUsesDominantDirection() {
        assertEquals(RedlineGuardPlan.Wing.WEST, RedlineGuardPlan.Wing.nearest(-4, 1));
        assertEquals(RedlineGuardPlan.Wing.SOUTH, RedlineGuardPlan.Wing.nearest(2, 5));
    }
}
