package com.feel.gems.adventure;

/** Authored side-route clues tied to rooms that exist in the seven templates. */
public final class RouteDeskClues {
    private RouteDeskClues() {
    }

    public static String alternate(LandmarkKey target) {
        return switch (target) {
            case FRAMEFALL -> "Copper trails split around the sculpture garden."
                    + " Stone displays cast long shadows beside the gallery.";
            case LASTLIGHT -> "A newer copper shelter leans over the exchange."
                    + " Platform lamps brighten and dim by the tracks.";
            case REHEARSAL -> "A raised catwalk crosses the prop foundry."
                    + " Below it, the spotlight sweeps the stage approach.";
            case BRANCH -> "The escape tower stands apart from the vault."
                    + " From its roof, the old exit looks different.";
            case REDLINE -> "Gold shelter tiles line the infirmary wall."
                    + " Watchkeepers keep their eyes on the open yard.";
            case HUSHWIRE -> "A low trench cuts through the dishfield."
                    + " Blue light moves across the receivers.";
            case UNWRITTEN -> "The steward's annex faces the Court."
                    + " Its back room still holds an account of the stage.";
        };
    }
}
