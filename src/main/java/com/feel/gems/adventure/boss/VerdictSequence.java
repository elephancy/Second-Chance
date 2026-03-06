package com.feel.gems.adventure.boss;

/** Fixed encounter timings. Pure logic so phase changes cannot depend on client ticks. */
public final class VerdictSequence {
    public enum Phase { DORMANT, TELL, FIRE, RECOVERY }

    public static final int TELL_TICKS = 52;
    public static final int LOCK_TICK = 30;
    public static final int FIRE_TICKS = 2;
    public static final int RECOVERY_TICKS = 48;

    private Phase phase = Phase.DORMANT;
    private int ticks;

    public Phase phase() { return phase; }
    public int ticks() { return ticks; }
    public boolean aimLocked() { return phase == Phase.TELL && ticks >= LOCK_TICK; }

    public void start() {
        if (phase == Phase.DORMANT) {
            phase = Phase.TELL;
            ticks = 0;
        }
    }

    public void reset() {
        phase = Phase.DORMANT;
        ticks = 0;
    }

    /** Advances one server tick; returns true on a phase change. */
    public boolean tick() {
        if (phase == Phase.DORMANT) return false;
        ticks++;
        int duration = switch (phase) {
            case TELL -> TELL_TICKS;
            case FIRE -> FIRE_TICKS;
            case RECOVERY -> RECOVERY_TICKS;
            case DORMANT -> Integer.MAX_VALUE;
        };
        if (ticks < duration) return false;
        phase = switch (phase) {
            case TELL -> Phase.FIRE;
            case FIRE -> Phase.RECOVERY;
            case RECOVERY -> Phase.TELL;
            case DORMANT -> Phase.DORMANT;
        };
        ticks = 0;
        return true;
    }
}
