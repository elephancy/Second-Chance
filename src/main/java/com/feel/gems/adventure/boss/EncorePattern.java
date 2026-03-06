package com.feel.gems.adventure.boss;

import net.minecraft.core.BlockPos;

/** One real receiver among three equal opening calls. */
final class EncorePattern {
    enum Receiver {
        LEFT(-4, -6, "left"), RIGHT(4, -6, "right"), BACK(0, 9, "back");

        final int dx;
        final int dz;
        final String label;

        Receiver(int dx, int dz, String label) {
            this.dx = dx;
            this.dz = dz;
            this.label = label;
        }
    }

    static BlockPos pad(BlockPos center, Receiver receiver, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> center.offset(-receiver.dz, 0, receiver.dx);
            case 2 -> center.offset(-receiver.dx, 0, -receiver.dz);
            case 3 -> center.offset(receiver.dz, 0, -receiver.dx);
            default -> center.offset(receiver.dx, 0, receiver.dz);
        };
    }

    private Receiver real;
    Receiver next(int roll) {
        int index = real == null ? Math.floorMod(roll, Receiver.values().length)
                : (real.ordinal() + 1 + Math.floorMod(roll, 2)) % Receiver.values().length;
        real = Receiver.values()[index];
        return real;
    }

    Receiver real() {
        return real;
    }

    boolean isReal(Receiver receiver) {
        return real != null && real == receiver;
    }

    void reset() {
        real = null;
    }
}
