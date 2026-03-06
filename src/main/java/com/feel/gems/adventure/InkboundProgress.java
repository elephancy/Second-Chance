package com.feel.gems.adventure;

/** Three world-shared Inkbound recipes, independent of boss victory bits. */
public final class InkboundProgress {
    private int triedMask;

    public InkboundProgress(int triedMask) {
        this.triedMask = triedMask & 7;
    }

    public static int pair(int first, int second) {
        if (first == second || first < 0 || first > 2 || second < 0 || second > 2) {
            return -1;
        }
        return first + second - 1; // 0+1, 0+2, 1+2 map to bits 0, 1, 2.
    }

    public boolean record(int pair) {
        if (pair < 0 || pair > 2) throw new IllegalArgumentException("Invalid recipe");
        int bit = 1 << pair;
        if ((triedMask & bit) != 0) return false;
        triedMask |= bit;
        return true;
    }

    public int triedMask() {
        return triedMask;
    }

    public boolean unlocked() {
        return triedMask == 7;
    }
}
