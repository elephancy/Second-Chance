package com.feel.gems.adventure.satellite;

/** Shared limits for pre-carver selection and the saved-world safety audit. */
final class SatelliteTerrainBounds {
    static final int OCEAN_MIN_Y = 26;
    static final int OCEAN_MAX_Y = 27;
    static final int OCEAN_FLOOR_MIN_OFFSET = 3;
    static final int OCEAN_FLOOR_MAX_OFFSET = 10;
    // New v4 pieces have a sealed four-layer foot and contoured outer skirt.
    // Earlier saved pieces retain their exact, narrower terrain contract.
    // A level seabed beside v4's four-layer sealed foot is sound when the
    // adjacent natural column also has four dry, solid support layers.
    static final int OCEAN_V4_FLOOR_MIN_OFFSET = 0;
    static final int OCEAN_V4_FLOOR_MAX_OFFSET = 13;
    static final int OCEAN_SHORE_MAX_Y = 40;
    static final int ARCHIVE_SHELTER_RADIUS = 7;
    static final int ARCHIVE_SHELTER_MIN_OFFSET = 47;
    static final int ARCHIVE_SHELTER_MAX_OFFSET = 51;
    static final int ARCHIVE_ROOT_MIN_Y = 48;
    static final int ARCHIVE_ROOT_MAX_Y = 112;

    private SatelliteTerrainBounds() { }

    static boolean oceanFloor(int minY, int floor) {
        return floor >= minY + OCEAN_FLOOR_MIN_OFFSET
                && floor <= minY + OCEAN_FLOOR_MAX_OFFSET;
    }

    static boolean oceanV4Floor(int minY, int floor) {
        return floor >= minY + OCEAN_V4_FLOOR_MIN_OFFSET
                && floor <= minY + OCEAN_V4_FLOOR_MAX_OFFSET;
    }

    static boolean archiveShelterSurface(int minY, int surface) {
        return surface >= minY + ARCHIVE_SHELTER_MIN_OFFSET
                && surface <= minY + ARCHIVE_SHELTER_MAX_OFFSET;
    }
}
