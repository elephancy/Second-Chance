package com.feel.gems.adventure;

/** A brief repair opportunity after a flood pulse is survived at a refuge. */
public final class RiverwardBraceWindow {
    private static final long OPEN_TICKS = 20 * 3;
    private long westUntil = -1;
    private long eastUntil = -1;

    public void shelteredPulse(int refuge, long now) {
        if (refuge == RepairRouteState.WEST_BRACE) westUntil = now + OPEN_TICKS;
        if (refuge == RepairRouteState.EAST_BRACE) eastUntil = now + OPEN_TICKS;
    }

    public boolean canRepair(int refuge, long now) {
        return switch (refuge) {
            case RepairRouteState.WEST_BRACE -> now <= westUntil;
            case RepairRouteState.EAST_BRACE -> now <= eastUntil;
            default -> false;
        };
    }
}
