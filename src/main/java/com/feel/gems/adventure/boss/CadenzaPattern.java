package com.feel.gems.adventure.boss;

/** A small, readable set of answers to the party's previous beat. */
final class CadenzaPattern {
    enum Act {
        SPOTLIGHT("Spotlight", "Follow the light, then leave the locked circle."),
        CROSSCUT("Crosscut", "The line will lock. Step across it before the strike."),
        CHORUS("Chorus", "The center will pulse. Move out toward the seats.");

        final String label;
        final String hint;

        Act(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }

        Act next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private CadenzaPattern() { }

    static Act choose(int players, boolean closePair, double averageTravel,
                      Act requested) {
        if (requested != null) return requested;
        if (players > 1 && closePair) return Act.CHORUS;
        if (averageTravel >= 5.0) return Act.CROSSCUT;
        return Act.SPOTLIGHT;
    }
}
