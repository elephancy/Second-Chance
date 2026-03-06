package com.feel.gems.adventure.boss;

import java.util.UUID;

/** One committed route; a successful feint creates a longer recovery. */
public final class SecondMovePlan {
    public enum Route {
        NORTH(0, -1, "north"), SOUTH(0, 1, "south"),
        WEST(-1, 0, "west"), EAST(1, 0, "east"), CENTER(0, 0, "center");

        public final int dx;
        public final int dz;
        public final String label;

        Route(int dx, int dz, String label) {
            this.dx = dx;
            this.dz = dz;
            this.label = label;
        }

        public static Route of(double dx, double dz) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) < 3.0) return CENTER;
            if (Math.abs(dx) >= Math.abs(dz)) return dx < 0 ? WEST : EAST;
            return dz < 0 ? NORTH : SOUTH;
        }
    }

    private Route predicted = Route.SOUTH;
    private Route lastEscape = Route.CENTER;
    private UUID lastTarget;
    private boolean evaded;
    private boolean locked;

    public Route begin(UUID target, Route current) {
        predicted = evaded && target.equals(lastTarget) && lastEscape != Route.CENTER
                ? lastEscape : current == Route.CENTER ? Route.SOUTH : current;
        lastTarget = target;
        evaded = false;
        locked = false;
        return predicted;
    }

    public void lock() {
        locked = true;
    }

    public boolean resolve(Route actual) {
        if (!locked) throw new IllegalStateException("Route must lock before the strike");
        evaded = actual != Route.CENTER && actual != predicted;
        lastEscape = actual;
        return evaded;
    }

    public Route predicted() {
        return predicted;
    }

    public void clear() {
        lastTarget = null;
        lastEscape = Route.CENTER;
        evaded = false;
        locked = false;
        predicted = Route.SOUTH;
    }
}
