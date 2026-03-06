package com.feel.gems.adventure;

import net.minecraft.core.BlockPos;

/** Distance limits shared by mark placement and activation. */
final class ShortcutRoute {
    private ShortcutRoute() {
    }

    static boolean inRange(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz <= 128L * 128L
                && Math.abs((long) a.getY() - b.getY()) <= 32;
    }

    static boolean near(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz <= 4L * 4L;
    }
}
