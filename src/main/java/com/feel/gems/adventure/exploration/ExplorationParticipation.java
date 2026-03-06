package com.feel.gems.adventure.exploration;

/** A nearby friend shares a clear only after taking part in that same site. */
public final class ExplorationParticipation {
    private ExplorationParticipation() { }

    public static boolean ready(String kind, long origin,
                                ExplorationProgress.Journey journey,
                                boolean hasSpyglass, boolean facesEast,
                                boolean underOpenSky, boolean atDialHeight) {
        return switch (kind) {
            case "trail" -> journey.trailOrigin() == origin && journey.trailStep() == 1;
            case "cipher" -> journey.cipherPlaque() == origin && journey.cipherStep() == 3;
            case "vista" -> hasSpyglass && facesEast && underOpenSky && atDialHeight;
            default -> false;
        };
    }
}
