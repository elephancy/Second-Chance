package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FamForumRuntimeTest {
    @Test
    void sharedCluesNeedBothRoomsAndIgnoreRepeatReads() {
        FamForumPersistentState state = new FamForumPersistentState();
        assertFalse(state.ready());
        assertTrue(state.record(FamForumPersistentState.STORY));
        assertFalse(state.record(FamForumPersistentState.STORY));
        assertFalse(state.ready());
        assertTrue(state.record(FamForumPersistentState.SCHOOL));
        assertTrue(state.ready());
    }

    @Test
    void welcomeHintUsesVisitBeforeClearThenSuggestsTheNextRoute() {
        assertTrue(FamForumRuntime.hint(CampaignProgress.PlayerProgress.EMPTY)
                .contains("Rumor Atlas"));
        CampaignProgress.PlayerProgress visited = new CampaignProgress.PlayerProgress(
                LandmarkKey.FRAMEFALL.bit(), 0, 0, 0);
        assertTrue(FamForumRuntime.hint(visited).contains("Framefall Gallery"));
        CampaignProgress.PlayerProgress cleared = new CampaignProgress.PlayerProgress(
                LandmarkKey.FRAMEFALL.bit(), LandmarkKey.FRAMEFALL.bit(), 0, 0);
        assertTrue(FamForumRuntime.hint(cleared).contains("1 of seven"));
        CampaignProgress.PlayerProgress done = new CampaignProgress.PlayerProgress(
                LandmarkKey.ALL_MASK, LandmarkKey.ALL_MASK, 0, 0);
        assertTrue(FamForumRuntime.hint(done).contains("all seven"));
    }
}
