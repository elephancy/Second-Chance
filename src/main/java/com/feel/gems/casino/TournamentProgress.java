package com.feel.gems.casino;

import java.util.ArrayList;
import java.util.List;

/** Pure daily rules. A world can score each featured table only once per day. */
final class TournamentProgress {
    private static final CasinoGame[] SOLO = {
            CasinoGame.RPS, CasinoGame.CARDS, CasinoGame.WHEEL,
            CasinoGame.REELS, CasinoGame.TRIVIA
    };
    static final int TARGET = 3;

    private TournamentProgress() { }

    static List<CasinoGame> featured(long day) {
        // Adjacent Minecraft days have different triples; all five solo games
        // appear over a five-day cycle. Signal Table is always an optional sixth.
        int start = Math.floorMod(day, SOLO.length);
        List<CasinoGame> games = new ArrayList<>(4);
        for (int offset = 0; offset < TARGET; offset++) {
            games.add(SOLO[(start + offset) % SOLO.length]);
        }
        games.add(CasinoGame.COOP);
        return List.copyOf(games);
    }

    static boolean featured(long day, CasinoGame game) {
        return featured(day).contains(game);
    }

    static boolean qualifies(CasinoRound round) {
        if (!round.finished()) return false;
        // A free Reel challenge rewards any match. The paid table still returns
        // only the stake for a pair, with its existing odds and payout.
        return round.game() == CasinoGame.REELS
                ? round.payout() >= round.stake()
                : round.payout() > round.stake();
    }

    static int bit(CasinoGame game) {
        return 1 << game.ordinal();
    }

    static boolean completed(int mask) {
        return Integer.bitCount(mask & ((1 << CasinoGame.values().length) - 1)) >= TARGET;
    }

    static int record(int mask, long day, CasinoGame game, boolean won) {
        if (!won || !featured(day, game)) return mask;
        return mask | bit(game);
    }

    static String summary(long day, int mask) {
        StringBuilder text = new StringBuilder("Day ").append(day + 1).append(": ")
                .append(Math.min(TARGET, Integer.bitCount(mask))).append("/").append(TARGET)
                .append(" featured tables won. ");
        for (CasinoGame game : featured(day)) {
            text.append(game.title()).append((mask & bit(game)) != 0 ? " [won]" : " [open]")
                    .append("; ");
        }
        return text.toString().trim();
    }

}
