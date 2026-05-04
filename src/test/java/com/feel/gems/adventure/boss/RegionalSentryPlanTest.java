package com.feel.gems.adventure.boss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class RegionalSentryPlanTest {
    @Test
    void linesEndAtAuthoredBounds() {
        assertTrue(RegionalSentryPlan.axis(5, 0, 0, 0, true, 5, 1));
        assertFalse(RegionalSentryPlan.axis(5.1, 0, 0, 0, true, 5, 1));
        assertFalse(RegionalSentryPlan.axis(0, 1.1, 0, 0, true, 5, 1));
        assertTrue(RegionalSentryPlan.ray(4, 0, 0, 0, 1, 0, 1, 8, 1));
        assertFalse(RegionalSentryPlan.ray(-1, 0, 0, 0, 1, 0, 1, 8, 1));
        assertFalse(RegionalSentryPlan.ray(8.1, 0, 0, 0, 1, 0, 1, 8, 1));
    }

    @Test
    void spotlightAndCenterHaveFiniteRadius() {
        assertTrue(RegionalSentryPlan.circle(2, 0, 0, 0, 2));
        assertFalse(RegionalSentryPlan.circle(2.01, 0, 0, 0, 2));
    }
}
