package com.feel.gems.adventure.boss;

/** Finite, horizontal attack geometry shared by the six regional sentries. */
final class RegionalSentryPlan {
    private RegionalSentryPlan() { }

    static boolean circle(double x, double z, double cx, double cz, double radius) {
        double dx = x - cx;
        double dz = z - cz;
        return dx * dx + dz * dz <= radius * radius;
    }

    static boolean axis(double x, double z, double cx, double cz,
                        boolean alongX, double halfLength, double halfWidth) {
        return alongX
                ? Math.abs(x - cx) <= halfLength && Math.abs(z - cz) <= halfWidth
                : Math.abs(z - cz) <= halfLength && Math.abs(x - cx) <= halfWidth;
    }

    static boolean ray(double x, double z, double cx, double cz, int dx, int dz,
                       double start, double end, double halfWidth) {
        double along = (x - cx) * dx + (z - cz) * dz;
        double across = (x - cx) * dz - (z - cz) * dx;
        return along >= start && along <= end && Math.abs(across) <= halfWidth;
    }
}
