package com.feel.gems.adventure;

/** The two field calls can be split between players, within one bounded window. */
final class NourRelayPlan {
    static final int BOATHOUSE = 1;
    static final int FOGHOUSE = 2;
    private static final long WINDOW_TICKS = 20 * 180;

    private final long started;
    private int calls;

    NourRelayPlan(long started) {
        this.started = started;
    }

    boolean record(int site, long now) {
        if (site != BOATHOUSE && site != FOGHOUSE) {
            throw new IllegalArgumentException("Unknown harbor relay site");
        }
        if (expired(now) || (calls & site) != 0) return false;
        calls |= site;
        return true;
    }

    boolean ready(long now) {
        return calls == (BOATHOUSE | FOGHOUSE) && !expired(now);
    }

    int count() {
        return Integer.bitCount(calls);
    }

    long secondsLeft(long now) {
        return Math.max(0, (started + WINDOW_TICKS - now + 19) / 20);
    }

    boolean expired(long now) {
        return now < started || now - started > WINDOW_TICKS;
    }
}
