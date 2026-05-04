package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Collectors;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BossServicePlanTest {
    @Test
    void rehearsalLocksBeforeTheHarmlessStrikeResult() {
        assertEquals(PracticeSpotlightPlan.Phase.TRACKING,
                PracticeSpotlightPlan.phase(PracticeSpotlightPlan.LOCK_TICK - 1));
        assertEquals(PracticeSpotlightPlan.Phase.LOCKED,
                PracticeSpotlightPlan.phase(PracticeSpotlightPlan.LOCK_TICK));
        assertEquals(PracticeSpotlightPlan.Phase.RESULT,
                PracticeSpotlightPlan.phase(PracticeSpotlightPlan.STRIKE_TICK));
        assertEquals(PracticeSpotlightPlan.Phase.FINISHED,
                PracticeSpotlightPlan.phase(PracticeSpotlightPlan.END_TICK));
        Vec3 mark = new Vec3(10, 64, 10);
        assertFalse(PracticeSpotlightPlan.escaped(new Vec3(12.2, 64, 10), mark));
        assertTrue(PracticeSpotlightPlan.escaped(new Vec3(12.3, 64, 10), mark));
        assertTrue(PracticeSpotlightPlan.escaped(new Vec3(10, 67, 10), mark));
    }

    @Test
    void everyExpeditionHasItsOwnSpecificSideRoute() {
        var clues = Arrays.stream(LandmarkKey.values())
                .map(RouteDeskClues::alternate).collect(Collectors.toList());
        assertEquals(LandmarkKey.values().length, clues.stream().distinct().count());
        assertTrue(clues.stream().allMatch(clue -> clue.length() > 60 && clue.length() < 240));
        assertTrue(RouteDeskClues.alternate(LandmarkKey.BRANCH).contains("escape tower"));
        assertTrue(RouteDeskClues.alternate(LandmarkKey.UNWRITTEN).contains("annex"));
    }
}
