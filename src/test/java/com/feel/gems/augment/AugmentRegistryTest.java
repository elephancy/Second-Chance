package com.feel.gems.augment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AugmentRegistryTest {
    @Test
    void defaultAugmentsAreRegistered() {
        assertNotNull(AugmentRegistry.get("focus"));
        assertNotNull(AugmentRegistry.get("resonance"));
        assertNotNull(AugmentRegistry.get("persistence"));
        assertNotNull(AugmentRegistry.get("edge"));
        assertNotNull(AugmentRegistry.get("swift"));
        assertNotNull(AugmentRegistry.get("ward"));
        assertTrue(AugmentRegistry.all().size() >= 6);
    }

    @Test
    void defaultAugmentsHaveExpectedTargets() {
        assertEquals(AugmentTarget.GEM, AugmentRegistry.get("focus").target());
        assertEquals(AugmentTarget.GEM, AugmentRegistry.get("resonance").target());
        assertEquals(AugmentTarget.GEM, AugmentRegistry.get("persistence").target());
        assertEquals(AugmentTarget.LEGENDARY, AugmentRegistry.get("edge").target());
        assertEquals(AugmentTarget.LEGENDARY, AugmentRegistry.get("swift").target());
        assertEquals(AugmentTarget.LEGENDARY, AugmentRegistry.get("ward").target());
    }
}
