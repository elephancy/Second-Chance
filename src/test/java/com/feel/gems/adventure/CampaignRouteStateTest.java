package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.feel.gems.adventure.CampaignRouteState.Path;
import com.feel.gems.adventure.CampaignRouteState.Resolution;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignRouteStateTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void soloChoiceNeedsVoteAndThenLocks() {
        CampaignRouteState state = new CampaignRouteState();
        assertEquals(Resolution.NEED_VOTES, state.confirm(List.of(FIRST), FIRST));
        assertTrue(state.vote(FIRST, Path.REMEMBER));
        assertEquals(Resolution.CHOSEN, state.confirm(List.of(FIRST), FIRST));
        assertEquals(Path.REMEMBER, state.chosen());
        assertFalse(state.vote(FIRST, Path.RELEASE));
        assertEquals(Resolution.ALREADY_CHOSEN, state.confirm(List.of(FIRST), FIRST));
    }

    @Test
    void nearbyPlayersMustVoteAndTieDoesNotLockWorld() {
        CampaignRouteState state = new CampaignRouteState();
        state.vote(FIRST, Path.REPAIR);
        assertEquals(Resolution.NEED_VOTES, state.confirm(List.of(FIRST, SECOND), FIRST));
        state.vote(SECOND, Path.RELEASE);
        assertEquals(Resolution.NO_MAJORITY, state.confirm(List.of(FIRST, SECOND), FIRST));
        assertNull(state.chosen());
        state.vote(SECOND, Path.REPAIR);
        assertEquals(Resolution.CHOSEN, state.confirm(List.of(FIRST, SECOND), FIRST));
        assertEquals(Path.REPAIR, state.chosen());
    }

    @Test
    void proposerCannotConfirmAnotherPlayersMajority() {
        CampaignRouteState state = new CampaignRouteState();
        state.vote(FIRST, Path.REMEMBER);
        state.vote(SECOND, Path.RELEASE);
        state.vote(THIRD, Path.RELEASE);
        assertEquals(Resolution.PROPOSER_MISMATCH,
                state.confirm(List.of(FIRST, SECOND, THIRD), FIRST));
        assertNull(state.chosen());
        assertEquals(Resolution.CHOSEN,
                state.confirm(List.of(FIRST, SECOND, THIRD), SECOND));
        assertEquals(Path.RELEASE, state.chosen());
    }

    @Test
    void votesFromPlayersAwayFromCouncilDoNotDecideNearbyMajority() {
        CampaignRouteState state = new CampaignRouteState();
        state.vote(FIRST, Path.REMEMBER);
        state.vote(SECOND, Path.REMEMBER);
        state.vote(THIRD, Path.RELEASE);
        assertEquals(Resolution.CHOSEN, state.confirm(List.of(FIRST, SECOND), FIRST));
        assertEquals(Path.REMEMBER, state.chosen());
    }

    @Test
    void rememberPrintsRequireChosenRouteAndTwoDifferentNewPrints() {
        CampaignRouteState state = new CampaignRouteState();
        assertFalse(state.recordRememberPrint(0));
        state.vote(FIRST, Path.REMEMBER);
        assertEquals(Resolution.CHOSEN, state.confirm(List.of(FIRST), FIRST));
        assertFalse(state.bindRememberInkbound());
        assertTrue(state.recordRememberPrint(0));
        assertFalse(state.recordRememberPrint(0));
        assertFalse(state.recordRememberPrint(3));
        assertFalse(state.bindRememberInkbound());
        assertTrue(state.recordRememberPrint(2));
        assertEquals(2, state.rememberPrintCount());
        assertFalse(state.recordRememberPrint(1));
        assertTrue(state.bindRememberInkbound());
        assertTrue(state.rememberInkboundBound());
        assertFalse(state.recordRememberPrint(1));
        assertFalse(state.bindRememberInkbound());
    }

    @Test
    void otherRoutesCannotCompleteRememberChapter() {
        CampaignRouteState state = new CampaignRouteState();
        state.vote(FIRST, Path.REPAIR);
        assertEquals(Resolution.CHOSEN, state.confirm(List.of(FIRST), FIRST));
        assertFalse(state.recordRememberPrint(0));
        assertFalse(state.recordRememberPrint(1));
        assertFalse(state.bindRememberInkbound());
        assertEquals(0, state.rememberPrintCount());
    }

    @Test
    void rememberArchiveNeedsBothExpeditionWitnessesAfterBinding() {
        CampaignRouteState state = new CampaignRouteState();
        state.vote(FIRST, Path.REMEMBER);
        assertEquals(Resolution.CHOSEN, state.confirm(List.of(FIRST), FIRST));
        assertFalse(state.recordRememberWitness(1));
        state.recordRememberPrint(0);
        state.recordRememberPrint(1);
        assertTrue(state.bindRememberInkbound());
        assertFalse(state.compareRememberArchive());
        assertFalse(state.recordRememberWitness(3));
        assertTrue(state.recordRememberWitness(1));
        assertFalse(state.recordRememberWitness(1));
        assertFalse(state.compareRememberArchive());
        assertTrue(state.recordRememberWitness(2));
        assertEquals(2, state.rememberWitnessCount());
        assertTrue(state.compareRememberArchive());
        assertTrue(state.rememberArchiveCompared());
        assertFalse(state.recordRememberWitness(2));
        assertFalse(state.compareRememberArchive());
    }

    @Test
    void rememberTaleReversesGardenPathAndCanRetryWithoutLosingEarlierChapters() {
        CampaignRouteState state = new CampaignRouteState();
        state.vote(FIRST, Path.REMEMBER);
        assertEquals(Resolution.CHOSEN, state.confirm(List.of(FIRST), FIRST));
        assertFalse(state.startRememberTale());
        state.recordRememberPrint(0);
        state.recordRememberPrint(1);
        state.bindRememberInkbound();
        state.recordRememberWitness(1);
        state.recordRememberWitness(2);
        state.compareRememberArchive();
        assertTrue(state.startRememberTale());
        assertEquals(1, state.rememberTaleStage());
        assertEquals(2, state.markRememberTale(YehiaStoryState.TREE));
        assertEquals(1, state.markRememberTale(YehiaStoryState.MUSHROOM));
        assertEquals(2, state.markRememberTale(YehiaStoryState.TREE));
        assertEquals(3, state.markRememberTale(YehiaStoryState.LANTERN));
        assertEquals(4, state.markRememberTale(YehiaStoryState.MUSHROOM));
        assertFalse(state.startRememberTale());
        assertTrue(state.finishRememberTale());
        assertEquals(5, state.rememberTaleStage());
        assertFalse(state.finishRememberTale());
        assertTrue(state.rememberArchiveCompared());
    }
}
