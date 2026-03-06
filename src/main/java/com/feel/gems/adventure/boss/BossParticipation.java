package com.feel.gems.adventure.boss;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A fight's contributors remain eligible if they die or leave before its final hit. */
final class BossParticipation {
    private static final int PRESENCE_REQUIRED_TICKS = 60;

    private BossParticipation() { }

    static Set<UUID> eligibleIds(Map<UUID, Integer> presenceTicks, Set<UUID> contributors) {
        Set<UUID> eligible = new HashSet<>(contributors);
        presenceTicks.forEach((id, ticks) -> {
            if (ticks >= PRESENCE_REQUIRED_TICKS) eligible.add(id);
        });
        return Set.copyOf(eligible);
    }
}
