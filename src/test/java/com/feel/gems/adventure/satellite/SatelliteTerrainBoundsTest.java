package com.feel.gems.adventure.satellite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SatelliteTerrainBoundsTest {
    @Test
    void levelSeabedIsV4OnlyAndStillBounded() {
        int footY = 27;
        assertFalse(SatelliteTerrainBounds.oceanFloor(footY, footY));
        assertTrue(SatelliteTerrainBounds.oceanV4Floor(footY, footY));
        assertFalse(SatelliteTerrainBounds.oceanV4Floor(footY, footY - 1));
        assertTrue(SatelliteTerrainBounds.oceanV4Floor(footY, footY + 13));
        assertFalse(SatelliteTerrainBounds.oceanV4Floor(footY, footY + 14));
    }
}
