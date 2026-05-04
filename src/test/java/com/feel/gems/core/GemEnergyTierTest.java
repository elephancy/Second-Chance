package com.feel.gems.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;


public final class GemEnergyTierTest {
    @Test
    void fromEnergy_mapsExpectedNames() {
        assertEquals(GemEnergyTier.BROKEN, GemEnergyTier.fromEnergy(0));
        assertEquals(GemEnergyTier.COMMON, GemEnergyTier.fromEnergy(1));
        assertEquals(GemEnergyTier.RARE, GemEnergyTier.fromEnergy(2));
        assertEquals(GemEnergyTier.ELITE, GemEnergyTier.fromEnergy(3));
        assertEquals(GemEnergyTier.MYTHICAL, GemEnergyTier.fromEnergy(4));
        assertEquals(GemEnergyTier.LEGENDARY, GemEnergyTier.fromEnergy(5));
        assertEquals(GemEnergyTier.LEGENDARY_PLUS_5, GemEnergyTier.fromEnergy(10));
    }
}

