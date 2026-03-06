package com.feel.gems.adventure.estate;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Shared load points for a 47-square raised module and its loaded audit. */
final class EstateRaisedGeometry {
    static final int[] FOOTING_OFFSETS = {0, 12, 24, 36, 46};
    static final int MAX_SUPPORT_DEPTH = 32;
    static final int MAX_CUT = 12;
    static final int MAX_SEAM_RISE = 10;

    private EstateRaisedGeometry() { }

    static boolean footing(BoundingBox box, int x, int z) {
        return onFootingAxis(x - box.minX()) && onFootingAxis(z - box.minZ());
    }

    static boolean beam(BoundingBox box, int x, int z) {
        return onFootingAxis(x - box.minX()) || onFootingAxis(z - box.minZ());
    }

    private static boolean onFootingAxis(int offset) {
        for (int point : FOOTING_OFFSETS) {
            if (point == 46 ? offset == 45 || offset == 46
                    : offset == point || offset == point + 1) return true;
        }
        return false;
    }
}
