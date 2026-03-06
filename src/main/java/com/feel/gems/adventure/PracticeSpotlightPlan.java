package com.feel.gems.adventure;

import net.minecraft.world.phys.Vec3;

/** The same tracking, lock and strike timing as Tofy's normal boss cycle. */
public final class PracticeSpotlightPlan {
    public static final int LOCK_TICK = 36;
    public static final int STRIKE_TICK = 58;
    public static final int END_TICK = STRIKE_TICK + 4;
    public static final double RADIUS = 2.2;

    public enum Phase { TRACKING, LOCKED, RESULT, FINISHED }

    private PracticeSpotlightPlan() {
    }

    public static Phase phase(int tick) {
        if (tick < LOCK_TICK) return Phase.TRACKING;
        if (tick < STRIKE_TICK) return Phase.LOCKED;
        if (tick < END_TICK) return Phase.RESULT;
        return Phase.FINISHED;
    }

    public static boolean escaped(Vec3 player, Vec3 lock) {
        double dx = player.x - lock.x;
        double dz = player.z - lock.z;
        return Math.abs(player.y - lock.y) > 2.0 || dx * dx + dz * dz > RADIUS * RADIUS;
    }
}
