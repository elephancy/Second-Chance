package com.feel.gems.adventure;

/** A timed handoff from the infirmary through both restored shelter lines. */
final class RedlineAidPlan {
    enum Event { NONE, EAST_HELD, WEST_HELD, EXPIRED }

    private static final int HOLD_TICKS = 60;
    private static final int LIMIT_TICKS = 20 * 120;

    private final long started;
    private int stage;
    private int heldTicks;

    RedlineAidPlan(long started) {
        this.started = started;
    }

    Event tick(long now, boolean onEastShelter, boolean onWestShelter) {
        if (expired(now)) return Event.EXPIRED;
        if (stage >= 2) return Event.NONE;
        boolean onTarget = stage == 0 ? onEastShelter : onWestShelter;
        heldTicks = onTarget ? heldTicks + 5 : 0;
        if (heldTicks < HOLD_TICKS) return Event.NONE;
        heldTicks = 0;
        return stage++ == 0 ? Event.EAST_HELD : Event.WEST_HELD;
    }

    boolean ready(long now) {
        return stage == 2 && !expired(now);
    }

    boolean expired(long now) {
        return now < started || now - started > LIMIT_TICKS;
    }
}
