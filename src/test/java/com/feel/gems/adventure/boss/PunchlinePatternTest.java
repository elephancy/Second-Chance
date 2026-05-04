package com.feel.gems.adventure.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PunchlinePatternTest {
    @Test
    void counterRequiresThreeRepeatedAttacksFromTheSamePlayer() {
        PunchlinePattern pattern = new PunchlinePattern();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        pattern.record(first, PunchlinePattern.Attack.CLOSE);
        pattern.record(first, PunchlinePattern.Attack.CLOSE);
        pattern.record(second, PunchlinePattern.Attack.CLOSE);
        assertEquals(PunchlinePattern.Counter.NONE, pattern.counter(first));
        assertEquals(PunchlinePattern.Counter.NONE, pattern.counter(second));
        pattern.record(first, PunchlinePattern.Attack.PROJECTILE);
        assertEquals(PunchlinePattern.Counter.NONE, pattern.counter(first));
        pattern.record(first, PunchlinePattern.Attack.PROJECTILE);
        pattern.record(first, PunchlinePattern.Attack.PROJECTILE);
        assertEquals(PunchlinePattern.Counter.PROJECTILE, pattern.counter(first));
        pattern.record(first, PunchlinePattern.Attack.CLOSE);
        assertEquals(PunchlinePattern.Counter.NONE, pattern.counter(first));
        pattern.clear();
        assertEquals(PunchlinePattern.Counter.NONE, pattern.counter(first));
    }
}
