package com.feel.gems.adventure;

/** Two discoverable clues and one shared three-lamp call. */
public final class NourSignalPlan {
    public enum Color { BLUE, AMBER, WHITE }
    public enum Result { NEXT, RESET, COMPLETE }

    private boolean boatRead;
    private boolean fogRead;
    private int step;

    public void readBoat() {
        boatRead = true;
    }

    public void readFog() {
        fogRead = true;
    }

    public boolean ready() {
        return boatRead && fogRead;
    }

    public Result press(Color color) {
        Color[] call = {Color.BLUE, Color.AMBER, Color.WHITE};
        if (color != call[step]) {
            step = 0;
            return Result.RESET;
        }
        step++;
        if (step == call.length) {
            step = 0;
            return Result.COMPLETE;
        }
        return Result.NEXT;
    }
}
