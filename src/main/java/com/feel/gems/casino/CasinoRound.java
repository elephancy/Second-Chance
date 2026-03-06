package com.feel.gems.casino;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Server-side decision state for single-player tables. No inventory changes happen here. */
public final class CasinoRound {
    public record Choice(String id, String label) {
    }

    private record Card(int rank, String suit) {
        String label() {
            String face = switch (rank) {
                case 1 -> "A";
                case 11 -> "J";
                case 12 -> "Q";
                case 13 -> "K";
                default -> Integer.toString(rank);
            };
            return face + suit;
        }

        String sprite() {
            String suitName = switch (suit) {
                case "♠" -> "spades";
                case "♥" -> "hearts";
                case "♦" -> "diamonds";
                default -> "clubs";
            };
            String face = switch (rank) {
                case 1 -> "a";
                case 11 -> "j";
                case 12 -> "q";
                case 13 -> "k";
                default -> rank < 10 ? "0" + rank : Integer.toString(rank);
            };
            return "card_" + suitName + "_" + face;
        }
    }

    private record Question(String prompt, String[] answers, int correct) {
    }

    private static final String[] SYMBOLS = {"Seven", "Diamond", "Bell", "Clover"};
    private static final int[] TRIPLE_MULTIPLIER = {7, 6, 4, 3};
    private static final int[] RED_NUMBERS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};
    private static final List<Question> QUESTIONS = List.of(
            new Question("How many blocks form a full beacon base?", new String[]{"164", "81", "120"}, 0),
            new Question("Which item opens an End portal frame?", new String[]{"Ender pearl", "Eye of ender", "Echo shard"}, 1),
            new Question("How many obsidian blocks make an ender chest?", new String[]{"6", "8", "10"}, 1),
            new Question("Which block resists a piston push?", new String[]{"Obsidian", "Glass", "Stone"}, 0),
            new Question("How many iron ingots make an iron block?", new String[]{"4", "8", "9"}, 2),
            new Question("How many ticks pass in one Minecraft day?", new String[]{"12,000", "24,000", "36,000"}, 1),
            new Question("What lets you breathe underwater?", new String[]{"Fire resistance", "Water breathing", "Slow falling"}, 1),
            new Question("Which mob drops blaze rods?", new String[]{"Blaze", "Ghast", "Magma cube"}, 0),
            // The short quotes and speaker attributions below are recorded in QUOTE_NOTES.md.
            new Question("Who asked, \"Do we have a tutorial for this group?\"", new String[]{"Fam", "Ragab", "Hamed"}, 0),
            new Question("Who said \"even when i lose i win\"?", new String[]{"Omar", "Feel", "Effat"}, 1),
            new Question("Who said \"pi pi pi...pi time\"?", new String[]{"Sameh", "Effat", "Ragab"}, 2),
            new Question("Who said \"I just gotta roll the wheel some time\"?", new String[]{"Effat", "Omar", "Feel"}, 0),
            new Question("Who said \"the game never existed\"?", new String[]{"Ragab", "Omar", "Yehia"}, 1),
            new Question("Who called it \"TeamSpeak time\"?", new String[]{"Mostafa", "Safwat", "Nour"}, 2),
            new Question("Who asked \"why not amogos\"?", new String[]{"Yehia", "Othman", "Feel"}, 0),
            new Question("Who said \"Ragebaited to touch grass\"?", new String[]{"Hamed", "Mostafa", "Omar"}, 1),
            new Question("Who said \"Nah trust its funny\"?", new String[]{"Effat", "Ragab", "Sameh"}, 2),
            new Question("Who said \"this game is peak\"?", new String[]{"Ragab", "Hamed", "Feel"}, 0),
            new Question("Who said \"the game isnt the reward\"?", new String[]{"Feel", "Yehia", "Nour"}, 1),
            new Question("Who said \"we should play liar's bar sometimes it'd be fun with you guys\"?", new String[]{"Effat", "Fam", "Feel"}, 2)
    );

    private final CasinoGame game;
    private final int stake;
    private final Random random;
    private final List<String> history = new ArrayList<>();
    private final List<Card> deck = new ArrayList<>();
    private final List<Card> playerCards = new ArrayList<>();
    private final List<Card> dealerCards = new ArrayList<>();
    private final int[] reels = new int[3];
    private Question question;
    private int nextCard;
    private int rpsWins;
    private int dealerWins;
    private int payout = -1;
    private String result = "Choose your move.";

    public CasinoRound(CasinoGame game, Random random) {
        if (game == CasinoGame.COOP) {
            throw new IllegalArgumentException("Co-op rounds need two players");
        }
        this.game = game;
        this.stake = game.stake();
        this.random = random;
        switch (game) {
            case CARDS -> startCards();
            case REELS -> {
                for (int i = 0; i < reels.length; i++) reels[i] = random.nextInt(SYMBOLS.length);
                result = "Choose a symbol to keep for the final spin.";
            }
            case TRIVIA -> {
                question = QUESTIONS.get(random.nextInt(QUESTIONS.size()));
                result = question.prompt();
            }
            case WHEEL -> result = "Pick a color or half before the wheel spins.";
            default -> {
            }
        }
    }

    public CasinoGame game() {
        return game;
    }

    public int stake() {
        return stake;
    }

    public boolean finished() {
        return payout >= 0;
    }

    public int payout() {
        if (!finished()) throw new IllegalStateException("Round is still active");
        return payout;
    }

    public String result() {
        return result;
    }

    public List<String> playerCardSprites() {
        return game == CasinoGame.CARDS ? playerCards.stream().map(Card::sprite).toList() : List.of();
    }

    public List<String> dealerCardSprites() {
        if (game != CasinoGame.CARDS) return List.of();
        if (finished()) return dealerCards.stream().map(Card::sprite).toList();
        return List.of(dealerCards.getFirst().sprite(), "card_back");
    }

    public List<String> board() {
        return switch (game) {
            case RPS -> {
                List<String> lines = new ArrayList<>();
                lines.add("You " + rpsWins + " : " + dealerWins + " Dealer");
                lines.addAll(history);
                yield List.copyOf(lines);
            }
            case CARDS -> List.of(
                    "Your hand: " + cards(playerCards) + " (" + total(playerCards) + ")",
                    "Dealer: " + (finished() ? cards(dealerCards) + " (" + total(dealerCards) + ")"
                            : dealerCards.getFirst().label() + " + hidden"));
            case WHEEL -> history.isEmpty() ? List.of("Numbers 1-36; zero is the house slot") : List.copyOf(history);
            case REELS -> List.of("[ " + SYMBOLS[reels[0]] + " ] [ " + SYMBOLS[reels[1]] + " ] [ " + SYMBOLS[reels[2]] + " ]");
            case TRIVIA -> List.of(question.prompt());
            case COOP -> List.of();
        };
    }

    public List<Choice> choices() {
        if (finished()) return List.of();
        return switch (game) {
            case RPS -> List.of(new Choice("rock", "Rock"), new Choice("paper", "Paper"),
                    new Choice("scissors", "Scissors"));
            case CARDS -> List.of(new Choice("hit", "Hit"), new Choice("stand", "Stand"));
            case WHEEL -> List.of(new Choice("red", "Red"), new Choice("black", "Black"),
                    new Choice("high", "High 19-36"), new Choice("low", "Low 1-18"));
            case REELS -> List.of(new Choice("hold_0", "Hold left"), new Choice("hold_1", "Hold middle"),
                    new Choice("hold_2", "Hold right"), new Choice("reroll", "Reroll all"));
            case TRIVIA -> List.of(new Choice("answer_0", question.answers()[0]),
                    new Choice("answer_1", question.answers()[1]), new Choice("answer_2", question.answers()[2]));
            case COOP -> List.of();
        };
    }

    public boolean play(String action) {
        if (finished() || choices().stream().noneMatch(choice -> choice.id().equals(action))) return false;
        switch (game) {
            case RPS -> playRps(action);
            case CARDS -> playCards(action);
            case WHEEL -> playWheel(action);
            case REELS -> playReels(action);
            case TRIVIA -> playTrivia(action);
            case COOP -> throw new IllegalStateException("Co-op handled separately");
        }
        return true;
    }

    private void playRps(String action) {
        String[] moves = {"rock", "paper", "scissors"};
        int player = List.of(moves).indexOf(action);
        int dealer = random.nextInt(3);
        int comparison = (player - dealer + 3) % 3;
        String outcome = comparison == 0 ? "tie" : comparison == 1 ? "win" : "loss";
        history.add(action + " vs " + moves[dealer] + ": " + outcome);
        if (history.size() > 3) history.removeFirst();
        if (comparison == 1) rpsWins++;
        if (comparison == 2) dealerWins++;
        if (rpsWins == 2) finish(stake * 2, "You won the match. " + (stake * 2) + " emeralds returned.");
        else if (dealerWins == 2) finish(0, "Dealer wins the tournament.");
        else result = "Choose your next throw.";
    }

    private void startCards() {
        for (String suit : List.of("♠", "♥", "♦", "♣")) {
            for (int rank = 1; rank <= 13; rank++) deck.add(new Card(rank, suit));
        }
        Collections.shuffle(deck, random);
        playerCards.add(draw());
        dealerCards.add(draw());
        playerCards.add(draw());
        dealerCards.add(draw());
        result = "Hit for another card or stand on your total.";
        if (total(playerCards) == 21) {
            if (total(dealerCards) == 21) finish(stake, "Both have a natural 21. Stake returned.");
            else finish(stake * 2, "Natural 21. You win.");
        } else if (total(dealerCards) == 21) {
            finish(0, "Dealer has a natural 21.");
        }
    }

    private void playCards(String action) {
        if (action.equals("hit")) {
            playerCards.add(draw());
            if (total(playerCards) > 21) finish(0, "Bust. Dealer wins.");
            else if (total(playerCards) == 21) stand();
            else result = "Your hand is " + total(playerCards) + ". Hit or stand?";
        } else {
            stand();
        }
    }

    private void stand() {
        while (total(dealerCards) < 17) dealerCards.add(draw());
        int player = total(playerCards);
        int dealer = total(dealerCards);
        if (dealer > 21 || player > dealer) finish(stake * 2, "You win: " + player + " to " + dealer + ".");
        else if (player == dealer) finish(stake, "Push: both have " + player + ". Stake returned.");
        else finish(0, "Dealer wins: " + dealer + " to " + player + ".");
    }

    private Card draw() {
        return deck.get(nextCard++);
    }

    private static int total(List<Card> cards) {
        int total = 0;
        int aces = 0;
        for (Card card : cards) {
            if (card.rank() == 1) {
                total += 11;
                aces++;
            } else {
                total += Math.min(card.rank(), 10);
            }
        }
        while (total > 21 && aces-- > 0) total -= 10;
        return total;
    }

    private static String cards(List<Card> cards) {
        return cards.stream().map(Card::label).reduce((a, b) -> a + " " + b).orElse("");
    }

    private void playWheel(String action) {
        int number = random.nextInt(37);
        boolean red = false;
        for (int n : RED_NUMBERS) if (n == number) red = true;
        String color = number == 0 ? "green" : red ? "red" : "black";
        boolean won = number != 0 && switch (action) {
            case "red" -> red;
            case "black" -> !red;
            case "high" -> number >= 19;
            case "low" -> number <= 18;
            default -> false;
        };
        history.add("Wheel: " + number + " " + color + ". You chose " + action + ".");
        finish(won ? stake * 2 : 0, won ? "Your call wins." : "The wheel wins this round.");
    }

    private void playReels(String action) {
        int held = action.equals("reroll") ? -1 : Integer.parseInt(action.substring("hold_".length()));
        for (int i = 0; i < reels.length; i++) {
            if (i != held) reels[i] = random.nextInt(SYMBOLS.length);
        }
        if (reels[0] == reels[1] && reels[1] == reels[2]) {
            int reward = stake * TRIPLE_MULTIPLIER[reels[0]];
            finish(reward, "Triple " + SYMBOLS[reels[0]] + "! " + reward + " emeralds returned.");
        } else if (reels[0] == reels[1] || reels[0] == reels[2] || reels[1] == reels[2]) {
            finish(stake, "A pair. Your stake comes back.");
        } else {
            finish(0, "No match this spin.");
        }
    }

    private void playTrivia(String action) {
        int answer = Integer.parseInt(action.substring("answer_".length()));
        boolean correct = answer == question.correct();
        finish(correct ? stake + 1 : 0, correct ? "Correct. One emerald profit." : "Not quite. The right answer was "
                + question.answers()[question.correct()] + ".");
    }

    private void finish(int reward, String text) {
        payout = reward;
        result = text;
    }
}
