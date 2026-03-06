package com.feel.gems.adventure;

/** One player's timed passage across Khaled's two marked refuges. */
public final class KhaledCrossingPlan {
    public enum Event {NONE, WEST_REACHED, EAST_REACHED, WARNING, SHELTERED, FLOODED, EXPIRED}

    private static final long PULSE_TICKS = 20 * 8;
    private static final long FIRST_PULSE_TICKS = 20 * 12;
    private static final long LIMIT_TICKS = 20 * 75;
    private final long startedAt;
    private long nextPulse;
    private boolean warned;
    private int stage;

    public KhaledCrossingPlan(long startedAt) {
        this.startedAt = startedAt;
        nextPulse = startedAt + FIRST_PULSE_TICKS;
    }

    public Event visit(boolean atWestRefuge, boolean atEastRefuge) {
        if (stage == 0 && atWestRefuge) {
            stage = 1;
            return Event.WEST_REACHED;
        }
        if (stage == 1 && atEastRefuge) {
            stage = 2;
            return Event.EAST_REACHED;
        }
        return Event.NONE;
    }

    public Event tick(long now, boolean onShelter) {
        if (now - startedAt > LIMIT_TICKS) return Event.EXPIRED;
        if (now >= nextPulse) {
            nextPulse = now + PULSE_TICKS;
            warned = false;
            return onShelter ? Event.SHELTERED : Event.FLOODED;
        }
        if (!warned && now >= nextPulse - 40) {
            warned = true;
            return Event.WARNING;
        }
        return Event.NONE;
    }

    public boolean finish(long now) {
        return stage == 2 && now - startedAt <= LIMIT_TICKS;
    }

    public boolean expired(long now) {
        return now - startedAt > LIMIT_TICKS;
    }

    public int stage() {
        return stage;
    }
}
