package com.feel.gems.casino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TournamentStateTest {
    @Test
    void featuredSoloTablesRotateAcrossAllFiveWithOptionalSignalTable() {
        Set<CasinoGame> seen = new HashSet<>();
        for (long day = 0; day < 5; day++) {
            var games = TournamentProgress.featured(day);
            assertEquals(4, games.size());
            assertEquals(CasinoGame.COOP, games.getLast());
            seen.addAll(games);
        }
        assertEquals(Set.of(CasinoGame.values()), seen);
        assertFalse(TournamentProgress.featured(0, CasinoGame.REELS));
    }

    @Test
    void threeDistinctServerSettledWinsCompleteTheWorldDayWithoutWagers() {
        TournamentState state = new TournamentState();
        long day = 4;
        var games = TournamentProgress.featured(day);
        assertFalse(state.record(day, games.get(0), false));
        assertTrue(state.record(day, games.get(0), true));
        assertFalse(state.record(day, games.get(0), true));
        assertTrue(state.record(day, games.get(1), true));
        assertFalse(state.completed(day));
        assertTrue(state.record(day, CasinoGame.COOP, true));
        assertTrue(state.completed(day));
        assertFalse(state.record(day, games.get(2), false));
    }

    @Test
    void worldScorePersistsAndResetsOnTheNextDay() {
        TournamentState state = new TournamentState();
        long day = 3;
        var games = TournamentProgress.featured(day);
        for (int i = 0; i < 3; i++) assertTrue(state.record(day, games.get(i), true));
        var encoded = TournamentState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        TournamentState restored = TournamentState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(restored.completed(day));
        restored.onDay(day - 1);
        assertTrue(restored.completed(day));
        assertFalse(restored.completed(day + 1));
        assertEquals(0, restored.wonTables(day + 1));
    }
}
