package com.feel.gems.adventure.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SecondMovePlanTest {
    @Test
    void committedRouteMissesWhenTargetSwitchesAndRemembersOnlyThatTarget() {
        SecondMovePlan plan = new SecondMovePlan();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(SecondMovePlan.Route.NORTH,
                plan.begin(first, SecondMovePlan.Route.NORTH));
        assertThrows(IllegalStateException.class,
                () -> plan.resolve(SecondMovePlan.Route.EAST));
        plan.lock();
        assertTrue(plan.resolve(SecondMovePlan.Route.EAST));
        assertEquals(SecondMovePlan.Route.EAST,
                plan.begin(first, SecondMovePlan.Route.NORTH));
        plan.lock();
        assertFalse(plan.resolve(SecondMovePlan.Route.EAST));
        assertEquals(SecondMovePlan.Route.WEST,
                plan.begin(second, SecondMovePlan.Route.WEST));
        plan.clear();
        assertEquals(SecondMovePlan.Route.SOUTH,
                plan.begin(first, SecondMovePlan.Route.CENTER));
    }

    @Test
    void directionPicksDominantAxisAndLeavesCenterForCloseRangeCue() {
        assertEquals(SecondMovePlan.Route.WEST, SecondMovePlan.Route.of(-8, 2));
        assertEquals(SecondMovePlan.Route.SOUTH, SecondMovePlan.Route.of(1, 6));
        assertEquals(SecondMovePlan.Route.CENTER, SecondMovePlan.Route.of(1, 2));
    }
}
