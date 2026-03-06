package com.feel.gems.adventure.boss;

/** The shelter selected for a single guard cycle; reaching it breaks the line. */
final class RedlineGuardPlan {
    enum Wing {
        NORTH(0, -1, "north"), EAST(1, 0, "east"),
        SOUTH(0, 1, "south"), WEST(-1, 0, "west");

        final int dx;
        final int dz;
        final String label;

        Wing(int dx, int dz, String label) {
            this.dx = dx;
            this.dz = dz;
            this.label = label;
        }

        static Wing nearest(double dx, double dz) {
            if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? EAST : WEST;
            return dz >= 0 ? SOUTH : NORTH;
        }

        Wing next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private Wing wing;
    private Wing previous;
    private boolean locked;
    private boolean broken;

    Wing begin(Wing preferred) {
        wing = preferred == previous ? preferred.next() : preferred;
        previous = wing;
        locked = false;
        broken = false;
        return wing;
    }

    boolean reach(Wing touched) {
        if (wing == null || locked || broken || touched != wing) return false;
        broken = true;
        return true;
    }

    void lock() {
        locked = true;
    }

    boolean broken() {
        return broken;
    }

    Wing wing() {
        return wing;
    }

    void reset() {
        wing = null;
        previous = null;
        locked = false;
        broken = false;
    }
}
