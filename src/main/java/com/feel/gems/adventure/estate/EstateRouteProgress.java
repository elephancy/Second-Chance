package com.feel.gems.adventure.estate;

import net.minecraft.core.BlockPos;

/** Ordered, session-only proof that one player traversed one authored wing. */
final class EstateRouteProgress {
    private final int[] modules;
    private int index;
    private boolean seamEntered;

    EstateRouteProgress(char wing) {
        int first = switch (wing) {
            case 'N' -> 2;
            case 'E' -> 14;
            case 'S' -> 22;
            case 'W' -> 10;
            default -> throw new IllegalArgumentException("Unknown estate wing");
        };
        modules = new int[5];
        int id = first;
        for (int i = 0; i < modules.length; i++) {
            modules[i] = id;
            id = EstateModuleGraph.parent(id);
        }
        if (id != -1 || modules[4] != 12) {
            throw new IllegalStateException("Invalid estate wing graph");
        }
    }

    int current() { return modules[index]; }

    int first() { return modules[0]; }

    int next() { return index + 1 < modules.length ? modules[index + 1] : -1; }

    boolean complete() { return index == modules.length - 1; }

    boolean seamEntered() { return seamEntered; }

    boolean acceptsSeamPosition(EstateRouteSite.Seam seam, BlockPos here) {
        return !seamEntered || inSeam(seam, here);
    }

    static boolean inSeam(EstateRouteSite.Seam seam, BlockPos here) {
        int dx = here.getX() - seam.from().getX();
        int dz = here.getZ() - seam.from().getZ();
        int along = dx * seam.direction().getStepX()
                + dz * seam.direction().getStepZ();
        int lateral = dx * seam.direction().getStepZ()
                - dz * seam.direction().getStepX();
        return along >= -1 && along <= 13 && Math.abs(lateral) <= 4
                && Math.abs(here.getY()
                        - seam.floorAt(Math.clamp(along, 0, 12))) <= 5;
    }

    void enterSeam(boolean verified) {
        if (!complete() && verified) seamEntered = true;
    }

    boolean arrive(int module, boolean nearExit) {
        if (!seamEntered || !nearExit || module != next()) return false;
        index++;
        seamEntered = false;
        return true;
    }

    /** A module boundary can precede the far end of its twelve-block seam. */
    boolean continueToExit(int module, EstateRouteSite.Seam seam,
                           BlockPos here, boolean nearExit) {
        if (!seamEntered || module != next() || !inSeam(seam, here)) return false;
        return !nearExit || arrive(module, true);
    }
}
