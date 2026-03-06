package com.feel.gems.adventure.exploration;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Durable shared discoveries and personal puzzle state. Server thread only. */
public final class ExplorationState extends SavedData {
    private static final Codec<ExplorationProgress.Journey> JOURNEY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.optionalFieldOf("trailOrigin", ExplorationProgress.Journey.NONE)
                            .forGetter(ExplorationProgress.Journey::trailOrigin),
                    Codec.INT.optionalFieldOf("trailStep", 0)
                            .forGetter(ExplorationProgress.Journey::trailStep),
                    Codec.LONG.optionalFieldOf("cipherPlaque", ExplorationProgress.Journey.NONE)
                            .forGetter(ExplorationProgress.Journey::cipherPlaque),
                    Codec.INT.optionalFieldOf("cipherStep", 0)
                            .forGetter(ExplorationProgress.Journey::cipherStep)
            ).apply(instance, ExplorationProgress.Journey::new));
    private static final Codec<ExplorationState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("discovered", List.of())
                    .forGetter(state -> state.model.snapshot().discovered().stream().sorted().toList()),
            Codec.unboundedMap(UUIDUtil.AUTHLIB_CODEC, Codec.STRING.listOf())
                    .optionalFieldOf("earned", Map.of())
                    .forGetter(state -> state.model.snapshot().earned().entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey,
                                    row -> row.getValue().stream().sorted().toList()))),
            Codec.unboundedMap(UUIDUtil.AUTHLIB_CODEC, JOURNEY_CODEC)
                    .optionalFieldOf("journeys", Map.of())
                    .forGetter(state -> state.model.snapshot().journeys())
    ).apply(instance, ExplorationState::new));
    private static final SavedDataType<ExplorationState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_exploration"),
            ExplorationState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final ExplorationProgress model;

    public ExplorationState() {
        model = new ExplorationProgress();
    }

    private ExplorationState(List<String> discovered, Map<UUID, List<String>> earned,
                             Map<UUID, ExplorationProgress.Journey> journeys) {
        Map<UUID, Set<String>> earnedSets = earned.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        row -> Set.copyOf(new java.util.HashSet<>(row.getValue()))));
        model = ExplorationProgress.fromSnapshot(new ExplorationProgress.Snapshot(
                Set.copyOf(new java.util.HashSet<>(discovered)), earnedSets, journeys));
    }

    public static ExplorationState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public ExplorationProgress model() {
        return model;
    }

    public ExplorationProgress.Result complete(String activityId, Collection<UUID> participants) {
        ExplorationProgress.Result result = model.complete(activityId, participants);
        if (result.firstWorldDiscovery() || !result.firstPlayerClears().isEmpty()) setDirty();
        return result;
    }

    public void reconcileReceipt(UUID player, String activityId) {
        if (model.reconcileReceipt(player, activityId)) setDirty();
    }

    public void startTrail(UUID player, long origin) {
        if (model.startTrail(player, origin)) setDirty();
    }

    public void clearTrail(UUID player) {
        if (model.clearTrail(player)) setDirty();
    }

    public void markTrailTurn(UUID player) {
        if (model.markTrailTurn(player)) setDirty();
    }

    public void startCipher(UUID player, long plaque) {
        if (model.startCipher(player, plaque)) setDirty();
    }

    public int pressCipher(UUID player, int symbol) {
        int before = model.journey(player).cipherStep();
        int after = model.pressCipher(player, symbol);
        if (after != before) setDirty();
        return after;
    }

    public void clearCipher(UUID player) {
        if (model.clearCipher(player)) setDirty();
    }
}
