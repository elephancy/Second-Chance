package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-save adapter for the campaign model; all calls belong on the server thread. */
public final class CampaignPersistentState extends SavedData {
    private static final String STATE_ID = GemsMod.MOD_ID + "_rumor_atlas";
    private static final Codec<CampaignProgress.PlayerProgress> PLAYER_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("visited", 0)
                            .forGetter(CampaignProgress.PlayerProgress::visited),
                    Codec.INT.optionalFieldOf("cleared", 0)
                            .forGetter(CampaignProgress.PlayerProgress::cleared),
                    Codec.INT.optionalFieldOf("loreDelivered", 0)
                            .forGetter(CampaignProgress.PlayerProgress::loreDelivered),
                    Codec.INT.optionalFieldOf("signatureDelivered", 0)
                            .forGetter(CampaignProgress.PlayerProgress::signatureDelivered)
            ).apply(instance, CampaignProgress.PlayerProgress::new));
    private static final Codec<CampaignPersistentState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("worldChangedMask", 0)
                            .forGetter(state -> state.progress.snapshot().worldChangedMask()),
                    Codec.unboundedMap(UUIDUtil.AUTHLIB_CODEC, PLAYER_CODEC)
                            .optionalFieldOf("players", Map.of())
                            .forGetter(state -> state.progress.snapshot().players())
            ).apply(instance, CampaignPersistentState::new));
    private static final SavedDataType<CampaignPersistentState> TYPE =
            new SavedDataType<>(Identifier.withDefaultNamespace(STATE_ID), CampaignPersistentState::new,
                    CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final CampaignProgress progress;

    public CampaignPersistentState() {
        this(0, Map.of());
    }

    private CampaignPersistentState(int worldChangedMask,
                                    Map<UUID, CampaignProgress.PlayerProgress> players) {
        progress = CampaignProgress.fromSnapshot(
                new CampaignProgress.Snapshot(worldChangedMask, players));
    }

    public static CampaignPersistentState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public CampaignProgress model() {
        return progress;
    }

    public void visit(UUID player, LandmarkKey landmark) {
        if (progress.visit(player, landmark)) setDirty();
    }

    public CampaignProgress.Victory recordVictory(Collection<UUID> participants,
                                                   LandmarkKey landmark) {
        CampaignProgress.Victory result = progress.recordVictory(participants, landmark);
        setDirty();
        return result;
    }

    public void reconcileDelivered(UUID player, int loreMask, int signatureMask) {
        if (progress.reconcileDelivered(player, loreMask, signatureMask)) setDirty();
    }
}
