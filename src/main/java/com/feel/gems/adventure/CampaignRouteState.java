package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** One deliberate, persistent story route for a shared world. */
public final class CampaignRouteState extends SavedData {
    public enum Path {
        REMEMBER("remember", "Remember"),
        REPAIR("repair", "Repair"),
        RELEASE("release", "Release");

        private final String id;
        private final String label;

        Path(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() { return id; }
        public String label() { return label; }

        public static Path from(String id) {
            for (Path path : values()) if (path.id.equals(id)) return path;
            return null;
        }

        public static Path next(Path current) {
            return current == null ? REMEMBER : values()[(current.ordinal() + 1) % values().length];
        }
    }

    public enum Resolution { CHOSEN, ALREADY_CHOSEN, NEED_VOTES, NO_MAJORITY, PROPOSER_MISMATCH }

    private static final Codec<CampaignRouteState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("chosen", "")
                            .forGetter(state -> state.chosen == null ? "" : state.chosen.id()),
                    Codec.unboundedMap(UUIDUtil.AUTHLIB_CODEC, Codec.STRING)
                            .optionalFieldOf("votes", Map.of())
                            .forGetter(CampaignRouteState::savedVotes),
                    Codec.INT.optionalFieldOf("rememberPrints", 0)
                            .forGetter(state -> state.rememberPrints),
                    Codec.BOOL.optionalFieldOf("rememberInkboundBound", false)
                            .forGetter(state -> state.rememberInkboundBound),
                    Codec.INT.optionalFieldOf("rememberWitnesses", 0)
                            .forGetter(state -> state.rememberWitnesses),
                    Codec.BOOL.optionalFieldOf("rememberArchiveCompared", false)
                            .forGetter(state -> state.rememberArchiveCompared),
                    Codec.INT.optionalFieldOf("rememberTaleStage", 0)
                            .forGetter(state -> state.rememberTaleStage)
            ).apply(instance, CampaignRouteState::new));
    private static final SavedDataType<CampaignRouteState> TYPE =
            new SavedDataType<>(Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_story_route"),
                    CampaignRouteState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final Map<UUID, Path> votes = new HashMap<>();
    private Path chosen;
    private int rememberPrints;
    private boolean rememberInkboundBound;
    private int rememberWitnesses;
    private boolean rememberArchiveCompared;
    private int rememberTaleStage;

    public CampaignRouteState() {
        this("", Map.of(), 0, false, 0, false, 0);
    }

    private CampaignRouteState(String chosenId, Map<UUID, String> storedVotes,
                               int storedPrints, boolean storedBound,
                               int storedWitnesses, boolean storedCompared, int storedTaleStage) {
        chosen = Path.from(chosenId);
        if (chosen == Path.REMEMBER) {
            rememberPrints = storedPrints & 0b111;
            rememberInkboundBound = storedBound;
            rememberWitnesses = storedWitnesses & 0b11;
            rememberArchiveCompared = storedCompared;
            rememberTaleStage = rememberArchiveCompared ? Math.clamp(storedTaleStage, 0, 5) : 0;
        }
        if (chosen == null) {
            storedVotes.forEach((id, pathId) -> {
                Path path = Path.from(pathId);
                if (path != null) votes.put(id, path);
            });
        }
    }

    public static CampaignRouteState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public Path chosen() {
        return chosen;
    }

    public Path voteOf(UUID player) {
        return votes.get(player);
    }

    public int rememberPrintCount() {
        return Integer.bitCount(rememberPrints);
    }

    public boolean rememberInkboundBound() {
        return rememberInkboundBound;
    }

    /** Only prints made after Remember is chosen count toward its field chapter. */
    public boolean recordRememberPrint(int pair) {
        if (chosen != Path.REMEMBER || rememberInkboundBound || rememberPrintCount() >= 2
                || pair < 0 || pair > 2) return false;
        int bit = 1 << pair;
        if ((rememberPrints & bit) != 0) return false;
        rememberPrints |= bit;
        setDirty();
        return true;
    }

    public boolean bindRememberInkbound() {
        if (chosen != Path.REMEMBER || rememberInkboundBound || rememberPrintCount() < 2) return false;
        rememberInkboundBound = true;
        setDirty();
        return true;
    }

    public int rememberWitnessCount() {
        return Integer.bitCount(rememberWitnesses);
    }

    public boolean rememberArchiveCompared() {
        return rememberArchiveCompared;
    }

    /** Witnesses are found in two different expedition side buildings. */
    public boolean recordRememberWitness(int witness) {
        if (chosen != Path.REMEMBER || !rememberInkboundBound || rememberArchiveCompared
                || (witness != 1 && witness != 2) || (rememberWitnesses & witness) != 0) return false;
        rememberWitnesses |= witness;
        setDirty();
        return true;
    }

    public boolean compareRememberArchive() {
        if (chosen != Path.REMEMBER || !rememberInkboundBound || rememberArchiveCompared
                || rememberWitnesses != 0b11) return false;
        rememberArchiveCompared = true;
        setDirty();
        return true;
    }

    public int rememberTaleStage() {
        return rememberTaleStage;
    }

    public boolean startRememberTale() {
        if (chosen != Path.REMEMBER || !rememberArchiveCompared || rememberTaleStage != 0) return false;
        rememberTaleStage = 1;
        setDirty();
        return true;
    }

    /** Reverse the familiar tree, lantern, mushroom route without a penalty. */
    public int markRememberTale(int marker) {
        if (marker < YehiaStoryState.MUSHROOM || marker > YehiaStoryState.TREE) {
            throw new IllegalArgumentException("Unknown tale marker");
        }
        if (chosen != Path.REMEMBER || rememberTaleStage < 1 || rememberTaleStage > 3) {
            return rememberTaleStage;
        }
        int next = marker == 3 - rememberTaleStage ? rememberTaleStage + 1 : 1;
        if (next != rememberTaleStage) {
            rememberTaleStage = next;
            setDirty();
        }
        return rememberTaleStage;
    }

    public boolean finishRememberTale() {
        if (chosen != Path.REMEMBER || rememberTaleStage != 4) return false;
        rememberTaleStage = 5;
        setDirty();
        return true;
    }

    public boolean vote(UUID player, Path path) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(path, "path");
        if (chosen != null || votes.put(player, path) == path) return false;
        setDirty();
        return true;
    }

    public Resolution confirm(Collection<UUID> nearbyPlayers, UUID proposer) {
        Objects.requireNonNull(nearbyPlayers, "nearbyPlayers");
        Objects.requireNonNull(proposer, "proposer");
        if (chosen != null) return Resolution.ALREADY_CHOSEN;
        Set<UUID> present = new HashSet<>(nearbyPlayers);
        if (present.isEmpty() || !present.contains(proposer) || !votes.keySet().containsAll(present)) {
            return Resolution.NEED_VOTES;
        }
        Map<Path, Integer> counts = new EnumMap<>(Path.class);
        for (UUID id : present) counts.merge(votes.get(id), 1, Integer::sum);
        Path winner = null;
        for (Path path : Path.values()) {
            if (counts.getOrDefault(path, 0) > present.size() / 2) winner = path;
        }
        if (winner == null) return Resolution.NO_MAJORITY;
        if (votes.get(proposer) != winner) return Resolution.PROPOSER_MISMATCH;
        chosen = winner;
        votes.clear();
        setDirty();
        return Resolution.CHOSEN;
    }

    private Map<UUID, String> savedVotes() {
        Map<UUID, String> result = new HashMap<>();
        votes.forEach((id, path) -> result.put(id, path.id()));
        return result;
    }
}
