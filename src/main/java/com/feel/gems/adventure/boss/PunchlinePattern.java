package com.feel.gems.adventure.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A bounded, per-player memory of repeated attacks in one encounter. */
public final class PunchlinePattern {
    public enum Attack { CLOSE, PROJECTILE }
    public enum Counter { NONE, CLOSE, PROJECTILE }

    private record Streak(Attack attack, int count) { }

    private final Map<UUID, Streak> streaks = new HashMap<>();

    public void record(UUID player, Attack attack) {
        Streak previous = streaks.get(player);
        int count = previous != null && previous.attack() == attack
                ? Math.min(3, previous.count() + 1) : 1;
        streaks.put(player, new Streak(attack, count));
    }

    public Counter counter(UUID player) {
        Streak streak = streaks.get(player);
        if (streak == null || streak.count() < 3) return Counter.NONE;
        return streak.attack() == Attack.CLOSE ? Counter.CLOSE : Counter.PROJECTILE;
    }

    public void clear() {
        streaks.clear();
    }
}
