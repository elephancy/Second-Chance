package com.feel.gems.assassin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;


public final class AssassinStateTest {
    @Test
    void pointsUsesThreePerFinalKill() {
        assertEquals(0, AssassinState.points(0, 0));
        assertEquals(1, AssassinState.points(1, 0));
        assertEquals(3, AssassinState.points(0, 1));
        assertEquals(4, AssassinState.points(1, 1));
        assertEquals(8, AssassinState.points(2, 2));
    }
}

