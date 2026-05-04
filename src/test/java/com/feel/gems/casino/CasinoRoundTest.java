package com.feel.gems.casino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CasinoRoundTest {
    @Test
    void everyTwentyOneSpriteHasAnExactlyNamedMinecraftResource() throws IOException {
        Path cardDirectory = Path.of("src/main/resources/assets/gems/textures/gui/casino/cards");
        Set<String> expected = new HashSet<>(Set.of("card_back.png"));
        for (String suit : Set.of("clubs", "diamonds", "hearts", "spades")) {
            for (int rank = 2; rank <= 10; rank++) {
                expected.add("card_" + suit + "_" + (rank < 10 ? "0" + rank : rank) + ".png");
            }
            for (String face : Set.of("a", "j", "q", "k")) {
                expected.add("card_" + suit + "_" + face + ".png");
            }
        }
        Set<String> actual = new HashSet<>();
        try (var files = Files.list(cardDirectory)) {
            files.forEach(file -> actual.add(file.getFileName().toString()));
        }
        assertEquals(expected, actual);

        for (int seed = 0; seed < 100; seed++) {
            CasinoRound round = new CasinoRound(CasinoGame.CARDS, new Random(seed));
            for (String sprite : round.playerCardSprites()) {
                assertTrue(actual.contains(sprite + ".png"), sprite);
            }
            for (String sprite : round.dealerCardSprites()) {
                assertTrue(actual.contains(sprite + ".png"), sprite);
            }
        }
    }

    @Test
    void naturalTwentyOnePaysOnlyAfterBothOpeningHandsAreCompared() {
        CasinoRound bothNatural = new CasinoRound(CasinoGame.CARDS, new Random(315));
        assertTrue(bothNatural.finished());
        assertEquals(4, bothNatural.payout());
        assertTrue(bothNatural.result().contains("Both"));

        CasinoRound dealerNatural = new CasinoRound(CasinoGame.CARDS, new Random(8));
        assertTrue(dealerNatural.finished());
        assertEquals(0, dealerNatural.payout());

        CasinoRound playerNatural = new CasinoRound(CasinoGame.CARDS, new Random(6));
        assertTrue(playerNatural.finished());
        assertEquals(8, playerNatural.payout());
    }

    @Test
    void rpsNeedsTwoWinsAndCannotPayAgainAfterResolution() {
        Random alwaysScissors = new Random() {
            @Override
            public int nextInt(int bound) {
                return 2;
            }
        };
        CasinoRound round = new CasinoRound(CasinoGame.RPS, alwaysScissors);
        assertTrue(round.play("rock"));
        assertFalse(round.finished());
        assertTrue(round.play("rock"));
        assertEquals(6, round.payout());
        assertFalse(round.play("rock"));
        assertEquals(6, round.payout());
    }

    @Test
    void zeroOnWheelLosesRegardlessOfChosenBet() {
        Random alwaysZero = new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
        CasinoRound round = new CasinoRound(CasinoGame.WHEEL, alwaysZero);
        assertTrue(round.play("red"));
        assertEquals(0, round.payout());
        assertFalse(round.play("black"));
    }

    @Test
    void triviaAndReelsRequireAValidVisibleDecision() {
        CasinoRound trivia = new CasinoRound(CasinoGame.TRIVIA, new Random(0) {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        });
        assertFalse(trivia.play("answer_9"));
        assertTrue(trivia.play("answer_0"));
        assertEquals(3, trivia.payout());

        CasinoRound reels = new CasinoRound(CasinoGame.REELS, new Random(7));
        assertFalse(reels.play("collect"));
        assertFalse(reels.finished());
        assertTrue(reels.play("hold_1"));
        assertTrue(reels.finished());
        assertFalse(reels.play("reroll"));
    }

    @Test
    void optimalReelChoicesCannotProduceAnUnlimitedEmeraldFarm() {
        long payoutNumerator = 0;
        for (int left = 0; left < 4; left++) {
            for (int middle = 0; middle < 4; middle++) {
                for (int right = 0; right < 4; right++) {
                    int best = 0;
                    for (String choice : new String[]{"hold_0", "hold_1", "hold_2", "reroll"}) {
                        best = Math.max(best, expectedReelPayoutNumerator(left, middle, right, choice));
                    }
                    payoutNumerator += best;
                }
            }
        }
        // 64 visible starts, with 64 equally weighted final outcomes per start.
        assertTrue(payoutNumerator < CasinoGame.REELS.stake() * 64L * 64L,
                "Even the best visible choice must have a house edge over repeated rounds");
    }

    @Test
    void reelPairReturnsPaidStakeButQualifiesForFreeTournament() {
        int[] draws = {0, 1, 2, 0, 3};
        CasinoRound round = new CasinoRound(CasinoGame.REELS, new Random() {
            private int next;

            @Override
            public int nextInt(int bound) {
                return draws[next++];
            }
        });

        assertFalse(TournamentProgress.qualifies(round));
        assertTrue(round.play("hold_0"));
        assertEquals(round.stake(), round.payout());
        assertTrue(TournamentProgress.qualifies(round));
    }

    private static int expectedReelPayoutNumerator(int left, int middle, int right, String choice) {
        int total = 0;
        boolean reroll = choice.equals("reroll");
        for (int first = 0; first < 4; first++) {
            for (int second = 0; second < 4; second++) {
                for (int third = 0; third < (reroll ? 4 : 1); third++) {
                    int[] draws = {left, middle, right, first, second, third};
                    CasinoRound round = new CasinoRound(CasinoGame.REELS, new Random() {
                        private int next;

                        @Override
                        public int nextInt(int bound) {
                            return draws[next++];
                        }
                    });
                    assertTrue(round.play(choice));
                    total += round.payout() * (reroll ? 1 : 4);
                }
            }
        }
        return total;
    }
}
