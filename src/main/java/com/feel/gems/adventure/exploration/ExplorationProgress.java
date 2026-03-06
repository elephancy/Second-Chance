package com.feel.gems.adventure.exploration;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Server-thread model for optional discoveries; activity IDs are stable save keys. */
public final class ExplorationProgress {
    private final Set<String> discovered = new HashSet<>();
    private final Map<UUID, Set<String>> earned = new HashMap<>();
    private final Map<UUID, Journey> journeys = new HashMap<>();

    public record Journey(long trailOrigin, int trailStep, long cipherPlaque, int cipherStep) {
        public static final long NONE = Long.MIN_VALUE;
        public static final Journey EMPTY = new Journey(NONE, 0, NONE, 0);

        public Journey {
            trailStep = Math.clamp(trailStep, 0, 1);
            cipherStep = Math.clamp(cipherStep, 0, 3);
        }
    }

    public record Result(boolean firstWorldDiscovery, Set<UUID> firstPlayerClears) { }

    public record Snapshot(Set<String> discovered, Map<UUID, Set<String>> earned,
                           Map<UUID, Journey> journeys) { }

    public static String activityId(String kind, int x, int z) {
        if (!kind.equals("trail") && !kind.equals("cipher")
                && !kind.equals("vista") && !kind.equals("maze")
                && !kind.equals("estate") && !kind.equals("estate_n")
                && !kind.equals("estate_e") && !kind.equals("estate_s")
                && !kind.equals("estate_w") && !kind.equals("estate_mastery")) {
            throw new IllegalArgumentException("Unknown exploration activity: " + kind);
        }
        return kind + ":" + x + ":" + z;
    }

    public static String estateRoadId(char wing, int x, int z) {
        return activityId(switch (wing) {
            case 'N' -> "estate_n";
            case 'E' -> "estate_e";
            case 'S' -> "estate_s";
            case 'W' -> "estate_w";
            default -> throw new IllegalArgumentException("Unknown estate wing: " + wing);
        }, x, z);
    }

    public int estateRoadCount(UUID player, int x, int z) {
        int count = 0;
        for (char wing : new char[]{'N', 'E', 'S', 'W'}) {
            if (earned(player, estateRoadId(wing, x, z))) count++;
        }
        return count;
    }

    public boolean hasEstateMastery(UUID player) {
        return earned.getOrDefault(player, Set.of()).stream()
                .anyMatch(id -> id.startsWith("estate_mastery:"));
    }

    public static void validateId(String id) {
        Objects.requireNonNull(id, "activityId");
        if (id.length() > 80 || !id.matches(
                "(?:trail|cipher|vista|maze|estate|estate_[nesw]|estate_mastery):-?[0-9]+:-?[0-9]+")) {
            throw new IllegalArgumentException("Invalid exploration activity ID: " + id);
        }
    }

    public Result complete(String id, Collection<UUID> participants) {
        validateId(id);
        Objects.requireNonNull(participants, "participants");
        if (participants.isEmpty()) throw new IllegalArgumentException("No participants");
        Set<UUID> unique = Set.copyOf(participants);
        boolean firstWorld = discovered.add(id);
        Set<UUID> firstPlayers = new HashSet<>();
        for (UUID player : unique) {
            if (earned.computeIfAbsent(player, ignored -> new HashSet<>()).add(id)) {
                firstPlayers.add(player);
            }
        }
        return new Result(firstWorld, Set.copyOf(firstPlayers));
    }

    public boolean reconcileReceipt(UUID player, String id) {
        validateId(id);
        boolean firstWorld = discovered.add(id);
        boolean firstPlayer = earned.computeIfAbsent(player, ignored -> new HashSet<>()).add(id);
        return firstWorld || firstPlayer;
    }

    public boolean discovered(String id) {
        return discovered.contains(id);
    }

    public boolean earned(UUID player, String id) {
        return earned.getOrDefault(player, Set.of()).contains(id);
    }

    public Set<String> pending(UUID player) {
        return Set.copyOf(earned.getOrDefault(player, Set.of()));
    }

    public Journey journey(UUID player) {
        return journeys.getOrDefault(player, Journey.EMPTY);
    }

    public boolean startTrail(UUID player, long origin) {
        Journey old = journey(player);
        if (old.trailOrigin() == origin) return false;
        journeys.put(player, new Journey(origin, 0, old.cipherPlaque(), old.cipherStep()));
        return true;
    }

    public boolean markTrailTurn(UUID player) {
        Journey old = journey(player);
        if (old.trailOrigin() == Journey.NONE || old.trailStep() == 1) return false;
        journeys.put(player, new Journey(old.trailOrigin(), 1, old.cipherPlaque(), old.cipherStep()));
        return true;
    }

    public boolean clearTrail(UUID player) {
        Journey old = journey(player);
        if (old.trailOrigin() == Journey.NONE) return false;
        journeys.put(player, new Journey(Journey.NONE, 0, old.cipherPlaque(), old.cipherStep()));
        return true;
    }

    public boolean startCipher(UUID player, long plaque) {
        Journey old = journey(player);
        if (old.cipherPlaque() == plaque && old.cipherStep() == 0) return false;
        journeys.put(player, new Journey(old.trailOrigin(), old.trailStep(), plaque, 0));
        return true;
    }

    /** Returns the new step, or -1 when the sequence was not started. */
    public int pressCipher(UUID player, int symbol) {
        Journey old = journey(player);
        if (old.cipherPlaque() == Journey.NONE) return -1;
        int[] order = {0, 2, 1}; // Sun, Star, Moon: printed on each plaque.
        int step = old.cipherStep();
        int next = step < 3 && symbol == order[step] ? step + 1 : 0;
        journeys.put(player, new Journey(old.trailOrigin(), old.trailStep(), old.cipherPlaque(), next));
        return next;
    }

    public boolean clearCipher(UUID player) {
        Journey old = journey(player);
        if (old.cipherPlaque() == Journey.NONE) return false;
        journeys.put(player, new Journey(old.trailOrigin(), old.trailStep(), Journey.NONE, 0));
        return true;
    }

    public Snapshot snapshot() {
        Map<UUID, Set<String>> copy = new HashMap<>();
        earned.forEach((id, keys) -> copy.put(id, Set.copyOf(keys)));
        return new Snapshot(Set.copyOf(discovered), Map.copyOf(copy), Map.copyOf(journeys));
    }

    public static ExplorationProgress fromSnapshot(Snapshot snapshot) {
        ExplorationProgress progress = new ExplorationProgress();
        snapshot.discovered().stream().filter(ExplorationProgress::valid).forEach(progress.discovered::add);
        snapshot.earned().forEach((player, keys) -> {
            Set<String> validKeys = new HashSet<>();
            keys.stream().filter(ExplorationProgress::valid).forEach(validKeys::add);
            if (!validKeys.isEmpty()) progress.earned.put(player, validKeys);
        });
        snapshot.journeys().forEach((player, journey) -> {
            if (!journey.equals(Journey.EMPTY)) progress.journeys.put(player, journey);
        });
        return progress;
    }

    private static boolean valid(String id) {
        try {
            validateId(id);
            return true;
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return false;
        }
    }
}
