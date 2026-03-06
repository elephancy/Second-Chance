package com.feel.gems.adventure;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-thread campaign progress, kept separate from Gems energy and hearts.
 * A boss victory creates a durable personal entitlement before item delivery;
 * inventory-full, death, logout and rejoin can retry that delivery safely.
 */
public final class CampaignProgress {
    private final Map<UUID, PlayerProgress> players = new HashMap<>();
    private int worldChangedMask;

    /** Returns whether this is the player's first visit to the landmark. */
    public boolean visit(UUID player, LandmarkKey landmark) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(landmark, "landmark");
        PlayerProgress old = progress(player);
        if ((old.visited() & landmark.bit()) != 0) return false;
        players.put(player, new PlayerProgress(old.visited() | landmark.bit(), old.cleared(),
                old.loreDelivered(), old.signatureDelivered()));
        return true;
    }

    public Victory recordVictory(Collection<UUID> participants, LandmarkKey landmark) {
        Objects.requireNonNull(participants, "participants");
        Objects.requireNonNull(landmark, "landmark");
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("A boss victory requires at least one eligible player");
        }
        Map<UUID, Boolean> firstClears = new HashMap<>();
        for (UUID player : Set.copyOf(participants)) {
            Objects.requireNonNull(player, "participant");
            PlayerProgress old = progress(player);
            boolean first = (old.cleared() & landmark.bit()) == 0;
            players.put(player, new PlayerProgress(old.visited() | landmark.bit(),
                    old.cleared() | landmark.bit(), old.loreDelivered(),
                    old.signatureDelivered()));
            firstClears.put(player, first);
        }
        boolean worldFirst = (worldChangedMask & landmark.bit()) == 0;
        worldChangedMask |= landmark.bit();
        return new Victory(worldFirst, Map.copyOf(firstClears));
    }

    public boolean needsLore(UUID player, LandmarkKey landmark) {
        PlayerProgress state = progress(player);
        return (state.cleared() & landmark.bit()) != 0
                && (state.loreDelivered() & landmark.bit()) == 0;
    }

    public boolean needsSignature(UUID player, LandmarkKey landmark) {
        PlayerProgress state = progress(player);
        return (state.cleared() & landmark.bit()) != 0
                && (state.signatureDelivered() & landmark.bit()) == 0;
    }

    /** Call only after the item was actually placed in inventory or safe mail. */
    public void markLoreDelivered(UUID player, LandmarkKey landmark) {
        if (!needsLore(player, landmark)) {
            throw new IllegalStateException("No pending identity lore for " + landmark.id());
        }
        PlayerProgress old = progress(player);
        players.put(player, new PlayerProgress(old.visited(), old.cleared(),
                old.loreDelivered() | landmark.bit(), old.signatureDelivered()));
    }

    /** Call only after the item was actually placed in inventory or safe mail. */
    public void markSignatureDelivered(UUID player, LandmarkKey landmark) {
        if (!needsSignature(player, landmark)) {
            throw new IllegalStateException("No pending signature reward for " + landmark.id());
        }
        PlayerProgress old = progress(player);
        players.put(player, new PlayerProgress(old.visited(), old.cleared(),
                old.loreDelivered(), old.signatureDelivered() | landmark.bit()));
    }

    public boolean hasCleared(UUID player, LandmarkKey landmark) {
        return (progress(player).cleared() & landmark.bit()) != 0;
    }

    /** A player-owned delivery receipt also proves a clear if the world save lagged. */
    public boolean reconcileDelivered(UUID player, int loreMask, int signatureMask) {
        Objects.requireNonNull(player, "player");
        int lore = loreMask & LandmarkKey.ALL_MASK;
        int signature = signatureMask & LandmarkKey.ALL_MASK;
        int earned = lore | signature;
        if (earned == 0) return false;
        PlayerProgress old = progress(player);
        PlayerProgress merged = new PlayerProgress(old.visited() | earned,
                old.cleared() | earned, old.loreDelivered() | lore,
                old.signatureDelivered() | signature);
        if (merged.equals(old)) return false;
        players.put(player, merged);
        return true;
    }

    public boolean worldChanged(LandmarkKey landmark) {
        return (worldChangedMask & landmark.bit()) != 0;
    }

    public PlayerProgress progress(UUID player) {
        Objects.requireNonNull(player, "player");
        return players.getOrDefault(player, PlayerProgress.EMPTY);
    }

    public Snapshot snapshot() {
        return new Snapshot(worldChangedMask, Map.copyOf(players));
    }

    public static CampaignProgress fromSnapshot(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        CampaignProgress result = new CampaignProgress();
        result.worldChangedMask = snapshot.worldChangedMask() & LandmarkKey.ALL_MASK;
        snapshot.players().forEach((id, row) -> {
            int cleared = row.cleared() & LandmarkKey.ALL_MASK;
            result.players.put(id, new PlayerProgress((row.visited() | cleared) & LandmarkKey.ALL_MASK,
                    cleared, row.loreDelivered() & cleared,
                    row.signatureDelivered() & cleared));
        });
        return result;
    }

    public record PlayerProgress(int visited, int cleared, int loreDelivered,
                                 int signatureDelivered) {
        public static final PlayerProgress EMPTY = new PlayerProgress(0, 0, 0, 0);
    }

    public record Victory(boolean firstWorldChange, Map<UUID, Boolean> firstClears) {
    }

    public record Snapshot(int worldChangedMask, Map<UUID, PlayerProgress> players) {
    }
}
