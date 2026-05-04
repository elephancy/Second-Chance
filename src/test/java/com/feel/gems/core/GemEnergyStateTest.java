package com.feel.gems.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;


public final class GemEnergyStateTest {
    @Test
    void unlockedAbilityCount_energy0_1_none() {
        assertEquals(0, new GemEnergyState(0).unlockedAbilityCount(7));
        assertEquals(0, new GemEnergyState(1).unlockedAbilityCount(7));
    }

    @Test
    void unlockedAbilityCount_energy2_4_scalesLinearly() {
        assertEquals(1, new GemEnergyState(2).unlockedAbilityCount(7));
        assertEquals(2, new GemEnergyState(3).unlockedAbilityCount(7));
        assertEquals(3, new GemEnergyState(4).unlockedAbilityCount(7));
    }

    @Test
    void unlockedAbilityCount_energy5plus_unlocksAll() {
        assertEquals(7, new GemEnergyState(5).unlockedAbilityCount(7));
        assertEquals(7, new GemEnergyState(10).unlockedAbilityCount(7));
    }

    @Test
    void unlockedAbilityCount_respectsAbilityCount() {
        assertEquals(1, new GemEnergyState(5).unlockedAbilityCount(1));
        assertEquals(0, new GemEnergyState(5).unlockedAbilityCount(0));
        assertEquals(0, new GemEnergyState(5).unlockedAbilityCount(-1));
    }
}

