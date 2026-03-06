package com.feel.gems.casino;

import java.util.Arrays;
import java.util.Optional;

/** The six tables on the casino floor. Rules and payout are shown before staking. */
public enum CasinoGame {
    RPS("rps", "Rock Paper Scissors", 3,
            "First to win two throws takes 6 emeralds back. Ties replay. Lose and your 3-emerald stake is gone."),
    CARDS("cards", "Twenty-One", 4,
            "Get closer to 21 than the dealer without going over. Hit or stand; the dealer draws to 17. Win: 8 back. Tie: 4 back."),
    WHEEL("wheel", "Color Wheel", 2,
            "Call red, black, low (1-18), or high (19-36). Zero beats every call. A win returns 4 emeralds."),
    REELS("reels", "Hold and Spin", 3,
            "Keep one symbol or spin all three again. A pair returns your 3-emerald stake. Three matching symbols return 9-21."),
    TRIVIA("trivia", "Trivia", 2,
            "Answer one Minecraft or group question. Your first correct answer each Minecraft day returns 3 emeralds; later correct answers return 2."),
    COOP("coop", "Signal Table", 2,
            "Each player puts in 2 emeralds. Tell your partner the color you see, then choose theirs. If both guess right, each gets 3 emeralds on the first win of the day, or 2 afterward.");

    private final String id;
    private final String title;
    private final int stake;
    private final String rules;

    CasinoGame(String id, String title, int stake, String rules) {
        this.id = id;
        this.title = title;
        this.stake = stake;
        this.rules = rules;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public int stake() {
        return stake;
    }

    public String rules() {
        return rules;
    }

    public static Optional<CasinoGame> byId(String id) {
        return Arrays.stream(values()).filter(game -> game.id.equals(id)).findFirst();
    }
}
